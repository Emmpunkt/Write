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
 * Sucht die groesste Groesse fuer EINEN Stil, bei der der ganze Text noch in den [frame] passt.
 *
 * Alle uebrigen Stile bleiben, wie sie sind - auch beim Pruefen. Das ist der Unterschied zum
 * ersten Entwurf, der alle Stile mit einem gemeinsamen Faktor skalierte: Dabei wuchs ein Stil
 * mit, der im Text gar nicht vorkam (vom Nutzer am Geraet gefunden, 2026-08-04), und der
 * groesste Stil begrenzte die Suche, auch wenn er unbenutzt war.
 *
 * Gesucht wird auf demselben Raster wie in [fitSize]; bei nur einem Stil ist das Ergebnis
 * deshalb bitgenau dasselbe.
 */
fun fitEinzelstil(
    text: String,
    stile: List<TextStyle>,
    zuordnung: List<Int>,
    stilIndex: Int,
    schrift: (String) -> StrokeFont,
    frame: Frame,
    drehung: Drehung = Drehung.GRAD_0,
    minMm: Float = 2f,
    maxMm: Float = 25f,
    stepMm: Float = 0.1f,
): FitResult {
    require(stile.isNotEmpty()) { "Es braucht mindestens einen Stil" }
    require(stilIndex in stile.indices) { "Diesen Stil gibt es nicht: $stilIndex" }
    require(minMm > 0f) { "Mindestgroesse muss positiv sein" }
    require(maxMm >= minMm) { "Obergrenze liegt unter der Untergrenze" }
    require(stepMm > 0f) { "Schrittweite muss positiv sein" }

    fun groesse(stufe: Int): Float = (stufe.toDouble() * stepMm.toDouble()).toFloat()

    val unterste = ceil(minMm / stepMm - RASTER_TOLERANZ).toInt()
    val oberste = floor(maxMm / stepMm + RASTER_TOLERANZ).toInt()
    if (unterste > oberste) return FitResult(groesse(unterste), fits = false)
    if (text.isBlank()) return FitResult(groesse(oberste), fits = true)

    fun passt(stufe: Int): Boolean {
        val probe = stile.mapIndexed { i, stil ->
            if (i == stilIndex) stil.copy(sizeMm = groesse(stufe)) else stil
        }
        val laid = layoutAbsaetze(absaetzeAus(text, probe, zuordnung, schrift), frame, drehung)
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

/** Fuer den Fall, dass minMm/stepMm rechnerisch knapp neben einer ganzen Stufe landet. */
private const val RASTER_TOLERANZ = 1e-4
