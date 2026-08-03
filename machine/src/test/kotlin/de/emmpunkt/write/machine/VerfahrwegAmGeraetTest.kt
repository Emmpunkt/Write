package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.geometry.Point
import de.emmpunkt.write.core.geometry.Polyline
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Achsenkonfiguration wird beim Verbinden geholt und geht in die Grenzpruefung ein.
 *
 * Bis hierher nahm die App an, der fahrbare Bereich sei `[0, workArea]`. Wahr ist
 * `[mpos_mm, mpos_mm + max_travel]`; beim Plotter des Nutzers also 3..158 statt 0..155.
 * Solange der Arbeitsnullpunkt ueber der Untergrenze liegt, verschenkt die alte Annahme nur
 * Millimeter - liegt er darunter, laesst sie einen Auftrag in ALARM:2 laufen.
 */
class VerfahrwegAmGeraetTest {

    private val profile = MachineProfile(workAreaXMm = 155f, workAreaYMm = 105f)

    /** Die Achsenkonfiguration des Plotters des Nutzers. */
    private fun achse(mpos: String, travel: String) = """
        max_rate_mm_per_min: 1500.000
        acceleration_mm_per_sec2: 400.000
        max_travel_mm: $travel
        soft_limits: true
        homing:
          positive_direction: false
          mpos_mm: $mpos
    """.trimIndent().lines()

    /**
     * Die am 2026-08-03 ausgelesene Konfiguration des Plotters.
     *
     * Zwei Werte, die man nicht raten kann: die Untergrenze liegt bei 10 (nicht bei 0), und
     * die Z-Achse beschleunigt nur halb so schnell wie X/Y (200 statt 400).
     */
    private fun echterPlotter() = mapOf(
        'x' to achse(mpos = "10.000", travel = "155.000"),
        'y' to achse(mpos = "10.000", travel = "105.000"),
        'z' to listOf(
            "max_rate_mm_per_min: 2000.000",
            "acceleration_mm_per_sec2: 200.000",
            "max_travel_mm: 30.000",
            "soft_limits: false",
        ),
    )

    private suspend fun verbunden(
        fake: FakeFluidNc,
        block: suspend (MachineController) -> Unit,
    ) {
        val c = MachineController(TelnetTransport("127.0.0.1", fake.port), profile)
        try {
            c.connect().getOrThrow()
            block(c)
        } finally {
            c.disconnect()
        }
    }

    @Test
    fun `holt den fahrbaren Bereich beim Verbinden`() = runTest {
        FakeFluidNc(axisConfig = echterPlotter()).use { fake ->
            verbunden(fake) { c ->
                val limits = c.limits.value.travel
                assertEquals(10f, limits?.minXMm)
                assertEquals(165f, limits?.maxXMm)
                assertEquals(10f, limits?.minYMm)
                assertEquals(115f, limits?.maxYMm)
            }
            assertTrue(
                fake.received.any { it == "\$/axes/x" } && fake.received.any { it == "\$/axes/y" },
                "Achsenkonfiguration wurde nie abgefragt: ${fake.received}",
            )
        }
    }

    @Test
    fun `haelt die Beschleunigung von XY und Z auseinander`() = runTest {
        // Am Geraet gemessen: $120/$121 = 400 fuer XY, $122 = 200 fuer Z. Wer beides
        // gleichsetzt, schaetzt die Zeit der 790 Stifthuebe eines Auftrags falsch.
        FakeFluidNc(axisConfig = echterPlotter()).use { fake ->
            verbunden(fake) { c ->
                assertEquals(400f, c.limits.value.accelXYMmS2)
                assertEquals(200f, c.limits.value.accelZMmS2)
            }
            assertTrue(
                fake.received.any { it == "\$/axes/z" },
                "Z-Achse wurde nie abgefragt: ${fake.received}",
            )
        }
    }

    /**
     * Der Zustand, in dem der Plotter am 2026-08-03 wirklich stand.
     *
     * Der Arbeitsnullpunkt liegt auf Maschine (3, 3), die Achsen kommen aber nicht unter 10.
     * G54 liegt damit SIEBEN Millimeter unterhalb des fahrbaren Bereichs: alles, was auf dem
     * Blatt naeher als 7 mm an den Nullpunkt reicht, laesst die Maschine in ALARM:2 laufen -
     * mitten im Auftrag, mit halb beschriebenem Blatt.
     *
     * Die alte Pruefung gegen `[0, workArea]` haette genau das durchgelassen.
     */
    @Test
    fun `der reale Fall des Nutzers wird abgefangen`() = runTest {
        FakeFluidNc(axisConfig = echterPlotter(), wco = Triple(3f, 3f, 0f)).use { fake ->
            verbunden(fake) { c ->
                // Blatt-X 5 waere Maschine 8 - zwei Millimeter unter der Grenze.
                val probleme = c.preflight(
                    listOf(Polyline(listOf(Point(5f, 20f), Point(40f, 20f)))),
                    status = MachineStatus(MachineState.IDLE, null, null, ""),
                    istGehomt = true,
                )
                assertTrue(
                    probleme.any { it.contains("links") },
                    "Untergrenze der Achse wurde nicht geprueft: $probleme",
                )
            }
        }
    }

