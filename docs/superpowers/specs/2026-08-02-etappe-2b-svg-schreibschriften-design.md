# Etappe 2b: Einlinige SVG-Schreibschriften

Stand 2026-08-02. Baut auf Etappe 2a auf (Einpassen und Feintuning-Regler, auf `main`).

## Anlass: ein gemessener Fehler, kein Wunsch

Nach dem ersten Probeblatt mit 25 mm Versalhoehe fiel auf, dass das Ende eines Buchstabens
nicht dort liegt, wo der naechste anfaengt. Die Untersuchung hat den Befund bestaetigt und
verortet - er steckt in den Schriftdaten, nicht in der Maschine:

- Im fertigen G-Code gibt es **keine** Spruenge zwischen zeichnenden Segmenten (groesster
  Sprung 0,000 mm). Die App hebt an jeder Unterbrechung sauber den Stift; es entsteht eine
  Luecke, kein Kratzer.
- Von den 676 Kleinbuchstabenpaaren der Hershey-Schreibschrift verbinden **85 % exakt**, der
  Rest nicht. Mittlerer Versatz 0,99 mm bei 25 mm Versalhoehe.
- Im geplotteten Text `Etappe 2a geschafft` waren es konkret: `t`->`a` 6,9 mm, `2`->`a` 6,1 mm,
  `E`->`t` 3,6 mm; die uebrigen Uebergaenge 0,00 mm.
- Ursache: `t` endet oben am Querstrich (y=13 statt y=5), Grossbuchstaben und Ziffern haben gar
  keinen Anschlusspunkt.
- Die Hershey-Kalligrafie ist deutlich schlechter: nur 21 % verbunden, 6,6 mm mittlerer Versatz.
- Der Effekt skaliert mit der Groesse. Bei den ueblichen 7 mm sind es im Mittel 0,28 mm und
  damit unsichtbar - deshalb ist er bis zum ersten grossen Probeblatt nie aufgefallen.

Die Hershey-Schreibschriften sind schlicht nicht als durchgehend verbundene Kursiven gezeichnet.
Das ist mit Reglern nicht zu beheben; es braucht andere Schriftdaten.

## Umfang

1. Ein zweiter Font-Parser fuer einlinige SVG-Schriften, hinter der bestehenden
   `StrokeFont`-Schnittstelle.
2. Vier neue Schriften: EMS Allure, EMS Decorous Script, EMS Delight, EMS Invite.
3. Die Hershey-Kalligrafie entfaellt; Schreibschrift, Technisch und Serif bleiben.

Ausdruecklich **nicht** in diesem Schnitt: Dekor (Rahmen, Linien, Aufzaehlungen), Notizliste,
Vorlagen, gemischte Stile, SD-Upload.

## Die Schriften

Quelle: `gitlab.com/oskay/svg-fonts`, Verzeichnis `fonts/EMS`. Die Schriften stammen von
Sheldon B. Michaels, die SVG-Umsetzung von Windell H. Oskay, Lizenz **SIL Open Font License**.

| Datei | Name in der App | Charakter |
|---|---|---|
| `EMSAllure.svg` | Allure | schwungvolle, stark geneigte Kursive, verbunden |
| `EMSDecorousScript.svg` | Zierschrift | verbunden, aufrechter und ruhiger, gut lesbar |
| `EMSDelight.svg` | Druckschrift | klare moderne Handschrift, Buchstaben stehen einzeln |
| `EMSInvite.svg` | Einladung | feine elegante Kursive, verbunden |

Jede Datei bringt 216 Glyphen mit, darunter **ae, oe, ue, Ae, Oe, Ue, Eszett, Euro sowie
Halbgeviert- und Geviertstrich**. Alle vier laufen deutlich breiter als die Hershey-Schriften
(185-217 mm fuer `Einkaufsliste: Milch & Brot` bei 9 mm Versalhoehe).

Die Dateien kommen mit `OFL.txt` nach `core/src/main/resources/fonts/`; die Nennung kommt ins
README neben die bestehende Hershey-Auflage.

## Das Dateiformat

SVG-Font (SVG 1.1). Aufbau:

