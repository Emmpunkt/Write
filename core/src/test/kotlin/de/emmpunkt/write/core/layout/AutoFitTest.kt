package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prueft die Suche nach der groessten passenden Schriftgroesse.
 *
 * Der wichtigste Test ist [`Ergebnis ist maximal`]: ohne ihn bestuende auch eine Funktion,
 * die stur die Mindestgroesse zurueckgibt, alle uebrigen Pruefungen.
 */
class AutoFitTest {

    private val font = Fonts.load("script-simplex")
    private val stil = TextStyle("script-simplex", sizeMm = 7f)
    private val a6quer = Frame(widthMm = 148f, heightMm = 105f, margins = Margins.all(8f))

    private val langerText = """
        Einkaufsliste fuer Samstag: Milch, Brot, Kaffee, Butter und Eier.
        Danach zur Post und das Paket abholen, es liegt seit Dienstag dort.
        Abends noch die Rechnung schreiben und den Brief einwerfen.
    """.trimIndent()

    /** Passt der Text bei dieser Groesse in den Rahmen, ohne hart getrennt zu werden? */
    private fun passt(text: String, sizeMm: Float, frame: Frame = a6quer): Boolean {
        val laid = layoutText(text, stil.copy(sizeMm = sizeMm), frame, font)
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    @Test
    fun `verkleinert einen ueberlaufenden Text so weit, dass er passt`() {
        assertFalse(passt(langerText, 7f), "Der Testtext muss bei 7 mm ueberlaufen")

        val ergebnis = fitSize(langerText, stil, a6quer, font)

        assertTrue(ergebnis.fits)
        assertTrue(ergebnis.sizeMm < 7f, "Erwartet kleiner als 7 mm, war ${ergebnis.sizeMm}")
        assertTrue(passt(langerText, ergebnis.sizeMm), "Das Ergebnis passt nicht")
    }

    @Test
    fun `Ergebnis ist maximal`() {
        val ergebnis = fitSize(langerText, stil, a6quer, font)

        assertTrue(passt(langerText, ergebnis.sizeMm))
        assertFalse(
            passt(langerText, ergebnis.sizeMm + 0.1f),
            "Eine Stufe groesser passt auch noch - dann war das Ergebnis nicht maximal",
        )
    }

    @Test
    fun `Ergebnis liegt auf dem Zehntelmillimeter-Raster des Reglers`() {
        val zehntel = fitSize(langerText, stil, a6quer, font).sizeMm * 10f

        assertTrue(
            abs(zehntel - zehntel.roundToInt()) < 0.01f,
            "Groesse liegt nicht auf dem Raster: ${zehntel / 10f}",
        )
    }

    @Test
    fun `kurzer Text bekommt die Obergrenze`() {
        val ergebnis = fitSize("Hallo", stil, a6quer, font, maxMm = 12f)

        assertTrue(ergebnis.fits)
        assertEquals(12f, ergebnis.sizeMm, 0.001f)
    }

    @Test
    fun `ein zu langes Wort auf schmalem Blatt gilt als nicht einpassbar`() {
        val schmal = Frame(widthMm = 40f, heightMm = 105f, margins = Margins.all(8f))

        val ergebnis = fitSize("Donaudampfschifffahrtsgesellschaftskapitaen", stil, schmal, font)

        assertFalse(ergebnis.fits, "Ohne Trennstelle darf kein Erfolg gemeldet werden")
        assertEquals(2f, ergebnis.sizeMm, 0.001f, "Bei Misserfolg wird die Untergrenze geliefert")
    }

    @Test
    fun `leerer Text stuerzt nicht ab`() {
        assertEquals(25f, fitSize("", stil, a6quer, font).sizeMm, 0.001f)
        assertEquals(25f, fitSize("   \n  ", stil, a6quer, font).sizeMm, 0.001f)
    }

    @Test
    fun `passt und ist maximal fuer verschiedene Texte und Rahmen`() {
        val faelle = listOf(
            langerText to a6quer,
            "Kurz notiert" to Frame(105f, 74f, Margins.all(5f)),
            "Mehr Text als in eine Zeile passt, deutlich mehr sogar" to Frame(74f, 105f, Margins.all(6f)),
            langerText to Frame(105f, 148f, Margins.all(12f)),
        )

        faelle.forEach { (text, rahmen) ->
            val ergebnis = fitSize(text, stil, rahmen, font)
            assertTrue(ergebnis.fits, "'$text' wurde nicht eingepasst")
            assertTrue(passt(text, ergebnis.sizeMm, rahmen), "Ergebnis passt nicht: '$text'")
            assertFalse(
                passt(text, ergebnis.sizeMm + 0.1f, rahmen),
                "Nicht maximal bei '$text': ${ergebnis.sizeMm} mm",
            )
        }
    }
}
