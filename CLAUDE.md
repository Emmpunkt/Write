# Projekt: Write – Notizen als Schreibschrift auf dem FluidNC-Plotter

Android-App: Text tippen → als Single-Line-Schreibschrift setzen → 1:1 in der Vorschau sehen →
direkt an den Stiftplotter senden. Dazu eine schlanke Maschinensteuerung.

Technische Beschreibung siehe `README.md`. **Diese Datei hält fest, was dort NICHT steht:**
Arbeitsstand, Umgebung, getroffene Entscheidungen und die nächsten Schritte.

Repository: https://github.com/Emmpunkt/Write (öffentlich, MIT)

## Stand: Etappen 1, 2a und 2b abgeschlossen (2026-08-02)

Alles auf `main`, **118 Tests grün**, am echten Gerät und an der echten Maschine verifiziert.
Der letzte Testbogen lief vollständig durch und endete sauber auf dem Arbeitsnullpunkt mit
angehobenem Stift.

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
| `$130` / `$131` / `$132` | 155 / 105 / 30 mm | Arbeitsbereich |
| `$110` / `$111` | 1500 mm/min | Maximaler XY-Vorschub |
| `$112` | 2000 mm/min | Z-Vorschub |
| `$20` | 1 | Soft Limits **aktiv** |
| `$22` | 1 | Homing aktiv (nur X/Y) |
| `$10` | 1 | Statusbericht meldet MPos; WCO kommt nur periodisch |

**Besonderheiten, die das Verhalten der App bestimmen:**

1. **Die Z-Achse hat keinen Endschalter** und bleibt beim Homing außen vor. Ihr Nullpunkt
   entsteht nur über „Z hier nullen" und muss nach jedem Neustart der Steuerung neu gesetzt
   werden. Die Papierebene ist `Zw = 0`, geschrieben wird bei `Z −1,5`, verfahren bei `Z +3`.
2. **Der Stiftantrieb ist nicht fest gekoppelt.** Sobald der Stift aufsetzt, trägt ihn nur
   noch sein Eigengewicht. Deshalb darf `Z_unten` bewusst unter der Papierebene liegen
   (Übertravel) – das gleicht Unebenheiten aus. Der Anpressdruck ist nicht einstellbar.
3. **Der fahrbare Bereich ist Maschine 3…158 (X) bzw. 3…108 (Y), nicht 0…155.** In
   `$/axes/x` und `$/axes/y` steht bei negativer Referenzfahrt `mpos_mm: 3.0`; nach dem Homing
   steht die Maschine auf MPos (3, 3), und weiter zurück geht es nicht. Nachgemessen: ein Jog
   auf Maschine 2 wird auf exakt 3.000 begrenzt. **`$130`/`$131` sind der Verfahrweg ab dem
   Maschinennullpunkt, nicht ab dem Arbeitsnullpunkt.**
4. **Der Arbeitsnullpunkt (G54) ist nicht der Maschinennullpunkt.** Die App sendet in G54, die
   Firmware addiert den Versatz. Stand 2026-08-02 liegt G54 auf Maschine (3, 3) – bewusst auf
   der Untergrenze, damit das abschließende `G0 X0 Y0` überhaupt anfahrbar ist. Vorher lag er
   auf (2, 2), also 1 mm darunter: der Auftrag brach am Ende mit ALARM:2 ab, nachdem der Text
   fast fertig geschrieben war. `$#` liefert den aktuellen Wert.
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

Notizliste mit Room, Vorlagen mit Platzhaltern, gemischte Stile je Absatz, Upload auf SD
(`POST /upload` + `$SD/Run=` – anderer Endpunkt als das entfernte `/command`).

## Bekannte offene Punkte

- **Die Schrift „Allure" gefällt dem Nutzer nicht** (Rückmeldung 2026-08-02, nach dem echten
  Bogen). Sie ist zu überarbeiten oder ganz zu entfernen. Ausdrücklich **etwas für später** –
  nicht ungefragt anfangen. Beim Entfernen: `Fonts.kt`, die SVG-Datei unter
  `core/src/main/resources/fonts/`, die Nennung im README; gespeicherte `fontId`s laufen über
  den vorhandenen Rückfall in `Fonts.entry` auf die Vorgabe, es bricht also nichts.
- **Die App kennt die Untergrenze der Achsen nicht.** Sie nimmt den fahrbaren Bereich als
  `[0, workArea]` an; wahr ist `[mpos_mm, mpos_mm + max_travel]`. Solange der Arbeitsnullpunkt
  auf oder über der Untergrenze liegt, rechnet die Prüfung konservativ und damit sicher – sie
  verschenkt nur ein paar Millimeter. Sauber wäre, `$/axes/x` und `$/axes/y` auszulesen. Der
  Nutzer hat das am 2026-08-02 bewusst zurückgestellt („lass es erstmal so").
- **Die Zeitschätzung liegt rund 25 % zu niedrig.** Gemessen: 15 min statt geschätzter 11:20.
  Ursache ist in den Messwerten belegt – der tatsächliche Vorschub schwankte zwischen 157 und
  1.804 mm/min, weil die Maschine bei den kurzen Segmenten einer Schreibschrift den
  Sollvorschub selten erreicht. `estimateSeconds` rechnet ohne Beschleunigungsrampen. Ein
  Korrekturfaktor wäre eine Einzeilenänderung in `GCodeGenerator.kt`.
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
