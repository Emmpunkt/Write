package de.emmpunkt.write.core.font

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline

/**
 * Ergaenzt eine Basisschrift um Zeichen, die sie nicht mitbringt.
 *
 * Die Hershey-Schriften enthalten nur ASCII 32..127. Fuer deutsche Notizen fehlen damit
 * ausgerechnet die Umlaute und das Eszett - ohne diese Schicht waere die App fuer ihren
 * eigentlichen Zweck unbrauchbar.
 *
 * Drei Mechanismen, in dieser Reihenfolge:
 *  1. [COMPOSITIONS] - Umlaute als Grundbuchstabe plus aufgesetztem Trema.
 *  2. [drawEszett] / [drawEuro] - handdefinierte Glyphen, parametrisch in Versalhoehen
 *     angegeben, damit sie sich jeder Basisschrift anpassen.
 *  3. [SUBSTITUTIONS] - typografische Zeichen auf ihre ASCII-Entsprechung abgebildet.
 *     Android-Tastaturen setzen Gedankenstriche und geschwungene Anfuehrungszeichen
 *     automatisch ein; ohne diese Abbildung entstuenden Luecken im geplotteten Text.
 */
class GlyphOverlayFont(private val base: StrokeFont) : StrokeFont {

    override val id: String get() = base.id
    override val displayName: String get() = base.displayName
    override val capHeightUnits: Float get() = base.capHeightUnits

    private val cache = HashMap<Int, Glyph?>()

    override val ascenderUnits: Float by lazy {
        val own = COMPOSITIONS.keys.mapNotNull { glyph(it) }
            .flatMap { g -> g.strokes.flatMap { it.points } }
            .maxOfOrNull { it.y }
        maxOf(base.ascenderUnits, own ?: base.ascenderUnits)
    }

    override val descenderUnits: Float get() = base.descenderUnits

    /**
     * Bewusst der Wert der Basisschrift: die aufgesetzten Umlautpunkte sollen den Zeilenabstand
     * nicht vergroessern, sondern wie ein Akzent in den Durchschuss ragen.
     */
    override val lineHeightUnits: Float get() = base.lineHeightUnits

    override fun glyph(codePoint: Int): Glyph? =
        cache.getOrPut(codePoint) { build(codePoint) }

    private fun build(codePoint: Int): Glyph? {
        // Vor der Basisschrift: deren Striche sind fuer Fliesstext zu lang und sitzen zu hoch.
        STRICHE[codePoint]?.let { return drawDash(it) }

        base.glyph(codePoint)?.let { return it }

        COMPOSITIONS[codePoint]?.let { baseChar ->
            return base.glyph(baseChar)?.let { withDiaeresis(it) }
        }

        when (codePoint) {
            ESZETT -> return drawEszett()
            EURO -> return drawEuro()
        }

        SUBSTITUTIONS[codePoint]?.let { replacement ->
            return STRICHE[replacement]?.let { drawDash(it) } ?: base.glyph(replacement)
        }

        return null
    }

    /**
     * Setzt zwei kurze senkrechte Striche ueber die Glyphe.
     *
     * Die Hoehe richtet sich nach der tatsaechlichen Oberkante des Grundbuchstabens, damit das
     * Trema sowohl ueber dem niedrigen 'a' als auch ueber dem hohen 'A' sitzt und in beiden
     * Faellen denselben Abstand haelt.
     */
    private fun withDiaeresis(baseGlyph: Glyph): Glyph {
        val points = baseGlyph.strokes.flatMap { it.points }
        if (points.isEmpty()) return baseGlyph

        val c = capHeightUnits
        val top = points.maxOf { it.y }
        val centerX = (points.minOf { it.x } + points.maxOf { it.x }) / 2f

        val gap = DIAERESIS_GAP * c
        val height = DIAERESIS_HEIGHT * c
        val spread = DIAERESIS_SPREAD * c

        val bottomY = top + gap
        val topY = bottomY + height

        val dots = listOf(centerX - spread, centerX + spread).map { x ->
            Polyline(listOf(Point(x, bottomY), Point(x, topY)))
        }

        return Glyph(strokes = baseGlyph.strokes + dots, advance = baseGlyph.advance)
    }

    /**
     * Eszett als ein durchgehender Zug: Aufstrich, oberer Bogen, Taille, unterer Bogen mit Haken.
     * Ein einziger Strichzug bedeutet auch nur einen Pen-Down-Zyklus - und passt zum
     * Schreibschrift-Charakter der Script-Varianten.
     */
    private fun drawEszett(): Glyph {
        val c = capHeightUnits
        val shape = listOf(
            0.10f to 0.00f, 0.10f to 0.70f, 0.13f to 0.90f, 0.22f to 1.00f,
            0.36f to 1.02f, 0.48f to 0.96f, 0.52f to 0.84f, 0.48f to 0.72f,
            0.38f to 0.64f, 0.30f to 0.58f, 0.34f to 0.52f, 0.46f to 0.48f,
            0.58f to 0.40f, 0.62f to 0.28f, 0.58f to 0.14f, 0.46f to 0.04f,
            0.32f to 0.02f, 0.22f to 0.06f,
        )
        val stroke = Polyline(shape.map { (x, y) -> Point(x * c, y * c) })
        return Glyph(
            strokes = listOf(stroke),
            advance = base.glyph('b'.code)?.advance ?: (0.75f * c),
        )
    }

