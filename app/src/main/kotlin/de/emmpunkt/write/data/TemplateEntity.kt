package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Vorlage: Text mit Platzhaltern, Schriftbild, Textrahmen und die Werteliste.
 *
 * Anders als [NoteEntity] traegt sie den GANZEN RAHMEN mit - Groesse, Rand UND Position. Das
 * ist der Unterschied zwischen Notiz und Vorlage: Eine Notiz wird auf das Papier geschrieben,
 * das gerade auf dem Tisch liegt; eine Vorlage beschreibt, wo genau der Text stehen soll.
 *
 * Der Versatz war anfangs global, weil er scheinbar den Anschlag beschreibt. Das war ein
 * Trugschluss (2026-08-04): Der "Bogen" ist in Wahrheit eine Textbox auf dem Tisch. Wer auf
 * einer Grusskarte unten rechts schreiben will, braucht Groesse UND Position in derselben
 * Vorlage - sonst laesst sich die Position im Serienmodus gar nicht einstellen.
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

    // Textrahmen: Groesse, Rand und Position auf dem Tisch
    val paperWidthMm: Float,
    val paperHeightMm: Float,
    val marginMm: Float,
    val paperOffsetXMm: Float = 0f,
    val paperOffsetYMm: Float = 0f,
)
