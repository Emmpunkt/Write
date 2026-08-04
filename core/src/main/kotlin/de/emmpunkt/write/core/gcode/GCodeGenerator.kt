package de.emmpunkt.write.core.gcode

import de.emmpunkt.write.core.geometry.BoundingBox
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.core.geometry.boundingBox
import de.emmpunkt.write.core.geometry.drawLength
import de.emmpunkt.write.core.geometry.optimizeOrder
import de.emmpunkt.write.core.geometry.simplify
import de.emmpunkt.write.core.geometry.travelLength
import de.emmpunkt.write.core.layout.LaidOutText
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/** Ein fertiger Plotauftrag: die G-Code-Zeilen plus alles, was die App darueber anzeigen will. */
data class PlotJob(
    val lines: List<String>,
    val bounds: BoundingBox?,
    val drawLengthMm: Float,
    val travelLengthMm: Float,
    val penDownCount: Int,
    val estimatedSeconds: Float,
) {
    val text: String get() = lines.joinToString("\n")
}

/** Ergebnis der Grenzpruefung. [violations] ist leer, wenn der Auftrag sicher fahrbar ist. */
data class BoundsCheck(
    val bounds: BoundingBox?,
    val violations: List<String>,
) {
    val ok: Boolean get() = violations.isEmpty()
}

/**
 * Bringt die gesetzten Zeilen in die Reihenfolge, in der sie gefahren werden.
 *
 * Zeilen laufen immer von oben nach unten - das ist keine Kosmetik, sondern verhindert bei
 * Tinte, dass der Stift ueber noch feuchte Striche faehrt.
 *
 * Innerhalb einer Zeile entscheidet [MachineProfile.naturalWriteOrder]: in Schreibrichtung
 * (die Reihenfolge, in der das Layout die Zeichen gesetzt hat) oder nach kuerzesten Wegen.
 */
fun LaidOutText.orderedStrokes(profile: MachineProfile): List<Polyline> {
    val result = ArrayList<Polyline>()
    var pen = Point(0f, 0f)
    for (line in lines) {
        val simplified = line.strokes.simplify(profile.simplifyToleranceMm)
        // Das Layout erzeugt die Zuege bereits Zeichen fuer Zeichen von links nach rechts;
        // in Schreibrichtung ist also schlicht nichts umzusortieren.
        val ordered = if (profile.naturalWriteOrder) simplified else simplified.optimizeOrder(pen)
        result += ordered
        ordered.lastOrNull()?.let { pen = it.end }
    }
    return result
}

/** Verschiebt Blatt-Koordinaten in das Arbeitskoordinatensystem der Maschine. */
fun List<Polyline>.toMachineCoordinates(profile: MachineProfile): List<Polyline> =
    map { it.translate(profile.paperOffsetXMm, profile.paperOffsetYMm) }

/**
 * Lage des Arbeitsnullpunkts (G54) im Maschinenkoordinatensystem, wie ihn `$#` meldet.
 *
 * Die App sendet Koordinaten in G54. Die Firmware addiert diesen Versatz, bevor sie faehrt -
 * der Verfahrweg aus \$130/\$131 gilt aber ab dem MASCHINENnullpunkt. Beides deckt sich nur,
 * wenn der Versatz null ist; sonst ist genau um ihn weniger fahrbar, als der Arbeitsbereich
 * verspricht.
 */
data class WorkOffset(val xMm: Float, val yMm: Float)

/** Der fahrbare Bereich einer einzelnen Achse in Maschinenkoordinaten. */
data class AxisTravel(val minMm: Float, val maxMm: Float) {
    init {
        require(maxMm > minMm) { "Der Verfahrweg muss positiv sein" }
    }

    companion object {
        /**
         * Leitet den fahrbaren Bereich aus der Achsenkonfiguration von FluidNC ab.
         *
         * [mposMm] ist die Maschinenkoordinate, auf der die Achse nach der Referenzfahrt steht
         * (`homing/mpos_mm`). Auf welcher SEITE des Verfahrwegs sie liegt, entscheidet
         * [positiveDirection] (`homing/positive_direction`) - wer das verwechselt, spiegelt
         * den ganzen Bereich.
         */
        fun fromHoming(mposMm: Float, maxTravelMm: Float, positiveDirection: Boolean): AxisTravel =
            if (positiveDirection) {
                AxisTravel(mposMm - maxTravelMm, mposMm)
            } else {
                AxisTravel(mposMm, mposMm + maxTravelMm)
            }
    }
}

