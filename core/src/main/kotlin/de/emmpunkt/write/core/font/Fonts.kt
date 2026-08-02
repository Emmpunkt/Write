package de.emmpunkt.write.core.font

/**
 * Verzeichnis der mitgelieferten Schriften.
 *
 * Die Schriftdateien liegen als Java-Ressourcen im core-Modul und landen dadurch unveraendert
 * im APK. Jede geladene Schrift wird in [GlyphOverlayFont] eingepackt - der Rest der App
 * bekommt Schriften nie ohne diese Schicht zu sehen.
 */
object Fonts {

    /** In welchem Format die Schriftdatei vorliegt. Bestimmt den Parser. */
    enum class Format { JHF, SVG }

    data class Entry(
        val id: String,
        val displayName: String,
        val resource: String,
        val format: Format,
        /** Ob die Schrift verbundene Schreibschrift ist. Steuert nur die Anzeige in der Auswahl. */
        val cursive: Boolean,
    )

    val available: List<Entry> = listOf(
        Entry("allure", "Allure", "EMSAllure.svg", Format.SVG, cursive = true),
        Entry("zierschrift", "Zierschrift", "EMSDecorousScript.svg", Format.SVG, cursive = true),
        Entry("einladung", "Einladung", "EMSInvite.svg", Format.SVG, cursive = true),
        Entry("druckschrift", "Druckschrift", "EMSDelight.svg", Format.SVG, cursive = false),
        Entry("script-simplex", "Schreibschrift", "scripts.jhf", Format.JHF, cursive = true),
        Entry("sans", "Technisch", "futural.jhf", Format.JHF, cursive = false),
        Entry("serif", "Serif", "rowmans.jhf", Format.JHF, cursive = false),
    )

    val defaultId: String = available.first().id

    private val cache = HashMap<String, StrokeFont>()

    fun entry(id: String): Entry =
        available.firstOrNull { it.id == id } ?: available.first { it.id == defaultId }

    /** Laedt die Schrift (gepuffert). Unbekannte Bezeichner fallen auf [defaultId] zurueck. */
    @Synchronized
    fun load(id: String): StrokeFont {
        val e = entry(id)
        return cache.getOrPut(e.id) {
            val content = readResource("fonts/${e.resource}")
            val basis = when (e.format) {
                Format.JHF -> HersheyFont.parse(e.id, e.displayName, content)
                Format.SVG -> SvgFont.parse(e.id, e.displayName, content)
            }
            // Die Strich-Korrektur ist nur fuer die Hershey-Schriften noetig; die SVG-Schriften
            // bringen brauchbare eigene Striche mit.
            GlyphOverlayFont(basis, stricheErsetzen = e.format == Format.JHF)
        }
    }

    private fun readResource(path: String): String {
        val stream = Fonts::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Schriftdatei '$path' nicht im Paket gefunden")
        // ISO-8859-1 fuer die JHF-Dateien, deren Bytes direkt Koordinaten kodieren. Die
        // SVG-Dateien sind reines ASCII und kodieren alles darueber als HTML-Entitaet
        // (nachgeprueft: keine der vier enthaelt ein Byte ueber 0x7F) - sie ueberstehen
        // diese Kodierung damit unveraendert.
        return stream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
    }
}
