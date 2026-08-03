package de.emmpunkt.write.machine

/** Betriebszustaende, die FluidNC im Statusbericht meldet. */
enum class MachineState {
    IDLE, RUN, HOLD, JOG, ALARM, DOOR, CHECK, HOME, SLEEP, UNKNOWN;

    /** Nur im Ruhezustand darf ein Auftrag gestartet werden. */
    val readyForJob: Boolean get() = this == IDLE

    companion object {
        fun parse(token: String): MachineState = when (token.substringBefore(':').uppercase()) {
            "IDLE" -> IDLE
            "RUN" -> RUN
            "HOLD" -> HOLD
            "JOG" -> JOG
            "ALARM" -> ALARM
            "DOOR" -> DOOR
            "CHECK" -> CHECK
            "HOME" -> HOME
            "SLEEP" -> SLEEP
            else -> UNKNOWN
        }
    }
}

data class Position(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = Position(0f, 0f, 0f)

        fun parse(csv: String): Position? {
            val parts = csv.split(',')
            if (parts.size < 3) return null
            val x = parts[0].trim().toFloatOrNull() ?: return null
            val y = parts[1].trim().toFloatOrNull() ?: return null
            val z = parts[2].trim().toFloatOrNull() ?: return null
            return Position(x, y, z)
        }
    }

    operator fun minus(other: Position) = Position(x - other.x, y - other.y, z - other.z)
}

/**
 * Ein ausgewerteter Statusbericht.
 *
 * [work] ist die Position im Arbeitskoordinatensystem - also die Zahl, die den Nutzer
 * interessiert, weil sie sich auf seinen Nullpunkt bezieht.
 */
/**
 * Der SD-Lauf, den der Statusbericht als `SD:100.00,/sd/datei.nc` meldet.
 *
 * ACHTUNG, am 2026-08-03 am Geraet nachgemessen: [percent] ist der LESEfortschritt der Datei,
 * nicht der Bewegungsfortschritt. Bei einer kleinen Datei steht sofort 100 %, waehrend die
 * Achse noch faehrt - FluidNC liest voraus. Als Fortschrittsbalken taugt der Wert deshalb nur
 * bei grossen Dateien, und auch dort eilt er der Wirklichkeit voraus.
 *
 * Verlaesslich ist allein der Zustand: solange [MachineState.RUN] gemeldet wird, laeuft der
 * Auftrag; der Wechsel nach [MachineState.IDLE] ist das Ende.
 */
data class SdRun(val percent: Float, val path: String) {
    companion object {
        fun parse(value: String): SdRun? {
            val komma = value.indexOf(',')
            if (komma <= 0) return null
            val prozent = value.substring(0, komma).trim().toFloatOrNull() ?: return null
            val pfad = value.substring(komma + 1).trim()
            return if (pfad.isEmpty()) null else SdRun(prozent, pfad)
        }
    }
}

data class MachineStatus(
    val state: MachineState,
    val machine: Position?,
    val work: Position?,
    val raw: String,
    /** Gesetzt, solange ein Auftrag von der SD-Karte laeuft. */
    val sdRun: SdRun? = null,
) {
    companion object {
        val UNKNOWN = MachineStatus(MachineState.UNKNOWN, null, null, "")
    }
}

/**
 * Zerlegt die Antwortzeilen von FluidNC.
 *
 * Der Statusbericht sieht so aus:
 *   <Idle|MPos:10.000,20.000,3.000|FS:0,0|WCO:5.000,5.000,0.000>
 *
 * Der Versatz WCO wird nicht in jedem Bericht mitgeschickt, sondern nur wenn er sich aendert.
 * Deshalb merkt sich der Parser den letzten bekannten Wert - sonst waere die Arbeitsposition
 * in den meisten Berichten nicht berechenbar.
 */
class StatusParser {

    private var lastWorkCoordinateOffset: Position? = null

