package de.emmpunkt.write.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emmpunkt.write.data.AppSettings
import de.emmpunkt.write.machine.Axis
import de.emmpunkt.write.machine.MachineState
import de.emmpunkt.write.machine.Position
import java.util.Locale

@Composable
fun MachineScreen(
    settings: AppSettings,
    machine: MachineUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onHome: () -> Unit,
    onZeroXY: () -> Unit,
    onZeroZ: () -> Unit,
    onUnlock: () -> Unit,
    onJog: (Axis, Float) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var schritt by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Zustandskarte(settings, machine)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (machine.connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                    Text("Trennen")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !machine.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Verbinden")
                }
            }
            OutlinedButton(
                onClick = onUnlock,
                enabled = machine.connected && !machine.busy,
            ) {
                Text("Entsperren")
            }
        }

        Text("Schrittweite", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val schritte = listOf(0.1f, 1f, 10f)
            schritte.forEachIndexed { index, wert ->
                SegmentedButton(
                    selected = schritt == wert,
                    onClick = { schritt = wert },
                    shape = SegmentedButtonDefaults.itemShape(index, schritte.size),
                ) {
                    Text("${wert.fmt()} mm")
                }
            }
        }

        JogKreuz(
            enabled = machine.connected && !machine.busy,
            schritt = schritt,
            onJog = onJog,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onHome,
                enabled = machine.connected && !machine.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Homing X/Y")
            }
            FilledTonalButton(
                onClick = onZeroXY,
                enabled = machine.connected && !machine.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("X/Y nullen")
            }
        }

        // Z hat keinen Endschalter und bleibt bei der Referenzfahrt aussen vor. Ihr Nullpunkt
        // entsteht nur hier - und ist nach einem Neustart der Steuerung wieder weg.
        FilledTonalButton(
            onClick = onZeroZ,
            enabled = machine.connected && !machine.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Z hier nullen (Stift steht auf dem Papier)")
        }
        Text(
            "Die Z-Achse hat keinen Endschalter und wird nicht referenziert. Setze den " +
                "Nullpunkt auf die Papierebene – nach einem Neustart der Steuerung erneut.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Immer erreichbar, auch waehrend eines Auftrags - deshalb steht er nicht hinter
        // einer Bedingung.
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("NOT-HALT")
        }
    }
}

@Composable
private fun Zustandskarte(settings: AppSettings, machine: MachineUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .then(Modifier),
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = when {
                                !machine.connected -> androidx.compose.ui.graphics.Color.Gray
                                machine.status.state == MachineState.ALARM ->
                                    androidx.compose.ui.graphics.Color(0xFFD32F2F)
                                machine.status.state == MachineState.IDLE ->
                                    androidx.compose.ui.graphics.Color(0xFF388E3C)
                                else -> androidx.compose.ui.graphics.Color(0xFFF9A825)
                            },
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (machine.connected) {
                        "${settings.host} · ${machine.status.state}"
                    } else {
                        "Nicht verbunden (${settings.host})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Die Arbeitsposition bezieht sich auf den Nullpunkt des Nutzers - deshalb steht
            // sie zuerst. Sie ist nur bekannt, wenn der Versatz per $# gelesen werden konnte.
            if (machine.status.work != null || machine.status.machine != null) {
                PositionsTabelle(machine)
            }

            Text(
                text = if (machine.homed) "Referenziert" else "Noch nicht referenziert",
                style = MaterialTheme.typography.bodySmall,
                color = if (machine.homed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                "Arbeitsbereich ${settings.workAreaXMm.fmt()} × ${settings.workAreaYMm.fmt()} mm",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Fahrkreuz fuer X und Y plus eine getrennte Z-Spalte.
 *
 * Y-Plus liegt oben, X-Plus rechts - so, wie sich der Stift auf dem Blatt bewegt. Die
 * Anordnung folgt bewusst dem Blatt und nicht der Reihenfolge der Achsen.
 */
@Composable
private fun JogKreuz(enabled: Boolean, schritt: Float, onJog: (Axis, Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(2f),
        ) {
            JogTaste("Y +", enabled) { onJog(Axis.Y, schritt) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JogTaste("X −", enabled) { onJog(Axis.X, -schritt) }
                JogTaste("X +", enabled) { onJog(Axis.X, schritt) }
            }
            JogTaste("Y −", enabled) { onJog(Axis.Y, -schritt) }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            JogTaste("Z +", enabled) { onJog(Axis.Z, schritt) }
            Text("Stift", style = MaterialTheme.typography.labelSmall)
            JogTaste("Z −", enabled) { onJog(Axis.Z, -schritt) }
        }
    }
}

@Composable
private fun JogTaste(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Positionen als Spaltentabelle.
 *
 * Bewusst mit Gewichtungen statt einer Monospace-Zeile mit fester Zeichenbreite: bei
 * vergroesserter Systemschrift bricht eine solche Zeile um und wird unlesbar.
 */
@Composable
private fun PositionsTabelle(machine: MachineUiState) {
    @Composable
    fun zeile(titel: String, p: Position?, hervorgehoben: Boolean) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val stil = if (hervorgehoben) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            }
            Text(titel, style = stil, modifier = Modifier.weight(1.1f))
            listOf(p?.x, p?.y, p?.z).forEach { wert ->
                Text(
                    text = wert?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "–",
                    style = stil,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1.1f))
        listOf("X", "Y", "Z").forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
    zeile("Arbeit", machine.status.work, hervorgehoben = true)
    zeile("Maschine", machine.status.machine, hervorgehoben = false)
}
