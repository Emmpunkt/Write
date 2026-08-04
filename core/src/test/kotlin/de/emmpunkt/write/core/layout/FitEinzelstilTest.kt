package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.font.StrokeFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Einpassen eines einzelnen Stils.
 *
 * Vom Nutzer gemeldet (2026-08-04): Nach dem Einpassen von "Stil 2" stand der unbenutzte Stil
 * "Text" ploetzlich auf 15,9 statt 11,6 mm. Der alte Weg skalierte ALLE Stile mit demselben
 * Faktor - auch die, die im Text gar nicht vorkommen.
 */
class FitEinzelstilTest {

    private val schrift: (String) -> StrokeFont = { Fonts.load(it) }
    private val frame = Frame(105f, 148f, Margins.all(10f))
    private val text = "Titel\nMilch Brot Kaffee Butter Eier Mehl Zucker Salz Pfeffer Oel Reis"

    @Test
    fun `die anderen Stile bleiben unangetastet`() {
        val stile = listOf(
            TextStyle("sans", sizeMm = 11.6f),
            TextStyle("sans", sizeMm = 7.4f),
        )
        val ergebnis = fitEinzelstil(text, stile, listOf(1, 1), 1, schrift, frame)
        assertTrue(ergebnis.fits)

        // Der Aufrufer setzt NUR den gewaehlten Stil - hier nachgestellt.
        val nachher = stile.mapIndexed { i, s ->
            if (i == 1) s.copy(sizeMm = ergebnis.sizeMm) else s
        }
        assertEquals(11.6f, nachher[0].sizeMm, "Der unbenutzte Stil haette sich nicht ruehren duerfen")
    }

    @Test
    fun `bei einem einzigen Stil kommt genau heraus, was fitSize liefert`() {
        val stil = TextStyle("sans", sizeMm = 5f)
        val alt = fitSize(text, stil, frame, Fonts.load("sans"), minMm = 3f, maxMm = 25f)
        val neu = fitEinzelstil(text, listOf(stil), listOf(0, 0), 0, schrift, frame,
            minMm = 3f, maxMm = 25f)

        assertEquals(alt.fits, neu.fits)
        assertEquals(alt.sizeMm, neu.sizeMm, "Der alte Fall muss bitgenau erhalten bleiben")
    }

    @Test
    fun `das Ergebnis passt und eine Stufe groesser passt nicht mehr`() {
        val stile = listOf(TextStyle("sans", sizeMm = 12f), TextStyle("sans", sizeMm = 5f))
        val zuordnung = listOf(0, 1)
        val ergebnis = fitEinzelstil(text, stile, zuordnung, 1, schrift, frame)
        assertTrue(ergebnis.fits)

        fun passt(groesse: Float): Boolean {
            val probe = stile.mapIndexed { i, s -> if (i == 1) s.copy(sizeMm = groesse) else s }
            val laid = layoutAbsaetze(absaetzeAus(text, probe, zuordnung, schrift), frame)
            return !laid.overflow && laid.overlongWords.isEmpty()
        }
        assertTrue(passt(ergebnis.sizeMm), "Das gelieferte Mass passt nicht")
        assertFalse(passt(ergebnis.sizeMm + 0.1f), "Eine Stufe groesser haette auch gepasst")
    }

    @Test
    fun `ein unbenutzter Stil begrenzt das Einpassen nicht`() {
        // Der unbenutzte Stil ist riesig. Frueher zog er die Obergrenze herunter, weil die
        // Suche den GROESSTEN Stil im Reglerbereich halten musste.
        val mitRiese = listOf(TextStyle("sans", sizeMm = 25f), TextStyle("sans", sizeMm = 5f))
        val ohneRiese = listOf(TextStyle("sans", sizeMm = 6f), TextStyle("sans", sizeMm = 5f))

        val a = fitEinzelstil(text, mitRiese, listOf(1, 1), 1, schrift, frame)
        val b = fitEinzelstil(text, ohneRiese, listOf(1, 1), 1, schrift, frame)
        assertEquals(b.sizeMm, a.sizeMm, "Ein unbenutzter Stil darf das Ergebnis nicht aendern")
    }

    @Test
    fun `meldet Misserfolg, wenn auch die kleinste Stufe nicht passt`() {
        val winzig = Frame(30f, 12f, Margins.all(1f))
        val stile = listOf(TextStyle("sans", sizeMm = 5f))
        val ergebnis = fitEinzelstil(
            "Ein ziemlich langer Fliesstext, der dort niemals hineinpasst",
            stile, listOf(0), 0, schrift, winzig,
        )
        assertFalse(ergebnis.fits)
    }

    // ---- welche Stile ueberhaupt vorkommen ----

    @Test
    fun `benutzt werden die Stile aus der Zuordnung`() {
        assertEquals(setOf(1), benutzteStile("A\nB", listOf(1, 1), 2))
        assertEquals(setOf(0, 1), benutzteStile("A\nB", listOf(0, 1), 2))
    }

    @Test
    fun `eine zu kurze Zuordnung faellt auf den ersten Stil zurueck`() {
        // Genau wie absaetzeAus es tut - sonst wuerde hier etwas anderes gezaehlt als gesetzt.
        assertEquals(setOf(0, 1), benutzteStile("A\nB\nC", listOf(1), 2))
    }

    @Test
    fun `ein unbekannter Index zaehlt als erster Stil`() {
        assertEquals(setOf(0), benutzteStile("A\nB", listOf(7, 9), 2))
    }
}
