package de.emmpunkt.write.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateRepositoryTest {

    private val vorgabe = AppSettings()

    private fun repo() = TemplateRepository(FakeTemplateDao())

    private fun vorlage(name: String, jetzt: Long) =
        vorgabe.zuVorlage(id = 0L, name = name, text = "{name}", werte = "Anna", jetzt = jetzt)

    @Test
    fun `speichern vergibt eine Kennung und laden liefert dieselbe Vorlage`() = runTest {
        val r = repo()
        val id = r.speichern(vorlage("Platzkarten", 100L))

        assertTrue(id > 0L, "Keine Kennung vergeben")
        assertEquals("Platzkarten", r.laden(id)?.name)
    }

    @Test
    fun `die Liste ist nach letzter Aenderung sortiert`() = runTest {
        val r = repo()
        r.speichern(vorlage("alt", 100L))
        r.speichern(vorlage("neu", 300L))
        r.speichern(vorlage("mittel", 200L))

        assertEquals(listOf("neu", "mittel", "alt"), r.vorlagen.first().map { it.name })
    }

    @Test
    fun `die Werteliste ueberlebt das Speichern`() = runTest {
        // Sie ist der Grund, warum ein abgebrochener Satz spaeter fortsetzbar ist.
        val r = repo()
        val id = r.speichern(
            vorgabe.zuVorlage(0L, "Karten", "{anrede} {name}", "Liebe;Anna\nLieber;Bernd", 100L),
        )

        assertEquals("Liebe;Anna\nLieber;Bernd", r.laden(id)?.werte)
    }

    @Test
    fun `loeschen entfernt die Vorlage`() = runTest {
        val r = repo()
        val behalten = r.speichern(vorlage("behalten", 100L))
        val weg = r.speichern(vorlage("weg", 200L))

        r.loeschen(weg)

        assertEquals(listOf(behalten), r.vorlagen.first().map { it.id })
    }

    @Test
    fun `ohne Vorlagen liefert die zuletzt bearbeitete nichts`() = runTest {
        // Anders als bei den Notizen ist "keine Vorlage" ein gueltiger Zustand - der Nutzer
        // muss nicht erst eine anlegen, bevor er die App oeffnen darf.
        assertNull(repo().zuletztBearbeiteteOderNull())
    }

    @Test
    fun `die zuletzt bearbeitete Vorlage wird geliefert`() = runTest {
        val r = repo()
        r.speichern(vorlage("alt", 100L))
        r.speichern(vorlage("neu", 300L))

        assertEquals("neu", r.zuletztBearbeiteteOderNull()?.name)
    }
}
