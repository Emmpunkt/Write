# Projekt: Write – Notizen als Schreibschrift auf dem FluidNC-Plotter

Android-App: Text tippen → als Single-Line-Schreibschrift setzen → 1:1 in der Vorschau sehen →
direkt an den Stiftplotter senden. Dazu eine schlanke Maschinensteuerung.

Technische Beschreibung siehe `README.md`. **Diese Datei hält fest, was dort NICHT steht:**
Arbeitsstand, Umgebung, getroffene Entscheidungen und die nächsten Schritte.

Repository: https://github.com/Emmpunkt/Write (öffentlich, MIT)

---

## ➜ AKTUELLER STAND (2026-08-04) — hier anfangen

**Etappe 3 ist vollständig fertig.** 319 Tests grün, am Gerät verifiziert (Migration, Stile
anlegen/zuweisen/umbenennen, Neustart). Zweig `absatzstile`.

Fertig: Etappen 1, 2a, 2b und **Etappe 3 komplett** (SD-Upload, Notizliste, Vorlagen mit
Platzhaltern, Serienlauf, **benannte Absatzstile**) — dazu die beiden Korrekturrunden vom
2026-08-04, mit denen Blatt und Textrahmen getrennt wurden.

**Der Nutzer hat am 2026-08-04 an der Maschine bestätigt, dass der Rahmenversatz auf dem Papier
dort landet, wo er soll.**

### Offen: Etappe 4 „Gestalten"

Zwei Wünsche des Nutzers vom 2026-08-04, beide durchgesprochen und entschieden:

1. **Text in 90°-Schritten drehen.** Anlass: A6 hoch (105 × 148) passt nicht auf den Tisch
   (155 × 105). Wer hochkant schreiben will, legt das Blatt quer und dreht den Text. Entschieden:
   vier Knöpfe (0/90/180/270), **der Satz dreht sich im Rahmen, nicht der Rahmen** — bei 90/270
   wird auf einer Fläche mit vertauschten Maßen gesetzt und hineingedreht.
2. **Gezeichnete Zierrahmen**: Rechteck, Doppellinie, abgerundet, Sprechblase, Zierecken.
   Entschieden: **um den Textrahmen mit einstellbarem Abstand** (der Textsatz bleibt unberührt)
   und **selbst gerechnet** statt als Grafiken mitgeliefert (verzerrungsfrei bei jedem
   Seitenverhältnis, keine Lizenzfrage).

Der Plan dazu steht in `docs/superpowers/specs/` bzw. im Plan der Sitzung.

### Wenn du hier neu einsteigst

1. Diese Datei bis „Arbeitsweise" lesen — dort stehen die Sicherheitsregeln für die Maschine.
2. `README.md` erklärt das Wie (Aufbau, Schriften, Protokoll); diese Datei das Warum.
3. Die Abschnitte weiter unten sind **chronologisch** und halten fest, warum etwas so ist.
   Besonders die beiden Korrekturen vom 2026-08-04 — sie nicht ungefragt zurückdrehen.

---

## Stand: Etappen 1, 2a und 2b abgeschlossen (2026-08-02)

Alles auf `main`, **118 Tests grün**, am echten Gerät und an der echten Maschine verifiziert.
Der letzte Testbogen lief vollständig durch und endete sauber auf dem Arbeitsnullpunkt mit
angehobenem Stift.

## Stand 2026-08-03: zwei offene Punkte abgearbeitet, an der Maschine verifiziert

172 Tests grün. Die Live-Prüfung gegen den echten Plotter lief (rein lesend, plus ein
bewegungsfreier SD-Testlauf) und hat drei Annahmen widerlegt – siehe „Die Maschine".

**Auf Ansage des Nutzers (2026-08-03): Maschinenwerte gehören nicht ins Programm.** Verfahrweg,
Untergrenzen, Beschleunigungen und Vorschubgrenzen werden beim Verbinden aus `$/axes/*` geholt
und über das Profil gelegt (`MachineLimits` + `MachineProfile.applying` im core). Gespeicherte
Werte sind nur noch Rückfall ohne Verbindung. Automatisch statt Knopf – eine Abfrage, die man
zu drücken vergisst, ist dieselbe Fehlerquelle wie ein fester Wert.

Dabei fiel ein zweites Loch derselben Art auf und ist behoben: Der `MachineController` bekam
sein Profil **einmal beim Verbinden**. Wer danach den Papier-Offset verstellte, erzeugte
G-Code mit dem neuen Wert, während `preflight` noch gegen den alten prüfte. Jetzt bekommt er
einen `profileProvider` statt einer Kopie. Abgesichert durch
`Vorpruefung folgt einer spaeteren Aenderung der Einstellungen`.

