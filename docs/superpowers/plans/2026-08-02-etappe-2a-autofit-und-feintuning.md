# Etappe 2a: Auto-Fit und Feintuning-Regler – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Knopf setzt die groesste Schriftgroesse, bei der die Notiz sauber in den Rahmen
passt, und vier neue Regler im Editor stellen Laufweite, Wortabstand, Zeilenabstand und Neigung
mit sofortiger Vorschau ein.

**Architecture:** Die Suche nach der passenden Groesse ist eine reine Funktion `fitSize` im
Modul `core`, die `layoutText` wiederholt aufruft – ohne Android, damit auf dem PC testbar. Die
Oberflaeche bekommt die Regler in die bestehende `StilLeiste` des Editors; waehrend eines
Reglerzugs aktualisiert das ViewModel nur den Zustand, gespeichert wird erst beim Loslassen.

**Tech Stack:** Kotlin 2.2.20, Gradle 8.13, AGP 8.12.3, JDK 21, Jetpack Compose Material 3,
DataStore Preferences, `kotlin.test` mit JUnit.

**Spec:** `docs/superpowers/specs/2026-08-02-etappe-2a-autofit-und-feintuning-design.md`

## Global Constraints

- **Sprache im Code:** Bezeichner und Kommentare auf Deutsch ohne Umlaute (`Groesse`, nicht
  `Größe`), wie im gesamten Bestand. Nutzersichtbare Texte in der Oberflaeche **mit** Umlauten.
- **Kommentare begruenden, nicht beschreiben.** Der Bestand erklaert das *Warum*; ein Kommentar,
  der nur wiederholt, was der Code sagt, ist im Projektstil ein Fehler.
- `core` und `machine` bleiben **frei von Android-Abhaengigkeiten** – sie sind reine
  Kotlin/JVM-Module.
- Vorgabewerte, die im Code mehrfach vorkommen wuerden, stehen genau einmal in
  `AppSettings` bzw. `TextStyle`.
- Alle Tests laufen **ohne Geraet, ohne Emulator und ohne Netz**: `./gradlew test`.
- Nach jeder Task ist der Baum uebersetzbar und die Tests gruen.
- Commit-Nachrichten auf Deutsch, ohne Umlaute in der Betreffzeile.

## Dateien

| Datei | Verantwortung | Task |
|---|---|---|
| `core/src/main/kotlin/de/emmpunkt/write/core/layout/AutoFit.kt` | neu: `FitResult`, `fitSize` | 1 |
| `core/src/test/kotlin/de/emmpunkt/write/core/layout/AutoFitTest.kt` | neu: Tests dazu | 1 |
| `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt` | `updateSettingsLive`, `commitSettings`, `autoFit` | 2 |
| `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt` | neue Rueckrufe durchreichen | 3 |
| `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt` | Regler, Einpassen-Knopf, Slider-Verdrahtung | 3 |
| `core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt` | Musterbilder fuers Feintuning | 4 |
| `README.md`, `CLAUDE.md` | Stand fortschreiben | 5 |

---

### Task 1: `fitSize` im core-Modul

Die eigentliche Suche. Reines Kotlin, vollstaendig testbar.

**Files:**
- Create: `core/src/main/kotlin/de/emmpunkt/write/core/layout/AutoFit.kt`
- Test: `core/src/test/kotlin/de/emmpunkt/write/core/layout/AutoFitTest.kt`

**Interfaces:**
- Consumes: `layoutText(text, style, frame, font): LaidOutText` mit den Feldern `overflow: Boolean`
  und `overlongWords: Set<String>`; `TextStyle` (data class, `copy(sizeMm = …)`); `Frame`;
  `StrokeFont`; `Fonts.load(id): StrokeFont`.
- Produces:
  - `data class FitResult(val sizeMm: Float, val fits: Boolean)`
  - `fun fitSize(text: String, style: TextStyle, frame: Frame, font: StrokeFont, minMm: Float = 2f, maxMm: Float = 25f, stepMm: Float = 0.1f): FitResult`

- [ ] **Step 1: Den Test schreiben**

Neue Datei `core/src/test/kotlin/de/emmpunkt/write/core/layout/AutoFitTest.kt`:

