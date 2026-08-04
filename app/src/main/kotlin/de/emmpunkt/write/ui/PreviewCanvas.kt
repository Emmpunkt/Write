package de.emmpunkt.write.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.data.Blattbild

/**
 * Zeigt das Blatt, den Textrahmen darin und die Strichzuege, die geplottet werden.
 *
 * Gezeichnet werden genau die [Polyline]-Objekte, aus denen auch der G-Code entsteht - keine
 * zweite, nachgebildete Darstellung. Was hier steht, faehrt der Stift.
 *
 * Das weisse Rechteck ist das BLATT, also das Papier auf dem Tisch. Der gestrichelte Kasten
 * darin ist der Textrahmen. Vorher waren beide dasselbe: Wer den Text klein in eine Ecke
 * setzen wollte, sah in der Vorschau nur den Textkasten und nicht die Karte, auf der er liegt.
 */
@Composable
fun PreviewCanvas(
    strokes: List<Polyline>,
    blattbild: Blattbild,
    modifier: Modifier = Modifier,
    showRahmen: Boolean = true,
    showTravel: Boolean = false,
) {
    val frame = blattbild.frame
    val paperColor = Color(0xFFFDFDFA)
    val inkColor = MaterialTheme.colorScheme.onSurface
    val marginColor = MaterialTheme.colorScheme.outlineVariant
    val travelColor = Color(0x55E53935)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Ohne Beschneiden malt die Vorschau ueber ihren eigenen Rand hinaus.
            // Passt der Text nicht auf das Blatt, standen die ueberzaehligen Zeilen quer
            // ueber den Bedienelementen darunter - am Geraet aufgefallen. Auf dem Papier
            // gibt es diesen Ueberlauf nicht, dort endet das Blatt einfach.
            .clipToBounds()
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val blattW = blattbild.blattBreiteMm
            val blattH = blattbild.blattHoeheMm
            // Das Blatt so gross wie moeglich einpassen, Seitenverhaeltnis erhalten.
            val scale = minOf(size.width / blattW, size.height / blattH)
            val paperW = blattW * scale
            val paperH = blattH * scale
            val originX = (size.width - paperW) / 2f
            val originY = (size.height - paperH) / 2f

            drawRect(color = paperColor, topLeft = Offset(originX, originY), size = Size(paperW, paperH))

            if (showRahmen) {
                drawRect(
                    color = marginColor,
                    topLeft = Offset(
                        originX + blattbild.rahmenXMm * scale,
                        // Von oben gerechnet, weil der Bildschirm nach unten zaehlt.
                        originY + (blattH - blattbild.rahmenYMm - frame.heightMm) * scale,
                    ),
                    size = Size(frame.widthMm * scale, frame.heightMm * scale),
                    style = Stroke(
                        width = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    ),
                )
            }

            // Die Zuege stehen in Rahmen-Koordinaten - der Rahmenversatz kommt hier dazu.
            // Y spiegeln: im Layout zeigt Y nach oben, auf dem Bildschirm nach unten.
            fun px(xMm: Float) = originX + (blattbild.rahmenXMm + xMm) * scale
            fun py(yMm: Float) = originY + paperH - (blattbild.rahmenYMm + yMm) * scale

            if (showTravel) {
                drawTravel(strokes, ::px, ::py, travelColor)
            }

            val penWidth = (PEN_WIDTH_MM * scale).coerceAtLeast(1f)
            strokes.forEach { line ->
                val path = Path().apply {
                    moveTo(px(line.points[0].x), py(line.points[0].y))
                    line.points.drop(1).forEach { lineTo(px(it.x), py(it.y)) }
                }
                drawPath(
                    path = path,
                    color = inkColor,
                    style = Stroke(width = penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/** Leerfahrten als duenne Linien - zeigt, wie viel der Stift ohne zu schreiben unterwegs ist. */
private fun DrawScope.drawTravel(
    strokes: List<Polyline>,
    px: (Float) -> Float,
    py: (Float) -> Float,
    color: Color,
) {
    for (i in 0 until strokes.size - 1) {
        val from = strokes[i].end
        val to = strokes[i + 1].start
        drawLine(
            color = color,
            start = Offset(px(from.x), py(from.y)),
            end = Offset(px(to.x), py(to.y)),
            strokeWidth = 1f,
        )
    }
}

/** Ungefaehre Strichbreite eines Fineliners - macht die Vorschau realistisch. */
private const val PEN_WIDTH_MM = 0.4f
