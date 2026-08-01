package de.emmpunkt.write.machine

import de.emmpunkt.write.core.gcode.MachineProfile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamSenderTest {

    private val profile = MachineProfile(zUpMm = 3f, zDownMm = -1.5f)

    private fun gcode(count: Int) =
        listOf("G21", "G90", "G94", "G0 Z3") +
            (1..count).map { "G1 X$it.000 Y$it.000 F1500" } +
            listOf("G0 Z3", "G0 X0 Y0", "M2")

    private inline fun <T> withFake(
        configure: FakeFluidNc.() -> Unit = {},
        block: (FakeFluidNc, TelnetTransport) -> T,
    ): T {
        FakeFluidNc().use { fake ->
            fake.configure()
            val transport = TelnetTransport("127.0.0.1", fake.port)
            transport.connect()
            assertTrue(fake.awaitConnection(), "Verbindung kam nicht zustande")
            try {
                return block(fake, transport)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `sendet alle Zeilen in unveraenderter Reihenfolge`() = runTest {
        val lines = gcode(40)
        withFake { fake, transport ->
            val progress = StreamSender(transport, profile).send(lines, 10f).toList()

            assertTrue(progress.last() is SendProgress.Completed, "Auftrag nicht abgeschlossen: ${progress.last()}")
            assertEquals(lines, fake.received.toList())
        }
    }

    @Test
    fun `ueberschreitet den Empfangspuffer der Maschine nie`() = runTest {
        // Lange Zeilen erzwingen viele Pufferwechsel; mit Verzoegerung quittiert die Attrappe
        // langsamer als der Sender schiebt - genau die Lage, in der ein Ueberlauf entstuende.
        val lines = (1..60).map { "G1 X${it}.123 Y${it}.456 Z-1.500 F1500" }
        withFake({ ackDelayMs = 1 }) { fake, transport ->
            StreamSender(transport, profile).send(lines, 10f).toList()

            assertTrue(
                fake.peakBufferBytes.get() <= 127,
                "Empfangspuffer ueberlaufen: ${fake.peakBufferBytes.get()} Bytes",
            )
        }
    }

    @Test
    fun `haelt den Puffer gut gefuellt statt Zeile fuer Zeile zu warten`() = runTest {
        val lines = (1..60).map { "G1 X${it}.123 Y${it}.456 F1500" }
        withFake({ ackDelayMs = 1 }) { fake, transport ->
            StreamSender(transport, profile).send(lines, 10f).toList()

            // Wuerde der Sender auf jedes ok einzeln warten, laege der Spitzenwert bei der
            // Laenge einer Zeile. Der Bewegungsplaner liefe dann zwischen den Zuegen leer.
            assertTrue(
                fake.peakBufferBytes.get() > 60,
                "Sender wartet offenbar Zeile fuer Zeile (Spitze ${fake.peakBufferBytes.get()} Bytes)",
            )
        }
    }

    @Test
    fun `meldet Fortschritt fortlaufend und monoton`() = runTest {
        val lines = gcode(30)
        withFake { _, transport ->
            val progress = StreamSender(transport, profile).send(lines, 10f).toList()

            val started = progress.first()
            assertTrue(started is SendProgress.Started)
            assertEquals(lines.size, started.totalLines)

            val running = progress.filterIsInstance<SendProgress.Running>()
            assertTrue(running.isNotEmpty())
            assertEquals(running.map { it.ackedLines }.sorted(), running.map { it.ackedLines })
            assertEquals(lines.size, running.last().ackedLines)
            assertEquals(1f, running.last().fraction)
        }
    }

    @Test
    fun `bricht bei error ab und hebt den Stift`() = runTest {
        val lines = gcode(50)
        withFake({ failAtLine = 10 }) { fake, transport ->
            val progress = StreamSender(transport, profile).send(lines, 10f).toList()

            val failed = progress.last()
            assertTrue(failed is SendProgress.Failed, "Erwartet: Failed, war: $failed")
            assertTrue(failed.message.contains("abgelehnt"), failed.message)
            assertTrue(failed.penLifted, "Stift wurde nicht angehoben")

            // Der Rest des Auftrags darf nicht mehr gesendet worden sein.
            fake.awaitQuiet()
            assertTrue(fake.received.size < lines.size, "Sender hat nach dem Fehler weitergemacht")

            // Und der Stift muss tatsaechlich angehoben worden sein. Geprueft wird das LETZTE
            // Kommando - ein "G0 Z3" steht auch im Kopf jedes Auftrags und beweist nichts.
            assertEquals(
                "G0 Z3",
                fake.received.last(),
                "Letztes Kommando muss das Anheben sein: ${fake.received.takeLast(5)}",
            )
        }
    }

    @Test
    fun `bricht bei ALARM ab und haelt die Maschine an`() = runTest {
        val lines = gcode(50)
        withFake({ alarmAtLine = 8 }) { fake, transport ->
            val progress = StreamSender(transport, profile).send(lines, 10f).toList()

            val failed = progress.last()
            assertTrue(failed is SendProgress.Failed, "Erwartet: Failed, war: $failed")
            assertTrue(failed.message.contains("Alarm"), failed.message)

            // Feed Hold und Soft Reset muessen die Bewegung gestoppt haben, bevor Z faehrt.
            assertTrue(Commands.FEED_HOLD in fake.realtime, "Kein Feed Hold gesendet")
            assertTrue(Commands.SOFT_RESET in fake.realtime, "Kein Soft Reset gesendet")
        }
    }

    @Test
    fun `entsperrt vor dem Anheben denn im Alarmzustand faehrt nichts`() = runTest {
        withFake({ alarmAtLine = 5 }) { fake, transport ->
            StreamSender(transport, profile).send(gcode(30), 10f).toList()
            fake.awaitQuiet()

            val unlockIndex = fake.received.indexOf(Commands.UNLOCK)
            // Das LETZTE Anheben zaehlt - das erste stammt aus dem Kopf des G-Codes.
            val liftIndex = fake.received.indexOfLast { it.startsWith("G0 Z") }
            assertTrue(unlockIndex >= 0, "Kein \$X gesendet: ${fake.received.takeLast(6)}")
            assertTrue(
                liftIndex > unlockIndex,
                "Z-Bewegung vor dem Entsperren - die Maschine haette sie verworfen",
            )
        }
    }

    @Test
    fun `leerer Auftrag laeuft sauber durch`() = runTest {
        withFake { fake, transport ->
            val progress = StreamSender(transport, profile).send(emptyList(), 0f).toList()
            assertTrue(progress.last() is SendProgress.Completed)
            assertTrue(fake.received.isEmpty())
        }
    }

    @Test
    fun `Verbindungsabbruch wird gemeldet statt still zu enden`() = runTest {
        val transport = FakeFluidNc().use { fake ->
            val t = TelnetTransport("127.0.0.1", fake.port)
            t.connect()
            fake.awaitConnection()
            t
        } // Server hier geschlossen

        val progress = StreamSender(transport, profile).send(gcode(200), 10f).toList()
        val last = progress.last()
        assertTrue(
            last is SendProgress.Failed,
            "Abbruch muss als Fehler gemeldet werden, war: $last",
        )
        transport.close()
    }

    @Test
    fun `Statusberichte gelten nicht als Quittung`() = runTest {
        // Waehrend eines Auftrags fragt die Anzeige den Status ab. Wuerde der Sender die
        // Antwort <Idle|...> als ok werten, geriete die Pufferrechnung durcheinander und der
        // Empfangspuffer liefe ueber.
        val parser = StatusParser()
        val status = Response.classify("<Run|MPos:1.000,2.000,3.000|FS:500,0>", parser)
        assertTrue(status is Response.Status, "Statusbericht falsch eingeordnet: $status")
    }
}

/**
 * Auf der Verbindung gibt es nur einen Antwortstrom. Ohne Serialisierung liest die laufende
 * Statusabfrage die Quittung eines Fahrbefehls weg - der Befehl laeuft dann in die
 * Zeitueberschreitung und meldet "keine Antwort vom Plotter", obwohl die Maschine sauber
 * geantwortet hat. Genau das trat beim schnellen Antippen der Fahrtasten auf.
 */
class NebenlaeufigkeitTest {

    private val profile = MachineProfile(zUpMm = 3f, zDownMm = -1.5f)

    @Test
    fun `Fahrbefehle und Statusabfragen stoeren sich nicht`() = runTest {
        FakeFluidNc().use { fake ->
            val transport = TelnetTransport("127.0.0.1", fake.port)
            val c = MachineController(transport, profile)
            c.connect().getOrThrow()

            val fehler = java.util.concurrent.CopyOnWriteArrayList<String>()

            coroutineScope {
                // Die Anzeige fragt fortlaufend ab ...
                repeat(15) {
                    launch { runCatching { c.requestStatus() } }
                }
                // ... waehrend der Nutzer schnell auf die Fahrtasten tippt.
                repeat(15) { i ->
                    launch {
                        c.jog(if (i % 2 == 0) Axis.X else Axis.Y, 1f)
                            .onFailure { fehler += it.message ?: "unbekannt" }
                    }
                }
            }

            assertTrue(
                fehler.isEmpty(),
                "${fehler.size} von 15 Fahrbefehlen scheiterten: ${fehler.take(3)}",
            )
            // Alle Fahrbefehle muessen die Maschine auch wirklich erreicht haben.
            assertEquals(15, fake.received.count { it.startsWith("\$J=") })

            c.disconnect()
        }
    }
}

/**
 * Nach einem Not-Halt steht die Maschine im Alarmzustand. Sind Soft Limits aktiv und gilt sie
 * als nicht referenziert, verweigert sie jede Bewegung - der Stift bleibt dann auf dem Papier
 * stehen. Die App darf das keinesfalls als Erfolg melden.
 */
class NotHaltTest {

    private val profile = MachineProfile(zUpMm = 3f, zDownMm = -1.5f)

    @Test
    fun `meldet ehrlich wenn der Stift nicht angehoben werden konnte`() = runTest {
        FakeFluidNc(failAtLine = 10, rejectMovesAfterReset = true).use { fake ->
            val transport = TelnetTransport("127.0.0.1", fake.port)
            transport.connect()

            val progress = StreamSender(transport, profile)
                .send((1..40).map { "G1 X$it Y$it F1000" }, 10f)
                .toList()

            val failed = progress.last()
            assertTrue(failed is SendProgress.Failed, "Erwartet: Failed, war: $failed")
            assertFalse(
                failed.penLifted,
                "Anheben wurde abgelehnt, trotzdem als erfolgreich gemeldet",
            )
            transport.close()
        }
    }

    @Test
    fun `meldet Erfolg wenn das Anheben durchgeht`() = runTest {
        FakeFluidNc(failAtLine = 10).use { fake ->
            val transport = TelnetTransport("127.0.0.1", fake.port)
            transport.connect()

            val progress = StreamSender(transport, profile)
                .send((1..40).map { "G1 X$it Y$it F1000" }, 10f)
                .toList()

            val failed = progress.last() as SendProgress.Failed
            assertTrue(failed.penLifted, "Anheben ging durch, wurde aber nicht gemeldet")
            fake.awaitQuiet()
            assertEquals("G0 Z3", fake.received.last())
            transport.close()
        }
    }

    @Test
    fun `Not-Halt am Controller meldet Hinweis wenn der Stift unten bleibt`() = runTest {
        FakeFluidNc(rejectMovesAfterReset = true).use { fake ->
            val c = MachineController(TelnetTransport("127.0.0.1", fake.port), profile)
            c.connect().getOrThrow()

            val result = c.emergencyStop()

            assertTrue(result.stopped, "Anhalten muss gelingen")
            assertFalse(result.penLifted, "Bewegung wurde abgelehnt")
            val hinweis = assertNotNull(result.hint)
            assertTrue(hinweis.contains("Hand"), "Hinweis nennt nicht die Handlung: $hinweis")
            c.disconnect()
        }
    }
}
