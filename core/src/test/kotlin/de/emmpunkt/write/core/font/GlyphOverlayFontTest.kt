package de.emmpunkt.write.core.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlyphOverlayFontTest {

    private val deutscheZeichen = "äöüÄÖÜß"

    @Test
    fun `alle mitgelieferten Schriften koennen deutsche Zeichen`() {
        Fonts.available.forEach { entry ->
            val font = Fonts.load(entry.id)
            deutscheZeichen.forEach { c ->
                assertTrue(font.has(c.code), "${entry.id}: '$c' fehlt")
            }
        }
    }

    @Test
    fun `Trema sitzt ueber dem Grundbuchstaben und aendert den Vorschub nicht`() {
        val font = Fonts.load("sans")
        val a = assertNotNull(font.glyph('a'.code))
        val umlaut = assertNotNull(font.glyph('ä'.code))

        // Der Vorschub bleibt gleich - sonst wuerde 'ä' im Wort anders laufen als 'a'.
        assertEquals(a.advance, umlaut.advance)

        // Zwei zusaetzliche Striche fuer die Punkte.
        assertEquals(a.strokes.size + 2, umlaut.strokes.size)

        // Die Punkte liegen vollstaendig oberhalb des Grundbuchstabens.
        val topOfA = a.strokes.flatMap { it.points }.maxOf { it.y }
        val dots = umlaut.strokes.takeLast(2).flatMap { it.points }
        assertTrue(dots.all { it.y > topOfA }, "Trema ueberlappt den Grundbuchstaben")
    }

    @Test
    fun `Trema folgt der Hoehe des Grundbuchstabens`() {
        val font = Fonts.load("sans")
        val kleinTop = assertNotNull(font.glyph('ä'.code)).strokes.takeLast(2)
            .flatMap { it.points }.minOf { it.y }
        val grossTop = assertNotNull(font.glyph('Ä'.code)).strokes.takeLast(2)
            .flatMap { it.points }.minOf { it.y }

        // Ueber dem hohen 'A' muss das Trema hoeher sitzen als ueber dem niedrigen 'a'.
        assertTrue(grossTop > kleinTop, "Trema auf 'Ä' sitzt nicht hoeher als auf 'ä'")
    }

    @Test
    fun `Eszett steht auf der Grundlinie und hat eine Oberlaenge`() {
        val font = Fonts.load("sans")
        val glyph = assertNotNull(font.glyph('ß'.code))
        val points = glyph.strokes.flatMap { it.points }

        assertTrue(points.minOf { it.y } >= 0f, "Eszett darf nicht unter die Grundlinie")
        assertTrue(
            points.maxOf { it.y } > font.capHeightUnits * 0.8f,
            "Eszett braucht eine Oberlaenge",
        )
        assertTrue(glyph.advance > 0f)
    }

    @Test
    fun `typografische Zeichen werden ersetzt statt zu fehlen`() {
        val font = Fonts.load("sans")
        // Das sind die Zeichen, die Android-Tastaturen selbsttaetig einsetzen.
        "„“”‚‘’–—…".forEach { c ->
            if (c == '…') return@forEach
            assertTrue(font.has(c.code), "Ersatz fuer '$c' fehlt")
        }
    }

    @Test
    fun `meldet wirklich unbekannte Zeichen`() {
        val font = Fonts.load("sans")
        val fehlend = font.unsupportedCharacters("Grüße Ω")

        // Griechisch ist in den lateinischen Hershey-Saetzen nicht enthalten.
        assertTrue('Ω'.code in fehlend, "Unbekanntes Zeichen wurde nicht gemeldet")
        // Was die Schrift kann, darf nicht gemeldet werden.
        assertTrue('ü'.code !in fehlend)
        assertTrue('G'.code !in fehlend)
    }

    @Test
    fun `Zeilenumbrueche gelten nicht als fehlende Zeichen`() {
        val font = Fonts.load("sans")
        assertTrue(font.unsupportedCharacters("Hallo\nWelt").isEmpty())
    }

    @Test
    fun `laesst den Strich der Basisschrift stehen, wenn die Korrektur aus ist`() {
        // Nutze den Raw HersheyFont direkt, damit die Korrektur messbar ist.
        val content = readResource("fonts/futural.jhf")
        val basisRaw = HersheyFont.parse("sans", "Technisch", content)
        val eigen = assertNotNull(basisRaw.glyph('-'.code))

        val ohneKorrektur = GlyphOverlayFont(basisRaw, stricheErsetzen = false)
        val durchgereicht = assertNotNull(ohneKorrektur.glyph('-'.code))

        assertEquals(eigen.advance, durchgereicht.advance)
        assertEquals(
            eigen.strokes.flatMap { it.points }.map { it.x },
            durchgereicht.strokes.flatMap { it.points }.map { it.x },
        )
    }

    @Test
    fun `ersetzt den Strich weiterhin, wenn die Korrektur an ist`() {
        // Nutze den Raw HersheyFont direkt, um die Korrektur zu testen.
        val content = readResource("fonts/futural.jhf")
        val basis = HersheyFont.parse("sans", "Technisch", content)
        val eigen = assertNotNull(basis.glyph('-'.code))
        val korrigiert = assertNotNull(GlyphOverlayFont(basis).glyph('-'.code))

        assertTrue(
            korrigiert.advance < eigen.advance,
            "Der nachgezeichnete Strich muss schmaler sein als Hersheys eigener",
        )
    }

    private fun readResource(path: String): String {
        val stream = GlyphOverlayFontTest::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Schriftdatei '$path' nicht im Paket gefunden")
        return stream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
    }

    @Test
    fun `ersetzt typografische Zeichen auch bei abgeschalteter Strich-Korrektur`() {
        // Android-Tastaturen setzen diese Zeichen selbsttaetig ein; ohne Ersetzung entstuenden
        // Luecken im geplotteten Text. Das gilt unabhaengig von der Strich-Korrektur.
        val font = GlyphOverlayFont(Fonts.load("sans"), stricheErsetzen = false)
        assertNotNull(font.glyph(0x2019), "typografischer Apostroph muss ersetzt werden")
        assertNotNull(font.glyph(0x201C), "geschwungenes Anfuehrungszeichen muss ersetzt werden")
    }
}

