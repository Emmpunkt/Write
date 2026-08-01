package de.emmpunkt.write.core.gcode

import de.emmpunkt.write.core.debug.GCodeRenderer
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.core.geometry.travelLength
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.layoutText
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GCodeGeneratorTest {

    private val profile = MachineProfile(
        zUpMm = 3f,
        zDownMm = -1.5f,
        workAreaXMm = 200f,
        workAreaYMm = 200f,
    )
    private val penDownZ = (profile.zUpMm + profile.zDownMm) / 2f

    private var originalLocale: Locale? = null

    @BeforeTest
    fun setUp() {
        // Der Nutzer arbeitet auf einem deutsch eingestellten Geraet. Wuerde die Formatierung
        // die Standard-Locale benutzen, entstuende "X12,5" - FluidNC wiese jede Zeile ab.
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @AfterTest
    fun tearDown() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    private fun simpleStroke() = listOf(
        Polyline(listOf(Point(10f, 20f), Point(30.5f, 40.25f))),
    )

    @Test
    fun `benutzt Punkt als Dezimaltrennzeichen auch bei deutscher Locale`() {
        val job = generateGCode(simpleStroke(), profile)
        assertFalse(job.text.contains(','), "G-Code enthaelt ein Komma:\n${job.text}")
        assertTrue(job.text.contains("X30.5"), "Erwartete Koordinate fehlt:\n${job.text}")
    }

    @Test
    fun `Kopf setzt Einheiten und hebt den Stift vor der ersten Fahrt`() {
        val lines = generateGCode(simpleStroke(), profile).lines

        assertEquals("G21", lines[0])
        assertEquals("G90", lines[1])
        assertEquals("G94", lines[2])
        assertEquals("G0 Z3", lines[3])

        // Entscheidend: keine XY-Bewegung, bevor der Stift oben ist.
        val ersteXY = lines.indexOfFirst { it.contains("X") }
        val ersterHub = lines.indexOfFirst { it.startsWith("G0 Z") }
        assertTrue(ersterHub < ersteXY, "XY-Bewegung vor dem Anheben des Stifts")
    }

    @Test
    fun `senkt den Stift mit begrenztem Vorschub statt im Eilgang`() {
        val text = generateGCode(simpleStroke(), profile).text
        // Der Stift liegt lose auf; ein G0-Absenken wuerde auf das Papier schlagen.
        assertTrue(
            text.contains("G1 Z-1.5 F${profile.feedZMmMin}"),
            "Absenken muss G1 mit Z-Vorschub sein:\n$text",
        )
        assertFalse(text.contains("G0 Z-1.5"), "Absenken darf kein Eilgang sein")
    }

    @Test
    fun `jeder Strichzug wird gehoben abgeschlossen`() {
        val strokes = listOf(
            Polyline(listOf(Point(0f, 0f), Point(10f, 0f))),
            Polyline(listOf(Point(20f, 0f), Point(30f, 0f))),
        )
        val lines = generateGCode(strokes, profile).lines

        assertEquals(2, lines.count { it.startsWith("G1 Z-1.5") }, "Zwei Absenkungen erwartet")
        assertTrue(lines.count { it == "G0 Z3" } >= 3, "Jeder Zug muss mit Anheben enden")
        assertEquals("M2", lines.last())
    }

    @Test
    fun `Zeichenvorschub wird gesetzt und bleibt modal`() {
        val stroke = listOf(
            Polyline(listOf(Point(0f, 0f), Point(10f, 0f), Point(20f, 0f), Point(30f, 0f))),
        )
        val lines = generateGCode(stroke, profile).lines
        val drawMoves = lines.filter { it.startsWith("G1 X") }

        assertEquals(3, drawMoves.size)
        assertTrue(drawMoves[0].endsWith("F${profile.feedDrawMmMin}"), "Erster Zug ohne Vorschub")
        // Die folgenden Zeilen verzichten auf F - das spart Bytes im Sendepuffer.
        assertFalse(drawMoves[1].contains("F"), "Vorschub unnoetig wiederholt")
        assertFalse(drawMoves[2].contains("F"), "Vorschub unnoetig wiederholt")
    }

    @Test
    fun `kehrt am Ende zum Nullpunkt zurueck`() {
        val lines = generateGCode(simpleStroke(), profile).lines
        assertEquals("G0 X0 Y0", lines[lines.size - 2])
    }

    @Test
    fun `Papier-Offset verschiebt in Maschinenkoordinaten`() {
        val versetzt = profile.copy(paperOffsetXMm = 20f, paperOffsetYMm = 15f)
        val strokes = listOf(Polyline(listOf(Point(0f, 0f), Point(10f, 10f))))
            .toMachineCoordinates(versetzt)

        assertEquals(20f, strokes[0].points[0].x)
        assertEquals(15f, strokes[0].points[0].y)
    }

    @Test
    fun `Grenzpruefung erkennt Ueberschreitungen in alle Richtungen`() {
        val klein = profile.copy(workAreaXMm = 50f, workAreaYMm = 50f)

        assertTrue(checkBounds(listOf(Polyline(listOf(Point(0f, 0f), Point(40f, 40f)))), klein).ok)

        val rechts = checkBounds(listOf(Polyline(listOf(Point(0f, 0f), Point(60f, 10f)))), klein)
        assertFalse(rechts.ok)
        assertTrue(rechts.violations.single().contains("rechts"))

        val links = checkBounds(listOf(Polyline(listOf(Point(-5f, 0f), Point(10f, 10f)))), klein)
        assertFalse(links.ok)
        assertTrue(links.violations.single().contains("links"))

        val oben = checkBounds(listOf(Polyline(listOf(Point(0f, 0f), Point(10f, 70f)))), klein)
        assertFalse(oben.ok)
        assertTrue(oben.violations.single().contains("oben"))
    }

    @Test
    fun `Grenzpruefung meldet den Betrag der Ueberschreitung`() {
        val klein = profile.copy(workAreaXMm = 50f, workAreaYMm = 50f)
        val check = checkBounds(listOf(Polyline(listOf(Point(0f, 0f), Point(57.5f, 10f)))), klein)
        assertTrue(check.violations.single().contains("7.5"), "Betrag fehlt: ${check.violations}")
    }

    @Test
    fun `Pfadsortierung verkuerzt die Leerfahrten`() {
        val font = Fonts.load("sans")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val laid = layoutText(
            "Milch Brot Kaffee Butter Eier Mehl Zucker",
            TextStyle(fontId = "sans", sizeMm = 6f),
            frame,
            font,
        )

        val inSchreibrichtung = laid.orderedStrokes(profile.copy(naturalWriteOrder = true))
        val optimiert = laid.orderedStrokes(profile.copy(naturalWriteOrder = false))

        assertTrue(
            optimiert.travelLength() < inSchreibrichtung.travelLength(),
            "Sortierung brachte nichts: ${optimiert.travelLength()} mm " +
                "statt ${inSchreibrichtung.travelLength()} mm",
        )
    }

    @Test
    fun `in Schreibrichtung bleibt die Reihenfolge des Satzes erhalten`() {
        val font = Fonts.load("script-simplex")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val laid = layoutText("Hallo Welt", TextStyle("script-simplex", sizeMm = 8f), frame, font)

        val ordered = laid.orderedStrokes(profile.copy(naturalWriteOrder = true))

        // Das Layout setzt Zeichen fuer Zeichen von links nach rechts. In Schreibrichtung
        // darf daran nichts umsortiert werden - nur die Punkte je Zug duerfen ausgeduennt sein.
        assertEquals(laid.strokes.size, ordered.size)
        laid.strokes.zip(ordered).forEachIndexed { index, (original, gefahren) ->
            assertEquals(
                original.start, gefahren.start,
                "Zug $index beginnt woanders - es wurde umsortiert oder umgedreht",
            )
        }
    }

    @Test
    fun `in Schreibrichtung wandert der Stift ueber die Zeile nach rechts`() {
        val font = Fonts.load("sans")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val laid = layoutText("ABCDEFGH", TextStyle("sans", sizeMm = 8f), frame, font)
        val ordered = laid.orderedStrokes(profile.copy(naturalWriteOrder = true))

        // Buchstabenweise betrachtet muss es vorangehen: der Mittelpunkt jedes Zuges liegt
        // nie weit links von dem, was schon geschrieben wurde. Innerhalb eines Buchstabens
        // sind Ruecksprunge normal (Querbalken des A), ueber die Zeile hinweg nicht.
        val mitten = ordered.map { z -> z.points.map { it.x }.average() }
        val breite = laid.strokes.flatMap { it.points }.maxOf { it.x } -
            laid.strokes.flatMap { it.points }.minOf { it.x }
        val zeichenbreite = breite / 8f

        mitten.zipWithNext { a, b ->
            assertTrue(
                b > a - zeichenbreite,
                "Stift springt in der Zeile zurueck: von $a nach $b",
            )
        }
    }

    @Test
    fun `Zeilen bleiben trotz Sortierung in Lesereihenfolge`() {
        val font = Fonts.load("sans")
        val laid = layoutText("Eins\nZwei\nDrei", TextStyle("sans", sizeMm = 6f), Frame(105f, 148f), font)
        val ordered = laid.orderedStrokes(profile)

        // Sortiert wird nur INNERHALB einer Zeile; die Zeilenbloecke bleiben in Lesereihenfolge.
        // Sonst wuerde der Stift ueber noch feuchte Tinte fahren.
        // Vereinfachung und Sortierung erhalten die Anzahl der Zuege, also laesst sich Block
        // fuer Block vergleichen.
        assertEquals(laid.strokes.size, ordered.size, "Zuege sind verloren gegangen")

        var offset = 0
        var vorigeGrundlinie = Float.MAX_VALUE
        for (line in laid.lines) {
            val block = ordered.subList(offset, offset + line.strokes.size)
            offset += line.strokes.size

            val blockMinY = block.flatMap { it.points }.minOf { it.y }
            val zeileMinY = line.strokes.flatMap { it.points }.minOf { it.y }
            assertTrue(
                kotlin.math.abs(blockMinY - zeileMinY) < 0.5f,
                "Block gehoert nicht zu seiner Zeile '${line.text}'",
            )

            assertTrue(
                line.baselineYMm < vorigeGrundlinie,
                "Zeile '${line.text}' wird nicht von oben nach unten gezeichnet",
            )
            vorigeGrundlinie = line.baselineYMm
        }
        assertEquals(ordered.size, offset, "Nicht alle Zuege einer Zeile zugeordnet")
    }

    @Test
    fun `Vereinfachung reduziert die Zeilenzahl ohne die Form zu verlieren`() {
        val font = Fonts.load("script-simplex")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val laid = layoutText("Einkaufsliste", TextStyle("script-simplex", sizeMm = 8f), frame, font)

        val ohne = generateGCode(
            laid.orderedStrokes(profile.copy(simplifyToleranceMm = 0f)),
            profile,
        )
        val mit = generateGCode(laid.orderedStrokes(profile), profile)

        assertTrue(mit.lines.size <= ohne.lines.size)
        // Die Bounding-Box darf sich dabei nur im Rahmen der Toleranz aendern.
        val a = ohne.bounds!!
        val b = mit.bounds!!
        assertTrue(kotlin.math.abs(a.width - b.width) < 0.5f, "Form verzerrt: ${a.width} / ${b.width}")
    }

    @Test
    fun `Zeitschaetzung waechst mit der Textmenge`() {
        val font = Fonts.load("sans")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val style = TextStyle("sans", sizeMm = 5f)

        val kurz = layoutText("Hallo", style, frame, font).toPlotJob(profile)
        val lang = layoutText((1..10).joinToString("\n") { "Zeile $it Text" }, style, frame, font)
            .toPlotJob(profile)

        assertTrue(kurz.estimatedSeconds > 0f)
        assertTrue(lang.estimatedSeconds > kurz.estimatedSeconds)
    }

    @Test
    fun `erzeugter G-Code laesst sich zurueck zu genau dem Text simulieren`() {
        val font = Fonts.load("script-simplex")
        val frame = Frame(105f, 148f, Margins.all(10f))
        val laid = layoutText("Hallo Welt", TextStyle("script-simplex", sizeMm = 8f), frame, font)
        val job = laid.toPlotJob(profile)

        val segments = GCodeRenderer.simulate(job.lines, penDownZ)
        val zeichnend = segments.filter { it.drawing }

        assertTrue(zeichnend.isNotEmpty(), "Simulation fand keine Zeichenbewegung")

        // Der simulierte Bereich muss dem Layout entsprechen - das schliesst die ganze Kette,
        // von der Glyphe bis zur fertigen G-Code-Zeile.
        val simMinY = zeichnend.flatMap { listOf(it.y1, it.y2) }.min()
        val simMaxY = zeichnend.flatMap { listOf(it.y1, it.y2) }.max()
        val layoutMinY = laid.strokes.flatMap { it.points }.map { it.y }.min()
        val layoutMaxY = laid.strokes.flatMap { it.points }.map { it.y }.max()

        assertTrue(kotlin.math.abs(simMinY - layoutMinY) < 0.5f, "Y-Untergrenze weicht ab")
        assertTrue(kotlin.math.abs(simMaxY - layoutMaxY) < 0.5f, "Y-Obergrenze weicht ab")
    }

    @Test
    fun `Text steht aufrecht und nicht gespiegelt`() {
        val font = Fonts.load("sans")
        val frame = Frame(105f, 148f, Margins.all(10f))
        // 'L' ist asymmetrisch in beiden Achsen und deckt Spiegelfehler sofort auf:
        // der waagerechte Fuss liegt unten, der senkrechte Stamm links.
        val laid = layoutText("L", TextStyle("sans", sizeMm = 20f), frame, font)
        val job = laid.toPlotJob(profile)
        val pts = GCodeRenderer.simulate(job.lines, penDownZ)
            .filter { it.drawing }
            .flatMap { listOf(it.x1 to it.y1, it.x2 to it.y2) }

        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }

        // Auf halber Hoehe darf nur der linke Stamm liegen, unten aber der ganze Fuss.
        val mitte = (minY + maxY) / 2f
        val obenRechts = pts.any { it.second > mitte && it.first > (minX + maxX) / 2f }
        val untenRechts = pts.any { it.second < minY + (maxY - minY) * 0.1f && it.first > (minX + maxX) / 2f }

        assertFalse(obenRechts, "Oben rechts duerfte beim 'L' nichts liegen - Bild gespiegelt?")
        assertTrue(untenRechts, "Der Fuss des 'L' fehlt unten rechts - Bild gespiegelt?")
    }
}
