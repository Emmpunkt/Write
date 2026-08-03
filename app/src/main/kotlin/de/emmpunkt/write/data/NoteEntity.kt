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
 * [align] steht als Name des Enums und nicht als Zahl in der Datenbank: so bleibt sie von
 * Hand lesbar, und ein Umsortieren der Enum-Werte verschiebt nicht stillschweigend die
 * Ausrichtung aller gespeicherten Notizen.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    /** Zeitpunkt der letzten Aenderung; die Liste sortiert danach. */
    val updatedAt: Long,
    val fontId: String,
    val sizeMm: Float,
    val align: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,
)
