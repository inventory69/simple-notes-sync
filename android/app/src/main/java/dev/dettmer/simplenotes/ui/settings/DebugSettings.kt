package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.LogAnonymizer
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.SyncDebugLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ponytail: file-level flag, process-scoped, resets on process death
// 🆕 v2.14.0: In Beta-Builds von Anfang an offen: Tester sollen für einen Sync-Log nicht
// erst das Easter-Egg finden müssen. Release-Builds bleiben beim 5-Tap-Gate.
private var devOptionsUnlocked = BuildConfig.BETA_BUILD

/** Debug-Screen und Entwickleroptionen: Logs, Log-Export, Caches leeren. */
class DebugSettings(private val ctx: SettingsContext) {
    private val prefs = ctx.prefs

    private val _fileLoggingEnabled = MutableStateFlow(prefs.getBoolean(Constants.KEY_FILE_LOGGING_ENABLED, BuildConfig.BETA_BUILD))
    val fileLoggingEnabled: StateFlow<Boolean> = _fileLoggingEnabled.asStateFlow()

    // 🆕 v2.2.0
    private val _syncDebugLoggingEnabled = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_DEBUG_LOGGING, BuildConfig.SYNC_DEBUG_LOGGING_DEFAULT)
    )
    val syncDebugLoggingEnabled: StateFlow<Boolean> = _syncDebugLoggingEnabled.asStateFlow()

    // 🔧 v1.11.0: Easter-Egg, nur für die Sitzung
    private val _developerOptionsUnlocked = MutableStateFlow(devOptionsUnlocked)
    val developerOptionsUnlocked: StateFlow<Boolean> = _developerOptionsUnlocked.asStateFlow()

    /** 5× Tippen auf den Banner im Über-Screen. Nur im Speicher. */
    fun unlockDeveloperOptions() {
        devOptionsUnlocked = true
        _developerOptionsUnlocked.value = true
    }

    fun setFileLogging(enabled: Boolean) {
        prefs.edit { putBoolean(Constants.KEY_FILE_LOGGING_ENABLED, enabled) }
        _fileLoggingEnabled.value = enabled
        Logger.setFileLoggingEnabled(enabled)
        ctx.scope.launch {
            ctx.toast(ctx.string(if (enabled) R.string.toast_file_logging_enabled else R.string.toast_file_logging_disabled))
        }
    }

    fun setSyncDebugLogging(enabled: Boolean) {
        _syncDebugLoggingEnabled.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_DEBUG_LOGGING, enabled) }
        SyncDebugLogger.setEnabled(enabled)
    }

    fun clearLogs() {
        ctx.scope.launch {
            try {
                Logger.clearLogFile(ctx.app)
                // 🆕 v2.4.0: auch das persistente Sync-Debug-Log: der Button verspricht "Logs löschen".
                SyncDebugLogger.clearLog(ctx.app)
                ctx.toast(ctx.string(R.string.toast_logs_deleted))
            } catch (e: Exception) {
                ctx.toast(ctx.string(R.string.toast_error, e.message.orEmpty()))
            }
        }
    }

    /**
     * 🆕 v2.14.0: Anonymisierte Kopien der Logdateien für den Export.
     *
     * Geteilt wird nie die Originaldatei. Die Kopien liegen im Cache (`shared_logs/`) und tragen
     * sprechende Namen mit Datum (`simplenotes-app-2026-07-26.txt`); `.txt`, weil Mail-Clients
     * Textdateien inline zeigen. Was ersetzt wird, steht in [LogAnonymizer].
     *
     * @return teilbare Dateien, leer wenn es nichts zu exportieren gibt oder das Schreiben scheitert.
     */
    suspend fun prepareLogsForSharing(): List<File> = withContext(Dispatchers.IO) {
        val context = ctx.app
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sources = listOfNotNull(
            Logger.getLogFile(context)?.let { it to "simplenotes-app-$stamp.txt" },
            SyncDebugLogger.getLogFile(context)?.let { it to "simplenotes-sync-$stamp.txt" }
        ).filter { (source, _) -> source.exists() && source.length() > 0L }
        if (sources.isEmpty()) return@withContext emptyList()

        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        val username = CredentialStore.getUsername(context)
        // Scheitert das Laden, wird trotzdem exportiert: nur ohne Titel-Ersetzung.
        val titles = runCatching { NotesStorage(context).loadAllNotes().map { it.title } }
            .onFailure { Logger.w(TAG, "could not load note titles for anonymization: ${it.message}") }
            .getOrDefault(emptyList())
        // Inkl. Tombstones: gelöschte Ordner stehen noch in älteren Logzeilen.
        val folders = runCatching { FolderStore(context).loadMeta().map { it.name } }
            .onFailure { Logger.w(TAG, "could not load folder names for anonymization: ${it.message}") }
            .getOrDefault(emptyList())
        val targetDir = File(context.cacheDir, "shared_logs").apply { mkdirs() }
        // Ältere Kopien mit anderem Datum weg, sonst sammeln sie sich unbegrenzt im Cache.
        targetDir.listFiles()?.forEach { it.delete() }

        sources.mapNotNull { (source, targetName) ->
            runCatching {
                File(targetDir, targetName).apply {
                    writeText(LogAnonymizer.anonymize(source.readText(), serverUrl, username, titles, folders))
                }
            }.onFailure {
                Logger.w(TAG, "could not anonymize ${source.name} for sharing: ${it.message}")
            }.getOrNull()
        }
    }

    /** v1.8.0: Changelog beim nächsten Start erneut zeigen (zum Testen). */
    fun resetChangelogVersion() {
        prefs.edit { putInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, 0) }
    }

    fun clearETagCache() {
        prefs.edit {
            prefs.all.keys.filter {
                it.startsWith("etag_json_") ||
                    it.startsWith("etag_md_") ||
                    it.startsWith("content_hash_") ||
                    it.startsWith("content_hash_md_")
            }.forEach { key -> remove(key) }
        }
        Logger.d(TAG, "🧹 [DEBUG] Cleared E-Tag and content hash cache")
        ctx.showSnackbar(ctx.string(R.string.debug_etag_cache_cleared))
    }

    private companion object {
        const val TAG = "DebugSettings"
    }
}
