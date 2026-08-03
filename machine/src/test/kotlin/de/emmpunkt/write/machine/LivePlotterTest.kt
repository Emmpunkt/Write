package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /**
     * Prueft das Antwortformat von `$/axes/x` gegen die echte Firmware.
     *
     * Der Parser dafuer ist gegen einen NACHGEBILDETEN Block geschrieben - der genaue
     * Schluesselvorrat unterscheidet sich zwischen den FluidNC-Fassungen. Dieser Fall ist die
     * einzige Stelle, an der sich zeigt, ob er beim Geraet des Nutzers wirklich greift.
     *
     * Geprueft wird BEWUSST nur, DASS die Werte gelesen wurden und in sich stimmig sind -
     * nicht, WELCHE es sind. Ein Test, der auf `mpos_mm: 3.0` besteht, waere derselbe Fehler
     * wie ein fest eingetragener Wert im Programm: er ginge beim naechsten Umkonfigurieren
     * kaputt, und bei einer anderen Maschine sowieso. Die Zahlen stehen deshalb nur im
     * Protokoll, zum Nachsehen.
     */
    @Test
    fun `liest den fahrbaren Bereich und die Beschleunigung der Achsen`() = runTest {
        val c = controller() ?: return@runTest
        try {
            c.connect().getOrThrow()

            val limits = c.limits.value.travel
            val accelXY = c.limits.value.accelXYMmS2
            val accelZ = c.limits.value.accelZMmS2
            println("Fahrbarer Bereich: $limits")
            println("Beschleunigung XY: $accelXY mm/s^2")
            println("Beschleunigung Z:  $accelZ mm/s^2")

            assertNotNull(
                limits,
                "Achsenkonfiguration nicht lesbar - der Parser passt nicht zum Antwortformat " +
                    "dieser Firmware. Ausgabe von \$/axes/x pruefen.",
            )
            assertTrue(
                limits.maxXMm > limits.minXMm && limits.maxYMm > limits.minYMm,
                "Der gelesene Verfahrweg ist nicht positiv: $limits",
            )
            assertNotNull(accelXY, "Beschleunigung fehlt - die Zeitschaetzung bleibt ungenau")
            assertNotNull(accelZ, "Z-Beschleunigung fehlt - die Stifthuebe waeren falsch geschaetzt")
        } finally {
            c.disconnect()
        }
    }

    /**
     * Passt der eingestellte Arbeitsnullpunkt zum fahrbaren Bereich?
     *
     * Kein Test des Programms, sondern eine Diagnose der MASCHINE - deshalb steht das Ergebnis
     * im Protokoll und nicht in einer Zusicherung. Liegt G54 unter der Untergrenze, ist schon
     * die Rueckfahrt `G0 X0 Y0` am Ende jedes Auftrags unfahrbar; liegt er genau darauf, ist
     * sie es gerade noch, aber ohne jede Reserve.
     */
    @Test
    fun `meldet den Abstand zwischen Arbeitsnullpunkt und Untergrenze`() = runTest {
        val c = controller() ?: return@runTest
        try {
            c.connect().getOrThrow()
            val limits = c.limits.value.travel ?: return@runTest
            val offset = c.refreshWorkOffset().getOrThrow() ?: return@runTest

            val reserveX = offset.x - limits.minXMm
            val reserveY = offset.y - limits.minYMm
            println("Reserve unter dem Arbeitsnullpunkt: X $reserveX mm, Y $reserveY mm")
            println(
                when {
                    reserveX < 0f || reserveY < 0f ->
                        "ACHTUNG: G54 liegt unter dem Verfahrweg - die Rueckfahrt am Ende " +
                            "jedes Auftrags loest ALARM:2 aus."
                    reserveX == 0f || reserveY == 0f ->
                        "G54 liegt genau auf der Untergrenze: fahrbar, aber ohne Reserve."
                    else -> "G54 liegt im fahrbaren Bereich."
                },
            )
        } finally {
            c.disconnect()
        }
    }

    /**
     * Prueft den selbstgebauten multipart-Rumpf gegen die ECHTE WebUI.
     *
     * Der Nachbau in `FakeWebUi` koennte toleranter sein als die Firmware - er ist schliesslich
     * gegen dieselbe Vorstellung geschrieben wie der Client. Erst dieser Fall zeigt, ob die
     * Form stimmt.
     *
     * Bewegt nichts: die Testdatei enthaelt keine einzige Fahranweisung, wird nur abgelegt,
     * nachgelesen und wieder geloescht. `$SD/Run=` kommt hier bewusst NICHT vor.
     */
    @Test
    fun `laedt eine Datei auf die echte SD-Karte und raeumt wieder auf`() = runTest {
        val h = host ?: return@runTest
        val name = "/write-probe.nc"
        // Keine Bewegung: nur Einheiten setzen und Programmende.
        val inhalt = "G21\nG90\n(write live probe)\nM2\n".toByteArray()

        HttpSdTransfer(h).upload(name, inhalt)

        val c = controller() ?: return@runTest
        try {
            c.connect().getOrThrow()

            val liste = c.sdList().getOrThrow()
            println("Dateien auf der Karte: ${liste.size}")
            val eintrag = liste.firstOrNull { it.name.endsWith("write-probe.nc") }
            assertNotNull(eintrag, "Die hochgeladene Datei fehlt auf der Karte: $liste")
            assertEquals(
                inhalt.size, eintrag.sizeBytes,
                "Die Datei kam mit falscher Groesse an - der multipart-Rumpf stimmt nicht",
            )
        } finally {
            runCatching { c.sdDelete(name) }
            c.disconnect()
        }
    }
}
