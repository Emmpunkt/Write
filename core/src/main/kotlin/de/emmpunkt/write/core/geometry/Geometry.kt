package de.emmpunkt.write.core.geometry

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.tan

/**
 * Ein Punkt. Die Einheit haengt vom Kontext ab: innerhalb eines [de.emmpunkt.write.core.font.Glyph]
 * sind es Font-Einheiten, ab dem Layout durchgehend Millimeter.
 *
 * Y zeigt immer nach OBEN (G-Code-Konvention). Die Spiegelung der nach unten zeigenden
 * JHF-Achse passiert genau einmal, beim Parsen des Fonts.
 */
data class Point(val x: Float, val y: Float) {
    fun translate(dx: Float, dy: Float) = Point(x + dx, y + dy)
    fun scale(sx: Float, sy: Float) = Point(x * sx, y * sy)
    fun distanceTo(other: Point) = hypot(other.x - x, other.y - y)
}

/**
 * Ein zusammenhaengender Strichzug: der Stift geht am ersten Punkt herunter, faehrt alle
 * weiteren Punkte an und wird am Ende gehoben. Ein Strichzug entspricht damit genau einem
 * Pen-Down/Pen-Up-Zyklus der Maschine.
 */
data class Polyline(val points: List<Point>) {
    init {
        require(points.size >= 2) { "Ein Strichzug braucht mindestens zwei Punkte" }
    }

    val start: Point get() = points.first()
    val end: Point get() = points.last()

    /** Zeichenweg dieses Zuges (Stift unten). */
    val length: Float
        get() = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    fun reversed() = Polyline(points.reversed())

    fun map(transform: (Point) -> Point) = Polyline(points.map(transform))

    fun translate(dx: Float, dy: Float) = map { it.translate(dx, dy) }

    fun scale(sx: Float, sy: Float) = map { it.scale(sx, sy) }

    /**
     * Schert die Punkte um [degrees] nach rechts und erzeugt so eine kuenstliche Kursivierung.
     * Die Scherung erfolgt um die Grundlinie (y = 0), damit die Fusspunkte stehen bleiben.
     */
    fun slant(degrees: Float): Polyline {
        if (degrees == 0f) return this
        val factor = tan(Math.toRadians(degrees.toDouble())).toFloat()
        return map { Point(it.x + it.y * factor, it.y) }
    }
}

/** Achsparalleles Rechteck. Wird fuer die Grenzpruefung vor dem Senden gebraucht. */
data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    fun union(other: BoundingBox) = BoundingBox(
        minX = minOf(minX, other.minX),
        minY = minOf(minY, other.minY),
        maxX = maxOf(maxX, other.maxX),
        maxY = maxOf(maxY, other.maxY),
    )

    companion object {
        fun of(points: Iterable<Point>): BoundingBox? {
            val it = points.iterator()
            if (!it.hasNext()) return null
            val first = it.next()
            var minX = first.x
            var minY = first.y
            var maxX = first.x
            var maxY = first.y
            while (it.hasNext()) {
                val p = it.next()
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            return BoundingBox(minX, minY, maxX, maxY)
        }
    }
}

/** Bounding-Box ueber alle Strichzuege, oder null bei leerer Liste. */
fun List<Polyline>.boundingBox(): BoundingBox? =
    BoundingBox.of(this.flatMap { it.points })

/** Summe aller Zeichenwege (Stift unten). */
fun List<Polyline>.drawLength(): Float = sumOf { it.length.toDouble() }.toFloat()

/**
 * Summe aller Leerfahrten (Stift oben), inklusive der Anfahrt von [origin] zum ersten Zug.
 * Das ist die Groesse, die [optimizeOrder] minimiert.
 */
fun List<Polyline>.travelLength(origin: Point = Point(0f, 0f)): Float {
    if (isEmpty()) return 0f
    var total = origin.distanceTo(first().start)
    for (i in 0 until size - 1) {
        total += this[i].end.distanceTo(this[i + 1].start)
    }
    return total
}

/**
 * Sortiert Strichzuege so, dass die Leerfahrten kurz werden, und dreht einzelne Zuege dabei
 * um, wenn das den Weg verkuerzt (fuer den Stift ist die Zeichenrichtung unerheblich).
 *
 * Naechster-Nachbar-Heuristik: bei den wenigen Dutzend Zuegen einer Textzeile liefert sie
 * praktisch dieselbe Ersparnis wie ein exaktes Verfahren, in vernachlaessigbarer Zeit.
 *
 * Der Sinn: jeder Pen-Up/Down-Zyklus kostet auf einer Z-Stepper-Achse spuerbar Zeit. Die
 * Anzahl der Zyklen ist durch die Zahl der Strichzuege fest vorgegeben - kuerzen laesst sich
 * nur der Weg dazwischen.
 */
fun List<Polyline>.optimizeOrder(origin: Point = Point(0f, 0f)): List<Polyline> {
    if (size <= 1) return this

    val remaining = toMutableList()
    val result = ArrayList<Polyline>(size)
    var current = origin

    while (remaining.isNotEmpty()) {
        var bestIndex = 0
        var bestReversed = false
        var bestDistance = Float.MAX_VALUE

        remaining.forEachIndexed { index, line ->
            val toStart = current.distanceTo(line.start)
            if (toStart < bestDistance) {
                bestDistance = toStart
                bestIndex = index
                bestReversed = false
            }
            val toEnd = current.distanceTo(line.end)
            if (toEnd < bestDistance) {
                bestDistance = toEnd
                bestIndex = index
                bestReversed = true
            }
        }

        val chosen = remaining.removeAt(bestIndex).let { if (bestReversed) it.reversed() else it }
        result += chosen
        current = chosen.end
    }
    return result
}

/**
 * Entfernt Punkte, die auf der Verbindung ihrer Nachbarn liegen (Douglas-Peucker).
 *
 * Hershey-Glyphen sind Polygonzuege mit teils sehr dicht liegenden Punkten. Jeder Punkt wird
 * zu einer G1-Zeile, also zu Bytes im Sendepuffer und zu einem Planer-Segment. Bei einer
 * Toleranz weit unterhalb der Strichbreite ist die Vereinfachung auf dem Papier unsichtbar,
 * verkuerzt den G-Code aber deutlich.
 */
fun Polyline.simplify(toleranceMm: Float): Polyline {
    if (toleranceMm <= 0f || points.size <= 2) return this
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    simplifySegment(points, 0, points.size - 1, toleranceMm, keep)
    val kept = points.filterIndexed { index, _ -> keep[index] }
    return if (kept.size >= 2) Polyline(kept) else this
}

fun List<Polyline>.simplify(toleranceMm: Float): List<Polyline> = map { it.simplify(toleranceMm) }

private fun simplifySegment(
    points: List<Point>,
    first: Int,
    last: Int,
    tolerance: Float,
    keep: BooleanArray,
) {
    if (last <= first + 1) return
    var maxDistance = -1f
    var maxIndex = first
    for (i in first + 1 until last) {
        val d = perpendicularDistance(points[i], points[first], points[last])
        if (d > maxDistance) {
            maxDistance = d
            maxIndex = i
        }
    }
    if (maxDistance > tolerance) {
        keep[maxIndex] = true
        simplifySegment(points, first, maxIndex, tolerance, keep)
        simplifySegment(points, maxIndex, last, tolerance, keep)
    }
}

private fun perpendicularDistance(p: Point, a: Point, b: Point): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return p.distanceTo(a)
    return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / hypot(dx, dy)
}
