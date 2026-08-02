# Etappe 2b: Einlinige SVG-Schreibschriften – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vier echte verbundene Schreibschriften im SVG-Font-Format stehen in der App zur
Auswahl und beheben den gemessenen Buchstabenversatz der Hershey-Schreibschrift.

**Architecture:** Ein zweiter Parser `SvgFont` liefert dieselbe `StrokeFont`-Schnittstelle wie
`HersheyFont`; der Rest der App merkt vom Formatwechsel nichts. Die Ableitung der
Schriftmetriken wandert vorher in eine gemeinsame Datei, damit eine eingestellte Groesse in
allen sieben Schriften dasselbe bedeutet.

**Tech Stack:** Kotlin 2.2.20, Gradle 8.13, JDK 21, `kotlin.test` mit JUnit. Keine neue
Abhaengigkeit – `core` bleibt frei von Fremdbibliotheken.

**Spec:** `docs/superpowers/specs/2026-08-02-etappe-2b-svg-schreibschriften-design.md`

## Global Constraints

- Bezeichner und Kommentare im Code auf **Deutsch ohne Umlaute** (`Groesse`, nicht `Größe`).
  **Nutzersichtbare Texte in der Oberflaeche und der Fliesstext in `README.md`/`CLAUDE.md`
  dagegen mit Umlauten.**
- **Kommentare begruenden, nicht beschreiben.** Ein Kommentar, der wiederholt, was der Code
  sagt, ist im Projektstil ein Fehler.
- `core` und `machine` bleiben **frei von Android-Abhaengigkeiten** und ohne Fremdbibliotheken.
- Alle Tests laufen **ohne Geraet, ohne Emulator und ohne Netz**: `./gradlew test`.
- Nach jeder Task ist der Baum uebersetzbar und die Tests gruen.
- Commit-Nachrichten auf Deutsch, ohne Umlaute in der Betreffzeile.
- Die Bezier-Unterteilung betraegt genau **8** Stuecke je `C`-Segment.

## Dateien

| Datei | Verantwortung | Task |
|---|---|---|
| `core/src/main/kotlin/de/emmpunkt/write/core/font/FontMetrics.kt` | neu: Metriken aus Glyphen ableiten | 1 |
| `core/src/main/kotlin/de/emmpunkt/write/core/font/HersheyFont.kt` | nutzt `FontMetrics` statt eigener Ableitung | 1 |
| `core/src/main/kotlin/de/emmpunkt/write/core/font/SvgFont.kt` | neu: SVG-Font-Parser | 2 |
| `core/src/test/kotlin/de/emmpunkt/write/core/font/SvgFontTest.kt` | neu: Tests dazu | 2 |
| `core/src/main/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFont.kt` | Strich-Korrektur abschaltbar | 3 |
| `core/src/main/resources/fonts/EMS*.svg`, `EMS-OFL.txt` | die vier Schriften und ihre Lizenz | 4 |
| `core/src/main/kotlin/de/emmpunkt/write/core/font/Fonts.kt` | sieben Eintraege, Format je Eintrag | 4 |
| `core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt` | Vergleichsbogen aller Schriften | 5 |
| `README.md`, `CLAUDE.md` | Lizenznennung und Stand | 5 |

---

### Task 1: Metrik-Ableitung herausloesen

Reines Umbauen ohne Verhaltensaenderung. `SvgFont` braucht dieselbe Ableitung wie
`HersheyFont`; sie zweimal zu schreiben waere die Sorte Verdopplung, die spaeter auseinander
laeuft.

Der Trick, der das moeglich macht: `HersheyFont` berechnet die Versalhoehe heute **vor** der
Verschiebung auf die Grundlinie, aus JHF-Koordinaten. Nach der Verschiebung liegt die
Grundlinie aber bei y=0, und die Versalhoehe ist schlicht der hoechste Punkt der Referenzglyphe.
Beide Formate koennen die Ableitung also auf den fertigen, normalisierten Glyphen machen.

**Files:**
- Create: `core/src/main/kotlin/de/emmpunkt/write/core/font/FontMetrics.kt`
- Modify: `core/src/main/kotlin/de/emmpunkt/write/core/font/HersheyFont.kt:36-101`

**Interfaces:**
- Consumes: `Glyph(strokes: List<Polyline>, advance: Float)` aus `StrokeFont.kt`.
- Produces:
  - `data class FontMetrics(val capHeightUnits: Float, val ascenderUnits: Float, val descenderUnits: Float, val lineHeightUnits: Float)`
  - `fun FontMetrics.Companion.derive(id: String, glyphs: Map<Int, Glyph>): FontMetrics`

- [ ] **Step 1: `FontMetrics` anlegen**

Neue Datei `core/src/main/kotlin/de/emmpunkt/write/core/font/FontMetrics.kt`:

