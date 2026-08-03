package de.emmpunkt.write.data

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
 */
private val PLATZHALTER = Regex("""\{([\p{L}\p{N}_-]+)}""")

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
