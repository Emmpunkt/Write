package de.emmpunkt.write.data

import de.emmpunkt.write.core.decor.RahmenForm
import de.emmpunkt.write.core.decor.Zipfelseite
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.Drehung

/**
 * Trennzeichen der Werteliste.
 *
 * Das Komma schied aus, weil es in Namen vorkommt ("Schmidt, Anna"); der Tabulator laesst sich
 * auf einer Telefontastatur nicht tippen. Ein Wert, der selbst ein Semikolon enthaelt, ist
 * damit nicht darstellbar - bei Anreden und Namen faellt das nicht ins Gewicht.
 */
const val FELD_TRENNER = ';'

/**
 * Platzhalter stehen in geschweiften Klammern: `{name}`.
 *
 * Benannt und nicht durchnummeriert: Wer die Vorlage nach Monaten wieder oeffnet, liest
 * `{anrede} {name}` und weiss sofort, welche Spalte was ist. Bei `{1} {2}` muesste er die
 * Werteliste danebenlegen und abzaehlen.
 *
 * BEIDE geschweiften Klammern sind maskiert, und der Bindestrich ebenfalls. Java sieht das
 * lockerer, Android benutzt aber ICU: Dort warf `}` unmaskiert eine PatternSyntaxException,
 * und zwar erst beim ersten Zugriff auf diese Datei - die Tests auf dem PC blieben gruen,
 * die App stuerzte am Geraet ab. Regex-Feinheiten gehoeren deshalb ans Geraet, nicht nur in
 * den Unit-Test.
 */
private val PLATZHALTER = Regex("""\{([\p{L}\p{N}_\-]+)\}""")

/** Eine Zeile der Werteliste - ein Bogen. */
data class WerteZeile(
    /** 1-basiert und zugleich die Bogennummer, die der Serienlauf zaehlt. */
    val nummer: Int,
    val felder: Map<String, String>,
    /** null, wenn die Zeile brauchbar ist; sonst die Meldung fuer den Nutzer. */
    val fehler: String? = null,
) {
    /**
     * Kurzform fuer Meldungen ("Bogen 14 „Liebe Christiane"").
     *
     * NICHT der Text, der auf den Bogen kommt - der entsteht aus [einsetzen] und der Vorlage.
     */
    val bezeichnung: String get() = felder.values.filter { it.isNotEmpty() }.joinToString(" ")
}

/** Die Platzhalternamen, in Reihenfolge ihres ersten Auftretens, ohne Doppelte. */
fun platzhalterIn(text: String): List<String> =
    PLATZHALTER.findAll(text).map { it.groupValues[1] }.distinct().toList()

/** `null`, wenn die Vorlage brauchbar ist; sonst die Meldung. */
fun vorlagenFehler(text: String): String? =
    if (platzhalterIn(text).isEmpty()) {
        "Die Vorlage enthält keinen Platzhalter wie {name}."
    } else {
        null
    }

/**
 * Zerlegt die Werteliste: eine Zeile je Bogen, Felder durch [FELD_TRENNER] getrennt.
 *
 * Die Spalten ordnen sich [spalten] der Reihe nach zu - das sind die Platzhalter in der
 * Reihenfolge ihres ersten Auftretens im Vorlagentext.
 *
 * Zeilen mit falscher Feldzahl werden GEMELDET, nicht ergaenzt. Fehlende Felder stillschweigend
 * leer zu lassen erzeugte eine Karte mit einer Luecke, die erst auf dem Papier auffiele.
 */
fun werteZeilen(eingabe: String, spalten: List<String>): List<WerteZeile> {
    if (spalten.isEmpty()) return emptyList()

    return eingabe.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { index, zeile ->
            val felder = zeile.split(FELD_TRENNER).map { it.trim() }
            val nummer = index + 1
            if (felder.size != spalten.size) {
                val wort = if (felder.size == 1) "Feld" else "Felder"
                WerteZeile(
                    nummer = nummer,
                    felder = emptyMap(),
                    fehler = "Bogen $nummer hat ${felder.size} $wort, erwartet werden " +
                        "${spalten.size} (${spalten.joinToString(FELD_TRENNER.toString())}).",
                )
            } else {
                WerteZeile(nummer = nummer, felder = spalten.zip(felder).toMap())
            }
        }
        .toList()
}

