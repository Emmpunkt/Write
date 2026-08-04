package de.emmpunkt.write.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineLimits
import de.emmpunkt.write.core.gcode.PlotJob
import de.emmpunkt.write.core.gcode.applying
import de.emmpunkt.write.core.gcode.orderedStrokes
import de.emmpunkt.write.core.gcode.plotJobAus
import de.emmpunkt.write.core.gcode.toPlotJob
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.core.layout.LaidOutText
import de.emmpunkt.write.core.layout.absaetzeAus
import de.emmpunkt.write.core.layout.fitSkalierung
import de.emmpunkt.write.core.layout.layoutAbsaetze
import de.emmpunkt.write.core.layout.skaliert
import java.util.Locale
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.BogenBefund
import de.emmpunkt.write.data.NoteDatabase
import de.emmpunkt.write.data.NoteEntity
import de.emmpunkt.write.data.NoteRepository
import de.emmpunkt.write.data.PlotWakeLock
import de.emmpunkt.write.data.SerienZustand
import de.emmpunkt.write.data.Serienlauf
import de.emmpunkt.write.data.SettingsRepository
import de.emmpunkt.write.data.TemplateEntity
import de.emmpunkt.write.data.TemplateRepository
import de.emmpunkt.write.data.WerteZeile
import de.emmpunkt.write.data.einsetzen
import de.emmpunkt.write.data.mitNotiz
import de.emmpunkt.write.data.mitVorlage
import de.emmpunkt.write.data.neueNotiz
import de.emmpunkt.write.data.neueVorlage
import de.emmpunkt.write.data.platzhalterIn
import de.emmpunkt.write.data.pruefeBogen
import de.emmpunkt.write.data.rahmenFehler
import de.emmpunkt.write.data.vorlagenFehler
import de.emmpunkt.write.data.werteZeilen
import de.emmpunkt.write.data.zuNotiz
import de.emmpunkt.write.data.zuVorlage
import de.emmpunkt.write.data.zuordnung
import de.emmpunkt.write.data.zuordnungNachTextaenderung
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Was der Editor gerade anzeigt: Satz, Auftrag und die daraus folgenden Hinweise. */
data class DocumentState(
    val laidOut: LaidOutText? = null,
    /**
     * Alles, was auf das Blatt kommt: erst der gezeichnete Rahmen, dann der Text.
     *
     * Der Rahmen zuerst - ein paar lange Zuege am Anfang zeigen sofort, ob das Blatt richtig
     * liegt, bevor Hunderte Zeilen Text laufen.
     */
    val zuege: List<Polyline> = emptyList(),
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

/**
 * Alles, was der Serie-Reiter anzeigt.
 *
 * Bewusst EIN Zustand statt eines Dutzends einzelner Fluesse: Die Felder haengen voneinander ab
 * - aus dem Vorlagentext folgen die Spalten, daraus die Zeilen, daraus die Befunde. Getrennte
 * Fluesse koennten fuer einen Moment zueinander unpassende Staende zeigen.
 */
data class SerieUiState(
    val vorlagen: List<TemplateEntity> = emptyList(),
    val aktuelleId: Long = 0L,
    val name: String = "",
    val text: String = "",
    val werte: String = "",
    /** Arbeitszustand der Vorlage - getrennt vom Editor, damit sich beide nicht stoeren. */
    val settings: AppSettings = AppSettings(),
    /** Stil je Absatz des Vorlagentextes. Platzhalter enthalten keine Zeilenumbrueche, also
     * bleibt sie auch nach dem Einsetzen der Werte gueltig. */
    val zuordnung: List<Int> = emptyList(),
    /** Der Absatz, in dem der Cursor im Vorlagentext steht. */
    val absatzIndex: Int = 0,
    val spalten: List<String> = emptyList(),
    val zeilen: List<WerteZeile> = emptyList(),
    val befunde: List<BogenBefund> = emptyList(),
    /**
     * Der erste brauchbare Bogen, fertig gesetzt - fuer die Vorschau.
     *
     * Der Satz entsteht hier und nicht im Bildschirm: `PreviewCanvas` zeichnet fertige
     * Strichzuege, und Layout-Arbeit gehoert nicht in eine Compose-Funktion, die bei jeder
     * Neuzeichnung laeuft.
     */
    val vorschau: LaidOutText? = null,
    /** Vorlagenfehler oder unmoeglicher Rahmen - beides sperrt den Start. */
    val fehler: String? = null,
    val lauf: SerienZustand? = null,
) {
    val bogenGesamt: Int get() = zeilen.count { it.fehler == null }

    val startbar: Boolean
        get() = fehler == null &&
            bogenGesamt > 0 &&
            zeilen.all { it.fehler == null } &&
            befunde.all { it.inOrdnung } &&
            lauf == null
}

class PlotterViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository(app)
    private val wakeLock = PlotWakeLock(app)
    private val notes = NoteRepository(NoteDatabase.dao(app))
    private val templates = TemplateRepository(NoteDatabase.templateDao(app))

    private val _serie = MutableStateFlow(SerieUiState())
    val serie: StateFlow<SerieUiState> = _serie.asStateFlow()

    private var serienlauf: Serienlauf? = null
    private var serienAuftrag: Job? = null
    private var vorlageSpeichern: Job? = null

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

    /**
     * Welcher Stil fuer welchen Absatz gilt.
     *
     * Bewusst neben [_text] und nicht in den [AppSettings]: Die Zuordnung gehoert zum Text -
     * sie zaehlt dessen Absaetze - und nicht zum Schriftbild. Beim Tippen wird sie
     * nachgefuehrt, sonst rutschte hinter jedem eingefuegten Absatz alles eine Stelle.
     */
    private val _zuordnung = MutableStateFlow<List<Int>>(emptyList())
    val zuordnung: StateFlow<List<Int>> = _zuordnung.asStateFlow()

    /** Der Absatz, in dem der Cursor steht. Der Editor meldet ihn, die Stilleiste zeigt ihn. */
    private val _absatzIndex = MutableStateFlow(0)
    val absatzIndex: StateFlow<Int> = _absatzIndex.asStateFlow()

    /** Der Stil des Absatzes am Cursor - genau der, den die Regler bearbeiten. */
    val stilIndex: StateFlow<Int> = combine(_zuordnung, _absatzIndex) { zuordnung, absatz ->
        zuordnung.getOrElse(absatz) { 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
                offeneId = loaded.offeneNotizId,
            )
            uebernehmen(notiz)

            // Die Vorlagenliste laeuft mit; ohne Vorlage bleibt der Reiter leer, das ist
            // gueltig. Als eigener Auftrag, weil der Sammler nie zurueckkehrt.
            launch {
                templates.vorlagen.collect { liste -> _serie.update { it.copy(vorlagen = liste) } }
            }
            templates.zuletztBearbeiteteOderNull()?.let { vorlageUebernehmen(it) }
        }
    }

    /** Legt eine geladene Notiz in den Arbeitszustand: Text und Schriftbild. */
    private suspend fun uebernehmen(notiz: NoteEntity) {
        _aktuelleNotizId.value = notiz.id
        _text.value = notiz.text
        _settings.value = _settings.value.mitNotiz(notiz).copy(offeneNotizId = notiz.id)
        // Die gespeicherte Zuordnung kann kuerzer oder laenger sein als der Text - etwa bei
        // einer Notiz aus der Zeit vor den Stilen. Einmal durch die Nachfuehrung geschickt,
        // passt sie danach genau auf die Absaetze.
        _zuordnung.value = zuordnungNachTextaenderung(
            alt = notiz.text.split('\n'),
            neu = notiz.text.split('\n'),
            zuordnung = notiz.zuordnung(),
        )
        _absatzIndex.value = 0
        recompute()
        // Sofort merken und nicht erst beim naechsten Speichern: wird die App direkt nach dem
        // Wechsel beendet, oeffnete sie sonst wieder die vorige Notiz.
        repository.update(_settings.value)
    }

    fun onTextChanged(value: String) {
        _zuordnung.value = zuordnungNachTextaenderung(
            alt = _text.value.split('\n'),
            neu = value.split('\n'),
            zuordnung = _zuordnung.value,
        )
        _text.value = value
        recompute()
        persist()
    }

    /** Der Editor meldet, in welchem Absatz der Cursor jetzt steht. */
    fun onCursorAbsatz(index: Int) {
        _absatzIndex.value = index
    }

    /**
     * Weist dem Absatz am Cursor einen Stil zu.
     *
     * Die Zuordnung wird bis zu diesem Absatz mit dem ersten Stil aufgefuellt, falls sie kuerzer
     * ist - das kommt bei einer Notiz aus der Zeit vor den Stilen vor.
     */
    fun stilZuweisen(stilIndex: Int) {
        val absatz = _absatzIndex.value
        val anzahl = _text.value.split('\n').size
        _zuordnung.value = List(maxOf(anzahl, absatz + 1)) { i ->
            if (i == absatz) stilIndex else _zuordnung.value.getOrElse(i) { 0 }
        }
        recompute()
        persist()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        recompute()
        serieAufFrischeGlobaleLegen()
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
        serieAufFrischeGlobaleLegen()
    }

    /**
     * Legt den Serie-Arbeitszustand neu auf die AKTUELLEN globalen Werte.
     *
     * Ohne das war der Serie-Reiter ab dem Oeffnen einer Vorlage eingefroren: Eine Aenderung
     * an Maschinenadresse oder Vorschub wirkte im Editor sofort, im Serienmodus nie - und ein
     * Satz lief mit den alten Werten. Am Geraet gefunden (2026-08-04).
     *
     * Der Rahmen und das Schriftbild der Vorlage ueberleben das, denn genau die legt
     * [mitVorlage] wieder darueber.
     */
    private fun serieAufFrischeGlobaleLegen() {
        val s = _serie.value
        if (s.aktuelleId == 0L) return
        // Nur Transportmittel fuer die Vorlagen-eigenen Felder; der Zeitstempel wird hier
        // nicht gespeichert und ist deshalb belanglos.
        val vorlage = s.settings.zuVorlage(s.aktuelleId, s.name, s.text, s.werte, jetzt = 0L)
        _serie.update { it.copy(settings = _settings.value.mitVorlage(vorlage)) }
        serieNeuRechnen()
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
            fitSkalierung(
                text,
                s.toTextStyles(),
                _zuordnung.value,
                { Fonts.load(it) },
                s.toFrame(),
                drehung = s.drehung,
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

        // Alle Stile wandern gemeinsam: Was doppelt so gross war, bleibt doppelt so gross.
        val neueGroessen = skaliert(s.toTextStyles(), ergebnis.sizeMm).map { it.sizeMm }
        updateSettings { alt ->
            alt.copy(
                stile = alt.stile.mapIndexed { i, stil ->
                    stil.copy(sizeMm = neueGroessen.getOrElse(i) { stil.sizeMm })
                },
            )
        }
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
            val laid = layoutAbsaetze(
                absaetzeAus(_text.value, s.toTextStyles(), _zuordnung.value) { Fonts.load(it) },
                s.toFrame(),
                s.drehung,
            )
            val profil = s.toMachineProfile().applying(maschinenwerte)
            val alle = s.zierrahmenZuege() + laid.orderedStrokes(profil)
            val job = plotJobAus(alle, profil)
            DocumentState(
                laidOut = laid,
                zuege = alle,
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
            notes.speichern(
                s.zuNotiz(id, _text.value, System.currentTimeMillis(), _zuordnung.value),
            )
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

    // ---- Vorlagen ----

    /** Legt eine geladene Vorlage in den Serie-Arbeitszustand. */
    private fun vorlageUebernehmen(v: TemplateEntity) {
        _serie.update {
            it.copy(
                aktuelleId = v.id,
                name = v.name,
                text = v.text,
                werte = v.werte,
                // Basis sind die aktuellen Einstellungen: Verbindung, Vorschuebe und
                // Papier-Offset kommen von dort, Schriftbild und Blatt aus der Vorlage.
                settings = _settings.value.mitVorlage(v),
                zuordnung = zuordnungNachTextaenderung(
                    alt = v.text.split('\n'),
                    neu = v.text.split('\n'),
                    zuordnung = v.zuordnung(),
                ),
                absatzIndex = 0,
                lauf = null,
            )
        }
        serieNeuRechnen()
    }

    /**
     * Rechnet Spalten, Zeilen, Befunde und Vorschau neu.
     *
     * Laeuft nach jeder Aenderung an Text, Werten oder Schriftbild. Bei wenigen Dutzend Bogen
     * ist das eine Sache von Millisekunden; erst bei Hunderten waere ein Aufschub noetig.
     */
    private fun serieNeuRechnen() {
        val s = _serie.value
        val spalten = platzhalterIn(s.text)
        val zeilen = werteZeilen(s.werte, spalten)

        val fehler = vorlagenFehler(s.text) ?: rahmenFehler(s.settings)
        val brauchbare = zeilen.filter { it.fehler == null }

        val befunde = if (fehler != null) {
            emptyList()
        } else {
            runCatching {
                pruefeBogen(
                    zeilen = brauchbare,
                    vorlage = s.text,
                    stile = s.settings.toTextStyles(),
                    zuordnung = s.zuordnung,
                    frame = s.settings.toFrame(),
                    schrift = { Fonts.load(it) },
                    drehung = s.settings.drehung,
                )
            }.getOrDefault(emptyList())
        }

        val vorschau = if (fehler != null) {
            null
        } else {
            brauchbare.firstOrNull()?.let { erste ->
                runCatching {
                    layoutAbsaetze(
                        absaetzeAus(
                            einsetzen(s.text, erste.felder),
                            s.settings.toTextStyles(),
                            s.zuordnung,
                        ) { Fonts.load(it) },
                        s.settings.toFrame(),
                        s.settings.drehung,
                    )
                }.getOrNull()
            }
        }

        _serie.update {
            it.copy(
                spalten = spalten,
                zeilen = zeilen,
                befunde = befunde,
                vorschau = vorschau,
                fehler = fehler,
            )
        }
    }

    fun vorlageOeffnen(id: Long) {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            vorlageSofortSpeichern()
            templates.laden(id)?.let { vorlageUebernehmen(it) }
        }
    }

    fun vorlageAnlegen() {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            vorlageSofortSpeichern()
            val id = templates.speichern(neueVorlage(_settings.value, System.currentTimeMillis()))
            templates.laden(id)?.let { vorlageUebernehmen(it) }
        }
    }

    fun vorlageLoeschen(id: Long) {
        if (_machine.value.busy || _serie.value.lauf != null) return
        viewModelScope.launch {
            templates.loeschen(id)
            if (id == _serie.value.aktuelleId) {
                // Auf die naechstbeste umschalten - oder auf den leeren Zustand.
                val ersatz = templates.zuletztBearbeiteteOderNull()
                if (ersatz != null) {
                    vorlageUebernehmen(ersatz)
                } else {
                    _serie.value = SerieUiState(vorlagen = _serie.value.vorlagen)
                }
            }
        }
    }

    fun vorlageNameGeaendert(v: String) = serieFeldGeaendert { it.copy(name = v) }

    fun vorlageTextGeaendert(v: String) = serieFeldGeaendert { alt ->
        alt.copy(
            text = v,
            zuordnung = zuordnungNachTextaenderung(
                alt = alt.text.split('\n'),
                neu = v.split('\n'),
                zuordnung = alt.zuordnung,
            ),
        )
    }

    /** Der Serie-Reiter meldet, in welchem Absatz des Vorlagentextes der Cursor steht. */
    fun serieCursorAbsatz(index: Int) = _serie.update { it.copy(absatzIndex = index) }

    /** Weist dem Absatz am Cursor im Vorlagentext einen Stil zu. */
    fun serieStilZuweisen(stilIndex: Int) = serieFeldGeaendert { alt ->
        val anzahl = alt.text.split('\n').size
        alt.copy(
            zuordnung = List(maxOf(anzahl, alt.absatzIndex + 1)) { i ->
                if (i == alt.absatzIndex) stilIndex else alt.zuordnung.getOrElse(i) { 0 }
            },
        )
    }

    fun werteGeaendert(v: String) = serieFeldGeaendert { it.copy(werte = v) }

    fun serieSettingsAendern(transform: (AppSettings) -> AppSettings) =
        serieFeldGeaendert { it.copy(settings = transform(it.settings)) }

    /** Waehrend eines Reglerzugs: rechnen, aber nicht speichern - wie im Editor. */
    fun serieSettingsLive(transform: (AppSettings) -> AppSettings) {
        _serie.update { it.copy(settings = transform(it.settings)) }
        serieNeuRechnen()
    }

    fun serieSettingsCommit() = vorlageVerzoegertSpeichern()

    private fun serieFeldGeaendert(transform: (SerieUiState) -> SerieUiState) {
        _serie.update(transform)
        serieNeuRechnen()
        vorlageVerzoegertSpeichern()
    }

    private fun vorlageVerzoegertSpeichern() {
        vorlageSpeichern?.cancel()
        vorlageSpeichern = viewModelScope.launch {
            delay(SPEICHER_VERZOEGERUNG_MS)
            vorlageSchreiben()
        }
    }

    private suspend fun vorlageSofortSpeichern() {
        vorlageSpeichern?.cancel()
        vorlageSchreiben()
    }

    private suspend fun vorlageSchreiben() {
        val s = _serie.value
        // id 0 heisst: noch keine Vorlage offen. Speichern legte sonst versehentlich eine an.
        if (s.aktuelleId == 0L) return
        templates.speichern(
            s.settings.zuVorlage(
                id = s.aktuelleId,
                name = s.name,
                text = s.text,
                werte = s.werte,
                jetzt = System.currentTimeMillis(),
                zuordnung = s.zuordnung,
            ),
        )
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
        //
        // WICHTIG: die GESAMTEN Zuege, nicht nur den Text. Der gezeichnete Rahmen liegt weiter
        // aussen als jeder Buchstabe; pruefte man nur den Text, schlage das Softlimit erst
        // mitten im Auftrag zu - mit halb beschriebenem Blatt.
        val blattStrokes = doc.zuege
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

    // ---- Serienlauf ----

    /**
     * Plottet einen einzelnen Bogen und kehrt erst zurueck, wenn er durch ist.
     *
     * Bewusst dieselbe Kette wie ein einzelner Auftrag: Der [MachineController] prueft Grenzen,
     * Homing und Idle-Zustand, und der Auftrag endet mit der Rueckfahrt auf den Nullpunkt.
     * Ein zweiter Sendeweg mit eigener Sicherheitslogik waere genau die Abkuerzung, die
     * spaeter ein Blatt kostet.
     */
    private suspend fun serienBogenPlotten(
        text: String,
        s: AppSettings,
        zuordnung: List<Int>,
        ueberSdKarte: Boolean,
    ): Result<Unit> {
        val c = controller ?: return Result.failure(IllegalStateException("Nicht verbunden."))

        val laid = runCatching {
            layoutAbsaetze(
                absaetzeAus(text, s.toTextStyles(), zuordnung) { Fonts.load(it) },
                s.toFrame(),
                s.drehung,
            )
        }.getOrElse { return Result.failure(it) }

        val profil = s.toMachineProfile().applying(maschinenwerte)
        val alle = s.zierrahmenZuege() + laid.orderedStrokes(profil)
        val job = plotJobAus(alle, profil)
        if (job.penDownCount == 0) {
            return Result.failure(IllegalStateException("Der Bogen enthält nichts zu zeichnen."))
        }

        val fluss = if (ueberSdKarte) {
            c.plotViaSd(job, alle, sdTransfer())
        } else {
            c.plot(job, laid.strokes)
        }

        var fehler: String? = null
        fluss.collect { fortschritt ->
            _machine.update {
                it.copy(
                    progress = fortschritt,
                    busy = fortschritt is SendProgress.Started ||
                        fortschritt is SendProgress.Running,
                    sdLauf = ueberSdKarte,
                )
            }
            when (fortschritt) {
                is SendProgress.Failed -> fehler = fortschritt.message +
                    if (fortschritt.penLifted) "" else " ACHTUNG: Stift nicht angehoben."
                is SendProgress.Aborted -> fehler = "Abgebrochen"
                else -> Unit
            }
        }
        syncController()

        return fehler?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
    }

    fun serieStarten(ueberSdKarte: Boolean) {
        val s = _serie.value
        if (!s.startbar) return
        if (controller == null) {
            _machine.update { it.copy(message = "Nicht verbunden.") }
            return
        }

        val texte = s.zeilen.filter { it.fehler == null }.map { einsetzen(s.text, it.felder) }
        val lauf = Serienlauf(
            bogen = texte,
            plotteBogen = { _, text ->
                serienBogenPlotten(text, s.settings, s.zuordnung, ueberSdKarte)
            },
        )
        serienlauf = lauf

        viewModelScope.launch {
            lauf.zustand.collect { zustand ->
                _serie.update { it.copy(lauf = zustand) }
                // Zwischen zwei Karten liegt eine Wartezeit, in der das Telefon sonst
                // einschliefe und die Verbindung verloere - die Sperre gilt fuer den ganzen
                // Satz, nicht je Bogen.
                if (zustand is SerienZustand.Fertig || zustand is SerienZustand.Abgebrochen) {
                    wakeLock.release()
                }
            }
        }

        wakeLock.acquire()
        serieWeiter()
    }

    fun serieWeiter() {
        val lauf = serienlauf ?: return
        serienAuftrag = viewModelScope.launch { lauf.naechsterBogen() }
    }

    fun serieUeberspringen() {
        serienlauf?.ueberspringen()
    }

    fun serieAbbrechen() {
        serienAuftrag?.cancel()
        serienlauf?.abbrechen()
        serienlauf = null
        wakeLock.release()
        // Der Stift steht womoeglich noch auf dem Papier.
        emergencyStop()
    }

    /** Schliesst einen fertigen oder abgebrochenen Satz ab, damit der Reiter wieder frei ist. */
    fun serieBeenden() {
        serienlauf = null
        _serie.update { it.copy(lauf = null) }
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