```kotlin
package de.emmpunkt.write.core.font

/**
 * Die Schriftmetriken, abgeleitet aus den Glyphen selbst.
 *
 * Bewusst nicht aus Angaben der Schriftdatei: die SVG-Schriften geben durchweg
 * cap-height="500" an, gemessen sind es je nach Schrift 639 bis 939. Wuerde man das
 * uebernehmen, waere ein auf 7 mm eingestellter Text je nach Schrift fast doppelt so gross -
 * und die Groessenangabe der App verloere ihren Sinn, am Papier nachmessbar zu sein.
 *
 * Voraussetzung: die Glyphen liegen bereits in der Konvention aus [Glyph] vor, Grundlinie
 * also bei y = 0 und Y nach oben.
 */
data class FontMetrics(
    val capHeightUnits: Float,
    val ascenderUnits: Float,
    val descenderUnits: Float,
    val lineHeightUnits: Float,
) {
    companion object {
        /** Referenzglyphen fuer die Versalhoehe, in dieser Reihenfolge. */
        private val REFERENCE_GLYPHS = listOf('H'.code, 'A'.code, 'X'.code, 'x'.code)

        /** Buchstaben mit Oberlaenge - bestimmen die obere Haelfte der Zeilenhoehe. */
        private val ASCENDER_GLYPHS = listOf('h'.code, 'l'.code, 'b'.code, 'd'.code, 'k'.code)

        /** Buchstaben mit Unterlaenge - bestimmen die untere Haelfte der Zeilenhoehe. */
        private val DESCENDER_GLYPHS = listOf('g'.code, 'p'.code, 'q'.code, 'y'.code, 'j'.code)

        private fun extremeY(glyphs: Map<Int, Glyph>, codePoints: List<Int>, max: Boolean): Float? {
            val ys = codePoints.mapNotNull { glyphs[it] }
                .flatMap { g -> g.strokes.flatMap { it.points } }
                .map { it.y }
            if (ys.isEmpty()) return null
            return if (max) ys.max() else ys.min()
        }

        fun derive(id: String, glyphs: Map<Int, Glyph>): FontMetrics {
            val reference = REFERENCE_GLYPHS.firstNotNullOfOrNull { glyphs[it] }
                ?: error("Schrift '$id' enthaelt keine der Referenzglyphen H/A/X/x")
            val referencePoints = reference.strokes.flatMap { it.points }
            require(referencePoints.isNotEmpty()) { "Referenzglyphe von '$id' ist leer" }

            // Die Grundlinie liegt bei 0, die Versalhoehe ist damit der hoechste Punkt.
            val capHeight = referencePoints.maxOf { it.y }
            require(capHeight > 0f) { "Versalhoehe von '$id' ist nicht positiv" }

            val allPoints = glyphs.values.flatMap { g -> g.strokes.flatMap { it.points } }

            // Zeilenhoehe aus typischen Buchstaben statt aus dem Maximum ueber alle Glyphen:
            // Klammern und geschweifte Zeichen ragen weit ueber jede Oberlaenge hinaus. Wuerde
            // man danach gehen, stuenden alle Zeilen zu weit auseinander, obwohl solche Zeichen
            // im Text kaum vorkommen.
            val typoAscender = extremeY(glyphs, ASCENDER_GLYPHS, max = true) ?: capHeight
            val typoDescender = extremeY(glyphs, DESCENDER_GLYPHS, max = false) ?: 0f

            return FontMetrics(
                capHeightUnits = capHeight,
                ascenderUnits = allPoints.maxOfOrNull { it.y } ?: capHeight,
                descenderUnits = allPoints.minOfOrNull { it.y } ?: 0f,
                lineHeightUnits = typoAscender - typoDescender,
            )
        }
    }
}
```

- [ ] **Step 2: `HersheyFont` darauf umstellen**

In `HersheyFont.kt` entfallen `REFERENCE_GLYPHS`, `ASCENDER_GLYPHS`, `DESCENDER_GLYPHS` und
`extremeY` (sie stehen jetzt in `FontMetrics`). Die Methode `parse` wird ersetzt durch:

```kotlin
        fun parse(id: String, displayName: String, content: String): HersheyFont {
            val rawGlyphs = parseLines(content.lines())

            // Die Grundlinie bestimmen: in JHF-Koordinaten (Y nach unten) ist sie die
            // UNTERkante der Referenzglyphe, also ihr groesstes Y. Das muss vor der
            // Verschiebung geschehen und ist deshalb JHF-eigen; alles Weitere kann
            // FontMetrics auf den fertigen Glyphen ableiten.
            val reference = BASELINE_REFERENCE.firstNotNullOfOrNull { rawGlyphs[it] }
                ?: error("Schrift '$id' enthaelt keine der Referenzglyphen H/A/X/x")
            val referencePoints = reference.strokes.flatMap { it.points }
            require(referencePoints.isNotEmpty()) { "Referenzglyphe von '$id' ist leer" }
            val baseline = referencePoints.maxOf { it.y }

            val glyphs = rawGlyphs.mapValues { (_, raw) -> toBaselineOrigin(raw, baseline) }
            val metrics = FontMetrics.derive(id, glyphs)

            return HersheyFont(
                id = id,
                displayName = displayName,
                capHeightUnits = metrics.capHeightUnits,
                ascenderUnits = metrics.ascenderUnits,
                descenderUnits = metrics.descenderUnits,
                lineHeightUnits = metrics.lineHeightUnits,
                glyphs = glyphs,
            )
        }

        /** Dieselben Glyphen wie in [FontMetrics], hier nur zur Bestimmung der Grundlinie. */
        private val BASELINE_REFERENCE = listOf('H'.code, 'A'.code, 'X'.code, 'x'.code)
```

- [ ] **Step 3: Bestehende Tests laufen lassen**

Run: `./gradlew :core:test`
Expected: PASS. Die vorhandene `HersheyFontTest` prueft unter anderem `capHeightUnits == 9f`
fuer das H aus der Originalnotiz und die Abdeckung aller mitgelieferten Schriften — sie ist
damit der Beweis, dass der Umbau nichts veraendert hat.

Schlaegt etwas fehl, ist es ein echter Fehler im Umbau: die Ableitung soll **exakt** dieselben
Werte liefern wie vorher.

- [ ] **Step 4: Voller Testlauf**

Run: `./gradlew test`
Expected: PASS, 94 Tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/de/emmpunkt/write/core/font/FontMetrics.kt \
        core/src/main/kotlin/de/emmpunkt/write/core/font/HersheyFont.kt
