package de.emmpunkt.write.core.font

import de.emmpunkt.write.core.geometry.Polyline

/**
 * Eine Glyphe in Font-Einheiten.
 *
 * Koordinatensystem: Ursprung auf der Grundlinie am linken Rand der Glyphe, Y zeigt nach oben.
 * Ober- und Unterlaengen liegen also bei positivem bzw. negativem Y.
 *
 * @param strokes die Strichzuege; leer bei Leerzeichen.
 * @param advance Vorschubbreite - um so viel rueckt der Schreibpunkt weiter.
 */
data class Glyph(
    val strokes: List<Polyline>,
    val advance: Float,
)

/**
 * Eine Single-Line-Schrift: liefert Mittellinien, keine Umrisse.
 *
 * Das ist der entscheidende Unterschied zu TTF/OTF. Wuerde man Umrisse plotten, kaeme jeder
 * Buchstabe hohl und doppelstrichig heraus statt geschrieben.
 *
 * Implementierungen liefern Font-Einheiten; die Umrechnung nach Millimetern macht das Layout
 * ueber [capHeightUnits].
 */
interface StrokeFont {
    /** Stabiler Bezeichner, wird in Notizen gespeichert. */
    val id: String

    /** Anzeigename fuer die Font-Auswahl in der App. */
    val displayName: String

    /** Hoehe eines Grossbuchstabens in Font-Einheiten. Bezugsgroesse fuer "Schriftgroesse in mm". */
    val capHeightUnits: Float

    /**
     * Hoechster Punkt ueber dem Zeichenvorrat, den der Nutzer im Editor tatsaechlich eintippen
     * kann (ASCII 32-126, deutsche Sonderzeichen, Gedankenstriche). Bestimmt, wie weit die
     * erste Zeile vom oberen Rand wegbleiben muss, damit nichts herausragt.
     *
     * Bewusst nicht ueber alle Glyphen der Schriftdatei: manche mitgelieferten Schriften
     * enthalten weit mehr Zeichen als der Nutzer erreichen kann (Latin-Extended-
     * Akzentbuchstaben), und die ragen zum Teil deutlich ueber die Versalhoehe hinaus. Wuerden
     * sie mitgezaehlt, hielte "Einpassen" bei jeder Notiz in diesen Schriften unnoetig viel
     * Kopfabstand frei - unabhaengig davon, ob ein solches Zeichen ueberhaupt vorkommt.
     */
    val ascenderUnits: Float

    /** Tiefste Unterlaenge (negativ) ueber demselben erreichbaren Zeichenvorrat, s. [ascenderUnits]. */
    val descenderUnits: Float

    /**
     * Natuerliche Zeilenhoehe der Schrift, Bezugsgroesse fuer den Zeilenabstand.
     *
     * Bewusst getrennt von [ascenderUnits]: Akzente und Umlautpunkte ragen typografisch in den
     * Durchschuss hinein und duerfen den Zeilenabstand NICHT vergroessern - sonst stuenden auch
     * Zeilen ohne Umlaute unnoetig weit auseinander.
     */
    val lineHeightUnits: Float

    /** Die Glyphe zum Codepoint, oder null wenn die Schrift das Zeichen nicht kennt. */
    fun glyph(codePoint: Int): Glyph?

    fun has(codePoint: Int): Boolean = glyph(codePoint) != null
}

/**
 * Alle Zeichen eines Textes, die die Schrift nicht darstellen kann.
 *
 * Die App prueft das im Editor, damit fehlende Zeichen auffallen BEVOR gesendet wird - nicht
 * erst als Luecke auf dem Papier.
 */
fun StrokeFont.unsupportedCharacters(text: String): Set<Int> =
    text.codePoints().toArray()
        .filter { it != '\n'.code && it != '\r'.code && it != '\t'.code }
        .filterNot { has(it) }
        .toSet()
