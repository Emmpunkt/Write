package de.emmpunkt.write.core.decor

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Die Form eines gezeichneten Rahmens.
 *
 * Die Formen werden GERECHNET und nicht als Grafik mitgeliefert. Eine fertige Zeichnung muesste
 * auf jedes Seitenverhaeltnis gedehnt werden, und ein in die Laenge gezogener Schnoerkel sieht
 * sofort falsch aus. Gerechnet passt sich der Rahmen jedem Kasten an, und es gibt keine
 * Lizenzfrage.
 */
enum class RahmenForm(val bezeichnung: String) {
    KEINER("kein Rahmen"),
    RECHTECK("Rechteck"),
    DOPPELLINIE("Doppellinie"),
    ABGERUNDET("abgerundet"),
    SPRECHBLASE("Sprechblase"),
    ZIERECKEN("Zierecken"),
}

/** Wohin der Zipfel der Sprechblase zeigt. */
enum class Zipfelseite(val bezeichnung: String) {
    UNTEN_LINKS("unten links"),
    UNTEN_RECHTS("unten rechts"),
    LINKS("links"),
    RECHTS("rechts"),
}

/**
 * Zeichnet den Rahmen in einen Kasten von [breiteMm] x [hoeheMm].
 *
 * Ursprung ist die linke untere Ecke, Y zeigt nach oben - dieselbe Konvention wie im Textsatz
 * und im G-Code. Der Aufrufer verschiebt die Zuege an ihren Platz.
 *
 * Unmoegliche Masse ergeben eine leere Liste statt einer Ausnahme: Beim Tippen einer Zahl steht
 * im Eingabefeld kurzzeitig 0, und daran darf die Vorschau nicht zerbrechen.
 */
fun rahmenZuege(
    form: RahmenForm,
    breiteMm: Float,
    hoeheMm: Float,
    zipfel: Zipfelseite = Zipfelseite.UNTEN_LINKS,
): List<Polyline> {
    if (form == RahmenForm.KEINER || breiteMm <= 0f || hoeheMm <= 0f) return emptyList()

    return when (form) {
        RahmenForm.KEINER -> emptyList()
        RahmenForm.RECHTECK -> listOf(rechteck(0f, 0f, breiteMm, hoeheMm))
        RahmenForm.DOPPELLINIE -> doppellinie(breiteMm, hoeheMm)
        RahmenForm.ABGERUNDET -> listOf(abgerundet(breiteMm, hoeheMm, eckradius(breiteMm, hoeheMm)))
        RahmenForm.SPRECHBLASE -> listOf(sprechblase(breiteMm, hoeheMm, zipfel))
        RahmenForm.ZIERECKEN -> listOf(zierecken(breiteMm, hoeheMm))
    }
}

/**
 * Wie fein ein Bogen in Geraden zerlegt wird.
 *
 * An der Groesse bemessen und nicht mit fester Punktzahl: Ein kleiner Bogen bekaeme sonst
 * unnoetig viele Punkte (und der Plotter unnoetig viele Zeilen), ein grosser wuerde sichtbar
 * eckig. Der Sehnenfehler eines Kreisbogens ist r * (1 - cos(w/2)); daraus folgt der groesste
 * Schrittwinkel, der [SEHNENFEHLER_MM] noch einhaelt.
 */
private const val SEHNENFEHLER_MM = 0.05f

/** Bogen um [mx], [my] mit Radius [r], von [vonGrad] nach [bisGrad] (mathematisch positiv). */
private fun bogen(mx: Float, my: Float, r: Float, vonGrad: Float, bisGrad: Float): List<Point> {
    if (r <= 0f) return listOf(Point(mx, my))

    val spanne = Math.toRadians((bisGrad - vonGrad).toDouble())
    // acos-Argument sauber halten: bei sehr grossem Radius wird 1 - fehler/r knapp unter 1.
    val maxSchritt = 2.0 * acos((1.0 - SEHNENFEHLER_MM / r).coerceIn(-1.0, 1.0))
    val stuecke = if (maxSchritt <= 0.0) 1 else ceil(abs(spanne) / maxSchritt).toInt().coerceAtLeast(1)

    return (0..stuecke).map { i ->
        val w = Math.toRadians(vonGrad.toDouble()) + spanne * i / stuecke
        Point(mx + r * cos(w).toFloat(), my + r * sin(w).toFloat())
    }
}