git commit -m "core: Metrik-Ableitung in FontMetrics herausgeloest"
```

---

### Task 2: Der SVG-Font-Parser

**Files:**
- Create: `core/src/main/kotlin/de/emmpunkt/write/core/font/SvgFont.kt`
- Test: `core/src/test/kotlin/de/emmpunkt/write/core/font/SvgFontTest.kt`

**Interfaces:**
- Consumes: `FontMetrics.derive(id, glyphs)` aus Task 1; `Glyph`, `StrokeFont`, `Polyline`, `Point`.
- Produces: `object SvgFont { fun parse(id: String, displayName: String, content: String): StrokeFont }`

- [ ] **Step 1: Den Test schreiben**

Neue Datei `core/src/test/kotlin/de/emmpunkt/write/core/font/SvgFontTest.kt`:

```kotlin
package de.emmpunkt.write.core.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SvgFontTest {

    /**
     * Ein kleiner Font mit bekannten Werten. Enthaelt alles, worauf es ankommt: ein 'H' fuer
     * die Versalhoehe, Ober- und Unterlaenge fuer die Zeilenhoehe, ein HTML-kodiertes Umlaut-
     * zeichen, eine Bezierkurve und ein Leerzeichen ohne Pfad.
     */
    private val miniFont = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <svg xmlns="http://www.w3.org/2000/svg" version="1.1">
        <defs>
        <font id="Mini" horiz-adv-x="400" >
        <font-face units-per-em="1000" ascent="800" descent="-200" cap-height="500" />
        <glyph unicode=" " glyph-name="space" horiz-adv-x="200" />
        <glyph unicode="H" glyph-name="H" horiz-adv-x="600" d="M 100 0 L 100 700 M 500 0 L 500 700 M 100 350 L 500 350" />
        <glyph unicode="h" glyph-name="h" horiz-adv-x="500" d="M 0 0 L 0 720" />
        <glyph unicode="g" glyph-name="g" horiz-adv-x="500" d="M 0 300 L 0 -250" />
        <glyph unicode="&#xe4;" glyph-name="adieresis" horiz-adv-x="450" d="M 10 20 L 30 40" />
        <glyph unicode="C" glyph-name="C" horiz-adv-x="500" d="M 0 0 C 100 200 300 200 400 0" />
        </font>
        </defs>
        </svg>
    """.trimIndent()

    private fun mini() = SvgFont.parse("mini", "Mini", miniFont)

    @Test
    fun `liest Vorschub und Punkte einer Glyphe`() {
        val glyph = assertNotNull(mini().glyph('H'.code))

        assertEquals(600f, glyph.advance)
        assertEquals(3, glyph.strokes.size, "M beginnt jeweils einen neuen Zug")
        assertEquals(100f, glyph.strokes[0].points[0].x)
        assertEquals(0f, glyph.strokes[0].points[0].y)
        assertEquals(700f, glyph.strokes[0].points[1].y)
    }

    @Test
    fun `leitet die Versalhoehe aus dem H ab, nicht aus dem Attribut cap-height`() {
        // Die Datei behauptet cap-height="500", das H ist aber 700 hoch. Alle vier
        // mitgelieferten SVG-Schriften haben genau diesen Fehler.
        assertEquals(700f, mini().capHeightUnits)
    }

    @Test
    fun `leitet die Zeilenhoehe aus Ober- und Unterlaenge ab`() {
        // h reicht bis 720, g bis -250.
        assertEquals(970f, mini().lineHeightUnits)
    }

    @Test
    fun `entkodiert HTML-kodierte Zeichen`() {
        // Ohne Entkodierung fehlten ausgerechnet die deutschen Umlaute.
        val umlaut = assertNotNull(mini().glyph(0x00E4), "ae (&#xe4;) muss vorhanden sein")
        assertEquals(450f, umlaut.advance)
    }

    @Test
    fun `unterteilt eine Bezierkurve und behaelt Anfang und Ende exakt`() {
        val glyph = assertNotNull(mini().glyph('C'.code))
        val punkte = glyph.strokes.single().points

        assertEquals(9, punkte.size, "Startpunkt plus acht Teilstuecke")
        assertEquals(0f, punkte.first().x)
        assertEquals(0f, punkte.first().y)
        assertEquals(400f, punkte.last().x, 0.01f)
        assertEquals(0f, punkte.last().y, 0.01f)

        // Die Kurve woelbt sich nach oben, die Zwischenpunkte liegen also ueber der Sehne.
        assertTrue(punkte.drop(1).dropLast(1).all { it.y > 0f }, "Zwischenpunkte muessen gewoelbt liegen")
        // Und sie laufen monoton nach rechts.
        assertTrue(punkte.zipWithNext().all { (a, b) -> b.x > a.x }, "x muss monoton wachsen")
    }

    @Test
    fun `Leerzeichen hat Vorschub aber keine Striche`() {
        val space = assertNotNull(mini().glyph(' '.code))
        assertTrue(space.strokes.isEmpty())
        assertEquals(200f, space.advance)
    }

    @Test
    fun `mitgelieferte SVG-Schriften koennen deutsche Notizen`() {
        val noetig = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789äöüÄÖÜß.,;:!?-()€"
        listOf("allure", "zierschrift", "druckschrift", "einladung").forEach { id ->
            // Ohne diese Zusicherung prueft der Test die falsche Schrift: Fonts.entry faellt
            // bei unbekanntem Bezeichner still auf die Vorgabe zurueck, und deren
            // Overlay-Schicht deckt dieselben Sonderzeichen ab.
            assertEquals(id, Fonts.entry(id).id, "$id muss ein eigener Eintrag sein")
            val font = Fonts.load(id)
            noetig.forEach { ch ->
                assertTrue(font.has(ch.code), "$id: Zeichen '$ch' fehlt")
            }
            assertTrue(font.capHeightUnits > 0f, "$id: Versalhoehe nicht positiv")
            assertTrue(font.lineHeightUnits > font.capHeightUnits, "$id: Zeilenhoehe unplausibel")
        }
    }

    @Test
    fun `abgeleitete Versalhoehe entspricht der gemessenen Hoehe des H`() {
        listOf("allure", "zierschrift", "druckschrift", "einladung").forEach { id ->
            // Siehe oben: ohne das pruefte der Test die Vorgabeschrift statt der gemeinten.
            assertEquals(id, Fonts.entry(id).id, "$id muss ein eigener Eintrag sein")
            val font = Fonts.load(id)
            val h = assertNotNull(font.glyph('H'.code))
            val gemessen = h.strokes.flatMap { it.points }.maxOf { it.y }
            assertEquals(gemessen, font.capHeightUnits, 0.01f, "$id")
        }
    }
}
```

Die letzten beiden Testfaelle setzen die Schriften aus Task 4 voraus. Bis dahin schlagen sie
fehl — das ist beabsichtigt und wird in Task 4 aufgeloest. Fuehre sie in dieser Task mit
`--tests` gezielt **nicht** aus (siehe Step 2).

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestaetigen**

Run: `./gradlew :core:test --tests '*SvgFontTest*'`
Expected: FAIL – Uebersetzungsfehler, `SvgFont` ist unbekannt.

- [ ] **Step 3: Den Parser schreiben**

Neue Datei `core/src/main/kotlin/de/emmpunkt/write/core/font/SvgFont.kt`:

```kotlin
package de.emmpunkt.write.core.font

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline

/**
 * Schrift im SVG-Font-Format (SVG 1.1), wie sie das Projekt svg-fonts von Windell H. Oskay
 * liefert.
 *
 * Anders als TTF/OTF beschreiben diese Dateien Mittellinien statt Umrisse - genau das, was ein
 * Stiftplotter braucht. Zwei Eigenschaften des Formats machen den Parser klein:
 *
 * 1. Die Pfade sind bereits in Geradenstuecke aufgeloest. In EMSAllure.svg stehen 4259 L-,
 *    417 M- und nur 76 C-Kommandos; ein vollstaendiger SVG-Pfad-Parser waere unnoetiger
 *    Aufwand.
 * 2. Die Y-Achse zeigt bereits nach oben, mit der Grundlinie bei 0 und negativen
 *    Unterlaengen. Das ist die Konvention aus [Glyph] - eine Spiegelung wie bei JHF entfaellt.
 *
 * Gelesen wird mit regulaeren Ausdruecken statt mit einem XML-Parser: die Dateien sind
 * maschinengeneriert und gleichfoermig, und `core` bleibt so frei von Fremdbibliotheken.
 */
object SvgFont {

    /** Feste Unterteilung je Bezierkurve. Bei 1000 Einheiten Kegelhoehe liegt der Fehler
     *  deutlich unter der Strichbreite eines Fineliners; eine Fehlerschaetzung lohnt bei
     *  76 Kurven je Datei nicht. */
    private const val BEZIER_STUECKE = 8

    private val GLYPH = Regex("""<glyph\s+([^>]*?)/?>""", RegexOption.DOT_MATCHES_ALL)
    private val UNICODE_ATTR = Regex("""unicode="([^"]*)"""")
    private val ADVANCE_ATTR = Regex("""horiz-adv-x="(-?[\d.]+)"""")
    private val PATH_ATTR = Regex("""\sd="([^"]*)"""")
    private val ENTITY = Regex("""&#x([0-9a-fA-F]+);|&#(\d+);|&(amp|lt|gt|quot|apos);""")
    private val COMMAND = Regex("""([MLC])((?:\s*-?[\d.]+)+)""")
    private val NUMBER = Regex("""-?[\d.]+""")

    fun parse(id: String, displayName: String, content: String): StrokeFont {
        val glyphs = LinkedHashMap<Int, Glyph>()

        GLYPH.findAll(content).forEach { treffer ->
            val attribute = treffer.groupValues[1]
            val roh = UNICODE_ATTR.find(attribute)?.groupValues?.get(1) ?: return@forEach
            val zeichen = entkodieren(roh)
            // Ligaturen und mehrzeichige Eintraege ueberspringen: der Textsatz arbeitet
            // zeichenweise und koennte sie gar nicht ansprechen.
            if (zeichen.codePointCount(0, zeichen.length) != 1) return@forEach

            val advance = ADVANCE_ATTR.find(attribute)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val pfad = PATH_ATTR.find(attribute)?.groupValues?.get(1)
            glyphs[zeichen.codePointAt(0)] = Glyph(
                strokes = if (pfad.isNullOrBlank()) emptyList() else zuege(pfad),
                advance = advance,
            )
        }

        require(glyphs.isNotEmpty()) { "SVG-Schrift '$id' enthaelt keine Glyphen" }
        val metrics = FontMetrics.derive(id, glyphs)

        return SimpleStrokeFont(
            id = id,
            displayName = displayName,
            capHeightUnits = metrics.capHeightUnits,
            ascenderUnits = metrics.ascenderUnits,
            descenderUnits = metrics.descenderUnits,
            lineHeightUnits = metrics.lineHeightUnits,
            glyphs = glyphs,
        )
    }

    /** `&#xe4;` und Freunde aufloesen - ohne das fehlten ausgerechnet die Umlaute. */
    private fun entkodieren(roh: String): String =
        ENTITY.replace(roh) { m ->
            when {
                m.groupValues[1].isNotEmpty() -> m.groupValues[1].toInt(16).toChar().toString()
                m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt().toChar().toString()
                else -> when (m.groupValues[3]) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; else -> "'"
                }
            }
        }

    private fun zuege(pfad: String): List<Polyline> {
        val zuege = ArrayList<Polyline>()
        var aktuell = ArrayList<Point>()

        fun abschliessen() {
            if (aktuell.size >= 2) zuege += Polyline(aktuell.toList())
            aktuell = ArrayList()
        }

        COMMAND.findAll(pfad).forEach { treffer ->
            val befehl = treffer.groupValues[1]
            val werte = NUMBER.findAll(treffer.groupValues[2]).map { it.value.toFloat() }.toList()

            when (befehl) {
                "M" -> {
                    abschliessen()
                    // Nach dem ersten Punktpaar gelten weitere Paare als Geraden.
                    werte.chunked(2).filter { it.size == 2 }.forEach { aktuell += Point(it[0], it[1]) }
                }
                "L" -> werte.chunked(2).filter { it.size == 2 }.forEach { aktuell += Point(it[0], it[1]) }
                else -> werte.chunked(6).filter { it.size == 6 }.forEach { k ->
                    val start = aktuell.lastOrNull() ?: Point(k[0], k[1])
                    aktuell += bezier(start, Point(k[0], k[1]), Point(k[2], k[3]), Point(k[4], k[5]))
                }
            }
        }
        abschliessen()
        return zuege
    }

    /** Kubische Bezierkurve in [BEZIER_STUECKE] Geraden. Der Startpunkt steht schon im Zug. */
    private fun bezier(p0: Point, p1: Point, p2: Point, p3: Point): List<Point> =
        (1..BEZIER_STUECKE).map { schritt ->
            val t = schritt.toFloat() / BEZIER_STUECKE
            val g = 1f - t
            Point(
                g * g * g * p0.x + 3f * g * g * t * p1.x + 3f * g * t * t * p2.x + t * t * t * p3.x,
                g * g * g * p0.y + 3f * g * g * t * p1.y + 3f * g * t * t * p2.y + t * t * t * p3.y,
            )
        }
}

