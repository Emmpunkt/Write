package de.emmpunkt.write.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Beweist nur, dass im app-Modul ueberhaupt Tests laufen.
 *
 * Bis zu dieser Aufgabe gab es hier keine Test-Infrastruktur - alles Pruefbare lag in `core`
 * und `machine`. Die Notizliste bringt zum ersten Mal Logik ins app-Modul, die geprueft
 * werden muss.
 */
class FundamentTest {
    @Test
    fun `Tests im app-Modul laufen`() {
        assertEquals(4, 2 + 2)
    }
}
