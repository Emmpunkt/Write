package de.emmpunkt.write.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wo ein Satz gerade steht. */
sealed interface SerienZustand {
    /** Noch nichts geplottet; als Naechstes kommt Bogen [naechster] (0-basiert). */
    data class Bereit(val naechster: Int, val gesamt: Int) : SerienZustand

    data class Laeuft(val index: Int, val gesamt: Int) : SerienZustand

    /** [fertig] = wie viele Bogen erledigt sind, geplottet oder uebersprungen. */
    data class WartetAufBlatt(val fertig: Int, val gesamt: Int) : SerienZustand

    data class Fehlgeschlagen(val index: Int, val meldung: String) : SerienZustand

    data class Fertig(val geplottet: Int, val uebersprungen: Int) : SerienZustand

    data object Abgebrochen : SerienZustand
}

/**
 * Steuert einen Satz gleichartiger Bogen.
 *
 * Kennt weder Telnet noch SD-Karte: Das Plotten kommt als [plotteBogen] herein. Dadurch ist
 * der ganze Ablauf - Fehlschlag, Wiederholung, Ueberspringen, Abbruch, Wiederaufnahme - ohne
 * Maschine pruefbar. An der Maschine kostete jeder dieser Faelle ein Blatt Papier.
 *
 * @param plotteBogen Erfolg heisst: der Auftrag lief bis `SendProgress.Completed` durch.
 * @param startAb fuer die Wiederaufnahme eines abgebrochenen Satzes.
 */
class Serienlauf(
    private val bogen: List<String>,
    private val plotteBogen: suspend (index: Int, text: String) -> Result<Unit>,
    startAb: Int = 0,
) {
    private var naechster = startAb.coerceIn(0, bogen.size)
    private var geplottet = 0
    private var uebersprungen = 0
    private var abgebrochen = false

    private val _zustand = MutableStateFlow<SerienZustand>(
        if (naechster >= bogen.size) {
            SerienZustand.Fertig(geplottet = 0, uebersprungen = 0)
        } else {
            SerienZustand.Bereit(naechster, bogen.size)
        },
    )
    val zustand: StateFlow<SerienZustand> = _zustand.asStateFlow()

    /** Plottet den naechsten Bogen und haelt danach an. */
    suspend fun naechsterBogen() {
        if (abgebrochen || naechster >= bogen.size) return

        val index = naechster
        _zustand.value = SerienZustand.Laeuft(index, bogen.size)
        val ergebnis = plotteBogen(index, bogen[index])

        // Waehrend des Plottens abgebrochen: der Abbruch hat das letzte Wort.
        if (abgebrochen) return

        ergebnis.fold(
            onSuccess = {
                geplottet++
                weiterruecken()
            },
            onFailure = { e ->
                // Der Zaehler bleibt stehen - "nochmal" plottet denselben Bogen.
                _zustand.value = SerienZustand.Fehlgeschlagen(
                    index = index,
                    meldung = e.message ?: e::class.simpleName ?: "Unbekannter Fehler",
                )
            },
        )
    }

    /** Ueberspringt den aktuellen Bogen - nach einem Fehlschlag oder auf Wunsch. */
    fun ueberspringen() {
        if (abgebrochen || naechster >= bogen.size) return
        uebersprungen++
        weiterruecken()
    }

    fun abbrechen() {
        abgebrochen = true
        _zustand.value = SerienZustand.Abgebrochen
    }

    private fun weiterruecken() {
        naechster++
        _zustand.value = if (naechster >= bogen.size) {
            SerienZustand.Fertig(geplottet, uebersprungen)
        } else {
            SerienZustand.WartetAufBlatt(fertig = naechster, gesamt = bogen.size)
        }
    }
}