/** Traeger fuer bereits fertig geparste Glyphen. */
private class SimpleStrokeFont(
    override val id: String,
    override val displayName: String,
    override val capHeightUnits: Float,
    override val ascenderUnits: Float,
    override val descenderUnits: Float,
    override val lineHeightUnits: Float,
    private val glyphs: Map<Int, Glyph>,
) : StrokeFont {
    override fun glyph(codePoint: Int): Glyph? = glyphs[codePoint]
}
```

- [ ] **Step 4: Die formatbezogenen Tests laufen lassen**

Run: `./gradlew :core:test --tests '*SvgFontTest*'`

Expected: **6 bestanden, 2 fehlgeschlagen.** Fehlschlagen muessen genau
`mitgelieferte SVG-Schriften koennen deutsche Notizen` und
`abgeleitete Versalhoehe entspricht der gemessenen Hoehe des H`, jeweils an der Zusicherung
`… muss ein eigener Eintrag sein` – die Schriften nimmt erst Task 4 auf.

**Warum diese Zusicherung noetig ist:** `Fonts.entry` faellt bei unbekanntem Bezeichner still
auf die Vorgabeschrift zurueck. Ohne die Pruefung wuerden beide Tests die Hershey-Schreibschrift
laden und trotzdem bestehen – deren Overlay-Schicht kennt Umlaute, Eszett und Euro ebenfalls,
und die Versalhoehe leitet `FontMetrics` fuer jedes Format aus `H` ab. Ein Test, der auch ohne
seinen Pruefgegenstand besteht, ist wertlos.

Schlaegt einer der uebrigen sechs fehl, ist das ein echter Fehler im Parser.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/de/emmpunkt/write/core/font/SvgFont.kt \
        core/src/test/kotlin/de/emmpunkt/write/core/font/SvgFontTest.kt
git commit -m "core: Parser fuer einlinige SVG-Schriften"
```

