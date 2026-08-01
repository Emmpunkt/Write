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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.PaperPresets
import de.emmpunkt.write.machine.SendProgress
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    text: String,
    settings: AppSettings,
    document: DocumentState,
    machine: MachineUiState,
    onTextChange: (String) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onPlot: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTravel by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            label = { Text("Notiz") },
            placeholder = { Text("Text eingeben…") },
            visualTransformation = WortMarkierung(
                woerter = document.overlongWords,
                farbe = MaterialTheme.colorScheme.error,
            ),
        )

        Hinweise(document, settings)

        StilLeiste(settings, onSettingsChange)

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                PreviewCanvas(
                    strokes = document.laidOut?.strokes.orEmpty(),
                    frame = settings.toFrame(),
                    modifier = Modifier.height(200.dp),
                    showTravel = showTravel,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = document.job?.let { job ->
                            val zeit = job.estimatedSeconds.roundToInt()
                            val dauer = if (zeit >= 60) "${zeit / 60} min ${zeit % 60} s" else "$zeit s"
                            "%.0f mm Strich · %d Hübe · ca. %s".format(
                                Locale.GERMANY, job.drawLengthMm, job.penDownCount, dauer,
                            )
                        } ?: "Noch kein Text",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { showTravel = !showTravel }) {
                        Text(if (showTravel) "Wege aus" else "Wege")
                    }
                }
            }
        }

        SendeBereich(machine, document, onPlot, onStop)
    }
}

/** Warnungen, die vor dem Senden auffallen muessen - nicht erst als Luecke auf dem Papier. */
@Composable
private fun Hinweise(document: DocumentState, settings: AppSettings) {
    if (document.unsupported.isNotEmpty()) {
        val zeichen = document.unsupported.joinToString(" ") { String(Character.toChars(it)) }
        Warnung("Diese Schrift kann folgende Zeichen nicht: $zeichen")
    }
    if (document.overlongWords.isNotEmpty()) {
        Warnung(
            "Wird mitten im Wort getrennt (rot markiert). Bindestrich einfügen – dort " +
                "bricht die Zeile dann um.",
        )
    }
    if (document.overflow) {
        Warnung("Text ist höher als das Blatt.")
    }
    if (!settings.paperFitsWorkArea) {
        Warnung(
            "Das Blatt passt mit diesem Versatz nicht auf den Tisch " +
                "(${settings.workAreaXMm.fmt()} × ${settings.workAreaYMm.fmt()} mm).",
        )
    }
}

@Composable
private fun Warnung(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun StilLeiste(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuswahlFeld(
                label = "Schrift",
                selected = Fonts.entry(settings.fontId).displayName,
                options = Fonts.available.map { it.displayName },
                onSelect = { index ->
                    onChange { it.copy(fontId = Fonts.available[index].id) }
                },
                modifier = Modifier.weight(1.15f),
            )
            AuswahlFeld(
                label = "Blatt",
                selected = PaperPresets.all.firstOrNull {
                    it.widthMm == settings.paperWidthMm && it.heightMm == settings.paperHeightMm
                }?.name ?: "${settings.paperWidthMm.fmt()}×${settings.paperHeightMm.fmt()}",
                options = PaperPresets.all.map { it.name },
                onSelect = { index ->
                    val p = PaperPresets.all[index]
                    onChange { it.copy(paperWidthMm = p.widthMm, paperHeightMm = p.heightMm) }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Größe", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.sizeMm,
                onValueChange = { v -> onChange { it.copy(sizeMm = (v * 10).roundToInt() / 10f) } },
                valueRange = 3f..25f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${settings.sizeMm.fmt()} mm", style = MaterialTheme.typography.bodyMedium)
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val ausrichtungen = listOf(
                Align.LEFT to Icons.Default.FormatAlignLeft,
                Align.CENTER to Icons.Default.FormatAlignCenter,
                Align.RIGHT to Icons.Default.FormatAlignRight,
            )
            ausrichtungen.forEachIndexed { index, (align, icon) ->
                SegmentedButton(
                    selected = settings.align == align,
                    onClick = { onChange { it.copy(align = align) } },
                    shape = SegmentedButtonDefaults.itemShape(index, ausrichtungen.size),
                ) {
                    Icon(icon, contentDescription = align.name)
                }
            }
        }
    }
}

@Composable
private fun SendeBereich(
    machine: MachineUiState,
    document: DocumentState,
    onPlot: () -> Unit,
    onStop: () -> Unit,
) {
    val progress = machine.progress
    val laeuft = machine.busy && (progress is SendProgress.Running || progress is SendProgress.Started)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (laeuft && progress is SendProgress.Running) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Zeile ${progress.ackedLines} von ${progress.totalLines}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlot,
                enabled = machine.connected && !machine.busy && document.job != null &&
                    (document.job.penDownCount > 0),
                modifier = Modifier.weight(1f),
            ) {
                Text("An Plotter senden")
            }
            if (laeuft) {
                OutlinedButton(onClick = onStop) { Text("Not-Halt") }
            }
        }

        if (!machine.connected) {
            AssistChip(
                onClick = {},
                label = { Text("Nicht verbunden – siehe Reiter „Maschine\"") },
            )
        } else if (!machine.homed) {
            AssistChip(
                onClick = {},
                label = { Text("Noch nicht referenziert – Homing nötig") },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AuswahlFeld(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(
                androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true,
            ).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, fontFamily = FontFamily.Default) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun Float.fmt(): String =
    if (this == this.toInt().toFloat()) this.toInt().toString()
    else String.format(Locale.GERMANY, "%.1f", this)

/**
 * Hebt die hart getrennten Woerter im Eingabefeld hervor.
 *
 * Die Textlaenge bleibt unveraendert, deshalb genuegt [OffsetMapping.Identity] - Cursor und
 * Auswahl verhalten sich weiter wie gewohnt.
 */
private class WortMarkierung(
    private val woerter: Set<String>,
    private val farbe: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (woerter.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val builder = AnnotatedString.Builder(text.text)
        val stil = SpanStyle(color = farbe, textDecoration = TextDecoration.Underline)
        for (wort in woerter) {
            if (wort.isEmpty()) continue
            var index = text.text.indexOf(wort)
            while (index >= 0) {
                builder.addStyle(stil, index, index + wort.length)
                index = text.text.indexOf(wort, index + 1)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    // Damit Compose die Transformation bei gleichen Woertern nicht als geaendert ansieht.
    override fun equals(other: Any?): Boolean =
        other is WortMarkierung && other.woerter == woerter && other.farbe == farbe

    override fun hashCode(): Int = 31 * woerter.hashCode() + farbe.hashCode()
}
