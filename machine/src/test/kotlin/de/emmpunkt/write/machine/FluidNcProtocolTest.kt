package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Geprueft wird gegen Antworten, die vom Plotter des Nutzers stammen
 * (FluidNC v4.0.3, ausgelesen ueber Telnet) - nicht gegen erdachte Beispiele.
 */
class FluidNcProtocolTest {

    private val echterStatus = "<Idle|MPos:11.000,22.000,-6.750|FS:0,0>"
    private val echteOffsets = "[G54:11.000,22.000,-10.750]"

    @Test
    fun `liest den echten Statusbericht`() {
        val status = assertNotNull(StatusParser().parse(echterStatus))

        assertEquals(MachineState.IDLE, status.state)
        assertEquals(Position(11f, 22f, -6.75f), status.machine)
    }

    @Test
    fun `ohne WCO bleibt die Arbeitsposition unbekannt statt falsch`() {
        // Diese Firmware schickt WCO nicht im Statusbericht mit ($10=1). Eine geratene
        // Arbeitsposition waere schlimmer als gar keine - der Nutzer wuerde ihr vertrauen.
        val status = assertNotNull(StatusParser().parse(echterStatus))
        assertNull(status.work)
    }

    @Test
    fun `nach der Offset-Abfrage stimmt die Arbeitsposition`() {
        val parser = StatusParser()
        assertTrue(parser.applyOffsetReport(echteOffsets), "Offsetzeile nicht erkannt")

        val status = assertNotNull(parser.parse(echterStatus))

        // Nachgerechnet am Geraet: Zw = Zm - WCO = -6.750 - (-10.750) = 4.000
        assertEquals(Position(0f, 0f, 4f), status.work)
    }

    @Test
    fun `ignoriert Offsetzeilen anderer Koordinatensysteme`() {
        val parser = StatusParser()
        assertFalse(parser.applyOffsetReport("[G55:1.000,2.000,3.000]"))
        assertFalse(parser.applyOffsetReport("[TLO:0.000,0.000,0.000]"))
        assertNull(parser.workCoordinateOffset)
    }

    @Test
    fun `erkennt alle Betriebszustaende`() {
        val parser = StatusParser()
        val faelle = mapOf(
            "<Run|MPos:1.000,2.000,3.000|FS:500,0>" to MachineState.RUN,
            "<Jog|MPos:1.000,2.000,3.000|FS:500,0>" to MachineState.JOG,
            "<Home|MPos:0.000,0.000,0.000>" to MachineState.HOME,
            "<Hold:0|MPos:1.000,2.000,3.000>" to MachineState.HOLD,
            "<Alarm|MPos:1.000,2.000,3.000>" to MachineState.ALARM,
        )
        faelle.forEach { (line, expected) ->
            assertEquals(expected, assertNotNull(parser.parse(line)).state, line)
        }
    }

    @Test
    fun `nur Idle gilt als startbereit`() {
        assertTrue(MachineState.IDLE.readyForJob)
        listOf(
            MachineState.RUN, MachineState.JOG, MachineState.HOLD,
            MachineState.ALARM, MachineState.HOME, MachineState.UNKNOWN,
        ).forEach { assertFalse(it.readyForJob, "$it darf nicht startbereit sein") }
    }

    @Test
    fun `ordnet Antwortzeilen richtig ein`() {
        val parser = StatusParser()
        assertTrue(Response.classify("ok", parser) is Response.Ok)
        assertEquals("20", (Response.classify("error:20", parser) as Response.Error).code)
        assertEquals("1", (Response.classify("ALARM:1", parser) as Response.Alarm).code)
        assertTrue(Response.classify("Grbl 1.1f ['\$' for help]", parser) is Response.Info)
        assertTrue(Response.classify("[MSG:Machine: ESP32 Dev Controller V4]", parser) is Response.Info)
        assertTrue(Response.classify(echterStatus, parser) is Response.Status)
    }

    @Test
    fun `Jog-Befehle benutzen Punkt als Dezimaltrennzeichen`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("\$J=G91 G21 X-2.5 F1000", Commands.jog('X', -2.5f, 1000))
            assertEquals("\$J=G91 G21 Y0.1 F1000", Commands.jog('y', 0.1f, 1000))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `Nullen benutzt G10 L20 und laesst Z in Ruhe`() {
        // G10 L20 statt G92: der Versatz ueberlebt einen Soft-Reset. Z bleibt aussen vor,
        // weil die Schreibhoehe nicht versehentlich verstellt werden soll.
        assertEquals("G10 L20 P0 X0 Y0", Commands.zeroAxes(x = true, y = true, z = false))
        assertEquals("G10 L20 P0 Z0", Commands.zeroAxes(x = false, y = false, z = true))
    }
}

