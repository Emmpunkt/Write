package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineLimits
import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.gcode.PlotJob
import de.emmpunkt.write.core.gcode.TravelLimits
import de.emmpunkt.write.core.gcode.WorkOffset
import de.emmpunkt.write.core.gcode.checkBounds
import de.emmpunkt.write.core.geometry.Polyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Ergebnis eines Not-Halts.
 *
 * [penLifted] ist bewusst getrennt von [stopped]: Anhalten gelingt praktisch immer, das
 * Anheben des Stifts nicht - danach steht die Maschine im Alarmzustand.
 */
data class StopResult(val stopped: Boolean, val penLifted: Boolean) {
    /** Was der Nutzer jetzt tun muss, oder null wenn alles in Ordnung ist. */
    val hint: String?
        get() = when {
            !stopped -> "Der Plotter liess sich nicht anhalten - bitte am Geraet ausschalten."
            !penLifted ->
                "Angehalten, aber der Stift konnte nicht angehoben werden: nach einem Not-Halt " +
                    "verweigert der Plotter Bewegungen, bis er wieder referenziert ist. " +
                    "Stift von Hand anheben, dann Homing ausfuehren."
            else -> null
        }
}

/** Achsen, die die App ansprechen kann. */
enum class Axis(val letter: Char) { X('X'), Y('Y'), Z('Z') }

/**
 * Die Schnittstelle der App zur Maschine: verbinden, fahren, nullen, plotten.
 *
 * Alle Sicherheitspruefungen sitzen hier und nicht in der Oberflaeche. Ein zweiter Aufrufer -
 * etwa ein spaeterer Stapelbetrieb - erbt sie damit automatisch, und sie lassen sich ohne
 * Oberflaeche testen.
 */
