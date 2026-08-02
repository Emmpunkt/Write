package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.StrokeFont
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ergebnis der Groessensuche.
 *
 * @param sizeMm die gefundene Versalhoehe; bei [fits] = false die unveraenderte Untergrenze.
 * @param fits ob ueberhaupt eine passende Groesse gefunden wurde. Bei false darf die App die
 *   Groesse NICHT setzen, sondern muss es melden - sonst schriebe sie in dem Glauben los,
 *   eingepasst zu haben.
 */
data class FitResult(val sizeMm: Float, val fits: Boolean)

/**
 * Sucht die groesste Schriftgroesse, bei der [text] sauber in den [frame] passt.
 *
 * "Sauber" heisst: kein Ueberlauf nach unten UND kein Wort, das mitten im Wort getrennt werden
 * muss. Harte Trennungen mahnt die App an anderer Stelle als Fehler an - eine Groesse zu
 * liefern, bei der die Warnung stehen bleibt, waere kein Einpassen.
 *
 * [style].sizeMm wird ersetzt; alle uebrigen Felder bleiben wirksam, damit die Suche mit
 * derselben Laufweite und demselben Zeilenabstand rechnet, die spaeter gefahren werden.
 *
 * Gesucht wird auf dem Raster [stepMm] - demselben, das der Regler in der App anbietet. Sonst
 * naennte die App eine Groesse, die sich von Hand gar nicht mehr einstellen laesst.
 */
fun fitSize(
    text: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
    minMm: Float = 2f,
    maxMm: Float = 25f,
    stepMm: Float = 0.1f,
): FitResult {
    require(minMm > 0f) { "Mindestgroesse muss positiv sein" }
    require(maxMm >= minMm) { "Obergrenze liegt unter der Untergrenze" }
    require(stepMm > 0f) { "Schrittweite muss positiv sein" }

    if (text.isBlank()) return FitResult(maxMm, fits = true)

    fun groesse(stufe: Int): Float = (stufe.toDouble() * stepMm.toDouble()).toFloat()

    fun passt(stufe: Int): Boolean {
        val laid = layoutText(text, style.copy(sizeMm = groesse(stufe)), frame, font)
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    val unterste = ceil(minMm / stepMm - RASTER_TOLERANZ).toInt()
    val oberste = floor(maxMm / stepMm + RASTER_TOLERANZ).toInt()

    if (passt(oberste)) return FitResult(maxMm, fits = true)
    if (!passt(unterste)) return FitResult(minMm, fits = false)

    // Invariante: [unten] passt geprueft, [oben] passt geprueft nicht. Sie traegt auch dann,
    // wenn "kleiner passt eher" einmal nicht gilt - der Zeilenumbruch ist eine Treppe, kein
    // stetiger Verlauf.
    var unten = unterste
    var oben = oberste
    while (oben - unten > 1) {
        val mitte = unten + (oben - unten) / 2
        if (passt(mitte)) unten = mitte else oben = mitte
    }

    // Am Ende ist oben = unten + 1. Dieselbe Invariante liefert damit beides, worauf es
    // ankommt: das Ergebnis passt, und die naechstgroessere Stufe passt nicht.
    //
    // Nicht garantiert ist, dass es jenseits von [oben] keine noch groessere passende Stufe
    // gibt - bei einer Treppenfunktion waere dafuer nur die vollstaendige Suche sicher, und
    // die kostet das Zwanzigfache fuer einen Fall, der in echten Notizen nicht vorkommt.
    return FitResult(groesse(unten), fits = true)
}

/** Fuer den Fall, dass minMm/stepMm rechnerisch knapp neben einer ganzen Stufe landet. */
private const val RASTER_TOLERANZ = 1e-4
