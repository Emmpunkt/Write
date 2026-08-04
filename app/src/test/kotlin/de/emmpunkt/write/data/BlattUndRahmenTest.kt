package de.emmpunkt.write.data

import de.emmpunkt.write.core.decor.RahmenForm
import de.emmpunkt.write.core.geometry.boundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Blatt und Textrahmen sind zwei verschiedene Dinge.
 *
 * Vorher waren sie eins: `paperWidthMm` war zugleich das Papier auf dem Tisch UND der Kasten,
 * in den der Text gesetzt wurde. Wer den Text auf einer grossen Karte klein und unten rechts
 * haben wollte, musste das Blatt kleinluegen und es ueber den Versatz an die richtige Stelle
 * schieben - das Blatt in der Vorschau zeigte dann nicht die Karte, sondern den Textkasten.
 *
 * Neu:
 *  - Blatt  = was tatsaechlich auf dem Tisch liegt. Groesse und Lage, global unter Optionen.
 *  - Rahmen = der Kasten fuer den Text, IM Blatt positioniert. Gehoert zum Dokument.
 */
class BlattUndRahmenTest {

    @Test
    fun `der Textsatz benutzt den Rahmen, nicht das Blatt`() {
        val s = AppSettings(
            paperWidthMm = 148f, paperHeightMm = 105f,
            rahmenBreiteMm = 40f, rahmenHoeheMm = 30f,
        )

        assertEquals(40f, s.toFrame().widthMm)
        assertEquals(30f, s.toFrame().heightMm)
    }

    @Test
    fun `der Rahmen hat keine eigenen Raender mehr`() {
        // Frueher sass der Rand INNEN im Blatt. Jetzt IST der Rahmen der nutzbare Bereich -
        // ein zweiter Rand darin waere nur eine zweite Stellschraube fuer dieselbe Sache.
        val f = AppSettings(rahmenBreiteMm = 40f, rahmenHoeheMm = 30f).toFrame()

        assertEquals(40f, f.usableWidthMm)
        assertEquals(30f, f.usableHeightMm)
    }

    @Test
    fun `der Ursprung auf dem Tisch ist Blattlage plus Rahmenlage`() {
        // Der Rahmen wird IM Blatt angegeben. Verschiebt der Nutzer das Blatt am Anschlag,
        // wandert jeder Rahmen mit - genau das erwartet man von einem Blatt.
        val s = AppSettings(
            paperOffsetXMm = 10f, paperOffsetYMm = 5f,
            rahmenXMm = 4f, rahmenYMm = 3f,
        )

        val profil = s.toMachineProfile()
        assertEquals(14f, profil.paperOffsetXMm)
        assertEquals(8f, profil.paperOffsetYMm)
    }

    @Test
    fun `das Blatt muss auf den Tisch passen`() {
        val passt = AppSettings(
            workAreaXMm = 155f, workAreaYMm = 105f,
            paperWidthMm = 148f, paperHeightMm = 105f,
            paperOffsetXMm = 5f, paperOffsetYMm = 0f,
        )
        assertTrue(passt.blattPasstAufTisch)

        assertFalse(passt.copy(paperOffsetXMm = 10f).blattPasstAufTisch)
    }

    @Test
    fun `der Rahmen muss auf das Blatt passen`() {
        val s = AppSettings(
            paperWidthMm = 100f, paperHeightMm = 60f,
            rahmenXMm = 10f, rahmenYMm = 10f,
            rahmenBreiteMm = 95f, rahmenHoeheMm = 40f,
        )

        assertFalse(s.rahmenPasstAufsBlatt, "10 + 95 ragt über das 100 mm breite Blatt hinaus")
        // Bis an die Blattkante ist erlaubt: 10 + 90 = genau 100.
        assertTrue(s.copy(rahmenBreiteMm = 90f).rahmenPasstAufsBlatt)
    }

    @Test
    fun `ein Rahmen mit negativem Versatz liegt nicht mehr auf dem Blatt`() {
        val s = AppSettings(rahmenXMm = -1f)

        assertFalse(s.rahmenPasstAufsBlatt)
    }

