# Notizliste — Umsetzungsplan

> **Fuer agentische Umsetzer:** ERFORDERLICHE UNTER-SKILL: `superpowers:subagent-driven-development`
> (empfohlen) oder `superpowers:executing-plans`, um diesen Plan Aufgabe fuer Aufgabe
> abzuarbeiten. Die Schritte benutzen Checkbox-Syntax (`- [ ]`) zum Mitverfolgen.

**Ziel:** Mehrere Notizen dauerhaft speichern, jede mit ihrem eigenen Schriftbild, umschaltbar
ueber eine Liste im Editor.

**Aufbau:** Room speichert `NoteEntity` (Text + Schriftbild). Die gesamte Umwandlungs- und
Titel-Logik liegt in reinen Kotlin-Funktionen ohne Android-Bezug und wird auf dem PC geprueft;
Room selbst wird nur ueber ein DAO-Interface angesprochen, das in Tests durch ein Fake ersetzt
wird. Die vorhandene Regler-Oberflaeche bleibt unveraendert — `AppSettings` behaelt seine
Stil-Felder als Arbeitszustand, in den die geladene Notiz hinein- und aus dem sie
herausgeschrieben wird.

**Technik:** Kotlin 2.2.20, Room 2.8.2, KSP 2.2.20-2.0.4, Compose (BOM 2026.06.01), JUnit 5
ueber `kotlin("test")`, kotlinx-coroutines-test.

## Global geltende Vorgaben

- **Sprache:** Bezeichner, Kommentare und Testnamen auf Deutsch, Umlaute im Code als `ae/oe/ue`
  umschrieben (bestehende Konvention, siehe `machine/`). Nutzertexte in der Oberflaeche mit
  echten Umlauten.
- **Tests laufen ohne Geraet und ohne Netz.** Kein Robolectric, keine Instrumentation. Alles,
  was Android braucht (Room, DataStore, Compose), wird hinter einem Interface gekapselt.
- **Kommentare begruenden, sie beschreiben nicht.** Warum so und nicht anders — der bestehende
  Code ist durchgaengig so gehalten.
- **Blattformat, Rand und Papier-Offset bleiben global** und gehoeren NICHT zur Notiz
  (Entscheidung des Nutzers, siehe Spec).
