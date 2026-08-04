package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Vorlage: Text mit Platzhaltern, Schriftbild, Textrahmen und die Werteliste.
 *
 * Anders als [NoteEntity] traegt sie den GANZEN RAHMEN mit - Groesse UND Position. Das ist der
 * Unterschied zwischen Notiz und Vorlage: Eine Notiz wird auf das Papier geschrieben, das
 * gerade auf dem Tisch liegt; eine Vorlage beschreibt, wo genau auf dem Blatt der Text steht.
 *
 * Das BLATT gehoert bewusst NICHT hierher. Es beschreibt, was auf dem Tisch liegt, und steht
 * global unter Optionen. Anfangs war beides dasselbe Feld - dadurch war das Blatt in der
 * Vorschau nicht die Karte, sondern der Textkasten, und ein grosses Blatt mit kleinem Text
 * liess sich ueberhaupt nicht abbilden.
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
    /** Die benannten Absatzstile, eine Zeile je Stil - siehe `Stilformat.kt`. */
    val stile: String,
    /** Stilindex je Absatz des Vorlagentextes, durch Komma getrennt. */
    val absatzZuordnung: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,

    // Textrahmen: Groesse und Lage AUF DEM BLATT, ab dessen linker unterer Ecke.
    val rahmenXMm: Float,
    val rahmenYMm: Float,
    val rahmenBreiteMm: Float,
    val rahmenHoeheMm: Float,
    /** Wie der Text im Rahmen steht - Name des Enums, wie bei der Ausrichtung. */
    val drehung: String,
    /** Form des gezeichneten Rahmens, Abstand nach aussen und Zipfelseite der Sprechblase. */
    val rahmenForm: String,
    val rahmenAbstandMm: Float,
    val zipfel: String,
)
