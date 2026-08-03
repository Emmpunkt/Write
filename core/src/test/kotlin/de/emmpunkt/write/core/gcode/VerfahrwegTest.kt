package de.emmpunkt.write.core.gcode

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Der fahrbare Bereich beginnt nicht bei null.
 *
 * Beim Plotter des Nutzers steht in `$/axes/x` und `$/axes/y` bei negativer Referenzfahrt
 * `mpos_mm: 3.0`. Fahrbar ist also Maschine 3..158, nicht 0..155 - nachgemessen: ein Jog auf
 * Maschine 2 wird auf exakt 3.000 begrenzt.
 *
 * Bisher nahm die App `[0, workArea]` an. Das rechnet konservativ, solange der Arbeitsnullpunkt
 * auf oder ueber der Untergrenze liegt - es verschenkt dann nur Millimeter. Liegt er darunter,
 * haelt die Pruefung Koordinaten faelschlich fuer fahrbar, und die Maschine faehrt mitten im
 * Auftrag in ALARM:2.
 */
class VerfahrwegTest {

    private val tisch = MachineProfile(workAreaXMm = 155f, workAreaYMm = 105f)
    private fun zug(x: Float, y: Float) = listOf(Polyline(listOf(Point(1f, 1f), Point(x, y))))

    @Test
    fun `ohne Angabe gilt weiter der Bereich ab null`() {
        // Rueckfall fuer den Fall, dass die Achsenkonfiguration nicht gelesen werden konnte.
        val limits = TravelLimits.ofProfile(tisch)

        assertEquals(0f, limits.minXMm)
        assertEquals(155f, limits.maxXMm)
        assertEquals(0f, limits.minYMm)
        assertEquals(105f, limits.maxYMm)
    }

    @Test
    fun `negative Referenzfahrt legt den Bereich ueber den Nullpunkt`() {
        // mpos_mm = 3 bei negativer Referenzfahrt: die Maschine steht nach dem Homing auf 3
        // und kommt nicht darunter. Darueber liegt der Verfahrweg.
        val x = AxisTravel.fromHoming(mposMm = 3f, maxTravelMm = 155f, positiveDirection = false)

        assertEquals(3f, x.minMm)
        assertEquals(158f, x.maxMm)
    }

    @Test
    fun `positive Referenzfahrt legt den Bereich darunter`() {
        // Der andere Fall: die Referenzfahrt laeuft nach oben, mpos_mm ist dann die OBERE
        // Grenze. Wer das verwechselt, spiegelt den ganzen fahrbaren Bereich.
        val x = AxisTravel.fromHoming(mposMm = 155f, maxTravelMm = 155f, positiveDirection = true)

        assertEquals(0f, x.minMm)
        assertEquals(155f, x.maxMm)
    }

    @Test
    fun `Untergrenze schlaegt zu wenn der Arbeitsnullpunkt darunter liegt`() {
        // Der Fall, den die alte Pruefung nicht sah: Arbeitsnullpunkt auf Maschine (2, 2),
        // also EINEN Millimeter unter der Untergrenze 3. Eine Blattkoordinate von 0.5
        // waere Maschine 2.5 - unfahrbar.
        val limits = TravelLimits(minXMm = 3f, maxXMm = 158f, minYMm = 3f, maxYMm = 108f)
        val check = checkBounds(zug(0.5f, 20f), tisch, WorkOffset(2f, 2f), limits)

        assertFalse(check.ok, "Koordinate unterhalb der Achsen-Untergrenze durchgelassen")
        assertTrue(check.violations.any { it.contains("links") }, check.violations.toString())
    }

    @Test
    fun `Obergrenze waechst mit dem tatsaechlichen Verfahrweg`() {
        // Die Kehrseite: mit dem wahren Bereich 3..158 und Arbeitsnullpunkt auf 3 ist
        // Blatt-X 154 fahrbar (Maschine 157) - die alte Rechnung gegen 155 haette
        // schon bei 152 abgewiesen und dem Nutzer Platz verschenkt.
        val limits = TravelLimits(minXMm = 3f, maxXMm = 158f, minYMm = 3f, maxYMm = 108f)

        assertTrue(checkBounds(zug(154f, 20f), tisch, WorkOffset(3f, 3f), limits).ok)

        val darueber = checkBounds(zug(156f, 20f), tisch, WorkOffset(3f, 3f), limits)
        assertFalse(darueber.ok, "Maschine 159 liegt jenseits von 158")
        assertTrue(darueber.violations.any { it.contains("rechts") }, darueber.violations.toString())
    }

