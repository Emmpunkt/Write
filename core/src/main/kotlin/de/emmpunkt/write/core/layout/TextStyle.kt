package de.emmpunkt.write.core.layout

/** Horizontale Ausrichtung einer Textzeile im Rahmen. */
enum class Align { LEFT, CENTER, RIGHT }

/**
 * Wie der Text im Rahmen steht - in Vierteldrehungen gegen den Uhrzeigersinn.
 *
 * Der Anlass ist die Maschine: Ein A6-Blatt hoch (105 x 148 mm) passt nicht auf den Tisch
 * (155 x 105 mm). Wer hochkant schreiben will, legt das Blatt quer und dreht den Text.
 *
 * Bewusst nur diese vier Stufen und kein freier Winkel: Das gedrehte Rechteck bleibt
 * achsparallel, und damit bleibt die Frage "passt das noch aufs Blatt?" exakt beantwortbar.
 *
 * Gedreht wird der SATZ im Rahmen, nicht der Rahmen. Bei [GRAD_90] und [GRAD_270] wird deshalb
 * auf einer Flaeche mit vertauschten Massen gesetzt und das Ergebnis hineingedreht.
 */
enum class Drehung(val grad: Int) {
    GRAD_0(0),
    GRAD_90(90),
    GRAD_180(180),
    GRAD_270(270),
    ;

    /** Ob Breite und Hoehe der Satzflaeche zu tauschen sind. */
    val vertauscht: Boolean get() = this == GRAD_90 || this == GRAD_270
}

/** Raender des beschreibbaren Bereichs, in Millimetern. */
data class Margins(
    val left: Float = 10f,
    val top: Float = 10f,
    val right: Float = 10f,
    val bottom: Float = 10f,
) {
    companion object {
        fun all(mm: Float) = Margins(mm, mm, mm, mm)
    }
}

/**
 * Der beschreibbare Rahmen: das Blatt und seine Raender, in Millimetern.
 *
 * Ursprung ist die linke UNTERE Ecke, Y zeigt nach oben - dieselbe Konvention wie im G-Code,
 * damit zwischen Layout und Maschine keine Achsenspiegelung mehr noetig ist.
 */
data class Frame(
    val widthMm: Float,
    val heightMm: Float,
    val margins: Margins = Margins(),
) {
    val usableWidthMm: Float get() = widthMm - margins.left - margins.right
    val usableHeightMm: Float get() = heightMm - margins.top - margins.bottom

    init {
        require(usableWidthMm > 0f) { "Rahmenbreite abzueglich Raender ist nicht positiv" }
        require(usableHeightMm > 0f) { "Rahmenhoehe abzueglich Raender ist nicht positiv" }
    }
}

/**
 * Schriftschnitt fuer einen Textblock.
 *
 * @param sizeMm Versalhoehe in Millimetern - die Hoehe eines Grossbuchstabens. Bewusst diese
 *   Groesse und nicht die Kegelhoehe, weil sie am Papier direkt nachmessbar ist.
 * @param lineSpacing Faktor auf die natuerliche Zeilenhoehe der Schrift (Ober- bis Unterlaenge).
 * @param letterSpacing relativer Zuschlag auf jede Vorschubbreite; 0 = Normalwert der Schrift.
 * @param wordSpacing relativer Zuschlag auf die Breite des Leerzeichens. Die Vorgabe ist
 *   negativ: das Leerzeichen der Hershey-Schriften ist mit 0,76 Versalhoehen fuer Fliesstext
 *   ungewoehnlich breit und liesse Woerter auseinanderfallen.
 * @param slantDeg kuenstliche Kursivierung in Grad, Scherung um die Grundlinie.
 */
data class TextStyle(
    val fontId: String,
    val sizeMm: Float = 6f,
    val align: Align = Align.LEFT,
    val lineSpacing: Float = 1.15f,
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = -0.3f,
    val slantDeg: Float = 0f,
) {
    init {
        require(sizeMm > 0f) { "Schriftgroesse muss positiv sein" }
        require(lineSpacing > 0f) { "Zeilenabstand muss positiv sein" }
    }
}
