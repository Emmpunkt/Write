package de.emmpunkt.write.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Der komplette Satz-Ablauf, geprueft ohne Maschine.
 *
 * Genau dafuer bekommt [Serienlauf] das Plotten hereingereicht: Hier steckt eine Attrappe
 * darin, die mitzaehlt und auf Wunsch scheitert. An der Maschine kostete jeder dieser Faelle
 * ein Blatt Papier.
 */
class SerienlaufTest {

    /** Merkt sich, welche Bogen geplottet wurden, und scheitert bei den genannten. */
    private class PlotAttrappe(private val scheitertBei: Set<Int> = emptySet()) {
        val geplottet = mutableListOf<Int>()

        @Suppress("UNUSED_PARAMETER")
        fun plotte(index: Int, text: String): Result<Unit> {
            return if (index in scheitertBei) {
                Result.failure(IllegalStateException("Verbindung weg"))
            } else {
                geplottet += index
                Result.success(Unit)
            }
        }
    }

    private val dreiBogen = listOf("Anna", "Bernd", "Clara")

    @Test
    fun `ein Satz laeuft Bogen fuer Bogen durch`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) })

        assertEquals(SerienZustand.Bereit(0, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.WartetAufBlatt(1, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.WartetAufBlatt(2, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.Fertig(geplottet = 3, uebersprungen = 0), lauf.zustand.value)
        assertEquals(listOf(0, 1, 2), attrappe.geplottet)
    }

    @Test
    fun `nach einem Fehlschlag bleibt der Zaehler stehen`() = runTest {
        // Der entscheidende Unterschied: "nochmal" plottet DENSELBEN Bogen. Rueckte der
        // Zaehler weiter, bekaeme ein Gast keine Karte.
        val attrappe = PlotAttrappe(scheitertBei = setOf(1))
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) })

        lauf.naechsterBogen()
        lauf.naechsterBogen()

        val zustand = lauf.zustand.value
        assertIs<SerienZustand.Fehlgeschlagen>(zustand)
        assertEquals(1, zustand.index)
        assertTrue(zustand.meldung.contains("Verbindung"), "Die Ursache fehlt: ${zustand.meldung}")
    }

    @Test
    fun `ein fehlgeschlagener Bogen laesst sich wiederholen`() = runTest {
        var scheitern = true
        val geplottet = mutableListOf<Int>()
        val lauf = Serienlauf(dreiBogen, { index, _ ->
            if (index == 1 && scheitern) {
                scheitern = false
                Result.failure(IllegalStateException("Blatt verrutscht"))
            } else {
                geplottet += index
                Result.success(Unit)
            }
        })

        lauf.naechsterBogen() // Bogen 0
        lauf.naechsterBogen() // Bogen 1 scheitert
        lauf.naechsterBogen() // Bogen 1 nochmal, klappt

        assertEquals(listOf(0, 1), geplottet)
        assertEquals(SerienZustand.WartetAufBlatt(2, 3), lauf.zustand.value)
    }

    @Test
    fun `ein Bogen laesst sich ueberspringen`() = runTest {
        val attrappe = PlotAttrappe(scheitertBei = setOf(1))
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) })

        lauf.naechsterBogen()
        lauf.naechsterBogen()
        lauf.ueberspringen()
        lauf.naechsterBogen()

        assertEquals(listOf(0, 2), attrappe.geplottet)
        assertEquals(SerienZustand.Fertig(geplottet = 2, uebersprungen = 1), lauf.zustand.value)
    }

    @Test
    fun `ueberspringen geht auch ohne Fehlschlag`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) })

        lauf.ueberspringen()
        lauf.naechsterBogen()

        assertEquals(listOf(1), attrappe.geplottet)
    }

    @Test
    fun `nach dem Abbruch passiert nichts mehr`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) })

        lauf.naechsterBogen()
        lauf.abbrechen()
        lauf.naechsterBogen()
        lauf.ueberspringen()

        assertEquals(SerienZustand.Abgebrochen, lauf.zustand.value)
        assertEquals(listOf(0), attrappe.geplottet, "Nach dem Abbruch wurde weitergeplottet")
    }

    @Test
    fun `ein abgebrochener Satz laesst sich spaeter fortsetzen`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, { i, t -> attrappe.plotte(i, t) }, startAb = 2)

        assertEquals(SerienZustand.Bereit(2, 3), lauf.zustand.value)
        lauf.naechsterBogen()

        assertEquals(listOf(2), attrappe.geplottet)
        assertEquals(SerienZustand.Fertig(geplottet = 1, uebersprungen = 0), lauf.zustand.value)
    }

    @Test
    fun `waehrend des Plottens meldet der Zustand welcher Bogen laeuft`() = runTest {
        // Der Zustand waehrend des Laufs ist von aussen nicht zu erwischen - wenn
        // naechsterBogen() zurueckkehrt, ist er schon wieder weg. Deshalb fragt ihn die
        // Plot-Funktion selbst ab. Die Zuweisung nach der Erzeugung ist noetig, weil die
        // Funktion das Objekt braucht, das gerade erst entsteht.
        var lauf: Serienlauf? = null
        var gesehen: SerienZustand? = null
        lauf = Serienlauf(dreiBogen, { _, _ ->
            gesehen = lauf?.zustand?.value
            Result.success(Unit)
        })

        lauf.naechsterBogen()

        assertEquals(SerienZustand.Laeuft(0, 3), gesehen)
    }

    @Test
    fun `ein leerer Satz ist sofort fertig`() = runTest {
        val lauf = Serienlauf(emptyList(), { _, _ -> Result.success(Unit) })

        assertEquals(SerienZustand.Fertig(geplottet = 0, uebersprungen = 0), lauf.zustand.value)
    }
}
