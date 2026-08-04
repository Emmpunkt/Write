package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Einpassen, wenn mehrere Stile im Spiel sind.
 *
 * Gesucht wird nicht eine Groesse, sondern ein gemeinsamer Faktor: eine Ueberschrift, die
 * doppelt so gross ist wie der Fliesstext, muss danach immer noch doppelt so gross sein.
 */
class FitSkalierungTest {

    private val schrift: (String) -> de.emmpunkt.write.core.font.StrokeFont = { Fonts.load(it) }
    private val frame = Frame(widthMm = 105f, heightMm = 148f, margins = Margins.all(10f))
    private val text = "Titel\nMilch Brot Kaffee Butter Eier Mehl Zucker Salz Pfeffer Oel Reis"

    private val leit = TextStyle("sans", sizeMm = 5f)
    private val doppelt = TextStyle("sans", sizeMm = 10f)

    @Test
    fun `bei einem einzigen Stil kommt genau heraus, was fitSize liefert`() {
        val alt = fitSize(text, leit, frame, Fonts.load("sans"), minMm = 3f, maxMm = 25f)
        val neu = fitSkalierung(text, listOf(leit), listOf(0, 0), schrift, frame, minMm = 3f, maxMm = 25f)

        assertEquals(alt.fits, neu.fits)
        assertEquals(alt.sizeMm, neu.sizeMm, "Der alte Fall muss bitgenau erhalten bleiben")
    }

    @Test
    fun `haelt das Verhaeltnis der Stile ein`() {
        val ergebnis = fitSkalierung(text, listOf(leit, doppelt), listOf(1, 0), schrift, frame)
        assertTrue(ergebnis.fits)

        val skaliert = skaliert(listOf(leit, doppelt), ergebnis.sizeMm)
        // Auf dem 0,1-mm-Raster kann das Verhaeltnis um eine halbe Stufe je Wert wandern.
        assertTrue(
            abs(skaliert[1].sizeMm / skaliert[0].sizeMm - 2f) < 0.05f,
            "Verhaeltnis verrutscht: ${skaliert[0].sizeMm} zu ${skaliert[1].sizeMm}",
        )
    }

    @Test
    fun `das Ergebnis passt und eine Stufe groesser passt nicht mehr`() {
        val stile = listOf(leit, doppelt)
        val zuordnung = listOf(1, 0)
        val ergebnis = fitSkalierung(text, stile, zuordnung, schrift, frame)
        assertTrue(ergebnis.fits)

        assertTrue(passt(ergebnis.sizeMm, stile, zuordnung), "Das gelieferte Mass passt nicht")
        assertFalse(
            passt(ergebnis.sizeMm + 0.1f, stile, zuordnung),
            "Eine Stufe groesser haette auch noch gepasst - dann war die Suche zu vorsichtig",
        )
    }

    @Test
    fun `kein Stil verlaesst den Bereich, den der Regler anbietet`() {
        // Ein winziger Rahmen zwingt die Suche nach unten, ein riesiger nach oben.
        val gross = Frame(widthMm = 800f, heightMm = 800f, margins = Margins.all(1f))
        val ergebnis = fitSkalierung("Kurz\nText", listOf(leit, doppelt), listOf(1, 0), schrift, gross)

        val skaliert = skaliert(listOf(leit, doppelt), ergebnis.sizeMm)
        skaliert.forEach {
            assertTrue(
                it.sizeMm in 2f..25.001f,
                "Stil ausserhalb des Reglerbereichs: ${it.sizeMm} mm",
            )
        }
    }

    @Test
    fun `meldet Misserfolg, wenn auch die kleinste Stufe nicht passt`() {
        val winzig = Frame(widthMm = 30f, heightMm = 12f, margins = Margins.all(1f))
        val ergebnis = fitSkalierung(
            "Titel\nEin ziemlich langer Fliesstext, der dort niemals hineinpasst",
            listOf(leit, doppelt), listOf(1, 0), schrift, winzig,
        )
        assertFalse(ergebnis.fits, "Hier darf keine Groesse gemeldet werden, die nicht passt")
    }

    @Test
    fun `meldet Misserfolg, wenn die Stile zu weit auseinanderliegen`() {
        // 2 mm und 25 mm im selben Dokument: kein Faktor haelt beide in [3, 25].
        val extrem = listOf(TextStyle("sans", sizeMm = 2f), TextStyle("sans", sizeMm = 25f))
        val ergebnis = fitSkalierung("A\nB", extrem, listOf(0, 1), schrift, frame, minMm = 3f, maxMm = 25f)

        assertFalse(ergebnis.fits, "Kein gemeinsamer Faktor moeglich - das muss die App erfahren")
    }

    @Test
    fun `skaliert rastert jede Groesse auf ein Zehntel Millimeter`() {
        val skaliert = skaliert(listOf(TextStyle("sans", sizeMm = 3f), TextStyle("sans", sizeMm = 7f)), 4f)
        skaliert.forEach {
            val stufen = it.sizeMm / 0.1f
            assertTrue(
                abs(stufen - Math.round(stufen)) < 1e-3,
                "Nicht auf dem Raster: ${it.sizeMm} mm - so laesst sie sich von Hand nicht treffen",
            )
        }
        assertEquals(4f, skaliert[0].sizeMm, "Der Leitstil bekommt genau das gesuchte Mass")
    }

    @Test
    fun `leerer Text passt immer`() {
        val ergebnis = fitSkalierung("   ", listOf(leit), listOf(0), schrift, frame)
        assertTrue(ergebnis.fits)
    }

    private fun passt(leitgroesse: Float, stile: List<TextStyle>, zuordnung: List<Int>): Boolean {
        val laid = layoutAbsaetze(
            absaetzeAus(text, skaliert(stile, leitgroesse), zuordnung, schrift),
            frame,
        )
        return !laid.overflow && laid.overlongWords.isEmpty()
    }
}
