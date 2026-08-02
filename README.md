# Write – Notizen schreiben und auf dem FluidNC-Plotter zeichnen

Android-App: Text tippen, als Schreibschrift setzen, 1:1 in der Vorschau sehen und direkt an
den Stiftplotter senden. Dazu eine schlanke Maschinensteuerung (Fahren, Homing, Nullen).

## Aufbau

Drei Gradle-Module. `core` und `machine` sind reine Kotlin/JVM-Module ohne Android-Bezug und
damit vollständig auf dem PC testbar – ohne Gerät, ohne Emulator, ohne Plotter.

| Modul | Inhalt |
|---|---|
| `core` | Schriften (JHF- und SVG-Parser, Umlaut-Komposition), Textsatz, Geometrie, G-Code |
| `machine` | FluidNC-Protokoll, Telnet-Transport, Streaming, Sicherheitsprüfungen |
| `app` | Compose-Oberfläche: Editor mit Vorschau, Maschine, Einstellungen |

Die Vorschau zeichnet **dieselben** `Polyline`-Objekte, aus denen der G-Code entsteht – keine
zweite Darstellung. Was auf dem Bildschirm steht, fährt der Stift.

> Die Vorgabewerte (IP `192.168.2.18`, Arbeitsbereich 155 × 105 mm, Vorschübe) sind auf einen
> bestimmten Plotter zugeschnitten und in den Einstellungen der App änderbar.

## Bauen und installieren

