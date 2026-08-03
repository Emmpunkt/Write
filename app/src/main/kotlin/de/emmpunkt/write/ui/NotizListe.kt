package de.emmpunkt.write.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import de.emmpunkt.write.data.NoteEntity
import de.emmpunkt.write.data.titelVon
import java.text.DateFormat
import java.util.Date

/**
 * Die aufgeklappte Notizliste.
 *
 * Geloescht wird ueber ein Symbol mit Rueckfrage und bewusst NICHT ueber eine Wischgeste: in
 * einer Liste, die man zum Umschalten antippt, sitzt Wischen zu nah an der Auswahl - und ohne
 * Papierkorb ist ein versehentlich geloeschter Text weg.
 */
@Composable
fun NotizListe(
    notizen: List<NoteEntity>,
    aktuelleId: Long,
    onOeffnen: (Long) -> Unit,
    onLoeschen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var loeschKandidat by remember { mutableStateOf<NoteEntity?>(null) }

    LazyColumn(
        // Begrenzt, damit die Liste bei vielen Notizen nicht den ganzen Editor verdraengt.
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp),
    ) {
        items(notizen, key = { it.id }) { notiz ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clickable { onOeffnen(notiz.id) },
                colors = if (notiz.id == aktuelleId) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            titelVon(notiz.text),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(notiz.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { loeschKandidat = notiz }) {
                        Icon(Icons.Default.Delete, contentDescription = "Notiz löschen")
                    }
                }
            }
        }
    }

    loeschKandidat?.let { notiz ->
        AlertDialog(
            onDismissRequest = { loeschKandidat = null },
            title = { Text("Notiz löschen?") },
            text = {
                Text(
                    "„${titelVon(notiz.text)}“ wird gelöscht. " +
                        "Das lässt sich nicht rückgängig machen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onLoeschen(notiz.id)
                    loeschKandidat = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { loeschKandidat = null }) { Text("Abbrechen") }
            },
        )
    }
}