```kotlin
package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.Fonts
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prueft die Suche nach der groessten passenden Schriftgroesse.
 *
 * Der wichtigste Test ist [`Ergebnis ist maximal`]: ohne ihn bestuende auch eine Funktion,
 * die stur die Mindestgroesse zurueckgibt, alle uebrigen Pruefungen.
 */
class AutoFitTest {

    private val font = Fonts.load("script-simplex")
    private val stil = TextStyle("script-simplex", sizeMm = 7f)
    private val a6quer = Frame(widthMm = 148f, heightMm = 105f, margins = Margins.all(8f))

    private val langerText = """
        Einkaufsliste fuer Samstag: Milch, Brot, Kaffee, Butter und Eier.
        Danach zur Post und das Paket abholen, es liegt seit Dienstag dort.
        Abends noch die Rechnung schreiben und den Brief einwerfen.
    """.trimIndent()

    /** Passt der Text bei dieser Groesse in den Rahmen, ohne hart getrennt zu werden? */
    private fun passt(text: String, sizeMm: Float, frame: Frame = a6quer): Boolean {
        val laid = layoutText(text, stil.copy(sizeMm = sizeMm), frame, font)
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    @Test
    fun `verkleinert einen ueberlaufenden Text so weit, dass er passt`() {
        assertFalse(passt(langerText, 7f), "Der Testtext muss bei 7 mm ueberlaufen")

        val ergebnis = fitSize(langerText, stil, a6quer, font)

        assertTrue(ergebnis.fits)
        assertTrue(ergebnis.sizeMm < 7f, "Erwartet kleiner als 7 mm, war ${ergebnis.sizeMm}")
        assertTrue(passt(langerText, ergebnis.sizeMm), "Das Ergebnis passt nicht")
    }

    @Test
    fun `Ergebnis ist maximal`() {
        val ergebnis = fitSize(langerText, stil, a6quer, font)

        assertTrue(passt(langerText, ergebnis.sizeMm))
        assertFalse(
            passt(langerText, ergebnis.sizeMm + 0.1f),
            "Eine Stufe groesser passt auch noch - dann war das Ergebnis nicht maximal",
        )
    }

    @Test
    fun `Ergebnis liegt auf dem Zehntelmillimeter-Raster des Reglers`() {
        val zehntel = fitSize(langerText, stil, a6quer, font).sizeMm * 10f

        assertTrue(
            abs(zehntel - zehntel.roundToInt()) < 0.01f,
            "Groesse liegt nicht auf dem Raster: ${zehntel / 10f}",
        )
    }

    @Test
    fun `kurzer Text bekommt die Obergrenze`() {
        val ergebnis = fitSize("Hallo", stil, a6quer, font, maxMm = 12f)

        assertTrue(ergebnis.fits)
        assertEquals(12f, ergebnis.sizeMm, 0.001f)
    }

    @Test
    fun `ein zu langes Wort auf schmalem Blatt gilt als nicht einpassbar`() {
        val schmal = Frame(widthMm = 40f, heightMm = 105f, margins = Margins.all(8f))

        val ergebnis = fitSize("Donaudampfschifffahrtsgesellschaftskapitaen", stil, schmal, font)

        assertFalse(ergebnis.fits, "Ohne Trennstelle darf kein Erfolg gemeldet werden")
        assertEquals(2f, ergebnis.sizeMm, 0.001f, "Bei Misserfolg wird die Untergrenze geliefert")
    }

    @Test
    fun `leerer Text stuerzt nicht ab`() {
        assertEquals(25f, fitSize("", stil, a6quer, font).sizeMm, 0.001f)
        assertEquals(25f, fitSize("   \n  ", stil, a6quer, font).sizeMm, 0.001f)
    }

    @Test
    fun `passt und ist maximal fuer verschiedene Texte und Rahmen`() {
        val faelle = listOf(
            langerText to a6quer,
            "Kurz notiert" to Frame(105f, 74f, Margins.all(5f)),
            "Mehr Text als in eine Zeile passt, deutlich mehr sogar" to Frame(74f, 105f, Margins.all(6f)),
            langerText to Frame(105f, 148f, Margins.all(12f)),
        )

        faelle.forEach { (text, rahmen) ->
            val ergebnis = fitSize(text, stil, rahmen, font)
            assertTrue(ergebnis.fits, "'$text' wurde nicht eingepasst")
            assertTrue(passt(text, ergebnis.sizeMm, rahmen), "Ergebnis passt nicht: '$text'")
            assertFalse(
                passt(text, ergebnis.sizeMm + 0.1f, rahmen),
                "Nicht maximal bei '$text': ${ergebnis.sizeMm} mm",
            )
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestaetigen**

Run: `./gradlew :core:test --tests '*AutoFitTest*'`
Expected: FAIL – Uebersetzungsfehler, `fitSize` ist unbekannt.

- [ ] **Step 3: `fitSize` schreiben**

Neue Datei `core/src/main/kotlin/de/emmpunkt/write/core/layout/AutoFit.kt`:

```kotlin
package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.StrokeFont
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ergebnis der Groessensuche.
 *
 * @param sizeMm die gefundene Versalhoehe; bei [fits] = false die unveraenderte Untergrenze.
 * @param fits ob ueberhaupt eine passende Groesse gefunden wurde. Bei false darf die App die
 *   Groesse NICHT setzen, sondern muss es melden - sonst schriebe sie in dem Glauben los,
 *   eingepasst zu haben.
 */