1. **Zeitschätzung rechnet mit Beschleunigungsrampen** statt `Weg / Vorschub` (`rampSeconds`
   in `GCodeGenerator.kt`). Bewusst kein pauschaler Korrekturfaktor: der träfe den langen
   Strich wie den kurzen, obwohl der Fehler nur bei den kurzen entsteht. Gerechnet wird
   Bewegung für Bewegung, weil die Maschine an jedem Zugende wirklich steht (Stift hoch/runter
   dazwischen) – über Gesamtlängen wären genau diese Stillstände unsichtbar.
2. **`$/axes/x` und `$/axes/y` werden beim Verbinden ausgelesen** (`AxisSettings.kt`,
   `MachineController.fetchAxisSettings`). Daraus kommen der wahre fahrbare Bereich
   (`TravelLimits` → `checkBounds`) und die Beschleunigung für Punkt 1. Kennt die Firmware die
   Abfrage nicht, gilt weiter der Rückfall `[0, workArea]`.

### Was die Live-Prüfung ergeben hat

Der Parser greift, das Antwortformat passt. Verifiziert gelesen:
`TravelLimits(10, 165, 10, 115)`, Beschleunigung XY 400, Z 200.

Drei Annahmen waren falsch, alle drei nur durch Auslesen zu finden:

1. **`mpos_mm` war 10, nicht 3** – die Notiz von 2026-08-02 war überholt. (Inzwischen
   steht es wieder auf 3, vom Nutzer nachgestellt. Eben deshalb steht der Wert nirgends fest.)
2. **Die Z-Beschleunigung ist eine andere als die von XY** (200 vs. 400). Die erste Fassung
   setzte beide gleich – bei Hunderten Stifthüben je Auftrag ein echter Fehler.
3. **Der Arbeitsnullpunkt passte nicht mehr zum fahrbaren Bereich** (G54 auf 3,
   Untergrenze 10). Vom Nutzer am selben Tag behoben, indem er `mpos_mm` wieder auf 3 setzte –
   G54 liegt jetzt exakt auf der Untergrenze: fahrbar, aber **ohne Reserve**.

### Zeitschätzung: an einem echten Bogen kalibriert

Der erste Anlauf (nur Rampen) lag **+13 % zu hoch**. Der echte Bogen zeigte warum: **Das
Anheben des Stifts ist ein Eilgang, das Absenken nicht.** `G1 Z-1.5 F600` zum Senken, aber
`G0 Z3` zum Heben – und G0 fährt mit dem Höchstvorschub der Achse. Beide gleich zu rechnen
macht jeden Hub zu lang. Dafür gibt es jetzt `rapidZMmMin` im Profil, gefüllt aus
`max_rate_mm_per_min` der Z-Achse.

Gemessen am 2026-08-03 (A6 quer, 396 Zeilen, 28 Hübe, real 55 s):

| Modell | Schätzung | Abweichung |
|---|---|---|
| alte Formel `Weg / Vorschub` | 51 s | −7 % |
| Rampen, Z-Hub einheitlich | 62 s | +13 % |
| Rampen + Eilgang beim Anheben | 56,5 s | **+3 %** |

**Eine Messung an einem Auftrag, keine Garantie.** Der frühere Befund (25 % zu niedrig) stammt
von einem viel größeren Bogen, dessen damalige Einstellungen nicht mehr rekonstruierbar sind.
Junction Deviation bleibt außen vor – `rampSeconds` nimmt an, der Planer fahre einen Strichzug
ohne Zwischenstopp durch. Bei langem Text dürfte die Schätzung deshalb wieder zu knapp werden.

## Etappe 1 (2026-08-02)

Vollständig gebaut, **am echten Gerät und an der echten Maschine verifiziert.**

Der abschließende Dauertest lief durch: A6 quer, 3.480 mm Strich, 790 Pen-Down-Zyklen,
~1.900 G-Code-Zeilen, **rund 15 Minuten ohne Abbruch oder Alarm**. Die Maschine beendete
sauber auf dem Arbeitsnullpunkt mit angehobenem Stift.

Ebenfalls erprobt: Homing, X/Y- und Z-Nullen, Jogging, Not-Halt.

## Die Maschine

Selbstbau-Stiftplotter, FluidNC v4.0.3 auf ESP32, im WLAN unter **192.168.2.18**.
Ausgelesen über Telnet (Port 23):

