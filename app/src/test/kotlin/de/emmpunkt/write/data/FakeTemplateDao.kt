package de.emmpunkt.write.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Die Vorlagentabelle als Liste im Speicher.
 *
 * Dasselbe Muster wie `FakeNoteDao` und `FakeFluidNc`: die Schnittstelle ist echt, nur was
 * dahinter liegt, ist ersetzt.
 */
class FakeTemplateDao : TemplateDao {

    private val inhalt = MutableStateFlow<List<TemplateEntity>>(emptyList())
    private var naechsteId = 1L

    override fun alle(): Flow<List<TemplateEntity>> =
        inhalt.map { liste -> liste.sortedByDescending { it.updatedAt } }

    override suspend fun laden(id: Long): TemplateEntity? = inhalt.value.firstOrNull { it.id == id }

    override suspend fun speichern(vorlage: TemplateEntity): Long {
        // Wie Room: id 0 heisst "neu anlegen", alles andere ersetzt den vorhandenen Satz.
        return if (vorlage.id == 0L) {
            val id = naechsteId++
            inhalt.value = inhalt.value + vorlage.copy(id = id)
            id
        } else {
            inhalt.value = inhalt.value.map { if (it.id == vorlage.id) vorlage else it }
            vorlage.id
        }
    }

    override suspend fun loeschen(id: Long) {
        inhalt.value = inhalt.value.filterNot { it.id == id }
    }

    override suspend fun zuletztBearbeitete(): TemplateEntity? =
        inhalt.value.maxByOrNull { it.updatedAt }
}
