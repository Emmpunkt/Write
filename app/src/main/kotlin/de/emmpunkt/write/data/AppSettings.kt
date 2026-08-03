package de.emmpunkt.write.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.layout.TextStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Alle dauerhaften Einstellungen.
 *
 * Die Vorgaben stammen aus der ausgelesenen Konfiguration des Plotters (FluidNC v4.0.3,
 * 192.168.2.18): Arbeitsbereich 155 x 105 mm aus $130/$131, Vorschuebe aus $110-$112.
 * Damit ist die App nach der Installation ohne Einrichtung brauchbar.
 */
data class AppSettings(
    // Verbindung
    val host: String = "192.168.2.18",
    /** Telnet ist der einzige Weg - siehe Transport.kt, warum HTTP nicht taugt. */
    val telnetPort: Int = 23,

    // Maschine
    val zUpMm: Float = 3f,
    val zDownMm: Float = -1.5f,
    val feedDrawMmMin: Int = 1200,
    val feedTravelMmMin: Int = 1500,
    val feedZMmMin: Int = 600,
    val workAreaXMm: Float = 155f,
    val workAreaYMm: Float = 105f,
    /** Zuege in Schreibrichtung zeichnen statt nach kuerzesten Wegen zu sortieren. */
    val naturalWriteOrder: Boolean = true,

    // Papier
    val paperOffsetXMm: Float = 0f,
    val paperOffsetYMm: Float = 0f,
    val paperWidthMm: Float = 148f,
    val paperHeightMm: Float = 105f,
    val marginMm: Float = 8f,

    // Schrift
    val fontId: String = Fonts.defaultId,
    val sizeMm: Float = 7f,
    val align: Align = Align.LEFT,
    val lineSpacing: Float = 1.15f,
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = -0.3f,
    val slantDeg: Float = 0f,

    /**
     * Text aus der Zeit vor der Notizliste.
     *
     * Wird nur noch gelesen: daraus entsteht beim ersten Start die erste Notiz. Bewusst nicht
     * geloescht, damit der Text nicht weg waere, falls die Umstellung schiefginge.
     */
    val lastText: String = "",

    /**
     * Die zuletzt geoeffnete Notiz.
     *
     * Gemerkt statt aus Zeitstempeln erschlossen: beim Wechseln wird die verlassene Notiz
     * gespeichert und traegt danach die neuere Zeit. Am Geraet oeffnete die App nach einem
     * Neustart deshalb eine andere Notiz als die zuletzt sichtbare.
     */
    val offeneNotizId: Long = 0L,
) {
    companion object {
        /**
         * Grenzen des Groessenreglers im Editor.
         *
         * fitSize() im core-Modul bekommt dieselben Werte als minMm/maxMm mitgegeben. Waeren
         * Reglerbereich und Suchbereich unterschiedlich, koennte "Einpassen" eine Groesse
         * liefern, die der Regler gar nicht darstellen kann - genau das fuehrte dazu, dass der
         * Reglerknopf bei einem sehr langen Text links festklebte, waehrend die Anzeige 2 mm
         * zeigte.
         */
        const val SCHRIFTGROESSE_MIN_MM = 3f
        const val SCHRIFTGROESSE_MAX_MM = 25f
    }

    /**
     * Das Profil aus den gespeicherten Einstellungen.
     *
     * Achtung: Verfahrweg, Beschleunigungen und Vorschubgrenzen darin sind nur ein RUECKFALL
     * fuer den Fall, dass keine Verbindung steht. Sobald eine Maschine verbunden ist, gilt
     * `applying(limits)` - was sie ueber sich meldet, schlaegt jeden gespeicherten Wert.
     */
    fun toMachineProfile() = MachineProfile(
        zUpMm = zUpMm,
        zDownMm = zDownMm,
        feedDrawMmMin = feedDrawMmMin,
        feedTravelMmMin = feedTravelMmMin,
        feedZMmMin = feedZMmMin,
        // Der Eilgang darf nie unter dem gesetzten Z-Vorschub liegen - sonst wirft das
        // Profil. Solange keine Maschine verbunden ist, ist der wahre Wert unbekannt; dann
        // ist der gesetzte Vorschub die sicherste Annahme.
        rapidZMmMin = maxOf(MachineProfile().rapidZMmMin, feedZMmMin),
        workAreaXMm = workAreaXMm,
        workAreaYMm = workAreaYMm,
        paperOffsetXMm = paperOffsetXMm,
        paperOffsetYMm = paperOffsetYMm,
        naturalWriteOrder = naturalWriteOrder,
    )

    fun toTextStyle() = TextStyle(
        fontId = fontId,
        sizeMm = sizeMm,
        align = align,
        lineSpacing = lineSpacing,
        letterSpacing = letterSpacing,
        wordSpacing = wordSpacing,
        slantDeg = slantDeg,
    )

    fun toFrame() = Frame(
        widthMm = paperWidthMm,
        heightMm = paperHeightMm,
        margins = Margins.all(marginMm),
    )

    /** Ob das eingestellte Blatt ueberhaupt auf den Tisch passt. */
    val paperFitsWorkArea: Boolean
        get() = paperOffsetXMm + paperWidthMm <= workAreaXMm + 0.01f &&
            paperOffsetYMm + paperHeightMm <= workAreaYMm + 0.01f
}