    /**
     * Waagerechter Strich in typografischer Laenge und Hoehe.
     *
     * Die Hershey-Schriften bringen einen Bindestrich von 0,86 Versalhoehen mit - fast doppelt
     * so breit wie ein Kleinbuchstabe - und setzen ihn auf die Oberkante der Kleinbuchstaben
     * statt auf halbe Hoehe. Im Fliesstext faellt beides unangenehm auf.
     *
     * @param laengeAnteil Laenge als Anteil der Versalhoehe. Bindestrich kurz, Gedankenstrich
     *   laenger - im Deutschen sind das zwei verschiedene Zeichen mit verschiedener Laenge.
     */
    private fun drawDash(laengeAnteil: Float): Glyph {
        val c = capHeightUnits
        // Auf halber x-Hoehe, damit der Strich optisch in der Mitte der Kleinbuchstaben sitzt.
        val xHoehe = base.glyph('x'.code)
            ?.strokes?.flatMap { it.points }?.maxOfOrNull { it.y }
            ?: (c * 0.5f)
        val y = xHoehe * 0.5f

        val laenge = laengeAnteil * c
        val seite = DASH_SIDE_BEARING * c
        return Glyph(
            strokes = listOf(Polyline(listOf(Point(seite, y), Point(seite + laenge, y)))),
            advance = laenge + 2f * seite,
        )
    }

    /** Euro-Zeichen: C-Bogen mit zwei Querstrichen. */
    private fun drawEuro(): Glyph {
        val c = capHeightUnits
        val arc = listOf(
            0.62f to 0.62f, 0.50f to 0.70f, 0.34f to 0.70f, 0.22f to 0.60f,
            0.16f to 0.44f, 0.16f to 0.26f, 0.22f to 0.10f, 0.34f to 0.00f,
            0.50f to 0.00f, 0.62f to 0.08f,
        )
        val strokes = listOf(
            Polyline(arc.map { (x, y) -> Point(x * c, y * c) }),
            Polyline(listOf(Point(0.02f * c, 0.42f * c), Point(0.50f * c, 0.42f * c))),
            Polyline(listOf(Point(0.02f * c, 0.28f * c), Point(0.50f * c, 0.28f * c))),
        )
        return Glyph(strokes = strokes, advance = base.glyph('C'.code)?.advance ?: (0.75f * c))
    }

    companion object {
        private const val ESZETT = 0x00DF
        private const val EURO = 0x20AC

        /**
         * Seitlicher Abstand des Strichs, als Anteil der Versalhoehe.
         *
         * Der Strich soll nicht an den Nachbarbuchstaben stossen - in der Schreibschrift
         * laufen deren Aus- und Anstriche ohnehin weit heran.
         */
        private const val DASH_SIDE_BEARING = 0.14f

        /**
         * Waagerechte Striche und ihre Laenge in Versalhoehen.
         * Bindestrich kurz, Halbgeviertstrich deutlich laenger, Geviertstrich am laengsten.
         */
        private val STRICHE: Map<Int, Float> = mapOf(
            '-'.code to 0.30f,   // Bindestrich
            0x2010 to 0.30f,     // typografischer Bindestrich
            0x2013 to 0.50f,     // Halbgeviertstrich (Gedankenstrich)
            0x2014 to 0.70f,     // Geviertstrich
            0x2212 to 0.42f,     // Minus
        )

        /** Anteile der Versalhoehe. Empirisch so gewaehlt, dass das Trema nicht am Buchstaben klebt. */
        private const val DIAERESIS_GAP = 0.14f
        private const val DIAERESIS_HEIGHT = 0.14f
        private const val DIAERESIS_SPREAD = 0.15f

        /** Umlaut-Codepoint -> Grundbuchstabe, auf den das Trema gesetzt wird. */
        private val COMPOSITIONS: Map<Int, Int> = mapOf(
            0x00E4 to 'a'.code, // ae
            0x00F6 to 'o'.code, // oe
            0x00FC to 'u'.code, // ue
            0x00C4 to 'A'.code,
            0x00D6 to 'O'.code,
            0x00DC to 'U'.code,
        )

        /** Typografische Zeichen -> darstellbare ASCII-Entsprechung. */
        private val SUBSTITUTIONS: Map<Int, Int> = mapOf(
            0x2018 to '\''.code, // '
            0x2019 to '\''.code, // '
            0x201A to ','.code,  // ,
            0x201C to '"'.code,  // "
            0x201D to '"'.code,  // "
            0x201E to '"'.code,  // "
            0x00A0 to ' '.code,  // geschuetztes Leerzeichen
            0x00B4 to '\''.code, // Akut
            0x2022 to '.'.code,  // Aufzaehlungspunkt
        )
    }
}