- **Nach jeder Aufgabe:** `./gradlew test` muss gruen sein, `./gradlew assembleDebug` bauen.
- **Committen nach jeder Aufgabe**, Commit-Text auf Deutsch ohne Umlaute, mit
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` am Ende.

## Dateien

| Datei | Zustaendig fuer |
|---|---|
| `gradle/libs.versions.toml` | Room- und KSP-Versionen (aendern) |
| `app/build.gradle.kts` | Room, KSP, Test-Infrastruktur (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteEntity.kt` | Datensatz einer Notiz (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteLogik.kt` | Titel, Umwandlung, Erben, Migration — reine Funktionen (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteDao.kt` | DAO-Interface + Room-Fassung (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt` | Room-Datenbank, nur Verdrahtung (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteRepository.kt` | Laden, Speichern, Loeschen, Migration (neu) |
| `app/src/test/kotlin/de/emmpunkt/write/data/FakeNoteDao.kt` | DAO im Speicher, fuer Tests (neu) |
| `app/src/test/kotlin/de/emmpunkt/write/data/NoteLogikTest.kt` | Titel, Umwandlung, Erben (neu) |
| `app/src/test/kotlin/de/emmpunkt/write/data/NoteRepositoryTest.kt` | Repository gegen das Fake (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt` | aktuelle Notiz halten, umschalten (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/NotizListe.kt` | aufklappbare Liste (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt` | Liste einhaengen (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt` | neue Rueckrufe durchreichen (aendern) |

---

## Aufgabe 1: Fundament — Testbarkeit und Abhaengigkeiten

Das `app`-Modul hat bisher **keinen einzigen Test** und keine Test-Abhaengigkeiten. Ohne das
laesst sich nichts von dem pruefen, was hier entsteht.

**Dateien:**
- Aendern: `gradle/libs.versions.toml`
- Aendern: `app/build.gradle.kts`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/FundamentTest.kt`

**Schnittstellen:**
- Liefert: lauffaehiges `./gradlew :app:test`, Room- und KSP-Plugin verfuegbar.

- [ ] **Schritt 1: Versionen in den Katalog eintragen**

In `gradle/libs.versions.toml` unter `[versions]` ergaenzen:

```toml
room = "2.8.2"
ksp = "2.2.20-2.0.4"
```

Unter `[libraries]` ergaenzen:

```toml
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```

Unter `[plugins]` ergaenzen:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Wichtig:** Die KSP-Version muss mit der Kotlin-Version beginnen (`2.2.20-...`). Passt sie
nicht, bricht der Build mit einer Meldung ueber inkompatible Compiler-Versionen ab.

- [ ] **Schritt 2: app-Modul erweitern**

In `app/build.gradle.kts` im `plugins`-Block ergaenzen:

```kotlin
alias(libs.plugins.ksp)
```

Im `dependencies`-Block ergaenzen:

```kotlin
implementation(libs.androidx.room.runtime)
ksp(libs.androidx.room.compiler)

testImplementation(kotlin("test"))
testImplementation(libs.kotlinx.coroutines.test)
```

Im `android`-Block ergaenzen (sonst laufen die Unit-Tests nicht mit JUnit 5):

```kotlin
testOptions {
    unitTests.all { it.useJUnitPlatform() }
}
```

- [ ] **Schritt 3: Test schreiben, der beweist, dass die Infrastruktur steht**

`app/src/test/kotlin/de/emmpunkt/write/data/FundamentTest.kt`:

```kotlin
package de.emmpunkt.write.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Beweist nur, dass im app-Modul ueberhaupt Tests laufen.
 *
 * Bis zu dieser Aufgabe gab es hier keine Test-Infrastruktur - alles Pruefbare lag in `core`
 * und `machine`. Die Notizliste bringt zum ersten Mal Logik ins app-Modul, die geprueft
 * werden muss.
 */
class FundamentTest {
    @Test
    fun `Tests im app-Modul laufen`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Schritt 4: Ausfuehren**

Ausfuehren: `./gradlew :app:test`
Erwartet: BUILD SUCCESSFUL, 1 Test.

Falls Gradle die Abhaengigkeiten nicht findet: **ohne** `--offline` laufen lassen, Room und KSP
sind noch nicht im lokalen Zwischenspeicher.

- [ ] **Schritt 5: Gegenprobe, dass nichts anderes kaputt ist**

Ausfuehren: `./gradlew test assembleDebug`
Erwartet: BUILD SUCCESSFUL, 172 Tests aus `core` und `machine` weiterhin gruen.

- [ ] **Schritt 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test/
git commit -m "Fundament: Room, KSP und Test-Infrastruktur im app-Modul

Das app-Modul hatte bisher keine Tests. Die Notizliste bringt zum ersten Mal
Logik dorthin, die geprueft werden muss.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 2: Datensatz und reine Logik

Hier entsteht alles, was **ohne Android** funktioniert: der Datensatz, die Titelableitung und
die Umwandlung zwischen Notiz und Einstellungen. Room kommt erst in Aufgabe 4 dazu.

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/NoteEntity.kt`
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/NoteLogik.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/NoteLogikTest.kt`

**Schnittstellen:**
- Benutzt: `AppSettings` (vorhanden), `Align` aus `core.layout`.
- Liefert:
  - `NoteEntity(id: Long, text: String, updatedAt: Long, fontId: String, sizeMm: Float, align: String, lineSpacing: Float, letterSpacing: Float, wordSpacing: Float, slantDeg: Float)`
  - `fun titelVon(text: String, maxLaenge: Int = 40): String`
  - `fun NoteEntity.alignEnum(): Align`
  - `fun AppSettings.mitNotiz(note: NoteEntity): AppSettings`
  - `fun AppSettings.zuNotiz(id: Long, text: String, jetzt: Long): NoteEntity`
  - `fun neueNotiz(vorlage: NoteEntity?, vorgabe: AppSettings, jetzt: Long): NoteEntity`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/NoteLogikTest.kt`:

```kotlin
package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Logik der Notizliste, geprueft ohne Datenbank und ohne Geraet.
 *
 * Genau dafuer liegt sie in reinen Funktionen: waere sie im DAO oder im ViewModel, braeuchte
 * jeder dieser Faelle einen Emulator.
 */
class NoteLogikTest {

    private val vorgabe = AppSettings()

    private fun notiz(
        text: String = "Test",
        fontId: String = "sans",
        sizeMm: Float = 9f,
    ) = NoteEntity(
        id = 1L,
        text = text,
        updatedAt = 1000L,
        fontId = fontId,
        sizeMm = sizeMm,
        align = Align.CENTER.name,
        lineSpacing = 1.4f,
        letterSpacing = 0.2f,
        wordSpacing = -0.1f,
        slantDeg = 12f,
    )

    // ---- Titel ----

    @Test
    fun `Titel ist die erste nicht-leere Zeile`() {
        assertEquals("Einkaufsliste", titelVon("Einkaufsliste\nMilch\nBrot"))
    }

    @Test
    fun `fuehrende Leerzeilen werden uebersprungen`() {
        // Sonst hiesse eine Notiz, die mit Absatz beginnt, dauerhaft "Ohne Titel".
        assertEquals("Milch", titelVon("\n\n  \nMilch\nBrot"))
    }

    @Test
    fun `leerer Text bekommt einen Ersatztitel`() {
        assertEquals("Ohne Titel", titelVon(""))
        assertEquals("Ohne Titel", titelVon("   \n \n "))
    }

    @Test
    fun `zu langer Titel wird gekuerzt`() {
        val lang = "A".repeat(80)
        val titel = titelVon(lang, maxLaenge = 40)

        assertTrue(titel.length <= 41, "Zu lang: ${titel.length}")
        assertTrue(titel.endsWith("…"), "Kuerzung nicht kenntlich gemacht: $titel")
    }

    @Test
    fun `Umlaute bleiben heil`() {
        assertEquals("Grüße an Lieselotte", titelVon("Grüße an Lieselotte\nZeile 2"))
    }

    @Test
    fun `Rand-Leerzeichen der Titelzeile fallen weg`() {
        assertEquals("Milch", titelVon("   Milch   \nBrot"))
    }

    // ---- Umwandlung ----

    @Test
    fun `Notiz legt ihr Schriftbild ueber die Einstellungen`() {
        val s = vorgabe.mitNotiz(notiz(fontId = "serif", sizeMm = 11f))

        assertEquals("serif", s.fontId)
        assertEquals(11f, s.sizeMm)
        assertEquals(Align.CENTER, s.align)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(0.2f, s.letterSpacing)
        assertEquals(-0.1f, s.wordSpacing)
        assertEquals(12f, s.slantDeg)
    }

    @Test
    fun `Blatt und Maschine bleiben beim Laden einer Notiz unberuehrt`() {
        // Der Kern der Entscheidung des Nutzers: ein Notizwechsel darf das eingelegte
        // Papier nicht "aendern", und an der Maschine schon gar nichts.
        val eigene = vorgabe.copy(
            paperWidthMm = 200f,
            paperHeightMm = 150f,
            marginMm = 15f,
            paperOffsetXMm = 5f,
            paperOffsetYMm = 7f,
            host = "10.0.0.9",
            feedDrawMmMin = 900,
        )
        val s = eigene.mitNotiz(notiz())

        assertEquals(200f, s.paperWidthMm)
        assertEquals(150f, s.paperHeightMm)
        assertEquals(15f, s.marginMm)
        assertEquals(5f, s.paperOffsetXMm)
        assertEquals(7f, s.paperOffsetYMm)
        assertEquals("10.0.0.9", s.host)
        assertEquals(900, s.feedDrawMmMin)
    }

    @Test
    fun `unbekannte Ausrichtung faellt auf die Vorgabe zurueck`() {
        // In der Datenbank steht der Enum-Name als Text. Wird der Enum spaeter umbenannt,
        // darf die App nicht abstuerzen, sondern muss weiterlaufen.
        val kaputt = notiz().copy(align = "SCHRAEG_VON_UNTEN")
        assertEquals(AppSettings().align, vorgabe.mitNotiz(kaputt).align)
    }

    @Test
    fun `hin und zurueck erhaelt das Schriftbild`() {
        val original = notiz(text = "Hallo")
        val zurueck = vorgabe.mitNotiz(original).zuNotiz(id = 1L, text = "Hallo", jetzt = 1000L)

        assertEquals(original, zurueck)
    }

    // ---- Neue Notiz ----

    @Test
    fun `neue Notiz erbt das Schriftbild der Vorlage aber nicht den Text`() {
        val vorlage = notiz(text = "Alter Text", fontId = "serif", sizeMm = 11f)
        val neu = neueNotiz(vorlage, vorgabe, jetzt = 2000L)

        assertEquals("", neu.text, "Der Text der Vorlage wurde mitgeschleppt")
        assertEquals("serif", neu.fontId)
        assertEquals(11f, neu.sizeMm)
        assertEquals(0L, neu.id, "Eine neue Notiz darf noch keine Kennung haben")
        assertEquals(2000L, neu.updatedAt)
    }

    @Test
    fun `ohne Vorlage gelten die Vorgabewerte`() {
        val neu = neueNotiz(null, vorgabe, jetzt = 2000L)

        assertEquals(vorgabe.fontId, neu.fontId)
        assertEquals(vorgabe.sizeMm, neu.sizeMm)
        assertEquals(vorgabe.align.name, neu.align)
    }
}
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:test --tests '*NoteLogikTest*'`
Erwartet: FEHLER beim Uebersetzen, `Unresolved reference 'NoteEntity'` und `'titelVon'`.

- [ ] **Schritt 3: Den Datensatz anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/NoteEntity.kt`:

```kotlin
package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine gespeicherte Notiz: Text und Schriftbild.
 *
 * Blattformat, Raender und Papier-Offset gehoeren bewusst NICHT hierher. Sie beschreiben, was
 * auf dem Tisch liegt, nicht wie die Notiz aussieht - beim Umschalten auf eine andere Notiz
 * soll nicht plotzlich ein anderes Format eingestellt sein als das eingelegte Papier.
 *
 * [align] steht als Name des Enums und nicht als Zahl in der Datenbank: so bleibt sie von
 * Hand lesbar, und ein Umsortieren der Enum-Werte verschiebt nicht stillschweigend die
 * Ausrichtung aller gespeicherten Notizen.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    /** Zeitpunkt der letzten Aenderung; die Liste sortiert danach. */
    val updatedAt: Long,
    val fontId: String,
    val sizeMm: Float,
    val align: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,
)
```

- [ ] **Schritt 4: Die reine Logik anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/NoteLogik.kt`:

```kotlin
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
```

- [ ] **Schritt 5: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:test --tests '*NoteLogikTest*'`
Erwartet: BUILD SUCCESSFUL, 12 Tests gruen.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/NoteEntity.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/NoteLogik.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/NoteLogikTest.kt
git commit -m "Notiz-Datensatz und die Logik drumherum

Titelableitung, Umwandlung zwischen Notiz und Einstellungen, Erben fuer neue
Notizen - alles als reine Funktionen, damit es ohne Geraet pruefbar bleibt.

Blatt, Raender und Offset bleiben beim Laden einer Notiz unberuehrt; dafuer
gibt es eine eigene Gegenprobe.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 3: DAO-Schnittstelle, Fake und Repository

Das Repository ist die einzige Stelle, die der Rest der App kennt. Room steckt dahinter und
laesst sich in Tests durch eine Liste im Speicher ersetzen — dasselbe Muster wie `FakeFluidNc`
im `machine`-Modul.

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/NoteDao.kt`
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/NoteRepository.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/FakeNoteDao.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/NoteRepositoryTest.kt`

**Schnittstellen:**
- Benutzt: `NoteEntity`, `neueNotiz`, `AppSettings` aus Aufgabe 2.
- Liefert:
  - `interface NoteDao { fun alle(): Flow<List<NoteEntity>>; suspend fun laden(id: Long): NoteEntity?; suspend fun speichern(note: NoteEntity): Long; suspend fun loeschen(id: Long); suspend fun anzahl(): Int }`
  - `class NoteRepository(dao: NoteDao)` mit
    `val notizen: Flow<List<NoteEntity>>`,
    `suspend fun sicherstellenDassEineDaIst(lastText: String, vorgabe: AppSettings, jetzt: Long): NoteEntity`,
    `suspend fun speichern(note: NoteEntity): Long`,
    `suspend fun laden(id: Long): NoteEntity?`,
    `suspend fun loeschenOderLeeren(id: Long, jetzt: Long): NoteEntity?`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/NoteRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Schritt 2: Das Fake-DAO schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/FakeNoteDao.kt`:

```kotlin
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
```

- [ ] **Schritt 3: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:test --tests '*NoteRepositoryTest*'`
Erwartet: FEHLER, `Unresolved reference 'NoteDao'` und `'NoteRepository'`.

- [ ] **Schritt 4: Die DAO-Schnittstelle anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/NoteDao.kt`:

```kotlin
package de.emmpunkt.write.data

import androidx.room.Dao
import androidx.room.Delete
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
```

- [ ] **Schritt 5: Das Repository anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/NoteRepository.kt`:

```kotlin
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
     */
    suspend fun sicherstellenDassEineDaIst(
        lastText: String,
        vorgabe: AppSettings,
        jetzt: Long,
    ): NoteEntity {
        if (dao.anzahl() == 0) {
            val id = dao.speichern(vorgabe.zuNotiz(id = 0L, text = lastText, jetzt = jetzt))
            return checkNotNull(dao.laden(id)) { "Gerade angelegte Notiz nicht auffindbar" }
        }
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
```

- [ ] **Schritt 6: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:test`
Erwartet: BUILD SUCCESSFUL, **21 Tests** im app-Modul (1 Fundament + 12 Logik + 8 Repository).

- [ ] **Schritt 7: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/NoteDao.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/NoteRepository.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/
git commit -m "Notiz-Repository mit DAO-Schnittstelle und Fake

Room steckt hinter einem eigenen Interface, damit der Rest der App gegen eine
Liste im Speicher pruefbar bleibt - dasselbe Muster wie FakeFluidNc.

Zwei Regeln sitzen im Repository und nicht in der Oberflaeche: es gibt immer
mindestens eine Notiz, und die letzte wird geleert statt geloescht.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 4: Room verdrahten

Nur noch Verkabelung — die Logik steht. Diese Aufgabe hat bewusst keine eigenen Tests: sie
enthaelt keine Entscheidungen, und was hier schiefgeht, faellt beim ersten Start auf dem Geraet
sofort auf.

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt`

**Schnittstellen:**
- Liefert: `NoteDatabase.dao(context: Context): NoteDao`

- [ ] **Schritt 1: Die Datenbank anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt`:

```kotlin
package de.emmpunkt.write.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun notes(): RoomNoteDao

    companion object {
        @Volatile
        private var instanz: NoteDatabase? = null

        /**
         * Eine Datenbank fuer die ganze App.
         *
         * Room haelt Verbindungen und einen Zwischenspeicher; zwei Instanzen auf derselben
         * Datei wuerden sich gegenseitig veraltete Staende zeigen.
         */
        fun dao(context: Context): NoteDao = instanz ?: synchronized(this) {
            instanz ?: Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                "write_notes.db",
            ).build().also { instanz = it }
        }.notes()
    }
}
```

- [ ] **Schritt 2: Uebersetzen und pruefen, dass Room den Code erzeugt**

Ausfuehren: `./gradlew :app:assembleDebug`
Erwartet: BUILD SUCCESSFUL.

Bei `Cannot find implementation for NoteDatabase` ist das KSP-Plugin nicht aktiv — Aufgabe 1,
Schritt 2 pruefen.

Bei `Not sure how to convert a Cursor to this method's return type` steht in `RoomNoteDao` eine
Abfrage, deren Rueckgabetyp nicht passt — die Reihenfolge der `@Query`-Anmerkungen mit den
ueberschriebenen Methoden abgleichen.

- [ ] **Schritt 3: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt
git commit -m "Room-Datenbank fuer Notizen verdrahtet

Nur Verkabelung; alle Entscheidungen liegen in NoteLogik und NoteRepository und
sind dort ohne Geraet geprueft.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 5: ViewModel — Notiz laden, speichern, wechseln

Der Kern des Umbaus. Wichtig: **Die Regler-Oberflaeche bleibt unveraendert.** `AppSettings`
behaelt seine Stil-Felder als Arbeitszustand; beim Laden einer Notiz werden sie hineinkopiert,
beim Speichern heraus. Dadurch faellt der gesamte UI-Umbau weg, den ein eigener Notiz-Zustand
im ViewModel erzwungen haette.

**Dateien:**
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt`

**Schnittstellen:**
- Benutzt: `NoteRepository`, `NoteDatabase.dao`, `neueNotiz`, `mitNotiz`, `zuNotiz`, `titelVon`.
- Liefert (oeffentlich am ViewModel):
  - `val notizen: StateFlow<List<NoteEntity>>`
  - `val aktuelleNotizId: StateFlow<Long>`
  - `fun notizOeffnen(id: Long)`
  - `fun notizAnlegen()`
  - `fun notizLoeschen(id: Long)`

- [ ] **Schritt 1: Repository und Zustand anlegen**

In `PlotterViewModel.kt` bei den vorhandenen Feldern (nach `private val wakeLock`):

```kotlin
    private val notes = NoteRepository(NoteDatabase.dao(app))

    val notizen: StateFlow<List<NoteEntity>> = notes.notizen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _aktuelleNotizId = MutableStateFlow(0L)
    val aktuelleNotizId: StateFlow<Long> = _aktuelleNotizId.asStateFlow()

    /**
     * Laeuft, bis der Nutzer eine Weile nicht mehr tippt.
     *
     * Ohne diese Verzoegerung loeste jeder Tastendruck eine Schreibtransaktion aus.
     */
    private var speicherAuftrag: Job? = null
```

Noetige Ergaenzungen bei den Importen:

```kotlin
import de.emmpunkt.write.data.NoteDatabase
import de.emmpunkt.write.data.NoteEntity
import de.emmpunkt.write.data.NoteRepository
import de.emmpunkt.write.data.mitNotiz
import de.emmpunkt.write.data.neueNotiz
import de.emmpunkt.write.data.zuNotiz
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Schritt 2: Beim Start die Notiz laden statt lastText**

Im `init`-Block des ViewModels wird heute `lastText` in `_text` gelegt. Diesen Teil ersetzen:
Nachdem die Einstellungen zum ersten Mal geladen sind, einmalig

```kotlin
        viewModelScope.launch {
            val s = _settings.value
            val notiz = notes.sicherstellenDassEineDaIst(
                lastText = s.lastText,
                vorgabe = s,
                jetzt = System.currentTimeMillis(),
            )
            uebernehmen(notiz)
        }
```

Und die Hilfsmethode dazu:

```kotlin
    /** Legt eine geladene Notiz in den Arbeitszustand: Text und Schriftbild. */
    private fun uebernehmen(notiz: NoteEntity) {
        _aktuelleNotizId.value = notiz.id
        _text.value = notiz.text
        _settings.value = _settings.value.mitNotiz(notiz)
        recompute()
    }
```

- [ ] **Schritt 3: Speichern auf die Notiz umstellen**

Die vorhandene Methode `persist()` schreibt heute `lastText` in die Einstellungen. Sie
bekommt stattdessen:

```kotlin
    /**
     * Schreibt den Arbeitszustand in die aktuelle Notiz - verzoegert.
     *
     * Die Einstellungen wandern weiterhin in den DataStore: ihre Stilwerte sind ab jetzt die
     * Vorlage fuer die naechste neue Notiz.
     */
    private fun persist() {
        speicherAuftrag?.cancel()
        speicherAuftrag = viewModelScope.launch {
            delay(SPEICHER_VERZOEGERUNG_MS)
            val s = _settings.value
            repository.update(s)
            val id = _aktuelleNotizId.value
            if (id != 0L) {
                notes.speichern(s.zuNotiz(id, _text.value, System.currentTimeMillis()))
            }
        }
    }
```

Und bei den Konstanten am Ende der Klasse:

```kotlin
    private companion object {
        const val SPEICHER_VERZOEGERUNG_MS = 500L
    }
```

Steht dort bereits ein `companion object` (etwa mit `STATUS_POLL_MS`), die Konstante dort
ergaenzen statt ein zweites anzulegen.

- [ ] **Schritt 4: Umschalten, Anlegen, Loeschen**

```kotlin
    fun notizOeffnen(id: Long) {
        if (_machine.value.busy) return
        viewModelScope.launch {
            // Erst den offenen Stand sichern, sonst geht das zuletzt Getippte verloren.
            sofortSpeichern()
            notes.laden(id)?.let { uebernehmen(it) }
        }
    }

    fun notizAnlegen() {
        if (_machine.value.busy) return
        viewModelScope.launch {
            sofortSpeichern()
            val jetzt = System.currentTimeMillis()
            val vorlage = notes.laden(_aktuelleNotizId.value)
            val id = notes.speichern(neueNotiz(vorlage, _settings.value, jetzt))
            notes.laden(id)?.let { uebernehmen(it) }
        }
    }

    fun notizLoeschen(id: Long) {
        if (_machine.value.busy) return
        viewModelScope.launch {
            val geleert = notes.loeschenOderLeeren(id, System.currentTimeMillis())
            when {
                // War es die letzte, bleibt sie offen - nur eben leer.
                geleert != null -> uebernehmen(geleert)
                // Die offene Notiz ist weg: auf die naechstbeste umschalten.
                id == _aktuelleNotizId.value ->
                    notes.zuletztBearbeiteteOderNull()?.let { uebernehmen(it) }
            }
        }
    }

    /** Schreibt sofort statt verzoegert - vor jedem Wechsel noetig. */
    private suspend fun sofortSpeichern() {
        speicherAuftrag?.cancel()
        val s = _settings.value
        repository.update(s)
        val id = _aktuelleNotizId.value
        if (id != 0L) {
            notes.speichern(s.zuNotiz(id, _text.value, System.currentTimeMillis()))
        }
    }
```

`zuletztBearbeiteteOderNull()` steht bereits im Repository (Aufgabe 3, Schritt 5).

- [ ] **Schritt 5: Uebersetzen und pruefen**

Ausfuehren: `./gradlew test assembleDebug`
Erwartet: BUILD SUCCESSFUL, alle Tests gruen.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/NoteRepository.kt
git commit -m "ViewModel haelt die aktuelle Notiz

Beim Start wird die zuletzt bearbeitete geoeffnet, beim Wechseln der offene
Stand vorher gesichert. Gespeichert wird verzoegert, sonst schriebe jeder
Tastendruck eine Transaktion.

Die Regler-Oberflaeche bleibt unveraendert: AppSettings behaelt seine Stilfelder
als Arbeitszustand, in den die Notiz hinein- und aus dem sie herausgeschrieben
wird. Ihre gespeicherten Werte sind damit zugleich die Vorlage fuer die naechste
neue Notiz.

Waehrend eines laufenden Auftrags ist Umschalten gesperrt - sonst zeigte die
Vorschau etwas anderes, als die Maschine gerade faehrt.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 6: Die Liste in der Oberflaeche

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/ui/NotizListe.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt`

**Schnittstellen:**
- Benutzt: `notizen`, `aktuelleNotizId`, `notizOeffnen`, `notizAnlegen`, `notizLoeschen`,
  `titelVon`.

- [ ] **Schritt 1: Die Liste als eigene Datei**

`app/src/main/kotlin/de/emmpunkt/write/ui/NotizListe.kt`:

```kotlin
package de.emmpunkt.write.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.data.NoteEntity
import de.emmpunkt.write.data.titelVon
import java.text.DateFormat
import java.util.Date

/**
 * Die aufgeklappte Notizliste.
 *
 * Geloescht wird ueber ein Symbol mit Rueckfrage und bewusst NICHT ueber eine Wischgeste: in
 * einer Liste, die man zum Umschalten antippt, sitzt Wischen zu nah an der Auswahl - und ohne
 * Papierkorb ist ein versehentlich geloeschter Text weg.
 */
@Composable
fun NotizListe(
    notizen: List<NoteEntity>,
    aktuelleId: Long,
    onOeffnen: (Long) -> Unit,
    onLoeschen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var loeschKandidat by remember { mutableStateOf<NoteEntity?>(null) }

    LazyColumn(
        // Begrenzt, damit die Liste bei vielen Notizen nicht den ganzen Editor verdraengt.
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp),
    ) {
        items(notizen, key = { it.id }) { notiz ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onOeffnen(notiz.id) },
                colors = if (notiz.id == aktuelleId) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            titelVon(notiz.text),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(notiz.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { loeschKandidat = notiz }) {
                        Icon(Icons.Default.Delete, contentDescription = "Notiz löschen")
                    }
                }
            }
        }
    }

    loeschKandidat?.let { notiz ->
        AlertDialog(
            onDismissRequest = { loeschKandidat = null },
            title = { Text("Notiz löschen?") },
            text = { Text("„${titelVon(notiz.text)}“ wird gelöscht. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = {
                    onLoeschen(notiz.id)
                    loeschKandidat = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { loeschKandidat = null }) { Text("Abbrechen") }
            },
        )
    }
}
```

- [ ] **Schritt 2: Im Editor einhaengen**

In `EditorScreen.kt` die Signatur um fuenf Werte erweitern:

```kotlin
    notizen: List<NoteEntity>,
    aktuelleNotizId: Long,
    onNotizOeffnen: (Long) -> Unit,
    onNotizAnlegen: () -> Unit,
    onNotizLoeschen: (Long) -> Unit,
