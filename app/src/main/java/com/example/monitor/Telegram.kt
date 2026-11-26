package com.example.monitor

import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente mínimo da Bot API. Envia texto, documentos e lê comandos.
 */
object Telegram {

    private const val TAG = "Telegram"
    private const val BASE = "https://api.telegram.org/bot"
    private const val MAX_CHARS = 3800

    private fun api(method: String) = "$BASE${Config.BOT_TOKEN}/$method"

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    fun send(texto: String, chatId: String = Config.CHAT_ID): Boolean {
        if (!Config.configurado() || texto.isBlank()) return false
        var ok = true
        for (parte in fatiar(texto)) {
            ok = post("sendMessage", mapOf(
                "chat_id" to chatId,
                "text" to parte,
                "parse_mode" to "HTML",
                "disable_web_page_preview" to "true"
            )) && ok
        }
        return ok
    }

    fun sendFile(arquivo: File, legenda: String = ""): Boolean {
        if (!Config.configurado() || !arquivo.exists()) return false
        return postArquivo(arquivo, legenda)
    }

    fun getUpdates(offset: Long): List<Triple<Long, String, String>> {
        val saida = mutableListOf<Triple<Long, String, String>>()
        if (!Config.configurado()) return saida
        try {
            val conn = (URL("${api("getUpdates")}?timeout=0&offset=$offset")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 25000
            }
            val corpo = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val raiz = JSONObject(corpo)
            if (!raiz.optBoolean("ok")) return saida

            val resultado = raiz.optJSONArray("result") ?: return saida
            for (i in 0 until resultado.length()) {
                val upd = resultado.optJSONObject(i) ?: continue
                val id = upd.optLong("update_id", -1L)
                val msg = upd.optJSONObject("message") ?: continue
                val chat = msg.optJSONObject("chat")?.optString("id") ?: continue
                val texto = msg.optString("text")
                if (id >= 0 && texto.isNotBlank()) {
                    saida.add(Triple(id, chat, texto))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUpdates falhou", e)
        }
        return saida
    }

    private fun post(method: String, campos: Map<String, String>): Boolean {
        return try {
            val corpo = campos.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            val conn = (URL(api(method)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 45000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(corpo.toByteArray()) }
            val codigo = conn.responseCode
            if (codigo != 200) {
                Log.w(TAG, "HTTP $codigo em $method: ${conn.errorStream?.bufferedReader()?.readText()}")
            }
            conn.disconnect()
            codigo == 200
        } catch (e: Exception) {
            Log.e(TAG, "falha em $method", e)
            false
        }
    }

    private fun postArquivo(arquivo: File, legenda: String): Boolean {
        val fronteira = "----monitor${System.currentTimeMillis()}"
        return try {
            val conn = (URL(api("sendDocument")).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20000
                readTimeout = 120000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$fronteira")
            }

            DataOutputStream(conn.outputStream).use { out ->
                fun campo(nome: String, valor: String) {
                    out.writeBytes("--$fronteira\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$nome\"\r\n\r\n")
                    out.writeBytes("$valor\r\n")
                }

                campo("chat_id", Config.CHAT_ID)
                if (legenda.isNotBlank()) campo("caption", legenda)

                out.writeBytes("--$fronteira\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"document\"; filename=\"${arquivo.name}\"\r\n"
                )
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                FileInputStream(arquivo).use { it.copyTo(out) }
                out.writeBytes("\r\n--$fronteira--\r\n")
            }

            val codigo = conn.responseCode
            conn.disconnect()
            codigo == 200
        } catch (e: Exception) {
            Log.e(TAG, "envio de arquivo falhou", e)
            false
        }
    }

    private fun fatiar(texto: String): List<String> {
        if (texto.length <= MAX_CHARS) return listOf(texto)
        val partes = mutableListOf<String>()
        var atual = StringBuilder()
        for (linha in texto.lines()) {
            val pedaco = if (linha.length > MAX_CHARS) linha.chunked(MAX_CHARS) else listOf(linha)
            for (p in pedaco) {
                if (atual.length + p.length + 1 > MAX_CHARS) {
                    partes.add(atual.toString())
                    atual = StringBuilder()
                }
                atual.append(p).append('\n')
            }
        }
        if (atual.isNotBlank()) partes.add(atual.toString())
        return partes
    }
}