    @Test
    fun `dieselbe Grenze in Y`() {
        val limits = TravelLimits(minXMm = 3f, maxXMm = 158f, minYMm = 3f, maxYMm = 108f)

        val unten = checkBounds(zug(20f, 0.5f), tisch, WorkOffset(2f, 2f), limits)
        assertFalse(unten.ok)
        assertTrue(unten.violations.any { it.contains("unten") }, unten.violations.toString())

        val oben = checkBounds(zug(20f, 106f), tisch, WorkOffset(3f, 3f), limits)
        assertFalse(oben.ok)
        assertTrue(oben.violations.any { it.contains("oben") }, oben.violations.toString())
    }

    /**
     * Jeder Auftrag endet mit `G0 X0 Y0` - der Rueckfahrt auf den Arbeitsnullpunkt.
     *
     * Liegt der ausserhalb des fahrbaren Bereichs, laeuft die Maschine dort in ALARM:2 - und
     * zwar erst am ENDE, wenn das Blatt schon beschrieben ist. Genau das ist am Geraet
     * passiert, als der Nullpunkt einen Millimeter zu tief lag.
     *
     * Beim Stand vom 2026-08-03 ist der Abstand kein Millimeter mehr, sondern sieben: G54
     * liegt auf Maschine (3, 3), die Achsen kommen nicht unter 10. Die Pruefung muss den
     * Rueckkehrpunkt deshalb mitpruefen, auch wenn dort kein Strich liegt.
     */
    @Test
    fun `die Rueckfahrt auf den Nullpunkt wird mitgeprueft`() {
        val limits = TravelLimits(minXMm = 10f, maxXMm = 165f, minYMm = 10f, maxYMm = 115f)

        // Der Text selbst liegt sauber im Bereich: Blatt 20..40 ist Maschine 23..43.
        val check = checkBounds(
            listOf(Polyline(listOf(Point(20f, 20f), Point(40f, 40f)))),
            tisch,
            WorkOffset(3f, 3f),
            limits,
        )

        assertFalse(
            check.ok,
            "Die unfahrbare Rueckfahrt auf den Arbeitsnullpunkt wurde nicht bemerkt",
        )
        assertTrue(
            check.violations.any { it.contains("Arbeitsnullpunkt") },
            "Die Meldung nennt die Ursache nicht: ${check.violations}",
        )
    }

    @Test
    fun `liegt der Nullpunkt im Bereich stoert die Rueckfahrt nicht`() {
        // Gegenprobe: mit dem Arbeitsnullpunkt auf der Untergrenze ist (0,0) genau noch
        // anfahrbar - es darf keine Warnung geben.
        val limits = TravelLimits(minXMm = 10f, maxXMm = 165f, minYMm = 10f, maxYMm = 115f)
        val check = checkBounds(
            listOf(Polyline(listOf(Point(20f, 20f), Point(40f, 40f)))),
            tisch,
            WorkOffset(10f, 10f),
            limits,
        )
        assertTrue(check.ok, "Faelschlich abgewiesen: ${check.violations}")
    }

    @Test
    fun `der Vorgabewert veraendert das bisherige Verhalten nicht`() {
        // Gegenprobe zur Sicherheit: ohne gelesene Achsenkonfiguration muss genau das
        // herauskommen, was die App bisher gerechnet hat.
        val strokes = zug(149.27f, 102.41f)
        val nullpunkt = WorkOffset(2f, 2f)

        assertEquals(
            checkBounds(strokes, tisch, nullpunkt).violations,
            checkBounds(strokes, tisch, nullpunkt, TravelLimits.ofProfile(tisch)).violations,
        )
    }
}
