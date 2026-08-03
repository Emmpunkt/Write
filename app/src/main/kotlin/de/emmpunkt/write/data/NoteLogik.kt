package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align

/** Was in der Liste steht, wenn eine Notiz noch keinen Text hat. */
const val OHNE_TITEL = "Ohne Titel"

/**
 * Der Titel einer Notiz: ihre erste nicht-leere Zeile.
 *
 * Bewusst abgeleitet und nicht gespeichert - ein eigenes Feld waere ein zweiter Ort fuer
 * dieselbe Information und muesste beim Tippen nachgefuehrt werden.
 */
fun titelVon(text: String, maxLaenge: Int = 40): String {
    val zeile = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: return OHNE_TITEL

    return if (zeile.length <= maxLaenge) zeile else zeile.take(maxLaenge) + "…"
}

/**
 * Die Ausrichtung als Enum.
 *
 * Steht in der Datenbank ein unbekannter Name - etwa weil der Enum spaeter umbenannt wurde -
 * gilt die Vorgabe. Ein Absturz beim Oeffnen einer alten Notiz waere die schlechtere Antwort.
 */
fun NoteEntity.alignEnum(): Align =
    runCatching { Align.valueOf(align) }.getOrElse { AppSettings().align }

/**
 * Legt das Schriftbild der Notiz ueber die Einstellungen.
 *
 * Alles andere - Blatt, Raender, Offset, Maschine - bleibt unangetastet. Das ist der
 * entscheidende Punkt: ein Notizwechsel aendert die Gestaltung, nicht die Einrichtung.
 */
fun AppSettings.mitNotiz(note: NoteEntity): AppSettings = copy(
    fontId = note.fontId,
    sizeMm = note.sizeMm,
    align = note.alignEnum(),
    lineSpacing = note.lineSpacing,
    letterSpacing = note.letterSpacing,
    wordSpacing = note.wordSpacing,
    slantDeg = note.slantDeg,
)

/** Der umgekehrte Weg: aus dem Arbeitszustand wird wieder eine Notiz zum Speichern. */
fun AppSettings.zuNotiz(id: Long, text: String, jetzt: Long) = NoteEntity(
    id = id,
    text = text,
    updatedAt = jetzt,
    fontId = fontId,
    sizeMm = sizeMm,
    align = align.name,
    lineSpacing = lineSpacing,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    slantDeg = slantDeg,
)

/**
 * Eine neue, leere Notiz.
 *
 * Sie erbt das Schriftbild der zuletzt geoeffneten: wer eine Einkaufsliste in 5 mm schreibt,
 * schreibt die naechste meist genauso. Ohne Vorlage gelten die Vorgabewerte.
 */
fun neueNotiz(vorlage: NoteEntity?, vorgabe: AppSettings, jetzt: Long): NoteEntity =
    vorlage?.copy(id = 0L, text = "", updatedAt = jetzt)
        ?: vorgabe.zuNotiz(id = 0L, text = "", jetzt = jetzt)