/**
 * Der fahrbare Bereich in X und Y, in Maschinenkoordinaten.
 *
 * Er beginnt NICHT zwangslaeufig bei null: beim Plotter des Nutzers steht in `$/axes/x` und
 * `$/axes/y` bei negativer Referenzfahrt `mpos_mm: 3.0`, fahrbar ist also 3..158 statt 0..155.
 * Nachgemessen: ein Jog auf Maschine 2 wird auf exakt 3.000 begrenzt.
 *
 * [ofProfile] ist der Rueckfall, wenn die Achsenkonfiguration nicht gelesen werden konnte. Er
 * entspricht der frueheren Annahme `[0, workArea]` und rechnet konservativ, solange der
 * Arbeitsnullpunkt auf oder ueber der wahren Untergrenze liegt.
 */
data class TravelLimits(
    val minXMm: Float,
    val maxXMm: Float,
    val minYMm: Float,
    val maxYMm: Float,
) {
    companion object {
        fun ofProfile(profile: MachineProfile) = TravelLimits(
            minXMm = 0f,
            maxXMm = profile.workAreaXMm,
            minYMm = 0f,
            maxYMm = profile.workAreaYMm,
        )

        fun of(x: AxisTravel, y: AxisTravel) =
            TravelLimits(x.minMm, x.maxMm, y.minMm, y.maxMm)
    }
}

/**
 * Prueft, ob der Auftrag vollstaendig im Verfahrweg der Maschine liegt.
 *
 * Wird VOR dem Senden ausgewertet. Die Firmware hat zwar eigene Soft Limits, aber sie greifen
 * erst waehrend des Auftrags: eine Zielkoordinate ausserhalb loest ALARM:2 aus - mitten in der
 * Bewegung, mit aufliegendem Stift und halb geschriebenem Blatt. Am Geraet nachgemessen.
 * (Jog-Befehle verhalten sich anders: die werden auf die Grenze begrenzt statt abgewiesen.)
 *
 * Erwartet Zuege bereits in Blatt-plus-Papierversatz-Koordinaten, also in G54
 * (siehe [toMachineCoordinates]).
 *
 * @param workOffset Lage des Arbeitsnullpunkts. `null` heisst *unbekannt* und ist bewusst ein
 *   Fehler, kein stillschweigendes (0,0): genau diese Annahme liess den Auftrag bis zu zwei
 *   Millimeter ueber den Verfahrweg hinauslaufen.
 * @param limits Der fahrbare Bereich in Maschinenkoordinaten. Die Vorgabe `[0, workArea]` ist
 *   der Rueckfall, wenn die Achsenkonfiguration nicht gelesen werden konnte - anders als beim
 *   Arbeitsnullpunkt ist das hier vertretbar, weil die Annahme in die sichere Richtung irrt.
 */
fun checkBounds(
    machineStrokes: List<Polyline>,
    profile: MachineProfile,
    workOffset: WorkOffset?,
    limits: TravelLimits = TravelLimits.ofProfile(profile),
): BoundsCheck {
    val bounds = machineStrokes.boundingBox()
        ?: return BoundsCheck(null, emptyList())

    if (workOffset == null) {
        return BoundsCheck(
            bounds,
            listOf(
                "Arbeitsnullpunkt unbekannt - ohne ihn laesst sich nicht sagen, ob der Text " +
                    "in den Verfahrweg passt. Bitte neu verbinden oder referenzieren.",
            ),
        )
    }

    // Die Grenzen, umgerechnet in die Koordinaten, die tatsaechlich gesendet werden.
    val linksMin = limits.minXMm - workOffset.xMm
    val untenMin = limits.minYMm - workOffset.yMm
    val rechtsMax = limits.maxXMm - workOffset.xMm
    val obenMax = limits.maxYMm - workOffset.yMm

    val violations = buildList {
        if (bounds.minX < linksMin) {
            add("Text ragt %.1f mm links aus dem Arbeitsbereich".fmt(linksMin - bounds.minX))
        }
        if (bounds.minY < untenMin) {
            add("Text ragt %.1f mm unten aus dem Arbeitsbereich".fmt(untenMin - bounds.minY))
        }
        if (bounds.maxX > rechtsMax) {
            add("Text ragt %.1f mm rechts aus dem Arbeitsbereich".fmt(bounds.maxX - rechtsMax))
        }
        if (bounds.maxY > obenMax) {
            add("Text ragt %.1f mm oben aus dem Arbeitsbereich".fmt(bounds.maxY - obenMax))
        }

        // Jeder Auftrag endet mit `G0 X0 Y0`. Dort liegt kein Strich, gefahren wird trotzdem -
        // und wenn der Arbeitsnullpunkt unter dem Verfahrweg liegt, loest genau diese letzte
        // Bewegung ALARM:2 aus, wenn das Blatt schon fertig beschrieben ist. Am Geraet
        // passiert, als der Nullpunkt einen Millimeter zu tief lag.
        if (0f < linksMin || 0f < untenMin) {
            add(
                "Der Arbeitsnullpunkt liegt %.1f mm ausserhalb des Verfahrwegs - die Rueckfahrt "
                    .fmt(maxOf(linksMin, untenMin)) +
                    "am Ende des Auftrags waere nicht fahrbar. Bitte G54 neu setzen.",
            )
        }
    }
    return BoundsCheck(bounds, violations)
}

