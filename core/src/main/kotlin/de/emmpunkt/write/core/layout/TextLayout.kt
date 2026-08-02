package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.StrokeFont
import de.emmpunkt.write.core.font.unsupportedCharacters
import de.emmpunkt.write.core.geometry.Polyline

/** Eine gesetzte Zeile. [baselineYMm] ist der Abstand der Grundlinie zur Blattunterkante. */
data class LaidOutLine(
    val text: String,
    val widthMm: Float,
    val baselineYMm: Float,
    val strokes: List<Polyline>,
)

/**
 * Das Ergebnis des Textsatzes, in Blatt-Koordinaten (Ursprung linke untere Ecke, Y nach oben).
 *
 * [strokes] ist die einzige Wahrheit: die Vorschau zeichnet genau diese Zuege, und der
 * G-Code-Generator wandelt genau diese Zuege um. Damit kann die Vorschau gar nicht von dem
 * abweichen, was der Stift faehrt.
 */
data class LaidOutText(
    val lines: List<LaidOutLine>,
    val strokes: List<Polyline>,
    /** Benoetigte Hoehe von der obersten Oberlaenge bis zur untersten Unterlaenge. */
    val requiredHeightMm: Float,
    /** true, wenn der Text hoeher ist als der nutzbare Bereich - die App warnt dann. */
    val overflow: Boolean,
    /** Zeichen, die die Schrift nicht darstellen kann. */
    val unsupported: Set<Int>,
    /**
     * Woerter, die auch allein nicht in eine Zeile passen und deshalb hart getrennt werden
     * mussten - also mitten im Wort, ohne Trennstrich.
     *
     * Die App zeigt sie an, damit der Nutzer selbst entscheiden kann: einen Bindestrich an
     * einer sinnvollen Stelle setzen, die Schrift verkleinern oder den Rand verringern.
     * Eine automatische Silbentrennung waere hier fehl am Platz - sie braucht Sprachwissen
     * und traefe regelmaessig daneben.
     */
    val overlongWords: Set<String>,
)

/**
 * Setzt [text] in den [frame].
 *
 * Umbrochen wird an Wortgrenzen; ein Wort, das allein nicht in die Zeile passt, wird hart
 * getrennt, damit nichts ueber den Rand laeuft. Harte Zeilenumbrueche im Text bleiben erhalten.
 *
 * Ueberlauf nach unten wird gemeldet, nicht abgeschnitten - die Entscheidung, ob der Text
 * gekuerzt oder die Schrift verkleinert wird, trifft der Nutzer.
 */
fun layoutText(
    text: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
): LaidOutText {
    val metrics = Metrics(font, style)
    val usableWidth = frame.usableWidthMm

    val overlong = LinkedHashSet<String>()
    val brokenLines = text
        .split('\n')
        .flatMap { paragraph -> wrapParagraph(paragraph, metrics, usableWidth, overlong) }

    // Erste Grundlinie so tief, dass die hoechste Oberlaenge gerade unter den oberen Rand passt.
    val firstBaseline = frame.heightMm - frame.margins.top - metrics.ascenderMm
    val lines = ArrayList<LaidOutLine>(brokenLines.size)
    val allStrokes = ArrayList<Polyline>()

    brokenLines.forEachIndexed { index, lineText ->
        val baselineY = firstBaseline - index * metrics.lineAdvanceMm
        val width = metrics.widthOf(lineText)
        val startX = when (style.align) {
            Align.LEFT -> frame.margins.left
            Align.CENTER -> frame.margins.left + (usableWidth - width) / 2f
            Align.RIGHT -> frame.margins.left + (usableWidth - width)
        }

        val strokes = einlaufKorrigiert(metrics.renderLine(lineText, startX, baselineY), frame.margins.left)
        lines += LaidOutLine(lineText, width, baselineY, strokes)
        allStrokes += strokes
    }

    val requiredHeight = if (brokenLines.isEmpty()) {
        0f
    } else {
        metrics.ascenderMm + (brokenLines.size - 1) * metrics.lineAdvanceMm - metrics.descenderMm
    }

    return LaidOutText(
        lines = lines,
        strokes = allStrokes,
        requiredHeightMm = requiredHeight,
        overflow = requiredHeight > frame.usableHeightMm + OVERFLOW_TOLERANCE_MM,
        unsupported = font.unsupportedCharacters(text),
        overlongWords = overlong,
    )
}

