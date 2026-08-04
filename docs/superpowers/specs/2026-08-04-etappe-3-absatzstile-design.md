# Etappe 3 Teil 4: benannte Absatzstile

Stand 2026-08-04. Der letzte offene Punkt der Etappe 3.

## Wozu

Eine Grußkarte braucht eine große zentrierte Überschrift über kleinem linksbündigem Text.
Heute gilt ein Schriftbild für die ganze Notiz; wer beides will, muss zweimal plotten und das
Blatt dazwischen ausrichten.

## Was entschieden wurde

Die Frage, die zweimal in Fehlerberichten unterging, ist beantwortet:

- **Absatzweise**, nicht beliebige markierte Bereiche. Damit entfallen Textauswahl,
  Formatspuren im Editor und ein zeichengenaues Speicherformat.
- **Benannte Stile** („Überschrift", „Fließtext"), die man Absätzen zuweist — nicht ein Stil,
  der unsichtbar am Absatz klebt. Wer drei Absätze gleich haben will, weist dreimal denselben
  Namen zu und ändert ihn später an einer Stelle.
- Je Stil abweichen: **Schrift, Größe, Ausrichtung**. Feintuning (Laufweite, Wortabstand,
  Zeilenabstand, Neigung) bleibt dokumentweit — sonst trüge jeder Absatz sieben Werte statt
  drei, und der Zeilenabstand bräuchte zusätzlich eine eigene Regel für den Übergang.
- Die Stilliste **gehört der Notiz**, nicht der App. Eine neue Notiz erbt sie von der zuletzt
  geöffneten, wie heute schon das Schriftbild (`neueNotiz` in `NoteLogik.kt`). Gehörten die
  Stile der App, sähe eine alte Notiz beim nächsten Öffnen anders aus als beim Plotten davor,
  und ein gelöschter Stil ließe Absätze verwaisen.
- Zugewiesen wird **über den Cursor**: Cursor in den Absatz, dann in einer Chip-Reihe den Stil
  antippen. Keine zweite Bedienfläche auf einem ohnehin vollen Bildschirm.

## Ein Ort je Information

`fontId`, `sizeMm` und `align` stehen heute direkt in `AppSettings`, `NoteEntity` und
`TemplateEntity`. Neben einer Stilliste wären das zwei Orte für dieselbe Sache. Deshalb
**ersetzen** die Stile diese drei Felder: Stil 1 der Liste ist der Grundstil und tritt an ihre
Stelle. Die alten Werte wandern beim ersten Start hinein — im DataStore über den fehlenden
Schlüssel, in Room über eine Migration.

Das ist der teure Teil dieser Etappe. Er ist es wert: Das Projekt hat sich schon zweimal an
Feldern verletzt, die zwei Bedeutungen trugen.

## Speicherformat

Keine neue Abhängigkeit — kein JSON-Lib im Projekt. Zwei Textfelder im Stil der vorhandenen
Werteliste:

| Feld | Inhalt | Beispiel |
|---|---|---|
| `stile` | eine Zeile je Stil, Felder durch `\|` | `Überschrift\|allure\|12.0\|CENTER` |
| `absatzZuordnung` | Stil-Index je Absatz, durch Komma | `0,1,1,2` |

`|` wird beim Speichern aus dem Namen entfernt. Fehlende oder unbekannte Zuordnungseinträge
fallen auf Stil 0 zurück, statt die Notiz unlesbar zu machen.

## Bausteine

**core, Satz mit gemischten Stilen.** `layoutAbsaetze(absaetze, frame)` unter dem vorhandenen
`layoutText`, das als dünne Hülle bestehen bleibt. Zwei neue Regeln:

- Zeilenvorschub am Absatzwechsel: `max(Vorschub der vorigen Zeile, der neuen)`. Der eine Wert
  allein reicht in keiner Richtung — nach einer großen Überschrift säße kleiner Text in deren
  Unterlängen, vor einer Überschrift wäre zu wenig Platz.
- `requiredHeightMm` wird zur Summe aus Oberlänge der ersten Zeile, allen Vorschüben und der
  Unterlänge der letzten. Die Formel `(n-1) * lineAdvance` gilt nur bei einheitlicher Größe.

**core, Einpassen.** `fitSkalierung` sucht weiter die Größe von Stil 1 auf dem 0,1-mm-Raster;
alle übrigen Stile skalieren proportional mit. Die Verhältnisse bleiben erhalten, und bei nur
einem Stil ist das Ergebnis identisch zu heute.

**app, reine Funktionen.** `Stilformat.kt` liest und schreibt die beiden Textfelder.
`zuordnungNachTextaenderung(alt, neu, zuordnung)` führt die Zuordnung beim Tippen nach — ohne
sie verrutscht alles hinter einem eingefügten Absatz. Sie vergleicht gemeinsames Präfix und
Suffix und ersetzt nur den Bereich dazwischen; neue Absätze erben den Stil des Absatzes davor.
Beides ohne Gerät und ohne Netz prüfbar.

**app, Einstellungen und Datenbank.** `AppSettings.stile` (nie leer), `toTextStyle(stilIndex)`.
Room 4 → 5 mit Tabellenneubau nach dem Vorbild von `MIGRATION_3_4`; die `CREATE TABLE`-Anweisung
wörtlich aus dem erzeugten `NoteDatabase_Impl.kt`.

**app, Oberfläche.** Das Textfeld wechselt auf `TextFieldValue`, damit die Cursorposition
bekannt ist. Über der Stilleiste eine Chip-Reihe; die Regler darunter bearbeiten den gewählten
**Stil**, nicht den Absatz. Stil 1 ist nicht löschbar; beim Löschen fallen betroffene Absätze
auf Stil 1 zurück.

**Serie.** `pruefeBogen` rechnet über `layoutAbsaetze`, damit es bei einer Antwort auf „passt
das?" bleibt.

## Verifikation

Tests ohne Gerät für Vorschubregel, Höhenrechnung, `fitSkalierung` gegen `fitSize`,
Serialisierung mit kaputter Eingabe und die Zuordnungsnachführung. Migration trocken gegen das
erzeugte `NoteDatabase_Impl.kt` prüfen. Am Gerät zuerst nachsehen, ob vorhandene Notizen und
Vorlagen unverändert aussehen. An der Maschine ein Bogen mit Überschrift über Fließtext,
Abstand nachmessen.

## Danach

Etappe 4 „Gestalten": Text in 90°-Schritten drehen (A6 hoch passt nicht auf den Tisch, also
quer legen und drehen) und gezeichnete Zierrahmen um den Textrahmen.
