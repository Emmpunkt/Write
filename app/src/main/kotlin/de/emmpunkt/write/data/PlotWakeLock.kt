package de.emmpunkt.write.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Haelt Prozessor und WLAN wach, solange ein Auftrag laeuft.
 *
 * Eine volle Notiz sind schnell ueber tausend Zeilen und mehrere Minuten. Sperrt sich in der
 * Zeit der Bildschirm, drosselt Android das WLAN und schlaefert Hintergrundarbeit ein - die
 * Verbindung zum Plotter risse mitten im Text ab, mit aufliegendem Stift.
 *
 * Zwei Sperren, weil sie Verschiedenes tun: die Wake-Sperre haelt den Prozessor am Laufen,
 * die WLAN-Sperre verhindert, dass das Funkmodul in den Stromsparmodus geht. Ohne die zweite
 * liefe die App zwar weiter, bekaeme aber keine Quittungen mehr.
 *
 * Der Bildschirm bleibt zusaetzlich an (siehe MainActivity) - das ist die wirksamste
 * Massnahme, denn bei wachem Bildschirm drosselt Android gar nicht erst.
 */
class PlotWakeLock(context: Context) {

    private val appContext = context.applicationContext

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /** Nimmt beide Sperren. Mehrfaches Aufrufen ist unschaedlich. */
    fun acquire() {
        if (wakeLock?.isHeld == true) return

        runCatching {
            val power = appContext.getSystemService(PowerManager::class.java)
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)?.apply {
                setReferenceCounted(false)
                // Zeitgrenze als Notbremse: bliebe die Sperre nach einem Absturz haengen,
                // liefe sonst der Akku leer.
                acquire(MAX_HOLD_MS)
            }
        }

        runCatching {
            val wifi = appContext.getSystemService(WifiManager::class.java)
            wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    /** Gibt beide Sperren wieder frei. Muss auch im Fehlerfall aufgerufen werden. */
    fun release() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private companion object {
        const val TAG = "Write:Plot"

        /** Grosszuegig ueber jedem realistischen Auftrag, aber nicht unbegrenzt. */
        const val MAX_HOLD_MS = 30 * 60 * 1000L
    }
}
