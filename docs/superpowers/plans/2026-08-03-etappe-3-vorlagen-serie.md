# Vorlagen mit Platzhaltern und Serienlauf — Umsetzungsplan

> **Fuer agentische Umsetzer:** ERFORDERLICHE UNTER-SKILL: `superpowers:subagent-driven-development`
> (empfohlen) oder `superpowers:executing-plans`, um diesen Plan Aufgabe fuer Aufgabe
> abzuarbeiten. Die Schritte benutzen Checkbox-Syntax (`- [ ]`) zum Mitverfolgen.

**Ziel:** Einen Satz gleichartiger Karten in einem Durchgang schreiben — eine Vorlage mit
benannten Platzhaltern, eine Werteliste, und die App plottet Bogen fuer Bogen mit Pause zum
Blattwechsel.

**Aufbau:** Die gesamte Logik (Platzhalter, Werteliste, Ueberlaufpruefung, Ablaufsteuerung)
liegt in reinen Kotlin-Klassen ohne Android-Bezug und wird auf dem PC geprueft. Der
`Serienlauf` bekommt das Plotten als Funktion hereingereicht — im Test steckt dort eine
Attrappe, sodass Fehlschlag, Wiederholung, Ueberspringen und Wiederaufnahme ohne Maschine
pruefbar sind. Room speichert die Vorlagen in einer zweiten Tabelle neben den Notizen.

**Technik:** Kotlin 2.2.20, Room 2.8.2, KSP 2.2.20-2.0.4, Compose (BOM 2026.06.01), JUnit 5
ueber `kotlin("test")`, kotlinx-coroutines-test. Alles bereits im Projekt vorhanden — diese
Etappe braucht **keine neue Abhaengigkeit**.

## Global geltende Vorgaben

- **Sprache:** Bezeichner, Kommentare und Testnamen auf Deutsch, Umlaute im Code als `ae/oe/ue`
  umschrieben. Nutzertexte in der Oberflaeche mit echten Umlauten.
- **Tests laufen ohne Geraet und ohne Netz.** Kein Robolectric, keine Instrumentation.
- **Kommentare begruenden, sie beschreiben nicht.**
- **Trennzeichen der Werteliste ist das Semikolon** (`;`). Ein Wert mit Semikolon ist nicht
  darstellbar — bewusst in Kauf genommen.
- **Eine Vorlage traegt Schriftbild UND Blattformat**, aber **nicht** den Papier-Offset. Der
  bleibt global, weil er den Anschlag beschreibt, nicht das Dokument.
- **Die Notizen duerfen bei der Datenbank-Erweiterung nicht verlorengehen.** Kein
  `fallbackToDestructiveMigration`.
