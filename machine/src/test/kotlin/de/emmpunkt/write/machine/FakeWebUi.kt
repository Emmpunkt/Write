package de.emmpunkt.write.machine

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Ein Nachbau des Upload-Endpunkts der FluidNC-WebUI, auf einem echten Socket.
 *
 * Wie [FakeFluidNc] bewusst ueber TCP: so laeuft der multipart-Rumpf durch dieselbe
 * Byte-Verarbeitung wie am Geraet. Ein vorgetaeuschter Client wuerde genau die Fehler nicht
 * finden, die beim Zusammensetzen des Rumpfes entstehen.
 */
class FakeWebUi(
    /** HTTP-Code, mit dem geantwortet wird. */
    @Volatile var responseCode: Int = 200,
) : Closeable {

    private val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    /** Was der Server als hochgeladene Datei verstanden hat: Name -> Inhalt. */
    val uploads: MutableMap<String, ByteArray> = java.util.concurrent.ConcurrentHashMap()

    /** Der Wert des Feldes `path` der letzten Anfrage. */
    @Volatile var lastPathField: String? = null

    @Volatile private var running = true

    init {
        thread(isDaemon = true, name = "FakeWebUi") {
            while (running) {
                runCatching { server.accept().use { serve(it) } }.getOrNull() ?: break
            }
        }
    }

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val alles = input.readNBytesUntilQuiet()
        val text = alles.toString(Charsets.ISO_8859_1)

        val boundary = Regex("boundary=(\\S+)").find(text)?.groupValues?.get(1)
        if (boundary != null) {
            zerlege(alles, boundary)
        }

        val body = """{"files":[],"status":"ok"}"""
        val antwort = buildString {
            append("HTTP/1.1 $responseCode ${if (responseCode < 300) "OK" else "Error"}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${body.length}\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
        socket.getOutputStream().apply {
            write(antwort.toByteArray(Charsets.ISO_8859_1))
            flush()
        }
    }

    /**
     * Zerlegt den multipart-Rumpf.
     *
     * Auf ISO_8859_1 gestuetzt, weil dort jedes Byte auf genau ein Zeichen abbildet - der
     * Dateiinhalt kaeme sonst veraendert wieder heraus.
     */
    private fun zerlege(raw: ByteArray, boundary: String) {
        val text = raw.toString(Charsets.ISO_8859_1)
        val teile = text.split("--$boundary")
        for (teil in teile) {
            val trenn = teil.indexOf("\r\n\r\n")
            if (trenn < 0) continue
            val kopf = teil.substring(0, trenn)
            val inhalt = teil.substring(trenn + 4).removeSuffix("\r\n")

            val name = Regex("name=\"([^\"]*)\"").find(kopf)?.groupValues?.get(1) ?: continue
            val dateiname = Regex("filename=\"([^\"]*)\"").find(kopf)?.groupValues?.get(1)

            if (dateiname != null) {
                uploads[dateiname] = inhalt.toByteArray(Charsets.ISO_8859_1)
            } else if (name == "path") {
                lastPathField = inhalt
            }
        }
    }

    /** Liest, bis kurz nichts mehr kommt - reicht fuer eine einzelne Anfrage. */
    private fun java.io.InputStream.readNBytesUntilQuiet(): ByteArray {
        val puffer = java.io.ByteArrayOutputStream()
        val block = ByteArray(8192)
        val ende = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < ende) {
            if (available() > 0) {
                val n = read(block)
                if (n < 0) break
                puffer.write(block, 0, n)
            } else if (puffer.size() > 0) {
                // Etwas ist da und es kommt nichts mehr nach.
                Thread.sleep(60)
                if (available() == 0) break
            } else {
                Thread.sleep(20)
            }
        }
        return puffer.toByteArray()
    }

    override fun close() {
        running = false
        runCatching { server.close() }
    }
}