/**
 * Ein Zehntelmillimeter Nachsicht bei der Ueberlaufpruefung: sonst meldet ein Text, der
 * rechnerisch exakt passt, wegen Float-Rundung faelschlich einen Ueberlauf.
 */
private const val OVERFLOW_TOLERANCE_MM = 0.1f

/**
 * Schiebt eine fertig gesetzte Zeile nach rechts, falls sie links ueber [randX] hinausragt.
 *
 * Die Schreibschriften bringen in ihre Anfangsglyphen einen Einlaufstrich mit - den
 * Verbindungsstrich zum vorherigen Buchstaben, als negativer x-Wert kodiert. Am Zeilenanfang
 * gibt es diesen Vorgaenger nicht, der Strich ragt also ueber den Rand. Verschoben wird immer
 * die GANZE Zeile als ein Zug, nie einzelne Glyphen: sonst risse die Verbindung zwischen den
 * Buchstaben genau dort, wo dieser Ausgleich noetig ist.
 */
private fun einlaufKorrigiert(strokes: List<Polyline>, randX: Float): List<Polyline> {
    val linkesteX = strokes.flatMap { it.points }.minOfOrNull { it.x } ?: return strokes
    val ueberhang = randX - linkesteX
    return if (ueberhang > 0f) strokes.map { it.translate(ueberhang, 0f) } else strokes
}

private fun wrapParagraph(
    paragraph: String,
    metrics: Metrics,
    usableWidth: Float,
    overlong: MutableSet<String>,
): List<String> {
    if (paragraph.isEmpty()) return listOf("")

    val result = ArrayList<String>()
    var current = StringBuilder()

    fun flush() {
        result += current.toString()
        current = StringBuilder()
    }

    fun anhaengen(teil: String, mitLeerzeichen: Boolean) {
        val kandidat = if (current.isEmpty()) teil else current.toString() + (if (mitLeerzeichen) " " else "") + teil
        if (metrics.widthOf(kandidat) <= usableWidth) {
            current = StringBuilder(kandidat)
        } else {
            if (current.isNotEmpty()) flush()
            current = StringBuilder(teil)
        }
    }

    for (word in paragraph.split(' ')) {
        val ganzes = if (current.isEmpty()) word else "$current $word"
        when {
            // Das Wort passt in die laufende Zeile.
            metrics.widthOf(ganzes) <= usableWidth -> current = StringBuilder(ganzes)

            // Es passt allein in eine neue Zeile.
            metrics.widthOf(word) <= usableWidth -> {
                if (current.isNotEmpty()) flush()
                current = StringBuilder(word)
            }

            else -> {
                // Zu breit fuer eine ganze Zeile. Enthaelt das Wort Bindestriche, sind das
                // erlaubte Trennstellen - dort darf die Zeile enden, der Strich bleibt stehen.
                // Jedes Segment wird einzeln betrachtet: ein einziges zu langes Teilstueck
                // soll nicht die brauchbaren Trennstellen davor und danach entwerten.
                val teile = trennsegmente(word)
                teile.forEachIndexed { index, teil ->
                    if (metrics.widthOf(teil) <= usableWidth) {
                        anhaengen(teil, mitLeerzeichen = index == 0)
                    } else {
                        // Letzter Ausweg: hart trennen, damit nichts ueber den Rand laeuft.
                        // Gemeldet wird das ganze Wort - der Nutzer sucht die Trennstelle
                        // schliesslich im Wort, nicht im Bruchstueck.
                        overlong += word
                        if (current.isNotEmpty()) flush()
                        val stuecke = breakLongWord(teil, metrics, usableWidth)
                        stuecke.dropLast(1).forEach { result += it }
                        current = StringBuilder(stuecke.last())
                    }
                }
            }
        }
    }
    flush()
    return result
}