---

### Task 3: Strich-Korrektur abschaltbar machen

`GlyphOverlayFont` ersetzt Bindestrich, Halbgeviert- und Geviertstrich **vor** der
Basisschrift, weil Hersheys Strich mit 0,86 Versalhoehen fast doppelt so breit ist wie ein
Kleinbuchstabe und auf deren Oberkante sitzt. Die SVG-Schriften bringen ordentliche eigene
Striche mit; dort waere die Ersetzung eine Verschlechterung.

**Files:**
- Modify: `core/src/main/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFont.kt:21`, `:47-67`
- Test: `core/src/test/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFontTest.kt`

**Interfaces:**
- Produces: `class GlyphOverlayFont(base: StrokeFont, stricheErsetzen: Boolean = true)`

- [ ] **Step 1: Den Test schreiben**

An `core/src/test/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFontTest.kt` anhaengen (die
Datei existiert bereits; neue Testfaelle in die vorhandene Klasse einfuegen):

```kotlin
    @Test
    fun `laesst den Strich der Basisschrift stehen, wenn die Korrektur aus ist`() {
        val basis = Fonts.load("sans")
        val eigen = assertNotNull(basis.glyph('-'.code))

        val ohneKorrektur = GlyphOverlayFont(basis, stricheErsetzen = false)
        val durchgereicht = assertNotNull(ohneKorrektur.glyph('-'.code))

        assertEquals(eigen.advance, durchgereicht.advance)
        assertEquals(
            eigen.strokes.flatMap { it.points }.map { it.x },
            durchgereicht.strokes.flatMap { it.points }.map { it.x },
        )
    }

    @Test
    fun `ersetzt den Strich weiterhin, wenn die Korrektur an ist`() {
        val basis = Fonts.load("sans")
        val eigen = assertNotNull(basis.glyph('-'.code))
        val korrigiert = assertNotNull(GlyphOverlayFont(basis).glyph('-'.code))

        assertTrue(
            korrigiert.advance < eigen.advance,
            "Der nachgezeichnete Strich muss schmaler sein als Hersheys eigener",
        )
    }

    @Test
    fun `ersetzt typografische Zeichen auch bei abgeschalteter Strich-Korrektur`() {
        // Android-Tastaturen setzen diese Zeichen selbsttaetig ein; ohne Ersetzung entstuenden
        // Luecken im geplotteten Text. Das gilt unabhaengig von der Strich-Korrektur.
        val font = GlyphOverlayFont(Fonts.load("sans"), stricheErsetzen = false)
        assertNotNull(font.glyph(0x2019), "typografischer Apostroph muss ersetzt werden")
        assertNotNull(font.glyph(0x201C), "geschwungenes Anfuehrungszeichen muss ersetzt werden")
    }
```

Falls die Datei die Einfuhren `kotlin.test.assertEquals`, `assertNotNull` oder `assertTrue`
noch nicht hat, ergaenze sie.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestaetigen**

