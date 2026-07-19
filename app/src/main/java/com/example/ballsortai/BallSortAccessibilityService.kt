package com.example.ballsortai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button

class BallSortAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BallSortAccessibilityService? = null
        private const val NOTIF_CHANNEL = "ballsort_ai"
    }

    private lateinit var windowManager: WindowManager
    private var overlayButton: Button? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var watching = false
    private var lastBoardSignature: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        showStartButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        watching = false
        stopProjection()
        removeOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showStartButton() {
        removeOverlay()
        val btn = Button(this).apply {
            text = "Iniciar"
            setBackgroundColor(Color.parseColor("#2ECC71"))
            setTextColor(Color.WHITE)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24
        params.y = 300

        btn.setOnClickListener {
            btn.text = "Analisando..."
            btn.isEnabled = false
            requestProjectionIfNeeded { analyzeAndPlay() }
        }

        windowManager.addView(btn, params)
        overlayButton = btn
    }

    private fun updateButtonLabel(text: String) {
        handler.post { overlayButton?.text = text; overlayButton?.isEnabled = true }
    }

    private fun removeOverlay() {
        overlayButton?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        overlayButton = null
    }

    private var pendingAfterProjection: (() -> Unit)? = null

    private fun requestProjectionIfNeeded(after: () -> Unit) {
        if (mediaProjection != null) { after(); return }
        pendingAfterProjection = after
        val intent = Intent(this, ProjectionRequestActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun onProjectionGranted(resultCode: Int, data: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        ensureNotificationChannel()
        mediaProjection = mgr.getMediaProjection(resultCode, data)
        setupVirtualDisplay()
        pendingAfterProjection?.invoke()
        pendingAfterProjection = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Ball Sort AI", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BallSortAI-Capture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun stopProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        Thread.sleep(150)
        val image = reader.acquireLatestImage() ?: return null
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun analyzeAndPlay() {
        Thread {
            val bitmap = captureBitmap()
            if (bitmap == null) { updateButtonLabel("Erro captura"); return@Thread }

            val board = BoardDetector.detect(bitmap)
            if (board == null || board.tubes.isEmpty()) {
                updateButtonLabel("Não achei o tabuleiro")
                return@Thread
            }

            lastBoardSignature = signature(board)

            val tubeColors = board.tubes.map { it.colorsBottomToTop }
            val moves = Solver.solve(tubeColors, board.capacity)
            if (moves == null) {
                updateButtonLabel("Sem solução")
                return@Thread
            }

            playing = true
            executeMoves(moves, board)
        }.start()
    }

    private fun executeMoves(moves: List<Solver.Move>, board: BoardDetector.DetectedBoard) {
        var i = 0
        fun next() {
            if (i >= moves.size || !playing) {
                playing = false
                onLevelFinished()
                return
            }
            val move = moves[i]
            val fromTube = board.tubes[move.from]
            val toTube = board.tubes[move.to]
            tap(fromTube.tapX, fromTube.tapY) {
                handler.postDelayed({
                    tap(toTube.tapX, toTube.tapY) {
                        i++
                        handler.postDelayed({ next() }, 350)
                    }
                }, 250)
            }
        }
        handler.post { next() }
    }

    private fun tap(x: Int, y: Int, onDone: () -> Unit) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
        }, null)
    }

    private fun signature(board: BoardDetector.DetectedBoard): String {
        return board.capacity.toString() + "#" +
                board.tubes.joinToString("|") { it.colorsBottomToTop.joinToString(",") }
    }

    private fun onLevelFinished() {
        updateButtonLabel("Nível OK ✔")
        if (!watching) {
            watching = true
            watchForBoardChange()
        }
    }

    private fun watchForBoardChange() {
        if (!watching) return
        Thread {
            val bitmap = captureBitmap()
            val board = bitmap?.let { BoardDetector.detect(it) }
            val sig = board?.let { signature(it) }

            if (sig != null && sig != lastBoardSignature) {
                lastBoardSignature = sig
                watching = false
                updateButtonLabel("Analisando...")
                val tubeColors = board.tubes.map { it.colorsBottomToTop }
                val moves = Solver.solve(tubeColors, board.capacity)
                if (moves != null) {
                    playing = true
                    executeMoves(moves, board)
                } else {
                    updateButtonLabel("Sem solução")
                    watching = true
                    handler.postDelayed({ watchForBoardChange() }, 1200)
                }
            } else {
                handler.postDelayed({ watchForBoardChange() }, 1200)
            }
        }.start()
    }
}
