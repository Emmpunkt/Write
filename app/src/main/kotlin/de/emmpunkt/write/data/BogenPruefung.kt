package de.emmpunkt.write.data

import de.emmpunkt.write.core.font.StrokeFont
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.absaetzeAus
import de.emmpunkt.write.core.layout.layoutAbsaetze

/** Was bei einem einzelnen Bogen herauskommt. */
data class BogenBefund(
    /** 0-basiert; angezeigt wird `index + 1`. */
    val index: Int,
    /** Kurzform der Werte, damit die Meldung die Karte benennt. */
    val bezeichnung: String,
    /** Text hoeher als der nutzbare Bereich. */
    val ueberlauf: Boolean,
    /** Woerter, die mitten im Wort umbrochen werden mussten. */
    val hartGetrennt: Set<String>,
) {
    /**
     * Beides sperrt den Start.
     *
     * Im Editor ist eine harte Trennung nur eine Warnung - dort entscheidet der Nutzer bei
     * jedem Text selbst. Bei einem Satz Platzkarten faellt ein mitten durchgeschnittener
     * Nachname sofort auf, und niemand sieht ihn vor dem Plotten.
     */
    val inOrdnung: Boolean get() = !ueberlauf && hartGetrennt.isEmpty()
}

/**
 * Rechnet jeden Bogen durch, bevor die Maschine laeuft.
 *
 * Bewusst mit dem VORHANDENEN [layoutAbsaetze] - derselben Funktion, aus der auch Vorschau und
 * G-Code entstehen. Ein zweiter Weg, "passt das?" zu beantworten, koennte von dem abweichen,
 * was der Stift spaeter faehrt.
 *
 * [zeilen] darf nur fehlerfreie Zeilen enthalten; kaputte hat `werteZeilen` schon gemeldet.
 */
fun pruefeBogen(
    zeilen: List<WerteZeile>,
    vorlage: String,
    stile: List<TextStyle>,
    zuordnung: List<Int>,
    frame: Frame,
    schrift: (String) -> StrokeFont,
): List<BogenBefund> = zeilen.mapIndexed { index, zeile ->
    val text = einsetzen(vorlage, zeile.felder)
    val laid = layoutAbsaetze(absaetzeAus(text, stile, zuordnung, schrift), frame)
    BogenBefund(
        index = index,
        bezeichnung = zeile.bezeichnung,
        ueberlauf = laid.overflow,
        hartGetrennt = laid.overlongWords,
    )
}

/**
 * Prueft, ob aus den Einstellungen ueberhaupt ein Rahmen entstehen kann - und ob er aufs Blatt
 * passt.
 *
 * `Frame` wirft im Konstruktor bei einer Breite von 0 oder weniger. Beim Tippen einer Zahl
 * fuehrte das zum Absturz statt zu einer Meldung, deshalb der Fang.
 *
 * Der Ueberstand ueber das Blatt sperrt den Serienstart bewusst: Bei einem Satz Platzkarten
 * merkt es sonst niemand vor dem Plotten, und der Stift schreibt neben die Karte auf den Tisch.
 */
fun rahmenFehler(s: AppSettings): String? {
    runCatching { s.toFrame() }.exceptionOrNull()?.let {
        return "Der Textrahmen ergibt keine Fläche: ${it.message}"
    }
    if (!s.rahmenPasstAufsBlatt) {
        return "Der Textrahmen ragt über das Blatt hinaus " +
            "(${masz(s.paperWidthMm)} × ${masz(s.paperHeightMm)} mm). " +
            "Blatt unter Optionen vergrößern oder den Rahmen verschieben."
    }
    return null
}

/** Ganze Millimeter ohne Nachkomma - "50" liest sich besser als "50,0". */
private fun masz(mm: Float): String =
    if (mm == mm.toInt().toFloat()) mm.toInt().toString()
    else String.format(java.util.Locale.GERMANY, "%.1f", mm)
