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

        /** Dieselben Glyphen wie in [FontMetrics], hier nur zur Bestimmung der Grundlinie. */
        private val BASELINE_REFERENCE = listOf('H'.code, 'A'.code, 'X'.code, 'x'.code)

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
