package de.emmpunkt.write.data

import kotlinx.coroutines.flow.Flow

/**
 * Die einzige Stelle, ueber die der Rest der App an Notizen kommt.
 *
 * Alle Regeln sitzen hier und nicht in der Oberflaeche - insbesondere, dass immer mindestens
 * eine Notiz existiert.
 */
class NoteRepository(private val dao: NoteDao) {

    val notizen: Flow<List<NoteEntity>> = dao.alle()

    suspend fun laden(id: Long): NoteEntity? = dao.laden(id)

    suspend fun speichern(note: NoteEntity): Long = dao.speichern(note)

    /**
     * Sorgt dafuer, dass es beim Start eine Notiz gibt, und liefert die zuletzt bearbeitete.
     *
     * Eine einzige Regel, ohne Sonderfaelle: Ist die Tabelle leer, entsteht genau eine Notiz
     * aus [lastText] und den damaligen Stilwerten. War [lastText] leer, ist die Notiz eben
     * leer - das ist kein anderer Fall.
     *
     * [lastText] wird dabei nicht geloescht. Ginge bei der Umstellung etwas schief, waere der
     * Text sonst unwiederbringlich weg.
     *
     * [offeneId] ist die zuletzt geoeffnete Notiz. Sie wird gemerkt und nicht aus den
     * Zeitstempeln erschlossen: beim Wechseln wird die VERLASSENE Notiz gespeichert und traegt
     * danach die neuere Zeit - die App zeigte nach einem Neustart sonst die falsche.
     */
    suspend fun sicherstellenDassEineDaIst(
        lastText: String,
        vorgabe: AppSettings,
        jetzt: Long,
        offeneId: Long = 0L,
    ): NoteEntity {
        if (dao.anzahl() == 0) {
            val id = dao.speichern(vorgabe.zuNotiz(id = 0L, text = lastText, jetzt = jetzt))
            return checkNotNull(dao.laden(id)) { "Gerade angelegte Notiz nicht auffindbar" }
        }
        // Ist sie inzwischen geloescht, gilt wieder die zuletzt bearbeitete.
        dao.laden(offeneId)?.let { return it }
        return checkNotNull(dao.zuletztBearbeitete()) {
            "Tabelle ist nicht leer, liefert aber nichts"
        }
    }

    /** Die zuletzt bearbeitete Notiz, oder null bei leerer Tabelle. */
    suspend fun zuletztBearbeiteteOderNull(): NoteEntity? = dao.zuletztBearbeitete()

    /**
     * Loescht die Notiz - ausser es ist die letzte. Die wird stattdessen geleert.
     *
     * Liefert die geleerte Notiz, wenn das der Fall war, sonst `null`. Der Aufrufer weiss
     * damit, ob er auf etwas anderes umschalten muss oder ob dieselbe Notiz weiter offen
     * bleibt.
     */
    suspend fun loeschenOderLeeren(id: Long, jetzt: Long): NoteEntity? {
        if (dao.anzahl() <= 1) {
            val vorhanden = dao.laden(id) ?: return null
            val geleert = vorhanden.copy(text = "", updatedAt = jetzt)
            dao.speichern(geleert)
            return geleert
        }
        dao.loeschen(id)
        return null
    }
}