/** Ein geschlossenes Rechteck als ein Zug. */
private fun rechteck(x: Float, y: Float, b: Float, h: Float): Polyline = Polyline(
    listOf(
        Point(x, y),
        Point(x + b, y),
        Point(x + b, y + h),
        Point(x, y + h),
        Point(x, y),
    ),
)

/**
 * Der Abstand zwischen den beiden Linien der Doppellinie.
 *
 * Anteilig zur kuerzeren Seite, aber nach oben und unten begrenzt: Bei einer Visitenkarte
 * verschwaende ein fester 3-mm-Abstand die Flaeche, bei einem A4-Bogen waere ein fester
 * 1-mm-Abstand nicht mehr als Doppellinie zu erkennen.
 */
private fun doppellinie(b: Float, h: Float): List<Polyline> {
    val abstand = (minOf(b, h) * 0.03f).coerceIn(0.8f, 3f)
    // Frisst der Abstand den Kasten auf, bleibt es bei der einen Linie - zwei Linien
    // uebereinander waeren nur doppelte Fahrzeit fuer denselben Strich.
    if (b - 2 * abstand <= 0f || h - 2 * abstand <= 0f) return listOf(rechteck(0f, 0f, b, h))

    return listOf(
        rechteck(0f, 0f, b, h),
        rechteck(abstand, abstand, b - 2 * abstand, h - 2 * abstand),
    )
}

/** Eckradius: anteilig zur kuerzeren Seite, damit die Ecke quadratisch bleibt. */
private fun eckradius(b: Float, h: Float): Float = minOf(b, h) * 0.12f

/** Rechteck mit vier Viertelbogen-Ecken, als ein geschlossener Zug. */
private fun abgerundet(b: Float, h: Float, r: Float): Polyline {
    val punkte = ArrayList<Point>()
    punkte += Point(r, 0f)
    punkte += Point(b - r, 0f)
    punkte += bogen(b - r, r, r, -90f, 0f)
    punkte += Point(b, h - r)
    punkte += bogen(b - r, h - r, r, 0f, 90f)
    punkte += Point(r, h)
    punkte += bogen(r, h - r, r, 90f, 180f)
    punkte += Point(0f, r)
    punkte += bogen(r, r, r, 180f, 270f)
    punkte += Point(r, 0f)
    return Polyline(punkte)
}

/**
 * Abgerundeter Kasten mit einem Zipfel an einer Seite.
 *
 * Der Zipfel sitzt bewusst nicht mittig, sondern auf einem Viertel der Kante: Mittig wirkt er
 * wie ein Fehler in der Symmetrie, seitlich wie eine Sprechblase.
 */