    /**
     * Uebernimmt den Versatz aus einer Antwort auf `$#`, etwa `[G54:11.000,22.000,-10.750]`.
     *
     * Notwendig, weil FluidNC v4 mit der Vorgabe `$10=1` nur MPos meldet und WCO ueberhaupt
     * nicht im Statusbericht mitschickt. Ohne diese Abfrage bliebe die Arbeitsposition - also
     * genau die Zahl, die sich auf den Nullpunkt des Nutzers bezieht - dauerhaft unbekannt.
     */
    fun applyOffsetReport(line: String): Boolean {
        val t = line.trim()
        if (!t.startsWith("[G54:") || !t.endsWith("]")) return false
        val position = Position.parse(t.removePrefix("[G54:").removeSuffix("]")) ?: return false
        lastWorkCoordinateOffset = position
        return true
    }

    /** Der zuletzt bekannte Arbeitsversatz, oder null wenn er noch nie gemeldet wurde. */
    val workCoordinateOffset: Position? get() = lastWorkCoordinateOffset

    fun parse(line: String): MachineStatus? {
        val trimmed = line.trim()
        if (!trimmed.startsWith('<') || !trimmed.endsWith('>')) return null

        val fields = trimmed.substring(1, trimmed.length - 1).split('|')
        if (fields.isEmpty()) return null

        val state = MachineState.parse(fields[0])
        var machine: Position? = null
        var work: Position? = null
        var sdRun: SdRun? = null

        for (field in fields.drop(1)) {
            val name = field.substringBefore(':')
            val value = field.substringAfter(':', "")
            when (name) {
                "MPos" -> machine = Position.parse(value)
                "WPos" -> work = Position.parse(value)
                "WCO" -> Position.parse(value)?.let { lastWorkCoordinateOffset = it }
                "SD" -> sdRun = SdRun.parse(value)
            }
        }

        // FluidNC meldet entweder MPos oder WPos. Fehlt die Arbeitsposition, laesst sie sich
        // aus der Maschinenposition und dem gemerkten Versatz berechnen.
        if (work == null && machine != null) {
            lastWorkCoordinateOffset?.let { work = machine - it }
        }

        return MachineStatus(state, machine, work, trimmed, sdRun)
    }

    fun reset() {
        lastWorkCoordinateOffset = null
    }
}

/** Ein Eintrag der SD-Karte, wie `$SD/List` ihn meldet. */
data class SdFile(val name: String, val sizeBytes: Int) {
    companion object {
        /**
         * Zerlegt `[FILE: Ruler Test.nc|SIZE:59263]`.
         *
         * Der Dateiname darf Leerzeichen enthalten - auf der Karte des Nutzers ist das die
         * Regel, nicht die Ausnahme. Deshalb wird am letzten `|SIZE:` getrennt und nicht am
         * ersten Leerzeichen.
         */
        fun parse(line: String): SdFile? {
            val t = line.trim()
            if (!t.startsWith("[FILE:") || !t.endsWith("]")) return null
            val rumpf = t.removePrefix("[FILE:").removeSuffix("]")
            val trenn = rumpf.lastIndexOf("|SIZE:")
            if (trenn < 0) return null
            val name = rumpf.substring(0, trenn).trim()
            val size = rumpf.substring(trenn + "|SIZE:".length).trim().toIntOrNull() ?: return null
            return if (name.isEmpty()) null else SdFile(name, size)
        }
    }
}

/** Wie eine Antwortzeile zu bewerten ist. */
sealed interface Response {
    /** Quittung fuer genau eine gesendete Zeile. */
    data object Ok : Response

    /** Die Maschine hat eine Zeile abgelehnt. Der Auftrag muss abgebrochen werden. */
    data class Error(val code: String) : Response

    /** Alarmzustand - Bewegungen werden erst nach dem Entsperren wieder ausgefuehrt. */
    data class Alarm(val code: String) : Response