/**
 * Zerlegt ein Wort an seinen Bindestrichen in Teile, die je eine Zeile beenden duerfen.
 *
 * Der Strich bleibt am Ende seines Teils stehen: aus "Muster-Beispiel" wird
 * ["Muster-", "Beispiel"]. Genau so setzt man einen Bindestrich, wenn man von Hand trennt.
 */
private fun trennsegmente(word: String): List<String> {
    val teile = ArrayList<String>()
    val current = StringBuilder()
    for (ch in word) {
        current.append(ch)
        // Ein Strich am Wortanfang ist kein Trennzeichen, sondern ein Vorzeichen o. Ae.
        if ((ch == '-' || ch == '\u2010') && current.length > 1) {
            teile += current.toString()
            current.setLength(0)
        }
    }
    if (current.isNotEmpty()) teile += current.toString()
    return teile
}

/**
 * Zerlegt ein Wort zeichenweise, wenn es auch allein nicht in eine Zeile passt.
 *
 * Der letzte Ausweg: ohne ihn liefe der Text ueber den Blattrand hinaus. Das Wort wird
 * gleichzeitig in [LaidOutText.overlongWords] gemeldet, damit der Nutzer eingreifen kann.
 */
private fun breakLongWord(word: String, metrics: Metrics, usableWidth: Float): List<String> {
    val pieces = ArrayList<String>()
    var current = StringBuilder()
    for (ch in word) {
        val candidate = current.toString() + ch
        if (current.isNotEmpty() && metrics.widthOf(candidate) > usableWidth) {
            pieces += current.toString()
            current = StringBuilder().append(ch)
        } else {
            current = StringBuilder(candidate)
        }
    }
    pieces += current.toString()
    return pieces
}

/**
 * Rechnet Font-Einheiten in Millimeter um und misst bzw. zeichnet Zeilen.
 *
 * Die Umrechnung passiert ausschliesslich hier, damit Messung und Darstellung garantiert
 * denselben Massstab benutzen - sonst wuerde der Umbruch nicht zum gezeichneten Text passen.
 */
private class Metrics(private val font: StrokeFont, private val style: TextStyle) {

    val scale: Float = style.sizeMm / font.capHeightUnits
    val ascenderMm: Float = font.ascenderUnits * scale
    val descenderMm: Float = font.descenderUnits * scale
    val lineAdvanceMm: Float = font.lineHeightUnits * scale * style.lineSpacing

    fun advanceOf(codePoint: Int): Float {
        val glyph = font.glyph(codePoint) ?: return 0f
        val factor = if (codePoint == ' '.code) style.wordSpacing else style.letterSpacing
        return glyph.advance * scale * (1f + factor)
    }

    fun widthOf(text: String): Float =
        text.codePoints().toArray().sumOf { advanceOf(it).toDouble() }.toFloat()

    fun renderLine(text: String, startX: Float, baselineY: Float): List<Polyline> {
        val strokes = ArrayList<Polyline>()
        var penX = startX
        for (cp in text.codePoints()) {
            val glyph = font.glyph(cp)
            if (glyph != null) {
                glyph.strokes.forEach { stroke ->
                    // Reihenfolge ist wesentlich: erst skalieren, dann scheren (die Scherung
                    // wirkt um y = 0, also um die Grundlinie), erst danach verschieben.
                    strokes += stroke
                        .scale(scale, scale)
                        .slant(style.slantDeg)
                        .translate(penX, baselineY)
                }
            }
            penX += advanceOf(cp)
        }
        return strokes
    }
}