class MachineController(
    private val transport: Transport,
    /**
     * Liefert das JEWEILS GUELTIGE Profil - keine Kopie vom Zeitpunkt des Verbindens.
     *
     * Der Unterschied ist sicherheitsrelevant: aendert der Nutzer waehrend bestehender
     * Verbindung den Papier-Offset oder das Blattformat, entsteht der G-Code mit den neuen
     * Werten. Wuerde die Vorpruefung noch mit den alten rechnen, pruefte sie etwas anderes,
     * als gefahren wird - und liesse einen Auftrag durch, der ueber den Rand laeuft.
     */
    private val profileProvider: () -> MachineProfile,
    private val jogFeedMmMin: Int = 2000,
) {
    /** Bequemer Weg fuer Aufrufer mit festem Profil, vor allem Tests. */
    constructor(transport: Transport, profile: MachineProfile) : this(transport, { profile })

    private val profile: MachineProfile get() = profileProvider()

    private val statusParser = StatusParser()

    /**
     * Serialisiert JEDEN Zugriff, der eine Antwort von der Maschine liest.
     *
     * Auf der Verbindung gibt es nur einen Antwortstrom. Laufen Statusabfrage und ein Befehl
     * gleichzeitig, liest die eine Seite die Quittung der anderen weg - der Befehl wartet dann
     * vergeblich und meldet eine Zeitueberschreitung, obwohl die Maschine sauber geantwortet
     * hat. Genau das passiert beim schnellen Antippen der Fahrtasten.
     *
     * Realtime-Zeichen (Status-Anfrage, Feed Hold, Soft Reset, Jog-Abbruch) nehmen die Sperre
     * BEWUSST nicht: sie schreiben nur und muessen auch dann durchkommen, wenn gerade ein
     * Auftrag laeuft - sonst waere der Not-Halt wirkungslos.
     */
    private val transportLock = Mutex()

    private val _status = MutableStateFlow(MachineStatus.UNKNOWN)
    val status: StateFlow<MachineStatus> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /**
     * Ob in dieser Sitzung referenziert wurde.
     *
     * Ohne Homing sind die Maschinenkoordinaten und damit der Papier-Offset bedeutungslos -
     * der Text laege irgendwo. Deshalb ist es Voraussetzung fuer jeden Auftrag.
     */
    private val _homed = MutableStateFlow(false)
    val homed: StateFlow<Boolean> = _homed.asStateFlow()

    private val _limits = MutableStateFlow(MachineLimits.UNKNOWN)

    /**
     * Was die verbundene Maschine ueber sich selbst gemeldet hat.
     *
     * Wird beim Verbinden geholt, nicht auf Knopfdruck: eine Abfrage, die man zu druecken
     * vergessen kann, ist dieselbe Fehlerquelle wie ein fest eingetragener Wert. Sie kostet
     * nur Millisekunden und aendert sich im Betrieb nicht.
     *
     * Leer bedeutet *unbekannt*, nicht *unbegrenzt*: die Grenzpruefung faellt dann auf
     * `[0, workArea]` zurueck, was in die sichere Richtung irrt.
     */
    val limits: StateFlow<MachineLimits> = _limits.asStateFlow()

    suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            statusParser.reset()
            transport.connect()
            _connected.value = true
            _homed.value = false
            transportLock.withLock {
                drainBanner()
                fetchWorkOffset()
                // Einmal je Verbindung: die Achsenkonfiguration aendert sich im Betrieb nicht.
                fetchAxisSettings()
            }
            val status = requestStatus()
            "Verbunden (${transport.description}), Zustand: ${status.state}"
        }.onFailure {
            _connected.value = false
        }
    }

    fun disconnect() {
        runCatching { transport.close() }
        _connected.value = false
        _homed.value = false
        _status.value = MachineStatus.UNKNOWN
        // Die Grenzen gehoeren zu DIESER Maschine. Blieben sie stehen, rechnete die naechste
        // Verbindung mit fremden Werten - und die saehen richtig aus.
        _limits.value = MachineLimits.UNKNOWN
    }

    /** Fragt den Zustand ab und aktualisiert [status]. */
    suspend fun requestStatus(): MachineStatus = withContext(Dispatchers.IO) {
        transportLock.withLock { readStatus() }
    }

    private fun readStatus(): MachineStatus {
        transport.writeRealtime(Commands.STATUS_QUERY)
        val deadline = System.currentTimeMillis() + STATUS_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(200) ?: continue
            val parsed = statusParser.parse(line)
            if (parsed != null) {
                _status.value = parsed
                return parsed
            }
        }
        return _status.value
    }

    /** Statusabfrage im festen Takt, fuer die laufende Anzeige. */
    fun statusUpdates(intervalMs: Long = 500): Flow<MachineStatus> = flow {
        while (true) {
            emit(runCatching { requestStatus() }.getOrDefault(MachineStatus.UNKNOWN))
            kotlinx.coroutines.delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun jog(axis: Axis, deltaMm: Float): Result<Unit> =
        command(Commands.jog(axis.letter, deltaMm, jogFeedMmMin))

    suspend fun cancelJog(): Result<Unit> = realtime(Commands.JOG_CANCEL)

    /**
     * Referenzfahrt. Danach stimmen die Maschinenkoordinaten wieder mit der Wirklichkeit
     * ueberein, und der konfigurierte Papier-Offset trifft die richtige Stelle.
     */
    suspend fun home(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transportLock.withLock {
            transport.writeLine(Commands.HOME)
            awaitIdle(HOMING_TIMEOUT_MS)
            _homed.value = true
            // Nach der Referenzfahrt stimmt der alte Versatz nicht mehr.
            fetchWorkOffset()
            }
            Unit
        }
    }

    /** Setzt den Arbeitsnullpunkt auf die aktuelle Position. */
    suspend fun zeroXY(): Result<Unit> = commandThenRefreshOffset(
        Commands.zeroAxes(x = true, y = true, z = false),
    )

    suspend fun zeroZ(): Result<Unit> = commandThenRefreshOffset(
        Commands.zeroAxes(x = false, y = false, z = true),
    )

    /** Nullen veraendert G54 - der gemerkte Versatz muss danach neu geholt werden. */
    private suspend fun commandThenRefreshOffset(line: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                transportLock.withLock {
                    transport.writeLine(line)
                    awaitAcknowledgement()
                    fetchWorkOffset()
                }
                Unit
            }
        }

    suspend fun unlock(): Result<Unit> = command(Commands.UNLOCK)

    suspend fun feedHold(): Result<Unit> = realtime(Commands.FEED_HOLD)

    suspend fun resume(): Result<Unit> = realtime(Commands.CYCLE_START)

    /**
     * Not-Halt: haelt sofort an und versucht anschliessend, den Stift anzuheben.
     *
     * Der Ablauf ist durch die Firmware vorgegeben: Feed Hold stoppt die Bewegung, aber der
     * Rest des Auftrags liegt noch im Puffer - erst der Soft-Reset verwirft ihn. Danach steht
     * die Maschine im Alarmzustand und muss entsperrt werden.
     *
     * Ob das Anheben danach gelingt, ist NICHT sicher: sind Soft Limits aktiv (\$20=1) und
     * gilt die Maschine nach dem Reset als nicht referenziert, lehnt FluidNC jede Bewegung ab.
     * Deshalb wird die Antwort ausgewertet und ehrlich zurueckgemeldet, statt Erfolg zu
     * unterstellen - ein Nutzer, der faelschlich glaubt, der Stift sei oben, laesst ihn auf
     * dem Papier stehen.
     */
    suspend fun emergencyStop(): StopResult = withContext(Dispatchers.IO) {
        val angehalten = runCatching {
            transport.writeRealtime(Commands.FEED_HOLD)
            Thread.sleep(FEED_HOLD_SETTLE_MS)
            transport.writeRealtime(Commands.SOFT_RESET)
            Thread.sleep(RESET_SETTLE_MS)
        }.isSuccess

        _homed.value = false

        val gehoben = runCatching {
            // Nach dem Reset meldet sich die Firmware neu - diese Zeilen wegwerfen.
            drainBanner()
            transport.writeLine(Commands.UNLOCK)
            awaitAcknowledgement(SHORT_TIMEOUT_MS)
            transport.writeLine("G90")
            awaitAcknowledgement(SHORT_TIMEOUT_MS)
            transport.writeLine("G0 Z${formatMm(profile.zUpMm)}")
            awaitAcknowledgement(SHORT_TIMEOUT_MS)
        }.isSuccess

        StopResult(stopped = angehalten, penLifted = gehoben)
    }

    /** Hebt den Stift auf Sicherheitshoehe, ohne sonst etwas zu veraendern. */
    suspend fun liftPen(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transportLock.withLock {
                transport.writeLine("G90")
                awaitAcknowledgement()
                transport.writeLine("G0 Z${formatMm(profile.zUpMm)}")
                awaitAcknowledgement()
            }
        }
    }

    /**
     * Prueft einen Auftrag, bevor irgendetwas fahren kann.
     *
     * Die Liste ist leer, wenn gesendet werden darf. Jeder Eintrag ist ein Satz, der so in der
     * Oberflaeche stehen kann.
     */
    fun preflight(
        blattStrokes: List<Polyline>,
        status: MachineStatus = _status.value,
        istGehomt: Boolean = _homed.value,
    ): List<String> = buildList {
        if (!_connected.value) add("Keine Verbindung zum Plotter.")

        if (!istGehomt) {
            add("Der Plotter wurde noch nicht referenziert. Bitte zuerst Homing ausfuehren.")
        }

        if (_connected.value && !status.state.readyForJob) {
            add(
                when (status.state) {
                    MachineState.ALARM -> "Der Plotter ist im Alarmzustand. Bitte entsperren."
                    MachineState.RUN, MachineState.JOG -> "Der Plotter faehrt gerade."
                    MachineState.HOLD -> "Der Plotter ist angehalten."
                    MachineState.UNKNOWN -> "Zustand des Plotters unbekannt."
                    else -> "Der Plotter ist nicht bereit (${status.state})."
                },
            )
        }

        // Die eigentliche Grenzpruefung: lieber hier scheitern als an der Endlage.
        //
        // Der Arbeitsnullpunkt muss mit hinein. Die Firmware rechnet ihn auf jede gesendete
        // Koordinate auf, der Verfahrweg gilt aber ab dem Maschinennullpunkt - ohne ihn haelt
        // die Pruefung genau diese Millimeter faelschlich fuer fahrbar. Am Geraet des Nutzers
        // sind das 2 mm in X und Y.
        val machineStrokes = blattStrokes.map {
            it.translate(profile.paperOffsetXMm, profile.paperOffsetYMm)
        }
        addAll(
            checkBounds(
                machineStrokes,
                profile,
                arbeitsnullpunkt(),
                // Der wahre Verfahrweg, wenn er ausgelesen werden konnte - sonst der bisherige
                // Rueckfall [0, workArea].
                _limits.value.travel ?: TravelLimits.ofProfile(profile),
            ).violations,
        )
    }

    /**
     * Sendet einen geprueften Auftrag.
     *
     * Schlaegt die Vorpruefung fehl, wird nichts gesendet - die Maschine bewegt sich dann
     * ueberhaupt nicht.
     */
    fun plot(job: PlotJob, blattStrokes: List<Polyline>): Flow<SendProgress> =
        gepruefterAuftrag(job, blattStrokes) {
            StreamSender(transport, profile, statusParser)
        }

    /**
     * Sendet den Auftrag als Datei auf die SD-Karte und startet ihn dort.
     *
     * Der Auftrag ueberlebt danach einen Verbindungsabbruch - beim Streaming bliebe der Stift
     * dann mitten im Text auf dem Papier stehen.
     *
     * @param sdTransfer der Upload-Weg; `null`, wenn fuer diese Verbindung keiner eingerichtet
     *   ist - dann wird nichts gesendet und ehrlich gemeldet, statt still auf Telnet
     *   auszuweichen. Welcher Weg lief, muss der Nutzer wissen.
     */
    fun plotViaSd(
        job: PlotJob,
        blattStrokes: List<Polyline>,
        sdTransfer: SdTransfer?,
    ): Flow<SendProgress> {
        if (sdTransfer == null) {
            return flow {
                emit(
                    SendProgress.Failed(
                        "Fuer diesen Plotter ist kein SD-Upload eingerichtet.",
                        penLifted = true,
                    ),
                )
            }
        }
        return gepruefterAuftrag(job, blattStrokes) {
            SdSender(transport, sdTransfer, statusParser)
        }
    }

    /**
     * Gemeinsamer Rahmen beider Sendewege.
     *
     * Die Vorpruefung sitzt hier und nicht im jeweiligen Sender: ein zweiter Weg darf keine
     * zweite - womoeglich lueckenhafte - Sicherheitslogik mitbringen. Schlaegt sie fehl,
     * bewegt sich die Maschine ueberhaupt nicht.
     */
    private fun gepruefterAuftrag(
        job: PlotJob,
        blattStrokes: List<Polyline>,
        sender: () -> JobSender,
    ): Flow<SendProgress> {
        val probleme = preflight(blattStrokes)
        if (probleme.isNotEmpty()) {
            return flow {
                emit(SendProgress.Failed(probleme.joinToString(" "), penLifted = true))
            }
        }
        return flow {
            transportLock.withLock {
                emitAll(sender().send(job.lines, job.estimatedSeconds))
            }
        }.flowOn(Dispatchers.IO)
    }

    /** Listet die Dateien auf der SD-Karte. */
    suspend fun sdList(): Result<List<SdFile>> = withContext(Dispatchers.IO) {
        runCatching {
            transportLock.withLock {
                transport.writeLine(Commands.SD_LIST)
                val dateien = ArrayList<SdFile>()
                val deadline = System.currentTimeMillis() + SD_LIST_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    val line = transport.readLine(300) ?: continue
                    when (val r = Response.classify(line, statusParser)) {
                        is Response.Ok -> return@withLock dateien
                        is Response.Error -> throw IOException("SD-Liste abgelehnt (error:${r.code})")
                        else -> SdFile.parse(line)?.let { dateien += it }
                    }
                }
                throw IOException("Zeitueberschreitung beim Lesen der SD-Karte")
            }
        }
    }

    suspend fun sdDelete(path: String): Result<Unit> = command(Commands.sdDelete(path))

    private suspend fun command(line: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            transportLock.withLock {
                transport.writeLine(line)
                awaitAcknowledgement()
            }
        }
    }

    private suspend fun realtime(c: Char): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { transport.writeRealtime(c) }
    }

    /** Wartet auf ok; ein error oder ALARM wird zur Ausnahme, damit der Aufrufer es sieht. */
    private fun awaitAcknowledgement(timeoutMs: Long = COMMAND_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(200) ?: continue
            when (val r = Response.classify(line, statusParser)) {
                is Response.Ok -> return
                is Response.Error -> throw IOException("Befehl abgelehnt (error:${r.code})")
                is Response.Alarm -> throw IOException("Alarm ausgeloest (ALARM:${r.code})")
                is Response.Status -> _status.value = r.status
                is Response.Info -> Unit
            }
        }
        throw IOException("Keine Antwort vom Plotter")
    }

    /** Wartet, bis die Maschine wieder im Ruhezustand ist - fuer Homing die einzige Rueckmeldung. */
    private fun awaitIdle(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            transport.writeRealtime(Commands.STATUS_QUERY)
            val line = transport.readLine(500)
            if (line != null) {
                when (val r = Response.classify(line, statusParser)) {
                    is Response.Status -> {
                        _status.value = r.status
                        if (r.status.state == MachineState.IDLE) return
                    }
                    is Response.Alarm -> throw IOException("Homing fehlgeschlagen (ALARM:${r.code})")
                    is Response.Error -> throw IOException("Homing abgelehnt (error:${r.code})")
                    else -> Unit
                }
            }
        }
        throw IOException("Zeitueberschreitung beim Homing")
    }

    /**
     * Holt den Arbeitsversatz per `$#`.
     *
     * FluidNC v4 schickt WCO nicht im Statusbericht mit, deshalb muss er einmal aktiv geholt
     * werden - nach dem Verbinden, nach dem Homing und nach jedem Nullen, denn genau dann
     * aendert er sich.
     */
    suspend fun refreshWorkOffset(): Result<Position?> = withContext(Dispatchers.IO) {
        runCatching { transportLock.withLock { fetchWorkOffset() } }
    }

    /**
     * Der zuletzt geholte Arbeitsnullpunkt fuer die Grenzpruefung, oder `null`, wenn er
     * unbekannt ist.
     *
     * `null` fuehrt bewusst dazu, dass nicht gesendet werden darf. Ein angenommener Nullpunkt
     * waere schlimmer als gar keiner: er sieht richtig aus und laesst den Auftrag trotzdem in
     * den Anschlag fahren.
     */
    private fun arbeitsnullpunkt(): WorkOffset? =
        statusParser.workCoordinateOffset?.let { WorkOffset(it.x, it.y) }

    /**
     * Holt die Achsenkonfiguration per `$/axes/x` und `$/axes/y`.
     *
     * Daraus kommen zwei Groessen, die die App sonst raet: der wirklich fahrbare Bereich
     * (`mpos_mm` + `max_travel_mm`) und die Beschleunigung fuer die Zeitschaetzung.
     *
     * Beides ist eine Zugabe, kein Muss - eine Firmware, die die Abfrage nicht kennt,
     * quittiert einfach mit ok. Deshalb wird hier nichts geworfen: die Verbindung soll auch
     * dann zustande kommen, es gilt dann nur der Rueckfall.
     */
    private fun fetchAxisSettings() {
        val x = runCatching { readAxis('x') }.getOrNull()
        val y = runCatching { readAxis('y') }.getOrNull()
        // Z liefert keinen Verfahrweg (kein Endschalter, soft_limits: false), aber seine
        // Beschleunigung und ihren Hoechstvorschub - beide sind andere als bei XY.
        val z = runCatching { readAxis('z') }.getOrNull()

        val travelX = x?.travel
        val travelY = y?.travel
        _limits.value = MachineLimits(
            travel = if (travelX != null && travelY != null) {
                TravelLimits.of(travelX, travelY)
            } else {
                // Nur eine Achse zu kennen hilft nicht: die Pruefung braucht beide, und eine
                // halb bekannte Grenze waere schwerer zu deuten als gar keine.
                null
            },
            // Die langsamere der beiden Achsen bestimmt, wie lange eine Diagonale dauert.
            accelXYMmS2 = listOfNotNull(x?.accelMmS2, y?.accelMmS2).minOrNull(),
            accelZMmS2 = z?.accelMmS2,
            maxRateXYMmMin = listOfNotNull(x?.maxRateMmMin, y?.maxRateMmMin).minOrNull(),
            maxRateZMmMin = z?.maxRateMmMin,
        )
    }

    private fun readAxis(axis: Char): AxisSettings {
        transport.writeLine("\$/axes/$axis")
        val lines = ArrayList<String>()
        val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(300) ?: continue
            when (Response.classify(line, statusParser)) {
                is Response.Ok -> return AxisSettings.parse(lines)
                is Response.Error -> return AxisSettings.parse(emptyList())
                else -> lines += line
            }
        }
        return AxisSettings.parse(lines)
    }

    private fun fetchWorkOffset(): Position? {
        transport.writeLine("\$#")
        val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(300) ?: continue
            statusParser.applyOffsetReport(line)
            if (Response.classify(line, statusParser) is Response.Ok) break
        }
        return statusParser.workCoordinateOffset
    }

    /** Begruessungszeilen nach dem Verbindungsaufbau wegwerfen. */
    private fun drainBanner() {
        while (transport.readLine(300) != null) {
            // nur leeren
        }
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 2000L
        const val COMMAND_TIMEOUT_MS = 5000L

        /** Kurz gehalten: nach einem Not-Halt soll die Rueckmeldung schnell kommen. */
        const val SHORT_TIMEOUT_MS = 1500L
        const val FEED_HOLD_SETTLE_MS = 200L
        const val RESET_SETTLE_MS = 400L
        const val HOMING_TIMEOUT_MS = 120_000L

        /** Grosszuegig: die Karte des Nutzers traegt dutzende Dateien. */
        const val SD_LIST_TIMEOUT_MS = 10_000L

        fun formatMm(value: Float): String =
            String.format(java.util.Locale.ROOT, "%.3f", value)
                .trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}
