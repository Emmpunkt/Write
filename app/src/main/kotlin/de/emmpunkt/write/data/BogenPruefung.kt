package de.emmpunkt.write.data

import de.emmpunkt.write.core.font.StrokeFont
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.layoutText

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
 * Bewusst mit dem VORHANDENEN [layoutText] - derselben Funktion, aus der auch Vorschau und
 * G-Code entstehen. Ein zweiter Weg, "passt das?" zu beantworten, koennte von dem abweichen,
 * was der Stift spaeter faehrt.
 *
 * [zeilen] darf nur fehlerfreie Zeilen enthalten; kaputte hat `werteZeilen` schon gemeldet.
 */
fun pruefeBogen(
    zeilen: List<WerteZeile>,
    vorlage: String,
    style: TextStyle,
    frame: Frame,
    font: StrokeFont,
): List<BogenBefund> = zeilen.mapIndexed { index, zeile ->
    val laid = layoutText(einsetzen(vorlage, zeile.felder), style, frame, font)
    BogenBefund(
        index = index,
        bezeichnung = zeile.bezeichnung,
        ueberlauf = laid.overflow,
        hartGetrennt = laid.overlongWords,
    )
}

/**
 * Prueft, ob aus den Einstellungen ueberhaupt ein Rahmen entstehen kann.
 *
 * `Frame` wirft im Konstruktor, wenn die Raender breiter sind als das Blatt. Bei einer Vorlage
 * mit 8 mm Rand auf einer 10-mm-Karte fuehrte das zum Absturz statt zu einer Meldung.
 */
fun rahmenFehler(s: AppSettings): String? =
    runCatching { s.toFrame() }.exceptionOrNull()?.let {
        "Blatt und Rand passen nicht zusammen: ${it.message}"
    }
