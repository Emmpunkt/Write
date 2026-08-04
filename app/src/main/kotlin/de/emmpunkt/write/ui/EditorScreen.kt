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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.data.absatzAmCursor
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.data.NoteEntity
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
    /** Der Absatz, in dem der Cursor steht - der Editor ist die einzige Stelle, die ihn kennt. */
    absatzIndex: Int,
    stilIndex: Int,
    onCursor: (Int) -> Unit,
    onStilZuweisen: (Int) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsChangeLive: ((AppSettings) -> AppSettings) -> Unit,
    onSettingsCommit: () -> Unit,
    onAutoFit: () -> Unit,
    onPlot: () -> Unit,
    onPlotViaSd: () -> Unit,
    onStop: () -> Unit,
    notizen: List<NoteEntity>,
    aktuelleNotizId: Long,
    onNotizOeffnen: (Long) -> Unit,
    onNotizAnlegen: () -> Unit,
    onNotizLoeschen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTravel by remember { mutableStateOf(false) }
    var listeOffen by remember { mutableStateOf(false) }

    // Das Eingabefeld fuehrt seine Auswahl selbst - nur so ist bekannt, in welchem Absatz der
    // Cursor steht. Der Schluessel sorgt dafuer, dass beim Notizwechsel ein frischer Zustand
    // mit dem neuen Text entsteht; ohne ihn zeigte das Feld weiter den alten.
    var feld by remember(aktuelleNotizId) { mutableStateOf(TextFieldValue(text)) }
    if (feld.text != text) {
        // Von aussen geaendert (erste Ladung aus der Datenbank). Beim Tippen tritt das nicht
        // ein, weil der Text unveraendert zurueckkommt.
        feld = TextFieldValue(text, TextRange(text.length))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { listeOffen = !listeOffen }) {
                Text(if (listeOffen) "Notizen schließen" else "Notizen (${notizen.size})")
            }
            TextButton(onClick = onNotizAnlegen) { Text("+ Neu") }
        }

        if (listeOffen) {
            NotizListe(
                notizen = notizen,
                aktuelleId = aktuelleNotizId,
                onOeffnen = {
                    onNotizOeffnen(it)
                    listeOffen = false
                },
                onLoeschen = onNotizLoeschen,
            )
        }

        OutlinedTextField(
            value = feld,
            onValueChange = { neu ->
                feld = neu
                if (neu.text != text) onTextChange(neu.text)
                onCursor(absatzAmCursor(neu.text, neu.selection.start))
            },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            label = { Text("Notiz") },
            placeholder = { Text("Text eingeben…") },
            visualTransformation = WortMarkierung(
                woerter = document.overlongWords,
                farbe = MaterialTheme.colorScheme.error,
            ),
        )

        Hinweise(document, settings)

        StilLeiste(
            settings = settings,
            textLeer = text.isBlank(),
            absatzIndex = absatzIndex,
            stilIndex = stilIndex,
            onStilZuweisen = onStilZuweisen,
            onChange = onSettingsChange,
            onChangeLive = onSettingsChangeLive,
            onCommit = onSettingsCommit,
            onAutoFit = onAutoFit,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                // Der Ansichtsschalter steht ueber dem Bild und nicht daneben: unter der
                // Vorschau teilte er sich die Zeile mit den Kennzahlen, die mit der Textmenge
                // waechst - eins von beidem brach dann immer um. Als Overlay in der Bildecke
                // ginge es auch nicht, weil das Blatt je nach Format bis an den Rand reicht.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilterChip(
                        selected = showTravel,
                        onClick = { showTravel = !showTravel },
                        label = { Text("Leerfahrten", maxLines = 1, softWrap = false) },
                    )
                }
                PreviewCanvas(
                    strokes = document.laidOut?.strokes.orEmpty(),
                    blattbild = settings.toBlattbild(),
                    modifier = Modifier.height(200.dp),
                    showTravel = showTravel,
                )
                Text(
                    text = document.job?.let { job ->
                        val zeit = job.estimatedSeconds.roundToInt()
                        val dauer = if (zeit >= 60) {
                            "%d:%02d".format(Locale.GERMANY, zeit / 60, zeit % 60)
                        } else {
                            "$zeit s"
                        }
                        "%.0f mm · %d Hübe · ca. %s".format(
                            Locale.GERMANY, job.drawLengthMm, job.penDownCount, dauer,
                        )
                    } ?: "Noch kein Text",
                    style = MaterialTheme.typography.bodySmall,
                    // Volle Breite, seit der Schalter nicht mehr daneben sitzt.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        SendeBereich(machine, document, onPlot, onPlotViaSd, onStop)
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
    if (!settings.blattPasstAufTisch) {
        Warnung(
            "Das Blatt passt mit diesem Versatz nicht auf den Tisch " +
                "(${settings.workAreaXMm.fmt()} × ${settings.workAreaYMm.fmt()} mm).",
        )
    }
    if (!settings.rahmenPasstAufsBlatt) {
        Warnung(
            "Der Textrahmen ragt über das Blatt hinaus " +
                "(${settings.paperWidthMm.fmt()} × ${settings.paperHeightMm.fmt()} mm).",
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
private fun SendeBereich(
    machine: MachineUiState,
    document: DocumentState,
    onPlot: () -> Unit,
    onPlotViaSd: () -> Unit,
    onStop: () -> Unit,
) {
    val progress = machine.progress
    val laeuft = machine.busy && (progress is SendProgress.Running || progress is SendProgress.Started)
    val bereit = machine.connected && !machine.busy && document.job != null &&
        (document.job.penDownCount > 0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (laeuft && progress is SendProgress.Running) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (machine.sdLauf) {
                    // Ehrlich beschriften: der Wert kommt aus dem Lesefortschritt der Datei
                    // und eilt der Bewegung voraus - am Geraet nachgemessen.
                    "Läuft von SD-Karte, etwa ${(progress.fraction * 100).toInt()} % gelesen"
                } else {
                    "Zeile ${progress.ackedLines} von ${progress.totalLines}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlotViaSd,
                enabled = bereit,
                modifier = Modifier.weight(1f),
            ) {
                Text("Auf SD senden")
            }
            OutlinedButton(
                onClick = onPlot,
                enabled = bereit,
                modifier = Modifier.weight(1f),
            ) {
                Text("Direkt senden")
            }
            if (laeuft) {
                OutlinedButton(onClick = onStop) { Text("Not-Halt") }
            }
        }

        if (bereit) {
            Text(
                "Über SD läuft der Auftrag weiter, auch wenn die Verbindung abreißt. " +
                    "Direkt gesendet muss das Handy in Reichweite bleiben.",
                style = MaterialTheme.typography.bodySmall,
            )
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
