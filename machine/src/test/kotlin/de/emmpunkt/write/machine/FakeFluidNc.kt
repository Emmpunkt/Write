package de.emmpunkt.write.machine

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Ein FluidNC-Nachbau auf einem echten Socket.
 *
 * Bewusst ueber TCP und nicht als vorgetaeuschter Transport: so laufen Zeilenaufteilung,
 * Realtime-Zeichen und Zeitverhalten durch denselben Code wie am Geraet. Fehler, die nur beim
 * Zerlegen des Byte-Stroms auftreten, faende ein reiner Attrappen-Transport nicht.
 */
class FakeFluidNc(
    /** Zustand, den der Statusbericht meldet. */
    @Volatile var state: String = "Idle",
    /** Antwortet ab dieser Zeilennummer mit einem Fehler statt mit ok (1-basiert, 0 = nie). */
    @Volatile var failAtLine: Int = 0,
    /** Meldet ab dieser Zeilennummer einen Alarm (1-basiert, 0 = nie). */
    @Volatile var alarmAtLine: Int = 0,
    /** Kuenstliche Verzoegerung je Quittung, um langsame Abarbeitung nachzubilden. */
    @Volatile var ackDelayMs: Long = 0,
    /**
     * Lehnt nach einem Soft-Reset jede Bewegung ab - so verhaelt sich FluidNC mit aktiven
     * Soft Limits, solange die Maschine als nicht referenziert gilt.
     */
    @Volatile var rejectMovesAfterReset: Boolean = false,
    /**
     * Arbeitsnullpunkt (G54), den der Statusbericht als WCO meldet.
     *
     * Am Geraet des Nutzers steht er auf (2, 2) und nicht auf null - genau diese Verschiebung
     * verkuerzt den fahrbaren Weg gegenueber dem eingestellten Arbeitsbereich.
     */
    @Volatile var wco: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    /**
     * Ob der Statusbericht WCO mitschickt.
     *
     * Der Plotter des Nutzers tut das NICHT ($10=1) - dort ist die Abfrage per `$#` der
     * einzige Weg an den Arbeitsnullpunkt. Mit `false` laesst sich der Fall nachstellen,
     * in dem er unbekannt bleibt.
     */
    @Volatile var sendWco: Boolean = true,
    /**
     * Antwortblock auf `$/axes/x` und `$/axes/y`, Achsbuchstabe klein als Schluessel.
     *
     * Leer heisst: die Firmware kennt die Abfrage nicht und quittiert nur mit ok. Genau dann
     * muss die App auf ihre Vorgabewerte zurueckfallen, statt die Verbindung aufzugeben.
     */
    @Volatile var axisConfig: Map<Char, List<String>> = emptyMap(),
    /**
     * Wie viele Statusabfragen ein per `$SD/Run=` gestarteter Auftrag lang laeuft.
     *
     * Bildet nach, was am Geraet zu sehen war: waehrend des Laufs meldet der Statusbericht
     * `Run` und ein Feld `SD:<prozent>,<pfad>`, danach wieder `Idle`. Der Prozentwert steht
     * dabei sofort auf 100 - er ist der Lesefortschritt der Datei, nicht der der Bewegung.
     */
    @Volatile var sdRunPolls: Int = 3,
) : Closeable {

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    /**
     * Alle empfangenen Befehlszeilen, in Reihenfolge.
     *
     * CopyOnWriteArrayList, weil die Tests die Liste durchsuchen, waehrend der Server-Thread
     * noch schreibt. Eine synchronisierte Liste schuetzt zwar einzelne Zugriffe, nicht aber
     * das Durchlaufen - dort schlaegt sie mit ConcurrentModificationException fehl.
     */
    val received: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()

    /** Alle empfangenen Realtime-Zeichen. */
    val realtime: MutableList<Char> = java.util.concurrent.CopyOnWriteArrayList()

    /** Groesster Fuellstand des Empfangspuffers, den der Sender erzeugt hat. */
    val peakBufferBytes = AtomicInteger(0)

    private val connected = CountDownLatch(1)
    @Volatile private var running = true
    @Volatile private var wurdeZurueckgesetzt = false
    private var worker: Thread? = null

    /** Laufender SD-Auftrag: Pfad und wie viele Abfragen er noch dauert. */
    @Volatile private var sdLaufPfad: String? = null
    private val sdVerbleibend = AtomicInteger(0)

    init {
        worker = thread(isDaemon = true, name = "FakeFluidNc") {
            runCatching {
                server.accept().use { socket -> serve(socket) }
            }
        }
    }

    fun awaitConnection(timeoutMs: Long = 2000): Boolean =
        connected.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

    /**
     * Wartet, bis [quietMs] lang nichts mehr eingetroffen ist.
     *
     * Ein Test, der unmittelbar nach dem Ende des Sende-Flows prueft, kaeme dem Server-Thread
     * zuvor: die zuletzt geschriebenen Zeilen liegen dann noch im Socket und fehlen in
     * [received].
     */
    fun awaitQuiet(quietMs: Long = 300, timeoutMs: Long = 4000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSize = -1
        var lastChange = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val size = received.size
            if (size != lastSize) {
                lastSize = size
                lastChange = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - lastChange >= quietMs) {
                return
            }
            Thread.sleep(20)
        }
    }

    private fun serve(socket: Socket) {
        connected.countDown()
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        fun send(text: String) {
            output.write((text + "\n").toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        // Begruessung wie die echte Firmware.
        send("Grbl 1.1f ['\$' for help]")

        val line = StringBuilder()
        var lineNumber = 0

        /**
         * Empfangene, noch nicht quittierte Zeilen.
         *
         * Die echte Maschine liest ankommende Zeichen sofort in ihren Puffer und arbeitet sie
         * erst danach ab. Wuerde die Attrappe direkt beim Lesen quittieren, koennte sich nie
         * etwas anstauen - der Fuellstand des Empfangspuffers waere dann gar nicht messbar,
         * und ein Sender, der Zeile fuer Zeile wartet, faiele nicht auf.
         */
        val queue = java.util.concurrent.LinkedBlockingQueue<String>()
        val bufferedBytes = AtomicInteger(0)

        val acker = thread(isDaemon = true, name = "FakeFluidNc-ack") {
            while (running) {
                val cmd = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (ackDelayMs > 0) Thread.sleep(ackDelayMs)
                // Erst den Pufferplatz freigeben, dann quittieren: genau das bedeutet das "ok".
                // Andersherum entstuende ein Messfehler - der Sender darf auf das ok hin sofort
                // nachschieben, und der Zaehler stuende dann kurzzeitig zu hoch.
                bufferedBytes.addAndGet(-(cmd.length + 1))
                synchronized(output) {
                    when {
                        alarmAtLine > 0 && received.size >= alarmAtLine -> send("ALARM:1")
                        failAtLine > 0 && received.size >= failAtLine -> send("error:20")
                        rejectMovesAfterReset && wurdeZurueckgesetzt && cmd.startsWith("G0 Z") ->
                            send("error:9")
                        cmd.startsWith("\$/axes/") -> {
                            // Der Block kommt vor der Quittung, so wie bei der echten Firmware.
                            axisConfig[cmd.last().lowercaseChar()]?.forEach { send(it) }
                            send("ok")
                        }
                        cmd.startsWith("\$SD/Run=") -> {
                            sdLaufPfad = cmd.removePrefix("\$SD/Run=")
                            sdVerbleibend.set(sdRunPolls)
                            send("ok")
                        }
                        else -> send("ok")
                    }
                }
            }
        }

        try {
            while (running) {
                val b = input.read()
                if (b < 0) break
                val c = b.toChar()

                when {
                    // Realtime-Zeichen stehen ausserhalb des Zeilenprotokolls und werden
                    // sofort beantwortet, auch wenn noch Zeilen in der Warteschlange liegen.
                    c == Commands.STATUS_QUERY -> {
                        realtime += c
                        // Ein laufender SD-Auftrag geht nach der eingestellten Zahl von
                        // Abfragen zu Ende - so wie die echte Maschine irgendwann fertig ist.
                        val laufend = sdLaufPfad
                        val zustand = if (laufend != null) {
                            if (sdVerbleibend.decrementAndGet() <= 0) {
                                sdLaufPfad = null
                                "Idle"
                            } else {
                                "Run"
                            }
                        } else {
                            state
                        }
                        val sdFeld = if (laufend != null && zustand == "Run") {
                            "|SD:100.00,$laufend"
                        } else {
                            ""
                        }
                        synchronized(output) {
                            send(
                                if (sendWco) {
                                    "<$zustand|MPos:10.000,20.000,3.000|FS:0,0$sdFeld|" +
                                        "WCO:${wco.first},${wco.second},${wco.third}>"
                                } else {
                                    "<$zustand|MPos:10.000,20.000,3.000|FS:0,0$sdFeld>"
                                },
                            )
                        }
                    }
                    c == Commands.FEED_HOLD || c == Commands.CYCLE_START ||
                        c == Commands.SOFT_RESET || c == Commands.JOG_CANCEL -> {
                        realtime += c
                        if (c == Commands.SOFT_RESET) {
                            queue.clear()
                            bufferedBytes.set(0)
                            // Nach dem Reset ist die Maschine wieder ansprechbar.
                            failAtLine = 0
                            alarmAtLine = 0
                            wurdeZurueckgesetzt = true
                            synchronized(output) { send("Grbl 1.1f ['\$' for help]") }
                        }
                    }
                    c == '\n' -> {
                        val cmd = line.toString().trim()
                        line.setLength(0)
                        if (cmd.isNotEmpty()) {
                            received += cmd
                            lineNumber++
                            val fill = bufferedBytes.addAndGet(cmd.length + 1)
                            peakBufferBytes.updateAndGet { maxOf(it, fill) }
                            queue.put(cmd)
                        }
                    }
                    c == '\r' -> Unit
                    else -> line.append(c)
                }
            }
        } finally {
            running = false
            acker.interrupt()
        }
    }

    override fun close() {
        running = false
        runCatching { server.close() }
        worker?.interrupt()
    }
}
