package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Logik der Notizliste, geprueft ohne Datenbank und ohne Geraet.
 *
 * Genau dafuer liegt sie in reinen Funktionen: waere sie im DAO oder im ViewModel, braeuchte
 * jeder dieser Faelle einen Emulator.
 */
class NoteLogikTest {

    private val vorgabe = AppSettings()

    private fun notiz(
        text: String = "Test",
        fontId: String = "sans",
        sizeMm: Float = 9f,
    ) = NoteEntity(
        id = 1L,
        text = text,
        updatedAt = 1000L,
        fontId = fontId,
        sizeMm = sizeMm,
        align = Align.CENTER.name,
        lineSpacing = 1.4f,
        letterSpacing = 0.2f,
        wordSpacing = -0.1f,
        slantDeg = 12f,
    )

    // ---- Titel ----

    @Test
    fun `Titel ist die erste nicht-leere Zeile`() {
        assertEquals("Einkaufsliste", titelVon("Einkaufsliste\nMilch\nBrot"))
    }

    @Test
    fun `fuehrende Leerzeilen werden uebersprungen`() {
        // Sonst hiesse eine Notiz, die mit Absatz beginnt, dauerhaft "Ohne Titel".
        assertEquals("Milch", titelVon("\n\n  \nMilch\nBrot"))
    }

    @Test
    fun `leerer Text bekommt einen Ersatztitel`() {
        assertEquals("Ohne Titel", titelVon(""))
        assertEquals("Ohne Titel", titelVon("   \n \n "))
    }

    @Test
    fun `zu langer Titel wird gekuerzt`() {
        val lang = "A".repeat(80)
        val titel = titelVon(lang, maxLaenge = 40)

        assertTrue(titel.length <= 41, "Zu lang: ${titel.length}")
        assertTrue(titel.endsWith("…"), "Kuerzung nicht kenntlich gemacht: $titel")
    }

    @Test
    fun `Umlaute bleiben heil`() {
        assertEquals("Grüße an Lieselotte", titelVon("Grüße an Lieselotte\nZeile 2"))
    }

    @Test
    fun `Rand-Leerzeichen der Titelzeile fallen weg`() {
        assertEquals("Milch", titelVon("   Milch   \nBrot"))
    }

    // ---- Umwandlung ----

    @Test
    fun `Notiz legt ihr Schriftbild ueber die Einstellungen`() {
        val s = vorgabe.mitNotiz(notiz(fontId = "serif", sizeMm = 11f))

        assertEquals("serif", s.fontId)
        assertEquals(11f, s.sizeMm)
        assertEquals(Align.CENTER, s.align)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(0.2f, s.letterSpacing)
        assertEquals(-0.1f, s.wordSpacing)
        assertEquals(12f, s.slantDeg)
    }

    @Test
    fun `Blatt und Maschine bleiben beim Laden einer Notiz unberuehrt`() {
        // Der Kern der Entscheidung des Nutzers: ein Notizwechsel darf das eingelegte
        // Papier nicht "aendern", und an der Maschine schon gar nichts.
        val eigene = vorgabe.copy(
            paperWidthMm = 200f,
            paperHeightMm = 150f,
            marginMm = 15f,
            paperOffsetXMm = 5f,
            paperOffsetYMm = 7f,
            host = "10.0.0.9",
            feedDrawMmMin = 900,
        )
        val s = eigene.mitNotiz(notiz())

        assertEquals(200f, s.paperWidthMm)
        assertEquals(150f, s.paperHeightMm)
        assertEquals(15f, s.marginMm)
        assertEquals(5f, s.paperOffsetXMm)
        assertEquals(7f, s.paperOffsetYMm)
        assertEquals("10.0.0.9", s.host)
        assertEquals(900, s.feedDrawMmMin)
    }

    @Test
    fun `unbekannte Ausrichtung faellt auf die Vorgabe zurueck`() {
        // In der Datenbank steht der Enum-Name als Text. Wird der Enum spaeter umbenannt,
        // darf die App nicht abstuerzen, sondern muss weiterlaufen.
        val kaputt = notiz().copy(align = "SCHRAEG_VON_UNTEN")
        assertEquals(AppSettings().align, vorgabe.mitNotiz(kaputt).align)
    }

    @Test
    fun `hin und zurueck erhaelt das Schriftbild`() {
        val original = notiz(text = "Hallo")
        val zurueck = vorgabe.mitNotiz(original).zuNotiz(id = 1L, text = "Hallo", jetzt = 1000L)

        assertEquals(original, zurueck)
    }

    // ---- Neue Notiz ----

    @Test
    fun `neue Notiz erbt das Schriftbild der Vorlage aber nicht den Text`() {
        val vorlage = notiz(text = "Alter Text", fontId = "serif", sizeMm = 11f)
        val neu = neueNotiz(vorlage, vorgabe, jetzt = 2000L)

        assertEquals("", neu.text, "Der Text der Vorlage wurde mitgeschleppt")
        assertEquals("serif", neu.fontId)
        assertEquals(11f, neu.sizeMm)
        assertEquals(0L, neu.id, "Eine neue Notiz darf noch keine Kennung haben")
        assertEquals(2000L, neu.updatedAt)
    }

    @Test
    fun `ohne Vorlage gelten die Vorgabewerte`() {
        val neu = neueNotiz(null, vorgabe, jetzt = 2000L)

        assertEquals(vorgabe.fontId, neu.fontId)
        assertEquals(vorgabe.sizeMm, neu.sizeMm)
        assertEquals(vorgabe.align.name, neu.align)
    }
}
