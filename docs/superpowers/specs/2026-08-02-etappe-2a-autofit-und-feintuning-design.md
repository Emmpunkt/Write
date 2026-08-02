# Etappe 2a: Auto-Fit und Feintuning-Regler

Stand 2026-08-02. Baut auf Etappe 1 auf (abgeschlossen, an der Maschine verifiziert).

## Warum dieser Zuschnitt

Etappe 2 umfasst laut `CLAUDE.md` vier Punkte: SVG-Script-Fonts, Auto-Fit, Feintuning-Regler
und Dekor. Die SVG-Fonts sind der mit Abstand groesste davon – neuer Parser, neue
Schriftdateien, eigene Lizenzlage.

Sie kommen deshalb **spaeter als eigener Schnitt (Etappe 2b)**. Zuerst die beiden kleinen
Punkte, die das Schriftbild der vorhandenen Hershey-Schriften sofort verbessern und am Papier
beurteilbar machen. Danach ist bekannt, welche Stellschrauben wirklich zaehlen – dieses Wissen
geht in die Font-Arbeit ein.

Dekor (Rahmen, Trennlinien, Unterstreichungen) bleibt ebenfalls ausserhalb dieses Spec.

## Umfang

1. Auto-Fit: ein Knopf, der die groesste passende Schriftgroesse setzt.
2. Vier zusaetzliche Regler im Editor: Laufweite, Wortabstand, Zeilenabstand, Neigung.
3. Die Reglerwerte werden erst beim Loslassen gespeichert, nicht bei jeder Bewegung.

Ausdruecklich **nicht** in diesem Schnitt: SVG-Fonts, Dekor, Notizliste, der bekannte
Korrekturfaktor der Zeitschaetzung.

## Ausgangslage im Code

`TextStyle` fuehrt `sizeMm`, `lineSpacing`, `letterSpacing`, `wordSpacing` und `slantDeg`
bereits vollstaendig; `layoutText` wertet alle fuenf aus. Im Datenmodell fehlt also nichts.

Der Editor hat in `EditorScreen.StilLeiste` bereits Schriftwahl, Blattwahl, einen
Groessenregler und die Ausrichtung. Ausrichtung und Schriftauswahl bleiben unveraendert dort.

Zwei Schwaechen des heutigen Groessenreglers behebt dieser Schnitt mit:

- Er ruft `updateSettings`, und das ruft `persist()`. Waehrend eines Reglerzugs wird also bei
  jedem Wertschritt in DataStore geschrieben.
- Die uebrigen vier Stilwerte sind ueberhaupt nicht bedienbar.

## Teil 1 – `fitSize` im core-Modul

Neue Datei `core/src/main/kotlin/de/emmpunkt/write/core/layout/AutoFit.kt`. Reines Kotlin,
ohne Android-Bezug, damit auf dem PC testbar – wie der Rest von `core`.

```kotlin
data class FitResult(val sizeMm: Float, val fits: Boolean)

fun fitSize(
    text: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
    minMm: Float = 2f,
    maxMm: Float = 25f,
    stepMm: Float = 0.1f,
): FitResult
```

`style.sizeMm` wird von der Funktion ignoriert und ersetzt; alle uebrigen Felder des uebergebenen
Stils bleiben wirksam, damit die Suche mit genau der Laufweite und dem Zeilenabstand rechnet,
die auch gefahren werden.

### Kriterium

Eine Groesse gilt als passend, wenn `layoutText` mit ihr **weder Ueberlauf noch harte Trennung**
meldet:

```kotlin
laid.overflow == false && laid.overlongWords.isEmpty()
```

Harte Trennungen sind der Fehler, den die App an anderer Stelle rot anmahnt. Eine Groesse zu
liefern, bei der die Warnung stehen bleibt, waere kein Einpassen.

### Verfahren

Intervallhalbierung auf dem **0,1-mm-Raster**, demselben Raster, das der Regler anbietet – sonst
nennt die App eine Groesse, die sich von Hand nicht mehr treffen laesst. Zwischen 2 und 25 mm
sind das rund acht Halbierungen, also acht Layout-Durchlaeufe. Sie laufen nur auf Knopfdruck,
nicht beim Tippen.

