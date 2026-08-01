package de.emmpunkt.write.core.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HersheyFontTest {

    /**
     * Der Referenzfall aus der Originalnotiz von James Hurt (fonts/HERSHEY-NOTICE.txt):
     * "8 9MWOMOV RUMUV ROQUQ" ergibt ein 'H' mit linkem Bearing -5 und rechtem Bearing +5.
     *
     * Die Zeile wird auf die Spaltenbreiten des Formats gebracht: 5 Zeichen Nummer,
     * 3 Zeichen Vertex-Anzahl, dann der Rumpf.
     */
    private val notesExample = "    8  9MWOMOV RUMUV ROQUQ"

    /**
     * Baut eine JHF-Datei, in der [glyphLine] genau auf dem Codepoint von 'H' liegt.
     * Davor stehen leere Fuellglyphen (nur Bearing-Paar, wie ein Leerzeichen).
     * Damit wird zugleich geprueft, dass Zeile n auf Codepoint 32 + n abgebildet wird.
     */
    private fun miniFont(glyphLine: String): String {
        val filler = "    1  1JZ"
        val lines = MutableList('H'.code - 32) { filler }
        lines += glyphLine
        return lines.joinToString("\n")
    }

    @Test
    fun `parst das H aus der Originalnotiz`() {
        val font = HersheyFont.parse("test", "Test", miniFont(notesExample))
        val glyph = assertNotNull(font.glyph('H'.code))

        // Drei Strichzuege: linker Stamm, rechter Stamm, Querbalken.
        assertEquals(3, glyph.strokes.size)

        // Bearings -5 und +5 ergeben die Vorschubbreite 10.
        assertEquals(10f, glyph.advance)

        // X ist um das linke Bearing (-5) verschoben: aus O = -3 wird -3 - (-5) = 2.
        assertEquals(2f, glyph.strokes[0].points[0].x)
        assertEquals(8f, glyph.strokes[1].points[0].x)

        // Versalhoehe des H: von -5 bis 4 in JHF-Einheiten sind 9.
        assertEquals(9f, font.capHeightUnits)
    }

    @Test
    fun `spiegelt die Y-Achse und legt den Ursprung auf die Grundlinie`() {
        val font = HersheyFont.parse("test", "Test", miniFont(notesExample))
        val glyph = assertNotNull(font.glyph('H'.code))
        val ys = glyph.strokes.flatMap { it.points }.map { it.y }

        // Die Grundlinie ist 0, die Oberkante liegt bei der Versalhoehe - nicht darunter.
        assertEquals(0f, ys.min())
        assertEquals(9f, ys.max())
    }

    @Test
    fun `erkennt Pen-Up und trennt die Strichzuege korrekt`() {
        val font = HersheyFont.parse("test", "Test", miniFont(notesExample))
        val glyph = assertNotNull(font.glyph('H'.code))
        // Jeder der drei Zuege besteht aus genau zwei Punkten.
        glyph.strokes.forEach { assertEquals(2, it.points.size) }
    }

    @Test
    fun `mitgelieferte Schriften laden und decken ASCII ab`() {
        Fonts.available.forEach { entry ->
            val font = Fonts.load(entry.id)
            assertTrue(font.capHeightUnits > 0f, "${entry.id}: Versalhoehe nicht positiv")

            // ASCII 32..126 muss die Schrift vollstaendig koennen.
            (32..126).forEach { cp ->
                assertTrue(font.has(cp), "${entry.id}: Zeichen '${cp.toChar()}' ($cp) fehlt")
            }
        }
    }

    @Test
    fun `Leerzeichen hat Vorschub aber keine Striche`() {
        val font = Fonts.load("sans")
        val space = assertNotNull(font.glyph(' '.code))
        assertTrue(space.strokes.isEmpty(), "Leerzeichen darf keine Striche haben")
        assertTrue(space.advance > 0f, "Leerzeichen braucht einen Vorschub")
    }

    @Test
    fun `Schreibschrift zeichnet Buchstaben in einem Zug`() {
        val script = Fonts.load("script-simplex")
        val sans = Fonts.load("sans")

        // Das 'A' der Schreibschrift ist ein durchgehender Zug, das der Sans-Schrift nicht.
        // Genau darin besteht der Unterschied zwischen geschrieben und konstruiert.
        assertEquals(1, assertNotNull(script.glyph('A'.code)).strokes.size)
        assertTrue(assertNotNull(sans.glyph('A'.code)).strokes.size > 1)
    }
}
