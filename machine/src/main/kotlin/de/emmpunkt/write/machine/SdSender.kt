package de.emmpunkt.write.machine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Bringt den Auftrag als Datei auf die SD-Karte und startet ihn dort.
 *
 * Der Unterschied zum [StreamSender] ist nicht die Geschwindigkeit, sondern die Abhaengigkeit:
 * ab dem Start arbeitet die Maschine aus ihrem eigenen Dateisystem. Reisst die WLAN-Verbindung,
 * laeuft der Auftrag weiter, statt mit aufliegendem Stift stehenzubleiben - bei einem Blatt,
 * das eine Viertelstunde braucht, ist das der eigentliche Gewinn.
 *
 * Der Not-Halt bleibt trotzdem erreichbar, solange die Verbindung steht: Realtime-Zeichen
 * umgehen den Zeilenpuffer und wirken auch waehrend eines SD-Laufs.
 *
 * ## Warum der Fortschritt so grob ist
 *
 * Am Geraet nachgemessen: das Feld `SD:<prozent>` im Statusbericht ist der LESEfortschritt der
 * Datei, nicht der der Bewegung. Bei einer kleinen Datei steht dort sofort 100 %, waehrend die
 * Achse noch faehrt. Gemeldet wird er trotzdem - bei einem Blatt mit hunderttausend Zeichen
 * ist er die einzige Zahl, die es gibt - aber das ENDE wird am Zustandswechsel erkannt und
 * nicht am Prozentwert.
 */
class SdSender(
    private val transport: Transport,
    private val sdTransfer: SdTransfer,
    private val statusParser: StatusParser,
    /** Fester Name: der Text liegt ohnehin in der App, eine Historie auf der Karte brauchte niemand. */
    private val fileName: String = "/write.nc",
    private val pollIntervalMs: Long = 500,
) : JobSender {

    override fun send(lines: List<String>, estimatedSeconds: Float): Flow<SendProgress> = flow {
        emit(SendProgress.Started(lines.size, estimatedSeconds))

        val inhalt = lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.US_ASCII)

        // Erst hochladen. Schlaegt das fehl, darf NICHTS gestartet werden - sonst liefe die
        // Datei vom letzten Mal los, und die gehoert zu einem anderen Text.
        try {
            sdTransfer.upload(fileName, inhalt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                SendProgress.Failed(
                    "Die Datei liess sich nicht auf die SD-Karte laden: ${e.message}. " +
                        "Es wurde nichts gestartet.",
                    penLifted = true,
                ),
            )
            return@flow
        }

        try {
            transport.writeLine(Commands.sdRun(fileName))
            quittungAbwarten()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(SendProgress.Failed("Der Plotter startete die Datei nicht: ${e.message}", true))
            return@flow
        }

        // Ab hier laeuft die Maschine allein. Verfolgt wird nur noch, ob sie noch laeuft.
        var lief = false
        while (true) {
            val status = statusAbfragen()
            val prozent = status?.sdRun?.percent

            when {
                status == null -> Unit
                status.state == MachineState.RUN || status.state == MachineState.HOLD -> {
                    lief = true
                    emit(
                        SendProgress.Running(
                            sentLines = lines.size,
                            ackedLines = anteilAlsZeilen(prozent, lines.size),
                            totalLines = lines.size,
                        ),
                    )
                }
                status.state == MachineState.ALARM -> {
                    emit(
                        SendProgress.Failed(
                            "Der Plotter ist waehrend des Auftrags in den Alarmzustand " +
                                "gegangen. Der Stift steht moeglicherweise noch auf dem Papier.",
                            penLifted = false,
                        ),
                    )
                    return@flow
                }
                // Idle nach einem Lauf heisst fertig. Idle VOR dem ersten Run-Bericht heisst,
                // dass die Maschine noch nicht angelaufen ist - dann weiter warten.
                lief && status.state == MachineState.IDLE -> {
                    emit(SendProgress.Completed)
                    return@flow
                }
            }
            delay(pollIntervalMs)
        }
    }

    /**
     * Rechnet den Lesefortschritt in eine Zeilenzahl um, damit die Oberflaeche dieselbe
     * Anzeige benutzen kann wie beim Streaming.
     *
     * Die Zahl ist bewusst als Schaetzung zu lesen - siehe die Anmerkung zum Prozentwert oben.
     */
    private fun anteilAlsZeilen(percent: Float?, total: Int): Int {
        if (percent == null) return 0
        return (total * (percent.coerceIn(0f, 100f) / 100f)).toInt()
    }

    private fun statusAbfragen(): MachineStatus? {
        transport.writeRealtime(Commands.STATUS_QUERY)
        val deadline = System.currentTimeMillis() + STATUS_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(200) ?: continue
            statusParser.parse(line)?.let { return it }
        }
        return null
    }

    private fun quittungAbwarten() {
        val deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(200) ?: continue
            when (val r = Response.classify(line, statusParser)) {
                is Response.Ok -> return
                is Response.Error -> throw IOException("abgelehnt (error:${r.code})")
                is Response.Alarm -> throw IOException("Alarm (ALARM:${r.code})")
                else -> Unit
            }
        }
        throw IOException("keine Antwort")
    }

    private companion object {
        const val STATUS_TIMEOUT_MS = 2000L
        const val COMMAND_TIMEOUT_MS = 5000L
    }
}