Die Halbierung setzt voraus, dass eine kleinere Groesse eher passt als eine groessere. Das gilt
nicht streng: der Zeilenumbruch ist eine Treppenfunktion, ein Wort kann bei einer Winzigkeit
weniger eine Zeile hochrutschen und die Gesamthoehe damit springen lassen.

Die Suche haelt deshalb eine **Invariante** durch: die untere Schranke ist immer eine bereits
geprueft passende Stufe, die obere immer eine geprueft nicht passende. Beide Schranken sind
tatsaechlich geprueft, nicht erschlossen – die Nichtmonotonie kann ihnen damit nichts anhaben.

Am Ende der Halbierung liegen die Schranken eine Stufe auseinander. Dieselbe Invariante liefert
damit beides, worauf es ankommt: **das Ergebnis passt, und eine Stufe groesser passt nicht.**
Eine Nachkorrektur eruebrigt sich.

Was die Invariante **nicht** zusichert: dass es jenseits der oberen Schranke keine noch
groessere passende Stufe gibt. Sicher waere das bei einer Treppenfunktion nur mit der
vollstaendigen Suche ueber alle 230 Stufen – das Zwanzigfache an Rechenzeit fuer einen Fall,
der in echten Notizen nicht vorkommt. Bewusst nicht gemacht.

### Grenzfaelle

| Fall | Ergebnis |
|---|---|
| Leerer oder nur aus Leerzeichen bestehender Text | `FitResult(maxMm, fits = true)` |
| Passt schon bei `maxMm` | `FitResult(maxMm, fits = true)` |
| Passt auch bei `minMm` nicht | `FitResult(minMm, fits = false)` |

Bei `fits = false` aendert die App die Groesse **nicht** und meldet es stattdessen.

## Teil 2 – Editor und Persistenz

### Oberflaeche

`StilLeiste` in `EditorScreen.kt` bekommt unter der Groessenzeile eine ausklappbare Zeile
„Schriftbild". Zugeklappt sieht der Editor aus wie heute. Aufgeklappt erscheinen vier Regler
mit Wertanzeige:

| Regler | Feld | Bereich | Anzeige | Vorgabe |
|---|---|---|---|---|
| Laufweite | `letterSpacing` | −0,2 … +0,5 | Prozent | 0 |
| Wortabstand | `wordSpacing` | −0,6 … +1,0 | Prozent | −0,3 |
| Zeilenabstand | `lineSpacing` | 0,8 … 2,0 | Faktor | 1,15 |
| Neigung | `slantDeg` | −20 … +20 | Grad | 0 |

Der Aufklappzustand ist reiner Bildschirmzustand (`remember`) und wird nicht gespeichert.

Daneben ein Knopf **„Zuruecksetzen"**, der genau diese vier Werte auf die Vorgaben stellt. Bei vier
gekoppelten Reglern verstellt man sich schnell, und der Weg zurueck waere sonst Raten. Groesse,
Schrift, Blatt und Ausrichtung ruehrt er nicht an.

In die Groessenzeile kommt rechts der Knopf **„Einpassen"**. Er ist deaktiviert, solange der
Text leer ist – sonst spraenge die Schrift auf die Obergrenze, ohne dass etwas zu sehen waere.

### Persistenz

Das `PlotterViewModel` bekommt zwei Wege statt einem:

```kotlin
/** Waehrend eines Reglerzugs: Zustand und Vorschau aktualisieren, aber nicht speichern. */
fun updateSettingsLive(transform: (AppSettings) -> AppSettings)

/** Beim Loslassen: den erreichten Wert einmal speichern. */
fun commitSettings()
```

`Slider.onValueChange` ruft `updateSettingsLive`, `Slider.onValueChangeFinished` ruft
`commitSettings`. Das bestehende `updateSettings` (Zustand + Speichern in einem) bleibt
unveraendert fuer alles, was kein Regler ist: Schrift, Blatt, Ausrichtung. Dort ist sofortiges
Speichern richtig, weil es keinen Zug gibt, waehrend dessen sich der Wert fortlaufend aendert.