Run: `./gradlew :core:test --tests '*GlyphOverlayFontTest*'`
Expected: FAIL – der Konstruktor kennt den zweiten Parameter nicht.

- [ ] **Step 3: Den Parameter einbauen**

In `GlyphOverlayFont.kt` die Klassendeklaration (Zeile 21) ersetzen:

```kotlin
/**
 * @param stricheErsetzen ob die waagerechten Striche der Basisschrift durch eigene ersetzt
 *   werden. Fuer die Hershey-Schriften noetig - ihr Bindestrich ist mit 0,86 Versalhoehen
 *   breiter als jeder Kleinbuchstabe und sitzt auf dessen Oberkante. Die SVG-Schriften bringen
 *   brauchbare eigene Striche mit; dort waere die Ersetzung eine Verschlechterung.
 */
class GlyphOverlayFont(
    private val base: StrokeFont,
    private val stricheErsetzen: Boolean = true,
) : StrokeFont {
```

Und in `build` (Zeile 48-49) die erste Zeile ersetzen:

```kotlin
        // Vor der Basisschrift: deren Striche sind fuer Fliesstext zu lang und sitzen zu hoch.
        if (stricheErsetzen) STRICHE[codePoint]?.let { return drawDash(it) }
```

Die Ersetzung am Ende der Methode (`SUBSTITUTIONS`) bleibt unveraendert – sie greift erst,
wenn die Basisschrift nichts hat, und wird auch von SVG-Schriften gebraucht.

Ein Detail dort: die Zeile

```kotlin
            return STRICHE[replacement]?.let { drawDash(it) } ?: base.glyph(replacement)
```

muss ebenfalls auf den Schalter hoeren, sonst zeichnete ein ueber `SUBSTITUTIONS` umgeleitetes
Zeichen doch wieder den nachgezeichneten Strich:

```kotlin
            return (if (stricheErsetzen) STRICHE[replacement]?.let { drawDash(it) } else null)
                ?: base.glyph(replacement)
```

- [ ] **Step 4: Tests laufen lassen**

Run: `./gradlew :core:test --tests '*GlyphOverlayFontTest*'`
Expected: PASS, einschliesslich der bereits vorhandenen Testfaelle zu Umlauten und Eszett.

- [ ] **Step 5: Voller Testlauf**

Run: `./gradlew test`
Expected: PASS bis auf die zwei bekannten Fehlschlaege aus Task 2 (die Schriften fehlen noch).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFont.kt \
        core/src/test/kotlin/de/emmpunkt/write/core/font/GlyphOverlayFontTest.kt
git commit -m "core: Strich-Korrektur der Overlay-Schicht abschaltbar"
```

---

### Task 4: Schriften aufnehmen und Verzeichnis umstellen

**Files:**
- Create: `core/src/main/resources/fonts/EMSAllure.svg`, `EMSDecorousScript.svg`, `EMSDelight.svg`, `EMSInvite.svg`, `EMS-OFL.txt`
- Modify: `core/src/main/kotlin/de/emmpunkt/write/core/font/Fonts.kt`

**Interfaces:**
- Consumes: `SvgFont.parse(id, displayName, content)` aus Task 2; `GlyphOverlayFont(base, stricheErsetzen)` aus Task 3.
- Produces: `Fonts.available` mit sieben Eintraegen; `Fonts.Entry` mit dem neuen Feld `format`.

- [ ] **Step 1: Die Dateien holen**

```bash
cd core/src/main/resources/fonts
BASIS="https://gitlab.com/oskay/svg-fonts/-/raw/master/fonts/EMS"
for f in EMSAllure EMSDecorousScript EMSDelight EMSInvite; do
  curl -fsS --max-time 60 -o "$f.svg" "$BASIS/$f.svg"
done
curl -fsS --max-time 60 -o EMS-OFL.txt "$BASIS/OFL.txt"
ls -la EMS*
cd -
```

Erwartet: vier SVG-Dateien zwischen 44 und 71 KB sowie `EMS-OFL.txt`. Sind Dateien leer oder
fehlen, brich ab und melde es – ohne die Schriften ist der Rest der Task sinnlos.

Zur Kontrolle, dass es die richtigen Dateien sind:

```bash
grep -c '<glyph' core/src/main/resources/fonts/EMS*.svg
```

Erwartet: **216** in jeder der vier Dateien.

- [ ] **Step 2: `Fonts.kt` umstellen**

Die Datei vollstaendig ersetzen durch:

```kotlin
package de.emmpunkt.write.core.font

/**
 * Verzeichnis der mitgelieferten Schriften.
 *
 * Die Schriftdateien liegen als Java-Ressourcen im core-Modul und landen dadurch unveraendert
 * im APK. Jede geladene Schrift wird in [GlyphOverlayFont] eingepackt - der Rest der App
 * bekommt Schriften nie ohne diese Schicht zu sehen.
 */
object Fonts {

    /** In welchem Format die Schriftdatei vorliegt. Bestimmt den Parser. */
    enum class Format { JHF, SVG }

    data class Entry(
        val id: String,
        val displayName: String,
        val resource: String,
        val format: Format,
        /** Ob die Schrift verbundene Schreibschrift ist. Steuert nur die Anzeige in der Auswahl. */
        val cursive: Boolean,
    )

    val available: List<Entry> = listOf(
        Entry("allure", "Allure", "EMSAllure.svg", Format.SVG, cursive = true),
        Entry("zierschrift", "Zierschrift", "EMSDecorousScript.svg", Format.SVG, cursive = true),
        Entry("einladung", "Einladung", "EMSInvite.svg", Format.SVG, cursive = true),
        Entry("druckschrift", "Druckschrift", "EMSDelight.svg", Format.SVG, cursive = false),
        Entry("script-simplex", "Schreibschrift", "scripts.jhf", Format.JHF, cursive = true),
        Entry("sans", "Technisch", "futural.jhf", Format.JHF, cursive = false),
        Entry("serif", "Serif", "rowmans.jhf", Format.JHF, cursive = false),
    )

