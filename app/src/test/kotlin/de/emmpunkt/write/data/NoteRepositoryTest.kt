package de.emmpunkt.write.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteRepositoryTest {

    private val vorgabe = AppSettings()

    private fun repo(dao: FakeNoteDao = FakeNoteDao()) = NoteRepository(dao) to dao

    @Test
    fun `speichern vergibt eine Kennung und laden liefert dieselbe Notiz`() = runTest {
        val (r, _) = repo()
        val id = r.speichern(vorgabe.zuNotiz(id = 0L, text = "Milch", jetzt = 100L))

        assertTrue(id > 0L, "Keine Kennung vergeben")
        assertEquals("Milch", r.laden(id)?.text)
    }

    @Test
    fun `die Liste ist nach letzter Aenderung sortiert`() = runTest {
        val (r, _) = repo()
        r.speichern(vorgabe.zuNotiz(0L, "alt", jetzt = 100L))
        r.speichern(vorgabe.zuNotiz(0L, "neu", jetzt = 300L))
        r.speichern(vorgabe.zuNotiz(0L, "mittel", jetzt = 200L))

        val titel = r.notizen.first().map { it.text }
        assertEquals(listOf("neu", "mittel", "alt"), titel)
    }

    // ---- Migration ----

    @Test
    fun `beim ersten Start wird aus lastText die erste Notiz`() = runTest {
        val (r, _) = repo()
        val gespeicherteStilwerte = vorgabe.copy(fontId = "serif", sizeMm = 12f)

        val notiz = r.sicherstellenDassEineDaIst(
            lastText = "Alte Notiz",
            vorgabe = gespeicherteStilwerte,
            jetzt = 500L,
        )

        assertEquals("Alte Notiz", notiz.text)
        assertEquals("serif", notiz.fontId, "Die damaligen Stilwerte sind verloren")
        assertEquals(12f, notiz.sizeMm)
        assertEquals(1, r.notizen.first().size)
    }

    @Test
    fun `ohne lastText entsteht eine leere Notiz`() = runTest {
        // Kein Sonderfall, sondern derselbe Fall mit leerem Text - der Editor hat immer eine.
        val (r, _) = repo()
        val notiz = r.sicherstellenDassEineDaIst("", vorgabe, jetzt = 500L)

        assertEquals("", notiz.text)
        assertEquals(1, r.notizen.first().size)
    }

    @Test
    fun `ein zweiter Start legt nichts zusaetzliches an`() = runTest {
        val (r, _) = repo()
        r.sicherstellenDassEineDaIst("Alte Notiz", vorgabe, jetzt = 500L)
        val zweite = r.sicherstellenDassEineDaIst("Alte Notiz", vorgabe, jetzt = 900L)

        assertEquals(1, r.notizen.first().size, "Die Migration lief ein zweites Mal")
        assertEquals("Alte Notiz", zweite.text, "Nicht die vorhandene Notiz geliefert")
    }

    @Test
    fun `beim Start wird die zuletzt geoeffnete Notiz geliefert`() = runTest {
        // Nicht die zuletzt GESCHRIEBENE: beim Wechseln wird die verlassene Notiz gespeichert
        // und bekaeme damit den neueren Zeitstempel. Am Geraet fuehrte genau das dazu, dass
        // die App nach dem Neustart eine andere Notiz zeigte als die zuletzt offene.
        val (r, _) = repo()
        val zuerst = r.speichern(vorgabe.zuNotiz(0L, "die offene", jetzt = 100L))
        r.speichern(vorgabe.zuNotiz(0L, "spaeter geschrieben", jetzt = 300L))

        val notiz = r.sicherstellenDassEineDaIst("", vorgabe, jetzt = 500L, offeneId = zuerst)

        assertEquals("die offene", notiz.text)
    }

    @Test
    fun `eine verschwundene offene Notiz faellt auf die zuletzt bearbeitete zurueck`() = runTest {
        val (r, _) = repo()
        r.speichern(vorgabe.zuNotiz(0L, "noch da", jetzt = 100L))

        val notiz = r.sicherstellenDassEineDaIst("", vorgabe, jetzt = 500L, offeneId = 999L)

        assertEquals("noch da", notiz.text)
    }

    // ---- Loeschen ----

    @Test
    fun `loeschen entfernt die Notiz`() = runTest {
        val (r, _) = repo()
        val behalten = r.speichern(vorgabe.zuNotiz(0L, "behalten", 100L))
        val weg = r.speichern(vorgabe.zuNotiz(0L, "weg", 200L))

        r.loeschenOderLeeren(weg, jetzt = 300L)

        val uebrig = r.notizen.first()
        assertEquals(1, uebrig.size)
        assertEquals(behalten, uebrig.single().id)
    }

    @Test
    fun `die letzte Notiz wird geleert statt geloescht`() = runTest {
        // Der Editor darf nie ohne Notiz dastehen.
        val (r, _) = repo()
        val id = r.speichern(vorgabe.zuNotiz(0L, "die einzige", 100L))

        val danach = r.loeschenOderLeeren(id, jetzt = 300L)

        assertEquals(1, r.notizen.first().size, "Die letzte Notiz wurde geloescht")
        assertNotNull(danach)
        assertEquals("", danach.text, "Die letzte Notiz wurde nicht geleert")
        assertEquals(id, danach.id, "Die Kennung hat sich geaendert")
    }

    @Test
    fun `beim Loeschen einer von mehreren wird keine Ersatznotiz geliefert`() = runTest {
        val (r, _) = repo()
        r.speichern(vorgabe.zuNotiz(0L, "eins", 100L))
        val weg = r.speichern(vorgabe.zuNotiz(0L, "zwei", 200L))

        assertNull(r.loeschenOderLeeren(weg, jetzt = 300L))
    }
}
