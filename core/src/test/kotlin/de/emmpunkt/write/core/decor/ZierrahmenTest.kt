package de.emmpunkt.write.core.decor

import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.boundingBox
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die gezeichneten Rahmen.
 *
 * Sie werden gerechnet und nicht als Grafik mitgeliefert: So passen sie sich jedem
 * Seitenverhaeltnis an, ohne dass ein Schnoerkel in die Laenge gezogen wird.
 */
class ZierrahmenTest {

    private val breite = 120f
    private val hoehe = 80f

    private fun zuege(form: RahmenForm, b: Float = breite, h: Float = hoehe) =
        rahmenZuege(form, b, h)

    @Test
    fun `ohne Rahmen wird nichts gezeichnet`() {
        assertEquals(emptyList(), zuege(RahmenForm.KEINER))
    }

    @Test
    fun `scharfe Ecken liegen genau auf dem bestellten Kasten`() {
        // Rechteck und Doppellinie brauchen keinen Platz nach aussen - sie halten in der Ecke
        // nichts frei und duerfen deshalb exakt auf dem bestellten Mass sitzen.
        listOf(RahmenForm.RECHTECK, RahmenForm.DOPPELLINIE).forEach { form ->
            val box = zuege(form).boundingBox()!!
            assertTrue(
                box.minX >= -0.01f && box.minY >= -0.01f &&
                    box.maxX <= breite + 0.01f && box.maxY <= hoehe + 0.01f,
                "$form ragt heraus: $box",
            )
        }
    }

    @Test
    fun `runde und eingezogene Ecken ruecken nur so weit nach aussen wie noetig`() {
        // Sie MUESSEN hinausragen, sonst schnitten sie in den Text. Unbegrenzt darf das aber
        // nicht sein: Ein Rahmen, der den Kasten um die halbe Seitenlaenge ueberragt, waere auf
        // einer A6-Karte nicht mehr unterzubringen.
        val grenze = minOf(breite, hoehe) * 0.25f
        listOf(RahmenForm.ABGERUNDET, RahmenForm.ZIERECKEN, RahmenForm.SPRECHBLASE).forEach { form ->
            val box = zuege(form).boundingBox()!!
            assertTrue(
                box.minX >= -grenze && box.minY >= -grenze &&
                    box.maxX <= breite + grenze && box.maxY <= hoehe + grenze,
                "$form ragt zu weit hinaus: $box (Grenze $grenze mm)",
            )
        }
    }

    @Test
    fun `jede Form nutzt ihre Flaeche wirklich aus`() {
        // Ein Rahmen, der nur in einer Ecke kleben bleibt, waere kein Rahmen.
        RahmenForm.entries.filter { it != RahmenForm.KEINER }.forEach { form ->
            val box = zuege(form).boundingBox()!!
            assertTrue(box.width > breite * 0.9f, "$form ist zu schmal: ${box.width}")
            assertTrue(box.height > hoehe * 0.9f, "$form ist zu niedrig: ${box.height}")
        }
    }

    @Test
    fun `das Rechteck ist ein einziger geschlossener Zug`() {
        val zuege = zuege(RahmenForm.RECHTECK)
        assertEquals(1, zuege.size)
        assertEquals(zuege.single().start, zuege.single().end, "Der Zug muesste sich schliessen")
    }

    @Test
    fun `die Doppellinie besteht aus zwei ineinanderliegenden Zuegen`() {
        val zuege = zuege(RahmenForm.DOPPELLINIE)
        assertEquals(2, zuege.size)

        val aussen = zuege[0].points.let { p -> p.maxOf { it.x } - p.minOf { it.x } }
        val innen = zuege[1].points.let { p -> p.maxOf { it.x } - p.minOf { it.x } }
        assertTrue(innen < aussen, "Der innere Zug muesste kleiner sein: $innen gegen $aussen")
    }

