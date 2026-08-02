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
