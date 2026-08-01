# Write – Notizen schreiben und auf dem FluidNC-Plotter zeichnen

Android-App: Text tippen, als Schreibschrift setzen, 1:1 in der Vorschau sehen und direkt an
den Stiftplotter senden. Dazu eine schlanke Maschinensteuerung (Fahren, Homing, Nullen).

## Aufbau

Drei Gradle-Module. `core` und `machine` sind reine Kotlin/JVM-Module ohne Android-Bezug und
damit vollständig auf dem PC testbar – ohne Gerät, ohne Emulator, ohne Plotter.

| Modul | Inhalt |
|---|---|
| `core` | Schriften (JHF-Parser, Umlaut-Komposition), Textsatz, Geometrie, G-Code |
| `machine` | FluidNC-Protokoll, Telnet-Transport, Streaming, Sicherheitsprüfungen |
| `app` | Compose-Oberfläche: Editor mit Vorschau, Maschine, Einstellungen |

Die Vorschau zeichnet **dieselben** `Polyline`-Objekte, aus denen der G-Code entsteht – keine
zweite Darstellung. Was auf dem Bildschirm steht, fährt der Stift.

> Die Vorgabewerte (IP `192.168.2.18`, Arbeitsbereich 155 × 105 mm, Vorschübe) sind auf einen
> bestimmten Plotter zugeschnitten und in den Einstellungen der App änderbar.

## Bauen und installieren

```bash
./gradlew test                 # 86 Tests, ohne Netz und ohne Gerät
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Live-Prüfung gegen den echten Plotter (nur lesende Befehle, bewegt nichts):

```bash
./gradlew :machine:test -PplotterHost=192.168.2.18
```

Musterbilder des Schriftbilds erzeugen (rendert fertigen G-Code zurück nach PNG,
Ergebnis in `core/build/preview/`):

```bash
./gradlew :core:test --tests '*PreviewSamplesTest*'
```

## Der Plotter

Ausgelesen am 2026-08-01 über Telnet, FluidNC v4.0.3 auf `192.168.2.18`:

| Größe | Wert | Folge für die App |
|---|---|---|
| Arbeitsbereich `$130`/`$131` | 155 × 105 mm | Vorgabe; Grenzprüfung vor jedem Auftrag |
| Vorschub X/Y `$110`/`$111` | 1500 mm/min | Obergrenze für Schreiben und Leerfahrt |
| Vorschub Z `$112` | 2000 mm/min | |
| Soft Limits `$20` | aktiv | Ein zu großer Auftrag löst Alarm aus – die Vorprüfung fängt ihn vorher ab |
| Homing `$22` | aktiv | `$H` verfügbar |
| Statusbericht `$10=1` | MPos; WCO nur **periodisch**, nicht in jedem Bericht | Der Parser merkt sich den letzten Versatz; `$#` beim Verbinden liefert ihn sofort statt erst nach Sekunden |

Nachgerechnet und verifiziert: `[G54:11.000,22.000,-10.750]` und `MPos Z −6.750`
ergeben `Zw = 4.000 mm`.

Die Z-Achse hat **keinen Endschalter** und bleibt beim Homing außen vor. Ihr Nullpunkt entsteht
allein über „Z hier nullen"; nach einem Neustart der Steuerung liegt die Maschinenkoordinate
dort auf 0, wo die Achse gerade steht, und muss neu gesetzt werden.

## Schriften

Hershey-Vektorschriften im JHF-Format (`core/src/main/resources/fonts/`), gemeinfrei.
Enthalten sind zwei Schreibschriften und zwei technische Schriften.

Die Dateien decken nur ASCII 32–126 ab. `GlyphOverlayFont` ergänzt, was für deutsche Notizen
fehlt: Umlaute durch aufgesetztes Trema, ß und € als handdefinierte Glyphen, und typografische
Zeichen (Gedankenstriche, geschwungene Anführungszeichen), die Android-Tastaturen selbsttätig
einsetzen, werden auf ihre ASCII-Entsprechung abgebildet.

