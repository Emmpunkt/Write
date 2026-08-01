package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueft den Client gegen den ECHTEN Plotter.
 *
 * Laeuft nur, wenn die Adresse ueber die Systemeigenschaft `plotterHost` gesetzt wird:
 *   ./gradlew :machine:test -DplotterHost=192.168.2.18
 * Ohne die Angabe ueberspringen sich die Faelle, damit der normale Testlauf kein Netz braucht.
 *
 * Bewusst ausschliesslich LESENDE Befehle - Status und Arbeitsversatz. Nichts hier bewegt
 * eine Achse oder veraendert einen Zustand der Maschine.
 */
class LivePlotterTest {

    private val host: String? = System.getProperty("plotterHost")

    private fun controller(): MachineController? {
        val h = host ?: return null
        return MachineController(TelnetTransport(h, 23), MachineProfile())
    }

    @Test
    fun `verbindet sich und liest den Zustand`() = runTest {
        val c = controller() ?: return@runTest
        try {
            val info = c.connect().getOrThrow()
            println("Verbindung: $info")

            val status = c.requestStatus()
            println("Zustand: ${status.raw}")

            assertNotNull(status.machine, "Keine Maschinenposition erhalten")
            assertTrue(
                status.state != MachineState.UNKNOWN,
                "Zustand nicht erkannt: ${status.raw}",
            )
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun `ermittelt die Arbeitsposition ueber den Arbeitsversatz`() = runTest {
        val c = controller() ?: return@runTest
        try {
            c.connect().getOrThrow()
            val offset = c.refreshWorkOffset().getOrThrow()
            val status = c.requestStatus()

            println("Arbeitsversatz: $offset")
            println("Maschinenposition: ${status.machine}")
            println("Arbeitsposition:   ${status.work}")

            // Der eigentliche Nachweis: diese Firmware schickt WCO nicht im Statusbericht mit.
            // Ohne die Abfrage per $# bliebe die Arbeitsposition unbekannt.
            assertNotNull(offset, "Arbeitsversatz konnte nicht gelesen werden")
            assertNotNull(status.work, "Arbeitsposition wurde nicht berechnet")

            val machine = assertNotNull(status.machine)
            assertTrue(
                kotlin.math.abs((machine.z - offset.z) - status.work!!.z) < 0.001f,
                "Arbeitsposition passt nicht zur Rechnung Zw = Zm - WCO",
            )
        } finally {
            c.disconnect()
        }
    }
}
