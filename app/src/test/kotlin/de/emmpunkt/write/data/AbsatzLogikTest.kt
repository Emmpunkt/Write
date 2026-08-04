package de.emmpunkt.write.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Zuordnung Absatz -> Stil beim Tippen nachfuehren.
 *
 * Das ist die Stelle, an der eine Zuordnung ueber Indizes sonst zerbricht: Wer einen Absatz in
 * der Mitte einfuegt, verschiebt alles dahinter. Ohne Nachfuehrung wechselt in dem Moment das
 * Schriftbild jedes folgenden Absatzes.
 */
class AbsatzLogikTest {

    @Test
    fun `unveraenderter Text laesst die Zuordnung in Ruhe`() {
        val absaetze = listOf("Titel", "Text", "Gruss")
        assertEquals(
            listOf(0, 1, 2),
            zuordnungNachTextaenderung(absaetze, absaetze, listOf(0, 1, 2)),
        )
    }

    @Test
    fun `ein geaenderter Absatz behaelt seinen Stil`() {
        assertEquals(
            listOf(0, 1, 2),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text", "Gruss"),
                listOf("Titel", "Text mehr", "Gruss"),
                listOf(0, 1, 2),
            ),
        )
    }

    @Test
    fun `ein eingefuegter Absatz erbt den Stil des Absatzes davor`() {
        assertEquals(
            listOf(0, 1, 1, 2),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text", "Gruss"),
                listOf("Titel", "Text", "Noch Text", "Gruss"),
                listOf(0, 1, 2),
            ),
        )
    }

    @Test
    fun `ein geloeschter Absatz nimmt seinen Eintrag mit`() {
        assertEquals(
            listOf(0, 2),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text", "Gruss"),
                listOf("Titel", "Gruss"),
                listOf(0, 1, 2),
            ),
        )
    }

    @Test
    fun `ein in der Mitte geteilter Absatz vererbt seinen Stil an beide Haelften`() {
        // Der haeufigste Fall ueberhaupt: Eingabetaste mitten im Text.
        assertEquals(
            listOf(0, 1, 1, 2),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text und mehr", "Gruss"),
                listOf("Titel", "Text", "und mehr", "Gruss"),
                listOf(0, 1, 2),
            ),
        )
    }

    @Test
    fun `eine neue Leerzeile hinter einem Absatz erbt dessen Stil`() {
        assertEquals(
            listOf(0, 0, 1),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text"),
                listOf("Titel", "", "Text"),
                listOf(0, 1),
            ),
        )
    }

    @Test
    fun `am Anfang eingefuegte Absaetze erben vom ersten Stil`() {
        assertEquals(
            listOf(0, 0, 1),
            zuordnungNachTextaenderung(
                listOf("Titel", "Text"),
                listOf("Neu", "Titel", "Text"),
                listOf(0, 1),
            ),
        )
    }

    @Test
    fun `das Ergebnis hat immer so viele Eintraege wie es Absaetze gibt`() {
        val faelle = listOf(
            listOf("A") to listOf("A", "B", "C", "D"),
            listOf("A", "B", "C") to listOf("B"),
            listOf("A", "B") to emptyList(),
            emptyList<String>() to listOf("A", "B"),
        )
        faelle.forEach { (alt, neu) ->
            val ergebnis = zuordnungNachTextaenderung(alt, neu, List(alt.size) { it })
            assertEquals(neu.size, ergebnis.size, "Bei $alt -> $neu")
        }
    }

    @Test
    fun `eine zu kurze Zuordnung wird stillschweigend aufgefuellt`() {
        // Kommt vor, wenn eine Notiz aus der Zeit vor den Stilen geladen wird.
        assertEquals(
            listOf(0, 0, 0),
            zuordnungNachTextaenderung(
                listOf("A", "B", "C"),
                listOf("A", "B", "C"),
                emptyList(),
            ),
        )
    }
}
