package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import kotlin.test.Test
import kotlin.test.assertEquals

class VorlageUmwandlungTest {

    private val vorgabe = AppSettings()

    private fun vorlage() = TemplateEntity(
        id = 1L,
        name = "Platzkarten",
        text = "{anrede} {name},",
        werte = "Liebe;Anna",
        updatedAt = 1000L,
        fontId = "serif",
        sizeMm = 11f,
        align = Align.CENTER.name,
        lineSpacing = 1.4f,
        letterSpacing = 0.2f,
        wordSpacing = -0.1f,
        slantDeg = 12f,
        paperWidthMm = 100f,
        paperHeightMm = 70f,
        marginMm = 6f,
        paperOffsetXMm = 12f,
        paperOffsetYMm = 8f,
    )

    @Test
    fun `die Vorlage legt Schriftbild UND Blatt ueber die Einstellungen`() {
        // Der Unterschied zur Notiz: eine Grusskarte bringt ihr Format mit.
        val s = vorgabe.mitVorlage(vorlage())

        assertEquals("serif", s.fontId)
        assertEquals(11f, s.sizeMm)
        assertEquals(Align.CENTER, s.align)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(0.2f, s.letterSpacing)
        assertEquals(-0.1f, s.wordSpacing)
        assertEquals(12f, s.slantDeg)
        assertEquals(100f, s.paperWidthMm)
        assertEquals(70f, s.paperHeightMm)
        assertEquals(6f, s.marginMm)
    }

    @Test
    fun `die Vorlage bringt auch ihre Position mit`() {
        // Korrektur vom 2026-08-04: Der Rahmen ist in Wahrheit eine Textbox, kein Blatt am
        // Anschlag. Wer auf einer Grusskarte unten rechts schreiben will, braucht Groesse UND
        // Position in derselben Vorlage. Vorher war der Versatz global - dadurch liess sich
        // die Position im Serienmodus ueberhaupt nicht einstellen.
        val eigene = vorgabe.copy(paperOffsetXMm = 5f, paperOffsetYMm = 7f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals(12f, s.paperOffsetXMm)
        assertEquals(8f, s.paperOffsetYMm)
    }

    @Test
    fun `eine neue Vorlage uebernimmt die globale Position als Ausgangspunkt`() {
        // Die globalen Werte bleiben die Vorgabe - nur eben nicht mehr bindend.
        val eigene = vorgabe.copy(paperOffsetXMm = 5f, paperOffsetYMm = 7f)
        val neu = neueVorlage(eigene, jetzt = 2000L)

        assertEquals(5f, neu.paperOffsetXMm)
        assertEquals(7f, neu.paperOffsetYMm)
    }

    @Test
    fun `Maschine und Verbindung bleiben unberuehrt`() {
        // Sie beschreiben das Geraet, nicht das Dokument - anders als der Rahmen.
        val eigene = vorgabe.copy(host = "10.0.0.9", feedDrawMmMin = 900, workAreaXMm = 300f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals("10.0.0.9", s.host)
        assertEquals(900, s.feedDrawMmMin)
        assertEquals(300f, s.workAreaXMm)
    }

    @Test
    fun `spaetere Aenderungen an der Maschine erreichen eine offene Vorlage`() {
        // Der Serie-Reiter hatte die globalen Werte beim Oeffnen eingefroren: eine Aenderung
        // an Host oder Vorschub wirkte danach nicht mehr, und der Satz lief mit alten Werten.
        // Neu aufgelegt statt neu geladen ist der Weg, das im ViewModel zu beheben.
        val alt = vorgabe.mitVorlage(vorlage())
        val neueGlobale = vorgabe.copy(host = "10.0.0.9", feedDrawMmMin = 900)

        val frisch = neueGlobale.mitVorlage(
            alt.zuVorlage(id = 1L, name = "x", text = "{name}", werte = "", jetzt = 0L),
        )

        assertEquals("10.0.0.9", frisch.host, "Die neue Maschinenadresse kam nicht an")
        assertEquals(900, frisch.feedDrawMmMin)
        // ... und der Rahmen der Vorlage hat das ueberlebt.
        assertEquals(100f, frisch.paperWidthMm)
        assertEquals(12f, frisch.paperOffsetXMm)
    }

    @Test
    fun `unbekannte Ausrichtung faellt auf die Vorgabe zurueck`() {
        val kaputt = vorlage().copy(align = "SCHRAEG_VON_UNTEN")

        assertEquals(AppSettings().align, vorgabe.mitVorlage(kaputt).align)
    }

    @Test
    fun `hin und zurueck erhaelt die Vorlage`() {
        val original = vorlage()
        val zurueck = vorgabe.mitVorlage(original)
            .zuVorlage(
                id = 1L,
                name = "Platzkarten",
                text = "{anrede} {name},",
                werte = "Liebe;Anna",
                jetzt = 1000L,
            )

        assertEquals(original, zurueck)
    }

    @Test
    fun `eine neue Vorlage uebernimmt die aktuellen Einstellungen und ist sonst leer`() {
        val eigene = vorgabe.copy(fontId = "serif", sizeMm = 9f, paperWidthMm = 120f)
        val neu = neueVorlage(eigene, jetzt = 2000L)

        assertEquals(0L, neu.id, "Eine neue Vorlage darf noch keine Kennung haben")
        assertEquals("serif", neu.fontId)
        assertEquals(9f, neu.sizeMm)
        assertEquals(120f, neu.paperWidthMm)
        assertEquals("", neu.werte)
        assertEquals(2000L, neu.updatedAt)
    }

    @Test
    fun `eine neue Vorlage bringt einen Beispieltext mit Platzhalter mit`() {
        // Sonst startet der Nutzer mit einem leeren Feld und der Meldung "kein Platzhalter" -
        // ohne zu wissen, wie einer aussieht.
        val neu = neueVorlage(vorgabe, jetzt = 2000L)

        assertEquals(null, vorlagenFehler(neu.text))
    }
}
