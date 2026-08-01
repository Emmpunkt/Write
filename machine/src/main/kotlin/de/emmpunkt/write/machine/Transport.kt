package de.emmpunkt.write.machine

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Verbindung zur Maschine. Alle Methoden blockieren; der Aufrufer sorgt fuer einen
 * passenden Dispatcher.
 *
 * Es gibt bewusst nur den Telnet-Weg. Die Weboberflaeche von FluidNC nimmt Befehle zwar unter
 * /command entgegen, liefert die Antworten aber ueber ihren WebSocket aus - eine HTTP-Anfrage
 * beantwortet sie mit "WebSocket dead". Ein HTTP-Transport waere damit ohne eigene
 * WebSocket-Anbindung wertlos, und fuer einen Auftrag ohnehin zu langsam: jede Zeile
 * braeuchte einen eigenen Umlauf.
 */
interface Transport : Closeable {
    val description: String

    fun connect()

    /** Sendet eine Befehlszeile inklusive Zeilenende. */
    fun writeLine(line: String)

    /**
     * Sendet ein Realtime-Zeichen ohne Zeilenende. Diese Zeichen umgehen den Empfangspuffer
     * und wirken auch dann noch, wenn die Maschine mit Zeilen ausgelastet ist.
     */
    fun writeRealtime(c: Char)

    /** Naechste Antwortzeile, oder null wenn innerhalb von [timeoutMs] keine kam. */
    fun readLine(timeoutMs: Long): String?
}

/**
 * Telnet-Verbindung (FluidNC-Vorgabe: Port 23).
 *
 * Der bevorzugte Weg: die Verbindung bleibt offen, Antworten treffen als Zeilenstrom ein, und
 * nur so laesst sich der ok-Handshake ohne Wartezeit pro Zeile fahren.
 */
class TelnetTransport(
    private val host: String,
    private val port: Int = 23,
    private val connectTimeoutMs: Int = 4000,
) : Transport {

    override val description = "Telnet $host:$port"

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    /**
     * Angefangene, noch unvollstaendige Zeile.
     *
     * Bewusst kein BufferedReader.readLine(): dessen Timeout-Verhalten wuerde bereits gelesene
     * Zeichen verwerfen. Beim Streaming gingen so Quittungen verloren, und der Sender bliebe
     * mit vollem Puffer stehen.
     */
    private val pending = StringBuilder()

    override fun connect() {
        close()
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.tcpNoDelay = true
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    override fun writeLine(line: String) {
        val out = output ?: throw IOException("Nicht verbunden")
        out.write((line + "\n").toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    override fun writeRealtime(c: Char) {
        val out = output ?: throw IOException("Nicht verbunden")
        out.write(c.code)
        out.flush()
    }

    override fun readLine(timeoutMs: Long): String? {
        val stream = input ?: throw IOException("Nicht verbunden")
        val s = socket ?: throw IOException("Nicht verbunden")

        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val newline = pending.indexOf("\n")
            if (newline >= 0) {
                val line = pending.substring(0, newline)
                pending.delete(0, newline + 1)
                return line.trimEnd('\r')
            }

            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null

            s.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            try {
                val b = stream.read()
                if (b < 0) throw IOException("Verbindung wurde von der Gegenstelle geschlossen")
                pending.append(b.toChar())
            } catch (_: SocketTimeoutException) {
                return null
            }
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        pending.setLength(0)
    }
}