data class FitResult(val sizeMm: Float, val fits: Boolean)

/**
 * Sucht die groesste Schriftgroesse, bei der [text] sauber in den [frame] passt.
 *
 * "Sauber" heisst: kein Ueberlauf nach unten UND kein Wort, das mitten im Wort getrennt werden
 * muss. Harte Trennungen mahnt die App an anderer Stelle als Fehler an - eine Groesse zu
 * liefern, bei der die Warnung stehen bleibt, waere kein Einpassen.
 *
 * [style].sizeMm wird ersetzt; alle uebrigen Felder bleiben wirksam, damit die Suche mit
 * derselben Laufweite und demselben Zeilenabstand rechnet, die spaeter gefahren werden.
 *
 * Gesucht wird auf dem Raster [stepMm] - demselben, das der Regler in der App anbietet. Sonst
 * naennte die App eine Groesse, die sich von Hand gar nicht mehr einstellen laesst.
 */
fun fitSize(
    text: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
    minMm: Float = 2f,
    maxMm: Float = 25f,
    stepMm: Float = 0.1f,
): FitResult {
    require(minMm > 0f) { "Mindestgroesse muss positiv sein" }
    require(maxMm >= minMm) { "Obergrenze liegt unter der Untergrenze" }
    require(stepMm > 0f) { "Schrittweite muss positiv sein" }

    if (text.isBlank()) return FitResult(maxMm, fits = true)

    fun groesse(stufe: Int): Float = (stufe.toDouble() * stepMm.toDouble()).toFloat()

    fun passt(stufe: Int): Boolean {
        val laid = layoutText(text, style.copy(sizeMm = groesse(stufe)), frame, font)
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    val unterste = ceil(minMm / stepMm - RASTER_TOLERANZ).toInt()
    val oberste = floor(maxMm / stepMm + RASTER_TOLERANZ).toInt()

    if (passt(oberste)) return FitResult(maxMm, fits = true)
    if (!passt(unterste)) return FitResult(minMm, fits = false)

    // Invariante: [unten] passt geprueft, [oben] passt geprueft nicht. Sie traegt auch dann,
    // wenn "kleiner passt eher" einmal nicht gilt - der Zeilenumbruch ist eine Treppe, kein
    // stetiger Verlauf.
    var unten = unterste
    var oben = oberste
    while (oben - unten > 1) {
        val mitte = unten + (oben - unten) / 2
        if (passt(mitte)) unten = mitte else oben = mitte
    }

    // Am Ende ist oben = unten + 1. Dieselbe Invariante liefert damit beides, worauf es
    // ankommt: das Ergebnis passt, und die naechstgroessere Stufe passt nicht.
    //
    // Nicht garantiert ist, dass es jenseits von [oben] keine noch groessere passende Stufe
    // gibt - bei einer Treppenfunktion waere dafuer nur die vollstaendige Suche sicher, und
    // die kostet das Zwanzigfache fuer einen Fall, der in echten Notizen nicht vorkommt.
    return FitResult(groesse(unten), fits = true)
}

/** Fuer den Fall, dass minMm/stepMm rechnerisch knapp neben einer ganzen Stufe landet. */
private const val RASTER_TOLERANZ = 1e-4
```

- [ ] **Step 4: Tests laufen lassen, gruen bestaetigen**

Run: `./gradlew :core:test --tests '*AutoFitTest*'`
Expected: PASS, alle sieben Testfaelle.

Schlaegt `verkleinert einen ueberlaufenden Text` mit „Der Testtext muss bei 7 mm ueberlaufen"
fehl, ist der Testtext fuer A6 quer zu kurz geraten – dann eine weitere Zeile anhaengen, nicht
die Behauptung abschwaechen.

- [ ] **Step 5: Alle Tests laufen lassen**

Run: `./gradlew test`
Expected: PASS – die 86 bestehenden Tests bleiben unberuehrt, `fitSize` ist reine Ergaenzung.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/de/emmpunkt/write/core/layout/AutoFit.kt \
        core/src/test/kotlin/de/emmpunkt/write/core/layout/AutoFitTest.kt
git commit -m "core: fitSize sucht die groesste passende Schriftgroesse"
```

---

### Task 2: ViewModel – Live-Zustand, Speichern beim Loslassen, Einpassen

**Files:**
- Modify: `app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt`

**Interfaces:**
- Consumes: `fitSize(...): FitResult` aus Task 1; bestehende private Methoden `recompute()` und
  `persist()`; `_settings: MutableStateFlow<AppSettings>`; `_machine.update { it.copy(message = …) }`.
- Produces:
  - `fun updateSettingsLive(transform: (AppSettings) -> AppSettings)`
  - `fun commitSettings()`
  - `fun autoFit()`

Kein automatisierter Test: `PlotterViewModel` ist ein `AndroidViewModel` und braeuchte
Robolectric oder Instrumentierung, die es im Projekt nicht gibt. Die Pruefung von Hand steht in
Task 3, Schritt 5.

- [ ] **Step 1: Die beiden Wege trennen**

In `PlotterViewModel.kt` die bestehende Methode `updateSettings` (ab Zeile 88) so ergaenzen,
dass daneben die beiden neuen stehen:

```kotlin
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        recompute()
        persist()
    }

    /**
     * Waehrend eines Reglerzugs: Zustand und Vorschau nachfuehren, aber nichts speichern.
     *
     * Ein Zug loest dutzende Wertaenderungen aus. Wuerde jede davon in DataStore geschrieben,
     * schriebe die App waehrend eines einzigen Fingerstrichs dutzende Male auf den Speicher -
     * und ein abgebrochener Zug hinterliesse trotzdem einen gespeicherten Wert.
     */
    fun updateSettingsLive(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        recompute()
    }

    /** Beim Loslassen: den erreichten Wert einmal sichern. */
    fun commitSettings() = persist()
```

- [ ] **Step 2: `autoFit` ergaenzen**

Direkt unter `commitSettings` einfuegen:

```kotlin
    /**
     * Setzt die groesste Schriftgroesse, bei der die Notiz sauber in den Rahmen passt.
     *
     * Findet sich keine, bleibt die Groesse stehen und die App sagt es. Stillschweigend die
     * Untergrenze zu setzen waere schlimmer als nichts zu tun: der Text liefe weiter ueber,
     * nur in unlesbarer Groesse.
     */
    fun autoFit() {
        val text = _text.value
        if (text.isBlank()) return

        val s = _settings.value
        val ergebnis = runCatching {
            fitSize(text, s.toTextStyle(), s.toFrame(), Fonts.load(s.fontId))
        }.getOrElse { e ->
            _machine.update { it.copy(message = "Einpassen nicht moeglich: ${e.message}") }
            return
        }

        if (!ergebnis.fits) {
            val minimum = String.format(Locale.GERMANY, "%.1f", ergebnis.sizeMm)
            _machine.update {
                it.copy(
                    message = "Passt auch bei $minimum mm nicht – Rand verkleinern, " +
                        "Text kürzen oder einen Bindestrich setzen.",
                )
            }
            return
        }

        updateSettings { it.copy(sizeMm = ergebnis.sizeMm) }
    }
```

- [ ] **Step 3: Einfuhren ergaenzen**

Zu den bestehenden `import`-Zeilen in `PlotterViewModel.kt` hinzufuegen:

```kotlin
import de.emmpunkt.write.core.layout.fitSize
import java.util.Locale
```

`Fonts` ist bereits eingefuehrt (Zeile 6).

- [ ] **Step 4: Uebersetzen**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/PlotterViewModel.kt
git commit -m "app: Reglerwerte erst beim Loslassen speichern, autoFit ergaenzt"
```

---

### Task 3: Editor – vier Regler, Einpassen-Knopf, Verdrahtung

**Files:**
- Modify: `app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt` (Signatur, `StilLeiste` ab Zeile 174)
- Modify: `app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt` (Aufruf ab Zeile 104)

**Interfaces:**
- Consumes: `updateSettingsLive`, `commitSettings`, `autoFit` aus Task 2; `AppSettings` mit
  `sizeMm`, `letterSpacing`, `wordSpacing`, `lineSpacing`, `slantDeg`; das vorhandene
  `Float.fmt()` in derselben Datei (Zeile 330).
- Produces: erweiterte `EditorScreen`-Signatur mit `onSettingsChangeLive`, `onSettingsCommit`,
  `onAutoFit`.

- [ ] **Step 1: Signatur von `EditorScreen` erweitern**

In `EditorScreen.kt` die Parameterliste (Zeile 59-69) ersetzen durch:

```kotlin
@Composable
fun EditorScreen(
    text: String,
    settings: AppSettings,
    document: DocumentState,
    machine: MachineUiState,
    onTextChange: (String) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsCommit: () -> Unit,
    onAutoFit: () -> Unit,
    onPlot: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Und den Aufruf der Leiste (Zeile 93) ersetzen durch:

```kotlin
        StilLeiste(
            settings = settings,
            textLeer = text.isBlank(),
            onChange = onSettingsChange,
            onChangeLive = onSettingsChangeLive,
            onCommit = onSettingsCommit,
            onAutoFit = onAutoFit,
        )
```

- [ ] **Step 2: `StilLeiste` ersetzen**

Die komplette bisherige `StilLeiste` (Zeile 173-235) durch diese Fassung ersetzen. Schrift-,
Blatt- und Ausrichtungswahl bleiben unveraendert; neu sind der Einpassen-Knopf, der
Aufklappbereich und die Verdrahtung der Regler auf live/commit:

```kotlin
@Composable
private fun StilLeiste(
    settings: AppSettings,
    textLeer: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onCommit: () -> Unit,
    onAutoFit: () -> Unit,
) {
    // Reiner Bildschirmzustand: welche Regler zuletzt offen standen, muss nichts ueberdauern.
    var feintuningOffen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuswahlFeld(
                label = "Schrift",
                selected = Fonts.entry(settings.fontId).displayName,
                options = Fonts.available.map { it.displayName },
                onSelect = { index ->
                    onChange { it.copy(fontId = Fonts.available[index].id) }
                },
                modifier = Modifier.weight(1.15f),
            )
            AuswahlFeld(
                label = "Blatt",
                selected = PaperPresets.all.firstOrNull {
                    it.widthMm == settings.paperWidthMm && it.heightMm == settings.paperHeightMm
                }?.name ?: "${settings.paperWidthMm.fmt()}×${settings.paperHeightMm.fmt()}",
                options = PaperPresets.all.map { it.name },
                onSelect = { index ->
                    val p = PaperPresets.all[index]
                    onChange { it.copy(paperWidthMm = p.widthMm, paperHeightMm = p.heightMm) }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Größe", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.sizeMm,
                onValueChange = { v -> onChangeLive { it.copy(sizeMm = auf(v, 0.1f)) } },
                onValueChangeFinished = onCommit,
                valueRange = 3f..25f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${settings.sizeMm.fmt()} mm", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAutoFit, enabled = !textLeer) { Text("Einpassen") }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val ausrichtungen = listOf(
                Align.LEFT to Icons.Default.FormatAlignLeft,
                Align.CENTER to Icons.Default.FormatAlignCenter,
                Align.RIGHT to Icons.Default.FormatAlignRight,
            )
            ausrichtungen.forEachIndexed { index, (align, icon) ->
                SegmentedButton(
                    selected = settings.align == align,
                    onClick = { onChange { it.copy(align = align) } },
                    shape = SegmentedButtonDefaults.itemShape(index, ausrichtungen.size),
                ) {
                    Icon(icon, contentDescription = align.name)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { feintuningOffen = !feintuningOffen }) {
                Text(if (feintuningOffen) "Schriftbild ausblenden" else "Schriftbild…")
            }
            if (feintuningOffen) {
                TextButton(
                    onClick = {
                        val v = AppSettings()
                        onChange {
                            it.copy(
                                letterSpacing = v.letterSpacing,
                                wordSpacing = v.wordSpacing,
                                lineSpacing = v.lineSpacing,
                                slantDeg = v.slantDeg,
                            )
                        }
                    },
                ) {
                    Text("Zurücksetzen")
                }
            }
        }

        if (feintuningOffen) {
            StilRegler(
                label = "Laufweite",
                wert = settings.letterSpacing,
                bereich = -0.2f..0.5f,
                schritt = 0.01f,
                anzeige = { "%+d %%".format(Locale.GERMANY, (it * 100).roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(letterSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Wortabstand",
                wert = settings.wordSpacing,
                bereich = -0.6f..1.0f,
                schritt = 0.01f,
                anzeige = { "%+d %%".format(Locale.GERMANY, (it * 100).roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(wordSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Zeilenabstand",
                wert = settings.lineSpacing,
                bereich = 0.8f..2.0f,
                schritt = 0.05f,
                anzeige = { String.format(Locale.GERMANY, "%.2f", it) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(lineSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Neigung",
                wert = settings.slantDeg,
                bereich = -20f..20f,
                schritt = 1f,
                anzeige = { "%+d°".format(Locale.GERMANY, it.roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(slantDeg = v) } },
                onCommit = onCommit,
            )
        }
    }
}

/**
 * Ein beschrifteter Regler mit Wertanzeige.
 *
 * Der Wert wandert waehrend des Zugs nur durch [onChangeLive]; gespeichert wird erst in
 * [onCommit] beim Loslassen.
 */
@Composable
private fun StilRegler(
    label: String,
    wert: Float,
    bereich: ClosedFloatingPointRange<Float>,
    schritt: Float,
    anzeige: (Float) -> String,
    onChangeLive: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(104.dp),
        )
        Slider(
            value = wert,
            onValueChange = { v -> onChangeLive(auf(v, schritt)) },
            onValueChangeFinished = onCommit,
            valueRange = bereich,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            anzeige(wert),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
        )
    }
}

/**
 * Rundet auf ein Vielfaches von [schritt].
 *
 * Ohne das lieferte der Regler beliebige Zwischenwerte - die Anzeige zappelte, und
 * „Einpassen" naennte eine Groesse, die sich von Hand nicht wieder treffen laesst.
 */
private fun auf(wert: Float, schritt: Float): Float =
    ((wert / schritt).roundToInt() * schritt.toDouble()).toFloat()
```

- [ ] **Step 3: Fehlende Einfuhren in `EditorScreen.kt` ergaenzen**

```kotlin
import androidx.compose.foundation.layout.width
```

`Locale`, `roundToInt`, `remember`, `mutableStateOf`, `Slider`, `TextButton`, `Text` und
`AppSettings` sind bereits eingefuehrt.

- [ ] **Step 4: `MainActivity` verdrahten**

Den `EditorScreen`-Aufruf (Zeile 104-114) ersetzen durch:

```kotlin
                Reiter.EDITOR -> EditorScreen(
                    text = text,
                    settings = settings,
                    document = document,
                    machine = machine,
                    onTextChange = viewModel::onTextChanged,
                    onSettingsChange = viewModel::updateSettings,
                    onSettingsChangeLive = viewModel::updateSettingsLive,
                    onSettingsCommit = viewModel::commitSettings,
                    onAutoFit = viewModel::autoFit,
                    onPlot = viewModel::plot,
                    onStop = viewModel::cancelPlot,
                    modifier = Modifier.padding(innerPadding),
                )
```

- [ ] **Step 5: Bauen und von Hand pruefen**

```bash
./gradlew assembleDebug
adb tcpip 5555 && adb connect 192.168.2.30:5555
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Am Geraet der Reihe nach:

1. Einen Text eingeben, der ueberlaeuft (Warnung „Text ist höher als das Blatt" erscheint).
   „Einpassen" druecken → Warnung verschwindet, die Vorschau fuellt den Rahmen.
2. „Schriftbild…" aufklappen, an jedem der vier Regler ziehen → die Vorschau folgt fluessig.
3. „Zurücksetzen" → die vier Werte stehen wieder auf 0 / −30 % / 1,15 / 0°, die Groesse bleibt.
4. **Persistenz:** einen Regler auf einen auffaelligen Wert ziehen, loslassen, die App ueber den
   Aufgabenschalter beenden, neu starten → der Wert steht noch da.
5. Bei leerem Textfeld ist „Einpassen" ausgegraut.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/emmpunkt/write/ui/EditorScreen.kt \
        app/src/main/kotlin/de/emmpunkt/write/MainActivity.kt
git commit -m "app: Feintuning-Regler und Einpassen-Knopf im Editor"
```

---

### Task 4: Musterbilder fuers Feintuning

Damit das Schriftbild am Bildschirm beurteilbar ist, bevor Papier und Maschinenzeit draufgehen.

**Files:**
- Modify: `core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt`

**Interfaces:**
- Consumes: die vorhandene private Methode `render(name, text, style, frame, showTravel)` in
  derselben Datei; `fitSize` aus Task 1.

- [ ] **Step 1: Musterbilder ergaenzen**

In `PreviewSamplesTest` den bestehenden Block `// Neigung und Laufweite.` (Zeile 100-102)
ersetzen durch:

```kotlin
        // Neigung und Laufweite.
        render("07-feintuning", "Kursiv gestellt", TextStyle("script-simplex", sizeMm = 10f, slantDeg = 12f, letterSpacing = 0.15f),
            Frame(120f, 30f, Margins.all(5f)))

        // Die vier Feintuning-Regler in ihren Randlagen - so laesst sich am Bildschirm
        // entscheiden, ob die Bereiche der Regler brauchbar gewaehlt sind.
        val muster = "Handschrift wird hier\nueber zwei Zeilen gesetzt"
        val reglerRahmen = Frame(120f, 55f, Margins.all(5f))
        val varianten = listOf(
            "10-laufweite-eng" to TextStyle("script-simplex", sizeMm = 7f, letterSpacing = -0.2f),
            "11-laufweite-weit" to TextStyle("script-simplex", sizeMm = 7f, letterSpacing = 0.5f),
            "12-wortabstand-eng" to TextStyle("script-simplex", sizeMm = 7f, wordSpacing = -0.6f),
            "13-wortabstand-weit" to TextStyle("script-simplex", sizeMm = 7f, wordSpacing = 1.0f),
            "14-zeilen-eng" to TextStyle("script-simplex", sizeMm = 7f, lineSpacing = 0.8f),
            "15-zeilen-weit" to TextStyle("script-simplex", sizeMm = 7f, lineSpacing = 2.0f),
            "16-neigung-links" to TextStyle("script-simplex", sizeMm = 7f, slantDeg = -20f),
            "17-neigung-rechts" to TextStyle("script-simplex", sizeMm = 7f, slantDeg = 20f),
        )
        varianten.forEach { (name, stil) -> render(name, muster, stil, reglerRahmen) }
```

- [ ] **Step 2: Ein Bild vom Einpassen ergaenzen**

Direkt darunter einfuegen:

```kotlin
        // Was "Einpassen" aus einem zu langen Text macht.
        val zuViel = "Einkaufsliste fuer Samstag: Milch, Brot, Kaffee, Butter und Eier. " +
            "Danach zur Post und das Paket abholen, es liegt seit Dienstag dort."
        val a6quer = Frame(148f, 105f, Margins.all(8f))
        val eingepasst = fitSize(zuViel, TextStyle("script-simplex"), a6quer, Fonts.load("script-simplex"))
        render("18-eingepasst", zuViel, TextStyle("script-simplex", sizeMm = eingepasst.sizeMm), a6quer)
        println("Eingepasst auf ${eingepasst.sizeMm} mm (passt=${eingepasst.fits})")
```

Die Zusicherung am Ende der Methode auf die neue Zahl anheben:

```kotlin
        assertTrue(erzeugt >= 19, "Es wurden nur $erzeugt Musterbilder erzeugt")
```

- [ ] **Step 3: Einfuhr ergaenzen**

```kotlin
import de.emmpunkt.write.core.layout.fitSize
```

- [ ] **Step 4: Musterbilder erzeugen**

Run: `./gradlew :core:test --tests '*PreviewSamplesTest*'`
Expected: PASS, und `core/build/preview/` enthaelt mindestens 19 PNG-Dateien, darunter
`10-laufweite-eng.png` bis `18-eingepasst.png`.

Die Bilder ansehen: `xdg-open core/build/preview/18-eingepasst.png`

- [ ] **Step 5: Commit**

```bash
git add core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt
git commit -m "core: Musterbilder fuer Feintuning und Einpassen"
```

---

### Task 5: Stand fortschreiben

**Files:**
- Modify: `README.md` (Abschnitt „Stand", Zeile 166-188)
- Modify: `CLAUDE.md` (Abschnitte „Stand" und „Etappe 2")

- [ ] **Step 1: `README.md` ergaenzen**

Vor dem Abschnitt „Warum nur Telnet" einen neuen Abschnitt einfuegen:

```markdown
## Schriftbild einstellen

Im Editor stellt „Schriftbild…" vier Regler auf: Laufweite, Wortabstand, Zeilenabstand und
Neigung. Die Vorschau folgt sofort; gespeichert wird der Wert erst, wenn der Finger den Regler
loslaesst – sonst schriebe ein einziger Zug dutzende Male auf den Speicher.

„Einpassen" neben dem Groessenregler sucht die groesste Schriftgroesse, bei der der Text in den
Rahmen passt, **ohne** dass ein Wort hart getrennt werden muss. Gesucht wird durch
Intervallhalbierung auf dem Zehntelmillimeter-Raster des Reglers; findet sich keine passende
Groesse, bleibt die eingestellte stehen und die App sagt es, statt eine unlesbare zu setzen.
```

Im Abschnitt „Stand" die Zeile zu Etappe 2 ersetzen durch:

```markdown
- **Etappe 2a steht:** Einpassen und die vier Feintuning-Regler.
- **Etappe 2b:** SVG-Script-Fonts (EMS Allure, EMS Casual Script), danach Rahmen und Linien.
```

- [ ] **Step 2: `CLAUDE.md` fortschreiben**

Den Abschnitt „Etappe 2 – „Schön" (als Nächstes)" ersetzen durch:

```markdown
## Etappe 2a abgeschlossen (2026-08-02)

Auto-Fit (`fitSize` im core) und die vier Feintuning-Regler im Editor. Reglerwerte gehen
waehrend des Zugs nur in den Zustand, gespeichert wird beim Loslassen (`updateSettingsLive` /
`commitSettings` im ViewModel).

## Etappe 2b – „Schön" (als Nächstes)

1. **SVG-Script-Fonts** (EMS Allure, EMS Casual Script, SIL OFL). Hershey-Script verbindet die
   Buchstaben nur teilweise; die EMS-Schriften sind echte verbundene Kursiven. Erfordert einen
   zweiten Font-Parser hinter der bestehenden `StrokeFont`-Schnittstelle – der Rest der App
   merkt davon nichts.
2. **Dekor**: Rahmen, Trennlinien, Unterstreichungen, Aufzählungspunkte.
```

- [ ] **Step 3: Vollstaendiger Testlauf**

Run: `./gradlew test`
Expected: PASS – die bestehenden 86 Tests plus die sieben neuen aus Task 1.

- [ ] **Step 4: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Doku: Etappe 2a beschrieben, Etappe 2b abgegrenzt"
```

---

## Abnahme

- [ ] `./gradlew test` gruen, einschliesslich `AutoFitTest`.
- [ ] Ein zu langer Text plus „Einpassen": Ueberlauf-Warnung und rote Markierungen verschwinden.
- [ ] Die vier Regler veraendern die Vorschau sichtbar und fluessig.
- [ ] Ein Reglerwert ueberlebt den Neustart der App.
- [ ] „Einpassen" ist bei leerem Text ausgegraut.
- [ ] Ein Probeblatt an der echten Maschine: die eingepasste Groesse bleibt auf dem Papier im
      Rahmen. **Vorher ankuendigen** – dieser Schritt bewegt die Maschine.
