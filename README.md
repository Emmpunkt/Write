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
./gradlew test                 # 243 Tests, ohne Netz und ohne Gerät
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

## Maschinenwerte werden ausgelesen, nicht gepflegt

Verfahrweg, Untergrenzen, Beschleunigungen und Vorschubgrenzen holt die App **beim Verbinden**
aus `$/axes/x`, `/y` und `/z`. Nicht auf Knopfdruck: eine Abfrage, die man zu drücken vergessen
kann, ist dieselbe Fehlerquelle wie ein fest eingetragener Wert — und sie kostet Millisekunden.

Der Anlass ist belegt. Eine Notiz im Projekt nannte `mpos_mm: 3.0`; ausgelesen waren es 10. Die
Konfiguration hatte sich geändert, ohne dass es auffiel. Derselbe Plotter kann morgen andere
Werte haben, und ein anderer hat ohnehin andere.

Die Trennlinie:

| Kommt aus der Maschine | Bleibt Einstellung |
|---|---|
| Verfahrweg und Untergrenzen | Stifthöhen `Z_up`/`Z_down` (hängen am Stift, nicht am Gerät) |
| Beschleunigungen (XY und Z getrennt) | Papier-Offset, Blattformat, Ränder |
| Vorschub-**Obergrenzen** | Gewünschter Vorschub, bis zu dieser Grenze |

Langsamer zu schreiben bleibt also deine Entscheidung; nur ein Wert **über** dem, was die
Maschine kann, wird gekappt — sonst wäre bloß die Zeitschätzung zu optimistisch, denn die
Firmware begrenzt ohnehin. Ohne Verbindung gelten die gespeicherten Werte als Rückfall, damit
Vorschau und Grenzprüfung auch offline etwas Vernünftiges rechnen.

Aus demselben Grund bekommt der `MachineController` sein Profil als Provider und nicht als
Kopie: Verstellst du während bestehender Verbindung den Papier-Offset, entsteht der G-Code mit
dem neuen Wert — die Vorprüfung muss dann mit demselben rechnen und nicht mit dem von vorhin.

## Der Plotter

Ausgelesen am 2026-08-03 über Telnet, FluidNC v4.0.3 auf `192.168.2.18`. Die Werte stehen hier
als Beispiel, nicht als Vorgabe – die App holt sie sich beim Verbinden selbst (siehe oben):

| Größe | Wert | Folge für die App |
|---|---|---|
| Verfahrweg `$130`/`$131` | 155 × 105 mm | **ab dem Maschinennullpunkt**, nicht ab G54 |
| Untergrenze `mpos_mm` (X, Y) | > 0, variabel | fahrbar ist `[mpos_mm, mpos_mm + max_travel]` |
| Vorschub X/Y `$110`/`$111` | 1500 mm/min | Obergrenze für Schreiben und Leerfahrt |
| Vorschub Z `$112` | 2000 mm/min | |
| Beschleunigung `$120`/`$121` | 400 mm/s² | geht in die Zeitschätzung ein |
| Beschleunigung Z `$122` | 200 mm/s² | **halb so groß wie XY** – nicht gleichsetzen |
| Soft Limits `$20` | aktiv | Ein zu großer Auftrag löst Alarm aus – die Vorprüfung fängt ihn vorher ab |
| Homing `$22` | aktiv (nur X/Y) | `$H` verfügbar; Z hat `soft_limits: false` |
| Statusbericht `$10=1` | MPos; WCO nur **periodisch**, nicht in jedem Bericht | Der Parser merkt sich den letzten Versatz; `$#` beim Verbinden liefert ihn sofort statt erst nach Sekunden |

Nachgerechnet und verifiziert: `[G54:11.000,22.000,-10.750]` und `MPos Z −6.750`
ergeben `Zw = 4.000 mm`.

