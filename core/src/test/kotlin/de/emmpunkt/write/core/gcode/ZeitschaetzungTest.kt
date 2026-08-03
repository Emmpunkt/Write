package de.emmpunkt.write.core.gcode

import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.layoutText
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Zeitschaetzung lag am Geraet rund 25 % zu niedrig: gemessen 15 Minuten, geschaetzt 11:20.
 *
 * Die Ursache steht in den Messwerten - der tatsaechliche Vorschub schwankte zwischen 157 und
 * 1.804 mm/min. Bei den kurzen Segmenten einer Schreibschrift erreicht die Maschine den
 * Sollvorschub schlicht nicht: sie beschleunigt, muss aber schon wieder bremsen. Die alte
 * Rechnung `Weg / Vorschub` unterstellt dagegen ueberall volle Geschwindigkeit.
 *
 * Deshalb wird hier das Rampenmodell geprueft und nicht ein Korrekturfaktor: ein pauschaler
 * Aufschlag traefe den langen Strich genauso wie den kurzen, obwohl der Fehler genau bei den
 * kurzen entsteht.
 */
class ZeitschaetzungTest {

    private val profile = MachineProfile(
        workAreaXMm = 200f,
        workAreaYMm = 200f,
        // Ohne Leerlauf-Sortierung bleibt die Reihenfolge vorhersagbar.
        naturalWriteOrder = true,
    )

    /** Ein gerader Zug der Laenge [mm] ab dem Nullpunkt. */
    private fun zug(mm: Float) = Polyline(listOf(Point(0f, 0f), Point(mm, 0f)))

    @Test
    fun `lange Bewegung erreicht den Sollvorschub und faehrt ein Trapez`() {
        val v = 20f      // 1200 mm/min
        val a = 200f     // mm/s^2
        // Beschleunigen und Bremsen brauchen zusammen v^2/a = 2 mm; 50 mm liegen weit darueber.
        val t = rampSeconds(lengthMm = 50f, feedMmMin = 1200, accelMmS2 = a)

        assertEquals(50f / v + v / a, t, 1e-4f)
        assertTrue(t > 50f / v, "Die Rampe muss Zeit kosten")
    }

    @Test
    fun `kurze Bewegung erreicht den Sollvorschub nie und faehrt ein Dreieck`() {
        val a = 200f
        // 0.5 mm liegt unter der Strecke, die zum Hochbeschleunigen noetig waere (1 mm).
        val t = rampSeconds(lengthMm = 0.5f, feedMmMin = 1200, accelMmS2 = a)

        // Dreiecksprofil: halbe Strecke beschleunigen, halbe bremsen.
        assertEquals(2f * sqrt(0.5f / a), t, 1e-4f)

        // Und das ist der Kern des Fehlers: fast dreimal so lang wie die naive Rechnung.
        val naiv = 0.5f / 20f
        assertTrue(t > naiv * 2.5f, "Erwartete deutliche Unterschaetzung, war $t statt $naiv")
    }

    @Test
    fun `sehr grosse Beschleunigung faellt auf die naive Rechnung zurueck`() {
        // Gegenprobe: die Rampe ist die einzige Quelle des Unterschieds. Bei praktisch
        // unendlicher Beschleunigung muss wieder Weg/Vorschub herauskommen.
        val t = rampSeconds(lengthMm = 50f, feedMmMin = 1200, accelMmS2 = 1e9f)
        assertEquals(50f / 20f, t, 1e-3f)
    }

