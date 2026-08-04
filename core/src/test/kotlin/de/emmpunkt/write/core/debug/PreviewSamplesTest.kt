package de.emmpunkt.write.core.debug

import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.gcode.MachineProfile
import de.emmpunkt.write.core.gcode.toPlotJob
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.core.layout.Frame
import de.emmpunkt.write.core.layout.Margins
import de.emmpunkt.write.core.layout.TextStyle
import de.emmpunkt.write.core.layout.AbsatzSatz
import de.emmpunkt.write.core.layout.fitSize
import de.emmpunkt.write.core.layout.layoutAbsaetze
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Erzeugt Musterbilder aus fertigem G-Code nach core/build/preview/.
 *
 * Kein Testfall im engeren Sinn, sondern die Sichtpruefung ohne Plotter: die Bilder zeigen,
 * was der Stift faehrt. Schriftbild, Umlaute und Zeilenfall lassen sich so beurteilen, bevor
 * Papier verbraucht wird.
 */
class PreviewSamplesTest {

    private val profile = MachineProfile(zUpMm = 3f, zDownMm = -1.5f, workAreaXMm = 300f, workAreaYMm = 300f)
    private val penDownZ = (profile.zUpMm + profile.zDownMm) / 2f
    private val outDir = File("build/preview")

    private fun render(
        name: String,
        text: String,
        style: TextStyle,
        frame: Frame,
        showTravel: Boolean = false,
    ): File = rendereAbsaetze(
        name,
        text.split('\n').map { AbsatzSatz(it, style, Fonts.load(style.fontId)) },
        frame,
        showTravel,
    )

    private fun rendereAbsaetze(
        name: String,
        absaetze: List<AbsatzSatz>,
        frame: Frame,
        showTravel: Boolean = false,
    ): File {
        val laid = layoutAbsaetze(absaetze, frame)
        val job = laid.toPlotJob(profile)
        val target = File(outDir, "$name.png")
        GCodeRenderer.renderPng(
            lines = job.lines,
            penDownZ = penDownZ,
            widthMm = frame.widthMm,
            heightMm = frame.heightMm,
            target = target,
            pixelsPerMm = 8,
            showTravel = showTravel,
        )
        println("$name: ${GCodeRenderer.summary(job.lines, penDownZ)}, ${job.penDownCount} Huebe, " +
            "ca. ${job.estimatedSeconds.toInt()} s, Ueberlauf=${laid.overflow}")
        return target
    }

    @Test
    fun `Schriftmetriken ausgeben`() {
        println("Schrift            cap  asc  desc lineH  space  space/cap")
        Fonts.available.forEach { e ->
            val f = Fonts.load(e.id)
            val space = f.glyph(' '.code)!!.advance
            println(
                String.format(
                    java.util.Locale.ROOT,
                    "%-18s %4.1f %4.1f %5.1f %5.1f %6.1f %8.2f",
                    e.id, f.capHeightUnits, f.ascenderUnits, f.descenderUnits,
                    f.lineHeightUnits, space, space / f.capHeightUnits,
                ),
            )
        }
    }