```

Im Rumpf, direkt vor dem `OutlinedTextField` mit der Notiz:

```kotlin
        var listeOffen by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { listeOffen = !listeOffen }) {
                Text(if (listeOffen) "Notizen schließen" else "Notizen (${notizen.size})")
            }
            TextButton(onClick = onNotizAnlegen) { Text("+ Neu") }
        }

        if (listeOffen) {
            NotizListe(
                notizen = notizen,
                aktuelleId = aktuelleNotizId,
                onOeffnen = {
                    onNotizOeffnen(it)
                    listeOffen = false
                },
                onLoeschen = onNotizLoeschen,
            )
        }
```

Noetiger Import: `import de.emmpunkt.write.data.NoteEntity`.

- [ ] **Schritt 3: In MainActivity durchreichen**

Beim Aufruf von `EditorScreen` ergaenzen:

```kotlin
                    notizen = notizen,
                    aktuelleNotizId = aktuelleNotizId,
                    onNotizOeffnen = viewModel::notizOeffnen,
                    onNotizAnlegen = viewModel::notizAnlegen,
                    onNotizLoeschen = viewModel::notizLoeschen,
```

Und weiter oben, wo die anderen Zustaende eingesammelt werden:

```kotlin
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val aktuelleNotizId by viewModel.aktuelleNotizId.collectAsStateWithLifecycle()
```

- [ ] **Schritt 4: Bauen und auf dem Geraet ansehen**

```bash
./gradlew test assembleDebug
adb connect 192.168.2.30:5555
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Am Geraet pruefen — **nicht nur bauen und behaupten**:

