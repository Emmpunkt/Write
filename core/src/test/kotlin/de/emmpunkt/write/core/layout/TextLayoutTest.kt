package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextLayoutTest {

    private val font = Fonts.load("sans")
    private val frame = Frame(widthMm = 105f, heightMm = 148f, margins = Margins.all(10f))
    private val style = TextStyle(fontId = "sans", sizeMm = 5f)

    @Test
    fun `bricht an Wortgrenzen und haelt die nutzbare Breite ein`() {
        val text = "Milch Brot Kaffee Butter Eier Mehl Zucker Salz Pfeffer Oel Reis Nudeln"
        val result = layoutText(text, style, frame, font)

        assertTrue(result.lines.size > 1, "Text muesste umbrechen")
        result.lines.forEach { line ->
            assertTrue(
                line.widthMm <= frame.usableWidthMm + 0.01f,
                "Zeile zu breit: '${line.text}' (${line.widthMm} mm)",
            )
            assertFalse(line.text.startsWith(" "), "Zeile beginnt mit Leerzeichen")
        }
        // Kein Wort darf beim Umbruch verloren gehen.
        assertEquals(
            text.split(' '),
            result.lines.flatMap { it.text.split(' ') }.filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `respektiert harte Zeilenumbrueche`() {
        val result = layoutText("Eins\nZwei\nDrei", style, frame, font)
        assertEquals(listOf("Eins", "Zwei", "Drei"), result.lines.map { it.text })
    }

    @Test
    fun `leere Zeilen bleiben als Abstand erhalten`() {
        val result = layoutText("Oben\n\nUnten", style, frame, font)
        assertEquals(3, result.lines.size)
        assertEquals("", result.lines[1].text)
    }

    @Test
    fun `trennt ein Wort das allein zu breit ist`() {
        val schmal = Frame(widthMm = 40f, heightMm = 100f, margins = Margins.all(5f))
        val result = layoutText("Donaudampfschifffahrtsgesellschaft", style, schmal, font)

        assertTrue(result.lines.size > 1, "Ueberlanges Wort muss getrennt werden")
        result.lines.forEach {
            assertTrue(it.widthMm <= schmal.usableWidthMm + 0.01f, "Zeile ragt heraus: '${it.text}'")
        }
        assertEquals(
            "Donaudampfschifffahrtsgesellschaft",
            result.lines.joinToString("") { it.text },
        )
    }

    @Test
    fun `richtet links zentriert und rechts korrekt aus`() {
        val text = "Kurz"
        val left = layoutText(text, style.copy(align = Align.LEFT), frame, font).lines.first()
        val center = layoutText(text, style.copy(align = Align.CENTER), frame, font).lines.first()
        val right = layoutText(text, style.copy(align = Align.RIGHT), frame, font).lines.first()

        val leftX = left.strokes.flatMap { it.points }.minOf { it.x }
        val centerX = center.strokes.flatMap { it.points }.minOf { it.x }
        val rightX = right.strokes.flatMap { it.points }.minOf { it.x }

        assertTrue(leftX < centerX, "Zentriert muss weiter rechts beginnen als linksbuendig")
        assertTrue(centerX < rightX, "Rechtsbuendig muss weiter rechts beginnen als zentriert")

        // Rechter Rand: das Zeilenende liegt am rechten Rand des nutzbaren Bereichs.
        val rightEnd = frame.margins.left + frame.usableWidthMm
        assertTrue(
            abs((rightX + right.widthMm) - rightEnd) < 1.0f,
            "Rechtsbuendige Zeile endet nicht am rechten Rand",
        )
    }

    @Test
    fun `Text bleibt innerhalb der Raender`() {
        val result = layoutText("Einkaufsliste fuer Samstag", style, frame, font)
        val points = result.strokes.flatMap { it.points }

        assertTrue(points.minOf { it.x } >= frame.margins.left - 0.01f, "ragt links heraus")
        assertTrue(
            points.maxOf { it.x } <= frame.widthMm - frame.margins.right + 0.01f,
            "ragt rechts heraus",
        )
        assertTrue(
            points.maxOf { it.y } <= frame.heightMm - frame.margins.top + 0.01f,
            "ragt oben heraus",
        )
    }

    @Test
    fun `meldet Ueberlauf statt abzuschneiden`() {
        val winzig = Frame(widthMm = 105f, heightMm = 30f, margins = Margins.all(5f))
        val vieleZeilen = (1..20).joinToString("\n") { "Zeile $it" }
        val result = layoutText(vieleZeilen, style, winzig, font)

        assertTrue(result.overflow, "Ueberlauf muss gemeldet werden")
        // Nichts darf verloren gehen - der Nutzer entscheidet, was passiert.
        assertEquals(20, result.lines.size)
    }

    @Test
    fun `passender Text meldet keinen Ueberlauf`() {
        val result = layoutText("Kurze Notiz", style, frame, font)
        assertFalse(result.overflow)
    }

    @Test
    fun `groessere Schrift erzeugt proportional breitere Zeilen`() {
        val klein = layoutText("Hallo", style.copy(sizeMm = 5f), frame, font).lines.first()
        val gross = layoutText("Hallo", style.copy(sizeMm = 10f), frame, font).lines.first()
        assertTrue(
            abs(gross.widthMm - 2f * klein.widthMm) < 0.01f,
            "Doppelte Groesse muss doppelte Breite ergeben",
        )
    }

    @Test
    fun `Schriftgroesse in mm entspricht der Versalhoehe auf dem Papier`() {
        val result = layoutText("H", style.copy(sizeMm = 8f), frame, font)
        val points = result.strokes.flatMap { it.points }
        val hoehe = points.maxOf { it.y } - points.minOf { it.y }

        // Das ist die Zusage an den Nutzer: "8 mm" heisst 8 mm hohe Grossbuchstaben.
        assertTrue(abs(hoehe - 8f) < 0.01f, "Versalhoehe war $hoehe mm statt 8 mm")
    }

    @Test
    fun `Neigung schert um die Grundlinie`() {
        val gerade = layoutText("l", style, frame, font).lines.first()
        val schraeg = layoutText("l", style.copy(slantDeg = 15f), frame, font).lines.first()

        val baseline = gerade.baselineYMm
        // Auf Grundlinienhoehe bleibt die Position gleich, darueber wandert sie nach rechts.
        val geradeOben = gerade.strokes.flatMap { it.points }.maxByOrNull { it.y }!!
        val schraegOben = schraeg.strokes.flatMap { it.points }.maxByOrNull { it.y }!!

        assertTrue(schraegOben.x > geradeOben.x, "Neigung wirkt nicht")
        assertTrue(abs(schraegOben.y - geradeOben.y) < 0.01f, "Neigung darf die Hoehe nicht aendern")
        assertTrue(geradeOben.y > baseline, "Testannahme: Oberkante liegt ueber der Grundlinie")
    }

    @Test
    fun `reicht fehlende Zeichen nach oben durch`() {
        val result = layoutText("Grüße Ω", style, frame, font)
        assertTrue('Ω'.code in result.unsupported)
        assertTrue('ü'.code !in result.unsupported)
    }

    @Test
    fun `Zeilen laufen von oben nach unten`() {
        val result = layoutText("Eins\nZwei\nDrei", style, frame, font)
        val baselines = result.lines.map { it.baselineYMm }
        assertEquals(baselines.sortedDescending(), baselines, "Zeilen muessen abwaerts laufen")
    }
}

/**
 * Woerter duerfen nicht stillschweigend mitten im Wort getrennt werden. Wo es unvermeidlich
 * ist, muss die App es melden - und ein vom Nutzer gesetzter Bindestrich muss als Trennstelle
 * wirken, sonst kann er das Problem gar nicht beheben.
 */
class WorttrennungTest {

    private val font = Fonts.load("sans")
    // Breit genug fuer "Donaudampf-" allein, zu schmal fuer das ganze Wort - genau der Fall,
    // in dem sich Bindestrich-Trennung und Notbehelf unterscheiden muessen.
    private val schmal = Frame(widthMm = 80f, heightMm = 100f, margins = Margins.all(5f))
    private val style = TextStyle(fontId = "sans", sizeMm = 5f)

    @Test
    fun `bricht nach einem Bindestrich um statt mitten im Wort`() {
        val result = layoutText("Donaudampf-schifffahrt", style, schmal, font)

        assertTrue(result.lines.size > 1, "Text muesste umbrechen")
        // Der Strich bleibt am Ende seiner Zeile stehen - so trennt man von Hand.
        assertEquals("Donaudampf-", result.lines[0].text)
        assertEquals("schifffahrt", result.lines[1].text)
        // Und es gilt nicht als harte Trennung.
        assertTrue(
            result.overlongWords.isEmpty(),
            "Bindestrich-Trennung darf nicht als Notbehelf gemeldet werden: ${result.overlongWords}",
        )
    }

    @Test
    fun `meldet Woerter die hart getrennt werden mussten`() {
        val result = layoutText("Donaudampfschifffahrtsgesellschaft", style, schmal, font)

        assertTrue(
            "Donaudampfschifffahrtsgesellschaft" in result.overlongWords,
            "Hart getrenntes Wort wurde nicht gemeldet: ${result.overlongWords}",
        )
        // Trotzdem darf nichts ueber den Rand laufen.
        result.lines.forEach {
            assertTrue(it.widthMm <= schmal.usableWidthMm + 0.01f, "Zeile ragt heraus: '${it.text}'")
        }
    }

    @Test
    fun `meldet nichts wenn alles sauber umbricht`() {
        val result = layoutText("Milch Brot Kaffee", style, schmal, font)
        assertTrue(result.overlongWords.isEmpty(), "Falscher Alarm: ${result.overlongWords}")
    }

    @Test
    fun `mehrere Bindestriche ergeben mehrere Trennstellen`() {
        val result = layoutText("Ober-Unter-Mittelstufe", style, schmal, font)
        assertTrue(result.overlongWords.isEmpty(), "Sollte an den Strichen umbrechen")
        // Jede Zeile ausser der letzten endet auf einem Bindestrich.
        result.lines.dropLast(1).forEach {
            assertTrue(it.text.endsWith("-"), "Zeile endet nicht am Trennstrich: '${it.text}'")
        }
        assertEquals("Ober-Unter-Mittelstufe", result.lines.joinToString("") { it.text })
    }

    @Test
    fun `ein fuehrender Strich ist keine Trennstelle`() {
        // "-5 Grad" darf nicht nach dem Minus umbrechen.
        val result = layoutText("-5", style, schmal, font)
        assertEquals(listOf("-5"), result.lines.map { it.text })
    }

    @Test
    fun `Text bleibt vollstaendig erhalten`() {
        val text = "Donaudampfschifffahrtsgesellschaft und Ober-Unter-Mittelstufe"
        val result = layoutText(text, style, schmal, font)
        val zusammen = result.lines.joinToString("") { it.text }
        assertEquals(text.replace(" ", ""), zusammen.replace(" ", ""))
    }
}
