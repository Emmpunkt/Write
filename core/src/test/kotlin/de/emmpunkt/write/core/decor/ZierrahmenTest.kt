package de.emmpunkt.write.core.decor

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
    fun `jede Form bleibt innerhalb ihrer Masse`() {
        RahmenForm.entries.filter { it != RahmenForm.KEINER }.forEach { form ->
            val box = zuege(form).boundingBox()
                ?: error("$form hat gar nichts gezeichnet")
            assertTrue(
                box.minX >= -0.01f && box.minY >= -0.01f &&
                    box.maxX <= breite + 0.01f && box.maxY <= hoehe + 0.01f,
                "$form ragt heraus: $box",
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
    fun `abgerundete Ecken lassen die Ecke selbst frei`() {
        val punkte = zuege(RahmenForm.ABGERUNDET).flatMap { it.points }
        // Kein Punkt darf genau in der Ecke liegen - dort ist der Bogen.
        assertTrue(
            punkte.none { abs(it.x) < 0.01f && abs(it.y) < 0.01f },
            "In der Ecke liegt ein Punkt - dann ist sie nicht rund",
        )
        assertTrue(punkte.any { abs(it.x) < 0.01f }, "Die linke Kante muesste anliegen")
        assertTrue(punkte.any { abs(it.y) < 0.01f }, "Die untere Kante muesste anliegen")
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