private fun String.fmt(value: Float) = String.format(Locale.ROOT, this, value)

/**
 * Wandelt Strichzuege in Maschinenkoordinaten in G-Code.
 *
 * Der Stift wird mit G1 und begrenztem Vorschub abgesenkt, nicht mit G0: bei einem lose
 * aufliegenden Stift wuerde ein Eilgang-Absenken auf das Papier schlagen.
 */
fun generateGCode(machineStrokes: List<Polyline>, profile: MachineProfile): PlotJob {
    val out = ArrayList<String>()

    out += "G21"  // Millimeter
    out += "G90"  // absolute Koordinaten
    out += "G94"  // Vorschub in Einheiten pro Minute
    // Erste Anweisung ueberhaupt: Stift hoch. Vorher darf keine XY-Bewegung stehen.
    out += "G0 Z${num(profile.zUpMm)}"

    for (stroke in machineStrokes) {
        val first = stroke.start
        out += "G0 X${num(first.x)} Y${num(first.y)}"
        out += "G1 Z${num(profile.zDownMm)} F${profile.feedZMmMin}"

        stroke.points.drop(1).forEachIndexed { index, p ->
            // Der Vorschub ist modal - nur die erste Zeichenbewegung braucht ihn.
            val feed = if (index == 0) " F${profile.feedDrawMmMin}" else ""
            out += "G1 X${num(p.x)} Y${num(p.y)}$feed"
        }

        out += "G0 Z${num(profile.zUpMm)}"
    }

    out += "G0 Z${num(profile.zUpMm)}"
    out += "G0 X0 Y0"
    out += "M2"

    val drawLength = machineStrokes.drawLength()
    val travelLength = machineStrokes.travelLength()
    return PlotJob(
        lines = out,
        bounds = machineStrokes.boundingBox(),
        drawLengthMm = drawLength,
        travelLengthMm = travelLength,
        penDownCount = machineStrokes.size,
        estimatedSeconds = estimateSeconds(machineStrokes, profile),
    )
}

/** Bequemer Weg vom Satz zum Auftrag: ordnen, verschieben, umwandeln. */
fun LaidOutText.toPlotJob(profile: MachineProfile): PlotJob =
    plotJobAus(orderedStrokes(profile), profile)

/**
 * Auftrag aus fertigen Blatt-Zuegen - fuer alles, was zum Text noch dazukommt.
 *
 * Der Weg fuer Text plus Dekoration: Ein gezeichneter Rahmen steht in keinem [LaidOutLine], und
 * [orderedStrokes] liest nur aus den Zeilen. Wer beides plotten will, legt die Zuege selbst
 * zusammen und uebergibt sie hier - die Reihenfolge der Liste ist die Reihenfolge auf dem
 * Papier.
 */
fun plotJobAus(zuege: List<Polyline>, profile: MachineProfile): PlotJob =
    generateGCode(zuege.toMachineCoordinates(profile), profile)