Die Z-Achse hat **keinen Endschalter** und bleibt beim Homing außen vor. Ihr Nullpunkt entsteht
allein über „Z hier nullen"; nach einem Neustart der Steuerung liegt die Maschinenkoordinate
dort auf 0, wo die Achse gerade steht, und muss neu gesetzt werden.

### Der fahrbare Bereich beginnt nicht bei null

`$130`/`$131` sind der Verfahrweg **ab dem Maschinennullpunkt**, nicht ab dem Arbeitsnullpunkt.
In `$/axes/x` und `$/axes/y` steht bei negativer Referenzfahrt ein `mpos_mm` > 0 – nach dem
Homing steht die Maschine genau dort, und weiter zurück geht es nicht. Fahrbar ist
`[mpos_mm, mpos_mm + max_travel]`.

Die App liest deshalb beim Verbinden `$/axes/x`, `$/axes/y` und `$/axes/z` aus und rechnet mit
dem echten Bereich. Kennt eine Firmware die Abfrage nicht, fällt sie auf die frühere Annahme
`[0, $130]` zurück: die irrt in die sichere Richtung, solange der Arbeitsnullpunkt über der
wahren Untergrenze liegt, und verschenkt dann nur ein paar Millimeter.

Warum das keine Kosmetik ist: **Der Arbeitsnullpunkt muss auf oder über dieser Untergrenze
liegen.** Lag er darunter, löste jeder Auftrag ALARM:2 aus – mitten in der Bewegung, mit halb
beschriebenem Blatt, und die alte Prüfung gegen `[0, $130]` hätte ihn anstandslos
durchgelassen. Betroffen ist auch die Rückfahrt `G0 X0 Y0` am Ende jedes Auftrags: dort liegt
kein Strich, gefahren wird trotzdem. Beides prüft die App jetzt.

Aus derselben Abfrage kommt `acceleration_mm_per_sec2` – siehe „Wie lange dauert das?".

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

## Wie lange dauert das?

Die Schätzung vor dem Senden rechnet **mit Beschleunigungsrampen**, nicht mit `Weg / Vorschub`.
Der Unterschied ist nicht akademisch: ohne sie lag die Angabe rund ein Viertel zu niedrig
(gemessen 15 Minuten, geschätzt 11:20). In den Messwerten steht auch, warum – der tatsächliche
Vorschub schwankte zwischen 157 und 1.804 mm/min, weil die Maschine bei den kurzen Segmenten
einer Schreibschrift den Sollvorschub selten erreicht.

Gerechnet wird deshalb Bewegung für Bewegung: jeder Strichzug und jede Leerfahrt beginnt aus
dem Stand und endet im Stand, denn dazwischen wird der Stift gehoben und gesenkt. Reicht die
Strecke für den Sollvorschub, ist es ein Trapez (`s/v + v/a`); reicht sie nicht, ein Dreieck
(`2·√(s/a)`) – und dieser zweite Fall ist bei einer Schreibschrift die Mehrheit.

Die Beschleunigung kommt aus der verbundenen Maschine – **getrennt für XY und Z**, weil sie
sich unterscheiden (gemessen: 400 gegenüber 200 mm/s²). Wer beides gleichsetzt, verrechnet sich
bei den Stifthüben, und davon hat ein Auftrag Hunderte. Ohne Verbindung gilt ein Vorgabewert;
die Schätzung ist dann ungenauer, aber nichts wird dadurch unsicher.

Ein pauschaler Korrekturfaktor wäre der falsche Weg gewesen: er träfe den langen Strich genauso
wie den kurzen, obwohl der Fehler nur bei den kurzen entsteht.

Ein zweiter Punkt kam aus derselben Messung: **Das Anheben des Stifts ist ein Eilgang, das
Absenken nicht.** Der erzeugte G-Code senkt mit `G1 … F600` (begrenzt, sonst schlägt der lose
Stift auf), hebt aber mit `G0` — und das fährt mit dem Höchstvorschub der Achse. Beide gleich
zu rechnen macht jeden Hub zu lang; bei hunderten Hüben je Auftrag ist das deutlich sichtbar.

