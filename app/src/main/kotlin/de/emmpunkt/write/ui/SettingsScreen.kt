package de.emmpunkt.write.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.data.AppSettings
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Abschnitt("Verbindung")
        TextFeld("Adresse des Plotters", settings.host) { v -> onChange { it.copy(host = v) } }
        ZahlFeld("Telnet-Port", settings.telnetPort.toFloat()) { v ->
            onChange { it.copy(telnetPort = v.toInt()) }
        }
        Hinweis(
            "Die App spricht ausschließlich Telnet. Die Weboberfläche des Plotters liefert " +
                "Antworten über ihren WebSocket aus und beantwortet reine HTTP-Anfragen mit " +
                "\"WebSocket dead\" – als Übertragungsweg taugt sie deshalb nicht.",
        )

        HorizontalDivider()
        Abschnitt("Stift und Z-Achse")
        Hinweis(
            "Der Stift liegt mit Eigengewicht auf. Z_unten darf deshalb bewusst unter der " +
                "Papierebene liegen – dieses Übertravel gleicht Unebenheiten aus.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("Z oben (mm)", settings.zUpMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(zUpMm = v) }
            }
            ZahlFeld("Z unten (mm)", settings.zDownMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(zDownMm = v) }
            }
        }

        HorizontalDivider()
        Abschnitt("Vorschübe (mm/min)")
        Hinweis("Am Plotter ausgelesen: X/Y höchstens 1500, Z höchstens 2000.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("Schreiben", settings.feedDrawMmMin.toFloat(), Modifier.weight(1f)) { v ->
                onChange { it.copy(feedDrawMmMin = v.toInt()) }
            }
            ZahlFeld("Leerfahrt", settings.feedTravelMmMin.toFloat(), Modifier.weight(1f)) { v ->
                onChange { it.copy(feedTravelMmMin = v.toInt()) }
            }
            ZahlFeld("Z", settings.feedZMmMin.toFloat(), Modifier.weight(1f)) { v ->
                onChange { it.copy(feedZMmMin = v.toInt()) }
            }
        }

        HorizontalDivider()
        Abschnitt("Arbeitsbereich")
        Hinweis("Am Plotter ausgelesen (\$130/\$131): 155 × 105 mm. Soft Limits sind aktiv.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("X max (mm)", settings.workAreaXMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(workAreaXMm = v) }
            }
            ZahlFeld("Y max (mm)", settings.workAreaYMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(workAreaYMm = v) }
            }
        }

        HorizontalDivider()
        Abschnitt("Blatt")
        Hinweis(
            "Das Papier, das auf dem Tisch liegt. Der Versatz beschreibt, wo seine linke " +
                "untere Ecke am Anschlag sitzt. Wo der Text auf diesem Blatt steht, legt der " +
                "Textrahmen fest – der steht im Editor und gehört zur jeweiligen Vorlage.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("Versatz X (mm)", settings.paperOffsetXMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(paperOffsetXMm = v) }
            }
            ZahlFeld("Versatz Y (mm)", settings.paperOffsetYMm, Modifier.weight(1f)) { v ->
                onChange { it.copy(paperOffsetYMm = v) }
            }
        }
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
        Hinweis(
            "Der Rand wird nur von „Blatt füllen“ benutzt: damit springt der Textrahmen auf " +
                "das ganze Blatt, um diesen Betrag eingerückt.",
        )

        HorizontalDivider()
        Abschnitt("Schreibweise")
        Schalter(
            titel = "In Schreibrichtung zeichnen",
            erklaerung = "Buchstabe für Buchstabe von links nach rechts, so wie von Hand " +
                "geschrieben. Der Stift fährt nie über bereits Geschriebenes zurück. " +
                "Ausgeschaltet sortiert die App nach kürzesten Wegen – etwas schneller, " +
                "aber der Stift springt in der Zeile hin und her.",
            wert = settings.naturalWriteOrder,
        ) { v -> onChange { it.copy(naturalWriteOrder = v) } }

        HorizontalDivider()
        Abschnitt("Feintuning der Schrift")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("Zeilenabstand", settings.lineSpacing, Modifier.weight(1f)) { v ->
                onChange { it.copy(lineSpacing = v.coerceAtLeast(0.1f)) }
            }
            ZahlFeld("Laufweite", settings.letterSpacing, Modifier.weight(1f)) { v ->
                onChange { it.copy(letterSpacing = v) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZahlFeld("Wortabstand", settings.wordSpacing, Modifier.weight(1f)) { v ->
                onChange { it.copy(wordSpacing = v) }
            }
            ZahlFeld("Neigung (Grad)", settings.slantDeg, Modifier.weight(1f)) { v ->
                onChange { it.copy(slantDeg = v) }
            }
        }
    }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(titel, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Hinweis(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextFeld(label: String, wert: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = wert,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Schalter(titel: String, erklaerung: String, wert: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                erklaerung,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = wert, onCheckedChange = onChange)
    }
}
