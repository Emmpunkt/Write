package de.emmpunkt.write.data

import kotlinx.coroutines.flow.Flow

/**
 * Die einzige Stelle, ueber die der Rest der App an Vorlagen kommt.
 *
 * Bewusst OHNE die Regel "es gibt immer mindestens eine", die bei den Notizen gilt: Der Editor
 * braucht eine Notiz, um ueberhaupt etwas anzuzeigen. Ohne Vorlage ist der Serie-Reiter
 * dagegen schlicht leer, und das ist ein gueltiger Zustand.
 */
class TemplateRepository(private val dao: TemplateDao) {

    val vorlagen: Flow<List<TemplateEntity>> = dao.alle()

    suspend fun laden(id: Long): TemplateEntity? = dao.laden(id)

    suspend fun speichern(vorlage: TemplateEntity): Long = dao.speichern(vorlage)

    suspend fun loeschen(id: Long) = dao.loeschen(id)

    suspend fun zuletztBearbeiteteOderNull(): TemplateEntity? = dao.zuletztBearbeitete()
}