Am 2026-08-03 an einem echten Bogen nachgemessen (A6 quer, 396 Zeilen, 28 Hübe, 55 s):

| Modell | Schätzung | Abweichung |
|---|---|---|
| alte Formel `Weg / Vorschub` | 51 s | −7 % |
| Rampen, Z-Hub einheitlich | 62 s | +13 % |
| Rampen + Eilgang beim Anheben | 56,5 s | **+3 %** |

Das ist **eine** Messung an **einem** Auftrag, keine Garantie. Der frühere Befund (25 % zu
niedrig) stammt von einem viel größeren Bogen, dessen damalige Einstellungen nicht mehr
rekonstruierbar sind. Junction Deviation bleibt weiterhin außen vor: `rampSeconds` nimmt an,
der Planer fahre einen Strichzug ohne Zwischenstopp durch, während FluidNC an scharfen Ecken
abbremst — bei einem langen Text dürfte die Schätzung deshalb wieder zu knapp werden.

## Notizen

Mehrere Notizen liegen dauerhaft in einer Room-Datenbank. Die Liste klappt über dem Textfeld
auf; angetippt wird umgeschaltet, „+ Neu" legt an, das Papierkorb-Symbol löscht nach Rückfrage.

**Jede Notiz trägt ihr eigenes Schriftbild** — Schriftart, Größe, Ausrichtung, Zeilen-, Zeichen-
und Wortabstand, Neigung. Beim Umschalten kommt es mit. Eine neue Notiz erbt es von der zuletzt
offenen: wer eine Einkaufsliste in 5 mm schreibt, schreibt die nächste meist genauso.

**Blattformat, Rand und Papier-Offset gelten dagegen global.** Sie beschreiben, was auf dem
Tisch liegt, nicht wie die Notiz aussieht. Ein Notizwechsel darf das eingelegte Papier nicht
„ändern" — sonst plottete man auf einen Bogen, der gar nicht drunterliegt. Ein abweichendes
Format gehört zu den Vorlagen (Teil 3), nicht zur Notiz.

Der **Titel ist die erste nicht-leere Zeile**, abgeleitet statt gespeichert: ein eigenes Feld
wäre ein zweiter Ort für dieselbe Information und müsste beim Tippen nachgeführt werden.

Zwei Regeln sitzen im Repository und nicht in der Oberfläche: Es gibt **immer mindestens eine
Notiz**, und die **letzte wird geleert statt gelöscht**. Der Editor darf nie ohne Notiz
dastehen. Welche Notiz offen ist, wird gemerkt und nicht aus den Zeitstempeln erschlossen —
beim Wechseln wird die *verlassene* Notiz gespeichert und trüge danach die neuere Zeit.

## Serie: viele Karten am Stück

Für einen Satz gleichartiger Karten — Platzkarten, Einladungen. Eine **Vorlage** mit benannten
Platzhaltern in geschweiften Klammern, dazu eine Werteliste:

```
Vorlage:  {anrede} {name}, wir freuen uns auf dich!
Werte:    Liebe;Anna
          Lieber;Bernd
```