    val defaultId: String = available.first().id

    private val cache = HashMap<String, StrokeFont>()

    fun entry(id: String): Entry =
        available.firstOrNull { it.id == id } ?: available.first { it.id == defaultId }

    /** Laedt die Schrift (gepuffert). Unbekannte Bezeichner fallen auf [defaultId] zurueck. */
    @Synchronized
    fun load(id: String): StrokeFont {
        val e = entry(id)
        return cache.getOrPut(e.id) {
            val content = readResource("fonts/${e.resource}")
            val basis = when (e.format) {
                Format.JHF -> HersheyFont.parse(e.id, e.displayName, content)
                Format.SVG -> SvgFont.parse(e.id, e.displayName, content)
            }
            // Die Strich-Korrektur ist nur fuer die Hershey-Schriften noetig; die SVG-Schriften
            // bringen brauchbare eigene Striche mit.
            GlyphOverlayFont(basis, stricheErsetzen = e.format == Format.JHF)
        }
    }

    private fun readResource(path: String): String {
        val stream = Fonts::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Schriftdatei '$path' nicht im Paket gefunden")
        // ISO-8859-1 fuer die JHF-Dateien, deren Bytes direkt Koordinaten kodieren. Die
        // SVG-Dateien sind reines ASCII und kodieren alles darueber als HTML-Entitaet
        // (nachgeprueft: keine der vier enthaelt ein Byte ueber 0x7F) - sie ueberstehen
        // diese Kodierung damit unveraendert.
        return stream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
    }
}
```

Die Datei der Kalligrafie wird nicht mehr gebraucht:

```bash
git rm core/src/main/resources/fonts/scriptc.jhf
```

**Zwei Folgen dieser Reihenfolge, die beabsichtigt sind:**

1. `defaultId` ist jetzt `allure` statt `script-simplex` – der erste Eintrag der Liste. Neue
   Installationen starten also mit der besten verbundenen Schrift. Bestehende Installationen
   behalten ihre gespeicherte `fontId`, weil `AppSettings` sie aus DataStore liest.
2. Eine gespeicherte `fontId` `script-complex` (Kalligrafie) gibt es nicht mehr. `Fonts.entry`
   faengt das ueber den vorhandenen Rueckfall ab und liefert die Vorgabeschrift – es bricht
   nichts, die Notiz oeffnet mit Allure.

Beides ist in der Abnahme zu pruefen.

- [ ] **Step 3: Alle Tests laufen lassen**

Run: `./gradlew test`
Expected: PASS. Jetzt bestehen auch die beiden Testfaelle aus Task 2, die auf die Schriften
warten. Der bereits vorhandene Test `mitgelieferte Schriften laden und decken ASCII ab` in
`HersheyFontTest` prueft **alle** Eintraege aus `Fonts.available` – also auch die vier neuen –
auf vollstaendige ASCII-Abdeckung; er ist damit die Gegenprobe, dass die SVG-Dateien wirklich
brauchbar geladen werden.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/fonts/ core/src/main/kotlin/de/emmpunkt/write/core/font/Fonts.kt
git commit -m "core: vier EMS-Schreibschriften aufgenommen, Kalligrafie entfernt"
```

---

### Task 5: Musterbilder und Doku

**Files:**
- Modify: `core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt`
- Modify: `README.md`, `CLAUDE.md`

- [ ] **Step 1: Vergleichsbogen ergaenzen**

In `PreviewSamplesTest` innerhalb von `Musterbilder erzeugen` den Block, der alle Schriften
vergleicht (`// Alle vier Schriften im Vergleich.`), ersetzen durch:

```kotlin
        // Alle Schriften im Vergleich - der Bogen, an dem sich das Schriftbild beurteilen
        // laesst, ohne Papier zu verbrauchen.
        val probe = "Handschrift 123 äöüß"
        Fonts.available.forEachIndexed { i, entry ->
            render("05-${i}-${entry.id}", probe, TextStyle(entry.id, sizeMm = 9f), Frame(150f, 30f, Margins.all(5f)))
        }

        // Der Fall, der Etappe 2b ausgeloest hat: bei grosser Schrift trat der Versatz
        // zwischen den Buchstaben zutage. Dieselbe Zeile in jeder Schrift, gross gesetzt.
        Fonts.available.forEach { entry ->
            render("19-versatz-${entry.id}", "Etappe geschafft",
                TextStyle(entry.id, sizeMm = 20f), Frame(230f, 45f, Margins.all(5f)))
        }
```

Und die Zusicherung am Ende der Methode anheben:

```kotlin
        assertTrue(erzeugt >= 30, "Es wurden nur $erzeugt Musterbilder erzeugt")
```

- [ ] **Step 2: Musterbilder erzeugen und ansehen**

Run: `./gradlew :core:test --tests '*PreviewSamplesTest*' --rerun-tasks`
Expected: PASS, und `core/build/preview/` enthaelt je Schrift ein Bild `19-versatz-*.png`.

Sieh dir mindestens `19-versatz-allure.png` und `19-versatz-script-simplex.png` an und
vergleiche: bei Allure muessen die Buchstaben ineinander uebergehen, bei der Hershey-
Schreibschrift bleiben die bekannten Luecken. Faellt der Vergleich anders aus, melde es –
dann stimmt eine Annahme dieses Plans nicht.

- [ ] **Step 3: `README.md` ergaenzen**

Den Abschnitt „## Schriften" ersetzen durch:

