# Etappe 3, Teil 2: Notizliste

Stand 2026-08-03. Baut auf Teil 1 auf (SD-Upload, Branch `maschinenwerte-und-sd-upload`).

## Anlass

Die App speichert genau **einen** Text: `lastText` im DataStore, ueberschrieben bei jeder
Aenderung. Wie duenn das ist, hat sich beim Pruefen der Oberflaeche am 2026-08-03 gezeigt -
eine einzige Texteingabe zu Testzwecken hat den gespeicherten Text des Nutzers geloescht, ohne
Rueckfrage und ohne Weg zurueck.

Dazu kommt der eigentliche Zweck: Wer eine Einkaufsliste, eine Grusskarte und eine Beschriftung
schreibt, will die nicht jedes Mal neu tippen und neu einstellen.

## Umfang

1. Mehrere Notizen, dauerhaft gespeichert (Room).
2. **Die Schriftbild-Einstellungen gehoeren zur Notiz**, nicht zur App.
3. Liste zum Umschalten, Anlegen und Loeschen.
4. Migration des vorhandenen `lastText` zur ersten Notiz.

Ausdruecklich **nicht** in diesem Schnitt: Vorlagen mit Platzhaltern, gemischte Stile je
Absatz, Suche, Ordner, Sortieroptionen, Papierkorb, Export.

## Was zur Notiz gehoert und was nicht

Die Trennlinie ist dieselbe wie bei den Maschinenwerten: *was ist Gestaltung* gegen *wie steht
das Geraet*.

Die Notiz traegt **Text und Schriftbild**, sonst nichts. Alles, was mit dem Blatt und der
Maschine zu tun hat, bleibt global.

| Pro Notiz (Datenbank) | Global (bleibt DataStore) |
|---|---|
| `text` | `host`, `telnetPort` |
| `fontId`, `sizeMm`, `align` | `feedDrawMmMin`, `feedTravelMmMin`, `feedZMmMin` |
| `lineSpacing`, `letterSpacing` | `zUpMm`, `zDownMm` |
| `wordSpacing`, `slantDeg` | `paperWidthMm`, `paperHeightMm`, `marginMm` |
| | `paperOffsetXMm`, `paperOffsetYMm`, `naturalWriteOrder` |

**Das Blattformat bleibt global** - Entscheidung des Nutzers am 2026-08-03. Begruendung: Er
legt das Papier ein, und beim Umschalten auf eine andere Notiz soll nicht plotzlich ein anderes
Format eingestellt sein als das, was auf dem Tisch liegt. Der **Rand** folgt derselben Logik
und steht ohnehin schon heute im Einstellungen-Reiter, also dort, wo die globalen Werte
stehen. Der **Papier-Offset** ebenso. (Berichtigt am 2026-08-04: Bei einer VORLAGE gehoert er doch dazu - siehe den Nachtrag am Ende dieser Datei.)

Folge fuer die Bedienung: Das Blatt-Auswahlfeld sitzt weiterhin im Editor (kurzer Weg), wirkt
aber auf alle Notizen. Vom Nutzer bestaetigt - der Editor ist das erste Fenster, dort wird
eingestellt und danach gewechselt; das Format bleibt im Alltag ohnehin meist dasselbe.

**Ausblick auf Teil 3 (Vorlagen), hier bewusst noch nicht gebaut:** Genau bei einer Grusskarte
waere ein abweichendes Format erwuenscht. Das ist der Unterschied zwischen einer Notiz und
einer Vorlage - eine Vorlage darf ein Format mitbringen, eine gewoehnliche Notiz nicht. Wenn
Teil 3 ansteht, ist das der Ort dafuer, nicht das Notizmodell.

Die Maschinenwerte (Verfahrweg, Beschleunigungen, Vorschubgrenzen) bleiben, wo sie seit dem
2026-08-03 sind: sie werden beim Verbinden ausgelesen und gehoeren weder der Notiz noch den
Einstellungen.

## Datenmodell

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val updatedAt: Long,          // Sortierung: zuletzt bearbeitet zuerst
    // Schriftbild - und nur das. Blatt, Raender und Offset bleiben global.
    val fontId: String,
    val sizeMm: Float,
    val align: String,            // Enum als Name, damit die Datenbank lesbar bleibt
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,
)
```

Kein gespeichertes Titelfeld: **der Titel ist die erste nicht-leere Zeile**, gekuerzt. Ein
eigenes Feld waere ein zweiter Ort fuer dieselbe Information und muesste beim Tippen
nachgefuehrt werden.

`createdAt` fehlt bewusst - es wird nirgends angezeigt und nirgends sortiert. YAGNI.

## Aufbau

Room ist Android-gebunden, die Entity liegt deshalb im `app`-Modul. **Die Logik liegt nicht in
der Datenbankschicht** - sonst braeuchte es Robolectric oder ein Geraet, um sie zu pruefen, und
dieses Projekt testet alles auf dem PC.

Reine Funktionen, ohne Android-Bezug und damit direkt testbar:

| Funktion | Aufgabe |
|---|---|
| `titelVon(text: String): String` | erste nicht-leere Zeile, gekuerzt; Rueckfall bei leerem Text |
| `NoteEntity.toSettings(global: AppSettings): AppSettings` | Notiz + globale Werte -> vollstaendige Einstellungen fuer Layout und Vorschau |
| `AppSettings.toNote(id, text, jetzt): NoteEntity` | umgekehrter Weg beim Speichern |
| `neueNotizAus(vorlage: NoteEntity?, vorgabe: AppSettings): NoteEntity` | eine neue Notiz erbt das Schriftbild der zuletzt geoeffneten |
| `migriere(lastText, settings, jetzt): NoteEntity?` | einmalig beim ersten Start; `null`, wenn nichts zu retten ist |

Das DAO ist ein Interface. Das Repository wird gegen ein Fake-DAO getestet (In-Memory-Liste),
so wie `FakeFluidNc` die Maschine ersetzt.

```
NoteDao (Interface)  <-- Room-Implementierung (Geraet)
                     <-- FakeNoteDao (Test)
