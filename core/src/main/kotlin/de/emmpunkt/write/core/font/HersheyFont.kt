package de.emmpunkt.write.core.font

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline

/**
 * Schrift im JHF-Format (Hershey-Vektorschriften).
 *
 * Format laut der Originalnotiz von James Hurt (siehe fonts/HERSHEY-NOTICE.txt):
 *   Spalte 0..4   Glyphennummer
 *   Spalte 5..7   Anzahl Koordinatenpaare, INKLUSIVE des folgenden Bearing-Paars
 *   Spalte 8      linke Bearing-Position
 *   Spalte 9      rechte Bearing-Position
 *   ab Spalte 10  die Punkte, je zwei Zeichen
 * Jeder Wert ist relativ zu 'R' kodiert: wert = zeichen - 'R'.
 * Das Paar " R" bedeutet Pen-Up und beginnt einen neuen Strichzug.
 *
 * Beispiel aus der Notiz: "8 9MWOMOV RUMUV ROQUQ" ergibt ein 'H'.
 *
 * Die JHF-Y-Achse zeigt nach unten und wird beim Laden gespiegelt; ausserdem wird der Ursprung
 * von der Glyphenmitte auf die Grundlinie am linken Rand verschoben. Danach gilt fuer alle
 * Glyphen die Konvention aus [Glyph].
 */
