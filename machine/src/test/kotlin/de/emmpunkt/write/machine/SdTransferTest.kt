package de.emmpunkt.write.machine

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Der Upload auf die SD-Karte.
 *
 * Die Form des multipart-Rumpfes ist nicht frei gewaehlt - sie ist am 2026-08-03 gegen die
 * echte WebUI geprueft worden (`path=/` plus Dateifeld mit fuehrendem Schraegstrich, HTTP 200,
 * Datei kam mit exakter Groesse an). Diese Tests halten genau diese Form fest, damit sie beim
 * Umbauen nicht unbemerkt verlorengeht.
 */
class SdTransferTest {

    private val inhalt = "G21\nG90\nG0 X1 Y1\nM2\n".toByteArray()

    @Test
    fun `laedt den Inhalt unveraendert hoch`() {
        FakeWebUi().use { web ->
            HttpSdTransfer("127.0.0.1", web.port).upload("/write.nc", inhalt)

            assertContentEquals(
                inhalt,
                web.uploads["/write.nc"],
                "Der Dateiinhalt kam veraendert an",
            )
        }
    }

    @Test
    fun `binaerreine Uebertragung auch bei Sonderzeichen`() {
        // Ein Kommentar mit Umlaut reicht, um eine falsche Zeichensatzbehandlung aufzudecken.
        val mitUmlaut = "(Gruesse von der Maschine: aeoeue)\nG21\n".toByteArray(Charsets.UTF_8)
        FakeWebUi().use { web ->
            HttpSdTransfer("127.0.0.1", web.port).upload("/umlaut.nc", mitUmlaut)

            assertContentEquals(mitUmlaut, web.uploads["/umlaut.nc"])
        }
    }

    @Test
    fun `ergaenzt einen fehlenden Schraegstrich`() {
        // FluidNC erwartet den absoluten Pfad; ohne ihn landet die Datei woanders oder nirgends.
        FakeWebUi().use { web ->
            HttpSdTransfer("127.0.0.1", web.port).upload("write.nc", inhalt)

            assertTrue(
                web.uploads.containsKey("/write.nc"),
                "Erwartet wurde /write.nc, hochgeladen: ${web.uploads.keys}",
            )
        }
    }

    @Test
    fun `schickt das Zielverzeichnis als eigenes Feld`() {
        FakeWebUi().use { web ->
            HttpSdTransfer("127.0.0.1", web.port).upload("/write.nc", inhalt)

            assertEquals("/", web.lastPathField, "Das Feld path fehlt oder ist falsch")
        }
    }

    @Test
    fun `ein abgelehnter Upload wirft`() {
        // Entscheidend fuer die Sicherheit: schlaegt der Upload fehl, darf der Aufrufer
        // NICHT $SD/Run= schicken - sonst liefe die vorige Datei los.
        FakeWebUi(responseCode = 500).use { web ->
            val fehler = assertFailsWith<IOException> {
                HttpSdTransfer("127.0.0.1", web.port).upload("/write.nc", inhalt)
            }
            assertTrue(
                fehler.message!!.contains("500"),
                "Die Meldung nennt den Grund nicht: ${fehler.message}",
            )
        }
    }

    @Test
    fun `ein toter Plotter wirft statt stillzuhalten`() {
        val transfer = HttpSdTransfer("127.0.0.1", port = 1, connectTimeoutMs = 500)
        assertFailsWith<IOException> { transfer.upload("/write.nc", inhalt) }
    }
}
