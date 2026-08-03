package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.gcode.PlotJob
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Der Weg ueber die SD-Karte.
 *
 * Wichtigste Eigenschaft: er erbt dieselben Sicherheitsregeln wie das Streaming. Ein zweiter
 * Sendeweg mit eigener - womoeglich lueckenhafter - Vorpruefung waere genau die Art Abkuerzung,
 * die spaeter ein Blatt kostet.
 */
class SdSenderTest {

    private val profile = MachineProfile(workAreaXMm = 155f, workAreaYMm = 105f)

    private val job = PlotJob(
        lines = listOf("G21", "G90", "G0 X10 Y10", "G1 Z-1.5 F600", "G1 X20 Y20", "M2"),
        bounds = null,
        drawLengthMm = 14f,
        travelLengthMm = 14f,
        penDownCount = 1,
        estimatedSeconds = 3f,
    )
    private val blattStrokes = listOf(Polyline(listOf(Point(10f, 10f), Point(20f, 20f))))

    /** Ein Upload-Weg, der protokolliert statt zu netzwerken. */
    private class NotizTransfer(private val fehler: Exception? = null) : SdTransfer {
        val hochgeladen = mutableListOf<Pair<String, ByteArray>>()
        override val description = "Notiz"
        override fun upload(name: String, content: ByteArray) {
            fehler?.let { throw it }
            hochgeladen += name to content
        }
    }

    private fun controller(fake: FakeFluidNc, offsetNull: Boolean = true) =
        MachineController(TelnetTransport("127.0.0.1", fake.port), profile).also {
            if (!offsetNull) Unit
        }

    @Test
    fun `laedt hoch und startet die Datei`() = runTest {
        FakeFluidNc(wco = Triple(0f, 0f, 0f)).use { fake ->
            val transfer = NotizTransfer()
            val c = controller(fake)
            try {
                c.connect().getOrThrow()
                c.home().getOrThrow()

                val schritte = c.plotViaSd(job, blattStrokes, transfer).toList()

                assertEquals(1, transfer.hochgeladen.size, "Es wurde nicht genau eine Datei geladen")
                val (name, inhalt) = transfer.hochgeladen.single()
                assertEquals("/write.nc", name)
                assertTrue(
                    inhalt.decodeToString().startsWith("G21\nG90\n"),
                    "Der Dateiinhalt ist nicht der Auftrag",
                )
                assertTrue(
                    inhalt.decodeToString().trimEnd().endsWith("M2"),
                    "Der Auftrag endet nicht mit M2",
                )

                fake.awaitQuiet()
                assertTrue(
                    fake.received.any { it == "\$SD/Run=/write.nc" },
                    "Die Datei wurde nicht gestartet: ${fake.received}",
                )
                assertTrue(
                    schritte.last() is SendProgress.Completed,
                    "Auftrag nicht als fertig gemeldet: ${schritte.last()}",
                )
            } finally {
                c.disconnect()
            }
        }
    }

    @Test
    fun `ein misslungener Upload startet nichts`() = runTest {
        // Die gefaehrlichste Verwechslung: schlaegt der Upload fehl und wird trotzdem
        // gestartet, plottet die Maschine die Datei vom LETZTEN Mal - also einen anderen Text.
        FakeFluidNc(wco = Triple(0f, 0f, 0f)).use { fake ->
            val transfer = NotizTransfer(IOException("Karte voll"))
            val c = controller(fake)
            try {
                c.connect().getOrThrow()
                c.home().getOrThrow()

                val schritte = c.plotViaSd(job, blattStrokes, transfer).toList()

                fake.awaitQuiet()
                assertFalse(
                    fake.received.any { it.startsWith("\$SD/Run") },
                    "Nach einem fehlgeschlagenen Upload wurde trotzdem gestartet!",
                )
                val letzter = schritte.last()
                assertTrue(letzter is SendProgress.Failed, "Fehler nicht gemeldet: $letzter")
                assertTrue(
                    (letzter as SendProgress.Failed).message.contains("Karte voll"),
                    "Die Meldung nennt den Grund nicht: ${letzter.message}",
                )
            } finally {
                c.disconnect()
            }
        }
    }

    @Test
    fun `ohne Homing wird auch ueber SD nichts gesendet`() = runTest {
        // Der Kern: die Vorpruefung gilt fuer BEIDE Wege. Ohne Referenzfahrt ist der
        // Papier-Offset bedeutungslos und der Text laege irgendwo.
        FakeFluidNc(wco = Triple(0f, 0f, 0f)).use { fake ->
            val transfer = NotizTransfer()
            val c = controller(fake)
            try {
                c.connect().getOrThrow()

                val schritte = c.plotViaSd(job, blattStrokes, transfer).toList()

                assertTrue(transfer.hochgeladen.isEmpty(), "Trotz fehlendem Homing hochgeladen")
                val letzter = schritte.single()
                assertTrue(letzter is SendProgress.Failed)
                assertTrue(
                    (letzter as SendProgress.Failed).message.contains("referenziert"),
                    "Meldung nennt den Grund nicht: ${letzter.message}",
                )
            } finally {
                c.disconnect()
            }
        }
    }

    @Test
    fun `ein Auftrag ausserhalb des Verfahrwegs wird auch ueber SD abgewiesen`() = runTest {
        FakeFluidNc(wco = Triple(0f, 0f, 0f)).use { fake ->
            val transfer = NotizTransfer()
            val c = controller(fake)
            try {
                c.connect().getOrThrow()
                c.home().getOrThrow()

                // Weit jenseits der 155 mm in X.
                val zuWeit = listOf(Polyline(listOf(Point(10f, 10f), Point(400f, 20f))))
                val schritte = c.plotViaSd(job, zuWeit, transfer).toList()

                assertTrue(transfer.hochgeladen.isEmpty(), "Auftrag ausserhalb wurde hochgeladen")
                assertTrue(schritte.single() is SendProgress.Failed)
            } finally {
                c.disconnect()
            }
        }
    }

    @Test
    fun `ohne eingerichteten Upload wird nicht heimlich gestreamt`() = runTest {
        // Der Nutzer hat zwei Knoepfe und muss wissen, welcher Weg lief. Ein stiller
        // Rueckfall auf Telnet waere genau die Art Ueberraschung, die niemand will.
        FakeFluidNc(wco = Triple(0f, 0f, 0f)).use { fake ->
            val c = controller(fake)
            try {
                c.connect().getOrThrow()
                c.home().getOrThrow()

                val schritte = c.plotViaSd(job, blattStrokes, sdTransfer = null).toList()

                fake.awaitQuiet()
                assertFalse(
                    fake.received.any { it.startsWith("G1 X") },
                    "Es wurde still auf Telnet ausgewichen: ${fake.received}",
                )
                assertTrue(schritte.single() is SendProgress.Failed)
            } finally {
                c.disconnect()
            }
        }
    }
}