| Größe | Wert | Bedeutung |
|---|---|---|
| `$130` / `$131` / `$132` | 155 / 105 / 30 mm | Verfahrweg **ab dem Maschinennullpunkt** |
| `$110` / `$111` | 1500 mm/min | Maximaler XY-Vorschub |
| `$112` | 2000 mm/min | Z-Vorschub |
| `$120` / `$121` | 400 mm/s² | XY-Beschleunigung (geht in die Zeitschätzung ein) |
| `$122` | 200 mm/s² | Z-Beschleunigung – **halb so groß wie XY**, nicht gleichsetzen |
| `mpos_mm` (X, Y) | > 0 | Untergrenze des fahrbaren Bereichs, aus `$/axes/x` – **variabel** |
| `$20` | 1 | Soft Limits **aktiv** |
| `$22` | 1 | Homing aktiv (nur X/Y; Z hat `soft_limits: false`) |
| `$10` | 1 | Statusbericht meldet MPos; WCO kommt nur periodisch |

Alle Werte am 2026-08-03 über Telnet ausgelesen – **als Beispiel, nicht als Vorgabe.**
Die App holt sie sich beim Verbinden selbst; `mpos_mm` hat sich binnen eines Tages von 3 auf 10
und zurück auf 3 geändert.

**Besonderheiten, die das Verhalten der App bestimmen:**

1. **Die Z-Achse hat keinen Endschalter** und bleibt beim Homing außen vor. Ihr Nullpunkt
   entsteht nur über „Z hier nullen" und muss nach jedem Neustart der Steuerung neu gesetzt
   werden. Die Papierebene ist `Zw = 0`, geschrieben wird bei `Z −1,5`, verfahren bei `Z +3`.
2. **Der Stiftantrieb ist nicht fest gekoppelt.** Sobald der Stift aufsetzt, trägt ihn nur
   noch sein Eigengewicht. Deshalb darf `Z_unten` bewusst unter der Papierebene liegen
   (Übertravel) – das gleicht Unebenheiten aus. Der Anpressdruck ist nicht einstellbar.
3. **Der fahrbare Bereich beginnt nicht bei null.** In `$/axes/x` und `$/axes/y` steht bei
   negativer Referenzfahrt ein `mpos_mm` > 0; fahrbar ist `[mpos_mm, mpos_mm + max_travel]`.
   **`$130`/`$131` sind der Verfahrweg ab dem Maschinennullpunkt, nicht ab dem
   Arbeitsnullpunkt.**

   **Konkrete Zahlen gehören hier nicht hin** – sie haben sich schon zweimal geändert (3 → 10
   → 3) und wurden beide Male zu spät bemerkt. Die App liest sie beim Verbinden aus; wer sie
   sehen will, nimmt `./gradlew :machine:test -PplotterHost=<ip>`, dort stehen sie im
   Protokoll.
4. **Der Arbeitsnullpunkt (G54) ist nicht der Maschinennullpunkt.** Die App sendet in G54, die
   Firmware addiert den Versatz. `$#` liefert den aktuellen Wert.

   **Er muss auf oder über der Achsen-Untergrenze liegen.** Sonst ist schon die Rückfahrt
   `G0 X0 Y0` am Ende jedes Auftrags unfahrbar, und der Alarm kommt, wenn das Blatt fertig
   beschrieben ist. Genau das ist zweimal passiert. Die Grenzprüfung fängt es seit dem
   2026-08-03 vorher ab; der Live-Fall
   `meldet den Abstand zwischen Arbeitsnullpunkt und Untergrenze` zeigt die Reserve an.
5. **Soft Limits greifen, aber erst mitten im Auftrag.** Eine Zielkoordinate außerhalb löst bei
   G0/G1 ALARM:2 aus – mit halb beschriebenem Blatt. Jog-Befehle verhalten sich anders: die
   werden auf die Grenze *begrenzt* statt abgewiesen. Wer daraus schließt, die Firmware prüfe
   nicht, irrt (dieser Fehlschluss ist hier schon einmal passiert).
6. **Ein Not-Halt macht die Maschine bewegungsunfähig.** Nach dem Soft-Reset steht sie im
   Alarmzustand und verweigert bei aktiven Soft Limits jede Bewegung, bis wieder referenziert
   wurde. Der Stift lässt sich dann NICHT mehr anheben – die App meldet das ehrlich, statt
   Erfolg zu unterstellen.

## Entwicklungsumgebung

- Android Studio, JDK 21, SDK 34+36, Gradle 8.13, AGP 8.12.3, Kotlin 2.2.20
- Handy: Samsung, **Installation über WLAN, nicht über USB** – über Kabel bricht die
  Übertragung reproduzierbar ab (Gerät geht mitten im Streamed Install offline):

