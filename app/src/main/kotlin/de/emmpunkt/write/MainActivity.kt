package de.emmpunkt.write

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.emmpunkt.write.ui.EditorScreen
import de.emmpunkt.write.ui.MachineScreen
import de.emmpunkt.write.ui.PlotterViewModel
import de.emmpunkt.write.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WriteApp() }
    }
}

private enum class Reiter(val titel: String, val symbol: ImageVector) {
    EDITOR("Notiz", Icons.Default.Edit),
    MASCHINE("Maschine", Icons.Default.Tune),
    EINSTELLUNGEN("Einstellungen", Icons.Default.Settings),
}

@Composable
fun WriteApp(viewModel: PlotterViewModel = viewModel()) {
    val context = LocalContext.current
    val farben = remember {
        runCatching { dynamicLightColorScheme(context) }.getOrElse { lightColorScheme() }
    }

    MaterialTheme(colorScheme = farben) {
        var reiter by rememberSaveable { mutableIntStateOf(0) }
        val snackbar = remember { SnackbarHostState() }

        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val text by viewModel.text.collectAsStateWithLifecycle()
        val document by viewModel.document.collectAsStateWithLifecycle()
        val machine by viewModel.machine.collectAsStateWithLifecycle()
        val notizen by viewModel.notizen.collectAsStateWithLifecycle()
        val aktuelleNotizId by viewModel.aktuelleNotizId.collectAsStateWithLifecycle()

        // Bildschirm anlassen, solange ein Auftrag laeuft. Das ist die wirksamste Massnahme
        // gegen abbrechende Verbindungen: bei wachem Bildschirm drosselt Android das WLAN
        // gar nicht erst. Die Sperren in PlotWakeLock sichern den Rest ab.
        val view = LocalView.current
        DisposableEffect(machine.busy) {
            view.keepScreenOn = machine.busy
            onDispose { view.keepScreenOn = false }
        }

        // Rueckmeldungen der Maschine erscheinen unabhaengig vom gerade sichtbaren Reiter.
        LaunchedEffect(machine.message) {
            machine.message?.let {
                snackbar.showSnackbar(it)
                viewModel.dismissMessage()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Reiter.entries.forEachIndexed { index, eintrag ->
                        NavigationBarItem(
                            selected = reiter == index,
                            onClick = { reiter = index },
                            icon = { Icon(eintrag.symbol, contentDescription = eintrag.titel) },
                            label = { Text(eintrag.titel) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            when (Reiter.entries[reiter]) {
                Reiter.EDITOR -> EditorScreen(
                    text = text,
                    settings = settings,
                    document = document,
                    machine = machine,
                    onTextChange = viewModel::onTextChanged,
                    onSettingsChange = viewModel::updateSettings,
                    onSettingsChangeLive = viewModel::updateSettingsLive,
                    onSettingsCommit = viewModel::commitSettings,
                    onAutoFit = viewModel::autoFit,
                    onPlot = viewModel::plot,
                    onPlotViaSd = viewModel::plotViaSd,
                    onStop = viewModel::cancelPlot,
                    notizen = notizen,
                    aktuelleNotizId = aktuelleNotizId,
                    onNotizOeffnen = viewModel::notizOeffnen,
                    onNotizAnlegen = viewModel::notizAnlegen,
                    onNotizLoeschen = viewModel::notizLoeschen,
                    modifier = Modifier.padding(innerPadding),
                )

                Reiter.MASCHINE -> MachineScreen(
                    settings = settings,
                    machine = machine,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onHome = viewModel::home,
                    onZeroXY = viewModel::zeroXY,
                    onZeroZ = viewModel::zeroZ,
                    onUnlock = viewModel::unlock,
                    onJog = viewModel::jog,
                    onStop = viewModel::emergencyStop,
                    modifier = Modifier.padding(innerPadding),
                )

                Reiter.EINSTELLUNGEN -> SettingsScreen(
                    settings = settings,
                    onChange = viewModel::updateSettings,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
