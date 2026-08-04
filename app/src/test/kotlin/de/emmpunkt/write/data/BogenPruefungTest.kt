package de.emmpunkt.write.data

import de.emmpunkt.write.core.font.Fonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BogenPruefungTest {

    private val font = Fonts.load(Fonts.defaultId)

    /** Kleine Karte mit passendem Textrahmen, damit ein langer Name zuverlaessig ueberlaeuft. */
    private val klein = AppSettings(
        paperWidthMm = 60f, paperHeightMm = 30f, marginMm = 4f, sizeMm = 6f,
    ).blattFuellen()

    private fun zeilen(vararg werte: String) =
        werteZeilen(werte.joinToString("\n"), listOf("name"))

    @Test
    fun `ein passender Bogen wird nicht bemaengelt`() {
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(1, befunde.size)
        assertTrue(befunde[0].inOrdnung, "Anna passt auf die Karte, wurde aber bemaengelt")
    }

    @Test
    fun `ein zu langer Wert wird gemeldet`() {
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna", "Christiane Schmidt-Wagner von Hohenlohe zu Langenburg"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertTrue(befunde[0].inOrdnung)
        assertFalse(befunde[1].inOrdnung, "Der lange Name passt nicht, wurde aber durchgewinkt")
    }

    @Test
    fun `der Befund nennt Bogennummer und Bezeichnung`() {
        // Ohne beides muesste der Nutzer raten, welche Karte gemeint ist.
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna", "Bernd"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(listOf(0, 1), befunde.map { it.index })
        assertEquals(listOf("Anna", "Bernd"), befunde.map { it.bezeichnung })
    }

    @Test
    fun `ein hart getrenntes Wort gilt als nicht in Ordnung`() {
        // Bei einer Platzkarte ist ein mitten durchgeschnittener Nachname genauso unbrauchbar
        // wie ein Ueberlauf - anders als im Editor, wo es nur eine Warnung ist.
        val befunde = pruefeBogen(
            zeilen = zeilen("Donaudampfschifffahrtsgesellschaftskapitaen"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertFalse(befunde[0].inOrdnung)
        assertTrue(befunde[0].hartGetrennt.isNotEmpty(), "Die harte Trennung wurde nicht erkannt")
    }

    @Test
    fun `kaputte Zeilen werden nicht geprueft`() {
        // werteZeilen hat sie schon gemeldet; hier wuerden sie nur ein zweites Mal auftauchen.
        val gemischt = werteZeilen("Liebe;Anna\nBernd", listOf("anrede", "name"))
        val befunde = pruefeBogen(
            zeilen = gemischt.filter { it.fehler == null },
            vorlage = "{anrede} {name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(1, befunde.size)
    }

    // ---- Rahmenfehler ----

    @Test
    fun `ein Rahmen ohne Flaeche wird als Meldung geliefert statt zu werfen`() {
        // Frame wirft im Konstruktor. Ohne diese Abfangung stuerzte die App ab, sobald beim
        // Tippen kurzzeitig eine 0 im Feld steht.
        val unmoeglich = klein.copy(rahmenBreiteMm = 0f)

        val fehler = rahmenFehler(unmoeglich)

        assertNotNull(fehler, "Der unmoegliche Rahmen wurde nicht bemaengelt")
    }

    @Test
    fun `ein Rahmen ueber der Blattkante sperrt den Satz`() {
        // Sonst schriebe der Stift bei einem Satz Platzkarten neben die Karte auf den Tisch,
        // und vor dem Plotten faellt es niemandem auf.
        val hinaus = klein.copy(rahmenXMm = 30f, rahmenBreiteMm = 52f)

        assertNotNull(rahmenFehler(hinaus), "Der Ueberstand wurde nicht bemaengelt")
    }

    @Test
    fun `ein brauchbarer Rahmen liefert keine Meldung`() {
        assertNull(rahmenFehler(klein))
    }
}
