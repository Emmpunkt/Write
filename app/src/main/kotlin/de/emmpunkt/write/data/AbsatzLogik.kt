package de.emmpunkt.write.data

/**
 * Fuehrt die Zuordnung Absatz -> Stil nach, wenn sich der Text geaendert hat.
 *
 * Ohne diese Nachfuehrung zerbricht jede Zuordnung ueber Indizes beim ersten eingefuegten
 * Absatz: alles dahinter rutscht eine Stelle und bekommt das Schriftbild seines Nachbarn.
 *
 * Verglichen wird, was vorn und hinten gleich geblieben ist; ersetzt wird nur der Bereich
 * dazwischen. Absaetze, die dort neu entstanden sind, erben den Stil des Absatzes davor - das
 * trifft den haeufigsten Fall (Eingabetaste mitten im Text) richtig, ohne dass irgendwo eine
 * Absatzkennung mitgefuehrt werden muesste.
 *
 * Eine zu kurze [zuordnung] wird mit dem ersten Stil aufgefuellt; so laesst sich eine Notiz aus
 * der Zeit vor den Stilen ohne Sonderweg oeffnen.
 */
fun zuordnungNachTextaenderung(
    alt: List<String>,
    neu: List<String>,
    zuordnung: List<Int>,
): List<Int> {
    fun stilVon(index: Int): Int = zuordnung.getOrElse(index) { 0 }

    val gemeinsam = minOf(alt.size, neu.size)

    var praefix = 0
    while (praefix < gemeinsam && alt[praefix] == neu[praefix]) praefix++

    var suffix = 0
    while (suffix < gemeinsam - praefix &&
        alt[alt.size - 1 - suffix] == neu[neu.size - 1 - suffix]
    ) {
        suffix++
    }

    val ergebnis = ArrayList<Int>(neu.size)

    // Vorn unveraendert: Stile bleiben, wo sie sind.
    for (i in 0 until praefix) ergebnis += stilVon(i)

    // Die Mitte wurde ersetzt. Wo noch ein alter Absatz an derselben Stelle stand, gilt dessen
    // Stil weiter; alles Weitere ist neu und erbt vom Absatz darueber.
    val alteMitte = alt.size - suffix - praefix
    for (i in 0 until (neu.size - suffix - praefix)) {
        ergebnis += if (i < alteMitte) {
            stilVon(praefix + i)
        } else {
            ergebnis.lastOrNull() ?: 0
        }
    }

    // Hinten unveraendert: die Stile der letzten Absaetze wandern mit.
    for (i in alt.size - suffix until alt.size) ergebnis += stilVon(i)

    return ergebnis
}

/** Der Absatz, in dem [cursor] steht - der Index in `text.split('\n')`. */
fun absatzAmCursor(text: String, cursor: Int): Int =
    text.take(cursor.coerceIn(0, text.length)).count { it == '\n' }
