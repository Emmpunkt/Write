package de.emmpunkt.write.data

import de.emmpunkt.write.core.decor.RahmenForm
import de.emmpunkt.write.core.decor.Zipfelseite
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.Drehung
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
        stile = stileAlsText(listOf(Absatzstil("Text", "serif", 11f, Align.CENTER))),
        absatzZuordnung = "",
        lineSpacing = 1.4f,
        letterSpacing = 0.2f,
        wordSpacing = -0.1f,
        slantDeg = 12f,
        rahmenXMm = 12f,
        rahmenYMm = 8f,
        rahmenBreiteMm = 100f,
        rahmenHoeheMm = 70f,
        drehung = Drehung.GRAD_0.name,
        rahmenForm = RahmenForm.KEINER.name,
        rahmenAbstandMm = 4f,
        zipfel = Zipfelseite.UNTEN_LINKS.name,
    )

    @Test
    fun `die Vorlage legt Schriftbild UND Textrahmen ueber die Einstellungen`() {
        // Der Unterschied zur Notiz: eine Grusskarte bringt ihren Textkasten mit.
        val s = vorgabe.mitVorlage(vorlage())

        assertEquals("serif", s.stile.single().fontId)
        assertEquals(11f, s.stile.single().sizeMm)
        assertEquals(Align.CENTER, s.stile.single().align)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(0.2f, s.letterSpacing)
        assertEquals(-0.1f, s.wordSpacing)
        assertEquals(12f, s.slantDeg)
        assertEquals(100f, s.rahmenBreiteMm)
        assertEquals(70f, s.rahmenHoeheMm)
    }

    @Test
    fun `die Vorlage bringt auch ihre Position mit`() {
        // Korrektur vom 2026-08-04: Der Rahmen ist eine Textbox, kein Blatt am Anschlag. Wer
        // auf einer Grusskarte unten rechts schreiben will, braucht Groesse UND Position in
        // derselben Vorlage - vorher war der Versatz global und im Serienmodus gar nicht
        // einstellbar.
        val eigene = vorgabe.copy(rahmenXMm = 5f, rahmenYMm = 7f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals(12f, s.rahmenXMm)
        assertEquals(8f, s.rahmenYMm)
    }

    @Test
    fun `das Blatt bleibt global und wandert nicht in die Vorlage`() {
        // Zweite Korrektur vom 2026-08-04: Blatt und Textrahmen sind zwei Dinge. Das Blatt
        // beschreibt das Papier auf dem Tisch - eine Vorlage darf es nicht mitbringen, sonst
        // wechselt beim Oeffnen einer Vorlage stillschweigend das eingelegte Format.
        val eigene = vorgabe.copy(paperWidthMm = 50f, paperHeightMm = 50f, paperOffsetXMm = 3f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals(50f, s.paperWidthMm)
        assertEquals(50f, s.paperHeightMm)
        assertEquals(3f, s.paperOffsetXMm)
    }

    @Test
    fun `eine neue Vorlage uebernimmt den aktuellen Rahmen als Ausgangspunkt`() {
        val eigene = vorgabe.copy(rahmenXMm = 5f, rahmenYMm = 7f)
        val neu = neueVorlage(eigene, jetzt = 2000L)

        assertEquals(5f, neu.rahmenXMm)
        assertEquals(7f, neu.rahmenYMm)
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
        assertEquals(100f, frisch.rahmenBreiteMm)
        assertEquals(12f, frisch.rahmenXMm)
    }

    @Test
    fun `unbekannte Ausrichtung faellt auf die Vorgabe zurueck`() {
        val kaputt = vorlage().copy(stile = "Text|serif|11.0|SCHRAEG_VON_UNTEN")

        assertEquals(Align.LEFT, vorgabe.mitVorlage(kaputt).stile.single().align)
    }

    @Test
    fun `die Drehung wandert mit der Vorlage`() {
        val hochkant = vorlage().copy(drehung = Drehung.GRAD_90.name)
        assertEquals(Drehung.GRAD_90, vorgabe.mitVorlage(hochkant).drehung)
    }

    @Test
    fun `eine unbekannte Drehung faellt auf aufrecht zurueck`() {
        val kaputt = vorlage().copy(drehung = "KOPFUEBER")
        assertEquals(Drehung.GRAD_0, vorgabe.mitVorlage(kaputt).drehung)
    }

    @Test
    fun `der gezeichnete Rahmen wandert mit der Vorlage`() {
        val mitRahmen = vorlage().copy(
            rahmenForm = RahmenForm.SPRECHBLASE.name,
            rahmenAbstandMm = 6f,
            zipfel = Zipfelseite.RECHTS.name,
        )
        val s = vorgabe.mitVorlage(mitRahmen)

        assertEquals(RahmenForm.SPRECHBLASE, s.rahmenForm)
        assertEquals(6f, s.rahmenAbstandMm)
        assertEquals(Zipfelseite.RECHTS, s.zipfel)
    }

    @Test
    fun `eine unbekannte Rahmenform faellt auf keinen Rahmen zurueck`() {
        val kaputt = vorlage().copy(rahmenForm = "BAROCK")
        assertEquals(RahmenForm.KEINER, vorgabe.mitVorlage(kaputt).rahmenForm)
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
        val eigene = vorgabe.copy(
            stile = listOf(Absatzstil("Text", "serif", 9f, Align.LEFT)),
            rahmenBreiteMm = 120f,
        )
        val neu = neueVorlage(eigene, jetzt = 2000L)

        assertEquals(0L, neu.id, "Eine neue Vorlage darf noch keine Kennung haben")
        assertEquals("serif", neu.stilListe().single().fontId)
        assertEquals(9f, neu.stilListe().single().sizeMm)
        assertEquals(120f, neu.rahmenBreiteMm)
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
