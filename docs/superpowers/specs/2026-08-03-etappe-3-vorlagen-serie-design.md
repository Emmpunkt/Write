# Vorlagen mit Platzhaltern und Serienlauf — Entwurf

**Datum:** 2026-08-03
**Etappe:** 3, Teil 3
**Steht davor:** Teil 1 (SD-Upload) und Teil 2 (Notizliste) sind gebaut und am Geraet geprueft.

## Ziel

Einen Satz gleichartiger Karten in einem Durchgang schreiben: eine Vorlage mit Platzhalter,
eine Liste von Werten, und die App plottet Bogen fuer Bogen mit Pause zum Blattwechsel.

Der Anwendungsfall sind Platzkarten und Einladungen — genau das, wofuer ein Stiftplotter
gebaut ist und wofuer Handschrift den Unterschied macht.

## Entscheidungen des Nutzers (2026-08-03)

| Frage | Entscheidung |
|---|---|
| Zweck | **Serie**: viele Karten am Stueck, nicht nur ein Textbaustein |
| Platzhalter | **Erstmal einer**, das Modell aber so schneiden, dass mehrere ohne Umbau folgen |
| Ablauf | **Ein Auftrag je Bogen**, die App wartet dazwischen auf Knopfdruck |
| Ort | **Eigener Reiter „Serie"** neben Notiz/Maschine/Einstellungen |
| Ueberlauf | **Vorher alle pruefen, Start sperren**, bis kein Bogen mehr uebersteht |
| Ablaufsteuerung | **Eigene Klasse** mit hineingereichter Plot-Funktion (Variante B) |

## Abgrenzung: was die Vorlage traegt und was nicht

Eine Vorlage traegt **Schriftbild UND Blattformat**. Das ist der Unterschied zur Notiz, und er
war bei Teil 2 schon angekuendigt: Eine Grusskarte bringt ihr Format mit, eine Notiz nicht.

Der **Papier-Offset bleibt global**. Er beschreibt, wo die Blattecke am Anschlag liegt, und das
aendert sich nicht dadurch, dass ein kleineres Blatt eingelegt wird. Waere er Teil der Vorlage,
wanderte eine Einrichtungsgroesse ins Dokument — genau der Fehler, den Teil 2 vermieden hat.

Die **Maschinenwerte** (Verfahrweg, Beschleunigungen, Vorschubgrenzen) bleiben, wo sie sind:
Sie werden beim Verbinden ausgelesen und gehoeren weder Notiz noch Vorlage.

## Datenmodell

```kotlin
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Name der Vorlage, z. B. "Platzkarten Hochzeit". */
    val name: String,
    /** Text mit Platzhalter in geschweiften Klammern: "Liebe {name}," */
    val text: String,
    /** Werte, eine Zeile je Bogen. Mitgespeichert, damit ein Satz wiederholbar ist. */
    val werte: String,
    val updatedAt: Long,

    // Schriftbild - wie NoteEntity
    val fontId: String,
    val sizeMm: Float,
    val align: String,
    val lineSpacing: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val slantDeg: Float,

    // Blattformat - der Unterschied zur Notiz
    val paperWidthMm: Float,
    val paperHeightMm: Float,
    val marginMm: Float,
)
```

`werte` steht als mehrzeiliger Text und nicht als eigene Tabelle. Eine Zeile ist ein Bogen; das
ist genau die Form, in der der Nutzer sie eintippt oder aus der Zwischenablage einfuegt. Eine
zweite Tabelle brauchte Fremdschluessel und Sortierung fuer eine Liste, die als Text schon
richtig sortiert ist.

**Platzhalter benannt, nicht positionell.** Auch bei nur einem erlaubten Platzhalter steht
`{name}` im Text und nicht `{}`. Kommen spaeter `{tisch}` und `{datum}` dazu, aendert sich am
gespeicherten Text nichts — nur die Werteliste wird von einer Spalte zu mehreren. Das
Datenbankschema bleibt unberuehrt.

## Reine Logik (`VorlagenLogik.kt`)

Ohne Android, damit alles auf dem PC pruefbar bleibt — dasselbe Muster wie `NoteLogik.kt`.

| Funktion | Aufgabe |
|---|---|
| `platzhalterIn(text: String): List<String>` | Namen der Platzhalter, in Reihenfolge, ohne Doppelte |
| `vorlagenFehler(text: String): String?` | `null`, wenn die Vorlage brauchbar ist; sonst die Meldung |
| `werteZeilen(eingabe: String): List<String>` | Zeilen trimmen, leere weglassen |
| `einsetzen(text: String, werte: Map<String, String>): String` | Platzhalter ersetzen |
| `bogenTexte(vorlage: String, werte: List<String>): List<String>` | ein fertiger Text je Bogen |
| `AppSettings.mitVorlage(v: TemplateEntity): AppSettings` | Schriftbild **und Blatt** ueberlagern |
| `AppSettings.zuVorlage(id, name, text, werte, jetzt): TemplateEntity` | der Rueckweg |