NoteRepository       -- Flow<List<NoteEntity>>, laden, speichern, loeschen
PlotterViewModel     -- kennt die aktuelle Notiz-ID
```

## Speichern

Wie bisher **automatisch und verzoegert**, kein Speichern-Knopf. Der bestehende Mechanismus
(`updateSettingsLive` / `commitSettings`) bleibt: waehrend eines Reglerzugs geht der Wert nur in
den Zustand, geschrieben wird beim Loslassen. Sonst schriebe ein einziger Zug dutzende Male auf
die Datenbank.

Fuer den Text kommt eine Verzoegerung dazu (etwa 500 ms nach dem letzten Tastendruck), damit
nicht jeder Buchstabe eine Transaktion ausloest.

## Migration

Eine einzige Regel, ohne Sonderfaelle: **Ist die Tabelle leer, entsteht genau eine Notiz aus
`lastText` und den aktuellen Stilwerten.** War `lastText` leer, ist die Notiz eben leer - das
ist kein anderer Fall, sondern derselbe mit leerem Text. Der Editor hat damit immer eine Notiz.

`lastText` wird danach nicht mehr gelesen. Der Schluessel bleibt vorerst im DataStore stehen -
ein Loeschen braechte nichts und naehme die Moeglichkeit, bei einem Fehler nachzusehen.

Die Stilwerte in `AppSettings` bleiben als **Vorgabe fuer neue Notizen** erhalten, wenn keine
vorherige existiert. Sie werden nicht mehr fuer die Anzeige benutzt.

## Bedienung

Ueber dem Notizfeld eine Zeile mit zwei Bedienelementen:

```
[ Notizen ▾ ]                                   [ + Neu ]
┌─────────────────────────────────────────────────────┐
│ Notiz                                               │
```

- **„Notizen"** klappt die Liste auf: Titel (erste Zeile), Datum der letzten Aenderung.
  Antippen laedt die Notiz und schliesst die Liste.
- **„Neu"** legt eine leere Notiz an, die das Schriftbild der aktuellen erbt.
- **Loeschen** ueber ein Muelleimer-Symbol am Listeneintrag, mit Rueckfrage. Bewusst KEIN
  Wischen: in einer Liste, die man zum Umschalten antippt, sitzt die Wischgeste zu nah an der
  Auswahl - und ein versehentlich geloeschter Text ist ohne Papierkorb weg. Die letzte Notiz
  laesst sich nicht loeschen; sie wird stattdessen geleert.

Beim Start oeffnet sich die zuletzt bearbeitete Notiz. Der Weg „tippen -> plotten" bleibt damit
genau so kurz wie heute; die Liste ist nur da, wenn man sie braucht.

Der aktuell laufende Auftrag ist davon unberuehrt: Waehrend `machine.busy` bleiben Umschalten,
Anlegen und Loeschen gesperrt. Sonst zeigte die Vorschau etwas anderes, als die Maschine gerade
faehrt.

## Zu pruefen (Tests, alle ohne Geraet)

1. **Titelableitung**: erste nicht-leere Zeile; leerer Text; Text nur aus Leerzeilen; sehr
   langer Titel wird gekuerzt; Umlaute bleiben heil.
2. **Hin und zurueck**: `toNote` -> `toSettings` liefert dieselben Stilwerte. Fangen die
   globalen Werte dabei keinen Schaden.
3. **Erben**: eine neue Notiz uebernimmt das Schriftbild der Vorlage, aber nicht ihren Text.
4. **Migration**: aus vorhandenem `lastText` entsteht genau eine Notiz mit den damaligen
   Stilwerten; bei leerer Datenbank ohne `lastText` entsteht eine leere Notiz; ein zweiter
   Start legt nichts Zusaetzliches an.
5. **Repository gegen Fake-DAO**: speichern, laden, loeschen, Sortierung nach `updatedAt`.
6. **Die Vorschau folgt der geladenen Notiz**: nach dem Umschalten stimmen Schrift, Groesse und
   Laufweite mit der Notiz ueberein, nicht mit der vorherigen.
   **Gegenprobe im selben Fall:** Blattformat, Rand und Papier-Offset bleiben dabei
   unveraendert - sie gehoeren nicht der Notiz. Ein Umschalten darf das eingelegte Papier
   nicht "aendern".
7. **Die letzte Notiz laesst sich nicht loeschen**, sondern wird geleert - der Editor darf nie
   ohne Notiz dastehen.

## Risiken

- **Room bringt eine neue Abhaengigkeit** (`androidx.room`, plus KSP fuer den Compiler). Das
  ist der Preis fuer dauerhafte Speicherung; DataStore mit einer serialisierten Liste waere
  billiger, skaliert aber schlecht und macht Teilaktualisierungen umstaendlich.
- **Der Umbau fasst `AppSettings` an**, und damit die Stelle, an der Layout, Vorschau und
  Grenzpruefung haengen. Sieben Felder wandern aus dem globalen Zustand in die Notiz; jeder
  Aufrufer muss mitgezogen werden. Die vorhandenen Tests der Layout-Kette decken das ab.

  Kleiner geworden ist das Risiko dadurch, dass Blatt, Rand und Offset global bleiben:
  `toFrame()` und die Grenzpruefung bleiben unberuehrt, betroffen ist nur `toTextStyle()`.
- **Datenverlust bei fehlerhafter Migration.** Deshalb wird `lastText` nicht geloescht, sondern
  nur nicht mehr gelesen.