### Waagerechte Striche

Die Hershey-Schriften bringen einen Bindestrich von 0,86 Versalhöhen mit – breiter als jeder
Kleinbuchstabe – und setzen ihn auf deren Oberkante. Im Fließtext fällt beides auf.
`GlyphOverlayFont` ersetzt ihn deshalb: 0,30 Versalhöhen lang, auf halber x-Höhe, mit
0,14 Versalhöhen Luft an jeder Seite. Halbgeviert- (0,50) und Geviertstrich (0,70) sind eigene Längen, weil das
im Deutschen verschiedene Zeichen sind. Abgesichert durch `StricheTest`.

Attribution siehe `core/src/main/resources/fonts/HERSHEY-NOTICE.txt` – die Nennung von
A. V. Hershey und James Hurt ist Auflage der Nutzungsbedingung.

## Schreibreihenfolge

Standardmäßig zeichnet die App **in Schreibrichtung**: Zeichen für Zeichen von links nach
rechts, so wie von Hand geschrieben. Der Stift fährt dabei nie über bereits Geschriebenes
zurück – bei Tinte verhindert das Verschmieren. Zeilen laufen immer von oben nach unten.

Abschaltbar unter *Einstellungen → Schreibweise*: dann sortiert die App innerhalb jeder Zeile
nach kürzesten Wegen. Das ist etwas schneller, lässt den Stift aber hin- und herspringen.

## Worttrennung

Die App trennt Wörter **nicht** eigenmächtig nach Silben – dafür bräuchte sie Sprachwissen und
träfe regelmäßig daneben. Stattdessen:

- Ein **Bindestrich im Wort ist eine Trennstelle.** Die Zeile darf dort enden, der Strich
  bleibt stehen: aus `Donaudampf-schifffahrt` wird `Donaudampf-` / `schifffahrt`.
- Passt ein Wort auch allein nicht in die Zeile, wird es als letzter Ausweg hart getrennt
  (sonst liefe es über den Blattrand) **und im Editor rot markiert**, zusammen mit einem
  Hinweis. Der Nutzer setzt dann selbst einen Bindestrich an eine sinnvolle Stelle.

Abgesichert durch `WorttrennungTest`.

## Warum nur Telnet

Die Weboberfläche nimmt Befehle unter `/command` zwar entgegen, liefert die Antworten aber über
ihren WebSocket aus – eine reine HTTP-Anfrage beantwortet sie mit `WebSocket dead`. Ein
HTTP-Transport wäre damit ohne eigene WebSocket-Anbindung wertlos und für einen Auftrag
ohnehin zu langsam, weil jede Zeile einen eigenen Umlauf bräuchte. Telnet auf Port 23 kann
alles, was die App benötigt; der HTTP-Weg wurde deshalb wieder entfernt (samt OkHttp).

## Wach bleiben während eines Auftrags

Eine volle Notiz sind schnell über tausend Zeilen und mehrere Minuten. Sperrt sich in der Zeit
der Bildschirm, drosselt Android das WLAN – die Verbindung risse mitten im Text ab, mit
aufliegendem Stift. Dagegen drei Maßnahmen, solange ein Auftrag läuft:

- Der Bildschirm bleibt an (`keepScreenOn`) – die wirksamste, weil Android dann gar nicht
  erst drosselt.
- `PARTIAL_WAKE_LOCK` hält den Prozessor wach.
- `WIFI_MODE_FULL_HIGH_PERF` verhindert, dass das Funkmodul in den Stromsparmodus geht.

Alle drei werden im `finally`-Zweig wieder freigegeben, auch bei Abbruch oder Fehler, und
zusätzlich in `onCleared()`.

## Nur ein Leser auf der Verbindung

