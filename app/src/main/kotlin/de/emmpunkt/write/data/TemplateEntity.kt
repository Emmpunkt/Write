package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Vorlage: Text mit Platzhaltern, Schriftbild, Blattformat und die Werteliste.
 *
 * Anders als [NoteEntity] traegt sie das BLATTFORMAT mit. Das ist der Unterschied zwischen
 * Notiz und Vorlage: Eine Grusskarte bringt ihr Format mit, eine Notiz wird auf das Papier
 * geschrieben, das gerade auf dem Tisch liegt.
 *
 * Der Papier-Offset gehoert weiterhin NICHT dazu - er beschreibt, wo die Blattecke am Anschlag
 * liegt, und das aendert sich nicht dadurch, dass ein kleineres Blatt eingelegt wird.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Name der Vorlage, z. B. "Platzkarten Hochzeit". */
    val name: String,
    /** Text mit Platzhaltern in geschweiften Klammern: "{anrede} {name}," */
    val text: String,
    /**
     * Werteliste: eine Zeile je Bogen, Felder durch Semikolon getrennt.
     *
     * Mitgespeichert, damit ein Satz wiederholbar ist - und damit ein abgebrochener Satz
     * spaeter fortgesetzt werden kann, ohne die Namen neu zu tippen.
     */
    val werte: String,
    val updatedAt: Long,

    // Schriftbild
    val fontId: String,
    val sizeMm: Float,
    val align: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,

    // Blattformat
    val paperWidthMm: Float,
    val paperHeightMm: Float,
    val marginMm: Float,
)
