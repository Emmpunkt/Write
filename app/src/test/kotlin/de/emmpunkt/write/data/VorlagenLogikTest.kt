package de.emmpunkt.write.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vorlagen-Logik, geprueft ohne Datenbank und ohne Geraet.
 *
 * Sie liegt in reinen Funktionen, damit genau das moeglich ist - im ViewModel braeuchte jeder
 * dieser Faelle einen Emulator.
 */
class VorlagenLogikTest {

    // ---- Platzhalter finden ----

    @Test
    fun `Platzhalter stehen in der Reihenfolge ihres ersten Auftretens`() {
        assertEquals(
            listOf("anrede", "name"),
            platzhalterIn("{anrede} {name}, wir freuen uns!"),
        )
    }

    @Test
    fun `ein doppelt genannter Platzhalter zaehlt einmal`() {
        // Sonst erwartete die Werteliste zwei Spalten fuer dasselbe Feld.
        assertEquals(listOf("name"), platzhalterIn("{name}, ja du, {name}!"))
    }

    @Test
    fun `Umlaute im Platzhalternamen sind erlaubt`() {
        assertEquals(listOf("groesse", "größe"), platzhalterIn("{groesse} {größe}"))
    }

    @Test
    fun `Text ohne Platzhalter liefert nichts`() {
        assertEquals(emptyList(), platzhalterIn("Frohe Weihnachten"))
    }

    @Test
    fun `geschweifte Klammern ohne Inhalt sind kein Platzhalter`() {
        assertEquals(emptyList(), platzhalterIn("Ein Satz mit {} darin"))
    }

    // ---- Vorlage pruefen ----

    @Test
    fun `eine Vorlage ohne Platzhalter wird bemaengelt`() {
        val fehler = vorlagenFehler("Frohe Weihnachten")

        assertNotNull(fehler)
        assertTrue(fehler.contains("{name}"), "Die Meldung zeigt kein Beispiel: $fehler")
    }

    @Test
    fun `mehrere Platzhalter sind ausdruecklich in Ordnung`() {
        // Der Nutzer braucht sie fuer die Anrede: "Liebe" oder "Lieber", je nach Person.
        assertNull(vorlagenFehler("{anrede} {name},"))
    }

    // ---- Werteliste ----

    @Test
    fun `jede Zeile wird zu einem Bogen`() {
        val zeilen = werteZeilen("Liebe;Anna\nLieber;Bernd", listOf("anrede", "name"))

        assertEquals(2, zeilen.size)
        assertEquals(mapOf("anrede" to "Liebe", "name" to "Anna"), zeilen[0].felder)
        assertEquals(mapOf("anrede" to "Lieber", "name" to "Bernd"), zeilen[1].felder)
        assertTrue(zeilen.all { it.fehler == null })
    }

    @Test
    fun `leere Zeilen und Rand-Leerzeichen fallen weg`() {
        val zeilen = werteZeilen("\n  Liebe ; Anna  \n\n", listOf("anrede", "name"))

        assertEquals(1, zeilen.size)
        assertEquals(mapOf("anrede" to "Liebe", "name" to "Anna"), zeilen[0].felder)
    }

    @Test
    fun `die Nummer zaehlt Bogen, nicht Zeilen der Eingabe`() {
        // Eine Leerzeile in der Mitte darf die Bogennummern nicht verschieben - sonst passte
        // die Meldung nicht zum Zaehler des Serienlaufs.
        val zeilen = werteZeilen("Liebe;Anna\n\nLieber;Bernd", listOf("anrede", "name"))

        assertEquals(listOf(1, 2), zeilen.map { it.nummer })
    }

    @Test
    fun `zu wenige Felder werden gemeldet und nicht ergaenzt`() {
        val zeilen = werteZeilen("Liebe;Anna\nBernd", listOf("anrede", "name"))

        assertNull(zeilen[0].fehler)
        val fehler = zeilen[1].fehler
        assertNotNull(fehler)
        assertTrue(fehler.contains("Bogen 2"), "Ohne Bogennummer nicht auffindbar: $fehler")
        assertTrue(fehler.contains("anrede;name"), "Die erwartete Form fehlt: $fehler")
    }

    @Test
    fun `zu viele Felder werden ebenfalls gemeldet`() {
        val zeilen = werteZeilen("Liebe;Anna;Zusatz", listOf("anrede", "name"))

        assertNotNull(zeilen[0].fehler)
    }

    @Test
    fun `ein leeres Feld ist erlaubt`() {
        // Nicht jede Karte braucht jedes Feld - ein Titel fehlt oft berechtigt.
        val zeilen = werteZeilen(";Anna", listOf("titel", "name"))

        assertNull(zeilen[0].fehler)
        assertEquals("", zeilen[0].felder["titel"])
    }

    @Test
    fun `die Bezeichnung laesst leere Felder weg`() {
        val zeilen = werteZeilen(";Anna", listOf("titel", "name"))

        assertEquals("Anna", zeilen[0].bezeichnung)
    }

    @Test
    fun `ohne Spalten entsteht kein Bogen`() {
        // Eine Vorlage ohne Platzhalter wird schon von vorlagenFehler abgefangen; hier darf
        // nichts Halbfertiges herauskommen.
        assertEquals(emptyList(), werteZeilen("Anna\nBernd", emptyList()))
    }

    // ---- Einsetzen ----

    @Test
    fun `Platzhalter werden ersetzt`() {
        assertEquals(
            "Liebe Anna, wir freuen uns!",
            einsetzen("{anrede} {name}, wir freuen uns!", mapOf("anrede" to "Liebe", "name" to "Anna")),
        )
    }

    @Test
    fun `derselbe Platzhalter wird an jeder Stelle ersetzt`() {
        assertEquals("Anna, ja du, Anna!", einsetzen("{name}, ja du, {name}!", mapOf("name" to "Anna")))
    }

    @Test
    fun `ein unbekannter Platzhalter bleibt stehen`() {
        // Ein sichtbares {tisch} auf dem Bogen ist ein Fehler, den man sieht. Eine
        // stillschweigende Luecke waere schlimmer.
        assertEquals("Anna, Tisch {tisch}", einsetzen("{name}, Tisch {tisch}", mapOf("name" to "Anna")))
    }

    @Test
    fun `Sonderzeichen im Wert werden nicht als Ersetzungsmuster gedeutet`() {
        // Regex-Ersetzung deutet $1 sonst als Gruppenverweis - der Wert kaeme verstuemmelt an.
        assertEquals("Preis: 5\$1", einsetzen("Preis: {betrag}", mapOf("betrag" to "5\$1")))
    }
}