    @Test
    fun `Blatt fuellen legt den Rahmen um den Rand eingerueckt auf das Blatt`() {
        val s = AppSettings(paperWidthMm = 148f, paperHeightMm = 105f, marginMm = 8f)

        val gefuellt = s.blattFuellen()

        assertEquals(8f, gefuellt.rahmenXMm)
        assertEquals(8f, gefuellt.rahmenYMm)
        assertEquals(132f, gefuellt.rahmenBreiteMm)
        assertEquals(89f, gefuellt.rahmenHoeheMm)
    }

    @Test
    fun `Blatt fuellen laesst den Rand weg, wenn er das Blatt aufbrauchen wuerde`() {
        // Sonst entstuende ein Rahmen mit Breite 0 oder weniger - und `Frame` wirft dann im
        // Konstruktor. Ein Absturz beim Tippen einer Zahl waere die schlechteste Antwort.
        val s = AppSettings(paperWidthMm = 10f, paperHeightMm = 10f, marginMm = 8f)

        val gefuellt = s.blattFuellen()

        assertEquals(0f, gefuellt.rahmenXMm)
        assertEquals(10f, gefuellt.rahmenBreiteMm)
        assertEquals(10f, gefuellt.rahmenHoeheMm)
    }

    @Test
    fun `die Vorgabe schreibt genauso wie vor der Trennung`() {
        // A6 quer mit 8 mm Rand - der Rahmen der Vorgabe muss Punkt fuer Punkt dasselbe
        // Ergebnis liefern wie das alte Blatt-mit-Rand, sonst verrutscht jede bestehende
        // Notiz beim naechsten Plotten.
        val s = AppSettings()

        assertEquals(132f, s.toFrame().usableWidthMm)
        assertEquals(89f, s.toFrame().usableHeightMm)
        assertEquals(8f, s.toMachineProfile().paperOffsetXMm)
        assertEquals(8f, s.toMachineProfile().paperOffsetYMm)
    }

    // ---- Gezeichneter Rahmen ----

    @Test
    fun `ohne Rahmenform wird nichts gezeichnet`() {
        assertEquals(emptyList(), AppSettings().zierrahmenZuege())
    }

    @Test
    fun `der gezeichnete Rahmen umschliesst den Textkasten mit Abstand`() {
        val s = AppSettings(
            rahmenXMm = 10f, rahmenYMm = 10f,
            rahmenBreiteMm = 100f, rahmenHoeheMm = 60f,
            rahmenForm = RahmenForm.RECHTECK, rahmenAbstandMm = 5f,
        )
        val box = s.zierrahmenZuege().boundingBox()!!

        // In RAHMEN-Koordinaten: der Kasten liegt bei 0..100 / 0..60, der Rahmen 5 mm darum.
        assertEquals(-5f, box.minX, 0.01f)
        assertEquals(-5f, box.minY, 0.01f)
        assertEquals(105f, box.maxX, 0.01f)
        assertEquals(65f, box.maxY, 0.01f)
    }

    @Test
    fun `ein Rahmen, der ueber das Blatt ragt, wird gemeldet`() {
        // Der Textkasten passt bequem, der Rahmen darum aber nicht mehr - genau der Fall, der
        // den Stift sonst neben die Karte auf den Tisch schreiben liesse.
        val s = AppSettings(
            paperWidthMm = 100f, paperHeightMm = 100f,
            rahmenXMm = 2f, rahmenYMm = 2f,
            rahmenBreiteMm = 96f, rahmenHoeheMm = 96f,
            rahmenForm = RahmenForm.RECHTECK, rahmenAbstandMm = 5f,
        )

        assertTrue(s.rahmenPasstAufsBlatt, "Der Textkasten selbst passt")
        assertFalse(s.zierrahmenPasstAufsBlatt, "Der Rahmen darum passt nicht mehr")
    }

    @Test
    fun `ohne Rahmenform ist die Blattpruefung immer zufrieden`() {
        val s = AppSettings(rahmenXMm = 0f, rahmenYMm = 0f, rahmenAbstandMm = 50f)
        assertTrue(s.zierrahmenPasstAufsBlatt)
    }
}