    data class Status(val status: MachineStatus) : Response

    /** Meldungen und Banner, die keine Quittung darstellen. */
    data class Info(val text: String) : Response

    companion object {
        fun classify(line: String, parser: StatusParser): Response {
            val t = line.trim()
            parser.parse(t)?.let { return Status(it) }
            return when {
                t.equals("ok", ignoreCase = true) -> Ok
                t.startsWith("error:", ignoreCase = true) -> Error(t.removePrefix("error:").trim())
                t.startsWith("ALARM:", ignoreCase = true) -> Alarm(t.removePrefix("ALARM:").trim())
                else -> Info(t)
            }
        }
    }
}

/**
 * Befehle und Steuerzeichen von GRBL/FluidNC.
 *
 * Die Realtime-Zeichen werden OHNE Zeilenende gesendet und umgehen den Empfangspuffer - nur
 * deshalb wirken sie auch dann noch, wenn die Maschine mit abgearbeiteten Zeilen ausgelastet ist.
 */
object Commands {
    const val STATUS_QUERY = '?'
    const val FEED_HOLD = '!'
    const val CYCLE_START = '~'
    /** Ctrl-X: bricht alles ab und setzt den Interpreter zurueck. */
    const val SOFT_RESET = '\u0018'
    /** Beendet eine laufende Jog-Bewegung, ohne den uebrigen Zustand anzutasten. */
    const val JOG_CANCEL = '\u0085'

    const val HOME = "\$H"
    const val UNLOCK = "\$X"
    const val SETTINGS = "\$\$"
    const val BUILD_INFO = "\$I"

    /**
     * Startet eine Datei von der SD-Karte.
     *
     * Ab hier arbeitet die Maschine allein - der Auftrag ueberlebt einen Verbindungsabbruch.
     * Der Not-Halt bleibt trotzdem erreichbar, solange die Verbindung steht: Realtime-Zeichen
     * umgehen den Zeilenpuffer.
     */
    fun sdRun(path: String) = "\$SD/Run=${absolut(path)}"

    fun sdDelete(path: String) = "\$SD/Delete=${absolut(path)}"

    const val SD_LIST = "\$SD/List"
    const val SD_STATUS = "\$SD/Status"

    /** FluidNC erwartet den fuehrenden Schraegstrich; am Geraet nachgeprueft. */
    private fun absolut(path: String) = if (path.startsWith("/")) path else "/$path"

    /**
     * Setzt den Arbeitsnullpunkt auf die aktuelle Position (G10 L20 P0).
     * Anders als G92 ueberlebt dieser Versatz einen Soft-Reset.
     */
    fun zeroAxes(x: Boolean = true, y: Boolean = true, z: Boolean = false): String = buildString {
        append("G10 L20 P0")
        if (x) append(" X0")
        if (y) append(" Y0")
        if (z) append(" Z0")
    }

    /**
     * Relative Fahrt im Jog-Modus. Jog-Befehle lassen sich mit [JOG_CANCEL] abbrechen und
     * veraendern den modalen Zustand des Programms nicht - deshalb nicht einfach G91/G0.
     */
    fun jog(axis: Char, deltaMm: Float, feedMmMin: Int): String =
        "\$J=G91 G21 ${axis.uppercaseChar()}${formatMm(deltaMm)} F$feedMmMin"

    /** Fahrt auf eine absolute Arbeitskoordinate, ebenfalls im Jog-Modus. */
    fun jogAbsolute(axis: Char, positionMm: Float, feedMmMin: Int): String =
        "\$J=G90 G21 ${axis.uppercaseChar()}${formatMm(positionMm)} F$feedMmMin"

    /** Punkt als Dezimaltrennzeichen erzwingen - auf deutschem Geraet sonst ein Komma. */
    private fun formatMm(value: Float): String =
        String.format(java.util.Locale.ROOT, "%.3f", value)
            .trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