1. Der alte Text ist als erste Notiz da (Migration).
2. „+ Neu" legt eine leere Notiz an; Schrift und Groesse bleiben wie zuvor.
3. Umschalten laedt Text UND Schriftbild; **Blattformat und Rand aendern sich dabei nicht**.
4. Loeschen fragt nach; die letzte Notiz wird geleert statt entfernt.
5. Nach dem Neustart der App ist die zuletzt bearbeitete Notiz offen.

- [ ] **Schritt 5: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/ app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt
git commit -m "Notizliste in der Oberflaeche

Aufklappbar ueber dem Notizfeld, damit der Weg tippen -> plotten so kurz bleibt
wie bisher. Geloescht wird ueber ein Symbol mit Rueckfrage und nicht per Wischen:
in einer Liste, die man zum Umschalten antippt, saesse die Wischgeste zu nah an
der Auswahl.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 7: Dokumentation nachziehen

- [ ] **Schritt 1: README ergaenzen**

Einen Abschnitt „Notizen" vor „Zwei Wege zum Plotter" einfuegen, der festhaelt:
- Jede Notiz traegt ihr eigenes Schriftbild.
- Blattformat, Rand und Papier-Offset gelten global — mit der Begruendung (das Papier liegt
  auf dem Tisch, nicht im Dokument).
