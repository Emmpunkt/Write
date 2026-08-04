package de.emmpunkt.write.data

import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.TextStyle

/**
 * Ein benannter Absatzstil.
 *
 * Nur diese drei Werte duerfen von Absatz zu Absatz wechseln. Laufweite, Wortabstand,
 * Zeilenabstand und Neigung gelten fuer das ganze Dokument und stehen weiter in [AppSettings] -
 * sonst truege jeder Stil sieben Werte statt drei, ohne dass jemand danach gefragt haette.
 */
data class Absatzstil(
    val name: String,
    val fontId: String,
    val sizeMm: Float,
    val align: Align,
)

/** Was ein Stil heisst, dem der Name abhandengekommen ist. Ein leerer Chip ist nicht bedienbar. */
const val STIL_OHNE_NAMEN = "Stil"

/**
 * Trennt die Felder einer Stilzeile.
 *
 * Nicht das Semikolon - das ist in der Werteliste der Vorlagen schon vergeben und kommt in
 * Namen vor. Der senkrechte Strich laesst sich auf einer Telefontastatur kaum versehentlich
 * tippen; falls doch, wird er beim Speichern aus dem Namen entfernt.
 */
private const val FELDTRENNER = '|'

/** Schreibt die Stile, eine Zeile je Stil. */
fun stileAlsText(stile: List<Absatzstil>): String = stile.joinToString("\n") { stil ->
    listOf(
        bereinigt(stil.name),
        stil.fontId,
        stil.sizeMm.toString(),
        stil.align.name,
    ).joinToString(FELDTRENNER.toString())
}

/**
 * Liest die Stile zurueck.
 *
 * Der Text kommt aus der Datenbank und darf alles enthalten. Unbrauchbare Zeilen werden
 * uebergangen statt die Notiz unlesbar zu machen; eine leere Liste ist ein gueltiges Ergebnis,
 * ueber die Vorgabe entscheidet der Aufrufer an einer Stelle.
 */
fun stileAusText(text: String): List<Absatzstil> = text.lineSequence()
    .mapNotNull { zeile ->
        val felder = zeile.split(FELDTRENNER)
        if (felder.size < 4) return@mapNotNull null
        val groesse = felder[2].trim().toFloatOrNull() ?: return@mapNotNull null
        if (groesse <= 0f) return@mapNotNull null
        val name = felder[0].trim().ifBlank { STIL_OHNE_NAMEN }
        val fontId = felder[1].trim()
        if (fontId.isEmpty()) return@mapNotNull null

        Absatzstil(
            name = name,
            fontId = fontId,
            sizeMm = groesse,
            // Ein umbenannter oder entfernter Enum-Wert darf keine Notiz kosten.
            align = runCatching { Align.valueOf(felder[3].trim()) }.getOrDefault(Align.LEFT),
        )
    }
    .toList()

/** Schreibt die Zuordnung Absatz -> Stilindex. */
fun zuordnungAlsText(zuordnung: List<Int>): String = zuordnung.joinToString(",")

/**
 * Liest die Zuordnung zurueck.
 *
 * Ein unlesbarer Eintrag wird zum ersten Stil, behaelt aber seinen Platz. Ihn wegzulassen waere
 * schlimmer als ein falsches Schriftbild: dann ruecken alle folgenden Absaetze eine Stelle vor
 * und bekommen samt und sonders den Stil ihres Nachbarn.
 */
fun zuordnungAusText(text: String): List<Int> =
    if (text.isBlank()) {
        emptyList()
    } else {
        text.split(',').map { it.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0 }
    }

/**
 * Legt das dokumentweite Feinbild ueber die Stile.
 *
 * Erst hier entstehen die [TextStyle]s, mit denen der Satz rechnet: Schrift, Groesse und
 * Ausrichtung aus dem Stil, alles Uebrige aus den Einstellungen.
 */
fun List<Absatzstil>.alsTextStyles(s: AppSettings): List<TextStyle> = map { stil ->
    TextStyle(
        fontId = stil.fontId,
        sizeMm = stil.sizeMm,
        align = stil.align,
        lineSpacing = s.lineSpacing,
        letterSpacing = s.letterSpacing,
        wordSpacing = s.wordSpacing,
        slantDeg = s.slantDeg,
    )
}

/** Entfernt aus einem Namen, was das Format zerbrechen wuerde. */
private fun bereinigt(name: String): String =
    name.replace(FELDTRENNER.toString(), "").replace("\n", " ").trim()
