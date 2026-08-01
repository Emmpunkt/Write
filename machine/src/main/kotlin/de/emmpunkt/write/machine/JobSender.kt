package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.IOException
import java.util.ArrayDeque

/** Fortschritt eines laufenden Auftrags. */
sealed interface SendProgress {
    data class Started(val totalLines: Int, val estimatedSeconds: Float) : SendProgress

    data class Running(
        val sentLines: Int,
        val ackedLines: Int,
        val totalLines: Int,
    ) : SendProgress {
        val fraction: Float get() = if (totalLines == 0) 0f else ackedLines.toFloat() / totalLines
    }

    data object Completed : SendProgress

    /** Abbruch durch den Nutzer. [penLifted] sagt, ob der Stift noch angehoben werden konnte. */
    data class Aborted(val penLifted: Boolean) : SendProgress

    data class Failed(val message: String, val penLifted: Boolean) : SendProgress
}

/** Ein Auftrag laesst sich auf verschiedenen Wegen zur Maschine bringen. */
interface JobSender {
    fun send(lines: List<String>, estimatedSeconds: Float): Flow<SendProgress>
}

/**
 * Schickt den Auftrag Zeile fuer Zeile und wartet auf die Quittungen.
 *
 * Verfahren: Character Counting. Es werden so lange neue Zeilen nachgeschoben, wie die Summe
 * der noch unquittierten Bytes in den Empfangspuffer der Maschine passt. Jedes "ok" gibt die
 * Bytes der aeltesten Zeile wieder frei.
 *
 * Der Grund fuer den Aufwand: wuerde man Zeile fuer Zeile auf das "ok" warten, liefe der
 * Bewegungsplaner zwischen den Zeilen leer und der Stift bliebe an jedem Punkt kurz stehen -
 * bei einer Schreibschrift mit hunderten kurzen Segmenten waere das deutlich sichtbar.
 */
class StreamSender(
    private val transport: Transport,
    private val profile: MachineProfile,
    private val statusParser: StatusParser = StatusParser(),
) : JobSender {

    override fun send(lines: List<String>, estimatedSeconds: Float): Flow<SendProgress> = flow {
        emit(SendProgress.Started(lines.size, estimatedSeconds))

        val inFlight = ArrayDeque<Int>()
        var pendingBytes = 0
        var sent = 0
        var acked = 0

        /** Wartet auf die naechste Quittung. Gibt den Fehlertext zurueck, oder null wenn alles gut ist. */
        suspend fun awaitAck(): String? {
            while (true) {
                val line = transport.readLine(READ_TIMEOUT_MS)
                    ?: if (inFlight.isEmpty()) return null else continue

                when (val response = Response.classify(line, statusParser)) {
                    is Response.Ok -> {
                        if (inFlight.isNotEmpty()) pendingBytes -= inFlight.removeFirst()
                        acked++
                        emit(SendProgress.Running(sent, acked, lines.size))
                        return null
                    }
                    is Response.Error -> return "Maschine hat eine Zeile abgelehnt (error:${response.code})"
                    is Response.Alarm -> return "Maschine im Alarmzustand (ALARM:${response.code})"
                    // Statusberichte und Meldungen sind keine Quittungen.
                    is Response.Status, is Response.Info -> Unit
                }
            }
        }

        try {
            for (line in lines) {
                if (!currentCoroutineContext().isActive) {
                    emit(SendProgress.Aborted(penLifted = liftPen()))
                    return@flow
                }

                val bytes = line.length + 1
                while (pendingBytes + bytes > RX_BUFFER_BYTES && inFlight.isNotEmpty()) {
                    awaitAck()?.let { message ->
                        emit(SendProgress.Failed(message, penLifted = liftPen()))
                        return@flow
                    }
                }

                transport.writeLine(line)
                inFlight.addLast(bytes)
                pendingBytes += bytes
                sent++
                emit(SendProgress.Running(sent, acked, lines.size))
            }

            // Auf die restlichen Quittungen warten - sonst gilt der Auftrag als fertig,
            // waehrend die Maschine noch faehrt.
            while (inFlight.isNotEmpty()) {
                awaitAck()?.let { message ->
                    emit(SendProgress.Failed(message, penLifted = liftPen()))
                    return@flow
                }
            }

            emit(SendProgress.Completed)
        } catch (e: CancellationException) {
            // Abbruch durch den Nutzer: erst anhalten, dann den Stift vom Papier nehmen.
            liftPen()
            throw e
        } catch (e: IOException) {
            // Verbindung weg. Der Versuch, den Stift zu heben, schlaegt dann meist ebenfalls
            // fehl - der Nutzer muss es wissen, deshalb steht es in der Meldung.
            emit(SendProgress.Failed(e.message ?: "Verbindung verloren", penLifted = liftPen()))
        }
    }

    /**
     * Bringt die Maschine zum Stehen und hebt den Stift.
     *
     * Reihenfolge: Feed Hold haelt die Bewegung an, der Soft-Reset leert die Warteschlange
     * (sonst wuerde der Rest des Auftrags weiterlaufen), das Entsperren macht die Maschine
     * wieder bewegungsbereit, und erst dann laesst sich Z anheben.
     */
    private fun liftPen(): Boolean = runCatching {
        transport.writeRealtime(Commands.FEED_HOLD)
        Thread.sleep(FEED_HOLD_SETTLE_MS)
        transport.writeRealtime(Commands.SOFT_RESET)
        Thread.sleep(RESET_SETTLE_MS)

        // Nach dem Reset meldet sich die Firmware neu - diese Zeilen wegwerfen.
        while (transport.readLine(200) != null) Unit

        transport.writeLine(Commands.UNLOCK)
        awaitOk()
        transport.writeLine("G90")
        awaitOk()
        transport.writeLine("G0 Z${formatMm(profile.zUpMm)}")
        awaitOk()
        true
    }.getOrDefault(false)

    /**
     * Wartet auf die Quittung und wirft, wenn die Maschine ablehnt.
     *
     * Ohne diese Pruefung wuerde ein abgelehntes Anheben als Erfolg durchgehen - der Nutzer
     * glaubte dann, der Stift sei oben, waehrend er auf dem Papier steht.
     */
    private fun awaitOk() {
        val deadline = System.currentTimeMillis() + LIFT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(200) ?: continue
            when (Response.classify(line, statusParser)) {
                is Response.Ok -> return
                is Response.Error, is Response.Alarm ->
                    throw IOException("Plotter hat den Befehl abgelehnt")
                else -> Unit
            }
        }
        throw IOException("Keine Antwort beim Anheben des Stifts")
    }

    private companion object {
        /**
         * Empfangspuffer von GRBL. FluidNC hat mehr, aber dieser Wert ist mit jeder Firmware
         * sicher - ein zu grosser Wert liesse Zeichen still verlorengehen.
         */
        const val RX_BUFFER_BYTES = 127

        const val READ_TIMEOUT_MS = 10_000L
        const val FEED_HOLD_SETTLE_MS = 200L
        const val RESET_SETTLE_MS = 400L
        const val LIFT_TIMEOUT_MS = 1500L

        fun formatMm(value: Float): String =
            String.format(java.util.Locale.ROOT, "%.3f", value)
                .trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }
}
