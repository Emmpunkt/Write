package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Das Speicherformat der Stile.
 *
 * Der Text kommt aus der Datenbank und kann alles enthalten - eine halb geschriebene Zeile aus
 * einem abgebrochenen Schreibvorgang ebenso wie ein Rest aus einer aelteren Fassung. Er darf
 * die App nie zum Absturz bringen.
 */
class StilformatTest {

    private val stile = listOf(
        Absatzstil("Überschrift", "allure", 12f, Align.CENTER),
        Absatzstil("Fließtext", "sans", 6f, Align.LEFT),
    )

    @Test
    fun `geschrieben und wieder gelesen kommt dasselbe heraus`() {
        assertEquals(stile, stileAusText(stileAlsText(stile)))
    }

    @Test
    fun `leerer Text ergibt keine Stile - die Vorgabe setzt der Aufrufer`() {
        assertEquals(emptyList(), stileAusText(""))
        assertEquals(emptyList(), stileAusText("   \n  "))
    }

    @Test
    fun `kaputte Zeilen werden uebergangen, brauchbare bleiben`() {
        val text = "Titel|allure|12.0|CENTER\nnur Müll ohne Trenner\n|||\nText|sans|6.0|LEFT"
        val gelesen = stileAusText(text)

        assertEquals(listOf("Titel", "Text"), gelesen.map { it.name })
    }

    @Test
    fun `eine unbekannte Ausrichtung faellt auf linksbuendig zurueck`() {
        val gelesen = stileAusText("Titel|allure|12.0|DIAGONAL")
        assertEquals(Align.LEFT, gelesen.single().align)
    }

    @Test
    fun `eine unlesbare Groesse macht die Zeile ungueltig`() {
        // Hier zu raten waere schlechter als die Zeile fallen zu lassen: eine falsche Groesse
        // faellt erst auf dem Papier auf.
        assertEquals(emptyList(), stileAusText("Titel|allure|riesig|LEFT"))
    }

    @Test
    fun `der Trenner wird aus dem Namen entfernt`() {
        val text = stileAlsText(listOf(Absatzstil("A|B", "sans", 6f, Align.LEFT)))
        val gelesen = stileAusText(text)

        assertEquals(1, gelesen.size, "Der Trenner im Namen darf keine zweite Zeile erzeugen")
        assertEquals("AB", gelesen.single().name)
    }

    @Test
    fun `ein Zeilenumbruch im Namen wird entfernt`() {
        val gelesen = stileAusText(stileAlsText(listOf(Absatzstil("A\nB", "sans", 6f, Align.LEFT))))
        assertEquals(1, gelesen.size)
        assertTrue(!gelesen.single().name.contains('\n'))
    }

    @Test
    fun `ein leerer Name bekommt eine Ersatzbezeichnung`() {
        val gelesen = stileAusText(stileAlsText(listOf(Absatzstil("   ", "sans", 6f, Align.LEFT))))
        assertTrue(gelesen.single().name.isNotBlank(), "Ein namenloser Chip ist nicht bedienbar")
    }

    @Test
    fun `die Zuordnung geht durch Schreiben und Lesen unveraendert hindurch`() {
        val zuordnung = listOf(0, 1, 1, 2)
        assertEquals(zuordnung, zuordnungAusText(zuordnungAlsText(zuordnung)))
    }

    @Test
    fun `ein kaputter Eintrag faellt auf den ersten Stil zurueck und behaelt seinen Platz`() {
        // Wegzulassen waere schlimmer als ein falscher Stil: dann ruecken alle folgenden
        // Absaetze eine Stelle vor und bekommen samt und sonders das Schriftbild ihres
        // Nachbarn.
        assertEquals(listOf(0, 0, 2), zuordnungAusText("0,x,2"))
        assertEquals(listOf(0, 0, 0), zuordnungAusText("a,b,c"))
    }

    @Test
    fun `ohne Text gibt es keine Zuordnung`() {
        assertEquals(emptyList(), zuordnungAusText(""))
    }

    @Test
    fun `negative Indizes werden zu null`() {
        assertEquals(listOf(0, 1), zuordnungAusText("-3,1"))
    }
}
