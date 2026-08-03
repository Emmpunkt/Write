package de.emmpunkt.write.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.PaperPresets
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Die Regler fuer Schriftbild und Blatt.
 *
 * Aus `EditorScreen.kt` herausgeloest, weil sie von zwei Bildschirmen gebraucht werden: dem
 * Editor und dem Serie-Reiter. Eine zweite, leicht abweichende Leiste zu bauen hiesse, dass
 * beide mit der Zeit auseinanderlaufen.
 *
 * Sie arbeiten auf [AppSettings], auch wenn im Serie-Reiter eine Vorlage bearbeitet wird: Die
 * Vorlage wird dafuer in einen AppSettings-Arbeitszustand geladen - dasselbe Verfahren wie bei
 * den Notizen.
 */

@Composable
fun StilLeiste(
    settings: AppSettings,
    textLeer: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onCommit: () -> Unit,
    onAutoFit: () -> Unit,
) {
    // Reiner Bildschirmzustand: welche Regler zuletzt offen standen, muss nichts ueberdauern.
    var feintuningOffen by remember { mutableStateOf(false) }

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
                onValueChange = { v -> onChangeLive { it.copy(sizeMm = auf(v, 0.1f)) } },
                onValueChangeFinished = onCommit,
                valueRange = AppSettings.SCHRIFTGROESSE_MIN_MM..AppSettings.SCHRIFTGROESSE_MAX_MM,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${settings.sizeMm.fmt()} mm", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAutoFit, enabled = !textLeer) { Text("Einpassen") }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { feintuningOffen = !feintuningOffen }) {
                Text(if (feintuningOffen) "Schriftbild aus" else "Schriftbild…")
            }
            if (feintuningOffen) {
                TextButton(
                    onClick = {
                        val v = AppSettings()
                        onChange {
                            it.copy(
                                letterSpacing = v.letterSpacing,
                                wordSpacing = v.wordSpacing,
                                lineSpacing = v.lineSpacing,
                                slantDeg = v.slantDeg,
                            )
                        }
                    },
                ) {
                    Text("Zurücksetzen")
                }
            }
        }

        if (feintuningOffen) {
            StilRegler(
                label = "Laufweite",
                wert = settings.letterSpacing,
                bereich = -0.2f..0.5f,
                schritt = 0.01f,
                anzeige = { "%+d %%".format(Locale.GERMANY, (it * 100).roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(letterSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Wortabstand",
                wert = settings.wordSpacing,
                bereich = -0.6f..1.0f,
                schritt = 0.01f,
                anzeige = { "%+d %%".format(Locale.GERMANY, (it * 100).roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(wordSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Zeilenabstand",
                wert = settings.lineSpacing,
                bereich = 0.8f..2.0f,
                schritt = 0.05f,
                anzeige = { String.format(Locale.GERMANY, "%.2f", it) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(lineSpacing = v) } },
                onCommit = onCommit,
            )
            StilRegler(
                label = "Neigung",
                wert = settings.slantDeg,
                bereich = -20f..20f,
                schritt = 1f,
                anzeige = { "%+d°".format(Locale.GERMANY, it.roundToInt()) },
                onChangeLive = { v -> onChangeLive { s -> s.copy(slantDeg = v) } },
                onCommit = onCommit,
            )
        }
    }
}

/**
 * Ein beschrifteter Regler mit Wertanzeige.
 *
 * Der Wert wandert waehrend des Zugs nur durch [onChangeLive]; gespeichert wird erst in
 * [onCommit] beim Loslassen.
 */
@Composable
private fun StilRegler(
    label: String,
    wert: Float,
    bereich: ClosedFloatingPointRange<Float>,
    schritt: Float,
    anzeige: (Float) -> String,
    onChangeLive: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(104.dp),
        )
        Slider(
            value = wert,
            onValueChange = { v -> onChangeLive(auf(v, schritt)) },
            onValueChangeFinished = onCommit,
            valueRange = bereich,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            anzeige(wert),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
        )
    }
}

/**
 * Rundet auf ein Vielfaches von [schritt].
 *
 * Ohne das lieferte der Regler beliebige Zwischenwerte - die Anzeige zappelte, und
 * „Einpassen" nennte eine Groesse, die sich von Hand nicht wieder treffen laesst.
 */
private fun auf(wert: Float, schritt: Float): Float =
    ((wert / schritt).roundToInt() * schritt.toDouble()).toFloat()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AuswahlFeld(
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
