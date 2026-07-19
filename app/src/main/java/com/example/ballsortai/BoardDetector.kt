package com.example.ballsortai

import android.graphics.Bitmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

object BoardDetector {

    data class DetectedTube(val tapX: Int, val tapY: Int, val colorsBottomToTop: List<Int>)
    data class DetectedBoard(val tubes: List<DetectedTube>, val capacity: Int)

    private data class TubeBox(val x0: Int, val x1: Int, val yTop: Int, val yBottom: Int)
    private data class RefinedTube(val cx: Int, val yTop: Int, val yBottom: Int)

    fun detect(bitmap: Bitmap): DetectedBoard? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val bg = backgroundColor(pixels)

        val boardTop = (h * Config.HEADER_FRACTION).toInt()
        val boardBottom = (h * (1 - Config.FOOTER_FRACTION)).toInt()
        if (boardBottom <= boardTop) return null

        val rowProfile = IntArray(boardBottom - boardTop)
        for (y in boardTop until boardBottom) {
            var count = 0
            var x = 0
            while (x < w) {
                if (colorDistance(pixels[y * w + x] and 0x00FFFFFF, bg) > Config.BG_THRESHOLD) count++
                x += 2
            }
            rowProfile[y - boardTop] = count
        }
        val rowThreshold = (w / 2) / 2
        val rowBands = findBands(rowProfile, rowThreshold).map { (a, b) -> Pair(a + boardTop, b + boardTop) }
        if (rowBands.isEmpty()) return null

        val tubeBoxes = ArrayList<TubeBox>()
        for ((bandTop, bandBottom) in rowBands) {
            val colProfile = IntArray(w)
            for (x in 0 until w) {
                var count = 0
                var y = bandTop
                while (y < bandBottom) {
                    if (colorDistance(pixels[y * w + x] and 0x00FFFFFF, bg) > Config.BG_THRESHOLD) count++
                    y += 2
                }
                colProfile[x] = count
            }
            val colThreshold = ((bandBottom - bandTop) / 2) / 2
            val colBands = findBands(colProfile, colThreshold)
            for ((x0, x1) in colBands) {
                if (x1 - x0 < w * 0.02) continue
                tubeBoxes.add(TubeBox(x0, x1, bandTop, bandBottom))
            }
        }
        if (tubeBoxes.isEmpty()) return null

        val refinedTubes = ArrayList<RefinedTube>()
        for (box in tubeBoxes) {
            val margin = (box.yBottom - box.yTop) / 4
            val searchTop = maxOf(0, box.yTop - margin)
            val searchBottom = minOf(h - 1, box.yBottom + margin)
            var yTop = -1
            var yBottom = -1
            for (y in searchTop..searchBottom) {
                var found = false
                var x = box.x0
                while (x <= box.x1) {
                    if (colorDistance(pixels[y * w + x] and 0x00FFFFFF, bg) > Config.BG_THRESHOLD) { found = true; break }
                    x += 2
                }
                if (found) { if (yTop == -1) yTop = y; yBottom = y }
            }
            if (yTop != -1 && yBottom - yTop > 10) {
                refinedTubes.add(RefinedTube((box.x0 + box.x1) / 2, yTop, yBottom))
            }
        }
        if (refinedTubes.isEmpty()) return null

        val diameters = ArrayList<Int>()
        for (t in refinedTubes) {
            var y = t.yBottom
            while (y >= t.yTop && colorDistance(pixels[y * w + t.cx] and 0x00FFFFFF, bg) <= Config.BG_THRESHOLD) y--
            if (y < t.yTop) continue
            val ballBottom = y
            while (y >= t.yTop && colorDistance(pixels[y * w + t.cx] and 0x00FFFFFF, bg) > Config.BG_THRESHOLD) y--
            val diameter = ballBottom - y
            if (diameter > 4) diameters.add(diameter)
        }
        if (diameters.isEmpty()) return null
        diameters.sort()
        val ballDiameter = diameters[diameters.size / 2]

        val votes = HashMap<Int, Int>()
        for (t in refinedTubes) {
            val cap = ((t.yBottom - t.yTop).toDouble() / ballDiameter).roundToInt().coerceAtLeast(1)
            votes[cap] = (votes[cap] ?: 0) + 1
        }
        val capacity = votes.maxByOrNull { it.value }?.key ?: return null

        val palette = ArrayList<Int>()
        val tubes = ArrayList<DetectedTube>()
        for (t in refinedTubes) {
            val colors = ArrayList<Int>()
            for (slot in 0 until capacity) {
                val sy = t.yBottom - slot * ballDiameter - ballDiameter / 2
                if (sy < t.yTop) break
                val py = sy.coerceIn(0, h - 1)
                val rgb = pixels[py * w + t.cx] and 0x00FFFFFF
                if (colorDistance(rgb, bg) <= Config.BG_THRESHOLD) break
                colors.add(matchOrCreatePalette(rgb, palette))
            }
            val tapY = (t.yTop + t.yBottom) / 2
            tubes.add(DetectedTube(t.cx, tapY, colors))
        }

        return DetectedBoard(tubes, capacity)
    }

    private fun findBands(profile: IntArray, threshold: Int): List<Pair<Int, Int>> {
        val bands = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in profile.indices) {
            if (profile[i] > threshold) {
                if (start == -1) start = i
            } else if (start != -1) {
                bands.add(Pair(start, i - 1))
                start = -1
            }
        }
        if (start != -1) bands.add(Pair(start, profile.size - 1))
        return bands
    }

    private fun backgroundColor(pixels: IntArray): Int {
        val counts = HashMap<Int, Int>()
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i] and 0x00FFFFFF
            val quant = ((p shr 16 and 0xFF) / 16 * 16 shl 16) or
                        ((p shr 8 and 0xFF) / 16 * 16 shl 8) or
                        ((p and 0xFF) / 16 * 16)
            counts[quant] = (counts[quant] ?: 0) + 1
            i += 7
        }
        return counts.maxByOrNull { it.value }?.key ?: 0x101833
    }

    private fun matchOrCreatePalette(rgb: Int, palette: ArrayList<Int>): Int {
        for ((idx, ref) in palette.withIndex()) {
            if (colorDistance(rgb, ref) < Config.COLOR_MATCH_THRESHOLD) return idx
        }
        palette.add(rgb)
        return palette.size - 1
    }

    private fun colorDistance(c1: Int, c2: Int): Double {
        val r1 = (c1 shr 16) and 0xFF; val g1 = (c1 shr 8) and 0xFF; val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF; val g2 = (c2 shr 8) and 0xFF; val b2 = c2 and 0xFF
        val dr = (r1 - r2).toDouble(); val dg = (g1 - g2).toDouble(); val db = (b1 - b2).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