/**
 * Dauer einer einzelnen Bewegung, die aus dem Stand beginnt und wieder zum Stehen kommt.
 *
 * Zwei Faelle, je nachdem ob die Strecke ueberhaupt fuer den Sollvorschub reicht:
 *
 * - Trapez: hochbeschleunigen, mit `v` fahren, bremsen. Beschleunigen und Bremsen kosten
 *   zusammen die Strecke `v^2/a` und die Zeit `v/a` mehr, als das blosse `s/v` unterstellt.
 * - Dreieck: die halbe Strecke wird beschleunigt, die halbe gebremst; `v` faellt nie an.
 *   Das ist der Fall, der die alte Schaetzung um ein Viertel danebenliegen liess - bei einer
 *   Schreibschrift ist die Mehrzahl der Bewegungen genau so kurz.
 *
 * Junction Deviation bleibt aussen vor: innerhalb eines Strichzugs faehrt der Planer weiche
 * Ecken durch, an scharfen bremst er ab. Der erste Fall ist hier angenommen - die Schaetzung
 * bleibt damit eher knapp als zu grosszuegig.
 */
internal fun rampSeconds(lengthMm: Float, feedMmMin: Int, accelMmS2: Float): Float {
    if (lengthMm <= 0f) return 0f
    val v = feedMmMin / 60f
    // Strecke, die zum Hochbeschleunigen UND Bremsen noetig waere.
    val rampDistance = v * v / accelMmS2
    return if (lengthMm >= rampDistance) {
        lengthMm / v + v / accelMmS2
    } else {
        2f * sqrt(lengthMm / accelMmS2)
    }
}

/**
 * Zeitschaetzung mit Beschleunigungsrampen.
 *
 * Gerechnet wird bewusst Bewegung fuer Bewegung statt ueber die Gesamtlaengen: die Maschine
 * steht am Anfang und am Ende jedes Strichzugs wirklich still, weil dazwischen der Stift
 * gehoben und gesenkt wird. Genau diese Stillstaende summieren sich - ueber die Summe aller
 * Wege gerechnet waeren sie unsichtbar.
 *
 * Nachgemessen am Geraet: 15 Minuten fuer einen Auftrag, den die alte Rechnung auf 11:20
 * schaetzte.
 */
private fun estimateSeconds(
    machineStrokes: List<Polyline>,
    profile: MachineProfile,
): Float {
    if (machineStrokes.isEmpty()) return 0f

    val zHub = abs(profile.zUpMm - profile.zDownMm)
    // Senken und Heben sind je eine eigene Bewegung aus dem Stand - kurz genug, dass die
    // Rampe hier den Grossteil der Zeit ausmacht.
    //
    // Die beiden Richtungen sind BEWUSST verschieden schnell: gesenkt wird mit G1 und
    // begrenztem Vorschub (sonst schlaegt der lose Stift auf), angehoben mit G0 im Eilgang.
    val zSeconds = (
        rampSeconds(zHub, profile.feedZMmMin, profile.accelZMmS2) +
            rampSeconds(zHub, profile.rapidZMmMin, profile.accelZMmS2)
        ) * machineStrokes.size

    var drawSeconds = 0f
    for (stroke in machineStrokes) {
        drawSeconds += rampSeconds(stroke.length, profile.feedDrawMmMin, profile.accelXYMmS2)
    }

    // Leerfahrten, einschliesslich der Anfahrt zum ersten Zug und der Rueckfahrt zum Nullpunkt.
    var travelSeconds = 0f
    var pen = Point(0f, 0f)
    for (stroke in machineStrokes) {
        travelSeconds += rampSeconds(
            pen.distanceTo(stroke.start), profile.feedTravelMmMin, profile.accelXYMmS2,
        )
        pen = stroke.end
    }
    travelSeconds += rampSeconds(
        pen.distanceTo(Point(0f, 0f)), profile.feedTravelMmMin, profile.accelXYMmS2,
    )

    return drawSeconds + travelSeconds + zSeconds
}

/**
 * Zahlformatierung mit Punkt als Dezimaltrennzeichen und ohne ueberfluessige Nullen.
 *
 * [Locale.ROOT] ist hier nicht optional: auf einem deutsch eingestellten Geraet wuerde die
 * Standard-Formatierung ein Komma erzeugen und FluidNC jede Zeile mit einem Fehler abweisen.
 */
internal fun num(value: Float): String {
    val s = String.format(Locale.ROOT, "%.3f", value)
    return s.trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
}
