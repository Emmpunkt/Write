package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text in 90-Grad-Schritten drehen.
 *
 * Anlass ist die Maschine: A6 hoch (105 x 148 mm) passt nicht auf den Tisch (155 x 105 mm).
 * Wer hochkant schreiben will, legt das Blatt quer und dreht den Text.
 *
 * Gedreht wird der SATZ im Rahmen, nicht der Rahmen. Bei 90 und 270 Grad wird deshalb auf einer
 * Flaeche mit vertauschten Massen gesetzt und das Ergebnis hineingedreht.
 */
class DrehungTest {

    private val font = Fonts.load("sans")
    private val rahmen = Frame(widthMm = 140f, heightMm = 90f, margins = Margins.all(0f))
    private val stil = TextStyle("sans", sizeMm = 5f)

    private fun satz(text: String, drehung: Drehung, frame: Frame = rahmen) =
        layoutAbsaetze(text.split('\n').map { AbsatzSatz(it, stil, font) }, frame, drehung)

    @Test
    fun `ohne Drehung bleibt alles, wie es war`() {
        val text = "Milch Brot Kaffee Butter Eier"
        assertEquals(satz(text, Drehung.GRAD_0), layoutAbsaetze(
            text.split('\n').map { AbsatzSatz(it, stil, font) },
            rahmen,
        ))
    }

    @Test
    fun `jede Drehung bleibt innerhalb des Rahmens`() {
        val text = "Milch Brot Kaffee Butter Eier Mehl Zucker Salz"
        Drehung.entries.forEach { drehung ->
            val punkte = satz(text, drehung).strokes.flatMap { it.points }
            assertTrue(punkte.isNotEmpty(), "Kein Strich bei $drehung")
            assertTrue(
                punkte.all { it.x >= -0.01f && it.x <= rahmen.widthMm + 0.01f },
                "Bei $drehung laeuft der Text seitlich aus dem Rahmen: " +
                    "${punkte.minOf { it.x }} .. ${punkte.maxOf { it.x }}",
            )
            assertTrue(
                punkte.all { it.y >= -0.01f && it.y <= rahmen.heightMm + 0.01f },
                "Bei $drehung laeuft der Text oben oder unten heraus: " +
                    "${punkte.minOf { it.y }} .. ${punkte.maxOf { it.y }}",
            )
        }
    }

    @Test
    fun `bei 90 Grad laeuft die Schreibrichtung nach oben`() {
        val gerade = satz("AB", Drehung.GRAD_0)
        val gedreht = satz("AB", Drehung.GRAD_90)

        // Ungedreht steht das B rechts vom A, gedreht darueber.
        assertTrue(breite(gerade) > hoehe(gerade), "Ungedreht muesste 'AB' breiter als hoch sein")
        assertTrue(hoehe(gedreht) > breite(gedreht), "Gedreht muesste 'AB' hoeher als breit sein")
    }

    @Test
    fun `bei 90 Grad steht auf der schmalen Seite mehr Text uebereinander`() {
        // Der eigentliche Zweck: Im hochkant gedrehten Satz ist die Zeile so lang wie der
        // Rahmen HOCH ist - im Querformat passt deshalb mehr in eine Zeile.
        val text = "Milch Brot Kaffee Butter Eier Mehl Zucker Salz Pfeffer Oel Reis Nudeln Tee"
        val quer = satz(text, Drehung.GRAD_0)
        val hoch = satz(text, Drehung.GRAD_90)

        assertTrue(
            hoch.lines.size > quer.lines.size,
            "Auf der schmalen Seite (90 Grad) muesste der Text oefter umbrechen: " +
                "${hoch.lines.size} gegen ${quer.lines.size} Zeilen",
        )
    }

    @Test
    fun `180 Grad stellt den Text auf den Kopf, ohne ihn aus dem Rahmen zu schieben`() {
        val gerade = satz("Hallo", Drehung.GRAD_0)
        val kopf = satz("Hallo", Drehung.GRAD_180)

        assertEquals(gerade.lines.size, kopf.lines.size)
        // Was oben war, ist unten: die erste Zeile sitzt gespiegelt zur Rahmenmitte.
        val obenGerade = gerade.strokes.flatMap { it.points }.maxOf { it.y }
        val untenKopf = kopf.strokes.flatMap { it.points }.minOf { it.y }
        assertTrue(
            abs((rahmen.heightMm - obenGerade) - untenKopf) < 0.01f,
            "Der Abstand zum Rand muesste sich spiegeln",
        )
    }

    @Test
    fun `270 Grad dreht in die andere Richtung als 90 Grad`() {
        val gegenUhrzeiger = satz("A", Drehung.GRAD_90).strokes.flatMap { it.points }
        val mitUhrzeiger = satz("A", Drehung.GRAD_270).strokes.flatMap { it.points }

        // 90 Grad dreht GEGEN den Uhrzeigersinn: der Seitenkopf - und damit die erste Zeile -
        // kippt nach links. Bei 270 Grad entsprechend nach rechts. Der Nutzer waehlt die
        // Richtung, in der die Schrift auf dem liegenden Blatt richtig herum steht.
        assertTrue(
            gegenUhrzeiger.minOf { it.x } < rahmen.widthMm / 2f,
            "Bei 90 Grad muesste die erste Zeile links stehen",
        )
        assertTrue(
            mitUhrzeiger.maxOf { it.x } > rahmen.widthMm / 2f,
            "Bei 270 Grad muesste die erste Zeile rechts stehen",
        )
    }

    @Test
    fun `die Raender drehen mit`() {
        // Ein Rand nur oben: ungedreht bleibt oben Platz, bei 90 Grad rechts.
        val nurOben = Frame(100f, 100f, Margins(left = 0f, top = 20f, right = 0f, bottom = 0f))

        val gerade = layoutAbsaetze(listOf(AbsatzSatz("X", stil, font)), nurOben, Drehung.GRAD_0)
        val gedreht = layoutAbsaetze(listOf(AbsatzSatz("X", stil, font)), nurOben, Drehung.GRAD_90)

        assertTrue(
            gerade.strokes.flatMap { it.points }.maxOf { it.y } <= 100f - 20f + 0.01f,
            "Ungedreht muesste oben der Rand frei bleiben",
        )
        assertTrue(
            gedreht.strokes.flatMap { it.points }.maxOf { it.x } <= 100f - 20f + 0.01f,
            "Bei 90 Grad muesste aus dem oberen Rand der rechte werden",
        )
    }

    @Test
    fun `der Umbruch rechnet mit der gedrehten Zeilenlaenge`() {
        // Die Flaeche ist in beiden Lagen gleich gross - verschieden ist die Zeilenlaenge.
        // Ein langes Wort passt quer in eine Zeile und muss hochkant hart getrennt werden.
        val flach = Frame(widthMm = 140f, heightMm = 30f, margins = Margins.all(0f))
        val wort = "Donaudampfschifffahrt"

        assertEquals(
            emptySet(), satz(wort, Drehung.GRAD_0, flach).overlongWords,
            "Quer passt das Wort in eine Zeile",
        )
        assertEquals(
            setOf(wort), satz(wort, Drehung.GRAD_90, flach).overlongWords,
            "Hochkant ist die Zeile nur 30 mm lang - da muss getrennt werden",
        )
    }

    private fun breite(t: LaidOutText): Float {
        val p = t.strokes.flatMap { it.points }
        return p.maxOf { it.x } - p.minOf { it.x }
    }

    private fun hoehe(t: LaidOutText): Float {
        val p = t.strokes.flatMap { it.points }
        return p.maxOf { it.y } - p.minOf { it.y }
    }
}