- **Nach jeder Aufgabe:** `./gradlew test` gruen, `./gradlew assembleDebug` baut.
- **Committen nach jeder Aufgabe**, Commit-Text auf Deutsch ohne Umlaute, mit
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` am Ende.
- **Gradle-Hinweis:** `:app:test` ist bei Android nur eine Sammelaufgabe. Einzelne Tests laufen
  mit `./gradlew :app:testDebugUnitTest --tests '*Name*'` — `--tests` kennt nur diese Aufgabe.

## Abweichung von der Spec

Die Spec nennt das Feld `WerteZeile.text`. Im Plan heisst es `bezeichnung` — es ist der Text
fuer **Meldungen** („Bogen 14 „Liebe Christiane Schmidt-Wagner""), nicht der Text, der auf den
Bogen kommt. Der entsteht erst aus `einsetzen(vorlage, felder)`. Zwei verschiedene Dinge
duerfen nicht gleich heissen.

## Dateien

| Datei | Zustaendig fuer |
|---|---|
| `app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt` | Platzhalter, Werteliste, Einsetzen — reine Funktionen (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/TemplateEntity.kt` | Datensatz einer Vorlage (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/BogenPruefung.kt` | Ueberlaufpruefung je Bogen (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/Serienlauf.kt` | Ablaufsteuerung des Satzes (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/TemplateDao.kt` | DAO-Schnittstelle + Room-Fassung (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/TemplateRepository.kt` | Laden, Speichern, Loeschen (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt` | zweite Tabelle, Version 2, Migration (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/StilLeiste.kt` | Regler, aus EditorScreen herausgeloest (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt` | Regler entfernen, Aufruf bleibt (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/SerieScreen.kt` | der neue Reiter (neu) |
| `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt` | Serie-Zustand und Maschinenanbindung (aendern) |
| `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt` | vierter Reiter (aendern) |
| `app/src/test/.../VorlagenLogikTest.kt`, `BogenPruefungTest.kt`, `SerienlaufTest.kt`, `TemplateRepositoryTest.kt`, `FakeTemplateDao.kt` | Tests (neu) |

---

## Aufgabe 1: Reine Logik — Platzhalter und Werteliste

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/VorlagenLogikTest.kt`

**Schnittstellen:**
- Benutzt: nichts aus dem Projekt (reines Kotlin).
- Liefert:
  - `const val FELD_TRENNER: Char`
  - `data class WerteZeile(nummer: Int, felder: Map<String,String>, fehler: String?)` mit
    `val bezeichnung: String`
  - `fun platzhalterIn(text: String): List<String>`
  - `fun vorlagenFehler(text: String): String?`
  - `fun werteZeilen(eingabe: String, spalten: List<String>): List<WerteZeile>`
  - `fun einsetzen(text: String, werte: Map<String, String>): String`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/VorlagenLogikTest.kt`:

```kotlin
package de.emmpunkt.write.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vorlagen-Logik, geprueft ohne Datenbank und ohne Geraet.
 *
 * Sie liegt in reinen Funktionen, damit genau das moeglich ist - im ViewModel braeuchte jeder
 * dieser Faelle einen Emulator.
 */
class VorlagenLogikTest {

    // ---- Platzhalter finden ----

    @Test
    fun `Platzhalter stehen in der Reihenfolge ihres ersten Auftretens`() {
        assertEquals(
            listOf("anrede", "name"),
            platzhalterIn("{anrede} {name}, wir freuen uns!"),
        )
    }

    @Test
    fun `ein doppelt genannter Platzhalter zaehlt einmal`() {
        // Sonst erwartete die Werteliste zwei Spalten fuer dasselbe Feld.
        assertEquals(listOf("name"), platzhalterIn("{name}, ja du, {name}!"))
    }

    @Test
    fun `Umlaute im Platzhalternamen sind erlaubt`() {
        assertEquals(listOf("groesse", "größe"), platzhalterIn("{groesse} {größe}"))
    }

    @Test
    fun `Text ohne Platzhalter liefert nichts`() {
        assertEquals(emptyList(), platzhalterIn("Frohe Weihnachten"))
    }

    @Test
    fun `geschweifte Klammern ohne Inhalt sind kein Platzhalter`() {
        assertEquals(emptyList(), platzhalterIn("Ein Satz mit {} darin"))
    }

    // ---- Vorlage pruefen ----

    @Test
    fun `eine Vorlage ohne Platzhalter wird bemaengelt`() {
        val fehler = vorlagenFehler("Frohe Weihnachten")

        assertNotNull(fehler)
        assertTrue(fehler.contains("{name}"), "Die Meldung zeigt kein Beispiel: $fehler")
    }

    @Test
    fun `mehrere Platzhalter sind ausdruecklich in Ordnung`() {
        // Der Nutzer braucht sie fuer die Anrede: "Liebe" oder "Lieber", je nach Person.
        assertNull(vorlagenFehler("{anrede} {name},"))
    }

    // ---- Werteliste ----

    @Test
    fun `jede Zeile wird zu einem Bogen`() {
        val zeilen = werteZeilen("Liebe;Anna\nLieber;Bernd", listOf("anrede", "name"))

        assertEquals(2, zeilen.size)
        assertEquals(mapOf("anrede" to "Liebe", "name" to "Anna"), zeilen[0].felder)
        assertEquals(mapOf("anrede" to "Lieber", "name" to "Bernd"), zeilen[1].felder)
        assertTrue(zeilen.all { it.fehler == null })
    }

    @Test
    fun `leere Zeilen und Rand-Leerzeichen fallen weg`() {
        val zeilen = werteZeilen("\n  Liebe ; Anna  \n\n", listOf("anrede", "name"))

        assertEquals(1, zeilen.size)
        assertEquals(mapOf("anrede" to "Liebe", "name" to "Anna"), zeilen[0].felder)
    }

    @Test
    fun `die Nummer zaehlt Bogen, nicht Zeilen der Eingabe`() {
        // Eine Leerzeile in der Mitte darf die Bogennummern nicht verschieben - sonst passte
        // die Meldung nicht zum Zaehler des Serienlaufs.
        val zeilen = werteZeilen("Liebe;Anna\n\nLieber;Bernd", listOf("anrede", "name"))

        assertEquals(listOf(1, 2), zeilen.map { it.nummer })
    }

    @Test
    fun `zu wenige Felder werden gemeldet und nicht ergaenzt`() {
        val zeilen = werteZeilen("Liebe;Anna\nBernd", listOf("anrede", "name"))

        assertNull(zeilen[0].fehler)
        val fehler = zeilen[1].fehler
        assertNotNull(fehler)
        assertTrue(fehler.contains("Bogen 2"), "Ohne Bogennummer nicht auffindbar: $fehler")
        assertTrue(fehler.contains("anrede;name"), "Die erwartete Form fehlt: $fehler")
    }

    @Test
    fun `zu viele Felder werden ebenfalls gemeldet`() {
        val zeilen = werteZeilen("Liebe;Anna;Zusatz", listOf("anrede", "name"))

        assertNotNull(zeilen[0].fehler)
    }

    @Test
    fun `ein leeres Feld ist erlaubt`() {
        // Nicht jede Karte braucht jedes Feld - ein Titel fehlt oft berechtigt.
        val zeilen = werteZeilen(";Anna", listOf("titel", "name"))

        assertNull(zeilen[0].fehler)
        assertEquals("", zeilen[0].felder["titel"])
    }

    @Test
    fun `die Bezeichnung laesst leere Felder weg`() {
        val zeilen = werteZeilen(";Anna", listOf("titel", "name"))

        assertEquals("Anna", zeilen[0].bezeichnung)
    }

    @Test
    fun `ohne Spalten entsteht kein Bogen`() {
        // Eine Vorlage ohne Platzhalter wird schon von vorlagenFehler abgefangen; hier darf
        // nichts Halbfertiges herauskommen.
        assertEquals(emptyList(), werteZeilen("Anna\nBernd", emptyList()))
    }

    // ---- Einsetzen ----

    @Test
    fun `Platzhalter werden ersetzt`() {
        assertEquals(
            "Liebe Anna, wir freuen uns!",
            einsetzen("{anrede} {name}, wir freuen uns!", mapOf("anrede" to "Liebe", "name" to "Anna")),
        )
    }

    @Test
    fun `derselbe Platzhalter wird an jeder Stelle ersetzt`() {
        assertEquals("Anna, ja du, Anna!", einsetzen("{name}, ja du, {name}!", mapOf("name" to "Anna")))
    }

    @Test
    fun `ein unbekannter Platzhalter bleibt stehen`() {
        // Ein sichtbares {tisch} auf dem Bogen ist ein Fehler, den man sieht. Eine
        // stillschweigende Luecke waere schlimmer.
        assertEquals("Anna, Tisch {tisch}", einsetzen("{name}, Tisch {tisch}", mapOf("name" to "Anna")))
    }

    @Test
    fun `Sonderzeichen im Wert werden nicht als Ersetzungsmuster gedeutet`() {
        // Regex-Ersetzung deutet $1 sonst als Gruppenverweis - der Wert kaeme verstuemmelt an.
        assertEquals("Preis: 5\$1", einsetzen("Preis: {betrag}", mapOf("betrag" to "5\$1")))
    }
}
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*VorlagenLogikTest*'`
Erwartet: FEHLER beim Uebersetzen, `Unresolved reference 'platzhalterIn'`.

- [ ] **Schritt 3: Die Logik anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt`:

```kotlin
package de.emmpunkt.write.data

/**
 * Trennzeichen der Werteliste.
 *
 * Das Komma schied aus, weil es in Namen vorkommt ("Schmidt, Anna"); der Tabulator laesst sich
 * auf einer Telefontastatur nicht tippen. Ein Wert, der selbst ein Semikolon enthaelt, ist
 * damit nicht darstellbar - bei Anreden und Namen faellt das nicht ins Gewicht.
 */
const val FELD_TRENNER = ';'

/**
 * Platzhalter stehen in geschweiften Klammern: `{name}`.
 *
 * Benannt und nicht durchnummeriert: Wer die Vorlage nach Monaten wieder oeffnet, liest
 * `{anrede} {name}` und weiss sofort, welche Spalte was ist. Bei `{1} {2}` muesste er die
 * Werteliste danebenlegen und abzaehlen.
 */
private val PLATZHALTER = Regex("""\{([\p{L}\p{N}_-]+)}""")

/** Eine Zeile der Werteliste - ein Bogen. */
data class WerteZeile(
    /** 1-basiert und zugleich die Bogennummer, die der Serienlauf zaehlt. */
    val nummer: Int,
    val felder: Map<String, String>,
    /** null, wenn die Zeile brauchbar ist; sonst die Meldung fuer den Nutzer. */
    val fehler: String? = null,
) {
    /**
     * Kurzform fuer Meldungen ("Bogen 14 „Liebe Christiane"").
     *
     * NICHT der Text, der auf den Bogen kommt - der entsteht aus [einsetzen] und der Vorlage.
     */
    val bezeichnung: String get() = felder.values.filter { it.isNotEmpty() }.joinToString(" ")
}

/** Die Platzhalternamen, in Reihenfolge ihres ersten Auftretens, ohne Doppelte. */
fun platzhalterIn(text: String): List<String> =
    PLATZHALTER.findAll(text).map { it.groupValues[1] }.distinct().toList()

/** `null`, wenn die Vorlage brauchbar ist; sonst die Meldung. */
fun vorlagenFehler(text: String): String? =
    if (platzhalterIn(text).isEmpty()) {
        "Die Vorlage enthält keinen Platzhalter wie {name}."
    } else {
        null
    }

/**
 * Zerlegt die Werteliste: eine Zeile je Bogen, Felder durch [FELD_TRENNER] getrennt.
 *
 * Die Spalten ordnen sich [spalten] der Reihe nach zu - das sind die Platzhalter in der
 * Reihenfolge ihres ersten Auftretens im Vorlagentext.
 *
 * Zeilen mit falscher Feldzahl werden GEMELDET, nicht ergaenzt. Fehlende Felder stillschweigend
 * leer zu lassen erzeugte eine Karte mit einer Luecke, die erst auf dem Papier auffiele.
 */
fun werteZeilen(eingabe: String, spalten: List<String>): List<WerteZeile> {
    if (spalten.isEmpty()) return emptyList()

    return eingabe.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, zeile ->
            val felder = zeile.split(FELD_TRENNER).map { it.trim() }
            val nummer = index + 1
            if (felder.size != spalten.size) {
                val wort = if (felder.size == 1) "Feld" else "Felder"
                WerteZeile(
                    nummer = nummer,
                    felder = emptyMap(),
                    fehler = "Bogen $nummer hat ${felder.size} $wort, erwartet werden " +
                        "${spalten.size} (${spalten.joinToString(FELD_TRENNER.toString())}).",
                )
            } else {
                WerteZeile(nummer = nummer, felder = spalten.zip(felder).toMap())
            }
        }
        .toList()
}

/**
 * Ersetzt die Platzhalter durch ihre Werte.
 *
 * Ein unbekannter Platzhalter bleibt stehen - siehe [WerteZeile]. Der Ersatz wird woertlich
 * eingesetzt: `Regex.replace` mit Funktion deutet `$1` im Wert NICHT als Gruppenverweis,
 * anders als die Variante mit Zeichenkette.
 */
fun einsetzen(text: String, werte: Map<String, String>): String =
    PLATZHALTER.replace(text) { treffer -> werte[treffer.groupValues[1]] ?: treffer.value }
```

- [ ] **Schritt 4: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*VorlagenLogikTest*'`
Erwartet: BUILD SUCCESSFUL, 19 Tests gruen.

Pruefen, dass die Tests wirklich liefen (ein leeres Ergebnis waere ebenfalls „gruen"):
`grep -o 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-de.emmpunkt.write.data.VorlagenLogikTest.xml`

- [ ] **Schritt 5: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/VorlagenLogikTest.kt
git commit -m "Vorlagen-Logik: Platzhalter, Werteliste, Einsetzen

Benannte Platzhalter statt durchnummerierter, damit eine Vorlage nach Monaten
noch lesbar ist. Zeilen mit falscher Feldzahl werden gemeldet statt ergaenzt -
eine stillschweigende Luecke faellt erst auf dem Papier auf.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 2: Datensatz der Vorlage und Umwandlung

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/TemplateEntity.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt` (Umwandlung anhaengen)
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/VorlageUmwandlungTest.kt`

**Schnittstellen:**
- Benutzt: `AppSettings` (vorhanden), `Align` aus `core.layout`, `alignEnum()`-Muster aus
  `NoteLogik.kt`.
- Liefert:
  - `TemplateEntity(id, name, text, werte, updatedAt, fontId, sizeMm, align, lineSpacing, letterSpacing, wordSpacing, slantDeg, paperWidthMm, paperHeightMm, marginMm)`
  - `fun AppSettings.mitVorlage(v: TemplateEntity): AppSettings`
  - `fun AppSettings.zuVorlage(id: Long, name: String, text: String, werte: String, jetzt: Long): TemplateEntity`
  - `fun neueVorlage(vorgabe: AppSettings, jetzt: Long): TemplateEntity`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/VorlageUmwandlungTest.kt`:

```kotlin
package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import kotlin.test.Test
import kotlin.test.assertEquals

class VorlageUmwandlungTest {

    private val vorgabe = AppSettings()

    private fun vorlage() = TemplateEntity(
        id = 1L,
        name = "Platzkarten",
        text = "{anrede} {name},",
        werte = "Liebe;Anna",
        updatedAt = 1000L,
        fontId = "serif",
        sizeMm = 11f,
        align = Align.CENTER.name,
        lineSpacing = 1.4f,
        letterSpacing = 0.2f,
        wordSpacing = -0.1f,
        slantDeg = 12f,
        paperWidthMm = 100f,
        paperHeightMm = 70f,
        marginMm = 6f,
    )

    @Test
    fun `die Vorlage legt Schriftbild UND Blatt ueber die Einstellungen`() {
        // Der Unterschied zur Notiz: eine Grusskarte bringt ihr Format mit.
        val s = vorgabe.mitVorlage(vorlage())

        assertEquals("serif", s.fontId)
        assertEquals(11f, s.sizeMm)
        assertEquals(Align.CENTER, s.align)
        assertEquals(1.4f, s.lineSpacing)
        assertEquals(0.2f, s.letterSpacing)
        assertEquals(-0.1f, s.wordSpacing)
        assertEquals(12f, s.slantDeg)
        assertEquals(100f, s.paperWidthMm)
        assertEquals(70f, s.paperHeightMm)
        assertEquals(6f, s.marginMm)
    }

    @Test
    fun `der Papier-Offset bleibt unberuehrt`() {
        // Er beschreibt, wo die Blattecke am Anschlag liegt - das aendert sich nicht dadurch,
        // dass ein kleineres Blatt eingelegt wird.
        val eigene = vorgabe.copy(paperOffsetXMm = 5f, paperOffsetYMm = 7f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals(5f, s.paperOffsetXMm)
        assertEquals(7f, s.paperOffsetYMm)
    }

    @Test
    fun `Maschine und Verbindung bleiben unberuehrt`() {
        val eigene = vorgabe.copy(host = "10.0.0.9", feedDrawMmMin = 900, workAreaXMm = 300f)
        val s = eigene.mitVorlage(vorlage())

        assertEquals("10.0.0.9", s.host)
        assertEquals(900, s.feedDrawMmMin)
        assertEquals(300f, s.workAreaXMm)
    }

    @Test
    fun `unbekannte Ausrichtung faellt auf die Vorgabe zurueck`() {
        val kaputt = vorlage().copy(align = "SCHRAEG_VON_UNTEN")

        assertEquals(AppSettings().align, vorgabe.mitVorlage(kaputt).align)
    }

    @Test
    fun `hin und zurueck erhaelt die Vorlage`() {
        val original = vorlage()
        val zurueck = vorgabe.mitVorlage(original)
            .zuVorlage(id = 1L, name = "Platzkarten", text = "{anrede} {name},",
                werte = "Liebe;Anna", jetzt = 1000L)

        assertEquals(original, zurueck)
    }

    @Test
    fun `eine neue Vorlage uebernimmt die aktuellen Einstellungen und ist sonst leer`() {
        val eigene = vorgabe.copy(fontId = "serif", sizeMm = 9f, paperWidthMm = 120f)
        val neu = neueVorlage(eigene, jetzt = 2000L)

        assertEquals(0L, neu.id, "Eine neue Vorlage darf noch keine Kennung haben")
        assertEquals("serif", neu.fontId)
        assertEquals(9f, neu.sizeMm)
        assertEquals(120f, neu.paperWidthMm)
        assertEquals("", neu.werte)
        assertEquals(2000L, neu.updatedAt)
    }

    @Test
    fun `eine neue Vorlage bringt einen Beispieltext mit Platzhalter mit`() {
        // Sonst startet der Nutzer mit einem leeren Feld und der Meldung "kein Platzhalter" -
        // ohne zu wissen, wie einer aussieht.
        val neu = neueVorlage(vorgabe, jetzt = 2000L)

        assertEquals(null, vorlagenFehler(neu.text))
    }
}
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*VorlageUmwandlungTest*'`
Erwartet: FEHLER, `Unresolved reference 'TemplateEntity'`.

- [ ] **Schritt 3: Den Datensatz anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/TemplateEntity.kt`:

```kotlin
package de.emmpunkt.write.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Eine Vorlage: Text mit Platzhaltern, Schriftbild, Blattformat und die Werteliste.
 *
 * Anders als [NoteEntity] traegt sie das BLATTFORMAT mit. Das ist der Unterschied zwischen
 * Notiz und Vorlage: Eine Grusskarte bringt ihr Format mit, eine Notiz wird auf das Papier
 * geschrieben, das gerade auf dem Tisch liegt.
 *
 * Der Papier-Offset gehoert weiterhin NICHT dazu - er beschreibt, wo die Blattecke am Anschlag
 * liegt, und das aendert sich nicht dadurch, dass ein kleineres Blatt eingelegt wird.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Name der Vorlage, z. B. "Platzkarten Hochzeit". */
    val name: String,
    /** Text mit Platzhaltern in geschweiften Klammern: "{anrede} {name}," */
    val text: String,
    /**
     * Werteliste: eine Zeile je Bogen, Felder durch Semikolon getrennt.
     *
     * Mitgespeichert, damit ein Satz wiederholbar ist - und damit ein abgebrochener Satz
     * spaeter fortgesetzt werden kann, ohne die Namen neu zu tippen.
     */
    val werte: String,
    val updatedAt: Long,

    // Schriftbild
    val fontId: String,
    val sizeMm: Float,
    val align: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,

    // Blattformat
    val paperWidthMm: Float,
    val paperHeightMm: Float,
    val marginMm: Float,
)
```

- [ ] **Schritt 4: Die Umwandlung an `VorlagenLogik.kt` anhaengen**

Zuerst den Import ergaenzen:

```kotlin
import de.emmpunkt.write.core.layout.Align
```

Dann ans Ende der Datei:

```kotlin
/** Beispieltext einer frisch angelegten Vorlage. */
private const val VORLAGE_BEISPIEL = "{anrede} {name},"

/**
 * Die Ausrichtung als Enum.
 *
 * Wie bei der Notiz: ein unbekannter Name in der Datenbank fuehrt zur Vorgabe, nicht zum
 * Absturz beim Oeffnen einer alten Vorlage.
 */
fun TemplateEntity.alignEnum(): Align =
    runCatching { Align.valueOf(align) }.getOrElse { AppSettings().align }

/**
 * Legt Schriftbild UND Blattformat der Vorlage ueber die Einstellungen.
 *
 * Papier-Offset, Maschine und Verbindung bleiben unberuehrt - sie beschreiben die Einrichtung,
 * nicht das Dokument.
 */
fun AppSettings.mitVorlage(v: TemplateEntity): AppSettings = copy(
    fontId = v.fontId,
    sizeMm = v.sizeMm,
    align = v.alignEnum(),
    lineSpacing = v.lineSpacing,
    letterSpacing = v.letterSpacing,
    wordSpacing = v.wordSpacing,
    slantDeg = v.slantDeg,
    paperWidthMm = v.paperWidthMm,
    paperHeightMm = v.paperHeightMm,
    marginMm = v.marginMm,
)

/** Der umgekehrte Weg: aus dem Arbeitszustand wird wieder eine Vorlage zum Speichern. */
fun AppSettings.zuVorlage(
    id: Long,
    name: String,
    text: String,
    werte: String,
    jetzt: Long,
) = TemplateEntity(
    id = id,
    name = name,
    text = text,
    werte = werte,
    updatedAt = jetzt,
    fontId = fontId,
    sizeMm = sizeMm,
    align = align.name,
    lineSpacing = lineSpacing,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    slantDeg = slantDeg,
    paperWidthMm = paperWidthMm,
    paperHeightMm = paperHeightMm,
    marginMm = marginMm,
)

/**
 * Eine neue, leere Vorlage mit den aktuellen Einstellungen als Ausgangspunkt.
 *
 * Der Beispieltext ist Absicht: Mit leerem Feld begruesste die App den Nutzer sonst mit
 * "enthält keinen Platzhalter", ohne zu zeigen, wie einer aussieht.
 */
fun neueVorlage(vorgabe: AppSettings, jetzt: Long): TemplateEntity =
    vorgabe.zuVorlage(id = 0L, name = "Neue Vorlage", text = VORLAGE_BEISPIEL, werte = "", jetzt = jetzt)
```

- [ ] **Schritt 5: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*VorlageUmwandlungTest*'`
Erwartet: BUILD SUCCESSFUL, 7 Tests gruen.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/TemplateEntity.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/VorlagenLogik.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/VorlageUmwandlungTest.kt
git commit -m "Datensatz der Vorlage und Umwandlung

Anders als die Notiz traegt die Vorlage das Blattformat mit - eine Grusskarte
bringt ihr Format mit. Der Papier-Offset bleibt aussen vor, er beschreibt den
Anschlag und nicht das Dokument; dafuer gibt es eine eigene Gegenprobe.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 3: Ueberlaufpruefung je Bogen

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/BogenPruefung.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/BogenPruefungTest.kt`

**Schnittstellen:**
- Benutzt: `layoutText`, `TextStyle`, `Frame`, `StrokeFont`, `Fonts.load` aus dem `core`-Modul;
  `WerteZeile` und `einsetzen` aus Aufgabe 1; `AppSettings.toFrame()`.
- Liefert:
  - `data class BogenBefund(index: Int, bezeichnung: String, ueberlauf: Boolean, hartGetrennt: Set<String>)` mit `val inOrdnung: Boolean`
  - `fun pruefeBogen(zeilen: List<WerteZeile>, vorlage: String, style: TextStyle, frame: Frame, font: StrokeFont): List<BogenBefund>`
  - `fun rahmenFehler(s: AppSettings): String?`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/BogenPruefungTest.kt`:

```kotlin
package de.emmpunkt.write.data

import de.emmpunkt.write.core.font.Fonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BogenPruefungTest {

    private val font = Fonts.load(Fonts.defaultId)

    /** Kleine Karte, damit ein langer Name zuverlaessig ueberlaeuft. */
    private val klein = AppSettings(paperWidthMm = 60f, paperHeightMm = 30f, marginMm = 4f, sizeMm = 6f)

    private fun zeilen(vararg werte: String) =
        werteZeilen(werte.joinToString("\n"), listOf("name"))

    @Test
    fun `ein passender Bogen wird nicht bemaengelt`() {
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(1, befunde.size)
        assertTrue(befunde[0].inOrdnung, "Anna passt auf die Karte, wurde aber bemaengelt")
    }

    @Test
    fun `ein zu langer Wert wird gemeldet`() {
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna", "Christiane Schmidt-Wagner von Hohenlohe zu Langenburg"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertTrue(befunde[0].inOrdnung)
        assertFalse(befunde[1].inOrdnung, "Der lange Name passt nicht, wurde aber durchgewinkt")
    }

    @Test
    fun `der Befund nennt Bogennummer und Bezeichnung`() {
        // Ohne beides muesste der Nutzer raten, welche Karte gemeint ist.
        val befunde = pruefeBogen(
            zeilen = zeilen("Anna", "Bernd"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(listOf(0, 1), befunde.map { it.index })
        assertEquals(listOf("Anna", "Bernd"), befunde.map { it.bezeichnung })
    }

    @Test
    fun `ein hart getrenntes Wort gilt als nicht in Ordnung`() {
        // Bei einer Platzkarte ist ein mitten durchgeschnittener Nachname genauso unbrauchbar
        // wie ein Ueberlauf - anders als im Editor, wo es nur eine Warnung ist.
        val befunde = pruefeBogen(
            zeilen = zeilen("Donaudampfschifffahrtsgesellschaftskapitaen"),
            vorlage = "{name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertFalse(befunde[0].inOrdnung)
        assertTrue(befunde[0].hartGetrennt.isNotEmpty(), "Die harte Trennung wurde nicht erkannt")
    }

    @Test
    fun `kaputte Zeilen werden nicht geprueft`() {
        // werteZeilen hat sie schon gemeldet; hier wuerden sie nur ein zweites Mal auftauchen.
        val gemischt = werteZeilen("Liebe;Anna\nBernd", listOf("anrede", "name"))
        val befunde = pruefeBogen(
            zeilen = gemischt.filter { it.fehler == null },
            vorlage = "{anrede} {name}",
            style = klein.toTextStyle(),
            frame = klein.toFrame(),
            font = font,
        )

        assertEquals(1, befunde.size)
    }

    // ---- Rahmenfehler ----

    @Test
    fun `ein Rand breiter als das Blatt wird als Meldung geliefert statt zu werfen`() {
        // Frame wirft im Konstruktor. Ohne diese Abfangung stuerzte die App ab, sobald jemand
        // 8 mm Rand auf einer 10-mm-Karte einstellt.
        val unmoeglich = AppSettings(paperWidthMm = 10f, paperHeightMm = 10f, marginMm = 8f)

        val fehler = rahmenFehler(unmoeglich)

        assertNotNull(fehler, "Der unmoegliche Rahmen wurde nicht bemaengelt")
    }

    @Test
    fun `ein brauchbarer Rahmen liefert keine Meldung`() {
        assertNull(rahmenFehler(klein))
    }
}
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*BogenPruefungTest*'`
Erwartet: FEHLER, `Unresolved reference 'pruefeBogen'`.

- [ ] **Schritt 3: Die Pruefung anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/BogenPruefung.kt`:

```kotlin
package de.emmpunkt.write.data

import de.emmpunkt.write.core.font.StrokeFont
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.layoutText

/** Was bei einem einzelnen Bogen herauskommt. */
data class BogenBefund(
    /** 0-basiert; angezeigt wird `index + 1`. */
    val index: Int,
    /** Kurzform der Werte, damit die Meldung die Karte benennt. */
    val bezeichnung: String,
    /** Text hoeher als der nutzbare Bereich. */
    val ueberlauf: Boolean,
    /** Woerter, die mitten im Wort umbrochen werden mussten. */
    val hartGetrennt: Set<String>,
) {
    /**
     * Beides sperrt den Start.
     *
     * Im Editor ist eine harte Trennung nur eine Warnung - dort entscheidet der Nutzer bei
     * jedem Text selbst. Bei einem Satz Platzkarten faellt ein mitten durchgeschnittener
     * Nachname sofort auf, und niemand sieht ihn vor dem Plotten.
     */
    val inOrdnung: Boolean get() = !ueberlauf && hartGetrennt.isEmpty()
}

/**
 * Rechnet jeden Bogen durch, bevor die Maschine laeuft.
 *
 * Bewusst mit dem VORHANDENEN [layoutText] - derselben Funktion, aus der auch Vorschau und
 * G-Code entstehen. Ein zweiter Weg, "passt das?" zu beantworten, koennte von dem abweichen,
 * was der Stift spaeter faehrt.
 *
 * [zeilen] darf nur fehlerfreie Zeilen enthalten; kaputte hat `werteZeilen` schon gemeldet.
 */
fun pruefeBogen(
    zeilen: List<WerteZeile>,
    vorlage: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
): List<BogenBefund> = zeilen.mapIndexed { index, zeile ->
    val laid = layoutText(einsetzen(vorlage, zeile.felder), style, frame, font)
    BogenBefund(
        index = index,
        bezeichnung = zeile.bezeichnung,
        ueberlauf = laid.overflow,
        hartGetrennt = laid.overlongWords,
    )
}

/**
 * Prueft, ob aus den Einstellungen ueberhaupt ein Rahmen entstehen kann.
 *
 * `Frame` wirft im Konstruktor, wenn die Raender breiter sind als das Blatt. Bei einer Vorlage
 * mit 8 mm Rand auf einer 10-mm-Karte fuehrte das zum Absturz statt zu einer Meldung.
 */
fun rahmenFehler(s: AppSettings): String? =
    runCatching { s.toFrame() }.exceptionOrNull()?.let {
        "Blatt und Rand passen nicht zusammen: ${it.message}"
    }
```

- [ ] **Schritt 4: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*BogenPruefungTest*'`
Erwartet: BUILD SUCCESSFUL, 7 Tests gruen.

Falls „ein zu langer Wert wird gemeldet" NICHT fehlschlaegt, ist die Testkarte zu gross: Die
Werte in `klein` verkleinern (Blatt 40x20 mm), bis der lange Name sicher uebersteht. Der Test
muss den Fehler wirklich fangen, sonst prueft er nichts.

- [ ] **Schritt 5: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/BogenPruefung.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/BogenPruefungTest.kt
git commit -m "Ueberlaufpruefung je Bogen vor dem Start

Rechnet jeden Bogen mit dem vorhandenen layoutText durch - derselben Funktion,
aus der Vorschau und G-Code entstehen. Ein zweiter Weg koennte von dem abweichen,
was der Stift faehrt.

Ein unmoeglicher Rahmen (Rand breiter als das Blatt) wird abgefangen: Frame wirft
dort im Konstruktor, was sonst die App abstuerzen liesse.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 4: Der Serienlauf

Das Kernstueck. Die Klasse kennt weder Telnet noch SD-Karte — deshalb ist der ganze Ablauf
gegen eine Attrappe pruefbar.

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/Serienlauf.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/SerienlaufTest.kt`

**Schnittstellen:**
- Benutzt: `kotlinx.coroutines.flow.StateFlow`.
- Liefert:
  - `sealed interface SerienZustand` mit `Bereit`, `Laeuft`, `WartetAufBlatt`, `Fehlgeschlagen`,
    `Fertig`, `Abgebrochen`
  - `class Serienlauf(bogen: List<String>, plotteBogen: suspend (Int, String) -> Result<Unit>, startAb: Int = 0)`
    mit `val zustand: StateFlow<SerienZustand>`, `suspend fun naechsterBogen()`,
    `fun ueberspringen()`, `fun abbrechen()`

- [ ] **Schritt 1: Die fehlschlagenden Tests schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/SerienlaufTest.kt`:

```kotlin
package de.emmpunkt.write.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Der komplette Satz-Ablauf, geprueft ohne Maschine.
 *
 * Genau dafuer bekommt [Serienlauf] das Plotten hereingereicht: Hier steckt eine Attrappe
 * darin, die mitzaehlt und auf Wunsch scheitert. An der Maschine kostete jeder dieser Faelle
 * ein Blatt Papier.
 */
class SerienlaufTest {

    /** Merkt sich, welche Bogen geplottet wurden, und scheitert bei den genannten. */
    private class PlotAttrappe(private val scheitertBei: Set<Int> = emptySet()) {
        val geplottet = mutableListOf<Int>()
        var versuche = 0
            private set

        suspend fun plotte(index: Int, @Suppress("UNUSED_PARAMETER") text: String): Result<Unit> {
            versuche++
            return if (index in scheitertBei) {
                Result.failure(IllegalStateException("Verbindung weg"))
            } else {
                geplottet += index
                Result.success(Unit)
            }
        }
    }

    private val dreiBogen = listOf("Anna", "Bernd", "Clara")

    @Test
    fun `ein Satz laeuft Bogen fuer Bogen durch`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, attrappe::plotte)

        assertEquals(SerienZustand.Bereit(0, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.WartetAufBlatt(1, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.WartetAufBlatt(2, 3), lauf.zustand.value)

        lauf.naechsterBogen()
        assertEquals(SerienZustand.Fertig(geplottet = 3, uebersprungen = 0), lauf.zustand.value)
        assertEquals(listOf(0, 1, 2), attrappe.geplottet)
    }

    @Test
    fun `nach einem Fehlschlag bleibt der Zaehler stehen`() = runTest {
        // Der entscheidende Unterschied: "nochmal" plottet DENSELBEN Bogen. Rueckte der
        // Zaehler weiter, bekaeme ein Gast keine Karte.
        val attrappe = PlotAttrappe(scheitertBei = setOf(1))
        val lauf = Serienlauf(dreiBogen, attrappe::plotte)

        lauf.naechsterBogen()
        lauf.naechsterBogen()

        val zustand = lauf.zustand.value
        assertIs<SerienZustand.Fehlgeschlagen>(zustand)
        assertEquals(1, zustand.index)
        assertTrue(zustand.meldung.contains("Verbindung"), "Die Ursache fehlt: ${zustand.meldung}")
    }

    @Test
    fun `ein fehlgeschlagener Bogen laesst sich wiederholen`() = runTest {
        var scheitern = true
        val geplottet = mutableListOf<Int>()
        val lauf = Serienlauf(dreiBogen, { index, _ ->
            if (index == 1 && scheitern) {
                scheitern = false
                Result.failure(IllegalStateException("Blatt verrutscht"))
            } else {
                geplottet += index
                Result.success(Unit)
            }
        })

        lauf.naechsterBogen()   // Bogen 0
        lauf.naechsterBogen()   // Bogen 1 scheitert
        lauf.naechsterBogen()   // Bogen 1 nochmal, klappt

        assertEquals(listOf(0, 1), geplottet)
        assertEquals(SerienZustand.WartetAufBlatt(2, 3), lauf.zustand.value)
    }

    @Test
    fun `ein Bogen laesst sich ueberspringen`() = runTest {
        val attrappe = PlotAttrappe(scheitertBei = setOf(1))
        val lauf = Serienlauf(dreiBogen, attrappe::plotte)

        lauf.naechsterBogen()
        lauf.naechsterBogen()
        lauf.ueberspringen()
        lauf.naechsterBogen()

        assertEquals(listOf(0, 2), attrappe.geplottet)
        assertEquals(SerienZustand.Fertig(geplottet = 2, uebersprungen = 1), lauf.zustand.value)
    }

    @Test
    fun `ueberspringen geht auch ohne Fehlschlag`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, attrappe::plotte)

        lauf.ueberspringen()
        lauf.naechsterBogen()

        assertEquals(listOf(1), attrappe.geplottet)
    }

    @Test
    fun `nach dem Abbruch passiert nichts mehr`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, attrappe::plotte)

        lauf.naechsterBogen()
        lauf.abbrechen()
        lauf.naechsterBogen()
        lauf.ueberspringen()

        assertEquals(SerienZustand.Abgebrochen, lauf.zustand.value)
        assertEquals(listOf(0), attrappe.geplottet, "Nach dem Abbruch wurde weitergeplottet")
    }

    @Test
    fun `ein abgebrochener Satz laesst sich spaeter fortsetzen`() = runTest {
        val attrappe = PlotAttrappe()
        val lauf = Serienlauf(dreiBogen, attrappe::plotte, startAb = 2)

        assertEquals(SerienZustand.Bereit(2, 3), lauf.zustand.value)
        lauf.naechsterBogen()

        assertEquals(listOf(2), attrappe.geplottet)
        assertEquals(SerienZustand.Fertig(geplottet = 1, uebersprungen = 0), lauf.zustand.value)
    }

    @Test
    fun `waehrend des Plottens meldet der Zustand welcher Bogen laeuft`() = runTest {
        // Der Zustand waehrend des Laufs ist von aussen nicht zu erwischen - wenn
        // naechsterBogen() zurueckkehrt, ist er schon wieder weg. Deshalb fragt ihn die
        // Plot-Funktion selbst ab. Die Zuweisung nach der Erzeugung ist noetig, weil die
        // Funktion das Objekt braucht, das gerade erst entsteht.
        var lauf: Serienlauf? = null
        var gesehen: SerienZustand? = null
        lauf = Serienlauf(dreiBogen, { _, _ ->
            gesehen = lauf?.zustand?.value
            Result.success(Unit)
        })

        lauf.naechsterBogen()

        assertEquals(SerienZustand.Laeuft(0, 3), gesehen)
    }

    @Test
    fun `ein leerer Satz ist sofort fertig`() = runTest {
        val lauf = Serienlauf(emptyList(), { _, _ -> Result.success(Unit) })

        assertEquals(SerienZustand.Fertig(geplottet = 0, uebersprungen = 0), lauf.zustand.value)
    }
}
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*SerienlaufTest*'`
Erwartet: FEHLER, `Unresolved reference 'Serienlauf'`.

- [ ] **Schritt 3: Den Serienlauf anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/Serienlauf.kt`:

```kotlin
package de.emmpunkt.write.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wo ein Satz gerade steht. */
sealed interface SerienZustand {
    /** Noch nichts geplottet; als Naechstes kommt Bogen [naechster] (0-basiert). */
    data class Bereit(val naechster: Int, val gesamt: Int) : SerienZustand

    data class Laeuft(val index: Int, val gesamt: Int) : SerienZustand

    /** [fertig] = wie viele Bogen erledigt sind, geplottet oder uebersprungen. */
    data class WartetAufBlatt(val fertig: Int, val gesamt: Int) : SerienZustand

    data class Fehlgeschlagen(val index: Int, val meldung: String) : SerienZustand

    data class Fertig(val geplottet: Int, val uebersprungen: Int) : SerienZustand

    data object Abgebrochen : SerienZustand
}

/**
 * Steuert einen Satz gleichartiger Bogen.
 *
 * Kennt weder Telnet noch SD-Karte: Das Plotten kommt als [plotteBogen] herein. Dadurch ist
 * der ganze Ablauf - Fehlschlag, Wiederholung, Ueberspringen, Abbruch, Wiederaufnahme - ohne
 * Maschine pruefbar. An der Maschine kostete jeder dieser Faelle ein Blatt Papier.
 *
 * @param plotteBogen Erfolg heisst: der Auftrag lief bis `SendProgress.Completed` durch.
 * @param startAb fuer die Wiederaufnahme eines abgebrochenen Satzes.
 */
class Serienlauf(
    private val bogen: List<String>,
    private val plotteBogen: suspend (index: Int, text: String) -> Result<Unit>,
    startAb: Int = 0,
) {
    private var naechster = startAb.coerceIn(0, bogen.size)
    private var geplottet = 0
    private var uebersprungen = 0
    private var abgebrochen = false

    private val _zustand = MutableStateFlow<SerienZustand>(
        if (naechster >= bogen.size) {
            SerienZustand.Fertig(geplottet = 0, uebersprungen = 0)
        } else {
            SerienZustand.Bereit(naechster, bogen.size)
        },
    )
    val zustand: StateFlow<SerienZustand> = _zustand.asStateFlow()

    /** Plottet den naechsten Bogen und haelt danach an. */
    suspend fun naechsterBogen() {
        if (abgebrochen || naechster >= bogen.size) return

        val index = naechster
        _zustand.value = SerienZustand.Laeuft(index, bogen.size)
        val ergebnis = plotteBogen(index, bogen[index])

        // Waehrend des Plottens abgebrochen: der Abbruch hat das letzte Wort.
        if (abgebrochen) return

        ergebnis.fold(
            onSuccess = {
                geplottet++
                weiterruecken()
            },
            onFailure = { e ->
                // Der Zaehler bleibt stehen - "nochmal" plottet denselben Bogen.
                _zustand.value = SerienZustand.Fehlgeschlagen(
                    index = index,
                    meldung = e.message ?: e::class.simpleName ?: "Unbekannter Fehler",
                )
            },
        )
    }

    /** Ueberspringt den aktuellen Bogen - nach einem Fehlschlag oder auf Wunsch. */
    fun ueberspringen() {
        if (abgebrochen || naechster >= bogen.size) return
        uebersprungen++
        weiterruecken()
    }

    fun abbrechen() {
        abgebrochen = true
        _zustand.value = SerienZustand.Abgebrochen
    }

    private fun weiterruecken() {
        naechster++
        _zustand.value = if (naechster >= bogen.size) {
            SerienZustand.Fertig(geplottet, uebersprungen)
        } else {
            SerienZustand.WartetAufBlatt(fertig = naechster, gesamt = bogen.size)
        }
    }
}
```

- [ ] **Schritt 4: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*SerienlaufTest*'`
Erwartet: BUILD SUCCESSFUL, 9 Tests gruen.

- [ ] **Schritt 5: Gegenprobe, dass der Test den Fehler faengt**

Im `onFailure`-Zweig versuchsweise `weiterruecken()` ergaenzen und die Tests erneut laufen
lassen. Erwartet: „nach einem Fehlschlag bleibt der Zaehler stehen" und „ein fehlgeschlagener
Bogen laesst sich wiederholen" schlagen fehl. Danach **zurueckbauen**.

Ohne diese Gegenprobe waere nicht bewiesen, dass die Tests die eine Regel absichern, auf die
es hier ankommt.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/Serienlauf.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/SerienlaufTest.kt
git commit -m "Serienlauf: Ablaufsteuerung eines Satzes

Bekommt das Plotten hereingereicht und kennt weder Telnet noch SD-Karte. Damit
sind Fehlschlag, Wiederholung, Ueberspringen, Abbruch und Wiederaufnahme ohne
Maschine pruefbar - an der Maschine kostete jeder dieser Faelle ein Blatt.

Ein Fehlschlag rueckt den Zaehler NICHT weiter: nochmal plottet denselben Bogen,
erst Ueberspringen geht weiter. Mit Gegenprobe abgesichert.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 5: Room — zweite Tabelle und Repository

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/TemplateDao.kt`
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/data/TemplateRepository.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/FakeTemplateDao.kt`
- Anlegen: `app/src/test/kotlin/de/emmpunkt/write/data/TemplateRepositoryTest.kt`

**Schnittstellen:**
- Benutzt: `TemplateEntity` aus Aufgabe 2, `NoteEntity`/`RoomNoteDao` (vorhanden).
- Liefert:
  - `interface TemplateDao` mit `alle()`, `laden(id)`, `speichern(v)`, `loeschen(id)`,
    `zuletztBearbeitete()`
  - `class TemplateRepository(dao: TemplateDao)` mit `val vorlagen: Flow<List<TemplateEntity>>`,
    `suspend fun laden(id)`, `suspend fun speichern(v): Long`, `suspend fun loeschen(id)`,
    `suspend fun zuletztBearbeiteteOderNull(): TemplateEntity?`
  - `NoteDatabase.templateDao(context): TemplateDao`

- [ ] **Schritt 1: Die fehlschlagenden Tests und das Fake schreiben**

`app/src/test/kotlin/de/emmpunkt/write/data/FakeTemplateDao.kt`:

```kotlin
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
```

`app/src/test/kotlin/de/emmpunkt/write/data/TemplateRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Schritt 2: Ausfuehren und Fehlschlag bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*TemplateRepositoryTest*'`
Erwartet: FEHLER, `Unresolved reference 'TemplateDao'`.

- [ ] **Schritt 3: DAO und Repository anlegen**

`app/src/main/kotlin/de/emmpunkt/write/data/TemplateDao.kt`:

```kotlin
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
```

`app/src/main/kotlin/de/emmpunkt/write/data/TemplateRepository.kt`:

```kotlin
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
```

- [ ] **Schritt 4: Ausfuehren und Erfolg bestaetigen**

Ausfuehren: `./gradlew :app:testDebugUnitTest --tests '*TemplateRepositoryTest*'`
Erwartet: BUILD SUCCESSFUL, 6 Tests gruen.

- [ ] **Schritt 5: Die Datenbank auf Version 2 heben — mit Migration**

`NoteDatabase.kt` bekommt die zweite Tabelle. **Wichtig:** Ohne Migration wirft Room beim
Start `IllegalStateException: Room cannot verify the data integrity`. Mit
`fallbackToDestructiveMigration()` waeren alle Notizen des Nutzers weg — das ist hier
ausdruecklich verboten.

Zuerst die Datei aendern:

```kotlin
package de.emmpunkt.write.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, TemplateEntity::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun notes(): RoomNoteDao
    abstract fun templates(): RoomTemplateDao

    companion object {
        @Volatile
        private var instanz: NoteDatabase? = null

        fun dao(context: Context): NoteDao = datenbank(context).notes()

        fun templateDao(context: Context): TemplateDao = datenbank(context).templates()

        /**
         * Version 1 -> 2: die Vorlagentabelle kommt dazu, die Notizen bleiben unberuehrt.
         *
         * Die Anweisung ist NICHT von Hand geschrieben, sondern aus dem von Room erzeugten
         * `NoteDatabase_Impl.kt` uebernommen. Room prueft beim Start Spalte fuer Spalte gegen
         * seine eigene Erwartung; eine selbst formulierte Anweisung weicht fast immer in einem
         * Detail ab (NOT NULL, Reihenfolge, Typname) und laesst die App dann abstuerzen.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_TEMPLATES)
            }
        }

        private const val CREATE_TEMPLATES = "HIER die Anweisung aus NoteDatabase_Impl.kt einsetzen"

        /**
         * Eine Datenbank fuer die ganze App.
         *
         * Room haelt Verbindungen und einen Zwischenspeicher; zwei Instanzen auf derselben
         * Datei wuerden sich gegenseitig veraltete Staende zeigen.
         */
        private fun datenbank(context: Context): NoteDatabase = instanz ?: synchronized(this) {
            instanz ?: Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                "write_notes.db",
            ).addMigrations(MIGRATION_1_2).build().also { instanz = it }
        }
    }
}
```

- [ ] **Schritt 6: Die echte CREATE-TABLE-Anweisung holen**

Ausfuehren: `./gradlew :app:assembleDebug`

Der Build laeuft durch (der Platzhaltertext ist gueltiges Kotlin). Danach:

```bash
grep -n "CREATE TABLE IF NOT EXISTS \`templates\`" \
  app/build/generated/ksp/debug/kotlin/de/emmpunkt/write/data/NoteDatabase_Impl.kt
```

Die gefundene Zeichenkette **woertlich** als Wert von `CREATE_TEMPLATES` einsetzen. Sie sieht
ungefaehr so aus (nicht abschreiben — die erzeugte Fassung ist massgeblich):

```
CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, ...)
```

Anschliessend erneut `./gradlew :app:assembleDebug`.

- [ ] **Schritt 7: Die Migration am Geraet pruefen**

Das ist der einzige Weg, sie zu pruefen — sie greift nur auf einer **vorhandenen** Datenbank
der Version 1.

```bash
adb connect 192.168.2.30:5555
# Vorher sichern, damit ein Fehlschlag die Notizen nicht kostet:
adb -s 192.168.2.30:5555 shell "run-as de.emmpunkt.write cat databases/write_notes.db" > /tmp/write_notes.v1.db
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.2.30:5555 shell am force-stop de.emmpunkt.write
adb -s 192.168.2.30:5555 logcat -c
adb -s 192.168.2.30:5555 shell am start -n de.emmpunkt.write/.MainActivity
adb -s 192.168.2.30:5555 logcat -d | grep -iE "Room|migration|FATAL"
```

Erwartet: kein Absturz, keine Room-Meldung. Die vorhandenen Notizen sind noch da.

Bei `Migration didn't properly handle` stimmt die CREATE-Anweisung nicht mit Rooms Erwartung
ueberein — die Meldung nennt die abweichende Spalte. Schritt 6 wiederholen.

- [ ] **Schritt 8: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/data/TemplateDao.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/TemplateRepository.kt \
        app/src/main/kotlin/de/emmpunkt/write/data/NoteDatabase.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/FakeTemplateDao.kt \
        app/src/test/kotlin/de/emmpunkt/write/data/TemplateRepositoryTest.kt
git commit -m "Vorlagentabelle in Room, mit Migration statt Neuanlage

Version 1 -> 2 legt nur die neue Tabelle an; die Notizen des Nutzers bleiben.
Die CREATE-Anweisung stammt aus dem von Room erzeugten Code - eine selbst
formulierte weicht fast immer in einem Detail ab und laesst die App abstuerzen.

Anders als bei den Notizen gibt es KEINE Regel 'mindestens eine' - ohne Vorlage
ist der Serie-Reiter schlicht leer, und das ist gueltig.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 6: Regler aus dem Editor herausloesen

Reiner Umbau, kein neues Verhalten. `EditorScreen.kt` ist mit ueber 500 Zeilen die groesste
Datei der App; die Regler werden von zwei Bildschirmen gebraucht.

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/ui/StilLeiste.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt`

**Schnittstellen:**
- Liefert: `@Composable fun StilLeiste(settings, textLeer, onChange, onChangeLive, onCommit, onAutoFit)`
  und `@Composable fun AuswahlFeld(label, selected, options, onSelect, modifier)` — beide
  **ohne** `private`, damit `SerieScreen` sie benutzen kann.

- [ ] **Schritt 1: Die drei Composables verschieben**

`StilLeiste`, `StilRegler` und `AuswahlFeld` samt ihrer Kommentare aus `EditorScreen.kt` in die
neue Datei `StilLeiste.kt` verschieben. Dort:

- `private fun StilLeiste` → `fun StilLeiste` (wird von zwei Bildschirmen gebraucht)
- `private fun AuswahlFeld` → `fun AuswahlFeld` (dito)
- `private fun StilRegler` bleibt `private` (nur innerhalb der Datei benutzt)

Die noetigen Importe wandern mit. Der Kopf der neuen Datei:

```kotlin
package de.emmpunkt.write.ui

/**
 * Die Regler fuer Schriftbild und Blatt.
 *
 * Aus `EditorScreen.kt` herausgeloest, weil sie von zwei Bildschirmen gebraucht werden: dem
 * Editor und dem Serie-Reiter. Eine zweite, leicht abweichende Leiste zu bauen hiesse, dass
 * beide mit der Zeit auseinanderlaufen.
 *
 * Sie arbeiten auf [AppSettings], auch wenn im Serie-Reiter eine Vorlage bearbeitet wird: Die
 * Vorlage wird dafuer in einen AppSettings-Arbeitszustand geladen - dasselbe Verfahren wie bei
 * den Notizen.
 */
```

In `EditorScreen.kt` bleiben die Aufrufe unveraendert. Nicht mehr benutzte Importe dort
entfernen (der Uebersetzer meldet sie als Warnung).

- [ ] **Schritt 2: Uebersetzen und pruefen, dass sich nichts geaendert hat**

Ausfuehren: `./gradlew test assembleDebug`
Erwartet: BUILD SUCCESSFUL, alle Tests gruen, keine neuen Warnungen ausser entfernten Importen.

- [ ] **Schritt 3: Am Geraet gegenprobe, dass der Editor unveraendert aussieht**

```bash
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.2.30:5555 shell am force-stop de.emmpunkt.write
adb -s 192.168.2.30:5555 shell am start -n de.emmpunkt.write/.MainActivity
adb -s 192.168.2.30:5555 exec-out screencap -p > /tmp/editor-nach-umbau.png
```

Erwartet: Schrift- und Blattfeld, Groessenregler, Ausrichtung und „Schriftbild…" stehen wie
zuvor. Ein reiner Umbau, der die Oberflaeche veraendert, ist keiner.

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/StilLeiste.kt \
        app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt
git commit -m "Regler aus EditorScreen herausgeloest

Sie werden vom Editor und vom Serie-Reiter gebraucht. Eine zweite, leicht
abweichende Leiste liefe mit der Zeit auseinander.

Reiner Umbau: kein neues Verhalten, am Geraet gegengeprueft.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 7: ViewModel — Serie-Zustand und Maschinenanbindung

**Dateien:**
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt`

**Schnittstellen:**
- Benutzt: `TemplateRepository`, `NoteDatabase.templateDao`, `Serienlauf`, `SerienZustand`,
  `platzhalterIn`, `vorlagenFehler`, `werteZeilen`, `einsetzen`, `pruefeBogen`, `rahmenFehler`,
  `mitVorlage`, `zuVorlage`, `neueVorlage`.
- Liefert (oeffentlich am ViewModel):
  - `data class SerieUiState(...)` mit `val bogenGesamt: Int` und `val startbar: Boolean`
  - `val serie: StateFlow<SerieUiState>`
  - `fun vorlageOeffnen(id: Long)`, `fun vorlageAnlegen()`, `fun vorlageLoeschen(id: Long)`
  - `fun vorlageNameGeaendert(v: String)`, `fun vorlageTextGeaendert(v: String)`,
    `fun werteGeaendert(v: String)`
  - `fun serieSettingsAendern(transform: (AppSettings) -> AppSettings)`,
    `fun serieSettingsLive(...)`, `fun serieSettingsCommit()`
  - `fun serieStarten(ueberSdKarte: Boolean)`, `fun serieWeiter()`,
    `fun serieUeberspringen()`, `fun serieAbbrechen()`

- [ ] **Schritt 1: Den Zustand anlegen**

Bei den Importen ergaenzen:

```kotlin
import de.emmpunkt.write.data.BogenBefund
import de.emmpunkt.write.data.SerienZustand
import de.emmpunkt.write.data.Serienlauf
import de.emmpunkt.write.data.TemplateEntity
import de.emmpunkt.write.data.TemplateRepository
import de.emmpunkt.write.data.WerteZeile
import de.emmpunkt.write.data.einsetzen
import de.emmpunkt.write.data.mitVorlage
import de.emmpunkt.write.data.neueVorlage
import de.emmpunkt.write.data.platzhalterIn
import de.emmpunkt.write.data.pruefeBogen
import de.emmpunkt.write.data.rahmenFehler
import de.emmpunkt.write.data.vorlagenFehler
import de.emmpunkt.write.data.werteZeilen
import de.emmpunkt.write.data.zuVorlage
```

Oberhalb der Klasse `PlotterViewModel`, neben `DocumentState` und `MachineUiState`:

```kotlin
/**
 * Alles, was der Serie-Reiter anzeigt.
 *
 * Bewusst EIN Zustand statt eines Dutzends einzelner Fluesse: Die Felder haengen voneinander ab
 * - aus dem Vorlagentext folgen die Spalten, daraus die Zeilen, daraus die Befunde. Getrennte
 * Fluesse koennten fuer einen Moment zueinander unpassende Staende zeigen.
 */
data class SerieUiState(
    val vorlagen: List<TemplateEntity> = emptyList(),
    val aktuelleId: Long = 0L,
    val name: String = "",
    val text: String = "",
    val werte: String = "",
    /** Arbeitszustand der Vorlage - getrennt vom Editor, damit sich beide nicht stoeren. */
    val settings: AppSettings = AppSettings(),
    val spalten: List<String> = emptyList(),
    val zeilen: List<WerteZeile> = emptyList(),
    val befunde: List<BogenBefund> = emptyList(),
    /**
     * Der erste brauchbare Bogen, fertig gesetzt - fuer die Vorschau.
     *
     * Der Satz entsteht hier und nicht im Bildschirm: `PreviewCanvas` zeichnet fertige
     * Strichzuege, und Layout-Arbeit gehoert nicht in eine Compose-Funktion, die bei jeder
     * Neuzeichnung laeuft.
     */
    val vorschau: LaidOutText? = null,
    /** Vorlagenfehler oder unmoeglicher Rahmen - beides sperrt den Start. */
    val fehler: String? = null,
    val lauf: SerienZustand? = null,
) {
    val bogenGesamt: Int get() = zeilen.count { it.fehler == null }

    val startbar: Boolean
        get() = fehler == null &&
            bogenGesamt > 0 &&
            zeilen.all { it.fehler == null } &&
            befunde.all { it.inOrdnung } &&
            lauf == null
}
```

In der Klasse, nach `private val notes = ...`:

```kotlin
    private val templates = TemplateRepository(NoteDatabase.templateDao(app))

    private val _serie = MutableStateFlow(SerieUiState())
    val serie: StateFlow<SerieUiState> = _serie.asStateFlow()

    private var serienlauf: Serienlauf? = null
    private var serienAuftrag: Job? = null
    private var vorlageSpeichern: Job? = null
```

Und im `init`-Block, nach dem Laden der Notiz:

```kotlin
            // Die Vorlagenliste laeuft mit; ohne Vorlage bleibt der Reiter leer, das ist gueltig.
            launch {
                templates.vorlagen.collect { liste -> _serie.update { it.copy(vorlagen = liste) } }
            }
            templates.zuletztBearbeiteteOderNull()?.let { vorlageUebernehmen(it) }
```

`launch` innerhalb des vorhandenen `viewModelScope.launch` — der Sammler laeuft dauerhaft, das
Laden der Notiz davor darf er nicht aufhalten.

- [ ] **Schritt 2: Vorlage laden, rechnen, speichern**

```kotlin
    // ---- Vorlagen ----

    /** Legt eine geladene Vorlage in den Serie-Arbeitszustand. */
    private fun vorlageUebernehmen(v: TemplateEntity) {
        _serie.update {
            it.copy(
                aktuelleId = v.id,
                name = v.name,
                text = v.text,
                werte = v.werte,
                // Basis sind die aktuellen Einstellungen: Verbindung, Vorschuebe und
                // Papier-Offset kommen von dort, Schriftbild und Blatt aus der Vorlage.
                settings = _settings.value.mitVorlage(v),
                lauf = null,
            )
        }
        serieNeuRechnen()
    }

    /**
     * Rechnet Spalten, Zeilen und Befunde neu.
     *
     * Laeuft nach jeder Aenderung an Text, Werten oder Schriftbild. Bei wenigen Dutzend Bogen
     * ist das eine Sache von Millisekunden; erst bei Hunderten waere ein Aufschub noetig.
     */
    private fun serieNeuRechnen() {
        val s = _serie.value
        val spalten = platzhalterIn(s.text)
        val zeilen = werteZeilen(s.werte, spalten)

        val fehler = vorlagenFehler(s.text) ?: rahmenFehler(s.settings)
        val befunde = if (fehler != null) {
            emptyList()
        } else {
            runCatching {
                pruefeBogen(
                    zeilen = zeilen.filter { it.fehler == null },
                    vorlage = s.text,
                    style = s.settings.toTextStyle(),
                    frame = s.settings.toFrame(),
                    font = Fonts.load(s.settings.fontId),
                )
            }.getOrDefault(emptyList())
        }

        // Vorschau des ersten brauchbaren Bogens - so sieht jede Karte aus.
        val vorschau = if (fehler != null) {
            null
        } else {
            zeilen.firstOrNull { it.fehler == null }?.let { erste ->
                runCatching {
                    layoutText(
                        einsetzen(s.text, erste.felder),
                        s.settings.toTextStyle(),
                        s.settings.toFrame(),
                        Fonts.load(s.settings.fontId),
                    )
                }.getOrNull()
            }
        }

        _serie.update {
            it.copy(
                spalten = spalten,
                zeilen = zeilen,
                befunde = befunde,
                vorschau = vorschau,
                fehler = fehler,
            )
        }
    }

    fun vorlageOeffnen(id: Long) {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            vorlageSofortSpeichern()
            templates.laden(id)?.let { vorlageUebernehmen(it) }
        }
    }

    fun vorlageAnlegen() {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            vorlageSofortSpeichern()
            val id = templates.speichern(neueVorlage(_settings.value, System.currentTimeMillis()))
            templates.laden(id)?.let { vorlageUebernehmen(it) }
        }
    }

    fun vorlageLoeschen(id: Long) {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            templates.loeschen(id)
            if (id == _serie.value.aktuelleId) {
                // Auf die naechstbeste umschalten - oder auf den leeren Zustand.
                val ersatz = templates.zuletztBearbeiteteOderNull()
                if (ersatz != null) vorlageUebernehmen(ersatz) else _serie.value =
                    SerieUiState(vorlagen = _serie.value.vorlagen)
            }
        }
    }

    fun vorlageNameGeaendert(v: String) = serieFeldGeaendert { it.copy(name = v) }
    fun vorlageTextGeaendert(v: String) = serieFeldGeaendert { it.copy(text = v) }
    fun werteGeaendert(v: String) = serieFeldGeaendert { it.copy(werte = v) }

    fun serieSettingsAendern(transform: (AppSettings) -> AppSettings) =
        serieFeldGeaendert { it.copy(settings = transform(it.settings)) }

    /** Waehrend eines Reglerzugs: rechnen, aber nicht speichern - wie im Editor. */
    fun serieSettingsLive(transform: (AppSettings) -> AppSettings) {
        _serie.update { it.copy(settings = transform(it.settings)) }
        serieNeuRechnen()
    }

    fun serieSettingsCommit() = vorlageVerzoegertSpeichern()

    private fun serieFeldGeaendert(transform: (SerieUiState) -> SerieUiState) {
        _serie.update(transform)
        serieNeuRechnen()
        vorlageVerzoegertSpeichern()
    }

    private fun vorlageVerzoegertSpeichern() {
        vorlageSpeichern?.cancel()
        vorlageSpeichern = viewModelScope.launch {
            delay(SPEICHER_VERZOEGERUNG_MS)
            vorlageSchreiben()
        }
    }

    private suspend fun vorlageSofortSpeichern() {
        vorlageSpeichern?.cancel()
        vorlageSchreiben()
    }

    private suspend fun vorlageSchreiben() {
        val s = _serie.value
        // id 0 heisst: noch keine Vorlage offen. Speichern legte sonst versehentlich eine an.
        if (s.aktuelleId == 0L) return
        templates.speichern(
            s.settings.zuVorlage(
                id = s.aktuelleId,
                name = s.name,
                text = s.text,
                werte = s.werte,
                jetzt = System.currentTimeMillis(),
            ),
        )
    }
```

- [ ] **Schritt 3: Einen einzelnen Bogen plotten**

Diese Funktion ist es, die der `Serienlauf` hereingereicht bekommt. Sie wartet, bis der Auftrag
durch ist, und liefert Erfolg oder Fehler:

```kotlin
    /**
     * Plottet einen einzelnen Bogen und kehrt erst zurueck, wenn er durch ist.
     *
     * Bewusst dieselbe Kette wie ein einzelner Auftrag: [MachineController] prueft Grenzen,
     * Homing und Idle-Zustand, und der Auftrag endet mit der Rueckfahrt auf den Nullpunkt.
     * Ein zweiter Sendeweg mit eigener Sicherheitslogik waere genau die Abkuerzung, die
     * spaeter ein Blatt kostet.
     */
    private suspend fun serienBogenPlotten(
        text: String,
        s: AppSettings,
        ueberSdKarte: Boolean,
    ): Result<Unit> {
        val c = controller ?: return Result.failure(IllegalStateException("Nicht verbunden."))

        val laid = runCatching {
            layoutText(text, s.toTextStyle(), s.toFrame(), Fonts.load(s.fontId))
        }.getOrElse { return Result.failure(it) }

        val job = laid.toPlotJob(s.toMachineProfile().applying(maschinenwerte))
        if (job.penDownCount == 0) {
            return Result.failure(IllegalStateException("Der Bogen enthält nichts zu zeichnen."))
        }

        val fluss = if (ueberSdKarte) {
            c.plotViaSd(job, laid.strokes, sdTransfer())
        } else {
            c.plot(job, laid.strokes)
        }

        var fehler: String? = null
        fluss.collect { fortschritt ->
            _machine.update {
                it.copy(
                    progress = fortschritt,
                    busy = fortschritt is SendProgress.Started || fortschritt is SendProgress.Running,
                    sdLauf = ueberSdKarte,
                )
            }
            when (fortschritt) {
                is SendProgress.Failed -> fehler = fortschritt.message +
                    if (fortschritt.penLifted) "" else " ACHTUNG: Stift konnte nicht angehoben werden."
                is SendProgress.Aborted -> fehler = "Abgebrochen"
                else -> Unit
            }
        }
        syncController()

        return fehler?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }
```

Noetige Importe, falls noch nicht vorhanden: `de.emmpunkt.write.core.layout.layoutText`.

- [ ] **Schritt 4: Den Satz steuern**

```kotlin
    // ---- Serienlauf ----

    fun serieStarten(ueberSdKarte: Boolean) {
        val s = _serie.value
        if (!s.startbar) return
        if (controller == null) {
            _machine.update { it.copy(message = "Nicht verbunden.") }
            return
        }

        val texte = s.zeilen.filter { it.fehler == null }.map { einsetzen(s.text, it.felder) }
        val lauf = Serienlauf(
            bogen = texte,
            plotteBogen = { _, text -> serienBogenPlotten(text, s.settings, ueberSdKarte) },
        )
        serienlauf = lauf

        viewModelScope.launch {
            lauf.zustand.collect { zustand ->
                _serie.update { it.copy(lauf = zustand) }
                // Zwischen zwei Karten liegt eine Wartezeit, in der das Telefon sonst
                // einschliefe und die Verbindung verlöre - die Sperre gilt fuer den ganzen Satz.
                if (zustand is SerienZustand.Fertig || zustand is SerienZustand.Abgebrochen) {
                    wakeLock.release()
                }
            }
        }

        wakeLock.acquire()
        serieWeiter()
    }

    fun serieWeiter() {
        val lauf = serienlauf ?: return
        serienAuftrag = viewModelScope.launch { lauf.naechsterBogen() }
    }

    fun serieUeberspringen() = serienlauf?.ueberspringen() ?: Unit

    fun serieAbbrechen() {
        serienAuftrag?.cancel()
        serienlauf?.abbrechen()
        serienlauf = null
        wakeLock.release()
        // Der Stift steht womoeglich noch auf dem Papier.
        emergencyStop()
    }

    /** Schliesst einen fertigen oder abgebrochenen Satz ab, damit der Reiter wieder bedienbar ist. */
    fun serieBeenden() {
        serienlauf = null
        _serie.update { it.copy(lauf = null) }
    }
```

- [ ] **Schritt 5: Uebersetzen und pruefen**

Ausfuehren: `./gradlew test assembleDebug`
Erwartet: BUILD SUCCESSFUL, alle Tests gruen.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt
git commit -m "ViewModel: Vorlagen und Serienlauf

Der Serie-Arbeitszustand ist getrennt vom Editor - eine Vorlage zu bearbeiten
darf die offene Notiz nicht veraendern.

Ein Bogen laeuft ueber dieselbe Kette wie ein einzelner Auftrag, samt Grenz-
pruefung und Rueckfahrt auf den Nullpunkt. Die Wachhalte-Sperre gilt fuer den
ganzen Satz, nicht je Bogen: dazwischen liegt eine Wartezeit, in der das Telefon
sonst einschliefe.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 8: Der Reiter „Serie"

**Dateien:**
- Anlegen: `app/src/main/kotlin/de/emmpunkt/write/ui/SerieScreen.kt`
- Aendern: `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt`

**Schnittstellen:**
- Benutzt: `SerieUiState`, `StilLeiste`, `AuswahlFeld`, `PreviewCanvas` (vorhanden),
  die Rueckrufe aus Aufgabe 7.

- [ ] **Schritt 1: Den Bildschirm anlegen**

`app/src/main/kotlin/de/emmpunkt/write/ui/SerieScreen.kt`:

```kotlin
package de.emmpunkt.write.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.SerienZustand
import de.emmpunkt.write.machine.SendProgress

/**
 * Der Serie-Reiter: Vorlage pflegen, Werteliste eintippen, Satz plotten.
 *
 * Die Regler kommen unveraendert aus dem Editor - siehe StilLeiste.kt. Die Vorlage steckt dafuer
 * in einem eigenen AppSettings-Arbeitszustand, getrennt vom Editor.
 */
@Composable
fun SerieScreen(
    serie: SerieUiState,
    machine: MachineUiState,
    onVorlageOeffnen: (Long) -> Unit,
    onVorlageAnlegen: () -> Unit,
    onVorlageLoeschen: (Long) -> Unit,
    onNameChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onWerteChange: (String) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsCommit: () -> Unit,
    onStarten: (Boolean) -> Unit,
    onWeiter: () -> Unit,
    onUeberspringen: () -> Unit,
    onAbbrechen: () -> Unit,
    onBeenden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loeschenBestaetigen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (serie.vorlagen.isEmpty()) {
            Text(
                "Noch keine Vorlage. „+ Neu" legt eine an — zum Beispiel für Platzkarten.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onVorlageAnlegen) { Text("+ Neu") }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuswahlFeld(
                label = "Vorlage",
                selected = serie.vorlagen.firstOrNull { it.id == serie.aktuelleId }?.name.orEmpty(),
                options = serie.vorlagen.map { it.name },
                onSelect = { index -> onVorlageOeffnen(serie.vorlagen[index].id) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onVorlageAnlegen) { Text("+ Neu") }
            TextButton(onClick = { loeschenBestaetigen = true }) { Text("Löschen") }
        }

        OutlinedTextField(
            value = serie.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name der Vorlage") },
            singleLine = true,
        )

        OutlinedTextField(
            value = serie.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("Vorlage") },
            placeholder = { Text("{anrede} {name},") },
            supportingText = { Text("Platzhalter in geschweiften Klammern, z. B. {name}") },
        )

        StilLeiste(
            settings = serie.settings,
            textLeer = serie.zeilen.isEmpty(),
            onChange = onSettingsChange,
            onChangeLive = onSettingsChangeLive,
            onCommit = onSettingsCommit,
            // Einpassen ergaebe je Bogen eine andere Groesse - bei einem Satz Platzkarten
            // stoert das. Der Nutzer stellt die Groesse einmal fuer alle ein.
            onAutoFit = {},
        )

        OutlinedTextField(
            value = serie.werte,
            onValueChange = onWerteChange,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            label = { Text("Werte — eine Zeile je Bogen") },
            supportingText = {
                Text(
                    if (serie.spalten.isEmpty()) {
                        "Erst einen Platzhalter in die Vorlage setzen."
                    } else {
                        "je Zeile: ${serie.spalten.joinToString(";")} — ${serie.bogenGesamt} Bogen"
                    },
                )
            },
        )

        Befund(serie)

        if (serie.lauf == null) {
            SerieStart(serie, machine, onStarten)
        } else {
            SerieLauf(serie.lauf, machine, onWeiter, onUeberspringen, onAbbrechen, onBeenden)
        }

        // Vorschau des ersten Bogens - so sieht jede Karte aus.
        serie.vorschau?.let { laid ->
            Text("Vorschau: Bogen 1", style = MaterialTheme.typography.labelLarge)
            PreviewCanvas(
                strokes = laid.strokes,
                frame = serie.settings.toFrame(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (loeschenBestaetigen) {
        AlertDialog(
            onDismissRequest = { loeschenBestaetigen = false },
            title = { Text("Vorlage löschen?") },
            text = { Text("„${serie.name}“ wird gelöscht, samt Werteliste.") },
            confirmButton = {
                TextButton(onClick = {
                    onVorlageLoeschen(serie.aktuelleId)
                    loeschenBestaetigen = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { loeschenBestaetigen = false }) { Text("Abbrechen") }
            },
        )
    }
}

/** Was der Vorpruefung aufgefallen ist. */
@Composable
private fun Befund(serie: SerieUiState) {
    val zeilenFehler = serie.zeilen.mapNotNull { it.fehler }
    val bogenFehler = serie.befunde.filterNot { it.inOrdnung }

    when {
        serie.fehler != null -> Meldung(serie.fehler)

        zeilenFehler.isNotEmpty() -> Meldung(zeilenFehler.joinToString("\n"))

        bogenFehler.isNotEmpty() -> Meldung(
            bogenFehler.joinToString("\n") { b ->
                val grund = if (b.ueberlauf) "läuft über" else "wird mitten im Wort getrennt"
                "Bogen ${b.index + 1} „${b.bezeichnung}“ $grund."
            } + "\n\nKürzen, Schrift verkleinern oder den Rand verringern.",
        )

        serie.bogenGesamt > 0 -> Text(
            "Alle ${serie.bogenGesamt} Bogen passen.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Meldung(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SerieStart(
    serie: SerieUiState,
    machine: MachineUiState,
    onStarten: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onStarten(false) },
            enabled = serie.startbar && machine.connected && !machine.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Satz plotten") }
        OutlinedButton(
            onClick = { onStarten(true) },
            enabled = serie.startbar && machine.connected && !machine.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Satz über SD") }
    }
}

@Composable
private fun SerieLauf(
    lauf: SerienZustand,
    machine: MachineUiState,
    onWeiter: () -> Unit,
    onUeberspringen: () -> Unit,
    onAbbrechen: () -> Unit,
    onBeenden: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (lauf) {
                is SerienZustand.Bereit -> "Bereit — Bogen ${lauf.naechster + 1} von ${lauf.gesamt}"
                is SerienZustand.Laeuft -> "Bogen ${lauf.index + 1} von ${lauf.gesamt} läuft…"
                is SerienZustand.WartetAufBlatt ->
                    "Bogen ${lauf.fertig} von ${lauf.gesamt} fertig — nächstes Blatt einlegen"
                is SerienZustand.Fehlgeschlagen ->
                    "Bogen ${lauf.index + 1} fehlgeschlagen: ${lauf.meldung}"
                is SerienZustand.Fertig ->
                    "Fertig: ${lauf.geplottet} geplottet" +
                        if (lauf.uebersprungen > 0) ", ${lauf.uebersprungen} übersprungen" else ""
                SerienZustand.Abgebrochen -> "Abgebrochen"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        // Der Fortschritt des laufenden Bogens. Bewusst schlicht: der Zaehler darueber sagt,
        // wo im Satz man steht, und das ist beim Blattwechsel die wichtigere Zahl.
        (machine.progress as? SendProgress.Running)?.let { p ->
            Text(
                "${(p.fraction * 100).toInt()} % dieses Bogens",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when (lauf) {
            is SerienZustand.Fertig, SerienZustand.Abgebrochen ->
                Button(onClick = onBeenden, modifier = Modifier.fillMaxWidth()) { Text("Schließen") }

            is SerienZustand.Laeuft ->
                Button(onClick = onAbbrechen, modifier = Modifier.fillMaxWidth()) {
                    Text("Abbrechen")
                }

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onWeiter, modifier = Modifier.weight(1f)) {
                    Text(if (lauf is SerienZustand.Fehlgeschlagen) "Nochmal" else "Nächster Bogen")
                }
                OutlinedButton(onClick = onUeberspringen) { Text("Überspringen") }
                OutlinedButton(onClick = onAbbrechen) { Text("Stopp") }
            }
        }
    }
}
```

**Hinweis zu `PreviewCanvas`:** Die Signatur ist `PreviewCanvas(strokes: List<Polyline>,
frame: Frame, modifier, showMargins, showTravel)` — sie nimmt **fertige Strichzuege**, keinen
Text. Deshalb liegt der gesetzte Bogen als `vorschau` im Zustand (Aufgabe 7) und nicht im
Bildschirm.

Im Editor gibt es **keine** Hilfsfunktion fuer den Fortschrittstext; die Anzeige steckt dort
inline in `SendeBereich`. Deshalb steht hier eine eigene, schlichte Zeile — nichts wird
importiert, was es nicht gibt.

- [ ] **Schritt 2: Den vierten Reiter einhaengen**

In `MainActivity.kt` den Enum erweitern:

```kotlin
private enum class Reiter(val titel: String, val symbol: ImageVector) {
    EDITOR("Notiz", Icons.Default.Edit),
    SERIE("Serie", Icons.Default.ContentCopy),
    MASCHINE("Maschine", Icons.Default.Tune),
    EINSTELLUNGEN("Einstellungen", Icons.Default.Settings),
}
```

Import ergaenzen: `import androidx.compose.material.icons.filled.ContentCopy`.

Bei den eingesammelten Zustaenden:

```kotlin
        val serie by viewModel.serie.collectAsStateWithLifecycle()
```

Und im `when`:

```kotlin
                Reiter.SERIE -> SerieScreen(
                    serie = serie,
                    machine = machine,
                    onVorlageOeffnen = viewModel::vorlageOeffnen,
                    onVorlageAnlegen = viewModel::vorlageAnlegen,
                    onVorlageLoeschen = viewModel::vorlageLoeschen,
                    onNameChange = viewModel::vorlageNameGeaendert,
                    onTextChange = viewModel::vorlageTextGeaendert,
                    onWerteChange = viewModel::werteGeaendert,
                    onSettingsChange = viewModel::serieSettingsAendern,
                    onSettingsChangeLive = viewModel::serieSettingsLive,
                    onSettingsCommit = viewModel::serieSettingsCommit,
                    onStarten = viewModel::serieStarten,
                    onWeiter = viewModel::serieWeiter,
                    onUeberspringen = viewModel::serieUeberspringen,
                    onAbbrechen = viewModel::serieAbbrechen,
                    onBeenden = viewModel::serieBeenden,
                    modifier = Modifier.padding(innerPadding),
                )
```

- [ ] **Schritt 3: Bauen und aufspielen**

```bash
./gradlew test assembleDebug
adb connect 192.168.2.30:5555
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Schritt 4: Am Geraet pruefen — ohne Maschine**

**Vorher die Einstellungen sichern**, weil Wischgesten per adb schon dreimal Regler verstellt
haben:

```bash
adb -s 192.168.2.30:5555 shell "run-as de.emmpunkt.write cat files/datastore/write_settings.preferences_pb" > /tmp/write_settings.backup.pb
```

Dann durchspielen — **nicht nur bauen und behaupten**:

1. Ohne Vorlage zeigt der Reiter den Hinweis und „+ Neu".
2. „+ Neu" legt eine Vorlage mit Beispieltext an; die Meldung „kein Platzhalter" erscheint NICHT.
3. Vorlage `{anrede} {name},` und Werte `Liebe;Anna` / `Lieber;Bernd` → „Alle 2 Bogen passen",
   die Zeile unter dem Wertefeld nennt `anrede;name`.
4. Eine Zeile auf `Bernd` kuerzen → „Bogen 2 hat 1 Feld, erwartet werden 2 (anrede;name)."
5. Schriftgroesse hochdrehen, bis ein Bogen uebersteht → der Befund nennt Nummer und Namen,
   „Satz plotten" ist gesperrt.
6. **In den Editor wechseln:** Notiz, Schrift und Blatt dort sind unveraendert. Das ist die
   wichtigste Gegenprobe — die beiden Arbeitszustaende duerfen sich nicht stoeren.
7. App neu starten → die Vorlage samt Werteliste ist noch da.

- [ ] **Schritt 5: Am Geraet pruefen — mit Maschine**

**Vorher ankuendigen, dass die Maschine faehrt.** Drei kleine Bogen genuegen; Papier einlegen
oder ohne Stift fahren lassen.

1. Verbinden, Referenzfahrt.
2. Satz mit drei Werten starten → Bogen 1 laeuft, danach „Bogen 1 von 3 fertig".
3. „Nächster Bogen" → Bogen 2.
4. Waehrend Bogen 3 laeuft: „Abbrechen" → Stift hebt, Zustand „Abgebrochen".
5. „Schließen", Satz erneut starten → beginnt wieder bei Bogen 1.

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/SerieScreen.kt \
        app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt
git commit -m "Reiter Serie: Vorlage pflegen und Satz plotten

Die Regler sind dieselben wie im Editor; die Vorlage steckt dafuer in einem
eigenen Arbeitszustand, damit das Bearbeiten die offene Notiz nicht anfasst.

Einpassen ist hier bewusst ohne Funktion: je Bogen eine andere Groesse ergaebe
einen Satz Platzkarten, der nicht zusammenpasst.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Aufgabe 9: Dokumentation nachziehen

- [ ] **Schritt 1: README ergaenzen**

Einen Abschnitt „Serie: viele Karten am Stück" nach „Notizen" einfuegen, der festhaelt:
- Vorlage mit benannten Platzhaltern `{anrede} {name}`, Werteliste mit Semikolon getrennt
- Eine Vorlage traegt Schriftbild **und** Blattformat — der Unterschied zur Notiz, mit
  Begruendung (die Grusskarte bringt ihr Format mit, der Papier-Offset beschreibt den Anschlag)
- Ein Auftrag je Bogen, Pause zum Blattwechsel, Fehlschlag wiederholbar oder ueberspringbar
- Vorpruefung sperrt den Start, solange ein Bogen uebersteht
- Die Testzahl in der Bauanleitung anpassen (`./gradlew test` — neuen Wert ermitteln mit:
  `grep -ho 'tests="[0-9]*"' */build/test-results/*/*.xml | grep -o '[0-9]*' | paste -sd+ | bc`)

- [ ] **Schritt 2: CLAUDE.md ergaenzen**

Unter „Etappe 3" festhalten: Teil 3 steht, was gebaut wurde, und dass Teil 4 (gemischte Stile
je Absatz) als Einziges offen bleibt. Die Begruendung fuer das Semikolon als Trennzeichen
gehoert dazu — sie wird sonst bei der ersten Aenderung wieder in Frage gestellt.

- [ ] **Schritt 3: Commit und Push**

```bash
git add README.md CLAUDE.md
git commit -m "Doku: Vorlagen und Serienlauf

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
git push origin main
```

---

## Selbstpruefung des Plans

**Abdeckung der Spec:**

| Anforderung aus der Spec | Aufgabe |
|---|---|
| Benannte Platzhalter, mehrere erlaubt | 1 |
| Werteliste als Tabelle, Semikolon, falsche Feldzahl gemeldet | 1 |
| Leere Felder erlaubt, doppelte Werte erlaubt | 1 (Test „ein leeres Feld ist erlaubt"; doppelte Werte entstehen ohne Sonderbehandlung) |
| Unbekannter Platzhalter bleibt stehen | 1 |
| Vorlage traegt Schriftbild UND Blatt, nicht den Offset | 2 |
| Ueberlaufpruefung mit vorhandenem `layoutText` | 3 |
| Harte Trennung sperrt ebenfalls | 3 |
| `Frame` wirft — abgefangen | 3 |
| Serienlauf mit hineingereichter Plot-Funktion | 4 |
| Fehlschlag rueckt den Zaehler nicht weiter | 4 (mit Gegenprobe) |
| Ueberspringen, Abbruch, Wiederaufnahme | 4 |
| Leerer Satz | 4 |
| Room, Notizen ueberleben | 5 |
| Keine Regel „mindestens eine Vorlage" | 5 |
| Regler wiederverwenden statt nachbauen | 6 |
| Serie-Zustand getrennt vom Editor | 7 (Code) + 8 (Geraeteprobe Punkt 6) |
| Beide Sendewege | 7, 8 |
| WakeLock ueber den ganzen Satz | 7 |
| Sperre waehrend eines Laufs | 7 |
| Reiter „Serie", Aufbau von oben nach unten | 8 |
| Start gesperrt bei leerer Liste | 7 (`startbar`), 8 |
| Geraeteprobe mit echtem Satz | 8 |

**Bekannte Schwachstellen dieses Plans:**

1. **Aufgabe 7 und 8 haben keine eigenen automatisierten Tests.** ViewModel und Compose sind
   ohne Emulator nicht pruefbar, und das Projekt hat sich bewusst gegen Robolectric
   entschieden. Deshalb stehen in Aufgabe 8 zwei ausdrueckliche Pruefliste — eine ohne und eine
   mit Maschine. Die Logik dahinter ist in den Aufgaben 1 bis 5 vollstaendig abgedeckt.

2. **Die Room-Migration ist nur am Geraet pruefbar.** Sie greift nur auf einer vorhandenen
   Datenbank der Version 1. Deshalb sichert Aufgabe 5, Schritt 7 die Datei vorher — ein
   Fehlschlag darf die Notizen nicht kosten.

3. **`serieNeuRechnen` rechnet bei jedem Tastendruck alle Bogen durch.** Bei einigen Dutzend
   ist das belanglos. Wer eines Tages 500 Platzkarten schreibt, wird es merken — dann gehoert
   dort derselbe Aufschub hin, den `persist()` schon benutzt. Vorher waere es geraten statt
   gemessen.