`vorlagenFehler` meldet zwei Faelle: kein Platzhalter („Die Vorlage enthält keinen Platzhalter
wie {name}.") und mehr als einer („Vorerst ist nur ein Platzhalter möglich, gefunden: {name},
{tisch}."). Die zweite Meldung nennt die gefundenen Namen — sonst sucht der Nutzer im Text.

Ein unbekannter Platzhalter in `einsetzen` bleibt stehen, statt zu leeren. Ein sichtbares
`{tisch}` auf dem Bogen ist ein Fehler, den man sieht; eine stillschweigende Luecke nicht.

## Ueberlaufpruefung

Jeder Bogen wird mit dem **vorhandenen `layoutText`** durchgerechnet — derselben Funktion, aus
der auch Vorschau und G-Code entstehen. Ein zweiter Weg, „passt das?" zu beantworten, koennte
von der Vorschau abweichen.

```kotlin
data class BogenBefund(
    val index: Int,          // 0-basiert intern, 1-basiert angezeigt
    val wert: String,
    val ueberlauf: Boolean,          // Text hoeher als das Blatt
    val hartGetrennt: Set<String>,   // Woerter, die mitten im Wort umbrochen wurden
) { val inOrdnung: Boolean get() = !ueberlauf && hartGetrennt.isEmpty() }

fun pruefeBogen(
    werte: List<String>,
    texte: List<String>,      // die fertig eingesetzten Bogentexte, gleich lang wie werte
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
): List<BogenBefund>
```

Beide Befunde sperren den Start. Ein hart getrenntes Wort ist bei einer Platzkarte genauso
unbrauchbar wie ein Ueberlauf — ein mitten durchgeschnittener Nachname faellt sofort auf.

**Ein Fallstrick:** `Frame` wirft im Konstruktor, wenn die Raender breiter sind als das Blatt.
Bei einer Vorlage mit 8 mm Rand auf einer 10-mm-Karte fuehrte das zu einem Absturz statt zu
einer Meldung. Die Pruefung faengt das ab und meldet es als Vorlagenfehler.

## Der Serienlauf (`Serienlauf.kt`)

Die Ablaufsteuerung, mit dem Plotten als hineingereichter Funktion:

```kotlin
class Serienlauf(
    private val bogen: List<String>,
    /** Erfolg heisst: der Auftrag lief bis `SendProgress.Completed` durch. */
    private val plotteBogen: suspend (index: Int, text: String) -> Result<Unit>,
    /** Fuer die Wiederaufnahme eines abgebrochenen Satzes. */
    startAb: Int = 0,
) {
    val zustand: StateFlow<SerienZustand>

    /** Plottet den naechsten Bogen und haelt danach an. */
    suspend fun naechsterBogen()
    /** Ueberspringt den aktuellen - nach einem Fehlschlag oder auf Wunsch. */
    fun ueberspringen()
    fun abbrechen()
}

sealed interface SerienZustand {
    data class Bereit(val naechster: Int, val gesamt: Int) : SerienZustand
    data class Laeuft(val index: Int, val gesamt: Int) : SerienZustand
    data class WartetAufBlatt(val fertig: Int, val gesamt: Int) : SerienZustand
    data class Fehlgeschlagen(val index: Int, val meldung: String) : SerienZustand
    data class Fertig(val geplottet: Int, val uebersprungen: Int) : SerienZustand
    data object Abgebrochen : SerienZustand
}
```

**Ein Fehlschlag rueckt den Zaehler nicht weiter.** „Nochmal" plottet denselben Bogen; erst
`ueberspringen()` geht weiter. Das ist der Unterschied zwischen „das Blatt ist verrutscht" und
„diesen Namen lasse ich weg" — und er entscheidet, ob ein Gast eine Karte bekommt.

Die Klasse kennt weder Telnet noch SD-Karte. Genau deshalb ist der ganze Ablauf gegen eine
Attrappe pruefbar: eine Funktion, die mitzaehlt und bei einem bestimmten Bogen scheitert.

Wiederaufnahme: `startAb = k` beginnt bei Bogen k. Ein abgebrochener Satz laesst sich damit
spaeter fortsetzen, ohne die ersten Karten noch einmal zu schreiben.

**Zwei Randfaelle, ausdruecklich festgelegt:**

- **Leere Werteliste** — kein Bogen, der Start bleibt gesperrt. Kein Sonderfall im Ablauf,
  sondern eine Bedingung der Oberflaeche, wie ein ueberstehender Bogen auch.
- **Doppelte Werte** — erlaubt. Zwei Karten fuer denselben Namen sind ein legitimer Wunsch, und
  eine Warnung waere bevormundend. Die Zeilen bleiben, wie sie eingetippt wurden.

## Anbindung an die Maschine

`plotteBogen` ist im ViewModel die vorhandene Auftragskette: Text setzen, `toPlotJob`, dann
`plot(...)` oder `plotViaSd(...)`. Beide Sendewege funktionieren unveraendert, weil die Serie
nichts umgeht — **dieselbe Vorpruefung, dieselbe Grenzpruefung, dieselbe Rueckfahrt** auf den
Arbeitsnullpunkt am Ende jedes Bogens.

Der Wachhalte-Mechanismus (`PlotWakeLock`) greift ueber den ganzen Satz, nicht nur je Bogen:
Zwischen zwei Karten liegt eine Wartezeit, in der das Telefon sonst einschliefe und die
Verbindung verlöre.

Waehrend eines laufenden Satzes ist das Umschalten von Notizen und Vorlagen gesperrt — wie
schon beim einzelnen Auftrag. Sonst zeigte die Vorschau etwas anderes, als die Maschine faehrt.

## Oberflaeche: Reiter „Serie"

Von oben nach unten:

1. **Vorlage** — Auswahlfeld, „+ Neu", Löschen mit Rueckfrage
2. **Name** und **Vorlagentext** mit Hinweis auf `{name}`
3. **Schriftbild und Blatt** — die Regler aus dem Editor, unveraendert
4. **Werteliste** — mehrzeiliges Feld, eine Zeile je Bogen, mit Zaehler „20 Bogen"
5. **Vorschau des ersten Bogens**
6. **Befund der Vorpruefung** — die Problemfaelle namentlich, oder „Alle 20 Bogen passen"
7. **Starten**, gesperrt solange ein Bogen uebersteht
8. **Waehrend des Laufs**: Zaehler, „Nächster Bogen", „Überspringen", „Abbrechen"

**Wiederverwendung statt Nachbau:** `StilLeiste` und `AuswahlFeld` sind heute privat in
`EditorScreen.kt`. Sie wandern in eine eigene Datei und werden von beiden Bildschirmen benutzt.
Die Vorlage wird dafuer — wie die Notiz bei Teil 2 — in einen `AppSettings`-Arbeitszustand
geladen. Dadurch bedient der Nutzer im Serie-Reiter dieselben Regler wie im Editor, und es
entsteht keine zweite, leicht abweichende Leiste, die spaeter auseinanderlaeuft.

Der Serie-Arbeitszustand ist **getrennt** vom Editor-Zustand. Eine Vorlage zu bearbeiten darf
die Notiz im Editor nicht veraendern.

`EditorScreen.kt` ist mit ueber 500 Zeilen ohnehin die groesste Datei der App; die Regler
herauszuloesen macht sie kleiner, nicht groesser.

## Was geprueft wird

**Ohne Geraet und ohne Netz:**

- Platzhalter finden, auch mehrere und doppelte
- `vorlagenFehler`: kein Platzhalter, mehr als einer, Rand breiter als das Blatt
- Werteliste: leere Zeilen, Rand-Leerzeichen, Umlaute
- `einsetzen`: unbekannter Platzhalter bleibt stehen
- Hin- und Rueckweg Vorlage ↔ `AppSettings`, **einschliesslich Blattformat**
- Ueberlaufpruefung: ein zu langer Wert wird gemeldet, ein passender nicht
- **Der ganze Serienablauf gegen eine Attrappe:** durchlaufen, Fehlschlag mit Wiederholung,
  Fehlschlag mit Ueberspringen, Abbruch mittendrin, Wiederaufnahme bei Bogen k, und dass der
  Zaehler nach einem Fehlschlag stehen bleibt

**Am Geraet:** ein echter Satz von drei Karten, mit einem absichtlich abgebrochenen Bogen
dazwischen.

## Bewusst nicht in diesem Schnitt

- **Mehrere Platzhalter** — das Modell traegt sie, die Oberflaeche noch nicht
- **CSV- oder Dateiimport** — die Zwischenablage deckt den Fall ab
- **Automatische Nummerierung oder Datum** — waeren eigene Platzhaltersorten
- **Papierkorb fuer Vorlagen** — wie bei den Notizen: Loeschen fragt nach, das genuegt
- **Gemischte Stile je Absatz** — das ist Teil 4