    @Test
    fun `mit richtig gesetztem Nullpunkt ist derselbe Auftrag in Ordnung`() = runTest {
        // Gegenprobe: die neue Grenze darf nicht pauschal alles abweisen. Liegt der
        // Arbeitsnullpunkt auf der Untergrenze, ist Blatt-X 7 (Maschine 17) laengst fahrbar -
        // und die Rueckfahrt auf (0,0) trifft genau die Untergrenze.
        FakeFluidNc(axisConfig = echterPlotter(), wco = Triple(10f, 10f, 0f)).use { fake ->
            verbunden(fake) { c ->
                val probleme = c.preflight(
                    listOf(Polyline(listOf(Point(7f, 20f), Point(40f, 20f)))),
                    status = MachineStatus(MachineState.IDLE, null, null, ""),
                    istGehomt = true,
                )
                assertTrue(probleme.isEmpty(), "Auftrag faelschlich abgewiesen: $probleme")
            }
        }
    }

    @Test
    fun `kennt die Firmware die Abfrage nicht bleibt es beim bisherigen Rueckfall`() = runTest {
        // Der Fake quittiert dann nur mit ok. Die Verbindung muss trotzdem zustande kommen -
        // ein Plotter ohne diese Abfrage ist kein Fehlerfall, nur einer ohne Zusatzwissen.
        FakeFluidNc(axisConfig = emptyMap(), wco = Triple(2f, 2f, 0f)).use { fake ->
            verbunden(fake) { c ->
                assertNull(c.limits.value.travel, "Grenzen ohne Datenlage erfunden")
                assertNull(c.limits.value.accelXYMmS2)
                assertNull(c.limits.value.accelZMmS2)

                // Die Pruefung faellt auf [0, workArea] zurueck: Blatt-X 0.5 gilt dort als
                // fahrbar, weil die Untergrenze unbekannt ist.
                val probleme = c.preflight(
                    listOf(Polyline(listOf(Point(0.5f, 20f), Point(40f, 20f)))),
                    status = MachineStatus(MachineState.IDLE, null, null, ""),
                    istGehomt = true,
                )
                assertTrue(probleme.isEmpty(), "Rueckfall verhaelt sich anders als zuvor: $probleme")
            }
        }
    }

    /**
     * Die Vorpruefung muss mit denselben Zahlen rechnen wie der erzeugte G-Code.
     *
     * Frueher bekam der Controller sein Profil EINMAL beim Verbinden. Aenderte der Nutzer
     * danach den Papier-Offset, entstand der G-Code mit dem neuen Wert, geprueft wurde aber
     * gegen den alten - die Pruefung haette einen Auftrag durchgewinkt, der herausragt.
     */
    @Test
    fun `Vorpruefung folgt einer spaeteren Aenderung der Einstellungen`() = runTest {
        FakeFluidNc(axisConfig = echterPlotter(), wco = Triple(10f, 10f, 0f)).use { fake ->
            var aktuell = profile
            val c = MachineController(
                TelnetTransport("127.0.0.1", fake.port),
                profileProvider = { aktuell },
            )
            try {
                c.connect().getOrThrow()
                val zug = listOf(Polyline(listOf(Point(10f, 20f), Point(40f, 20f))))

                assertTrue(
                    c.preflight(
                        zug,
                        status = MachineStatus(MachineState.IDLE, null, null, ""),
                        istGehomt = true,
                    ).isEmpty(),
                    "Ohne Versatz muss der Auftrag passen",
                )

                // Der Nutzer schiebt das Blatt um 140 mm nach rechts - jetzt ragt derselbe
                // Text weit ueber den Verfahrweg hinaus.
                aktuell = profile.copy(paperOffsetXMm = 140f)

                assertTrue(
                    c.preflight(
                        zug,
                        status = MachineStatus(MachineState.IDLE, null, null, ""),
                        istGehomt = true,
                    ).any { it.contains("rechts") },
                    "Die Pruefung rechnet noch mit dem Papier-Offset von vorhin",
                )
            } finally {
                c.disconnect()
            }
        }
    }

    @Test
    fun `nach dem Trennen sind die Grenzen wieder unbekannt`() = runTest {
        // Sonst wuerde die naechste Verbindung zu einer anderen Maschine mit den alten
        // Grenzen rechnen - und die sehen richtig aus.
        FakeFluidNc(axisConfig = echterPlotter()).use { fake ->
            val c = MachineController(TelnetTransport("127.0.0.1", fake.port), profile)
            c.connect().getOrThrow()
            assertTrue(c.limits.value.travel != null)
            c.disconnect()
            assertNull(c.limits.value.travel, "Grenzen der alten Verbindung blieben stehen")
        }
    }
}