```bash
adb tcpip 5555 && adb connect 192.168.2.30:5555
adb -s 192.168.2.30:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

- Live-Prüfung gegen die echte Maschine (nur lesende Befehle, bewegt nichts):
  `./gradlew :machine:test -PplotterHost=192.168.2.18`
- Schriftbild ansehen (rendert fertigen G-Code zurück nach PNG):
  `./gradlew :core:test --tests '*PreviewSamplesTest*'` → `core/build/preview/`

## Etappe 2a abgeschlossen (2026-08-02)

Auto-Fit (`fitSize` im core) und die vier Feintuning-Regler im Editor. Reglerwerte gehen
während des Zugs nur in den Zustand, gespeichert wird beim Loslassen (`updateSettingsLive` /
`commitSettings` im ViewModel).

## Etappe 2b abgeschlossen (2026-08-02)

Vier einlinige SVG-Schreibschriften (EMS Allure, Decorous Script, Invite, Delight; SIL OFL)
über einen zweiten Parser `SvgFont` hinter derselben `StrokeFont`-Schnittstelle. Die
Hershey-Kalligrafie ist entfernt.

**Anlass war eine Messung, kein Wunsch:** Am ersten Probeblatt mit 25 mm Versalhöhe fiel auf,
dass Buchstaben nicht aneinander anschließen. Die Untersuchung ergab: Von den 676
Kleinbuchstabenpaaren der Hershey-Schreibschrift verbinden 85 % exakt, der Rest nicht – `t`
endet oben am Querstrich, Großbuchstaben und Ziffern haben gar keinen Anschlusspunkt. Im
G-Code selbst gibt es dabei **keine** Sprünge zwischen zeichnenden Segmenten; die App hebt
sauber ab. Der Effekt skaliert mit der Größe und ist bei 7 mm mit 0,28 mm unsichtbar.

Zwei Dinge, die bei Arbeit an den Schriften Zeit sparen:

- **`cap-height` in den SVG-Dateien ist unbrauchbar** (überall 500, real 639–939). Metriken
  kommen aus `FontMetrics.derive` und damit aus den Glyphen.
- **Ob Buchstaben „verbinden", lässt sich nicht einfach messen.** Ein Versuch, den letzten
  Punkt eines Pfades als Auslaufstrich zu nehmen, lieferte für EMS Allure 30 % statt der im
  Bild sichtbaren durchgehenden Verbindung – die Strichrichtung folgt in diesen Schriften
  nicht der Schreibrichtung. Beurteilt wird über die Musterbilder.

### Beim Plotten aufgefallen und behoben

1. **Einlaufstrich am Zeilenanfang.** Die EMS-Glyphen haben negative x-Werte – den
   Verbindungsstrich zum vorherigen Buchstaben (Allure `j` −387, Invite `j` −410 Einheiten).
   Am Zeilenanfang gibt es keinen Vorgänger, der Strich ragte über den Rand. Die Hershey-
   Schriften hatten das Problem nie (minX immer ≥ 0). `TextLayout` rückt die Zeile jetzt um den
   tatsächlichen Überhang ein – immer die **ganze** Zeile, nie einzelne Glyphen, sonst risse
   die Verbindung genau dort auf.
2. **Der Arbeitsnullpunkt fehlte in der Grenzprüfung.** Siehe Punkt 3–4 unter „Die Maschine".
   `checkBounds` nimmt ihn jetzt als Parameter; ein *unbekannter* Versatz ist bewusst ein
   Fehler und kein stillschweigendes (0,0) – genau diese Annahme war der Fehler.

## Etappe 3 – „Bequem"

Notizliste mit Room, Vorlagen mit Platzhaltern, gemischte Stile je Absatz, Upload auf SD.
Reihenfolge nach Entscheidung des Nutzers (2026-08-03): **SD-Upload zuerst**, danach
Notizliste, Vorlagen, gemischte Stile.

### SD-Weg am 2026-08-03 verifiziert – er funktioniert

Am Gerät durchgespielt, mit einer bewegungsfreien Testdatei (nur `G21`/`G90`/`M2`), danach
wieder gelöscht:

- `POST /upload` (multipart, Felder `path=/` und die Datei) → **HTTP 200**, Datei liegt mit
  exakter Größe auf der Karte. Antwort ist die Dateiliste als JSON.
- `$SD/Run=/datei.nc` über Telnet → `ok`, dann `[MSG:INFO: Program End]`. Position unverändert.
- `$SD/List` listet, `$SD/Delete=/datei.nc` löscht.
- Karte ist bestückt und voll mit älteren Aufträgen (`$SD/Status` → `SD card detected`).

**Wichtig:** `POST /upload` funktioniert, obwohl `/command` weiterhin `WebSocket dead` liefert.
Der frühere Schluss „HTTP ist bei diesem Gerät wertlos" gilt also nur für Befehle, nicht für
den Dateitransfer. OkHttp muss dafür zurück ins `machine`-Modul.

**Noch offen:** `$SD/Status` liefert nur `SD card detected`, keinen Fortschritt. Für die
Fortschrittsanzeige ist das `SD`-Feld im normalen `?`-Statusbericht zu prüfen – das geht nur
während eines echten Laufs.

### Teil 2 steht: Notizliste (2026-08-03)

Room speichert `NoteEntity` (Text + Schriftbild), das DAO liegt hinter einem eigenen
Interface, damit alles ohne Emulator prüfbar bleibt – dasselbe Muster wie `FakeFluidNc`.
Die gesamte Logik (Titel, Umwandlung, Erben) sitzt in reinen Funktionen in `NoteLogik.kt`.

Festgelegt vom Nutzer: **Das Blatt gehört NICHT zur Notiz**, sondern gilt global – das Papier
liegt auf dem Tisch, nicht im Dokument. Ein abweichender *Textrahmen* darf erst bei den
**Vorlagen (Teil 3)** mitkommen; das ist der richtige Ort dafür, weil eine Grußkarte ihren
Textkasten mitbringt, eine Notiz aber nicht.

**Am Gerät gefundener Fehler, den kein Test zeigen konnte:** Die App öffnete nach einem
Neustart eine andere Notiz als die zuletzt sichtbare. Die offene Notiz war aus den
Zeitstempeln erschlossen – beim Wechseln wird aber die *verlassene* Notiz gespeichert und
trägt danach die neuere Zeit. Sie wird jetzt gemerkt (`offeneNotizId` in den Einstellungen).
Merksatz: „zuletzt bearbeitet" ist nicht „zuletzt angesehen".

### Teil 3 steht: Vorlagen und Serienlauf (2026-08-03)

Eine zweite Room-Tabelle (`templates`, DB-Version 2 **mit Migration** — die Notizen bleiben).
Die Ablaufsteuerung `Serienlauf` bekommt das Plotten als Funktion hereingereicht und kennt
weder Telnet noch SD-Karte; dadurch sind Fehlschlag, Wiederholung, Überspringen, Abbruch und
Wiederaufnahme ohne Maschine prüfbar.

**Trennzeichen der Werteliste ist das Semikolon.** Das Komma schied aus, weil es in Namen
vorkommt („Schmidt, Anna"); der Tabulator lässt sich auf einer Telefontastatur nicht tippen.
Diese Entscheidung bitte nicht ungefragt umdrehen — ein Wert mit Semikolon ist als Preis dafür
bewusst nicht darstellbar.

Anders als eine Notiz trägt eine Vorlage **den ganzen Textrahmen mit**: Breite, Höhe und
Position. (Der Rand fiel bei der zweiten Korrektur weg — siehe unten.)

### Korrektur am 2026-08-04: der Versatz gehört zur Vorlage

Teil 3 ließ den Versatz global, mit der Begründung, er beschreibe den Anschlag. **Das war
falsch** — Rückmeldung des Nutzers: „Die Blattgröße ist ja nicht immer real. Es ist mehr eine
Textbox. Der Text hat einen definierten Rahmen, aber den muss ich positionieren können."

Daraus wurden zwei echte Fehler, beide vom Nutzer gefunden:

1. **Der Serie-Reiter fror die globalen Werte beim Öffnen einer Vorlage ein.** Eine Änderung am
   Versatz wirkte im Editor sofort, im Serienmodus nie — und ein Satz wäre mit den alten Werten
   gelaufen. Behoben: `serieAufFrischeGlobaleLegen()` legt bei jeder globalen Änderung neu auf.
2. **Ein freies Format war im Serienmodus nicht einzugeben.** Das Blatt-Auswahlfeld *zeigt*
   „50×50" an, *anbieten* kann es nur die Vorgaben; die Zahlenfelder standen in Optionen und
   wirkten nur global. Wer einmal eine Vorgabe wählte, bekam sein Format nie zurück. Genau das
   war am Gerät passiert: Die Vorlage stand auf 76×76 — der Vorgabe „Haftnotiz 76".

**Merksatz:** Ein Feld, das Werte anzeigt, die es nicht anbieten kann, ist eine Falle.

### Zweite Korrektur am 2026-08-04: Blatt und Textrahmen sind zwei Dinge

Die erste Korrektur schob den ganzen Rahmen in die Vorlage — und machte damit **Blatt =
Textrahmen**. Rückmeldung des Nutzers: „Jetzt habe ich Blatt=Textrahmen. Das müsste eigentlich
getrennt sein. Also auch ein großes Blatt einstellen und den Textrahmen dann in diesem Blatt
positionieren."

Er hat recht: Ein großes Blatt mit kleinem Text darauf ließ sich gar nicht beschreiben. Wer das
wollte, musste das Blatt kleinlügen — und sah in der Vorschau dann nicht die Karte, sondern den
Textkasten. Seitdem gilt:

| | was es beschreibt | wo es steht | wem es gehört |
|---|---|---|---|
| **Blatt** | das Papier auf dem Tisch: Größe + Lage am Anschlag | Optionen, Auswahlfeld im Editor | global |
| **Textrahmen** | der Kasten für den Text, **im Blatt** positioniert | ausklappbar in der Stilleiste | dem Dokument (Vorlage) |

Der Textsatz rechnet in **Rahmen-Koordinaten**; auf den Tisch kommt der Rahmen erst in
`toMachineProfile()`, wo beide Verschiebungen addiert werden (`ursprungXMm`). Der Rahmen hat
**keine eigenen Ränder mehr** — er *ist* der nutzbare Bereich. `marginMm` blieb nur als Vorgabe
für „Blatt füllen"; ein Rand im Rahmen wäre eine zweite Stellschraube für dieselbe Sache.

Zwei Umstellungen mussten alte Einstellungen unverändert weiterschreiben, nicht bloß irgendwie
laden — sonst verrutscht jede bestehende Notiz beim nächsten Plotten:

- **DataStore:** Fehlen die Rahmen-Schlüssel, entsteht der Rahmen aus dem gespeicherten Blatt
  samt Rand (`blattFuellen()`). Die Vorgabe (A6 mit 8 mm) wäre dort schlicht falsch gewesen.
- **Room 3 → 4:** Der alte Rand wandert in die Lage, aus Kasten minus zweimal Rand wird die
  Rahmengröße. SQLite kann keine Spalten entfernen, deshalb der Umweg über eine neue Tabelle.
  Der alte Versatz zählte ab der Tisch-, der neue ab der Blattecke — solange das Blatt bei 0/0
  liegt, dieselbe Stelle. Am Gerät geprüft: beide Vorlagen samt Werteliste erhalten.

**Merksatz:** Wenn zwei Begriffe im Kopf des Nutzers verschieden sind, dürfen sie nicht auf
dasselbe Feld zeigen — auch dann nicht, wenn sie meistens denselben Wert haben.

**An der Maschine bestätigt (2026-08-04, vom Nutzer gefahren):** Der Rahmenversatz landet auf
dem Papier dort, wo er soll. Damit ist die Rechnung „Ursprung = Blattlage + Rahmenlage" nicht
nur durch Tests, sondern durch einen echten Bogen gedeckt.

**Drei Fehler, die erst das Gerät zeigte** — alle drei bei grünen Tests auf dem PC:

1. Das Platzhalter-Muster `\{([\p{L}\p{N}_-]+)}` warf am Gerät eine `PatternSyntaxException`.
   **Java ist bei Regex nachsichtiger als Androids ICU** — schließende geschweifte Klammer und
   Bindestrich müssen maskiert sein. Regex-Feinheiten gehören ans Gerät, nicht nur in den Test.
2. `PreviewCanvas` hat keine eigene Höhe; ohne `Modifier.height(...)` fällt sie auf ihre
   Polsterung zusammen.
3. Drei Knöpfe nebeneinander quetschten „Nächster Bogen" zu einer senkrechten Buchstabensäule.

**Nach einem Not-Halt ist die Maschine nicht mehr referenziert.** Der nächste Bogen meldet das
sauber als Fehlschlag, der Zähler bleibt stehen. Vor dem Weiterplotten neu homen.

**Beim Bedienen per adb:** Ein Bogen dauert ~25 s, ein Werkzeug-Roundtrip 15–20 s. Ein Abbruch
„mittendrin" ist mit Einzelbefehlen nicht zu treffen — Start und Abbruch gehören in EINEN
Aufruf, und Knopfpositionen holt man mit `uiautomator dump` statt sie aus Bildschirmfotos zu
schätzen.

Entwurfsentscheidung (2026-08-03): **zwei getrennte Knöpfe** – „Auf SD senden" und „Direkt
senden". Beide durchlaufen dasselbe `preflight`; der SD-Weg bekommt keine zweite
Sicherheitslogik.

### Teil 1 gebaut (2026-08-03) – 168 Tests grün

`SdTransfer`/`HttpSdTransfer` (Upload), `SdSender` (Upload → `$SD/Run=` → Statusverfolgung),
`MachineController.plotViaSd`, zwei Knöpfe im Editor. **Ohne neue Abhängigkeit**:
`HttpURLConnection` statt OkHttp – für einen multipart-POST wäre eine Bibliothek
unverhältnismäßig, zumal OkHttp hier schon einmal entfernt wurde.

Drei Regeln, die als Test festgehalten sind:
1. Schlägt der Upload fehl, wird **nichts** gestartet – sonst liefe die Datei vom letzten Mal.
2. Kein stiller Rückfall von SD auf Telnet – bei zwei Knöpfen muss sichtbar sein, welcher lief.
3. Homing-Pflicht und Grenzprüfung gelten für beide Wege (gemeinsames `preflight`).

Gegen die echte Firmware geprüft: der selbstgebaute multipart-Rumpf wird angenommen, die Datei
kommt mit exakter Größe an (`LivePlotterTest`, räumt hinterher auf).

**Der SD-Prozentwert ist der Lesefortschritt, nicht der Bewegungsfortschritt.** Am Gerät
gemessen: bei einer kleinen Datei steht sofort `SD:100.00`, während die Achse noch fährt. Das
Ende wird deshalb am Zustandswechsel `Run` → `Idle` erkannt.

### Noch offen bei Teil 1

**Erledigt am 2026-08-03:** Der vollständige Plot über SD lief auf Papier durch – „Hallo von
der SD-Karte", A6 quer, 396 Zeilen, 28 Hübe, 55 s, `Completed`, Endzustand sauber auf dem
Arbeitsnullpunkt mit angehobenem Stift. Der Fortschritt lief dabei sichtbar mit (4 % → 100 %):
bei einer Datei dieser Größe hinkt der Lesefortschritt nicht mehr auf 100 % fest wie bei der
37-Byte-Probe.

Wiederholbar mit `./gradlew :machine:test -PplotterHost=<ip> -PplotterPlot=true`
(`LivePlotTest`). Das zweite Flag ist Absicht: `-PplotterHost` allein startet nur die lesenden
Fälle, ein Test der den Stift aufsetzt darf nicht versehentlich mitlaufen.

### Teil 4 steht: benannte Absatzstile (2026-08-04)

Die lange offene Frage ist beantwortet: **absatzweise, nicht beliebige markierte Bereiche**, und
zwar über **benannte Stile** („Überschrift", „Fließtext"), die man Absätzen zuweist. Je Stil
wechseln **Schrift, Größe, Ausrichtung**; das Feintuning bleibt dokumentweit. Die Stilliste
gehört der Notiz, eine neue erbt sie von der zuletzt geöffneten.

**Die Stile haben `fontId`, `sizeMm` und `align` ERSETZT**, nicht ergänzt — in `AppSettings`,
`NoteEntity` und `TemplateEntity`. Stil 1 ist der Grundstil. Nebeneinander wären es zwei Orte
für dieselbe Information gewesen, und genau daran hat sich dieses Projekt schon zweimal
verletzt. Der Preis war eine Migration (**Room 4 → 5**, Tabellenneubau für beide Tabellen) und
ein Rückfall im DataStore auf die alten Schlüssel.

Zwei Dinge, die beim Bauen nicht offensichtlich waren:

1. **Der Zeilenvorschub am Absatzwechsel ist `max(oben, unten)`.** Der Wert der oberen Zeile
   allein ließe kleinen Text in den Unterlängen einer großen Überschrift sitzen, der der unteren
   allein gäbe der Überschrift zu wenig Luft. Nur das Maximum trägt in beide Richtungen.
   Entsprechend ist `requiredHeightMm` eine **Summe** der Vorschübe — `(n-1) * lineAdvance` gilt
   nur bei einheitlicher Größe.
2. **Die Zuordnung Absatz → Stil muss beim Tippen nachgeführt werden**
   (`zuordnungNachTextaenderung` in `AbsatzLogik.kt`). Ohne das verrutscht hinter jedem
   eingefügten Absatz alles. Verglichen wird gemeinsames Präfix und Suffix; neue Absätze erben
   den Stil des Absatzes davor, was den häufigsten Fall (Eingabetaste mitten im Text) trifft.

**Einpassen** skaliert seit Teil 4 alle Stile gemeinsam (`fitSkalierung`): gesucht wird die
Größe von Stil 1 auf demselben 0,1-mm-Raster, die übrigen wandern proportional mit. Der
Suchbereich ist so eingeengt, dass **jeder** Stil im Reglerbereich bleibt — sonst lieferte das
Einpassen bei doppelt so großer Überschrift eine Größe, die der Regler nicht darstellen kann.

**Speicherformat** (kein JSON-Lib im Projekt): Stile als eine Zeile je Stil mit `|` als
Feldtrenner, Zuordnung als Komma-Liste von Indizes. Ein unlesbarer Zuordnungseintrag wird zu 0,
**behält aber seinen Platz** — ihn wegzulassen verschöbe alle folgenden Absätze.

**Am Gerät geprüft (2026-08-04):** Die vorhandene Notiz kam unverändert durch die Migration
(Zierschrift 14,3 mm), zweiter Stil angelegt und zugewiesen, Größe getrennt eingestellt,
Neustart überstanden (`Text|zierschrift|14.3|LEFT\nStil 2|zierschrift|7.4|LEFT`, Zuordnung
`0,1`). **Noch nicht an der Maschine gefahren.**

## Bekannte offene Punkte

- ~~Die Schrift „Allure" gefällt dem Nutzer nicht~~ **Erledigt (2026-08-03): Allure bleibt.**
  Entscheidung des Nutzers nach dem zweiten Bogen: „wenn man nicht zu groß schreibt, sieht es
  OK aus." Kein Umbau, kein Entfernen.
- ~~Untergrenze der Achsen~~ und ~~Zeitschätzung~~ sind am 2026-08-03 gebaut, aber noch nicht
  an der Maschine geprüft. Siehe den Stand-Abschnitt oben.
- **Zurückgestellt vom Nutzer (2026-08-03): die zu hohe Zeitschätzung bei den SVG-Kursiven.**
  Geschätzt 59 s, real 30–45 s. Ausdrücklich hinten angestellt („die Zeitschätzung ist mir
  nicht wichtig"). Das braucht eine Messreihe über mehrere Schriften und Größen, keine
  Nachjustierung an einem einzelnen Wert.
- **Etappe 3 Teil 4 ist gebaut**, aber noch nicht an der Maschine gefahren. Ein Bogen mit
  großer Überschrift über kleinem Fließtext steht aus — nachmessen, ob der Abstand zur ersten
  Textzeile stimmt.
- Keine automatische Silbentrennung (bewusst): Sie bräuchte Sprachwissen und läge bei
  zusammengesetzten Wörtern regelmäßig daneben. Stattdessen wirkt ein vom Nutzer gesetzter
  Bindestrich als Trennstelle, und hart getrennte Wörter werden im Editor rot markiert.
- Kein HTTP-Transport (bewusst entfernt): Die WebUI liefert Antworten über ihren WebSocket
  aus und beantwortet reine HTTP-Anfragen mit `WebSocket dead`.

## Arbeitsweise

- **Antworten auf Deutsch.**
- Der Nutzer testet selbst an der Maschine und meldet präzise zurück – seine Beobachtungen
  ernst nehmen und nachmessen statt zu raten. Mehrere echte Fehler kamen so ans Licht:
  die leere Vorschau, die verlorenen Jog-Quittungen, die ungedeckte Not-Halt-Meldung.
- Vor Änderungen an Werten der Maschine: **auslesen, nicht schätzen.**
- Neue Regeln durch Tests absichern, die ohne Gerät und ohne Netz laufen. Bei Nebenläufigkeit
  gegenprüfen, dass der Test den Fehler auch wirklich fängt (Sperre kurz aushängen).
- Bei allem, was die Maschine bewegen könnte: vorher ankündigen. Rein lesende Abfragen
  (`?`, `$I`, `$$`, `$#`, `$/axes/x`) sind unbedenklich. **Ein Jog ist keine lesende Abfrage** –
  er wird an der Softlimit-Grenze abgeschnitten, aber er fährt los.
- Der Nutzer hat das Aufspielen der APK und das Bedienen der App per `adb` freigegeben, ebenso
  Testfahrten an der Maschine („die Maschine ist ungefährlich"). Gespeicherte Einstellungen
  lassen sich auslesen:
  `adb shell run-as de.emmpunkt.write cat files/datastore/write_settings.preferences_pb`
- **Eigene Fehlaussagen offen korrigieren.** In dieser Sitzung ist zweimal etwas Falsches
  behauptet worden (ein Verbindungsmaß der Schriften, der fehlende Softlimit-Schutz); beide
  Male brachte erst das Nachmessen die Wahrheit. Messen schlägt Plausibilität.