    @Test
    fun `abgerundete Ecken sind wirklich rund`() {
        val zuege = zuege(RahmenForm.ABGERUNDET)
        val punkte = zuege.flatMap { it.points }
        val box = zuege.boundingBox()!!

        // Kein Punkt in der Ecke des Rahmens selbst - dort laeuft der Bogen.
        assertTrue(
            punkte.none { abs(it.x - box.minX) < 0.01f && abs(it.y - box.minY) < 0.01f },
            "In der Rahmenecke liegt ein Punkt - dann ist sie nicht rund",
        )
        // Die Kanten liegen an ihren Raendern an.
        assertTrue(punkte.any { abs(it.x - box.minX) < 0.01f }, "Die linke Kante fehlt")
        assertTrue(punkte.any { abs(it.y - box.minY) < 0.01f }, "Die untere Kante fehlt")
    }

    @Test
    fun `Boegen werden fein genug aufgeloest`() {
        // Sehnenfehler unter 0,1 mm: der Abstand zweier Punkte auf dem Bogen bleibt klein.
        val ecke = rahmenZuege(RahmenForm.ABGERUNDET, 400f, 400f).single()
        val laengsteSehne = ecke.points.zipWithNext { a, b -> a.distanceTo(b) }
            .filter { it < 20f }  // die geraden Kanten ausklammern
            .maxOrNull() ?: 0f
        assertTrue(
            laengsteSehne < 8f,
            "Bei einem grossen Rahmen wird der Bogen eckig: Sehne $laengsteSehne mm",
        )
    }

    @Test
    fun `die Sprechblase bekommt ihren Zipfel an der gewaehlten Seite`() {
        val links = rahmenZuege(RahmenForm.SPRECHBLASE, breite, hoehe, Zipfelseite.UNTEN_LINKS)
        val rechts = rahmenZuege(RahmenForm.SPRECHBLASE, breite, hoehe, Zipfelseite.UNTEN_RECHTS)

        // Der Zipfel ist der tiefste Punkt - er sitzt links bzw. rechts der Mitte.
        val xLinks = tiefsterPunktX(links)
        val xRechts = tiefsterPunktX(rechts)
        assertTrue(xLinks < breite / 2f, "Zipfel muesste links sitzen, war bei $xLinks")
        assertTrue(xRechts > breite / 2f, "Zipfel muesste rechts sitzen, war bei $xRechts")
    }

    @Test
    fun `die Zierecken verzerren nicht, wenn der Rahmen breiter wird`() {
        // Der Kern der Entscheidung, die Formen zu rechnen statt zu zeichnen: Die Ecken
        // richten sich nach der KUERZEREN Seite und bleiben dadurch quadratisch.
        val quadratisch = rahmenZuege(RahmenForm.ZIERECKEN, 100f, 100f)
        val breit = rahmenZuege(RahmenForm.ZIERECKEN, 300f, 100f)

        val eckeA = eckenBox(quadratisch)
        val eckeB = eckenBox(breit)
        assertTrue(
            abs(eckeA.width - eckeB.width) < 0.01f && abs(eckeA.height - eckeB.height) < 0.01f,
            "Die Eckzier haette sich mitgedehnt: $eckeA gegen $eckeB",
        )
        assertTrue(
            eckeA.width <= 50f && eckeA.height <= 50f,
            "Die Eckzier frisst den halben Rahmen: $eckeA",
        )
    }

    @Test
    fun `die Zierecken richten sich nach der kuerzeren Seite`() {
        // Wird die kurze Seite kleiner, schrumpft die Zier mit - sonst sprengte sie den Rahmen.
        val gross = eckenBox(rahmenZuege(RahmenForm.ZIERECKEN, 300f, 100f))
        val klein = eckenBox(rahmenZuege(RahmenForm.ZIERECKEN, 300f, 50f))

        assertTrue(
            klein.width < gross.width - 0.01f,
            "Bei halber Hoehe muesste die Zier kleiner werden: $klein gegen $gross",
        )
    }