    @Test
    fun `Musterbilder erzeugen`() {
        val a6 = Frame(widthMm = 105f, heightMm = 148f, margins = Margins.all(10f))

        // Zeichensatz mit allen deutschen Sonderzeichen.
        val zeichensatz = "Hallo Welt\näöüß ÄÖÜ\n0123456789\n,.;:!?-()€"
        render("01-zeichensatz-script", zeichensatz, TextStyle("script-simplex", sizeMm = 8f), a6)
        render("02-zeichensatz-sans", zeichensatz, TextStyle("sans", sizeMm = 8f), a6)

        // Eine echte Notiz, wie sie in der App entstehen wuerde.
        val notiz = "Einkaufsliste\n\nMilch, Brot, Kaffee\nButter und Eier\n\nam Samstag abholen"
        render("03-notiz-script", notiz, TextStyle("script-simplex", sizeMm = 7f), a6)
        render("04-notiz-script-zentriert", notiz, TextStyle("script-simplex", sizeMm = 7f, align = Align.CENTER), a6)

        // Alle Schriften im Vergleich - der Bogen, an dem sich das Schriftbild beurteilen
        // laesst, ohne Papier zu verbrauchen.
        val probe = "Handschrift 123 äöüß"
        Fonts.available.forEachIndexed { i, entry ->
            render("05-${i}-${entry.id}", probe, TextStyle(entry.id, sizeMm = 9f), Frame(150f, 30f, Margins.all(5f)))
        }

        // Der Fall, der Etappe 2b ausgeloest hat: bei grosser Schrift trat der Versatz
        // zwischen den Buchstaben zutage. Dieselbe Zeile in jeder Schrift, gross gesetzt.
        Fonts.available.forEach { entry ->
            render("19-versatz-${entry.id}", "Etappe geschafft",
                TextStyle(entry.id, sizeMm = 20f), Frame(230f, 45f, Margins.all(5f)))
        }

        // Leerfahrten sichtbar: zeigt, ob die Pfadsortierung sinnvolle Wege waehlt.
        render("06-leerfahrten", "Milch Brot Kaffee", TextStyle("script-simplex", sizeMm = 10f),
            Frame(105f, 40f, Margins.all(8f)), showTravel = true)

        // Striche im Vergleich zu Kleinbuchstaben.
        render("08-striche", "neben-an nn-nn\nGedanke – Strich\nnenne o-o e-e",
            TextStyle("script-simplex", sizeMm = 9f), Frame(120f, 60f, Margins.all(5f)))
        render("09-striche-sans", "neben-an nn-nn\nGedanke – Strich",
            TextStyle("sans", sizeMm = 9f), Frame(120f, 45f, Margins.all(5f)))

        // Neigung und Laufweite.
        render("07-feintuning", "Kursiv gestellt", TextStyle("script-simplex", sizeMm = 10f, slantDeg = 12f, letterSpacing = 0.15f),
            Frame(120f, 30f, Margins.all(5f)))

        // Die vier Feintuning-Regler in ihren Randlagen - so laesst sich am Bildschirm
        // entscheiden, ob die Bereiche der Regler brauchbar gewaehlt sind.
        val muster = "Handschrift wird hier\nueber zwei Zeilen gesetzt"
        val reglerRahmen = Frame(120f, 55f, Margins.all(5f))
        val varianten = listOf(
            "10-laufweite-eng" to TextStyle("script-simplex", sizeMm = 7f, letterSpacing = -0.2f),
            "11-laufweite-weit" to TextStyle("script-simplex", sizeMm = 7f, letterSpacing = 0.5f),
            "12-wortabstand-eng" to TextStyle("script-simplex", sizeMm = 7f, wordSpacing = -0.6f),
            "13-wortabstand-weit" to TextStyle("script-simplex", sizeMm = 7f, wordSpacing = 1.0f),
            "14-zeilen-eng" to TextStyle("script-simplex", sizeMm = 7f, lineSpacing = 0.8f),
            "15-zeilen-weit" to TextStyle("script-simplex", sizeMm = 7f, lineSpacing = 2.0f),
            "16-neigung-links" to TextStyle("script-simplex", sizeMm = 7f, slantDeg = -20f),
            "17-neigung-rechts" to TextStyle("script-simplex", sizeMm = 7f, slantDeg = 20f),
        )
        varianten.forEach { (name, stil) -> render(name, muster, stil, reglerRahmen) }

        // Was "Einpassen" aus einem zu langen Text macht.
        val zuViel = "Einkaufsliste fuer Samstag: Milch, Brot, Kaffee, Butter und Eier. " +
            "Danach zur Post und das Paket abholen, es liegt seit Dienstag dort."
        val a6quer = Frame(148f, 105f, Margins.all(8f))
        val eingepasst = fitSize(zuViel, TextStyle("script-simplex"), a6quer, Fonts.load("script-simplex"))
        render("18-eingepasst", zuViel, TextStyle("script-simplex", sizeMm = eingepasst.sizeMm), a6quer)
        println("Eingepasst auf ${eingepasst.sizeMm} mm (passt=${eingepasst.fits})")

        // Gemischte Stile je Absatz: grosse zentrierte Ueberschrift ueber kleinem Fliesstext.
        // Hier sieht man die Vorschubregel am Absatzwechsel - der Abstand richtet sich nach der
        // groesseren der beiden Zeilen, sonst sackte der Text in die Unterlaengen des Titels.
        rendereAbsaetze(
            "20-absatzstile",
            listOf(
                AbsatzSatz(
                    "Einladung",
                    TextStyle("allure", sizeMm = 12f, align = Align.CENTER),
                    Fonts.load("allure"),
                ),
                AbsatzSatz("", TextStyle("sans", sizeMm = 5f), Fonts.load("sans")),
                AbsatzSatz(
                    "Wir feiern am Samstag ab 18 Uhr im Garten. Bring bitte gute Laune mit.",
                    TextStyle("sans", sizeMm = 5f),
                    Fonts.load("sans"),
                ),
                AbsatzSatz(
                    "Anna & Jonas",
                    TextStyle("allure", sizeMm = 7f, align = Align.RIGHT),
                    Fonts.load("allure"),
                ),
            ),
            a6quer,
        )

        val erzeugt = outDir.listFiles { f: File -> f.extension == "png" }?.size ?: 0
        assertTrue(erzeugt >= 30, "Es wurden nur $erzeugt Musterbilder erzeugt")
        println("Musterbilder in ${outDir.absolutePath}")
    }
}
