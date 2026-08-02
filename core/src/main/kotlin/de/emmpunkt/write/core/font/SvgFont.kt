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
