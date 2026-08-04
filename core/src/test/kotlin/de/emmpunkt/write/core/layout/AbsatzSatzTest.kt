package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Satz mit verschiedenen Stilen je Absatz.
 *
 * Der wunde Punkt ist der Uebergang zwischen zwei verschieden grossen Abstaetzen: dort gibt es
 * zwei Zeilenvorschuebe und keinen offensichtlich richtigen.
 */
class AbsatzSatzTest {

    private val sans = Fonts.load("sans")
    private val serif = Fonts.load("serif")
    private val frame = Frame(widthMm = 105f, heightMm = 148f, margins = Margins.all(10f))

    private fun klein(text: String) = AbsatzSatz(text, TextStyle("sans", sizeMm = 5f), sans)
    private fun gross(text: String) = AbsatzSatz(text, TextStyle("sans", sizeMm = 12f), sans)

    @Test
    fun `ein einziger Stil liefert genau das, was layoutText liefert`() {
        val text = "Milch Brot Kaffee Butter Eier Mehl\nZucker Salz"
        val style = TextStyle("sans", sizeMm = 5f)

        val ueberHuelle = layoutText(text, style, frame, sans)
        val direkt = layoutAbsaetze(
            text.split('\n').map { AbsatzSatz(it, style, sans) },
            frame,
        )

        assertEquals(ueberHuelle, direkt, "Die Verallgemeinerung muss den alten Fall exakt treffen")
    }

    @Test
    fun `nach einer grossen Ueberschrift bekommt der kleine Text den grossen Vorschub`() {
        val result = layoutAbsaetze(listOf(gross("Titel"), klein("Text")), frame)

        val abstand = result.lines[0].baselineYMm - result.lines[1].baselineYMm
        val grosserVorschub = vorschub(sizeMm = 12f)

        assertNahe(
            grosserVorschub, abstand,
            "Sonst saesse der kleine Text in den Unterlaengen der Ueberschrift",
        )
    }

    @Test
    fun `vor einer grossen Ueberschrift bekommt der Uebergang ebenfalls den grossen Vorschub`() {
        val result = layoutAbsaetze(listOf(klein("Text"), gross("Titel")), frame)

        val abstand = result.lines[0].baselineYMm - result.lines[1].baselineYMm
        assertNahe(
            vorschub(sizeMm = 12f), abstand,
            "Die grosse Zeile braucht ihren Platz, egal was ueber ihr steht",
        )
    }

    @Test
    fun `innerhalb eines Absatzes bleibt es beim eigenen Vorschub`() {
        // Ein Absatz, der sicher umbricht.
        val lang = klein("Milch Brot Kaffee Butter Eier Mehl Zucker Salz Pfeffer Oel Reis Nudeln")
        val result = layoutAbsaetze(listOf(lang), frame)

        assertTrue(result.lines.size > 1, "Text muesste umbrechen")
        val abstand = result.lines[0].baselineYMm - result.lines[1].baselineYMm
        assertNahe(vorschub(sizeMm = 5f), abstand)
    }

    @Test
    fun `die benoetigte Hoehe summiert die gemischten Vorschuebe`() {
        val result = layoutAbsaetze(listOf(gross("Titel"), klein("Text")), frame)

        val ersteBaseline = result.lines.first().baselineYMm
        val letzteBaseline = result.lines.last().baselineYMm
        val oben = ersteBaseline + hoehen(12f).ascender
        val unten = letzteBaseline + hoehen(5f).descender

        assertNahe(
            oben - unten, result.requiredHeightMm,
            "Die Hoehe muss von der obersten Oberlaenge bis zur untersten Unterlaenge reichen",
        )
    }

    @Test
    fun `jeder Absatz bricht mit seiner eigenen Groesse um`() {
        val text = "Milch Brot Kaffee Butter Eier Mehl"
        val result = layoutAbsaetze(listOf(gross(text), klein(text)), frame)

        val grosseZeilen = result.lines.count { it.baselineYMm > result.lines.last().baselineYMm }
        assertTrue(
            grosseZeilen > 0,
            "Der grosse Absatz muesste mehr Zeilen brauchen als der kleine",
        )
        result.lines.forEach {
            assertTrue(
                it.widthMm <= frame.usableWidthMm + 0.01f,
                "Zeile ragt heraus: '${it.text}' (${it.widthMm} mm)",
            )
        }
    }

    @Test
    fun `die Ausrichtung gilt je Absatz`() {
        val links = AbsatzSatz("Kurz", TextStyle("sans", sizeMm = 5f, align = Align.LEFT), sans)
        val rechts = AbsatzSatz("Kurz", TextStyle("sans", sizeMm = 5f, align = Align.RIGHT), sans)
        val result = layoutAbsaetze(listOf(links, rechts), frame)

        val xLinks = result.lines[0].strokes.flatMap { it.points }.minOf { it.x }
        val xRechts = result.lines[1].strokes.flatMap { it.points }.minOf { it.x }
        assertTrue(xRechts > xLinks, "Der rechtsbuendige Absatz muss weiter rechts stehen")
    }

    @Test
    fun `nicht darstellbare Zeichen werden je Absatz gegen dessen eigene Schrift geprueft`() {
        val result = layoutAbsaetze(
            listOf(
                AbsatzSatz("Text", TextStyle("sans", sizeMm = 5f), sans),
                AbsatzSatz("Text", TextStyle("serif", sizeMm = 5f), serif),
            ),
            frame,
        )
        // Beide Schriften koennen einfachen Text - hier geht es nur darum, dass die Pruefung
        // nicht die Schrift des ersten Absatzes auf alle anwendet.
        assertEquals(emptySet(), result.unsupported)
    }

    @Test
    fun `ein leerer Absatz bleibt als Abstand erhalten`() {
        val result = layoutAbsaetze(listOf(klein("Oben"), klein(""), klein("Unten")), frame)
        assertEquals(listOf("Oben", "", "Unten"), result.lines.map { it.text })
    }

    @Test
    fun `ohne Absaetze kommt nichts heraus`() {
        val result = layoutAbsaetze(emptyList(), frame)
        assertEquals(emptyList(), result.lines)
        assertEquals(0f, result.requiredHeightMm)
    }

    /** Zeilenvorschub einer Groesse - dieselbe Rechnung wie im Satz, aber unabhaengig davon. */
    private fun vorschub(sizeMm: Float, lineSpacing: Float = 1.15f): Float =
        sans.lineHeightUnits * (sizeMm / sans.capHeightUnits) * lineSpacing

    private class Hoehen(val ascender: Float, val descender: Float)

    private fun hoehen(sizeMm: Float): Hoehen {
        val scale = sizeMm / sans.capHeightUnits
        return Hoehen(sans.ascenderUnits * scale, sans.descenderUnits * scale)
    }

    private fun assertNahe(erwartet: Float, tatsaechlich: Float, hinweis: String = "") {
        assertTrue(
            abs(erwartet - tatsaechlich) < 0.01f,
            "$hinweis erwartet $erwartet mm, war $tatsaechlich mm",
        )
    }
}
