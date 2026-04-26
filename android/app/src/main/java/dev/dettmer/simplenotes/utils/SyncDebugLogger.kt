package dev.dettmer.simplenotes.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.dettmer.simplenotes.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🆕 v2.2.0: Persistentes Sync-Trigger-Logging zur Diagnose von Hard-to-reproduce-Bugs.
 *
 * Schreibt **eine Zeile pro Trigger-Versuch** in eine separate Datei
 * (`sync_debug.log`, max 2 MB, 1 Backup `.1`). Append-only, überlebt App-Neustarts.
 *
 * **Niemals** Notizinhalte loggen — nur Metadaten (Trigger, Zeit, Netz, Fehler).
 *
 * Aktivierung: [Constants.KEY_SYNC_DEBUG_LOGGING] (SharedPreferences-Toggle, default
 * [BuildConfig.SYNC_DEBUG_LOGGING_DEFAULT] — `true` für die v2.2.0-Diagnose-Phase,
 * vor dem nächsten Release auf `false` setzen).
 */
object SyncDebugLogger {

    private const val FILE_NAME     = "sync_debug.log"
    private const val FILE_NAME_BAK = "sync_debug.log.1"
    private const val MAX_FILE_BYTES = 2L * 1024L * 1024L // 2 MB
    private const val TAG = "SyncDebugLogger"

    enum class Outcome { STARTED, SKIPPED, FAILED, SUCCESS, NO_CHANGES, RETRY, CANCELLED }

    data class NetworkSnapshot(
        val wifiConnected: Boolean,
        val internetCapable: Boolean,
        val validated: Boolean,
        val transport: String, // "WIFI" | "CELLULAR" | "ETHERNET" | "OTHER" | "NONE"
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
    private val fileLock = Any()

    @Volatile private var enabledOverride: Boolean? = null
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setEnabled(enabled: Boolean) {
        enabledOverride = enabled
    }

    private fun isEnabled(ctx: Context): Boolean {
        enabledOverride?.let { return it }
        val prefs = ctx.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.KEY_SYNC_DEBUG_LOGGING, BuildConfig.SYNC_DEBUG_LOGGING_DEFAULT)
    }

    /**
     * Single-Method-API — logt einen Trigger-Versuch in einer Zeile.
     *
     * @param triggerType z.B. "WIFI_CONNECT", "ON_RESUME", "PERIODIC", "ON_SAVE", "BOOT"
     * @param outcome     Ergebnis des Trigger-Versuchs
     * @param reason      Optionale Begründung (max 200 Zeichen, keine Notizinhalte!)
     * @param networkState Snapshot des aktuellen Netzwerkzustands
     */
    fun logTrigger(
        triggerType: String,
        outcome: Outcome,
        reason: String? = null,
        networkState: NetworkSnapshot? = null,
    ) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) return
        synchronized(fileLock) {
            try {
                val line = formatLine(triggerType, outcome, reason, networkState)
                val file = File(ctx.filesDir, FILE_NAME)
                FileWriter(file, true).use { it.write(line) }
                rotateIfNeeded(ctx, file)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to write sync_debug.log: ${e.message}")
            }
        }
    }

    /**
     * Erstellt einen Snapshot des aktuellen Netzwerkzustands.
     * Enthält **keine** SSID, BSSID oder IP-Adressen — nur bool-Flags und Transport-Typ.
     */
    fun snapshotNetwork(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return NetworkSnapshot(false, false, false, "NONE")
        val caps = cm.getNetworkCapabilities(net)
            ?: return NetworkSnapshot(false, false, false, "NONE")
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        return NetworkSnapshot(
            wifiConnected   = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            internetCapable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated       = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            transport       = transport,
        )
    }

    /** Gibt die Log-Datei zurück, oder `null` wenn sie nicht existiert. */
    fun getLogFile(context: Context): File? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f else null
    }

    // ───────── private helpers ─────────

    private fun formatLine(
        triggerType: String,
        outcome: Outcome,
        reason: String?,
        networkState: NetworkSnapshot?,
    ): String {
        val ts = dateFormat.format(Date())
        val net = if (networkState != null) {
            "${networkState.transport}/${networkState.wifiConnected}" +
                "/${networkState.internetCapable}/${networkState.validated}"
        } else {
            "-"
        }
        val sanitizedReason = reason
            ?.replace('\t', ' ')
            ?.replace('\n', ' ')
            ?.take(200)
            ?: "-"
        return "$ts\tTRIGGER=$triggerType\tOUTCOME=$outcome\tNET=$net\tREASON=$sanitizedReason\n"
    }

    private fun rotateIfNeeded(ctx: Context, file: File) {
        try {
            if (file.length() > MAX_FILE_BYTES) {
                val backup = File(ctx.filesDir, FILE_NAME_BAK)
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to rotate sync_debug.log: ${e.message}")
        }
    }
}
