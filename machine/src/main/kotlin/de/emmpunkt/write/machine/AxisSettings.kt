package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.AxisTravel

/**
 * Was `$/axes/<achse>` ueber eine Achse verraet.
 *
 * Jedes Feld ist einzeln optional: die Antwort der Firmware ist ein YAML-Block, dessen genauer
 * Schluesselvorrat sich zwischen den FluidNC-Fassungen unterscheidet. Ein fehlender Wert
 * bleibt deshalb `null` und faellt beim Aufrufer auf die Vorgabe zurueck, statt den ganzen
 * Block unbrauchbar zu machen.
 */
data class AxisSettings(
    /**
     * Der wirklich fahrbare Bereich, sofern die Achse referenziert wird.
     *
     * `null` heisst *unbekannt*, nicht *unbegrenzt*. Die Z-Achse dieses Plotters hat keinen
     * Endschalter und liefert hier nichts - einen Bereich zu erfinden waere schlimmer als
     * keiner, weil er richtig aussaehe und daneben laege.
     */
    val travel: AxisTravel?,
    /** Beschleunigung fuer die Zeitschaetzung (`acceleration_mm_per_sec2`). */
    val accelMmS2: Float?,
    /** Vorschubgrenze der Achse (`max_rate_mm_per_min`). */
    val maxRateMmMin: Int?,
) {
    val isEmpty: Boolean get() = travel == null && accelMmS2 == null && maxRateMmMin == null

    companion object {
        /**
         * Zerlegt den Antwortblock.
         *
         * Gesucht werden die Schluessel, nicht ihre Stellung: Einrueckung, Reihenfolge und
         * unbekannte Zusatzzeilen bleiben ohne Wirkung. `mpos_mm` steht bei FluidNC unter
         * `homing:` und damit tiefer eingerueckt als der Rest - wer auf die Struktur baut,
         * bricht bei der naechsten Fassung.
         */
        fun parse(lines: List<String>): AxisSettings {
            val values = HashMap<String, String>()
            for (line in lines) {
                val trimmed = line.trim()
                val separator = trimmed.indexOf(':')
                if (separator <= 0) continue
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                if (value.isNotEmpty()) values[key] = value
            }

            val mpos = values["mpos_mm"]?.toStrictFloat()
            val maxTravel = values["max_travel_mm"]?.toStrictFloat()
            // Fehlt die Richtung, ist die Vorgabe von FluidNC eine negative Referenzfahrt.
            val positive = values["positive_direction"]?.trim()?.lowercase() == "true"

            val travel = if (mpos != null && maxTravel != null && maxTravel > 0f) {
                AxisTravel.fromHoming(mpos, maxTravel, positive)
            } else {
                null
            }

            return AxisSettings(
                travel = travel,
                accelMmS2 = values["acceleration_mm_per_sec2"]?.toStrictFloat()?.takeIf { it > 0f },
                maxRateMmMin = values["max_rate_mm_per_min"]?.toStrictFloat()?.toInt()
                    ?.takeIf { it > 0 },
            )
        }

        /**
         * Wandelt nur um, was zweifelsfrei eine Zahl mit Punkt als Dezimaltrennzeichen ist.
         *
         * `toFloatOrNull` wuerde bei "3,0" ebenfalls scheitern, aber die Absicht soll hier
         * im Code stehen: ein falsch gelesener Wert waere schlimmer als ein fehlender - er
         * ginge unbemerkt in die Grenzpruefung ein.
         */
        private fun String.toStrictFloat(): Float? {
            if (contains(',')) return null
            return substringBefore(' ').toFloatOrNull()
        }
    }
}