/**
 * Die Hershey-Schriften bringen einen Bindestrich von 0,86 Versalhoehen mit - breiter als
 * jeder Kleinbuchstabe - und setzen ihn auf deren Oberkante. Beides faellt im Fliesstext auf.
 */
class StricheTest {

    @Test
    fun `Bindestrich ist schmaler als ein Kleinbuchstabe`() {
        Fonts.available.forEach { entry ->
            val font = Fonts.load(entry.id)
            val strich = assertNotNull(font.glyph('-'.code))
            val n = assertNotNull(font.glyph('n'.code))

            val strichBreite = strich.strokes.flatMap { it.points }.let { p ->
                p.maxOf { it.x } - p.minOf { it.x }
            }
            val nBreite = n.strokes.flatMap { it.points }.let { p ->
                p.maxOf { it.x } - p.minOf { it.x }
            }
            assertTrue(
                strichBreite < nBreite,
                "${entry.id}: Bindestrich ($strichBreite) nicht schmaler als 'n' ($nBreite)",
            )
        }
    }

    @Test
    fun `Bindestrich sitzt mittig auf den Kleinbuchstaben`() {
        val font = Fonts.load("script-simplex")
        val strichY = assertNotNull(font.glyph('-'.code)).strokes.flatMap { it.points }.first().y
        val xHoehe = assertNotNull(font.glyph('x'.code)).strokes.flatMap { it.points }.maxOf { it.y }

        // Weder auf der Grundlinie noch auf der Oberkante, sondern dazwischen.
        assertTrue(strichY > xHoehe * 0.25f, "Strich sitzt zu tief: $strichY bei x-Hoehe $xHoehe")
        assertTrue(strichY < xHoehe * 0.75f, "Strich sitzt zu hoch: $strichY bei x-Hoehe $xHoehe")
    }

    @Test
    fun `Gedankenstrich ist laenger als der Bindestrich`() {
        val font = Fonts.load("script-simplex")
        fun breite(cp: Int): Float = assertNotNull(font.glyph(cp)).strokes
            .flatMap { it.points }.let { p -> p.maxOf { it.x } - p.minOf { it.x } }

        // Im Deutschen sind das zwei verschiedene Zeichen mit verschiedener Laenge.
        assertTrue(breite(0x2013) > breite('-'.code), "Halbgeviertstrich nicht laenger")
        assertTrue(breite(0x2014) > breite(0x2013), "Geviertstrich nicht laenger")
    }

    @Test
    fun `Vorschub passt zur Strichlaenge`() {
        val font = Fonts.load("sans")
        val strich = assertNotNull(font.glyph('-'.code))
        val n = assertNotNull(font.glyph('n'.code))
        // Ein kurzer Strich darf nicht den Platz eines breiten Buchstabens beanspruchen.
        assertTrue(
            strich.advance < n.advance,
            "Vorschub des Strichs (${strich.advance}) nicht kleiner als der von 'n' (${n.advance})",
        )
    }
}
