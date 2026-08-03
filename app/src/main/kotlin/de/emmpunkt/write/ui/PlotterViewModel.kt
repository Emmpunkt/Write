package de.emmpunkt.write.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineLimits
import de.emmpunkt.write.core.gcode.PlotJob
import de.emmpunkt.write.core.gcode.applying
import de.emmpunkt.write.core.gcode.toPlotJob
import de.emmpunkt.write.core.layout.LaidOutText
import de.emmpunkt.write.core.layout.fitSize
import de.emmpunkt.write.core.layout.layoutText
import java.util.Locale
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.NoteDatabase
import de.emmpunkt.write.data.NoteEntity
import de.emmpunkt.write.data.NoteRepository
import de.emmpunkt.write.data.PlotWakeLock
import de.emmpunkt.write.data.SettingsRepository
import de.emmpunkt.write.data.mitNotiz
import de.emmpunkt.write.data.neueNotiz
import de.emmpunkt.write.data.zuNotiz
import de.emmpunkt.write.machine.Axis
import de.emmpunkt.write.machine.HttpSdTransfer
import de.emmpunkt.write.machine.MachineController
import de.emmpunkt.write.machine.MachineStatus
import de.emmpunkt.write.machine.SdTransfer
import de.emmpunkt.write.machine.SendProgress
import de.emmpunkt.write.machine.TelnetTransport
import de.emmpunkt.write.machine.Transport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Was der Editor gerade anzeigt: Satz, Auftrag und die daraus folgenden Hinweise. */
data class DocumentState(
    val laidOut: LaidOutText? = null,
    val job: PlotJob? = null,
    /** Zeichen im Text, die die gewaehlte Schrift nicht kennt. */
    val unsupported: Set<Int> = emptySet(),
    /** Woerter, die hart getrennt werden mussten - im Editor markiert. */
    val overlongWords: Set<String> = emptySet(),
    val overflow: Boolean = false,
)

/** Zustand der Verbindung und eines etwaigen laufenden Auftrags. */
data class MachineUiState(
    val connected: Boolean = false,
    val homed: Boolean = false,
    val status: MachineStatus = MachineStatus.UNKNOWN,
    val busy: Boolean = false,
    val progress: SendProgress? = null,
    val message: String? = null,
    /**
     * Ob der laufende Auftrag von der SD-Karte kommt.
     *
     * Bei zwei Sendewegen muss sichtbar sein, welcher lief - sonst deutet der Nutzer eine
     * Stoerung falsch. Wirkt sich auch auf die Fortschrittsanzeige aus: der SD-Prozentwert
     * ist der Lesefortschritt der Datei und eilt der Bewegung voraus.
     */
    val sdLauf: Boolean = false,
)

class PlotterViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app)
    private val wakeLock = PlotWakeLock(app)
    private val notes = NoteRepository(NoteDatabase.dao(app))

    val notizen: StateFlow<List<NoteEntity>> = notes.notizen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _aktuelleNotizId = MutableStateFlow(0L)
    val aktuelleNotizId: StateFlow<Long> = _aktuelleNotizId.asStateFlow()

    /**
     * Laeuft, bis der Nutzer eine Weile nicht mehr tippt.
     *
     * Ohne diese Verzoegerung loeste jeder Tastendruck eine Schreibtransaktion aus.
     */
    private var speicherAuftrag: Job? = null

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _document = MutableStateFlow(DocumentState())
    val document: StateFlow<DocumentState> = _document.asStateFlow()

    private val _machine = MutableStateFlow(MachineUiState())
    val machine: StateFlow<MachineUiState> = _machine.asStateFlow()

    private var controller: MachineController? = null
    private var transport: Transport? = null

    /**
     * Beschleunigung, wie die verbundene Maschine sie meldet - `null`, solange keine
     * Verbindung stand. Bewusst nicht in den [AppSettings]: sie ist eine Eigenschaft des
     * Plotters und nichts, was der Nutzer einstellt.
     */
    /**
     * Was die verbundene Maschine ueber sich gemeldet hat.
     *
     * Bewusst NICHT in den [AppSettings]: das sind Eigenschaften des Geraets, keine
     * Einstellungen. Ein gespeicherter Verfahrweg ueberlebt sonst eine Umkonfiguration oder
     * wandert zu einem anderen Plotter mit - genau so entstand der Irrtum, die Untergrenze
     * liege bei 3, waehrend sie ausgelesen bei 10 lag.
     */
    private var maschinenwerte: MachineLimits = MachineLimits.UNKNOWN
    private var plotJobHandle: Job? = null
    private var statusPollHandle: Job? = null

    init {
        viewModelScope.launch {
            val loaded = repository.settings.first()
            _settings.value = loaded
            // Der Text kommt ab jetzt aus der Notiztabelle. `lastText` ist nur noch die Quelle
            // fuer die einmalige Umstellung und wird bewusst nicht mehr fortgeschrieben -
            // waere die Umstellung schiefgegangen, laege der alte Text sonst nirgends mehr.
            val notiz = notes.sicherstellenDassEineDaIst(
                lastText = loaded.lastText,
                vorgabe = loaded,
                jetzt = System.currentTimeMillis(),
            )
            uebernehmen(notiz)
        }
    }

    /** Legt eine geladene Notiz in den Arbeitszustand: Text und Schriftbild. */
    private fun uebernehmen(notiz: NoteEntity) {
        _aktuelleNotizId.value = notiz.id
        _text.value = notiz.text
        _settings.value = _settings.value.mitNotiz(notiz)
        recompute()
    }

    fun onTextChanged(value: String) {
        _text.value = value
        recompute()
        persist()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        recompute()
        persist()
    }

    /**
     * Waehrend eines Reglerzugs: Zustand und Vorschau nachfuehren, aber nichts speichern.
     *
     * Ein Zug loest dutzende Wertaenderungen aus. Wuerde jede davon in DataStore geschrieben,
     * schriebe die App waehrend eines einzigen Fingerstrichs dutzende Male auf den Speicher -
     * und ein abgebrochener Zug hinterliesse trotzdem einen gespeicherten Wert.
     */
    fun updateSettingsLive(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        recompute()
    }

    /** Beim Loslassen: den erreichten Wert einmal sichern. */
    fun commitSettings() = persist()

    /**
     * Setzt die groesste Schriftgroesse, bei der die Notiz sauber in den Rahmen passt.
     *
     * Findet sich keine, bleibt die Groesse stehen und die App sagt es. Stillschweigend die
     * Untergrenze zu setzen waere schlimmer als nichts zu tun: der Text liefe weiter ueber,
     * nur in unlesbarer Groesse.
     */
    fun autoFit() {
        val text = _text.value
        if (text.isBlank()) return

        val s = _settings.value
        val ergebnis = runCatching {
            fitSize(
                text,
                s.toTextStyle(),
                s.toFrame(),
                Fonts.load(s.fontId),
                minMm = AppSettings.SCHRIFTGROESSE_MIN_MM,
                maxMm = AppSettings.SCHRIFTGROESSE_MAX_MM,
            )
        }.getOrElse { e ->
            _machine.update { it.copy(message = "Einpassen nicht moeglich: ${e.message}") }
            return
        }

        if (!ergebnis.fits) {
            val minimum = String.format(Locale.GERMANY, "%.1f", ergebnis.sizeMm)
            _machine.update {
                it.copy(
                    message = "Passt auch bei $minimum mm nicht – Rand verkleinern, " +
                        "Text kürzen oder einen Bindestrich setzen.",
                )
            }
            return
        }

        updateSettings { it.copy(sizeMm = ergebnis.sizeMm) }
    }

    /**
     * Rechnet Satz und G-Code neu.
     *
     * Beides entsteht aus derselben Quelle: die Vorschau zeichnet die Strichzuege aus
     * [LaidOutText], und genau diese Zuege wandert der G-Code-Generator um. Eine Abweichung
     * zwischen Vorschau und Papier ist damit ausgeschlossen.
     */
    private fun recompute() {
        val s = _settings.value
        val result = runCatching {
            val font = Fonts.load(s.fontId)
            val laid = layoutText(_text.value, s.toTextStyle(), s.toFrame(), font)
            val job = laid.toPlotJob(s.toMachineProfile().applying(maschinenwerte))
            DocumentState(
                laidOut = laid,
                job = job,
                unsupported = laid.unsupported,
                overlongWords = laid.overlongWords,
                overflow = laid.overflow,
            )
        }
        _document.value = result.getOrElse { DocumentState() }
        result.exceptionOrNull()?.let { e ->
            _machine.update { it.copy(message = "Layout nicht moeglich: ${e.message}") }
        }
    }

    /**
     * Schreibt den Arbeitszustand in die aktuelle Notiz - verzoegert.
     *
     * Die Einstellungen wandern weiterhin in den DataStore: ihre Stilwerte sind ab jetzt die
     * Vorlage fuer die naechste neue Notiz.
     */
    private fun persist() {
        speicherAuftrag?.cancel()
        speicherAuftrag = viewModelScope.launch {
            delay(SPEICHER_VERZOEGERUNG_MS)
            schreiben()
        }
    }

    /** Schreibt sofort statt verzoegert - vor jedem Notizwechsel noetig. */
    private suspend fun sofortSpeichern() {
        speicherAuftrag?.cancel()
        schreiben()
    }

    private suspend fun schreiben() {
        val s = _settings.value
        repository.update(s)
        val id = _aktuelleNotizId.value
        // id 0 heisst: die Startnotiz steht noch nicht. Bis dahin gaebe es nichts zu
        // beschreiben, und ein Speichern legte versehentlich eine zweite Notiz an.
        if (id != 0L) {
            notes.speichern(s.zuNotiz(id, _text.value, System.currentTimeMillis()))
        }
    }

    // ---- Notizen ----

    fun notizOeffnen(id: Long) {
        if (_machine.value.busy) return
        viewModelScope.launch {
            // Erst den offenen Stand sichern, sonst geht das zuletzt Getippte verloren.
            sofortSpeichern()
            notes.laden(id)?.let { uebernehmen(it) }
        }
    }

    fun notizAnlegen() {
        if (_machine.value.busy) return
        viewModelScope.launch {
            sofortSpeichern()
            val jetzt = System.currentTimeMillis()
            val vorlage = notes.laden(_aktuelleNotizId.value)
            val id = notes.speichern(neueNotiz(vorlage, _settings.value, jetzt))
            notes.laden(id)?.let { uebernehmen(it) }
        }
    }

    fun notizLoeschen(id: Long) {
        if (_machine.value.busy) return
        viewModelScope.launch {
            val geleert = notes.loeschenOderLeeren(id, System.currentTimeMillis())
            when {
                // War es die letzte, bleibt sie offen - nur eben leer.
                geleert != null -> uebernehmen(geleert)
                // Die offene Notiz ist weg: auf die naechstbeste umschalten.
                id == _aktuelleNotizId.value ->
                    notes.zuletztBearbeiteteOderNull()?.let { uebernehmen(it) }
            }
        }
    }

    // ---- Maschine ----

    /**
     * Der Upload-Weg zur SD-Karte, oder `null`, wenn keine Verbindungsdaten stehen.
     *
     * Laeuft ueber HTTP zur WebUI und damit auf einem anderen Port als der Telnet-Kanal - am
     * Geraet geprueft: `POST /upload` funktioniert, obwohl `/command` `WebSocket dead` liefert.
     */
    private fun sdTransfer(): SdTransfer? {
        val s = _settings.value
        if (s.host.isBlank()) return null
        return HttpSdTransfer(s.host)
    }

    private fun buildController(): MachineController {
        val s = _settings.value
        transport?.let { runCatching { it.close() } }
        val t: Transport = TelnetTransport(s.host, s.telnetPort)
        transport = t
        // Bewusst ein Provider und keine Kopie: die Vorpruefung muss mit denselben Zahlen
        // rechnen wie der G-Code, auch wenn waehrend der Verbindung etwas verstellt wird.
        return MachineController(
            t,
            profileProvider = { _settings.value.toMachineProfile().applying(maschinenwerte) },
        ).also { controller = it }
    }

    fun connect() = withMachine("Verbinden") {
        val c = buildController()
        val info = c.connect().getOrThrow()
        observeController(c)
        startStatusPolling()
        info
    }

    /**
     * Fragt den Zustand fortlaufend ab, damit Position und Betriebszustand mitlaufen.
     *
     * Pausiert, solange ein Auftrag streamt oder ein Einzelbefehl aussteht: auf der
     * Telnet-Verbindung darf immer nur EINE Stelle lesen. Liefe die Abfrage parallel zum
     * Sender, fingen beide sich gegenseitig Antwortzeilen weg - der Sender verlöre
     * Quittungen und bliebe mit vollem Puffer stehen.
     */
    private fun startStatusPolling() {
        statusPollHandle?.cancel()
        statusPollHandle = viewModelScope.launch {
            while (isActive) {
                val c = controller
                if (c != null && !_machine.value.busy) {
                    runCatching { c.requestStatus() }
                    syncController()
                }
                delay(STATUS_POLL_MS)
            }
        }
    }

    fun disconnect() {
        statusPollHandle?.cancel()
        statusPollHandle = null
        controller?.disconnect()
        controller = null
        transport?.let { runCatching { it.close() } }
        transport = null
        _machine.value = MachineUiState(message = "Verbindung getrennt")
    }

    fun home() = withMachine("Homing") {
        requireController().home().getOrThrow()
        syncController()
        "Referenzfahrt abgeschlossen"
    }

    fun zeroXY() = withMachine("Nullen") {
        requireController().zeroXY().getOrThrow()
        syncController()
        "X und Y auf null gesetzt"
    }

    /**
     * Setzt den Z-Nullpunkt auf die aktuelle Hoehe.
     *
     * Diese Achse hat keinen Endschalter und bleibt bei der Referenzfahrt aussen vor. Ihr
     * Nullpunkt entsteht also ausschliesslich hier - und ueberlebt keinen Neustart der
     * Steuerung. Gemeint ist die Papieroberflaeche: davon ausgehend liegt Z_unten mit
     * Uebertravel darunter und Z_oben darueber.
     */
    fun zeroZ() = withMachine("Z nullen") {
        requireController().zeroZ().getOrThrow()
        syncController()
        "Z auf null gesetzt - das ist jetzt die Papierebene"
    }

    fun unlock() = withMachine("Entsperren") {
        requireController().unlock().getOrThrow()
        syncController()
        "Entsperrt"
    }

    fun jog(axis: Axis, deltaMm: Float) = withMachine("Fahren") {
        requireController().jog(axis, deltaMm).getOrThrow()
        syncController()
        null
    }

    fun refreshStatus() = withMachine(null) {
        requireController().requestStatus()
        syncController()
        null
    }

    /** Not-Halt: haelt an, bricht ab und hebt den Stift. Auch waehrend eines Auftrags. */
    fun emergencyStop() {
        plotJobHandle?.cancel()
        viewModelScope.launch {
            val c = controller
            if (c == null) {
                _machine.update { it.copy(message = "Nicht verbunden.") }
                return@launch
            }
            val result = c.emergencyStop()
            wakeLock.release()
            syncController()
            _machine.update {
                it.copy(
                    busy = false,
                    // Der Hinweis ist wichtiger als die Erfolgsmeldung: wer glaubt, der Stift
                    // sei oben, laesst ihn auf dem Papier stehen.
                    message = result.hint ?: "Not-Halt ausgefuehrt, Stift angehoben",
                )
            }
        }
    }

    /**
     * Sendet den Auftrag Zeile fuer Zeile ueber Telnet.
     *
     * Die Verbindung muss dabei durchgehend stehen: reisst sie, bleibt der Stift mitten im
     * Text auf dem Papier. Dagegen laufen die drei Wachhalte-Massnahmen.
     */
    fun plot() = sende(ueberSdKarte = false)

    /**
     * Legt den Auftrag als Datei auf der SD-Karte ab und startet ihn dort.
     *
     * Danach arbeitet die Maschine allein - ein Verbindungsabbruch bricht den Auftrag nicht
     * mehr ab. Der Not-Halt bleibt erreichbar, solange die Verbindung steht.
     */
    fun plotViaSd() = sende(ueberSdKarte = true)

    /**
     * Prueft den Auftrag und sendet ihn auf dem gewaehlten Weg.
     *
     * Die Pruefung liegt im [MachineController] und ist fuer beide Wege dieselbe; hier wird
     * nur angezeigt, was sie meldet.
     */
    private fun sende(ueberSdKarte: Boolean) {
        val c = controller
        val doc = _document.value
        val job = doc.job
        val laid = doc.laidOut

        if (c == null) {
            _machine.update { it.copy(message = "Nicht verbunden.") }
            return
        }
        if (job == null || laid == null || job.penDownCount == 0) {
            _machine.update { it.copy(message = "Kein Text zum Plotten.") }
            return
        }

        // Die Vorpruefung bekommt die Zuege in Blatt-Koordinaten; den Papier-Offset rechnet
        // sie selbst dazu, damit sie genau die Koordinaten prueft, die spaeter gefahren werden.
        val blattStrokes = laid.strokes
        val fluss = if (ueberSdKarte) {
            c.plotViaSd(job, blattStrokes, sdTransfer())
        } else {
            c.plot(job, blattStrokes)
        }

        plotJobHandle = viewModelScope.launch {
            _machine.update {
                it.copy(busy = true, message = null, progress = null, sdLauf = ueberSdKarte)
            }
            // Waehrend des Auftrags duerfen Prozessor und WLAN nicht einschlafen. Beim
            // SD-Weg genuegte streng genommen die Zeit des Uploads - aber solange die
            // Fortschrittsanzeige mitlaeuft, wird die Verbindung weiter gebraucht.
            wakeLock.acquire()
            try {
            fluss.collect { progress ->
                _machine.update { state ->
                    state.copy(
                        progress = progress,
                        busy = progress is SendProgress.Started || progress is SendProgress.Running,
                        message = when (progress) {
                            is SendProgress.Completed ->
                                if (ueberSdKarte) "Fertig geplottet (von SD-Karte)"
                                else "Fertig geplottet"
                            is SendProgress.Failed ->
                                progress.message + if (progress.penLifted) " Stift angehoben." else
                                    " ACHTUNG: Stift konnte nicht angehoben werden."
                            is SendProgress.Aborted -> "Abgebrochen"
                            else -> state.message
                        },
                    )
                }
            }
            } finally {
                // Auch bei Abbruch oder Fehler - sonst liefe der Akku leer.
                wakeLock.release()
            }
            syncController()
        }
    }

    fun cancelPlot() {
        plotJobHandle?.cancel()
        emergencyStop()
    }

    fun dismissMessage() = _machine.update { it.copy(message = null) }

    override fun onCleared() {
        // Sonst bliebe die Sperre haengen, wenn die App waehrend eines Auftrags beendet wird.
        wakeLock.release()
        controller?.disconnect()
        super.onCleared()
    }

    private fun requireController(): MachineController =
        controller ?: error("Nicht verbunden")

    private companion object {
        /** Schnell genug, um beim Fahren mitzulaufen, langsam genug fuer die Verbindung. */
        const val STATUS_POLL_MS = 600L

        /** Wartezeit nach der letzten Aenderung, bevor gespeichert wird. */
        const val SPEICHER_VERZOEGERUNG_MS = 500L
    }

    private fun observeController(c: MachineController) {
        viewModelScope.launch {
            c.connected.collect { connected -> _machine.update { it.copy(connected = connected) } }
        }
        viewModelScope.launch {
            // Die Maschine kennt ihre Grenzen besser als jede gespeicherte Vorgabe. Sobald
            // sie gelesen sind, wird mit ihnen gerechnet statt mit den Einstellungen.
            c.limits.collect { gelesen ->
                if (gelesen != maschinenwerte) {
                    maschinenwerte = gelesen
                    recompute()
                }
            }
        }
    }

    private fun syncController() {
        val c = controller ?: return
        _machine.update {
            it.copy(connected = c.connected.value, homed = c.homed.value, status = c.status.value)
        }
    }

    /** Fuehrt eine Maschinenaktion aus und schreibt Erfolg oder Fehler in die Oberflaeche. */
    private fun withMachine(label: String?, action: suspend () -> String?) {
        viewModelScope.launch {
            _machine.update { it.copy(busy = true) }
            val result = runCatching { action() }
            _machine.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { e ->
                            val what = label?.let { l -> "$l fehlgeschlagen: " } ?: ""
                            what + (e.message ?: e::class.simpleName ?: "Unbekannter Fehler")
                        },
                    ),
                )
            }
            syncController()
        }
    }
}