```markdown
## Schriften

Sieben einlinige Schriften, alle gemeinfrei oder unter freier Lizenz
(`core/src/main/resources/fonts/`).

**Vier SVG-Schreibschriften** aus dem Projekt [svg-fonts](https://gitlab.com/oskay/svg-fonts):
Allure, Zierschrift, Einladung und Druckschrift. Sie stammen von Sheldon B. Michaels, die
Umsetzung ins SVG-Font-Format von Windell H. Oskay, Lizenz **SIL Open Font License** (Wortlaut
in `EMS-OFL.txt`). Drei davon sind echte verbundene Kursiven; sie bringen Umlaute, ß, € und die
Gedankenstriche selbst mit.

**Drei Hershey-Vektorschriften** im JHF-Format: Schreibschrift, Technisch und Serif. Sie laufen
schmaler als die SVG-Schriften und eignen sich deshalb weiter für viel Text auf kleinem Blatt.
Sie decken nur ASCII 32–126 ab; `GlyphOverlayFont` ergänzt Umlaute durch aufgesetztes Trema,
ß und € als handdefinierte Glyphen und bildet typografische Zeichen auf ihre ASCII-Entsprechung
ab. Attribution siehe `HERSHEY-NOTICE.txt` – die Nennung von A. V. Hershey und James Hurt ist
Auflage der Nutzungsbedingung.

### Warum die Größenangabe aus den Glyphen kommt

Die Schriftgröße der App ist die Versalhöhe in Millimetern, am Papier nachmessbar. Die
SVG-Dateien geben dafür durchweg `cap-height="500"` an – gemessen sind es aber 639 bis 939.
Der Parser leitet die Versalhöhe deshalb aus der tatsächlichen Höhe des `H` ab, so wie es der
JHF-Parser schon tut. Nur so bedeutet eine eingestellte Größe in jeder Schrift dasselbe.

### Waagerechte Striche

Die Hershey-Schriften bringen einen Bindestrich von 0,86 Versalhöhen mit – breiter als jeder
Kleinbuchstabe – und setzen ihn auf deren Oberkante. Im Fließtext fällt beides auf.
`GlyphOverlayFont` ersetzt ihn deshalb: 0,30 Versalhöhen lang, auf halber x-Höhe, mit
0,14 Versalhöhen Luft an jeder Seite. Halbgeviert- (0,50) und Geviertstrich (0,70) sind eigene
Längen, weil das im Deutschen verschiedene Zeichen sind. Abgesichert durch `StricheTest`.

Bei den SVG-Schriften bleibt diese Korrektur **aus** – deren eigene Striche sind brauchbar.
```

Im Abschnitt „## Stand" die Etappen-Zeilen ersetzen durch:

```markdown
- **Etappe 2a steht:** Einpassen und die vier Feintuning-Regler.
- **Etappe 2b steht:** vier verbundene SVG-Schreibschriften, Hershey-Kalligrafie entfernt.
- **Etappe 3:** Notizliste, Vorlagen mit Platzhaltern, gemischte Stile, Upload auf SD.
```

- [ ] **Step 4: `CLAUDE.md` fortschreiben**

Den Abschnitt „## Etappe 2b – „Schön" (als Nächstes)" ersetzen durch:

```markdown
## Etappe 2b abgeschlossen (2026-08-02)

Vier einlinige SVG-Schreibschriften (EMS Allure, Decorous Script, Invite, Delight; SIL OFL)
über einen zweiten Parser `SvgFont` hinter derselben `StrokeFont`-Schnittstelle. Die
Hershey-Kalligrafie ist entfernt.

**Anlass war eine Messung, kein Wunsch:** Am ersten Probeblatt mit 25 mm Versalhöhe fiel auf,
dass Buchstaben nicht aneinander anschließen. Die Untersuchung ergab: Von den 676
Kleinbuchstabenpaaren der Hershey-Schreibschrift verbinden 85 % exakt, der Rest nicht – `t`
endet oben am Querstrich, Großbuchstaben und Ziffern haben gar keinen Anschlusspunkt. Im
G-Code selbst gibt es dabei **keine** Sprünge zwischen zeichnenden Segmenten; die App hebt
sauber ab. Der Effekt skaliert mit der Größe und ist bei 7 mm mit 0,28 mm unsichtbar.

Zwei Dinge, die bei Arbeit an den Schriften Zeit sparen:

- **`cap-height` in den SVG-Dateien ist unbrauchbar** (überall 500, real 639–939). Metriken
  kommen aus `FontMetrics.derive` und damit aus den Glyphen.
- **Ob Buchstaben „verbinden", lässt sich nicht einfach messen.** Ein Versuch, den letzten
  Punkt eines Pfades als Auslaufstrich zu nehmen, lieferte für EMS Allure 30 % statt der im
  Bild sichtbaren durchgehenden Verbindung – die Strichrichtung folgt in diesen Schriften
  nicht der Schreibrichtung. Beurteilt wird über die Musterbilder.

## Etappe 3 – „Bequem"

Notizliste mit Room, Vorlagen mit Platzhaltern, gemischte Stile je Absatz, Upload auf SD
(`POST /upload` + `$SD/Run=` – anderer Endpunkt als das entfernte `/command`).
```

- [ ] **Step 5: Voller Testlauf**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/test/kotlin/de/emmpunkt/write/core/debug/PreviewSamplesTest.kt README.md CLAUDE.md
git commit -m "Doku und Musterbilder fuer die SVG-Schreibschriften"
```

---

## Abnahme

- [ ] `./gradlew test` grün, einschliesslich `SvgFontTest`.
- [ ] `core/build/preview/19-versatz-allure.png` zeigt ineinander uebergehende Buchstaben,
      `19-versatz-script-simplex.png` die bekannten Luecken.
- [ ] In der App stehen sieben Schriften zur Wahl, die Kalligrafie ist verschwunden.
- [ ] Eine Notiz mit Umlauten, ß und Bindestrich ist in jeder EMS-Schrift vollstaendig
      darstellbar – der Editor meldet keine fehlenden Zeichen.
- [ ] Eine zuvor auf Kalligrafie eingestellte Notiz oeffnet ohne Fehler (Rueckfall auf die
      Vorgabeschrift).
- [ ] Ein Probeblatt an der Maschine in Allure bei 25 mm: die Buchstaben haengen zusammen.
      **Vorher ankuendigen** – dieser Schritt bewegt die Maschine.