private fun sprechblase(b: Float, h: Float, zipfel: Zipfelseite): Polyline {
    val r = eckradius(b, h)
    val laenge = minOf(b, h) * 0.18f
    val basis = minOf(b, h) * 0.14f

    // Der Kasten belegt nur den Teil, den der Zipfel uebrig laesst.
    val untenFrei = zipfel == Zipfelseite.UNTEN_LINKS || zipfel == Zipfelseite.UNTEN_RECHTS
    val kastenB = if (untenFrei) b else b - laenge
    val kastenH = if (untenFrei) h - laenge else h
    if (kastenB <= 2 * r || kastenH <= 2 * r) return abgerundet(b, h, eckradius(b, h))

    val xVersatz = if (zipfel == Zipfelseite.LINKS) laenge else 0f
    val yVersatz = if (untenFrei) laenge else 0f
    val kasten = abgerundet(kastenB, kastenH, eckradius(kastenB, kastenH))
        .translate(xVersatz, yVersatz)

    // Der Zipfel wird als Umweg in den Umriss eingefuegt: drei Punkte an der passenden Kante.
    val spitze: Point
    val fussA: Point
    val fussB: Point
    when (zipfel) {
        Zipfelseite.UNTEN_LINKS -> {
            val mitte = kastenB * 0.3f
            fussA = Point(mitte - basis / 2f, yVersatz)
            fussB = Point(mitte + basis / 2f, yVersatz)
            spitze = Point(mitte - basis, 0f)
        }
        Zipfelseite.UNTEN_RECHTS -> {
            val mitte = kastenB * 0.7f
            fussA = Point(mitte - basis / 2f, yVersatz)
            fussB = Point(mitte + basis / 2f, yVersatz)
            spitze = Point(mitte + basis, 0f)
        }
        Zipfelseite.LINKS -> {
            val mitte = kastenH * 0.7f
            fussA = Point(xVersatz, mitte + basis / 2f)
            fussB = Point(xVersatz, mitte - basis / 2f)
            spitze = Point(0f, mitte + basis)
        }
        Zipfelseite.RECHTS -> {
            val mitte = kastenH * 0.7f
            fussA = Point(kastenB, mitte - basis / 2f)
            fussB = Point(kastenB, mitte + basis / 2f)
            spitze = Point(b, mitte + basis)
        }
    }

    return Polyline(mitZipfel(kasten.points, fussA, fussB, spitze))
}

/**
 * Faedelt den Zipfel in den Umriss ein.
 *
 * Eingesetzt wird an der Stelle, an der der Umriss dem Zipfelfuss am naechsten kommt. Den
 * Zipfel als eigenen Zug zu zeichnen waere einfacher, gaebe aber zwei zusaetzliche Stifthuebe
 * und eine sichtbare Naht, wo die Linien nicht genau aufeinandertreffen.
 */
private fun mitZipfel(
    umriss: List<Point>,
    fussA: Point,
    fussB: Point,
    spitze: Point,
): List<Point> {
    val stelle = umriss.indices.minByOrNull { umriss[it].distanceTo(fussA) } ?: return umriss
    val ergebnis = ArrayList<Point>(umriss.size + 3)
    ergebnis += umriss.take(stelle + 1)
    ergebnis += fussA
    ergebnis += spitze
    ergebnis += fussB
    ergebnis += umriss.drop(stelle + 1)
    return ergebnis
}

/**
 * Gerade Kanten mit einem Schnoerkel in jeder Ecke.
 *
 * Die Groesse der Zier richtet sich nach der KUERZEREN Seite. Dadurch bleiben die Ecken
 * quadratisch, egal wie schmal der Rahmen wird - genau das kann eine fertige Zeichnung nicht,
 * die man auf das Seitenverhaeltnis dehnt.
 */
private fun zierecken(b: Float, h: Float): Polyline {
    val zier = (minOf(b, h) * 0.16f).coerceAtMost(minOf(b, h) / 2f)

    // Der Mittelpunkt jedes Bogens liegt in der Ecke SELBST. Dadurch treffen Bogenanfang und
    // Kantenende zwangslaeufig aufeinander - es gibt keine Naht, an der die Linien schief
    // zusammenlaufen koennten -, und der Bogen zieht sich von der Ecke weg nach innen.
    val punkte = ArrayList<Point>()
    punkte += Point(zier, 0f)
    punkte += Point(b - zier, 0f)
    punkte += bogen(b, 0f, zier, 180f, 90f)
    punkte += Point(b, h - zier)
    punkte += bogen(b, h, zier, 270f, 180f)
    punkte += Point(zier, h)
    punkte += bogen(0f, h, zier, 0f, -90f)
    punkte += Point(0f, zier)
    punkte += bogen(0f, 0f, zier, 90f, 0f)
    return Polyline(punkte)
}
