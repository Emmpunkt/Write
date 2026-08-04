package de.emmpunkt.write.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.SerienZustand
import de.emmpunkt.write.machine.SendProgress

/**
 * Der Serie-Reiter: Vorlage pflegen, Werteliste eintippen, Satz plotten.
 *
 * Die Regler kommen unveraendert aus dem Editor - siehe StilLeiste.kt. Die Vorlage steckt dafuer
 * in einem eigenen AppSettings-Arbeitszustand, getrennt vom Editor.
 */
@Composable
fun SerieScreen(
    serie: SerieUiState,
    machine: MachineUiState,
    onVorlageOeffnen: (Long) -> Unit,
    onVorlageAnlegen: () -> Unit,
    onVorlageLoeschen: (Long) -> Unit,
    onNameChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onWerteChange: (String) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsCommit: () -> Unit,
    onStarten: (Boolean) -> Unit,
    onWeiter: () -> Unit,
    onUeberspringen: () -> Unit,
    onAbbrechen: () -> Unit,
    onBeenden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loeschenBestaetigen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (serie.vorlagen.isEmpty()) {
            Text(
                "Noch keine Vorlage. „+ Neu“ legt eine an — zum Beispiel für Platzkarten.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onVorlageAnlegen) { Text("+ Neu") }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuswahlFeld(
                label = "Vorlage",
                selected = serie.vorlagen.firstOrNull { it.id == serie.aktuelleId }?.name.orEmpty(),
                options = serie.vorlagen.map { it.name },
                onSelect = { index -> onVorlageOeffnen(serie.vorlagen[index].id) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onVorlageAnlegen) { Text("+ Neu") }
            TextButton(onClick = { loeschenBestaetigen = true }) { Text("Löschen") }
        }

        OutlinedTextField(
            value = serie.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name der Vorlage") },
            singleLine = true,
        )

        OutlinedTextField(
            value = serie.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("Vorlage") },
            placeholder = { Text("{anrede} {name},") },
            supportingText = { Text("Platzhalter in geschweiften Klammern, z. B. {name}") },
        )

        StilLeiste(
            settings = serie.settings,
            textLeer = serie.zeilen.isEmpty(),
            onChange = onSettingsChange,
            onChangeLive = onSettingsChangeLive,
            onCommit = onSettingsCommit,
            // Einpassen ergaebe je Bogen eine andere Groesse - bei einem Satz Platzkarten
            // stoert das. Der Nutzer stellt die Groesse einmal fuer alle ein.
            onAutoFit = {},
        )

        Rahmen(serie.settings, onSettingsChange)

        OutlinedTextField(
            value = serie.werte,
            onValueChange = onWerteChange,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            label = { Text("Werte — eine Zeile je Bogen") },
            supportingText = {
                Text(
                    if (serie.spalten.isEmpty()) {
                        "Erst einen Platzhalter in die Vorlage setzen."
                    } else {
                        "je Zeile: ${serie.spalten.joinToString(";")} — ${serie.bogenGesamt} Bogen"
                    },
                )
            },
        )

        Befund(serie)

        if (serie.lauf == null) {
            SerieStart(serie, machine, onStarten)
        } else {
            SerieLauf(serie.lauf, machine, onWeiter, onUeberspringen, onAbbrechen, onBeenden)
        }

        // Vorschau des ersten Bogens - so sieht jede Karte aus.
        serie.vorschau?.let { laid ->
            Text("Vorschau: Bogen 1", style = MaterialTheme.typography.labelLarge)
            PreviewCanvas(
                strokes = laid.strokes,
                frame = serie.settings.toFrame(),
                // PreviewCanvas hat keine eigene Hoehe - ohne diese Angabe faellt sie auf ihre
                // Polsterung zusammen. Derselbe Wert wie im Editor, damit beide gleich wirken.
                modifier = Modifier.height(200.dp),
            )
        }
    }

    if (loeschenBestaetigen) {
        AlertDialog(
            onDismissRequest = { loeschenBestaetigen = false },
            title = { Text("Vorlage löschen?") },
            text = { Text("„${serie.name}“ wird gelöscht, samt Werteliste.") },
            confirmButton = {
                TextButton(onClick = {
                    onVorlageLoeschen(serie.aktuelleId)
                    loeschenBestaetigen = false
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { loeschenBestaetigen = false }) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Groesse, Rand und Position des Textrahmens - alles fuer DIESE Vorlage.
 *
 * Die Felder gehoeren hierher und nicht nach Optionen: Dort wirken sie nur auf die globale
 * Vorgabe, die eine bestehende Vorlage nie mehr erreicht. Ein freies Format war deshalb im
 * Serienmodus einmal verloren, nicht wieder einzugeben - das Auswahlfeld darueber zeigt zwar
 * "50×50" an, anbieten kann es aber nur die Vorgaben.
 */
@Composable
private fun Rahmen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Text("Textrahmen", style = MaterialTheme.typography.labelLarge)
    Text(
        "Wo auf dem Tisch der Text steht und wie groß der Kasten ist. Der Versatz zählt von " +
            "der linken unteren Ecke des Arbeitsbereichs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZahlFeld("Breite (mm)", settings.paperWidthMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(paperWidthMm = v) }
        }
        ZahlFeld("Höhe (mm)", settings.paperHeightMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(paperHeightMm = v) }
        }
        ZahlFeld("Rand (mm)", settings.marginMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(marginMm = v) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZahlFeld("Versatz X (mm)", settings.paperOffsetXMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(paperOffsetXMm = v) }
        }
        ZahlFeld("Versatz Y (mm)", settings.paperOffsetYMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(paperOffsetYMm = v) }
        }
    }
}

