package de.emmpunkt.write.core.gcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Maschinenwerte werden ausgelesen, nicht gepflegt.
 *
 * Der Anlass ist belegt: eine Notiz nannte `mpos_mm: 3.0`, ausgelesen waren es 10 - die
 * Konfiguration hatte sich geaendert, ohne dass es jemandem auffiel. Dieselbe Firmware kann
 * morgen andere Werte haben, und der naechste Plotter hat ohnehin andere. Ein fest
 * eingetragener Wert ist deshalb kein Startwert, sondern eine tickende Fehlerquelle.
 */
class MaschinenwerteTest {

    private val eingestellt = MachineProfile(
        workAreaXMm = 155f,
        workAreaYMm = 105f,
        feedDrawMmMin = 1200,
        feedTravelMmMin = 1500,
        feedZMmMin = 600,
        accelXYMmS2 = 200f,
        accelZMmS2 = 200f,
        zUpMm = 3f,
        zDownMm = -1.5f,
    )

    /** Die am 2026-08-03 ausgelesenen Werte des Plotters. */
    private val gelesen = MachineLimits(
        travel = TravelLimits(minXMm = 10f, maxXMm = 165f, minYMm = 10f, maxYMm = 115f),
        accelXYMmS2 = 400f,
        accelZMmS2 = 200f,
        maxRateXYMmMin = 1500,
        maxRateZMmMin = 2000,
    )

    @Test
    fun `ohne gelesene Werte bleibt das Profil unveraendert`() {
        assertSame(eingestellt, eingestellt.applying(MachineLimits.UNKNOWN))
    }

    @Test
    fun `Beschleunigung kommt von der Maschine`() {
        val p = eingestellt.applying(gelesen)

        assertEquals(400f, p.accelXYMmS2, "XY-Beschleunigung nicht uebernommen")
        assertEquals(200f, p.accelZMmS2, "Z-Beschleunigung nicht uebernommen")
    }

    @Test
    fun `Verfahrweg kommt von der Maschine`() {
        val p = eingestellt.applying(gelesen)

        // 165 - 10 = 155 in X, 115 - 10 = 105 in Y.
        assertEquals(155f, p.workAreaXMm)
        assertEquals(105f, p.workAreaYMm)
    }

    @Test
    fun `ein geaenderter Verfahrweg schlaegt durch`() {
        // Der eigentliche Zweck: waere die Maschine umkonfiguriert oder eine andere, duerfte
        // der gespeicherte Wert von 155 nicht stehenbleiben.
        val andere = gelesen.copy(
            travel = TravelLimits(minXMm = 0f, maxXMm = 300f, minYMm = 0f, maxYMm = 200f),
        )
        val p = eingestellt.applying(andere)

        assertEquals(300f, p.workAreaXMm)
        assertEquals(200f, p.workAreaYMm)
    }

    @Test
    fun `gewuenschter Vorschub unter der Grenze bleibt stehen`() {
        // Langsamer zu schreiben ist eine gestalterische Entscheidung, keine Unkenntnis.
        val p = eingestellt.applying(gelesen)

        assertEquals(1200, p.feedDrawMmMin, "Der gewaehlte Zeichenvorschub wurde ueberschrieben")
        assertEquals(1500, p.feedTravelMmMin)
        assertEquals(600, p.feedZMmMin)
    }

    @Test
    fun `zu hoher Vorschub wird auf die Grenze der Maschine gekappt`() {
        // Die Firmware begrenzt ohnehin - stuende der zu hohe Wert weiter im Profil, waere
        // nur die Zeitschaetzung zu optimistisch.
        val zuSchnell = eingestellt.copy(
            feedDrawMmMin = 3000,
            feedTravelMmMin = 4000,
            feedZMmMin = 5000,
            // Muss mitwachsen: das Profil laesst keinen Eilgang unter dem Vorschub zu.
            rapidZMmMin = 5000,
        )
        val p = zuSchnell.applying(gelesen)

        assertEquals(1500, p.feedDrawMmMin)
        assertEquals(1500, p.feedTravelMmMin)
        assertEquals(2000, p.feedZMmMin)
        assertEquals(2000, p.rapidZMmMin, "Der Eilgang muss auf den Hoechstvorschub zurueck")
    }

    @Test
    fun `Stifthoehen bleiben Sache des Nutzers`() {
        // Sie haengen am Stift und am Papier, nicht an der Maschine. Die Firmware weiss
        // nichts darueber, wie weit ein bestimmter Stift eintauchen soll.
        val p = eingestellt.applying(gelesen)

        assertEquals(3f, p.zUpMm)
        assertEquals(-1.5f, p.zDownMm)
    }

    @Test
    fun `einzelne unbekannte Werte lassen den Rest in Ruhe`() {
        // Eine Firmware, die nur einen Teil meldet, darf nicht den ganzen Satz entwerten.
        val nurBeschleunigung = MachineLimits(accelXYMmS2 = 400f)
        val p = eingestellt.applying(nurBeschleunigung)

        assertEquals(400f, p.accelXYMmS2)
        assertEquals(200f, p.accelZMmS2, "Unbekannter Wert hat den eingestellten geloescht")
        assertEquals(155f, p.workAreaXMm)
        assertEquals(1200, p.feedDrawMmMin)
    }
}