```xml
<font id="EMSAllure" horiz-adv-x="378">
<font-face units-per-em="1000" ascent="800" descent="-200" cap-height="500" x-height="300"/>
<glyph unicode="a" horiz-adv-x="526" d="M 495 183 L 457 132 ... L 324 343"/>
```

Vier Eigenschaften bestimmen den Parser:

1. **Die Pfade sind bereits geflattet.** In `EMSAllure.svg` stehen 4259 `L`-, 417 `M`- und nur
   76 `C`-Kommandos. Ein voller SVG-Pfad-Parser ist unnoetig; `M`, `L` und kubisches `C`
   genuegen.
2. **Die Y-Achse zeigt bereits nach oben**, Grundlinie bei y=0, Unterlaengen negativ. Das ist
   genau die Konvention aus `Glyph` - anders als bei JHF ist keine Spiegelung noetig.
3. **`unicode` ist HTML-kodiert** (`&#xe4;` fuer ae). Das muss entkodiert werden, sonst fehlen
   ausgerechnet die Umlaute.
4. **`cap-height` ist unbrauchbar.** Alle vier Dateien geben 500 an, gemessen sind es aber
   Allure 699, Decorous Script 639, Delight 665, Invite 939. Der Konverter hat den Wert
   pauschal gesetzt.

## Metriken aus den Glyphen, nicht aus dem font-face

Die Groessenangabe der App ist die **Versalhoehe in Millimetern** - sie ist am Papier
nachmessbar, das ist ihr ganzer Sinn. Uebernaehme der Parser `cap-height="500"`, waere ein auf
7 mm eingestellter Text in EMS Invite fast doppelt so gross wie in EMS Allure, obwohl beide
dieselbe Zahl anzeigen.

`SvgFont` leitet die Metriken deshalb genauso ab wie `HersheyFont`:

- **Versalhoehe** aus der tatsaechlichen Hoehe von `H` (Rueckfall `A`, `X`, `x`).
- **Oberlaenge/Unterlaenge** als Extremwerte ueber alle Glyphen.
- **Zeilenhoehe** aus den typischen Buchstaben `h b d k l` und `g p q y j` - nicht aus dem
  Maximum ueber alle Glyphen, weil Klammern und Akzente sonst jede Zeile auseinandertrieben.

Damit bedeutet eine eingestellte Groesse in jeder der sieben Schriften dasselbe.

## Der Parser

Neue Datei `core/src/main/kotlin/de/emmpunkt/write/core/font/SvgFont.kt`, reines Kotlin ohne
neue Abhaengigkeit - `core` bleibt frei von Fremdbibliotheken.

```kotlin
object SvgFont {
    fun parse(id: String, displayName: String, content: String): StrokeFont
}
```

Vorgehen:

1. Alle `<glyph …>`-Elemente ueber einen regulaeren Ausdruck einsammeln. Die Dateien sind
   maschinengeneriert und gleichfoermig; ein XML-Parser braechte eine Plattformabhaengigkeit
   ohne Gewinn.
2. Je Glyphe `unicode` (entkodiert, nur Einzelzeichen), `horiz-adv-x` und `d` lesen. Fehlt `d`
   - beim Leerzeichen - entsteht eine Glyphe ohne Striche mit blossem Vorschub.
3. Den Pfad in Strichzuege zerlegen: `M` beginnt einen neuen Zug, `L` verlaengert ihn, `C`
   wird in **acht** gerade Stuecke unterteilt. Die Unterteilung ist bewusst fest und nicht
   adaptiv: bei 76 Kurven in der ganzen Datei lohnt keine Fehlerschaetzung, und acht Stuecke
   liegen bei 1000 Einheiten Kegelhoehe unter der Strichbreite eines Fineliners.
4. Metriken wie oben ableiten.

## Verzeichnis und Overlay

