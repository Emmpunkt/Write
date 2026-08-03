package de.emmpunkt.write.core.gcode

/**
 * Was eine Maschine ueber sich selbst weiss.
 *
 * Diese Werte gehoeren der Maschine, nicht den Einstellungen des Nutzers: sie aendern sich,
 * wenn jemand die Firmware umkonfiguriert, und sie sind bei einem anderen Plotter andere.
 * Deshalb werden sie ausgelesen und nicht gepflegt.
 *
 * Jedes Feld ist einzeln optional. `null` heisst *unbekannt* - dann bleibt der bisher
 * eingestellte Wert stehen, statt dass ein erfundener ihn ueberschreibt.
 */
data class MachineLimits(
    /** Fahrbarer Bereich in Maschinenkoordinaten, aus `mpos_mm` und `max_travel_mm`. */
    val travel: TravelLimits? = null,
    /** Beschleunigung der XY-Achsen (`$120`/`$121`). */
    val accelXYMmS2: Float? = null,
    /** Beschleunigung der Z-Achse (`$122`) - nicht zwangslaeufig dieselbe wie XY. */
    val accelZMmS2: Float? = null,
    /** Hoechstvorschub der XY-Achsen (`$110`/`$111`). Obergrenze, kein Sollwert. */
    val maxRateXYMmMin: Int? = null,
    /** Hoechstvorschub der Z-Achse (`$112`). */
    val maxRateZMmMin: Int? = null,
) {
    val isEmpty: Boolean
        get() = travel == null && accelXYMmS2 == null && accelZMmS2 == null &&
            maxRateXYMmMin == null && maxRateZMmMin == null

    companion object {
        /** Noch nichts gelesen - jeder eingestellte Wert bleibt unangetastet. */
        val UNKNOWN = MachineLimits()
    }
}

/**
 * Legt die ausgelesenen Maschinenwerte ueber ein Profil.
 *
 * Wo die Maschine etwas ueber sich sagt, gewinnt sie: sie kennt ihren Verfahrweg und ihre
 * Beschleunigung sicher, waehrend ein gespeicherter Wert von einer frueheren Konfiguration
 * oder gar von einem anderen Geraet stammen kann. Genau dieser Fall ist eingetreten - eine
 * Notiz nannte `mpos_mm: 3.0`, ausgelesen waren es 10.
 *
 * Zwei Dinge bleiben bewusst unberuehrt:
 * - **Die Stifthoehen.** Sie haengen am Stift und am Papier, nicht an der Maschine.
 * - **Der gewuenschte Vorschub**, solange er unter der Grenze der Maschine liegt. Langsamer
 *   zu schreiben ist eine gestalterische Entscheidung. Nur wenn der eingestellte Wert die
 *   Maschine ueberfordert, wird er gekappt - sonst waere die Zeitschaetzung zu optimistisch,
 *   denn die Firmware begrenzt ohnehin.
 */
fun MachineProfile.applying(limits: MachineLimits): MachineProfile {
    if (limits.isEmpty) return this

    val maxXY = limits.maxRateXYMmMin
    val maxZ = limits.maxRateZMmMin

    return copy(
        // Der Verfahrweg der Maschine, nicht der gespeicherte. Bleibt der Rueckfall fuer
        // checkBounds, wenn spaeter einmal keine Grenzen gelesen werden konnten.
        workAreaXMm = limits.travel?.let { it.maxXMm - it.minXMm } ?: workAreaXMm,
        workAreaYMm = limits.travel?.let { it.maxYMm - it.minYMm } ?: workAreaYMm,
        feedDrawMmMin = maxXY?.let { minOf(feedDrawMmMin, it) } ?: feedDrawMmMin,
        feedTravelMmMin = maxXY?.let { minOf(feedTravelMmMin, it) } ?: feedTravelMmMin,
        feedZMmMin = maxZ?.let { minOf(feedZMmMin, it) } ?: feedZMmMin,
        // Der Eilgang IST der Hoechstvorschub der Achse - G0 fragt nicht nach dem
        // eingestellten Wert. Die Invariante rapidZ >= feedZ haelt von selbst, weil
        // feedZMmMin eine Zeile darueber auf denselben Wert gekappt wird.
        rapidZMmMin = maxZ ?: rapidZMmMin,
        accelXYMmS2 = limits.accelXYMmS2 ?: accelXYMmS2,
        accelZMmS2 = limits.accelZMmS2 ?: accelZMmS2,
    )
}
