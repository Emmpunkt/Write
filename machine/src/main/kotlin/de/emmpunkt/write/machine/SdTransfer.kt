package de.emmpunkt.write.machine

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Legt eine Datei auf der SD-Karte des Plotters ab.
 *
 * Warum HTTP, wo doch sonst alles ueber Telnet laeuft: Der Zeilenkanal von FluidNC ist fuer
 * Befehle gedacht, nicht fuer Dateien - ein Auftrag mit tausenden Zeilen braeuchte fuer jede
 * eine Quittung. Die WebUI hat dafuer einen eigenen Endpunkt.
 *
 * Am 2026-08-03 am Geraet nachgeprueft: `POST /upload` antwortet mit 200 und legt die Datei
 * richtig ab, OBWOHL `/command` weiterhin `WebSocket dead` liefert. Der frueher gezogene
 * Schluss "HTTP ist bei diesem Geraet wertlos" gilt also nur fuer Befehle.
 *
 * Bewusst ohne HTTP-Bibliothek: fuer einen einzigen multipart-POST waere eine Abhaengigkeit
 * unverhaeltnismaessig, zumal OkHttp aus diesem Projekt schon einmal entfernt wurde.
 */
interface SdTransfer {
    /**
     * Laedt [content] unter [name] hoch und ueberschreibt eine gleichnamige Datei.
     *
     * Wirft bei jedem Misserfolg - der Aufrufer darf danach NICHT `$SD/Run=` schicken, sonst
     * liefe die vorige Datei los.
     */
    fun upload(name: String, content: ByteArray)

    val description: String
}

class HttpSdTransfer(
    private val host: String,
    private val port: Int = 80,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 30_000,
) : SdTransfer {

    override val description: String get() = "HTTP $host:$port"

    override fun upload(name: String, content: ByteArray) {
        val path = if (name.startsWith("/")) name else "/$name"
        val boundary = "----WriteBoundary${System.nanoTime()}"
        val body = multipartBody(boundary, path, content)

        val connection = (URL("http://$host:$port/upload").openConnection() as HttpURLConnection)
        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            // Ohne feste Laenge puffert die Verbindung den ganzen Auftrag im Speicher; bei
            // einem Blatt sind das schnell hunderte Kilobyte.
            setFixedLengthStreamingMode(body.size)
        }

        try {
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val fehler = runCatching {
                    connection.errorStream?.readBytes()?.decodeToString()
                }.getOrNull().orEmpty().take(200)
                throw IOException("Der Plotter lehnte die Datei ab (HTTP $code) $fehler".trim())
            }
            // Antwort leeren, damit die Verbindung wiederverwendbar bleibt.
            runCatching { connection.inputStream.use { it.readBytes() } }
        } catch (e: IOException) {
            // Android blockiert Klartext-Verkehr ueber seine HTTP-Stacks seit API 28. Die
            // Meldung des Systems nennt zwar die Ursache, klingt aber nach einem Netzfehler -
            // am Geraet ist genau das passiert. Hier wird gesagt, was zu tun ist.
            if (e.message?.contains("Cleartext", ignoreCase = true) == true) {
                throw IOException(
                    "Android hat die unverschluesselte Verbindung zu $host blockiert. " +
                        "Der App fehlt die Freigabe fuer Klartext-Verkehr " +
                        "(res/xml/network_security_config.xml).",
                    e,
                )
            }
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Baut den multipart-Rumpf so, wie ihn die WebUI erwartet.
     *
     * Die Feldnamen sind nicht frei gewaehlt: `path` als Zielverzeichnis und der Dateiname mit
     * fuehrendem Schraegstrich sind genau die Form, die am Geraet funktioniert hat.
     */
    private fun multipartBody(boundary: String, path: String, content: ByteArray): ByteArray {
        val verzeichnis = path.substringBeforeLast('/', "").ifEmpty { "/" }
        val kopf = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"path\"\r\n\r\n")
            append("$verzeichnis\r\n")
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"$path\"\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val fuss = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        return kopf + content + fuss
    }
}
