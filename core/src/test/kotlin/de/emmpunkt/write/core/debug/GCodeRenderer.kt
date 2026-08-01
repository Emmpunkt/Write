package de.emmpunkt.write.core.debug

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Zeichnet ERZEUGTEN G-Code zurueck in ein Bild.
 *
 * Der Sinn: die Vorschau in der App rendert dieselben Strichzuege, aus denen der G-Code
 * entsteht - sie kann einen Fehler in der G-Code-Erzeugung also gar nicht zeigen. Dieser
 * Renderer geht den umgekehrten Weg und interpretiert die fertigen Zeilen so, wie es die
 * Maschine tut. Damit fallen Spiegelungen, Massstabsfehler, vergessene Pen-Up-Zeilen und
 * falsche Dezimaltrennzeichen auf, bevor Papier und Stift im Spiel sind.
 */
object GCodeRenderer {

    private val MOVE = Regex("^G0*([013])\\b", RegexOption.IGNORE_CASE)

    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val drawing: Boolean)

    /**
     * Interpretiert die G-Code-Zeilen und liefert die gefahrenen Segmente.
     * [penDownZ] ist die Schwelle, unterhalb derer der Stift als aufliegend gilt.
     */
    fun simulate(lines: List<String>, penDownZ: Float): List<Segment> {
        var x = 0f
        var y = 0f
        var z = Float.MAX_VALUE
        val segments = ArrayList<Segment>()

        for (raw in lines) {
            val line = raw.substringBefore(';').trim()
            if (line.isEmpty() || !MOVE.containsMatchIn(line)) continue

            val nx = word(line, 'X') ?: x
            val ny = word(line, 'Y') ?: y
            val nz = word(line, 'Z') ?: z

            val moved = nx != x || ny != y
            if (moved) {
                // Massgeblich ist die Stiftlage waehrend der Bewegung, also vor einem
                // Z-Wechsel in derselben Zeile.
                segments += Segment(x, y, nx, ny, drawing = z <= penDownZ)
            }
            x = nx; y = ny; z = nz
        }
        return segments
    }

    /**
     * Zahlen werden bewusst streng geparst: taucht ein Komma als Dezimaltrennzeichen auf,
     * schlaegt das hier fehl statt still ignoriert zu werden - genau der Locale-Fehler, den
     * ein deutsch eingestelltes Geraet erzeugen wuerde.
     */
    private fun word(line: String, letter: Char): Float? {
        val match = Regex("$letter(-?[0-9.,]+)", RegexOption.IGNORE_CASE).find(line) ?: return null
        val token = match.groupValues[1]
        require(!token.contains(',')) {
            "G-Code enthaelt ein Komma als Dezimaltrennzeichen: '$line'. " +
                "Die Formatierung muss Locale.ROOT benutzen."
        }
        return token.toFloatOrNull() ?: error("Unlesbare Zahl in '$line'")
    }

    /**
     * Rendert die Segmente als PNG. Das Blatt wird weiss dargestellt, Zeichenwege schwarz,
     * Leerfahrten optional in Rot - so ist auf einen Blick sichtbar, ob die Pfadsortierung
     * sinnvolle Wege waehlt.
     */
    fun renderPng(
        lines: List<String>,
        penDownZ: Float,
        widthMm: Float,
        heightMm: Float,
        target: File,
        pixelsPerMm: Int = 6,
        showTravel: Boolean = false,
    ) {
        val w = (widthMm * pixelsPerMm).toInt().coerceAtLeast(1)
        val h = (heightMm * pixelsPerMm).toInt().coerceAtLeast(1)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)

        // Y spiegeln: im G-Code zeigt Y nach oben, im Bild nach unten.
        fun px(mm: Float) = (mm * pixelsPerMm)
        fun py(mm: Float) = (h - mm * pixelsPerMm)

        for (s in simulate(lines, penDownZ)) {
            if (s.drawing) {
                g.color = Color.BLACK
                g.stroke = BasicStroke(pixelsPerMm * 0.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            } else {
                if (!showTravel) continue
                g.color = Color(255, 80, 80, 90)
                g.stroke = BasicStroke(1f)
            }
            g.drawLine(px(s.x1).toInt(), py(s.y1).toInt(), px(s.x2).toInt(), py(s.y2).toInt())
        }
        g.dispose()

        target.parentFile?.mkdirs()
        ImageIO.write(image, "png", target)
    }

    fun summary(lines: List<String>, penDownZ: Float): String {
        val segments = simulate(lines, penDownZ)
        val draw = segments.count { it.drawing }
        return String.format(
            Locale.ROOT,
            "%d Zeilen, %d Segmente (%d zeichnend, %d Leerfahrt)",
            lines.size, segments.size, draw, segments.size - draw,
        )
    }
}
