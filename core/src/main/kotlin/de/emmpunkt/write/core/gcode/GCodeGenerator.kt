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
 * Prueft, ob der Auftrag vollstaendig im Arbeitsbereich liegt.
 *
 * Wird VOR dem Senden ausgewertet. Ein Auftrag, der den Bereich verlaesst, wuerde sonst erst
 * beim Anfahren der Grenze bemerkt - dann steht der Stift schon auf dem Papier.
 *
 * Erwartet Zuege bereits in Maschinenkoordinaten (siehe [toMachineCoordinates]).
 */
fun checkBounds(machineStrokes: List<Polyline>, profile: MachineProfile): BoundsCheck {
    val bounds = machineStrokes.boundingBox()
        ?: return BoundsCheck(null, emptyList())

    val violations = buildList {
        if (bounds.minX < 0f) add("Text ragt %.1f mm links aus dem Arbeitsbereich".fmt(-bounds.minX))
        if (bounds.minY < 0f) add("Text ragt %.1f mm unten aus dem Arbeitsbereich".fmt(-bounds.minY))
        if (bounds.maxX > profile.workAreaXMm) {
            add("Text ragt %.1f mm rechts aus dem Arbeitsbereich".fmt(bounds.maxX - profile.workAreaXMm))
        }
        if (bounds.maxY > profile.workAreaYMm) {
            add("Text ragt %.1f mm oben aus dem Arbeitsbereich".fmt(bounds.maxY - profile.workAreaYMm))
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
        estimatedSeconds = estimateSeconds(drawLength, travelLength, machineStrokes.size, profile),
    )
}

/** Bequemer Weg vom Satz zum Auftrag: ordnen, verschieben, umwandeln. */
fun LaidOutText.toPlotJob(profile: MachineProfile): PlotJob =
    generateGCode(orderedStrokes(profile).toMachineCoordinates(profile), profile)

/**
 * Grobe Zeitschaetzung ohne Beschleunigungsrampen - die tatsaechliche Dauer liegt etwas
 * darueber. Genau genug, um vor dem Senden einzuschaetzen, ob es Sekunden oder Minuten werden.
 */
private fun estimateSeconds(
    drawLengthMm: Float,
    travelLengthMm: Float,
    penDownCount: Int,
    profile: MachineProfile,
): Float {
    val zTravel = abs(profile.zUpMm - profile.zDownMm) * 2f * penDownCount
    val minutes = drawLengthMm / profile.feedDrawMmMin +
        travelLengthMm / profile.feedTravelMmMin +
        zTravel / profile.feedZMmMin
    return minutes * 60f
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