    @Test
    fun `jede Form umschliesst den bestellten Kasten wirklich`() {
        // Am Geraet gefunden (2026-08-04): Die Sprechblase schnitt den Streifen fuer den Zipfel
        // aus dem BESTELLTEN Kasten heraus, statt ihn nach aussen anzuhaengen. Der umschliessende
        // Teil war dadurch bei Zipfel links/rechts rund 10 mm schmaler als bestellt - der Text
        // stand sichtbar neben der Blase.
        //
        // Geprueft werden AUCH DIE ECKEN. Die erste Fassung dieses Tests nahm sie aus - "bei
        // eingezogenen oder abgerundeten Ecken liegt die Ecke bauartbedingt frei". Das war eine
        // Ausrede, und sie hat genau den naechsten Fehler verdeckt: Die Zierecken schnitten
        // 3,6 mm tief in den Text, die abgerundete Ecke 2,9 mm. Wer einen Kasten bestellt, will
        // ihn ganz - dann muss die Form eben weiter nach aussen ruecken.
        val b = 58f
        val h = 58f
        val d = 0.1f
        val proben = listOf(
            Point(b / 2f, d), Point(b / 2f, h - d),
            Point(d, h / 2f), Point(b - d, h / 2f),
            Point(d, d), Point(b - d, d), Point(d, h - d), Point(b - d, h - d),
            Point(b / 2f, h / 2f),
        )

        RahmenForm.entries.filter { it != RahmenForm.KEINER }.forEach { form ->
            Zipfelseite.entries.forEach { seite ->
                val zuege = rahmenZuege(form, b, h, seite)
                proben.forEach { p ->
                    assertTrue(
                        zuege.any { liegtInnerhalb(p, it) },
                        "$form ($seite) umschliesst $p nicht",
                    )
                }
            }
        }
    }

    @Test
    fun `der Zipfel haengt aussen an und frisst den Kasten nicht auf`() {
        val b = 58f
        val h = 58f
        Zipfelseite.entries.forEach { seite ->
            val zuege = rahmenZuege(RahmenForm.SPRECHBLASE, b, h, seite)
            val box = zuege.boundingBox()!!
            // Der bestellte Kasten liegt vollstaendig drin; der Zipfel ragt darueber hinaus.
            assertTrue(box.minX <= 0.01f && box.minY <= 0.01f, "$seite: $box beginnt zu spaet")
            assertTrue(
                box.maxX >= b - 0.01f && box.maxY >= h - 0.01f,
                "$seite: $box endet zu frueh",
            )
        }
    }

    /** Ray-Casting: liegt [p] innerhalb des geschlossenen Zuges? */
    private fun liegtInnerhalb(p: Point, zug: de.emmpunkt.write.core.geometry.Polyline): Boolean {
        var drin = false
        val pts = zug.points
        for (i in pts.indices) {
            val a = pts[i]
            val c = pts[(i + 1) % pts.size]
            if ((a.y > p.y) != (c.y > p.y)) {
                val x = a.x + (p.y - a.y) / (c.y - a.y) * (c.x - a.x)
                if (p.x < x) drin = !drin
            }
        }
        return drin
    }

    @Test
    fun `unmoegliche Masse ergeben keinen Rahmen statt eines Absturzes`() {
        // Beim Tippen einer Zahl steht kurzzeitig 0 im Feld.
        RahmenForm.entries.forEach { form ->
            assertEquals(emptyList(), rahmenZuege(form, 0f, 50f), "$form bei Breite 0")
            assertEquals(emptyList(), rahmenZuege(form, 50f, -3f), "$form bei negativer Hoehe")
        }
    }

    private fun tiefsterPunktX(zuege: List<de.emmpunkt.write.core.geometry.Polyline>): Float =
        zuege.flatMap { it.points }.minByOrNull { it.y }!!.x

    /** Die Ausdehnung dessen, was in der linken unteren Ecke gezeichnet wird. */
    private fun eckenBox(zuege: List<de.emmpunkt.write.core.geometry.Polyline>) =
        de.emmpunkt.write.core.geometry.BoundingBox.of(
            zuege.flatMap { it.points }.filter { it.x < 40f && it.y < 40f },
        )!!
}
