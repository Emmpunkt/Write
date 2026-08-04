package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine gespeicherte Notiz: Text und Schriftbild.
 *
 * Blattformat, Raender und Papier-Offset gehoeren bewusst NICHT hierher. Sie beschreiben, was
 * auf dem Tisch liegt, nicht wie die Notiz aussieht - beim Umschalten auf eine andere Notiz
 * soll nicht ploetzlich ein anderes Format eingestellt sein als das eingelegte Papier.
 *
 * Schriftart, Groesse und Ausrichtung stehen nicht mehr einzeln hier, sondern in [stile] -
 * eine Notiz kann seit Etappe 3 Teil 4 mehrere benannte Stile tragen und jedem Absatz einen
 * davon zuweisen.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    /** Zeitpunkt der letzten Aenderung; die Liste sortiert danach. */
    val updatedAt: Long,
    /**
     * Die benannten Absatzstile, eine Zeile je Stil - siehe `Stilformat.kt`.
     *
     * Als Text und nicht als eigene Tabelle: Die Stile gehoeren untrennbar zu dieser Notiz,
     * werden nie einzeln abgefragt und nie von anderswo referenziert. Eine zweite Tabelle
     * braeuchte Fremdschluessel und Aufraeumen fuer nichts.
     */
    val stile: String,
    /** Stilindex je Absatz, durch Komma getrennt. */
    val absatzZuordnung: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,
)
