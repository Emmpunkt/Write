package de.emmpunkt.write.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.core.font.Fonts
import de.emmpunkt.write.core.layout.Align
import de.emmpunkt.write.data.Absatzstil
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
    /** Der Absatz, in dem der Cursor steht - 0-basiert, angezeigt wird `+ 1`. */
    absatzIndex: Int,
    /** Der Stil dieses Absatzes. Er ist es, den die Regler darunter bearbeiten. */
    stilIndex: Int,
    /** Chip angetippt: Dieser Stil gilt ab jetzt fuer den Absatz am Cursor. */
    onStilZuweisen: (Int) -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onCommit: () -> Unit,
    onAutoFit: () -> Unit,
    /**
     * Das Blatt ist global - es beschreibt das Papier auf dem Tisch, nicht das Dokument.
     * Deshalb bekommt es einen eigenen Weg: Im Serie-Reiter geht [onChange] in die Vorlage,
     * das Blattformat aber muss trotzdem in den Einstellungen landen.
     */
    onBlattChange: ((AppSettings) -> AppSettings) -> Unit = onChange,
) {
    // Reiner Bildschirmzustand: welche Regler zuletzt offen standen, muss nichts ueberdauern.
    var feintuningOffen by remember { mutableStateOf(false) }
    var rahmenOffen by remember { mutableStateOf(false) }
    var stilDialogOffen by remember { mutableStateOf(false) }

    val stil = settings.stile.getOrElse(stilIndex) { settings.stile.first() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StilChips(
            stile = settings.stile,
            absatzIndex = absatzIndex,
            stilIndex = stilIndex,
            onZuweisen = onStilZuweisen,
            onAnlegen = {
                onChange { it.mitNeuemStil(stilIndex) }
                // Der neue Stil steht am Ende - und gilt sofort fuer den Absatz am Cursor,
                // sonst legte man einen Stil an, den niemand benutzt.
                onStilZuweisen(settings.stile.size)
            },
            onBearbeiten = { stilDialogOffen = true },
        )

        if (stilDialogOffen) {
            StilDialog(
                stil = stil,
                loeschbar = stilIndex > 0 && settings.stile.size > 1,
                onUmbenennen = { neu ->
                    onChange { s -> s.mitStil(stilIndex) { it.copy(name = neu) } }
                    stilDialogOffen = false
                },
                onLoeschen = {
                    onChange { it.ohneStil(stilIndex) }
                    stilDialogOffen = false
                },
                onSchliessen = { stilDialogOffen = false },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuswahlFeld(
                label = "Schrift",
                selected = Fonts.entry(stil.fontId).displayName,
                options = Fonts.available.map { it.displayName },
                onSelect = { index ->
                    onChange { s ->
                        s.mitStil(stilIndex) { it.copy(fontId = Fonts.available[index].id) }
                    }
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
                    onBlattChange { it.copy(paperWidthMm = p.widthMm, paperHeightMm = p.heightMm) }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Größe", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = stil.sizeMm,
                onValueChange = { v ->
                    onChangeLive { s -> s.mitStil(stilIndex) { it.copy(sizeMm = auf(v, 0.1f)) } }
                },
                onValueChangeFinished = onCommit,
                valueRange = AppSettings.SCHRIFTGROESSE_MIN_MM..AppSettings.SCHRIFTGROESSE_MAX_MM,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${stil.sizeMm.fmt()} mm", style = MaterialTheme.typography.bodyMedium)
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
                    selected = stil.align == align,
                    onClick = { onChange { s -> s.mitStil(stilIndex) { it.copy(align = align) } } },
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
            TextButton(onClick = { rahmenOffen = !rahmenOffen }) {
                Text(if (rahmenOffen) "Textrahmen aus" else "Textrahmen…")
            }
            if (rahmenOffen) {
                TextButton(onClick = { onChange { it.blattFuellen() } }) { Text("Blatt füllen") }
            }
        }

        if (rahmenOffen) {
            Rahmenfelder(settings, onChange)
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
 * Die Stile als Chip-Reihe, darueber die Nummer des Absatzes am Cursor.
 *
 * Antippen weist zu UND waehlt aus - beides ist derselbe Griff, weil die Regler darunter immer
 * den Stil des Absatzes zeigen, in dem man gerade steht. Ein getrennter "gewaehlter Stil"
 * waere ein zweiter Zustand, der zur Cursorposition nicht passen kann.
 */
@Composable
private fun StilChips(
    stile: List<Absatzstil>,
    absatzIndex: Int,
    stilIndex: Int,
    onZuweisen: (Int) -> Unit,
    onAnlegen: () -> Unit,
    onBearbeiten: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Absatz ${absatzIndex + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onBearbeiten) {
            Icon(Icons.Default.Edit, contentDescription = "Stil bearbeiten")
        }
    }

    // Waagerecht scrollbar: Bei fuenf Stilen mit langen Namen ist in einer Telefonzeile sonst
    // Schluss, und der letzte Chip waere nicht mehr erreichbar.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stile.forEachIndexed { index, stil ->
            FilterChip(
                selected = index == stilIndex,
                onClick = { onZuweisen(index) },
                label = { Text(stil.name, maxLines = 1, softWrap = false) },
            )
        }
        AssistChip(onClick = onAnlegen, label = { Text("+") })
    }
}

/**
 * Umbenennen und Loeschen eines Stils.
 *
 * Als Dialog hinter einem sichtbaren Stift-Knopf statt hinter langem Tippen auf den Chip: Eine
 * Geste, die man nicht sieht, findet niemand.
 */
@Composable
private fun StilDialog(
    stil: Absatzstil,
    loeschbar: Boolean,
    onUmbenennen: (String) -> Unit,
    onLoeschen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    var name by remember { mutableStateOf(stil.name) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Stil „${stil.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!loeschbar) {
                    Text(
                        "Der erste Stil bleibt bestehen – ohne ihn hätte das Dokument kein " +
                            "Schriftbild.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUmbenennen(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            if (loeschbar) {
                TextButton(onClick = onLoeschen) { Text("Löschen") }
            } else {
                TextButton(onClick = onSchliessen) { Text("Abbrechen") }
            }
        },
    )
}

/**
 * Groesse und Lage des Textkastens - auf dem Blatt, nicht auf dem Tisch.
 *
 * Standardmaessig eingeklappt: die vier Zahlen stellt man einmal ein und danach selten wieder,
 * waehrend Schrift, Groesse und Ausrichtung staendig gebraucht werden. Ausgeklappt sind sie
 * dieselben Felder wie fuer das Blatt unter Optionen - nur zaehlt der Versatz hier ab der
 * linken unteren BLATTECKE.
 *
 * Im Editor wirken sie auf die globale Vorgabe, im Serie-Reiter auf die offene Vorlage. Das
 * entscheidet der Aufrufer ueber `onChange`, nicht dieser Baustein.
 */
@Composable
private fun Rahmenfelder(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    Text(
        "Der Kasten, in den der Text gesetzt wird. Versatz ab der linken unteren Ecke des " +
            "Blattes (${settings.paperWidthMm.fmt()} × ${settings.paperHeightMm.fmt()} mm).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZahlFeld("Breite (mm)", settings.rahmenBreiteMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(rahmenBreiteMm = v) }
        }
        ZahlFeld("Höhe (mm)", settings.rahmenHoeheMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(rahmenHoeheMm = v) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZahlFeld("Versatz X (mm)", settings.rahmenXMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(rahmenXMm = v) }
        }
        ZahlFeld("Versatz Y (mm)", settings.rahmenYMm, Modifier.weight(1f)) { v ->
            onChange { it.copy(rahmenYMm = v) }
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

/**
 * Zahlenfeld, das waehrend der Eingabe nicht dazwischenfunkt.
 *
 * Der eingetippte Text bleibt stehen, solange er sich nicht als Zahl lesen laesst - sonst
 * wuerde ein halb eingegebenes "1," sofort verworfen. Uebernommen wird nur ein gueltiger Wert;
 * das Komma als Dezimaltrennzeichen ist ausdruecklich erlaubt.
 *
 * Aus `SettingsScreen.kt` hierher geholt, weil der Serie-Reiter dieselben Felder fuer den
 * Textrahmen der Vorlage braucht. Zwei getrennte Fassungen liefen sonst auseinander.
 */
@Composable
fun ZahlFeld(
    label: String,
    wert: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    // Der Schluessel `wert` sorgt dafuer, dass ein von aussen geaenderter Wert im Feld
    // ankommt - etwa wenn eine andere Vorlage geoeffnet wird.
    var text by rememberSaveable(wert) { mutableStateOf(wert.fmt()) }
    OutlinedTextField(
        value = text,
        onValueChange = { neu ->
            text = neu
            neu.replace(',', '.').toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
