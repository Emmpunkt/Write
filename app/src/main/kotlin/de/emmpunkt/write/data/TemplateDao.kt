package de.emmpunkt.write.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Vorlagentabelle.
 *
 * Wie bei den Notizen eine eigene Schnittstelle und nicht das Room-DAO direkt: so laesst sich
 * der Rest der App gegen eine Liste im Speicher pruefen, ohne Emulator.
 */
interface TemplateDao {
    fun alle(): Flow<List<TemplateEntity>>
    suspend fun laden(id: Long): TemplateEntity?

    /** Legt an (id = 0) oder ersetzt. Liefert die Kennung. */
    suspend fun speichern(vorlage: TemplateEntity): Long
    suspend fun loeschen(id: Long)
    suspend fun zuletztBearbeitete(): TemplateEntity?
}

/** Die von Room erzeugte Fassung. */
@Dao
interface RoomTemplateDao : TemplateDao {

    @Query("SELECT * FROM templates ORDER BY updatedAt DESC")
    override fun alle(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    override suspend fun laden(id: Long): TemplateEntity?

    @Upsert
    override suspend fun speichern(vorlage: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    override suspend fun loeschen(id: Long)

    @Query("SELECT * FROM templates ORDER BY updatedAt DESC LIMIT 1")
    override suspend fun zuletztBearbeitete(): TemplateEntity?
}