/**
 * Die Vorpruefung ist die letzte Instanz vor einer Bewegung. Am Geraet sind Soft Limits aktiv
 * ($20=1), ein zu grosser Auftrag loeste dort einen Alarm aus - moeglicherweise mitten im Text
 * und mit aufliegendem Stift.
 */
class PreflightTest {

    /** Arbeitsbereich wie am Geraet ausgelesen: $130=155, $131=105. */
    private val profile = MachineProfile(workAreaXMm = 155f, workAreaYMm = 105f)

    private var originalLocale: Locale? = null

    @BeforeTest
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @AfterTest
    fun tearDown() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    private fun controller(fake: FakeFluidNc) =
        MachineController(TelnetTransport("127.0.0.1", fake.port), profile)

    private fun strokes(maxX: Float, maxY: Float) =
        listOf(Polyline(listOf(Point(5f, 5f), Point(maxX, maxY))))

    @Test
    fun `ohne Verbindung wird nichts gesendet`() {
        FakeFluidNc().use { fake ->
            val probleme = controller(fake).preflight(strokes(100f, 80f))
            assertTrue(probleme.any { it.contains("Keine Verbindung") }, probleme.toString())
        }
    }

    @Test
    fun `ohne Homing wird nichts gesendet`() = kotlinx.coroutines.test.runTest {
        FakeFluidNc().use { fake ->
            val c = controller(fake)
            c.connect().getOrThrow()

            val probleme = c.preflight(strokes(100f, 80f))
            assertTrue(
                probleme.any { it.contains("referenziert") },
                "Homing-Pflicht nicht durchgesetzt: $probleme",
            )
            c.disconnect()
        }
    }

    @Test
    fun `Auftrag ausserhalb des Arbeitsbereichs wird abgelehnt`() = kotlinx.coroutines.test.runTest {
        FakeFluidNc().use { fake ->
            val c = controller(fake)
            c.connect().getOrThrow()

            // 160 mm in X - der Plotter kann nur 155.
            val probleme = c.preflight(strokes(160f, 80f), istGehomt = true)
            assertTrue(probleme.any { it.contains("rechts") }, "Grenze nicht erkannt: $probleme")
            c.disconnect()
        }
    }

    @Test
    fun `Alarmzustand verhindert den Start`() = kotlinx.coroutines.test.runTest {
        FakeFluidNc(state = "Alarm").use { fake ->
            val c = controller(fake)
            c.connect().getOrThrow()
            c.requestStatus()

            val probleme = c.preflight(strokes(100f, 80f), istGehomt = true)
            assertTrue(probleme.any { it.contains("Alarm") }, "Alarm nicht erkannt: $probleme")
            c.disconnect()
        }
    }

    @Test
    fun `passender Auftrag auf referenzierter Maschine wird durchgelassen`() =
        kotlinx.coroutines.test.runTest {
            FakeFluidNc().use { fake ->
                val c = controller(fake)
                c.connect().getOrThrow()
                c.requestStatus()

                val probleme = c.preflight(strokes(150f, 100f), istGehomt = true)
                assertTrue(probleme.isEmpty(), "Gueltiger Auftrag abgelehnt: $probleme")
                c.disconnect()
            }
        }

    @Test
    fun `Papier-Offset geht in die Grenzpruefung ein`() = kotlinx.coroutines.test.runTest {
        FakeFluidNc().use { fake ->
            // Blatt liegt 20 mm weiter rechts; ein Text bis 145 mm auf dem Blatt landet damit
            // bei 165 mm in Maschinenkoordinaten - ausserhalb der 155 mm.
            val versetzt = profile.copy(paperOffsetXMm = 20f)
            val c = MachineController(TelnetTransport("127.0.0.1", fake.port), versetzt)
            c.connect().getOrThrow()

            val probleme = c.preflight(strokes(145f, 80f), istGehomt = true)
            assertTrue(
                probleme.any { it.contains("rechts") },
                "Papier-Offset wurde bei der Grenzpruefung nicht beruecksichtigt: $probleme",
            )
            c.disconnect()
        }
    }
}
