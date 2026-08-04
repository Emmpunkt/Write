package de.emmpunkt.write.core.layout

import de.emmpunkt.write.core.font.StrokeFont
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ergebnis der Groessensuche.
 *
 * @param sizeMm die gefundene Versalhoehe; bei [fits] = false die gepruefte Untergrenze (auf dem
 *   Raster [stepMm], kann also minimal von der uebergebenen Untergrenze abweichen).
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
 * nennte die App eine Groesse, die sich von Hand gar nicht mehr einstellen laesst.
 *
 * Die Breitenmessung (siehe Metrics.widthOf in TextLayout.kt) rechnet die Scherung durch
 * [TextStyle.slantDeg] nicht mit ein - die entsteht erst beim Zeichnen. Bei starker Neigung
 * kann der Kopf des letzten Buchstabens einer Zeile deshalb ueber den Rahmen hinausragen, obwohl
 * fitSize() Erfolg meldet.
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

    fun groesse(stufe: Int): Float = (stufe.toDouble() * stepMm.toDouble()).toFloat()

    val unterste = ceil(minMm / stepMm - RASTER_TOLERANZ).toInt()
    val oberste = floor(maxMm / stepMm + RASTER_TOLERANZ).toInt()
    require(unterste <= oberste) {
        "Intervall [$minMm, $maxMm] mm ist schmaler als eine Rasterstufe von $stepMm mm - " +
            "keine Stufe liegt darin"
    }

    if (text.isBlank()) return FitResult(maxMm, fits = true)

    fun passt(stufe: Int): Boolean {
        val laid = layoutText(text, style.copy(sizeMm = groesse(stufe)), frame, font)
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    if (passt(oberste)) return FitResult(groesse(oberste), fits = true)
    if (!passt(unterste)) return FitResult(groesse(unterste), fits = false)

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

/**
 * Skaliert alle [stile] so, dass der erste - der Leitstil - [leitgroesseMm] misst.
 *
 * Die uebrigen wandern proportional mit: Was doppelt so gross war, bleibt doppelt so gross.
 * Gerastert wird auf [stepMm], damit sich jede entstandene Groesse hinterher auch von Hand
 * wieder einstellen laesst; der Leitstil bekommt sein Mass unveraendert, weil die Suche es
 * bereits auf dem Raster gefunden hat.
 */
fun skaliert(
    stile: List<TextStyle>,
    leitgroesseMm: Float,
    stepMm: Float = 0.1f,
): List<TextStyle> {
    require(stile.isNotEmpty()) { "Es braucht mindestens einen Stil" }
    val faktor = leitgroesseMm / stile.first().sizeMm
    return stile.mapIndexed { index, stil ->
        if (index == 0) {
            stil.copy(sizeMm = leitgroesseMm)
        } else {
            stil.copy(sizeMm = aufRaster(stil.sizeMm * faktor, stepMm))
        }
    }
}

/**
 * Sucht den groessten gemeinsamen Massstab, bei dem [text] mit allen [stile]n in den [frame]
 * passt.
 *
 * Gesucht wird ueber die Groesse des Leitstils auf demselben 0,1-mm-Raster wie in [fitSize];
 * die uebrigen Stile skalieren mit. Bei nur einem Stil ist das Ergebnis deshalb bitgenau das
 * von [fitSize].
 *
 * Der Suchbereich ist enger als [minMm] .. [maxMm]: Er ist so gewaehlt, dass **jeder** Stil im
 * Bereich des Reglers bleibt. Sonst lieferte das Einpassen bei einer doppelt so grossen
 * Ueberschrift eine Groesse, die der Regler gar nicht mehr darstellen kann - genau der Fehler,
 * der den Reglerknopf schon einmal am Anschlag kleben liess.
 *
 * Liegen die Stile so weit auseinander, dass kein Faktor alle unterbringt, ist das Ergebnis
 * [FitResult.fits] = false. Die App darf dann nichts setzen, sondern muss es sagen.
 */
fun fitSkalierung(
    text: String,
    stile: List<TextStyle>,
    zuordnung: List<Int>,
    schrift: (String) -> StrokeFont,
    frame: Frame,
    minMm: Float = 2f,
    maxMm: Float = 25f,
    stepMm: Float = 0.1f,
): FitResult {
    require(stile.isNotEmpty()) { "Es braucht mindestens einen Stil" }
    require(minMm > 0f) { "Mindestgroesse muss positiv sein" }
    require(maxMm >= minMm) { "Obergrenze liegt unter der Untergrenze" }
    require(stepMm > 0f) { "Schrittweite muss positiv sein" }

    fun groesse(stufe: Int): Float = (stufe.toDouble() * stepMm.toDouble()).toFloat()

    val leit = stile.first().sizeMm
    val kleinster = stile.minOf { it.sizeMm }
    val groesster = stile.maxOf { it.sizeMm }

    val unterste = ceil(leit * (minMm / kleinster) / stepMm - RASTER_TOLERANZ).toInt()
    val oberste = floor(leit * (maxMm / groesster) / stepMm + RASTER_TOLERANZ).toInt()
    if (unterste > oberste) return FitResult(groesse(unterste), fits = false)

    if (text.isBlank()) return FitResult(groesse(oberste), fits = true)

    fun passt(stufe: Int): Boolean {
        val laid = layoutAbsaetze(
            absaetzeAus(text, skaliert(stile, groesse(stufe), stepMm), zuordnung, schrift),
            frame,
        )
        return !laid.overflow && laid.overlongWords.isEmpty()
    }

    if (passt(oberste)) return FitResult(groesse(oberste), fits = true)
    if (!passt(unterste)) return FitResult(groesse(unterste), fits = false)

    // Dieselbe Invariante wie in fitSize: [unten] passt geprueft, [oben] passt geprueft nicht.
    var unten = unterste
    var oben = oberste
    while (oben - unten > 1) {
        val mitte = unten + (oben - unten) / 2
        if (passt(mitte)) unten = mitte else oben = mitte
    }
    return FitResult(groesse(unten), fits = true)
}

/** Rundet auf ein Vielfaches von [stepMm], aber nie auf null - das waere keine Schrift mehr. */
private fun aufRaster(mm: Float, stepMm: Float): Float =
    maxOf(stepMm, (Math.round(mm / stepMm).toDouble() * stepMm.toDouble()).toFloat())

/** Fuer den Fall, dass minMm/stepMm rechnerisch knapp neben einer ganzen Stufe landet. */
private const val RASTER_TOLERANZ = 1e-4
