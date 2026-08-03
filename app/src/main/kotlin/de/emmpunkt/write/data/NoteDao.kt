package de.emmpunkt.write.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Notiztabelle.
 *
 * BEWUSST eine eigene Schnittstelle und nicht das Room-DAO direkt: so laesst sich der ganze
 * Rest der App gegen eine Liste im Speicher pruefen, ohne Emulator.
 */
interface NoteDao {
    fun alle(): Flow<List<NoteEntity>>
    suspend fun laden(id: Long): NoteEntity?

    /** Legt an (id = 0) oder ersetzt. Liefert die Kennung. */
    suspend fun speichern(note: NoteEntity): Long
    suspend fun loeschen(id: Long)
    suspend fun anzahl(): Int

    /**
     * Die zuletzt bearbeitete Notiz, oder null bei leerer Tabelle.
     *
     * Eigene Abfrage statt `alle().first()`: ein Flow laesst sich nicht ohne Blockieren
     * synchron lesen, und ein `runBlocking` im Repository waere genau die Art stiller
     * Fallstrick, die spaeter niemand mehr findet.
     */
    suspend fun zuletztBearbeitete(): NoteEntity?
}

/** Die von Room erzeugte Fassung. */
@Dao
interface RoomNoteDao : NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    override fun alle(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    override suspend fun laden(id: Long): NoteEntity?

    @Upsert
    override suspend fun speichern(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    override suspend fun loeschen(id: Long)

    @Query("SELECT COUNT(*) FROM notes")
    override suspend fun anzahl(): Int

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT 1")
    override suspend fun zuletztBearbeitete(): NoteEntity?
}
