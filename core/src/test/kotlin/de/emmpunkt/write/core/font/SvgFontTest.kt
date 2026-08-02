package de.emmpunkt.write.core.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SvgFontTest {

    /**
     * Ein kleiner Font mit bekannten Werten. Enthaelt alles, worauf es ankommt: ein 'H' fuer
     * die Versalhoehe, Ober- und Unterlaenge fuer die Zeilenhoehe, ein HTML-kodiertes Umlaut-
     * zeichen, eine Bezierkurve und ein Leerzeichen ohne Pfad.
     */
    private val miniFont = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <svg xmlns="http://www.w3.org/2000/svg" version="1.1">
        <defs>
        <font id="Mini" horiz-adv-x="400" >
        <font-face units-per-em="1000" ascent="800" descent="-200" cap-height="500" />
        <glyph unicode=" " glyph-name="space" horiz-adv-x="200" />
        <glyph unicode="H" glyph-name="H" horiz-adv-x="600" d="M 100 0 L 100 700 M 500 0 L 500 700 M 100 350 L 500 350" />
        <glyph unicode="h" glyph-name="h" horiz-adv-x="500" d="M 0 0 L 0 720" />
        <glyph unicode="g" glyph-name="g" horiz-adv-x="500" d="M 0 300 L 0 -250" />
        <glyph unicode="&#xe4;" glyph-name="adieresis" horiz-adv-x="450" d="M 10 20 L 30 40" />
        <glyph unicode="C" glyph-name="C" horiz-adv-x="500" d="M 0 0 C 100 200 300 200 400 0" />
        </font>
        </defs>
        </svg>
    """.trimIndent()

    private fun mini() = SvgFont.parse("mini", "Mini", miniFont)

    /**
     * Das 'H' hat bewusst KEIN eigenes horiz-adv-x. Nach SVG 1.1 muss es dann den Wert vom
     * umschliessenden <font>-Element erben (hier 333) - deckt genau die Vererbungsregel ab,
     * die miniFont nicht prueft, weil dort jede Glyphe ihren eigenen Vorschub traegt.
     */
    private val erbschaftFont = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <svg xmlns="http://www.w3.org/2000/svg" version="1.1">
        <defs>
        <font id="Erbschaft" horiz-adv-x="333" >
        <font-face units-per-em="1000" ascent="800" descent="-200" cap-height="500" />
        <glyph unicode="H" glyph-name="H" d="M 100 0 L 100 700 M 500 0 L 500 700 M 100 350 L 500 350" />
        </font>
        </defs>
        </svg>
    """.trimIndent()

    @Test
    fun `liest Vorschub und Punkte einer Glyphe`() {
        val glyph = assertNotNull(mini().glyph('H'.code))

        assertEquals(600f, glyph.advance)
        assertEquals(3, glyph.strokes.size, "M beginnt jeweils einen neuen Zug")
        assertEquals(100f, glyph.strokes[0].points[0].x)
        assertEquals(0f, glyph.strokes[0].points[0].y)
        assertEquals(700f, glyph.strokes[0].points[1].y)
    }

    @Test
    fun `leitet die Versalhoehe aus dem H ab, nicht aus dem Attribut cap-height`() {
        // Die Datei behauptet cap-height="500", das H ist aber 700 hoch. Alle vier
        // mitgelieferten SVG-Schriften haben genau diesen Fehler.
        assertEquals(700f, mini().capHeightUnits)
    }

    @Test
    fun `leitet die Zeilenhoehe aus Ober- und Unterlaenge ab`() {
        // h reicht bis 720, g bis -250.
        assertEquals(970f, mini().lineHeightUnits)
    }

    @Test
    fun `entkodiert HTML-kodierte Zeichen`() {
        // Ohne Entkodierung fehlten ausgerechnet die deutschen Umlaute.
        val umlaut = assertNotNull(mini().glyph(0x00E4), "ae (&#xe4;) muss vorhanden sein")
        assertEquals(450f, umlaut.advance)
    }

    @Test
    fun `unterteilt eine Bezierkurve und behaelt Anfang und Ende exakt`() {
        val glyph = assertNotNull(mini().glyph('C'.code))
        val punkte = glyph.strokes.single().points

        assertEquals(9, punkte.size, "Startpunkt plus acht Teilstuecke")
        assertEquals(0f, punkte.first().x)
        assertEquals(0f, punkte.first().y)
        assertEquals(400f, punkte.last().x, 0.01f)
        assertEquals(0f, punkte.last().y, 0.01f)

        // Die Kurve woelbt sich nach oben, die Zwischenpunkte liegen also ueber der Sehne.
        assertTrue(punkte.drop(1).dropLast(1).all { it.y > 0f }, "Zwischenpunkte muessen gewoelbt liegen")
        // Und sie laufen monoton nach rechts.
        assertTrue(punkte.zipWithNext().all { (a, b) -> b.x > a.x }, "x muss monoton wachsen")
    }

    @Test
    fun `Leerzeichen hat Vorschub aber keine Striche`() {
        val space = assertNotNull(mini().glyph(' '.code))
        assertTrue(space.strokes.isEmpty())
        assertEquals(200f, space.advance)
    }

    @Test
    fun `Glyphe ohne eigenes horiz-adv-x erbt den Vorschub vom umschliessenden font-Element`() {
        val glyph = assertNotNull(SvgFont.parse("erbschaft", "Erbschaft", erbschaftFont).glyph('H'.code))
        assertEquals(333f, glyph.advance)
    }

    @Test
    fun `mitgelieferte SVG-Schriften koennen deutsche Notizen`() {
        val noetig = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789äöüÄÖÜß.,;:!?-()€"
        listOf("allure", "zierschrift", "druckschrift", "einladung").forEach { id ->
            assertEquals(
                id, Fonts.entry(id).id,
                "$id muss ein eigener Eintrag sein - Fonts.entry faellt sonst still auf die " +
                    "Vorgabeschrift zurueck, und dieser Test prueft dann die falsche Schrift",
            )
            val font = Fonts.load(id)
            noetig.forEach { ch ->
                assertTrue(font.has(ch.code), "$id: Zeichen '$ch' fehlt")
            }
            assertTrue(font.capHeightUnits > 0f, "$id: Versalhoehe nicht positiv")
            assertTrue(font.lineHeightUnits > font.capHeightUnits, "$id: Zeilenhoehe unplausibel")
        }
    }

    /**
     * Plausibilitaetsprobe fuer die abgeleiteten Metriken, ueber alle sieben mitgelieferten
     * Schriften: das Verhaeltnis von Oberlaenge zu Versalhoehe muss nahe beieinander liegen,
     * weil dieselbe Handschrift-Proportion dahintersteckt.
     *
     * Vor der Behebung von Befund 1 mass ascenderUnits ueber alle Glyphen der Schriftdatei statt
     * ueber den Zeichenvorrat, den der Nutzer tatsaechlich eintippen kann. Die vier EMS-Schriften
     * enthalten je 216 Glyphen inklusive Latin-Extended-Akzentbuchstaben (u. a. Ŭ, Ć, Å, Ą), die
     * weit ueber die Versalhoehe hinausragen - das Verhaeltnis lag dadurch bei 1,39 bis 1,59
     * statt wie bei den Hershey-Schriften bei rund 1,28. Diese Grenze liegt so, dass sie vor der
     * Behebung fehlgeschlagen waere (gegengeprueft), nach der Behebung aber fuer alle sieben
     * Schriften erfuellt ist.
     */
    @Test
    fun `Verhaeltnis von Oberlaenge zu Versalhoehe liegt bei allen Schriften nahe beieinander`() {
        Fonts.available.forEach { e ->
            val font = Fonts.load(e.id)
            val ratio = font.ascenderUnits / font.capHeightUnits
            assertTrue(
                ratio in 1.0f..1.45f,
                "${e.id}: Verhaeltnis Oberlaenge/Versalhoehe $ratio liegt ausserhalb des " +
                    "plausiblen Bereichs 1.0..1.45",
            )
        }
    }
}