Alle Zugriffe, die eine Antwort lesen, laufen im `MachineController` durch eine Mutex.
Grund: Es gibt nur einen Antwortstrom. Liefen Statusabfrage und Fahrbefehl gleichzeitig, läse
die eine Seite die Quittung der anderen weg – der Befehl meldete dann „keine Antwort vom
Plotter", obwohl die Maschine sauber geantwortet hat.

Realtime-Zeichen (`?`, `!`, Soft Reset, Jog-Abbruch) nehmen die Sperre bewusst **nicht**: sie
schreiben nur und müssen auch während eines laufenden Auftrags durchkommen, sonst wäre der
Not-Halt wirkungslos. `NebenlaeufigkeitTest` sichert das ab.

## Sicherheitsregeln

Sie liegen im `MachineController`, nicht in der Oberfläche – ein zweiter Aufrufer erbt sie damit.

1. **Grenzprüfung** vor dem Senden. Ragt der Text heraus, bewegt sich die Maschine gar nicht erst.
2. **Homing-Pflicht.** Ohne Referenzfahrt ist der Papier-Offset bedeutungslos.
3. **Nur im Zustand `Idle`** startet ein Auftrag.
4. **`error` oder `ALARM`** stoppt das Streaming sofort: Feed Hold, Soft Reset, Entsperren,
   dann Z anheben – in dieser Reihenfolge, denn im Alarmzustand verwirft die Maschine
   Bewegungsbefehle.
5. **Not-Halt** ist während eines Auftrags permanent erreichbar. Er hält zuverlässig an –
   ob der Stift danach noch angehoben werden kann, ist dagegen nicht sicher: nach dem
   Soft-Reset steht die Maschine im Alarmzustand und verweigert bei aktiven Soft Limits
   jede Bewegung, solange sie als nicht referenziert gilt. Die App wertet deshalb die
   Antwort aus und meldet ehrlich, statt Erfolg zu unterstellen – wer glaubt, der Stift
   sei oben, lässt ihn sonst auf dem Papier stehen. Abgesichert durch `NotHaltTest`.

## Lizenz

Der Code steht unter der MIT-Lizenz (siehe `LICENSE`).

**Ausgenommen sind die Schriftdaten** unter `core/src/main/resources/fonts/` (`*.jhf`): Das
sind die Hershey-Vektorschriften. Ihre Nutzung ist an die Nennung von Dr. A. V. Hershey
(ursprünglicher Urheber) und James Hurt (Format) gebunden. Der vollständige Wortlaut liegt in
`HERSHEY-NOTICE.txt` im selben Verzeichnis und ist mit den Schriftdaten weiterzugeben.

## Stand

**Etappe 1 steht:** Schrift, Textsatz, G-Code, Vorschau, Streaming, Jog/Homing/Nullen,
Einstellungen. 86 Tests grün, APK gebaut und auf dem Gerät installiert. Die App verbindet
sich mit dem Plotter und zeigt Zustand und Position korrekt an (verifiziert am 2026-08-01).

Hinweis zur Installation: Über USB brach die Übertragung reproduzierbar ab (Gerät ging
mitten im Streamed Install offline). Über WLAN läuft sie zuverlässig:

```bash
adb tcpip 5555 && adb connect <handy-ip>:5555
adb -s <handy-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Noch offen:
- **Es wurde noch nie tatsächlich geplottet.** `Z oben`/`Z unten` und der Papier-Versatz
  sind ungemessene Annahmen (3 / −1.5 mm). Vor dem ersten Blatt einmessen, und den ersten
  Durchlauf mit `Z unten = Z oben` trocken fahren.
- Die Z-Achse hat keinen Endschalter und wird nicht referenziert. Ihr Nullpunkt entsteht nur
  über „Z hier nullen" und ist nach einem Neustart der Steuerung erneut zu setzen.
- **Etappe 2:** SVG-Script-Fonts, Auto-Fit, Feintuning, Rahmen und Linien.
- **Etappe 3:** Notizliste, Vorlagen mit Platzhaltern, gemischte Stile, Upload auf SD.