/** Was der Vorpruefung aufgefallen ist. */
@Composable
private fun Befund(serie: SerieUiState) {
    val zeilenFehler = serie.zeilen.mapNotNull { it.fehler }
    val bogenFehler = serie.befunde.filterNot { it.inOrdnung }

    when {
        serie.fehler != null -> Meldung(serie.fehler)

        zeilenFehler.isNotEmpty() -> Meldung(zeilenFehler.joinToString("\n"))

        bogenFehler.isNotEmpty() -> Meldung(
            bogenFehler.joinToString("\n") { b ->
                val grund = if (b.ueberlauf) "läuft über" else "wird mitten im Wort getrennt"
                "Bogen ${b.index + 1} „${b.bezeichnung}“ $grund."
            } + "\n\nKürzen, Schrift verkleinern oder den Rand verringern.",
        )

        serie.bogenGesamt > 0 -> Text(
            "Alle ${serie.bogenGesamt} Bogen passen.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Meldung(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SerieStart(
    serie: SerieUiState,
    machine: MachineUiState,
    onStarten: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onStarten(false) },
            enabled = serie.startbar && machine.connected && !machine.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Satz plotten") }
        OutlinedButton(
            onClick = { onStarten(true) },
            enabled = serie.startbar && machine.connected && !machine.busy,
            modifier = Modifier.weight(1f),
        ) { Text("Satz über SD") }
    }
}

@Composable
private fun SerieLauf(
    lauf: SerienZustand,
    machine: MachineUiState,
    onWeiter: () -> Unit,
    onUeberspringen: () -> Unit,
    onAbbrechen: () -> Unit,
    onBeenden: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (lauf) {
                is SerienZustand.Bereit -> "Bereit — Bogen ${lauf.naechster + 1} von ${lauf.gesamt}"
                is SerienZustand.Laeuft -> "Bogen ${lauf.index + 1} von ${lauf.gesamt} läuft…"
                is SerienZustand.WartetAufBlatt ->
                    "Bogen ${lauf.fertig} von ${lauf.gesamt} fertig — nächstes Blatt einlegen"
                is SerienZustand.Fehlgeschlagen ->
                    "Bogen ${lauf.index + 1} fehlgeschlagen: ${lauf.meldung}"
                is SerienZustand.Fertig ->
                    "Fertig: ${lauf.geplottet} geplottet" +
                        if (lauf.uebersprungen > 0) ", ${lauf.uebersprungen} übersprungen" else ""
                SerienZustand.Abgebrochen -> "Abgebrochen"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        // Der Fortschritt des laufenden Bogens. Bewusst schlicht: der Zaehler darueber sagt,
        // wo im Satz man steht, und das ist beim Blattwechsel die wichtigere Zahl.
        (machine.progress as? SendProgress.Running)?.let { p ->
            Text(
                "${(p.fraction * 100).toInt()} % dieses Bogens",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when (lauf) {
            is SerienZustand.Fertig, SerienZustand.Abgebrochen ->
                Button(onClick = onBeenden, modifier = Modifier.fillMaxWidth()) { Text("Schließen") }

            is SerienZustand.Laeuft ->
                Button(onClick = onAbbrechen, modifier = Modifier.fillMaxWidth()) {
                    Text("Abbrechen")
                }

            // Der wichtigste Knopf bekommt eine eigene Zeile. Zu dritt nebeneinander wurde er
            // am Geraet auf eine senkrechte Buchstabensaeule zusammengequetscht - "Nächster
            // Bogen" ist zu lang, um sich den Platz mit zwei weiteren zu teilen.
            else -> {
                Button(onClick = onWeiter, modifier = Modifier.fillMaxWidth()) {
                    Text(if (lauf is SerienZustand.Fehlgeschlagen) "Nochmal" else "Nächster Bogen")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onUeberspringen, modifier = Modifier.weight(1f)) {
                        Text("Überspringen")
                    }
                    OutlinedButton(onClick = onAbbrechen, modifier = Modifier.weight(1f)) {
                        Text("Stopp")
                    }
                }
            }
        }
    }
}
