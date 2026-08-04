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
import de.emmpunkt.write.core.decor.RahmenForm
import de.emmpunkt.write.core.decor.Zipfelseite
import de.emmpunkt.write.core.decor.rahmenZuege
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.Drehung
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.core.geometry.boundingBox
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

    // Blatt: was tatsaechlich auf dem Tisch liegt. Global, weil es die Maschine beschreibt und
    // nicht das Dokument - beim Umschalten der Notiz wechselt nicht das eingelegte Papier.
    val paperOffsetXMm: Float = 0f,
    val paperOffsetYMm: Float = 0f,
    val paperWidthMm: Float = 148f,
    val paperHeightMm: Float = 105f,
    /** Nur noch die Vorgabe fuer [blattFuellen] - der Textsatz kennt keinen Rand mehr. */
    val marginMm: Float = 8f,

    // Textrahmen: der Kasten, in den der Text gesetzt wird. Gemessen ab der linken unteren
    // Ecke des BLATTES, nicht des Tisches. Gehoert zum Dokument und wandert mit einer Vorlage
    // mit; die Vorgabe entspricht dem alten "Blatt A6 mit 8 mm Rand".
    val rahmenXMm: Float = 8f,
    val rahmenYMm: Float = 8f,
    val rahmenBreiteMm: Float = 132f,
    val rahmenHoeheMm: Float = 89f,
    /**
     * Wie der Text im Rahmen steht.
     *
     * Gehoert zum Rahmen, nicht zum Stil: Die Drehung betrifft die ganze Seite, nicht einzelne
     * Absaetze. A6 hoch passt nicht auf den Tisch - hochkant schreibt man, indem man das Blatt
     * quer legt und den Text dreht.
     */
    val drehung: Drehung = Drehung.GRAD_0,

    // Gezeichneter Rahmen: sitzt UM den Textrahmen, mit Abstand nach aussen. Dadurch bleibt der
    // Textsatz voellig unberuehrt - Umbruch und Einpassen kennen ihn gar nicht.
    val rahmenForm: RahmenForm = RahmenForm.KEINER,
    val rahmenAbstandMm: Float = 4f,
    val zipfel: Zipfelseite = Zipfelseite.UNTEN_LINKS,

    // Schrift. Schriftart, Groesse und Ausrichtung stehen NICHT mehr einzeln hier, sondern in
    // den Stilen - sonst gaebe es sie zweimal, einmal global und einmal je Stil. Was hier
    // bleibt, gilt fuer das ganze Dokument.
    val stile: List<Absatzstil> = listOf(GRUNDSTIL),
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
    init {
        require(stile.isNotEmpty()) { "Es muss immer mindestens einen Stil geben" }
    }

    companion object {
        /**
         * Der Stil, den es immer gibt.
         *
         * Er tritt an die Stelle der frueheren Einzelfelder fontId/sizeMm/align und ist damit
         * genau das, was die App vor den Absatzstilen konnte. Loeschen laesst er sich nicht -
         * ein Dokument ohne Stil haette kein Schriftbild.
         */
        val GRUNDSTIL = Absatzstil(
            name = "Text",
            fontId = Fonts.defaultId,
            sizeMm = 7f,
            align = Align.LEFT,
        )

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
        // Der Textsatz rechnet in Rahmen-Koordinaten. Auf den Tisch kommt der Rahmen erst
        // hier - und dafuer zaehlen beide Verschiebungen: die des Blattes am Anschlag und
        // die des Rahmens auf dem Blatt.
        paperOffsetXMm = ursprungXMm,
        paperOffsetYMm = ursprungYMm,
        naturalWriteOrder = naturalWriteOrder,
    )

    /**
     * Aendert genau einen Stil und laesst die uebrigen stehen.
     *
     * Der Griff, mit dem die Regler arbeiten: Sie wirken immer auf den Stil des Absatzes, in
     * dem der Cursor steht - nicht auf den Absatz selbst. Wer denselben Stil zweimal zugewiesen
     * hat, aendert damit bewusst beide Absaetze.
     */
    fun mitStil(index: Int, aendern: (Absatzstil) -> Absatzstil): AppSettings =
        copy(stile = stile.mapIndexed { i, stil -> if (i == index) aendern(stil) else stil })

    /**
     * Legt einen weiteren Stil an - als Kopie von [vorlage], damit man nur das aendern muss,
     * was anders sein soll.
     */
    fun mitNeuemStil(vorlage: Int): AppSettings {
        val quelle = stile.getOrElse(vorlage) { stile.first() }
        return copy(stile = stile + quelle.copy(name = freierStilname()))
    }

    /**
     * Entfernt einen Stil. Der erste bleibt immer stehen - ohne ihn haette das Dokument kein
     * Schriftbild mehr.
     */
    fun ohneStil(index: Int): AppSettings =
        if (index <= 0 || index >= stile.size) this
        else copy(stile = stile.filterIndexed { i, _ -> i != index })

    /** "Stil 2", "Stil 3", ... - der erste Name, den es noch nicht gibt. */
    private fun freierStilname(): String {
        val vergeben = stile.map { it.name }.toSet()
        var n = stile.size + 1
        while ("Stil $n" in vergeben) n++
        return "Stil $n"
    }

    /** Wo die linke untere Ecke des Textrahmens auf dem Tisch liegt. */
    val ursprungXMm: Float get() = paperOffsetXMm + rahmenXMm
    val ursprungYMm: Float get() = paperOffsetYMm + rahmenYMm

    /**
     * Die Stile als fertige Schriftbilder fuer den Satz.
     *
     * Schrift, Groesse und Ausrichtung kommen aus dem jeweiligen Stil, alles Uebrige aus diesen
     * Einstellungen - das Feintuning gilt fuer das ganze Dokument.
     */
    fun toTextStyles(): List<TextStyle> = stile.alsTextStyles(this)

    /** Das Schriftbild eines einzelnen Stils; auf den ersten zurueckfallend. */
    fun toTextStyle(stilIndex: Int = 0): TextStyle =
        toTextStyles().getOrElse(stilIndex) { toTextStyles().first() }

    /**
     * Der Textrahmen als Satzflaeche.
     *
     * Ohne Raender: der Rahmen IST der nutzbare Bereich. Ein Rand darin waere eine zweite
     * Stellschraube fuer dieselbe Sache - wer Abstand zum Blattrand will, schiebt den Rahmen.
     */
    fun toFrame() = Frame(
        widthMm = rahmenBreiteMm,
        heightMm = rahmenHoeheMm,
        margins = Margins.all(0f),
    )

    /**
     * Die Zuege des gezeichneten Rahmens - in RAHMEN-Koordinaten, wie der Textsatz.
     *
     * Der Rahmen umschliesst den Textkasten mit [rahmenAbstandMm] Luft; sein Ursprung liegt
     * deshalb bei minus diesem Abstand. Auf den Tisch kommt er wie der Text ueber
     * `toMachineProfile()` - beide durchlaufen dieselbe Verschiebung, also koennen sie gar
     * nicht gegeneinander verrutschen.
     */
    fun zierrahmenZuege(): List<Polyline> {
        val a = rahmenAbstandMm.coerceAtLeast(0f)
        return rahmenZuege(rahmenForm, rahmenBreiteMm + 2 * a, rahmenHoeheMm + 2 * a, zipfel)
            .map { it.translate(-a, -a) }
    }

    /**
     * Ob der gezeichnete Rahmen noch aufs Blatt passt.
     *
     * Eigene Pruefung neben [rahmenPasstAufsBlatt]: Der Textkasten kann bequem passen und der
     * Zierrahmen trotzdem ueber die Karte hinausragen - dann schriebe der Stift daneben auf
     * den Tisch.
     */
    val zierrahmenPasstAufsBlatt: Boolean
        get() {
            // Gemessen an den ERZEUGTEN Zuegen, nicht nachgerechnet: Der Zipfel der
            // Sprechblase haengt aussen an, und jede kuenftige Form darf das ebenso. Eine
            // zweite Rechnung neben `rahmenZuege` liefe frueher oder spaeter auseinander -
            // genau daran ist diese Pruefung schon einmal vorbeigelaufen.
            val box = zierrahmenZuege().boundingBox() ?: return true
            return rahmenXMm + box.minX >= -0.01f && rahmenYMm + box.minY >= -0.01f &&
                rahmenXMm + box.maxX <= paperWidthMm + 0.01f &&
                rahmenYMm + box.maxY <= paperHeightMm + 0.01f
        }

    /** Blatt und Rahmen, wie die Vorschau sie braucht. */
    fun toBlattbild() = Blattbild(
        blattBreiteMm = paperWidthMm,
        blattHoeheMm = paperHeightMm,
        rahmenXMm = rahmenXMm,
        rahmenYMm = rahmenYMm,
        frame = toFrame(),
    )

    /** Ob das eingestellte Blatt ueberhaupt auf den Tisch passt. */
    val blattPasstAufTisch: Boolean
        get() = paperOffsetXMm >= -0.01f && paperOffsetYMm >= -0.01f &&
            paperOffsetXMm + paperWidthMm <= workAreaXMm + 0.01f &&
            paperOffsetYMm + paperHeightMm <= workAreaYMm + 0.01f

    /** Ob der Textrahmen innerhalb des Blattes liegt. */
    val rahmenPasstAufsBlatt: Boolean
        get() = rahmenXMm >= -0.01f && rahmenYMm >= -0.01f &&
            rahmenXMm + rahmenBreiteMm <= paperWidthMm + 0.01f &&
            rahmenYMm + rahmenHoeheMm <= paperHeightMm + 0.01f

    /**
     * Legt den Rahmen auf das ganze Blatt, um [marginMm] eingerueckt.
     *
     * Der eine Griff, mit dem man aus einem frisch gewaehlten Blattformat einen brauchbaren
     * Rahmen bekommt, ohne vier Zahlen auszurechnen. Frisst der Rand das Blatt auf, faellt er
     * weg statt einen Rahmen der Breite 0 zu bauen - `Frame` wirft dabei im Konstruktor.
     */
    fun blattFuellen(): AppSettings {
        val rand = marginMm.coerceAtLeast(0f)
        val breite = paperWidthMm - 2f * rand
        val hoehe = paperHeightMm - 2f * rand
        return if (breite > 0f && hoehe > 0f) {
            copy(
                rahmenXMm = rand, rahmenYMm = rand,
                rahmenBreiteMm = breite, rahmenHoeheMm = hoehe,
            )
        } else {
            copy(
                rahmenXMm = 0f, rahmenYMm = 0f,
                rahmenBreiteMm = paperWidthMm, rahmenHoeheMm = paperHeightMm,
            )
        }
    }
}