/**
 * Ersetzt die Platzhalter durch ihre Werte.
 *
 * Ein unbekannter Platzhalter bleibt stehen - siehe [WerteZeile]. Der Ersatz wird woertlich
 * eingesetzt: `Regex.replace` mit Funktion deutet `$1` im Wert NICHT als Gruppenverweis,
 * anders als die Variante mit Zeichenkette.
 */
fun einsetzen(text: String, werte: Map<String, String>): String =
    PLATZHALTER.replace(text) { treffer -> werte[treffer.groupValues[1]] ?: treffer.value }

/** Beispieltext einer frisch angelegten Vorlage. */
private const val VORLAGE_BEISPIEL = "{anrede} {name},"

/**
 * Die Stile der Vorlage, garantiert nicht leer.
 *
 * Wie bei der Notiz: eine unlesbare Spalte fuehrt zum Grundstil, nicht zum Absturz beim
 * Oeffnen einer alten Vorlage.
 */
fun TemplateEntity.stilListe(): List<Absatzstil> =
    stileAusText(stile).ifEmpty { listOf(AppSettings.GRUNDSTIL) }

/** Die Absatzzuordnung der Vorlage. */
fun TemplateEntity.zuordnung(): List<Int> = zuordnungAusText(absatzZuordnung)

/**
 * Legt Schriftbild UND Textrahmen der Vorlage ueber die Einstellungen.
 *
 * Maschine, Verbindung UND BLATT bleiben unberuehrt - sie beschreiben das Geraet und das
 * Papier, das gerade aufliegt, nicht das Dokument. Der Textrahmen dagegen gehoert ganz der
 * Vorlage, samt Position: Sie beschreibt, wo auf dem Blatt der Text stehen soll.
 *
 * Bewusst als Ueberlagerung und nicht als Ersatz: So laesst sich eine offene Vorlage jederzeit
 * neu auf die AKTUELLEN globalen Werte legen, wenn sich Host oder Vorschub geaendert haben.
 */
fun AppSettings.mitVorlage(v: TemplateEntity): AppSettings = copy(
    stile = v.stilListe(),
    lineSpacing = v.lineSpacing,
    letterSpacing = v.letterSpacing,
    wordSpacing = v.wordSpacing,
    slantDeg = v.slantDeg,
    rahmenXMm = v.rahmenXMm,
    rahmenYMm = v.rahmenYMm,
    rahmenBreiteMm = v.rahmenBreiteMm,
    rahmenHoeheMm = v.rahmenHoeheMm,
    drehung = runCatching { Drehung.valueOf(v.drehung) }.getOrDefault(Drehung.GRAD_0),
    rahmenForm = runCatching { RahmenForm.valueOf(v.rahmenForm) }.getOrDefault(RahmenForm.KEINER),
    rahmenAbstandMm = v.rahmenAbstandMm,
    zipfel = runCatching { Zipfelseite.valueOf(v.zipfel) }.getOrDefault(Zipfelseite.UNTEN_LINKS),
)

/** Der umgekehrte Weg: aus dem Arbeitszustand wird wieder eine Vorlage zum Speichern. */
fun AppSettings.zuVorlage(
    id: Long,
    name: String,
    text: String,
    werte: String,
    jetzt: Long,
    zuordnung: List<Int> = emptyList(),
) = TemplateEntity(
    id = id,
    name = name,
    text = text,
    werte = werte,
    updatedAt = jetzt,
    stile = stileAlsText(stile),
    absatzZuordnung = zuordnungAlsText(zuordnung),
    lineSpacing = lineSpacing,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    slantDeg = slantDeg,
    rahmenXMm = rahmenXMm,
    rahmenYMm = rahmenYMm,
    rahmenBreiteMm = rahmenBreiteMm,
    rahmenHoeheMm = rahmenHoeheMm,
    drehung = drehung.name,
    rahmenForm = rahmenForm.name,
    rahmenAbstandMm = rahmenAbstandMm,
    zipfel = zipfel.name,
)

/**
 * Eine neue, leere Vorlage mit den aktuellen Einstellungen als Ausgangspunkt.
 *
 * Der Beispieltext ist Absicht: Mit leerem Feld begruesste die App den Nutzer sonst mit
 * "enthält keinen Platzhalter", ohne zu zeigen, wie einer aussieht.
 */
fun neueVorlage(vorgabe: AppSettings, jetzt: Long): TemplateEntity = vorgabe.zuVorlage(
    id = 0L,
    name = "Neue Vorlage",
    text = VORLAGE_BEISPIEL,
    werte = "",
    jetzt = jetzt,
)
