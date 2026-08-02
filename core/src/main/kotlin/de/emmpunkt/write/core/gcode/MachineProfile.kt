package de.emmpunkt.write.core.gcode

/**
 * Alles, was die App ueber die Maschine wissen muss.
 *
 * Zur Z-Achse dieses Plotters: der Antrieb ist nicht fest mit dem Stift gekoppelt. Sobald der
 * Stift aufsetzt, traegt ihn nur noch sein Eigengewicht. Deshalb darf [zDownMm] bewusst ein
 * Stueck UNTER der Papierebene liegen - dieses Uebertravel gleicht Unebenheiten aus und haelt
 * die Strichstaerke konstant, ohne dass etwas verspannt.
 */
data class MachineProfile(
    /** Sichere Hoehe fuer Leerfahrten. Klein halten: jeder Hub kostet Zeit. */
    val zUpMm: Float = 3f,
    /** Schreibhoehe mit Uebertravel (negativ = unter der Papierebene). */
    val zDownMm: Float = -1.5f,

    /**
     * Vorschuebe. Die Vorgaben stammen aus der ausgelesenen Konfiguration des Plotters
     * (\$110/\$111 = 1500 mm/min fuer X/Y, \$112 = 2000 fuer Z) und liegen bewusst nicht
     * darueber: die Firmware begrenzt hoehere Werte ohnehin, die Zeitschaetzung der App
     * waere dann aber zu optimistisch.
     */
    val feedDrawMmMin: Int = 1200,
    val feedTravelMmMin: Int = 1500,
    val feedZMmMin: Int = 600,

    /**
     * Verfahrweg ab dem MASCHINENnullpunkt, Vorgabe aus \$130/\$131 des Plotters.
     *
     * Nicht ab dem Arbeitsnullpunkt - dieser Irrtum stand hier frueher und hat gekostet:
     * gesendet wird in G54, und dessen Nullpunkt liegt beim Geraet des Nutzers auf (2, 2).
     * Vom eingestellten Bereich sind also 2 mm weniger fahrbar, als er verspricht. Deshalb
     * verlangt checkBounds den Arbeitsnullpunkt als eigenen Parameter.
     *
     * Am Geraet sind Soft Limits eingeschaltet, aber sie retten den Auftrag nicht: eine
     * Zielkoordinate ausserhalb loest ALARM:2 aus - mitten in der Bewegung, mit halb
     * geschriebenem Blatt. Die Pruefung in checkBounds soll genau davor liegen.
     *
     * ACHTUNG, hier noch nicht abgebildet: der fahrbare Bereich beginnt nicht bei 0. Beim
     * Geraet des Nutzers steht in der Achsenkonfiguration `mpos_mm: 3.0` bei negativer
     * Referenzfahrt - fahrbar ist also Maschine 3..158, nicht 0..155. Nachgemessen: ein Jog
     * auf Maschine 2 wird auf 3 begrenzt.
     */
    val workAreaXMm: Float = 155f,
    val workAreaYMm: Float = 105f,

    /** Lage der linken unteren Blattecke im Arbeitskoordinatensystem (fester Anschlag). */
    val paperOffsetXMm: Float = 0f,
    val paperOffsetYMm: Float = 0f,

    /**
     * Reihenfolge der Strichzuege innerhalb einer Zeile.
     *
     * true  - in Schreibrichtung, Zeichen fuer Zeichen von links nach rechts, so wie von Hand
     *         geschrieben. Der Stift faehrt dabei nie ueber bereits Geschriebenes zurueck,
     *         was bei Tinte das Verschmieren verhindert.
     * false - nach kuerzesten Wegen sortiert. Etwas schneller, aber der Stift springt in der
     *         Zeile hin und her.
     */
    val naturalWriteOrder: Boolean = true,

    /**
     * Zulaessige Abweichung beim Ausduennen der Polygonzuege. Deutlich unterhalb der
     * Strichbreite eines Stifts, damit die Vereinfachung auf dem Papier unsichtbar bleibt,
     * aber sichtbar weniger G-Code-Zeilen entstehen.
     */
    val simplifyToleranceMm: Float = 0.05f,
) {
    init {
        require(zUpMm > zDownMm) { "Z_up muss ueber Z_down liegen" }
        require(feedDrawMmMin > 0 && feedTravelMmMin > 0 && feedZMmMin > 0) {
            "Vorschuebe muessen positiv sein"
        }
        require(workAreaXMm > 0f && workAreaYMm > 0f) { "Arbeitsbereich muss positiv sein" }
    }
}