Eine Zeile je Bogen, Felder durch **Semikolon** getrennt. Die Spalten ordnen sich den
Platzhaltern in der Reihenfolge ihres ersten Auftretens zu; der Reiter zeigt die erwartete Form
über dem Feld an. Das Komma schied als Trennzeichen aus, weil es in Namen vorkommt („Schmidt,
Anna"). Ein Wert mit Semikolon ist damit nicht darstellbar — bei Anreden und Namen belanglos.

Benannt statt durchnummeriert, weil man `{anrede} {name}` nach Monaten noch versteht; bei
`{1} {2}` müsste man die Werteliste danebenlegen und abzählen.

**Eine Vorlage trägt Schriftbild UND den ganzen Textrahmen** — Breite, Höhe, Rand *und*
Position. Das ist der Unterschied zur Notiz: Eine Notiz wird auf das Papier geschrieben, das
gerade auf dem Tisch liegt; eine Vorlage beschreibt, wo genau der Text stehen soll. Alle fünf
Werte sind im Serie-Reiter als Zahlenfelder erreichbar.

> Anfangs blieb der Versatz global, mit der Begründung, er beschreibe den Anschlag. Das war ein
> Trugschluss (2026-08-04): Der „Bogen" ist in Wahrheit eine Textbox auf dem Tisch — wer auf
> einer Grußkarte unten rechts schreiben will, braucht Größe und Position in derselben Vorlage.
> Die globalen Werte in den Optionen sind seitdem nur noch die Vorgabe für Notizen und für neu
> angelegte Vorlagen.

**Vor dem Start wird jeder Bogen durchgerechnet** — mit demselben `layoutText`, aus dem auch
Vorschau und G-Code entstehen. Läuft einer über oder wird ein Wort mitten im Wort getrennt,
nennt die App Bogennummer und Namen („Bogen 14 „Christiane Schmidt-Wagner" läuft über") und
sperrt den Start. Kein verschwendetes Papier. Zeilen mit falscher Feldzahl werden gemeldet
statt stillschweigend ergänzt — eine Lücke fiele erst auf dem Papier auf.

**Ein Auftrag je Bogen.** Nach jedem hält die App an („Bogen 3 von 20 fertig — nächstes Blatt
einlegen") und wartet auf Knopfdruck. Ein fehlgeschlagener Bogen **rückt den Zähler nicht
weiter**: „Nochmal" plottet denselben, erst „Überspringen" geht weiter. Das ist der Unterschied
zwischen „das Blatt ist verrutscht" und „diesen Namen lasse ich weg".

Beide Sendewege funktionieren, weil die Serie nur die vorhandene Auftragskette je Bogen aufruft
— samt Grenzprüfung und Rückfahrt auf den Nullpunkt.

> **Nach einem Abbruch ist die Maschine nicht mehr referenziert.** Der Not-Halt setzt die
> Steuerung zurück; der nächste Bogen meldet dann „noch nicht referenziert". Erst wieder
> Homing fahren, dann weiter.

## Zwei Wege zum Plotter

| | **Auf SD senden** | **Direkt senden** |
|---|---|---|
| Weg | `POST /upload`, dann `$SD/Run=` | Zeile für Zeile über Telnet |
| Verbindungsabbruch | Auftrag läuft weiter | Stift bleibt auf dem Papier stehen |
| Fortschritt | grob (siehe unten) | Zeile für Zeile, genau |
| Not-Halt | erreichbar, solange die Verbindung steht | erreichbar |

Beide durchlaufen **dasselbe `preflight`** — Grenzprüfung, Homing-Pflicht und Idle-Zustand
sitzen an einer Stelle im `MachineController`. Ein zweiter Sendeweg mit eigener, womöglich
lückenhafter Sicherheitslogik wäre genau die Abkürzung, die später ein Blatt kostet.

Schlägt der Upload fehl, wird **nichts** gestartet. Sonst plottete die Maschine die Datei vom
letzten Mal — also einen anderen Text. Und es gibt keinen stillen Rückfall von SD auf Telnet:
bei zwei Knöpfen muss sichtbar bleiben, welcher Weg lief.

Die Datei heißt immer `/write.nc` und wird überschrieben. Eine Historie auf der Karte bräuchte
Verwaltung, die niemand will — der Text liegt ohnehin in der App.

### Warum der SD-Fortschritt grob ist

Am Gerät nachgemessen: Das Feld `SD:<prozent>` im Statusbericht ist der **Lesefortschritt der
Datei**, nicht der der Bewegung. Bei einer kleinen Datei steht dort sofort 100 %, während die
Achse noch fährt — FluidNC liest voraus. Der Wert wird trotzdem angezeigt (bei einem vollen
Blatt ist er die einzige Zahl, die es gibt), aber das **Ende** erkennt die App am
Zustandswechsel `Run` → `Idle` und nicht am Prozentwert.

## Warum nur Telnet für Befehle

Die Weboberfläche nimmt Befehle unter `/command` zwar entgegen, liefert die Antworten aber über
ihren WebSocket aus – eine reine HTTP-Anfrage beantwortet sie mit `WebSocket dead`. Ein
HTTP-Transport für **Befehle** wäre damit ohne eigene WebSocket-Anbindung wertlos und für einen
Auftrag ohnehin zu langsam, weil jede Zeile einen eigenen Umlauf bräuchte. Telnet auf Port 23
kann alles, was die App an Befehlen benötigt.

**Für Dateien gilt das nicht.** Am 2026-08-03 nachgeprüft: `POST /upload` antwortet mit 200 und
legt die Datei korrekt ab, obwohl `/command` weiterhin `WebSocket dead` liefert. Der frühere
Schluss „HTTP ist bei diesem Gerät wertlos" galt also nur für Befehle. Der Upload läuft
deshalb über HTTP – mit `HttpURLConnection` aus dem JDK und **ohne** neue Abhängigkeit: für
einen einzigen multipart-POST wäre eine Bibliothek unverhältnismäßig, zumal OkHttp aus diesem
Projekt schon einmal entfernt wurde.

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
Einstellungen. Am echten Gerät und an der echten Maschine verifiziert: der Dauertest schrieb
A6 quer, 3.480 mm Strich in 790 Pen-Down-Zyklen, rund 15 Minuten ohne Abbruch oder Alarm, und
endete sauber auf dem Arbeitsnullpunkt mit angehobenem Stift (2026-08-02).

- **Etappe 2a steht:** Einpassen und die vier Feintuning-Regler.
- **Etappe 2b steht:** vier verbundene SVG-Schreibschriften, Hershey-Kalligrafie entfernt.
  Am 2026-08-02 an der Maschine geschrieben.
- **Etappe 3, Teil 1 steht:** Upload auf die SD-Karte, zwei getrennte Sendewege.
- **Etappe 3, Teil 2 steht:** Notizliste mit Room, jede Notiz mit eigenem Schriftbild.
  Am 2026-08-03 am Gerät durchgespielt.
- **Etappe 3, Teil 3 steht:** Vorlagen mit Platzhaltern und Serienlauf. Am 2026-08-03 an der
  Maschine gefahren — drei Bogen mit Blattwechsel, dazu der Fehlschlag-Pfad.
- **Etappe 3, offen:** gemischte Stile je Absatz.

243 Tests grün, alle ohne Netz und ohne Gerät.

Hinweis zur Installation: Über USB brach die Übertragung reproduzierbar ab (Gerät ging
mitten im Streamed Install offline). Über WLAN läuft sie zuverlässig:

```bash
adb tcpip 5555 && adb connect <handy-ip>:5555
adb -s <handy-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Noch offen:
- Die Z-Achse hat keinen Endschalter und wird nicht referenziert. Ihr Nullpunkt entsteht nur
  über „Z hier nullen" und ist nach einem Neustart der Steuerung erneut zu setzen.
- **Der Arbeitsnullpunkt des Geräts passt nicht zum fahrbaren Bereich** (G54 auf Maschine 3,
  Untergrenze 10). Entweder G54 neu setzen oder durchgehend mit Rand ≥ 7 mm arbeiten. Die
  Grenzprüfung fängt es ab, statt es in den Alarm laufen zu lassen.
- Die Zeitschätzung liegt weiterhin zu niedrig, nur weniger als vorher – siehe „Wie lange
  dauert das?".
- Die Zeitschätzung fällt bei den SVG-Kursiven zu hoch aus (gemessen 2026-08-03: geschätzt
  59 s, real 30–45 s). Vom Nutzer zurückgestellt — gehört mit einer Messreihe über mehrere
  Schriften und Textlängen sauber vermessen, nicht nachjustiert.