Der bestehende Groessenregler wird auf denselben Weg umgestellt.

Kein Entprellen, keine Verzoegerung, keine zusaetzliche Coroutine. Der Unterschied ist allein,
**wann** geschrieben wird, nicht wie oft pro Sekunde.

### Einpassen

```kotlin
fun autoFit()
```

Laedt die Schrift, ruft `fitSize` mit dem aktuellen Text, Stil und Rahmen. Bei `fits = true`
wird `sizeMm` gesetzt, der Satz neu gerechnet und sofort gespeichert. Bei `fits = false` bleibt
die Groesse stehen und es erscheint der Hinweis (mit dem tatsaechlichen `minMm` im Text):

> Passt auch bei 2 mm nicht – Rand verkleinern oder Text kuerzen.

Bei leerem Text tut `autoFit()` nichts. `fitSize` liefert dort zwar `maxMm`, aber diese Groesse
zu uebernehmen waere unsinnig; der Knopf ist in diesem Fall ohnehin deaktiviert, und die
Abfrage im ViewModel deckt den Rest ab.

Die Meldung laeuft ueber den bestehenden `message`-Weg in `MachineUiState`.

## Teil 3 – Absicherung

### Ohne Geraet und ohne Netz pruefbar

Neue `core/src/test/kotlin/de/emmpunkt/write/core/layout/AutoFitTest.kt`:

1. Ein Text, der bei 7 mm ueberlaeuft, ergibt eine kleinere Groesse, und `layoutText` meldet bei
   dieser Groesse weder Ueberlauf noch harte Trennung.
2. **Maximalitaet:** eine Stufe groesser (`+0,1 mm`) verletzt mindestens eines der beiden
   Kriterien. Ohne diesen Test bestuende auch „gib immer 2 mm zurueck" die Pruefung.
3. Das Ergebnis liegt auf dem 0,1-mm-Raster.
4. Ein Text, der schon bei 25 mm passt, bekommt 25 mm – die Suche geht nicht unnoetig herunter.
5. Ein sehr langes Wort auf schmalem Blatt liefert `fits = false`, nicht eine Groesse, bei der
   das Wort weiterhin hart getrennt wuerde.
6. Leerer Text stuerzt nicht ab und liefert `maxMm`.
7. Die Invariante traegt breit: fuer mehrere verschiedene Texte und Rahmen gilt jedes Mal
   beides – das Ergebnis passt, und eine Stufe groesser passt nicht.

Erweiterung von `PreviewSamplesTest`: Musterbilder mit verschiedenen Feintuning-Werten
(enge und weite Laufweite, geneigt, enger Zeilenabstand). Damit laesst sich das Schriftbild am
Bildschirm beurteilen, bevor Papier und Maschinenzeit draufgehen.

### Nicht automatisiert pruefbar

Die Live/Commit-Trennung sitzt im `PlotterViewModel`, einem `AndroidViewModel`. Ein Test dafuer
braeuchte Robolectric oder Instrumentierung; beides gibt es im Projekt nicht, und den Unterbau
allein hierfuer einzuziehen steht in keinem Verhaeltnis zum Nutzen.

Stattdessen von Hand pruefen: einen Regler ziehen, die App ueber den Aufgabenschalter beenden,
neu starten. Steht der zuletzt losgelassene Wert da, greift die Trennung.

## Abnahme

- `./gradlew test` laeuft gruen durch, einschliesslich der neuen `AutoFitTest`.
- Ein zu langer Text, ein Druck auf „Einpassen": die Ueberlauf-Warnung und etwaige rote
  Markierungen verschwinden, die Vorschau fuellt den Rahmen.
- Die vier Regler veraendern die Vorschau sichtbar und fluessig.
- Werte ueberleben einen Neustart der App.
- Ein Probeblatt an der echten Maschine bestaetigt, dass die eingepasste Groesse auf dem Papier
  im Rahmen bleibt.
