package de.emmpunkt.write.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Die Notiztabelle als Liste im Speicher.
 *
 * Room selbst braeuchte einen Emulator; dieses Fake nicht. Dasselbe Muster wie `FakeFluidNc`
 * im machine-Modul: die Schnittstelle ist echt, nur was dahinter liegt, ist ersetzt.
 */
class FakeNoteDao : NoteDao {

    private val inhalt = MutableStateFlow<List<NoteEntity>>(emptyList())
    private var naechsteId = 1L

    override fun alle(): Flow<List<NoteEntity>> =
        inhalt.map { liste -> liste.sortedByDescending { it.updatedAt } }

    override suspend fun laden(id: Long): NoteEntity? = inhalt.value.firstOrNull { it.id == id }

    override suspend fun speichern(note: NoteEntity): Long {
        // Wie Room: id 0 heisst "neu anlegen", alles andere ersetzt den vorhandenen Satz.
        return if (note.id == 0L) {
            val id = naechsteId++
            inhalt.value = inhalt.value + note.copy(id = id)
            id
        } else {
            inhalt.value = inhalt.value.map { if (it.id == note.id) note else it }
            note.id
        }
    }

    override suspend fun loeschen(id: Long) {
        inhalt.value = inhalt.value.filterNot { it.id == id }
    }

    override suspend fun anzahl(): Int = inhalt.value.size

    override suspend fun zuletztBearbeitete(): NoteEntity? =
        inhalt.value.maxByOrNull { it.updatedAt }
}