```bash
./gradlew test                 # 106 Tests, ohne Netz und ohne Gerät
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

Sieben einlinige Schriften, alle gemeinfrei oder unter freier Lizenz
(`core/src/main/resources/fonts/`).

**Vier SVG-Schreibschriften** aus dem Projekt [svg-fonts](https://gitlab.com/oskay/svg-fonts):
Allure, Zierschrift, Einladung und Druckschrift (EMS Allure, EMS Decorous Script, EMS Invite,
EMS Delight). Geschaffen von Sheldon B. Michaels, die Umsetzung ins SVG-Font-Format von
Windell H. Oskay – beide Namen stehen so in den Metadaten jeder SVG-Datei. Jede ist die
Bearbeitung einer bestehenden Schrift: Allure von *Allura* (Rob Leuschke, TypeSETit),
Zierschrift von *Petit Formal Script* (Impallari Type), Einladung von *Tangerine* (Toshi
Omagari) und Druckschrift von *Delius* (Natalia Raices) – ebenfalls aus den Metadaten der
jeweiligen Datei. Lizenz **SIL Open Font License**: `EMS-OFL.txt` enthält nur den Lizenztext
mit ungefüllten Platzhaltern (`Copyright (c) <dates>, <Copyright Holder>`), keine Namen; die
Urheberangabe muss deshalb hier stehen. Drei davon sind echte verbundene Kursiven; sie bringen
Umlaute, ß, € und die Gedankenstriche selbst mit.

**Drei Hershey-Vektorschriften** im JHF-Format: Schreibschrift, Technisch und Serif. Sie laufen
schmaler als die SVG-Schriften und eignen sich deshalb weiter für viel Text auf kleinem Blatt.
Sie decken nur ASCII 32–126 ab; `GlyphOverlayFont` ergänzt Umlaute durch aufgesetztes Trema,
ß und € als handdefinierte Glyphen und bildet typografische Zeichen auf ihre ASCII-Entsprechung
ab. Attribution siehe `HERSHEY-NOTICE.txt` – die Nennung von A. V. Hershey und James Hurt ist
Auflage der Nutzungsbedingung.

### Warum die Größenangabe aus den Glyphen kommt

Die Schriftgröße der App ist die Versalhöhe in Millimetern, am Papier nachmessbar. Die
SVG-Dateien geben dafür durchweg `cap-height="500"` an – gemessen sind es aber 639 bis 939.
Der Parser leitet die Versalhöhe deshalb aus der tatsächlichen Höhe des `H` ab, so wie es der
JHF-Parser schon tut. Nur so bedeutet eine eingestellte Größe in jeder Schrift dasselbe.

### Waagerechte Striche

Die Hershey-Schriften bringen einen Bindestrich von 0,86 Versalhöhen mit – breiter als jeder
Kleinbuchstabe – und setzen ihn auf deren Oberkante. Im Fließtext fällt beides auf.
`GlyphOverlayFont` ersetzt ihn deshalb: 0,30 Versalhöhen lang, auf halber x-Höhe, mit
0,14 Versalhöhen Luft an jeder Seite. Halbgeviert- (0,50) und Geviertstrich (0,70) sind eigene
Längen, weil das im Deutschen verschiedene Zeichen sind. Abgesichert durch `StricheTest`.

Bei den SVG-Schriften bleibt diese Korrektur **aus** – deren eigene Striche sind brauchbar.

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

## Schriftbild einstellen

Im Editor stellt „Schriftbild…" vier Regler auf: Laufweite, Wortabstand, Zeilenabstand und
Neigung. Die Vorschau folgt sofort; gespeichert wird der Wert erst, wenn der Finger den Regler
loslässt – sonst schriebe ein einziger Zug dutzende Male auf den Speicher.

„Einpassen" neben dem Größenregler sucht die größte Schriftgröße, bei der der Text in den
Rahmen passt, **ohne** dass ein Wort hart getrennt werden muss. Gesucht wird durch
Intervallhalbierung auf dem Zehntelmillimeter-Raster des Reglers; findet sich keine passende
Größe, bleibt die eingestellte stehen und die App sagt es, statt eine unlesbare zu setzen.

Die Breitenmessung dabei berücksichtigt die Neigung nicht – bei starker Neigung kann die
Schrift deshalb seitlich etwas über den eingestellten Rand hinausragen; bei kräftiger Neigung
lohnt sich also ein größerer Rand.

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

**Ausgenommen sind die Schriftdaten** unter `core/src/main/resources/fonts/`:

- Die Hershey-Vektorschriften (`*.jhf`). Ihre Nutzung ist an die Nennung von Dr. A. V. Hershey
  (ursprünglicher Urheber) und James Hurt (Format) gebunden. Der vollständige Wortlaut liegt in
  `HERSHEY-NOTICE.txt` im selben Verzeichnis und ist mit den Schriftdaten weiterzugeben.
- Die vier EMS-SVG-Schriften (`*.svg`), lizenziert unter der SIL Open Font License. Urheber:
  Sheldon B. Michaels (Schrift), Windell H. Oskay (SVG-Umsetzung); Ursprungsschriften und deren
  Gestalter stehen im Abschnitt „Schriften" oben. `EMS-OFL.txt` trägt den Lizenzwortlaut, aber
  keine ausgefüllte Copyright-Zeile – die Namen oben schließen diese Lücke.

## Stand

**Etappe 1 steht:** Schrift, Textsatz, G-Code, Vorschau, Streaming, Jog/Homing/Nullen,
Einstellungen. 86 Tests grün, APK gebaut und auf dem Gerät installiert. Die App verbindet
sich mit dem Plotter und zeigt Zustand und Position korrekt an (verifiziert am 2026-08-01).

- **Etappe 2a steht:** Einpassen und die vier Feintuning-Regler.
- **Etappe 2b steht:** vier verbundene SVG-Schreibschriften, Hershey-Kalligrafie entfernt.
- **Etappe 3:** Notizliste, Vorlagen mit Platzhaltern, gemischte Stile, Upload auf SD.

Hinweis zur Installation: Über USB brach die Übertragung reproduzierbar ab (Gerät ging
mitten im Streamed Install offline). Über WLAN läuft sie zuverlässig:

```bash
adb tcpip 5555 && adb connect <handy-ip>:5555
adb -s <handy-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Noch offen:
- Die Z-Achse hat keinen Endschalter und wird nicht referenziert. Ihr Nullpunkt entsteht nur
  über „Z hier nullen" und ist nach einem Neustart der Steuerung erneut zu setzen.
- **Die neuen SVG-Schriften wurden noch nicht am Gerät bedient und noch nicht an der Maschine
  erprobt.** Das Probeblatt in Allure bei 25 mm, das den Anschluss zeigen soll, steht noch aus.