class HersheyFont private constructor(
    override val id: String,
    override val displayName: String,
    override val capHeightUnits: Float,
    override val ascenderUnits: Float,
    override val descenderUnits: Float,
    override val lineHeightUnits: Float,
    private val glyphs: Map<Int, Glyph>,
) : StrokeFont {

    override fun glyph(codePoint: Int): Glyph? = glyphs[codePoint]

    companion object {
        /**
         * Erstes Zeichen einer JHF-Datei. Die Dateien enthalten genau die 96 Zeichen ab
         * Leerzeichen in ASCII-Reihenfolge, Zeile n entspricht also Codepoint 32 + n.
         */
        private const val FIRST_CODE_POINT = 32

        /**
         * Referenzglyphen zur Bestimmung von Grundlinie und Versalhoehe, in dieser Reihenfolge.
         * Aus den Daten abgeleitet statt fest verdrahtet, weil die Hershey-Schriften
         * unterschiedliche Zeichenhoehen benutzen.
         */
        private val REFERENCE_GLYPHS = listOf('H'.code, 'A'.code, 'X'.code, 'x'.code)

        /** Buchstaben mit Oberlaenge - bestimmen die obere Haelfte der Zeilenhoehe. */
        private val ASCENDER_GLYPHS = listOf('h'.code, 'l'.code, 'b'.code, 'd'.code, 'k'.code)

        /** Buchstaben mit Unterlaenge - bestimmen die untere Haelfte der Zeilenhoehe. */
        private val DESCENDER_GLYPHS = listOf('g'.code, 'p'.code, 'q'.code, 'y'.code, 'j'.code)

        /** Groesstes bzw. kleinstes Y ueber den angegebenen Glyphen, oder null wenn keine da ist. */
        private fun extremeY(glyphs: Map<Int, Glyph>, codePoints: List<Int>, max: Boolean): Float? {
            val ys = codePoints.mapNotNull { glyphs[it] }
                .flatMap { g -> g.strokes.flatMap { it.points } }
                .map { it.y }
            if (ys.isEmpty()) return null
            return if (max) ys.max() else ys.min()
        }

        fun parse(id: String, displayName: String, content: String): HersheyFont {
            val rawGlyphs = parseLines(content.lines())

            val reference = REFERENCE_GLYPHS.firstNotNullOfOrNull { rawGlyphs[it] }
                ?: error("Schrift '$id' enthaelt keine der Referenzglyphen H/A/X/x")
            val referencePoints = reference.strokes.flatMap { it.points }
            require(referencePoints.isNotEmpty()) { "Referenzglyphe von '$id' ist leer" }

            // In JHF-Koordinaten (Y nach unten) ist die Grundlinie die UNTERkante der
            // Referenzglyphe, also ihr groesstes Y.
            val baseline = referencePoints.maxOf { it.y }
            val capHeight = baseline - referencePoints.minOf { it.y }
            require(capHeight > 0f) { "Versalhoehe von '$id' ist nicht positiv" }

            val glyphs = rawGlyphs.mapValues { (_, raw) -> toBaselineOrigin(raw, baseline) }

            val allPoints = glyphs.values.flatMap { g -> g.strokes.flatMap { it.points } }
            val ascender = allPoints.maxOfOrNull { it.y } ?: capHeight
            val descender = allPoints.minOfOrNull { it.y } ?: 0f

            // Zeilenhoehe aus typischen Buchstaben statt aus dem Maximum ueber alle Glyphen:
            // in den Hershey-Saetzen ragen Klammern und geschweifte Zeichen weit ueber jede
            // Oberlaenge hinaus. Wuerde man danach gehen, stuenden alle Zeilen zu weit
            // auseinander, obwohl solche Zeichen im Text kaum vorkommen.
            val typoAscender = extremeY(glyphs, ASCENDER_GLYPHS, max = true) ?: capHeight
            val typoDescender = extremeY(glyphs, DESCENDER_GLYPHS, max = false) ?: 0f

            return HersheyFont(
                id = id,
                displayName = displayName,
                capHeightUnits = capHeight,
                ascenderUnits = ascender,
                descenderUnits = descender,
                lineHeightUnits = typoAscender - typoDescender,
                glyphs = glyphs,
            )
        }

        /**
         * Verschiebt eine rohe Glyphe auf die Konvention aus [Glyph]: Y nach oben spiegeln,
         * Ursprung auf die Grundlinie legen. Die X-Verschiebung um das linke Bearing ist beim
         * Parsen bereits erfolgt.
         */
        private fun toBaselineOrigin(raw: Glyph, baseline: Float): Glyph =
            Glyph(
                strokes = raw.strokes.map { line -> line.map { Point(it.x, baseline - it.y) } },
                advance = raw.advance,
            )

        private fun parseLines(lines: List<String>): Map<Int, Glyph> {
            val glyphs = LinkedHashMap<Int, Glyph>()
            var index = 0
            var codePoint = FIRST_CODE_POINT

            while (index < lines.size) {
                val line = lines[index]
                index++
                if (line.isBlank()) continue

                require(line.length >= 10) { "JHF-Zeile zu kurz: '$line'" }
                val pairCount = line.substring(5, 8).trim().toIntOrNull()
                    ?: error("Unlesbare Vertex-Anzahl in JHF-Zeile: '$line'")

                // Lange Glyphen duerfen ueber mehrere Zeilen laufen. Unsere Schriften nutzen das
                // nicht, andere Hershey-Datensaetze schon - deshalb hier gleich mitbehandelt.
                var body = line.substring(8)
                while (body.length < pairCount * 2 && index < lines.size) {
                    body += lines[index]
                    index++
                }

                glyphs[codePoint] = parseGlyph(body, pairCount)
                codePoint++
            }
            return glyphs
        }

        private fun parseGlyph(body: String, pairCount: Int): Glyph {
            val usablePairs = minOf(pairCount, body.length / 2)
            require(usablePairs >= 1) { "Glyphe ohne Bearing-Paar" }

            val left = decode(body[0])
            val right = decode(body[1])

            val strokes = ArrayList<Polyline>()
            var current = ArrayList<Point>()

            fun flush() {
                when {
                    current.size >= 2 -> strokes += Polyline(current.toList())
                    // Ein einzelner Punkt ist gewollt (Punkt, i-Tuepfelchen): der Stift geht
                    // herunter und sofort wieder hoch. Als Polyline braucht das zwei Punkte.
                    current.size == 1 -> strokes += Polyline(listOf(current[0], current[0]))
                }
                current = ArrayList()
            }

            for (pair in 1 until usablePairs) {
                val a = body[pair * 2]
                val b = body[pair * 2 + 1]
                if (a == ' ' && b == 'R') {
                    flush()
                } else {
                    // X um das linke Bearing verschieben: Ursprung an den linken Glyphenrand.
                    current += Point(decode(a) - left, decode(b))
                }
            }
            flush()

            return Glyph(strokes = strokes, advance = right - left)
        }

        private fun decode(c: Char): Float = (c.code - 'R'.code).toFloat()
    }
}
