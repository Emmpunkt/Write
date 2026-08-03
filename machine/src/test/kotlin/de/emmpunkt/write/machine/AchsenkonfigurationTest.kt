package de.emmpunkt.write.machine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `$/axes/x` liefert die Achsenkonfiguration als eingerueckten YAML-Block.
 *
 * Daraus kommen drei Groessen, die die App bisher geraten hat:
 * - `homing/mpos_mm` und `max_travel_mm`: der WIRKLICH fahrbare Bereich. Er beginnt beim
 *   Plotter des Nutzers bei 3 und nicht bei 0.
 * - `acceleration_mm_per_sec2`: bis dahin fehlte sie in der Zeitschaetzung, die deshalb
 *   rund ein Viertel zu niedrig lag.
 *
 * Der Parser ist bewusst gegen die Form des Blocks unempfindlich - er sucht die Schluessel,
 * statt auf Einrueckung oder Reihenfolge zu bauen. Welche Schluessel eine bestimmte
 * FluidNC-Fassung genau ausgibt, ist zwischen den Versionen verschieden.
 */
class AchsenkonfigurationTest {

    /** Der Block, wie FluidNC v4 ihn auf `$/axes/x` ausgibt. */
    private val antwort = """
        axes/x:
          steps_per_mm: 80.000
          max_rate_mm_per_min: 1500.000
          acceleration_mm_per_sec2: 200.000
          max_travel_mm: 155.000
          soft_limits: true
          homing:
            cycle: 1
            allow_single_axis: true
            positive_direction: false
            mpos_mm: 3.000
            feed_mm_per_min: 100.000
            seek_mm_per_min: 800.000
        ok
    """.trimIndent().lines()

    @Test
    fun `liest den fahrbaren Bereich aus Referenzpunkt und Verfahrweg`() {
        val achse = AxisSettings.parse(antwort)

        // Negative Referenzfahrt: mpos_mm ist die UNTERE Grenze, der Weg liegt darueber.
        assertEquals(3f, achse.travel?.minMm)
        assertEquals(158f, achse.travel?.maxMm)
    }

    @Test
    fun `liest Beschleunigung und Vorschubgrenze`() {
        val achse = AxisSettings.parse(antwort)

        assertEquals(200f, achse.accelMmS2)
        assertEquals(1500, achse.maxRateMmMin)
    }

    @Test
    fun `positive Referenzfahrt dreht den Bereich um`() {
        val umgekehrt = antwort.map {
            it.replace("positive_direction: false", "positive_direction: true")
                .replace("mpos_mm: 3.000", "mpos_mm: 155.000")
        }
        val achse = AxisSettings.parse(umgekehrt)

        assertEquals(0f, achse.travel?.minMm)
        assertEquals(155f, achse.travel?.maxMm)
    }

    @Test
    fun `ohne Referenzpunkt bleibt der Bereich unbekannt statt geraten`() {
        // Die Z-Achse dieses Plotters wird gar nicht referenziert. Einen Bereich zu erfinden
        // waere schlimmer als keiner - er saehe richtig aus und laege daneben.
        val ohneHoming = antwort.filterNot { it.contains("mpos_mm") }
        val achse = AxisSettings.parse(ohneHoming)

        assertNull(achse.travel, "Bereich wurde ohne Referenzpunkt geraten")
        // Die uebrigen Werte sind davon unberuehrt und weiter brauchbar.
        assertEquals(200f, achse.accelMmS2)
    }

    @Test
    fun `Einrueckung und Zusatzschluessel stoeren nicht`() {
        val andersFormatiert = listOf(
            "max_travel_mm:  155.0",
            "\t\tacceleration_mm_per_sec2:\t200.0",
            "  irgendwas_neues: 42",
            "      positive_direction: false",
            "  mpos_mm: 3.0",
        )
        val achse = AxisSettings.parse(andersFormatiert)

        assertEquals(3f, achse.travel?.minMm)
        assertEquals(158f, achse.travel?.maxMm)
        assertEquals(200f, achse.accelMmS2)
    }

    @Test
    fun `Komma als Dezimaltrennzeichen wird nicht stillschweigend verschluckt`() {
        // Der Plotter meldet mit Punkt. Kaeme je ein Komma an, waere ein falsch gelesener
        // Wert schlimmer als ein fehlender: 3,0 als "3" und "0" zu lesen ergaebe Unsinn.
        val achse = AxisSettings.parse(listOf("mpos_mm: 3,0", "max_travel_mm: 155,0"))
        assertNull(achse.travel)
    }

    @Test
    fun `leere Antwort liefert nur Unbekanntes`() {
        val achse = AxisSettings.parse(emptyList())

        assertNull(achse.travel)
        assertNull(achse.accelMmS2)
        assertNull(achse.maxRateMmMin)
        assertTrue(achse.isEmpty)
    }
}
