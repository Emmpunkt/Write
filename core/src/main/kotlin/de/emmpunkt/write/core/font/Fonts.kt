package de.emmpunkt.write.core.font

/**
 * Verzeichnis der mitgelieferten Schriften.
 *
 * Die JHF-Dateien liegen als Java-Ressourcen im core-Modul und landen dadurch unveraendert im
 * APK. Jede geladene Schrift wird in [GlyphOverlayFont] eingepackt, damit Umlaute und Eszett
 * ueberall verfuegbar sind - der Rest der App bekommt Fonts nie ohne diese Schicht zu sehen.
 */
object Fonts {

    data class Entry(
        val id: String,
        val displayName: String,
        val resource: String,
        /** Ob die Schrift verbundene Schreibschrift ist. Steuert nur die Anzeige in der Auswahl. */
        val cursive: Boolean,
    )

    val available: List<Entry> = listOf(
        Entry("script-simplex", "Schreibschrift", "scripts.jhf", cursive = true),
        Entry("script-complex", "Kalligrafie", "scriptc.jhf", cursive = true),
        Entry("sans", "Technisch", "futural.jhf", cursive = false),
        Entry("serif", "Serif", "rowmans.jhf", cursive = false),
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
            GlyphOverlayFont(HersheyFont.parse(e.id, e.displayName, content))
        }
    }

    private fun readResource(path: String): String {
        val stream = Fonts::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Schriftdatei '$path' nicht im Paket gefunden")
        return stream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
    }
}