/**
 * Was die Vorschau zeichnet: das Blatt und den Rahmen darin.
 *
 * Als eigener Typ, damit `PreviewCanvas` nicht die kompletten Einstellungen entgegennehmen
 * muss - sie zeichnet Papier und Striche, sie hat mit Vorschueben und Hostnamen nichts zu tun.
 */
data class Blattbild(
    val blattBreiteMm: Float,
    val blattHoeheMm: Float,
    val rahmenXMm: Float,
    val rahmenYMm: Float,
    val frame: Frame,
)

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
        val geladen = AppSettings(
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
            rahmenXMm = p[Keys.rahmenX] ?: defaults.rahmenXMm,
            rahmenYMm = p[Keys.rahmenY] ?: defaults.rahmenYMm,
            rahmenBreiteMm = p[Keys.rahmenBreite] ?: defaults.rahmenBreiteMm,
            rahmenHoeheMm = p[Keys.rahmenHoehe] ?: defaults.rahmenHoeheMm,
            drehung = p[Keys.drehung]?.let { runCatching { Drehung.valueOf(it) }.getOrNull() }
                ?: defaults.drehung,
            rahmenForm = p[Keys.rahmenForm]
                ?.let { runCatching { RahmenForm.valueOf(it) }.getOrNull() } ?: defaults.rahmenForm,
            rahmenAbstandMm = p[Keys.rahmenAbstand] ?: defaults.rahmenAbstandMm,
            zipfel = p[Keys.zipfel]?.let { runCatching { Zipfelseite.valueOf(it) }.getOrNull() }
                ?: defaults.zipfel,
            // Fehlen die Stile, stammen die Einstellungen aus der Zeit davor: dann bildet das
            // damalige Schriftbild den Grundstil. Die Vorgabe waere hier falsch - wer in 12 mm
            // Zierschrift geschrieben hat, faende nach dem Update 7 mm Allure vor.
            stile = stileAusText(p[Keys.stile].orEmpty()).ifEmpty {
                val grund = AppSettings.GRUNDSTIL
                listOf(
                    grund.copy(
                        fontId = p[Keys.fontId] ?: grund.fontId,
                        sizeMm = p[Keys.size] ?: grund.sizeMm,
                        align = p[Keys.align]?.let { runCatching { Align.valueOf(it) }.getOrNull() }
                            ?: grund.align,
                    ),
                )
            },
            lineSpacing = p[Keys.lineSpacing] ?: defaults.lineSpacing,
            letterSpacing = p[Keys.letterSpacing] ?: defaults.letterSpacing,
            wordSpacing = p[Keys.wordSpacing] ?: defaults.wordSpacing,
            slantDeg = p[Keys.slant] ?: defaults.slantDeg,
            lastText = p[Keys.lastText] ?: defaults.lastText,
            offeneNotizId = p[Keys.offeneNotizId] ?: defaults.offeneNotizId,
        )

        // Wer die App vor der Trennung von Blatt und Rahmen benutzt hat, hat gespeicherte
        // Blattwerte, aber keinen Rahmen. Die Vorgabe (A6 mit 8 mm) waere dort schlicht
        // falsch: bei einer 76er Haftnotiz raunte sie ueber den Rand hinaus. Aus dem
        // gespeicherten Blatt samt Rand entsteht genau der Rahmen, der vorher gesetzt wurde.
        if (p[Keys.rahmenBreite] == null) geladen.blattFuellen() else geladen
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
            p[Keys.rahmenX] = s.rahmenXMm
            p[Keys.rahmenY] = s.rahmenYMm
            p[Keys.rahmenBreite] = s.rahmenBreiteMm
            p[Keys.rahmenHoehe] = s.rahmenHoeheMm
            p[Keys.drehung] = s.drehung.name
            p[Keys.rahmenForm] = s.rahmenForm.name
            p[Keys.rahmenAbstand] = s.rahmenAbstandMm
            p[Keys.zipfel] = s.zipfel.name
            p[Keys.stile] = stileAlsText(s.stile)
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
        val rahmenX = floatPreferencesKey("rahmen_x")
        val rahmenY = floatPreferencesKey("rahmen_y")
        val rahmenBreite = floatPreferencesKey("rahmen_breite")
        val rahmenHoehe = floatPreferencesKey("rahmen_hoehe")
        val drehung = stringPreferencesKey("drehung")
        val rahmenForm = stringPreferencesKey("rahmen_form")
        val rahmenAbstand = floatPreferencesKey("rahmen_abstand")
        val zipfel = stringPreferencesKey("zipfel")
        val stile = stringPreferencesKey("stile")

        // Nur noch zum LESEN: aus ihnen entsteht der Grundstil, wenn "stile" fehlt.
        // Geschrieben werden sie nicht mehr - sonst gaebe es das Schriftbild zweimal.
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
