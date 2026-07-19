package com.example.ballsortai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        layout.addView(TextView(this).apply {
            text = "Ball Sort AI\n\nSiga os 2 passos abaixo:"
            textSize = 18f
        })

        layout.addView(Button(this).apply {
            text = "1. Ativar permissão de Sobrepor outros apps"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        })

        layout.addView(Button(this).apply {
            text = "2. Ativar o Serviço de Acessibilidade"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        layout.addView(TextView(this).apply {
            text = "\nDepois disso, abra o jogo Ball Sort. Vai aparecer um botão " +
                    "verde 'Iniciar' flutuando na tela. Toque nele (na primeira vez " +
                    "o Android vai pedir autorização para capturar a tela - aceite).\n\n" +
                    "A IA detecta os tubos, as cores e a capacidade sozinha, sem " +
                    "calibração manual, e joga até resolver o nível. Quando você " +
                    "avançar de nível no próprio jogo, ela percebe a mudança do " +
                    "tabuleiro (novas cores, mais ou menos tubos) e continua jogando " +
                    "sozinha, sem precisar apertar Iniciar de novo."
            textSize = 14f
        })

        setContentView(layout)
    }
}
