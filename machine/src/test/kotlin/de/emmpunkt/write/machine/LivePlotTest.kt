package de.emmpunkt.write.machine

import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.gcode.applying
import de.emmpunkt.write.core.gcode.toPlotJob
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.layoutText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Schreibt WIRKLICH auf Papier - der einzige Test in diesem Projekt, der das tut.
 *
 * Laeuft deshalb nur, wenn BEIDE Eigenschaften gesetzt sind:
 *   ./gradlew :machine:test -PplotterHost=192.168.2.18 -PplotterPlot=true
 *
 * Die zweite ist Absicht: `-PplotterHost` allein startet die rein lesenden Live-Faelle, und
 * die sollen weiter gefahrlos laufen koennen. Ein Test, der den Stift aufsetzt, darf nicht
 * versehentlich mitlaufen.
 *
 * Voraussetzung am Geraet: Papier liegt auf, der Z-Nullpunkt ist gesetzt, und der
 * Arbeitsnullpunkt liegt auf oder ueber der Achsen-Untergrenze.
 */
class LivePlotTest {

    private val host: String? = System.getProperty("plotterHost")
    private val erlaubt: Boolean = System.getProperty("plotterPlot") == "true"

    @Test
    fun `plottet einen kurzen Text ueber die SD-Karte`() = runTest(timeout = kotlin.time.Duration.INFINITE) {
        val h = host ?: return@runTest
        if (!erlaubt) {
            println("Uebersprungen: -PplotterPlot=true fehlt (dieser Test schreibt auf Papier).")
            return@runTest
        }

        // A6 quer mit den Vorgabewerten der App.
        val frame = Frame(148f, 105f, Margins.all(8f))
        val laid = layoutText(
            "Hallo von der SD-Karte",
            TextStyle(fontId = Fonts.defaultId, sizeMm = 7f),
            frame,
            Fonts.load(Fonts.defaultId),
        )

        // Das Profil wird erst NACH dem Verbinden gebaut - mit den Werten, die die Maschine
        // gemeldet hat. Genau so macht es das ViewModel; eine Schaetzung aus Vorgabewerten
        // waere nicht die, die der Nutzer zu sehen bekommt.
        var profile = MachineProfile()
        val c = MachineController(TelnetTransport(h, 23), profileProvider = { profile })
        try {
            println("Verbinde: ${c.connect().getOrThrow()}")
            println("Grenzen:  ${c.limits.value.travel}")
            println("Gelesen:  ${c.limits.value}")
            println("Nullpunkt: ${c.refreshWorkOffset().getOrThrow()}")

            profile = MachineProfile().applying(c.limits.value)
            val job = laid.toPlotJob(profile)
            println("Auftrag:  ${job.lines.size} Zeilen, ${job.penDownCount} Huebe, " +
                "geschaetzt ${job.estimatedSeconds.toInt()} s")

            println("Referenzfahrt...")
            c.home().getOrThrow()

            val beginn = System.currentTimeMillis()
            var letzterAnteil = -1
            var ergebnis: SendProgress? = null

            c.plotViaSd(job, laid.strokes, HttpSdTransfer(h)).collect { fortschritt ->
                when (fortschritt) {
                    is SendProgress.Started ->
                        println("Gestartet (${fortschritt.totalLines} Zeilen)")
                    is SendProgress.Running -> {
                        val anteil = (fortschritt.fraction * 100).toInt()
                        if (anteil != letzterAnteil) {
                            letzterAnteil = anteil
                            println("  gelesen $anteil %")
                        }
                    }
                    else -> ergebnis = fortschritt
                }
            }

            val dauer = (System.currentTimeMillis() - beginn) / 1000
            println("Ergebnis: $ergebnis")
            println("Gebraucht: $dauer s, geschaetzt waren ${job.estimatedSeconds.toInt()} s")

            assertTrue(
                ergebnis is SendProgress.Completed,
                "Der Auftrag lief nicht sauber durch: $ergebnis",
            )

            val status = c.requestStatus()
            println("Endzustand: ${status.raw}")
        } finally {
            c.disconnect()
        }
    }
}