`Fonts.Entry` bekommt ein Feld fuer das Format (JHF oder SVG); `Fonts.load` waehlt danach den
Parser. Das vorhandene Feld `cursive` wird fuer die neuen Eintraege gesetzt: bei Allure,
Zierschrift und Einladung ja, bei Druckschrift nein. Die Liste umfasst danach sieben Schriften:
die vier EMS, dazu Schreibschrift, Technisch und Serif. Die Kalligrafie faellt weg - gespeicherte `fontId`s laufen ueber den vorhandenen
Rueckfall in `Fonts.entry` auf die Vorgabe, es bricht also nichts.

`GlyphOverlayFont` bleibt ueber beiden Formaten, arbeitet bei SVG-Schriften aber fast nie: die
Basisschrift wird zuerst gefragt, und sie kennt Umlaute, Eszett, Euro und die Striche selbst.
Was sie nicht kennt, sind die typografischen Anfuehrungszeichen **U+2018, U+2019 und U+201E** -
die setzen Android-Tastaturen selbsttaetig ein, und dafuer bleibt die Ersetzung noetig.

**Eine Aenderung ist erforderlich:** Die Strich-Korrektur (`STRICHE`) greift heute *vor* der
Basisschrift, weil Hersheys Bindestrich mit 0,86 Versalhoehen zu lang ist und auf der
Oberkante sitzt. Fuer die EMS-Schriften waere das falsch - sie bringen ordentliche eigene
Striche mit. Die Korrektur wird deshalb ueber einen Konstruktorparameter abschaltbar und fuer
SVG-Schriften nicht angewandt.

## Absicherung

Neue `SvgFontTest` im `core`, ohne Geraet und ohne Netz:

1. Ein kleiner, im Test eingebetteter SVG-Font wird geparst: zwei Glyphen mit bekannten
   Koordinaten, Vorschub und Metriken kommen exakt heraus.
2. `unicode="&#xe4;"` liefert die Glyphe fuer ae - die Entkodierung ist die Stelle, an der ein
   Fehler ausgerechnet die deutschen Zeichen kostet.
3. Ein `C`-Segment wird zu mehreren Punkten unterteilt, die alle zwischen Start- und Endpunkt
   liegen; Start- und Endpunkt selbst bleiben exakt erhalten.
4. Eine Glyphe ohne `d` (Leerzeichen) ergibt keine Striche, aber den richtigen Vorschub.
5. Fuer jede der vier mitgelieferten Schriften: alle Zeichen aus `abc…zäöüß.,-!?` sind
   vorhanden, und die abgeleitete Versalhoehe stimmt mit der gemessenen Hoehe von `H` ueberein.
6. Die Ersetzung greift: U+2019 liefert in einer SVG-Schrift die Glyphe des ASCII-Apostrophs.
7. Die Strich-Korrektur bleibt bei SVG-Schriften aus - der Bindestrich ist der eigene der
   Schrift, nicht der nachgezeichnete.

`PreviewSamplesTest` bekommt einen Vergleichsbogen aller sieben Schriften mit demselben Text.

**Was bewusst nicht getestet wird:** ob Buchstaben "verbinden". Ein erster Messversuch dafuer
nahm an, der letzte Punkt eines Pfades sei der Auslaufstrich - in diesen Schriften folgt die
Strichrichtung aber nicht der Schreibrichtung, und die Messung lieferte fuer EMS Allure 30 %
statt der im Bild sichtbaren durchgehenden Verbindung. Ein Test auf einer falschen Annahme
waere schlimmer als keiner. Die Musterbilder leisten das zuverlaessig.

## Abnahme

- `./gradlew test` gruen, einschliesslich `SvgFontTest`.
- Die Musterbilder zeigen alle sieben Schriften; bei den drei verbundenen EMS-Schriften gehen
  die Buchstaben sichtbar ineinander ueber.
- In der App stehen sieben Schriften zur Wahl, die Kalligrafie ist verschwunden, und eine zuvor
  auf Kalligrafie eingestellte Notiz oeffnet ohne Fehler.
- Eine Notiz mit Umlauten, Eszett und Bindestrich ist in jeder der vier EMS-Schriften
  vollstaendig darstellbar - der Editor meldet keine fehlenden Zeichen.
- Ein Probeblatt an der Maschine in EMS Allure bei 25 mm: die Buchstaben haengen zusammen, der
  im Anlass beschriebene Versatz ist weg.