/** Uebliche Blattformate, jeweils quer und hoch. */
object PaperPresets {
    data class Preset(val name: String, val widthMm: Float, val heightMm: Float)

    val all = listOf(
        Preset("A6 quer", 148f, 105f),
        Preset("A7 quer", 105f, 74f),
        Preset("A7 hoch", 74f, 105f),
        Preset("Karteikarte A6", 148f, 105f),
        Preset("Haftnotiz 76", 76f, 76f),
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "write_settings")

/** Laden und Speichern der Einstellungen. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            host = p[Keys.host] ?: defaults.host,
            telnetPort = p[Keys.telnetPort] ?: defaults.telnetPort,
            zUpMm = p[Keys.zUp] ?: defaults.zUpMm,
            zDownMm = p[Keys.zDown] ?: defaults.zDownMm,
            feedDrawMmMin = p[Keys.feedDraw] ?: defaults.feedDrawMmMin,
            feedTravelMmMin = p[Keys.feedTravel] ?: defaults.feedTravelMmMin,
            feedZMmMin = p[Keys.feedZ] ?: defaults.feedZMmMin,
            workAreaXMm = p[Keys.workAreaX] ?: defaults.workAreaXMm,
            workAreaYMm = p[Keys.workAreaY] ?: defaults.workAreaYMm,
            naturalWriteOrder = p[Keys.naturalOrder] ?: defaults.naturalWriteOrder,
            paperOffsetXMm = p[Keys.paperOffsetX] ?: defaults.paperOffsetXMm,
            paperOffsetYMm = p[Keys.paperOffsetY] ?: defaults.paperOffsetYMm,
            paperWidthMm = p[Keys.paperWidth] ?: defaults.paperWidthMm,
            paperHeightMm = p[Keys.paperHeight] ?: defaults.paperHeightMm,
            marginMm = p[Keys.margin] ?: defaults.marginMm,
            fontId = p[Keys.fontId] ?: defaults.fontId,
            sizeMm = p[Keys.size] ?: defaults.sizeMm,
            align = p[Keys.align]?.let { runCatching { Align.valueOf(it) }.getOrNull() }
                ?: defaults.align,
            lineSpacing = p[Keys.lineSpacing] ?: defaults.lineSpacing,
            letterSpacing = p[Keys.letterSpacing] ?: defaults.letterSpacing,
            wordSpacing = p[Keys.wordSpacing] ?: defaults.wordSpacing,
            slantDeg = p[Keys.slant] ?: defaults.slantDeg,
            lastText = p[Keys.lastText] ?: defaults.lastText,
            offeneNotizId = p[Keys.offeneNotizId] ?: defaults.offeneNotizId,
        )
    }

    suspend fun update(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.host] = s.host
            p[Keys.telnetPort] = s.telnetPort
            p[Keys.zUp] = s.zUpMm
            p[Keys.zDown] = s.zDownMm
            p[Keys.feedDraw] = s.feedDrawMmMin
            p[Keys.feedTravel] = s.feedTravelMmMin
            p[Keys.feedZ] = s.feedZMmMin
            p[Keys.workAreaX] = s.workAreaXMm
            p[Keys.workAreaY] = s.workAreaYMm
            p[Keys.naturalOrder] = s.naturalWriteOrder
            p[Keys.paperOffsetX] = s.paperOffsetXMm
            p[Keys.paperOffsetY] = s.paperOffsetYMm
            p[Keys.paperWidth] = s.paperWidthMm
            p[Keys.paperHeight] = s.paperHeightMm
            p[Keys.margin] = s.marginMm
            p[Keys.fontId] = s.fontId
            p[Keys.size] = s.sizeMm
            p[Keys.align] = s.align.name
            p[Keys.lineSpacing] = s.lineSpacing
            p[Keys.letterSpacing] = s.letterSpacing
            p[Keys.wordSpacing] = s.wordSpacing
            p[Keys.slant] = s.slantDeg
            p[Keys.lastText] = s.lastText
            p[Keys.offeneNotizId] = s.offeneNotizId
        }
    }

    private object Keys {
        val host = stringPreferencesKey("host")
        val telnetPort = intPreferencesKey("telnet_port")
        val zUp = floatPreferencesKey("z_up")
        val zDown = floatPreferencesKey("z_down")
        val feedDraw = intPreferencesKey("feed_draw")
        val feedTravel = intPreferencesKey("feed_travel")
        val feedZ = intPreferencesKey("feed_z")
        val workAreaX = floatPreferencesKey("work_area_x")
        val workAreaY = floatPreferencesKey("work_area_y")
        val naturalOrder = booleanPreferencesKey("natural_write_order")
        val paperOffsetX = floatPreferencesKey("paper_offset_x")
        val paperOffsetY = floatPreferencesKey("paper_offset_y")
        val paperWidth = floatPreferencesKey("paper_width")
        val paperHeight = floatPreferencesKey("paper_height")
        val margin = floatPreferencesKey("margin")
        val fontId = stringPreferencesKey("font_id")
        val size = floatPreferencesKey("size_mm")
        val align = stringPreferencesKey("align")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val letterSpacing = floatPreferencesKey("letter_spacing")
        val wordSpacing = floatPreferencesKey("word_spacing")
        val slant = floatPreferencesKey("slant")
        val lastText = stringPreferencesKey("last_text")
        val offeneNotizId = longPreferencesKey("offene_notiz_id")
    }
}
