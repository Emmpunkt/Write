# Projekt: Write – Notizen als Schreibschrift auf dem FluidNC-Plotter

Android-App: Text tippen → als Single-Line-Schreibschrift setzen → 1:1 in der Vorschau sehen →
direkt an den Stiftplotter senden. Dazu eine schlanke Maschinensteuerung.

Technische Beschreibung siehe `README.md`. **Diese Datei hält fest, was dort NICHT steht:**
Arbeitsstand, Umgebung, getroffene Entscheidungen und die nächsten Schritte.

Repository: https://github.com/Emmpunkt/Write (öffentlich, MIT)

## Stand: Etappe 1 abgeschlossen (2026-08-02)

Vollständig gebaut, 86 Tests grün, **am echten Gerät und an der echten Maschine verifiziert.**

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
3. **Der Arbeitsnullpunkt liegt bei Maschine (11, 22).** Die Soft Limits der Firmware gelten
   für Maschinenkoordinaten, die Grenzprüfung der App für Arbeitskoordinaten – das nutzbare
   Feld verschiebt sich also um diesen Versatz.
4. **Ein Not-Halt macht die Maschine bewegungsunfähig.** Nach dem Soft-Reset steht sie im
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

## Etappe 2 – „Schön" (als Nächstes)

1. **SVG-Script-Fonts** (EMS Allure, EMS Casual Script, SIL OFL). Hershey-Script verbindet die
   Buchstaben nur teilweise; die EMS-Schriften sind echte verbundene Kursiven. Erfordert einen
   zweiten Font-Parser hinter der bestehenden `StrokeFont`-Schnittstelle – der Rest der App
   merkt davon nichts.
2. **Auto-Fit**: Schriftgröße per Intervallhalbierung so einschachteln, dass der Text in den
   Rahmen passt. `layoutText` ist bereits darauf ausgelegt.
3. **Feintuning-Regler** in der Oberfläche (Laufweite, Wortabstand, Zeilenabstand, Neigung –
   im Datenmodell alles vorhanden, nur in den Einstellungen noch roh).
4. **Dekor**: Rahmen, Trennlinien, Unterstreichungen, Aufzählungspunkte.

## Etappe 3 – „Bequem"

Notizliste mit Room, Vorlagen mit Platzhaltern, gemischte Stile je Absatz, Upload auf SD
(`POST /upload` + `$SD/Run=` – anderer Endpunkt als das entfernte `/command`).

## Bekannte offene Punkte

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
  (`?`, `$I`, `$$`, `$#`) sind unbedenklich.