- Der Titel ist die erste Zeile.
- Die Testzahl in der Bauanleitung anpassen (`./gradlew test` — neuen Wert eintragen).

- [ ] **Schritt 2: CLAUDE.md ergaenzen**

Unter „Etappe 3" festhalten: Teil 2 steht, was gebaut wurde, und dass Vorlagen (Teil 3) der Ort
sind, an dem ein abweichendes Blattformat mitkommen darf.

- [ ] **Schritt 3: Commit und Push**

```bash
git add README.md CLAUDE.md
git commit -m "Doku: Notizliste

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
git push origin maschinenwerte-und-sd-upload
```

---

## Selbstpruefung des Plans

**Abdeckung der Spec:**

| Anforderung aus der Spec | Aufgabe |
|---|---|
| Mehrere Notizen, dauerhaft (Room) | 1, 3, 4 |
| Schriftbild gehoert zur Notiz | 2, 5 |
| Blatt/Rand/Offset bleiben global | 2 (Test „Blatt und Maschine bleiben unberuehrt"), 6 (Punkt 3 der Geraeteprobe) |
| Liste zum Umschalten, Anlegen, Loeschen | 6 |
| Migration von `lastText` | 3 (Tests), 5 (Aufruf beim Start) |
| Titel = erste Zeile | 2 |
| Neue Notiz erbt Schriftbild | 2, 5 |
| Letzte Notiz wird geleert statt geloescht | 3 |
| Gesperrt waehrend eines Auftrags | 5 |
| Alles ohne Geraet pruefbar | 1, 2, 3 |

**Offen gelassen (bewusst, laut Spec):** Vorlagen mit Platzhaltern, gemischte Stile je Absatz,
Suche, Ordner, Sortieroptionen, Papierkorb, Export.

**Bekannte Schwachstelle dieses Plans:** Aufgabe 5 und 6 haben keine eigenen automatisierten
Tests — das ViewModel und Compose sind ohne Emulator nicht pruefbar, und dieses Projekt hat
sich bewusst gegen Robolectric entschieden. Deshalb steht in Aufgabe 6 eine ausdrueckliche
Liste dessen, was am Geraet nachzusehen ist. Die Logik, die dort verdrahtet wird, ist in den
Aufgaben 2 und 3 vollstaendig abgedeckt.