    @Test
    fun `viele kurze Zuege dauern laenger als ein langer gleicher Gesamtlaenge`() {
        // Gleiche Strichlaenge, gleiche Anzahl Millimeter Papier - aber in Stuecken zerlegt
        // kommt die Maschine nie auf Touren. Genau das passiert bei einer Schreibschrift.
        val einLangerZug = listOf(zug(100f))
        val hundertKurze = (0 until 100).map {
            Polyline(listOf(Point(it * 1.5f, 0f), Point(it * 1.5f + 1f, 0f)))
        }

        val lang = generateGCode(einLangerZug, profile)
        val kurz = generateGCode(hundertKurze, profile)

        assertTrue(
            kurz.estimatedSeconds > lang.estimatedSeconds,
            "Zerstueckelt muss laenger dauern: ${kurz.estimatedSeconds} vs ${lang.estimatedSeconds}",
        )
    }

    @Test
    fun `Schaetzung liegt ueber der alten Rechnung ohne Rampen`() {
        val font = Fonts.load("script-simplex")
        val frame = Frame(148f, 105f, Margins.all(10f))
        val laid = layoutText(
            (1..8).joinToString("\n") { "Zeile $it mit etwas Text" },
            TextStyle("script-simplex", sizeMm = 6f),
            frame,
            font,
        )
        val job = laid.toPlotJob(profile)

        // Die alte Formel, wie sie bis zur Messung im Code stand.
        val alt = 60f * (
            job.drawLengthMm / profile.feedDrawMmMin +
                job.travelLengthMm / profile.feedTravelMmMin +
                (profile.zUpMm - profile.zDownMm) * 2f * job.penDownCount / profile.feedZMmMin
            )

        assertTrue(
            job.estimatedSeconds > alt,
            "Rampen fehlen weiterhin: ${job.estimatedSeconds} s vs ${alt} s",
        )
        // Der gemessene Fehlbetrag lag bei rund einem Viertel. Die Schaetzung soll ihn
        // aufholen, ohne ins Gegenteil zu kippen - sonst traut ihr niemand mehr.
        assertTrue(
            job.estimatedSeconds < alt * 2.5f,
            "Aufschlag unplausibel hoch: ${job.estimatedSeconds} s vs ${alt} s",
        )
    }

    /**
     * Das Anheben des Stifts ist ein Eilgang, das Absenken nicht.
     *
     * Im erzeugten G-Code steht `G1 Z-1.5 F600` zum Senken, aber `G0 Z3` zum Heben - und G0
     * faehrt mit dem Hoechstvorschub der Achse, nicht mit dem gesetzten. Wer beide gleich
     * rechnet, schaetzt jeden Hub zu lang; bei hunderten Huebén je Auftrag summiert sich das.
     *
     * Am Geraet gemessen (2026-08-03): 55 s fuer einen Auftrag, den das Modell mit gleichem
     * Z-Vorschub auf 62 s schaetzte.
     */
    @Test
    fun `Anheben rechnet im Eilgang und ist schneller als das Absenken`() {
        val langsam = profile.copy(feedZMmMin = 600, rapidZMmMin = 600)
        val eilgang = profile.copy(feedZMmMin = 600, rapidZMmMin = 2000)

        val a = generateGCode(listOf(zug(20f)), langsam).estimatedSeconds
        val b = generateGCode(listOf(zug(20f)), eilgang).estimatedSeconds

        assertTrue(b < a, "Der Eilgang beim Anheben wurde nicht beruecksichtigt: $b vs $a")
    }

    @Test
    fun `Eilgang wird nicht unter den Zeichenvorschub gedrueckt`() {
        // Gegenprobe: ein Profil ohne bekannten Eilgang darf nicht plotzlich langsamer
        // rechnen als vorher. Die Vorgabe muss mindestens dem Z-Vorschub entsprechen.
        val p = MachineProfile()
        assertTrue(
            p.rapidZMmMin >= p.feedZMmMin,
            "Der Eilgang darf nicht unter dem gesetzten Z-Vorschub liegen",
        )
    }

    @Test
    fun `Beschleunigung muss positiv sein`() {
        val abgelehnt = runCatching { profile.copy(accelXYMmS2 = 0f) }.isFailure
        assertTrue(abgelehnt, "Eine Beschleunigung von null bedeutet unendliche Fahrzeit")
    }
}
