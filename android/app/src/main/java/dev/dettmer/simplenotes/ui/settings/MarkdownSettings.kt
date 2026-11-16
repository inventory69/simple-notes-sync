package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.ExportProblems
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.MarkdownExportProgress
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Markdown-Auto-Sync (Export + Import über `<sf>-md/`) mit dem Erst-Export beim Einschalten.
 * Bei aktivem E2EE fällt dieser Spiegel später ganz weg (E2EE-Plan, Slice 3).
 */
class MarkdownSettings(
    private val ctx: SettingsContext,
    private val isOffline: () -> Boolean
) {
    private val prefs = ctx.prefs
    private val ioDispatcher = Dispatchers.IO

    private val _markdownAutoSync = MutableStateFlow(readAutoSync())
    val markdownAutoSync: StateFlow<Boolean> = _markdownAutoSync.asStateFlow()

    private val _markdownExportProgress = MutableStateFlow<MarkdownExportProgress?>(null)
    val markdownExportProgress: StateFlow<MarkdownExportProgress?> = _markdownExportProgress.asStateFlow()

    /**
     * Einschalten setzt die Prefs sofort und exportiert die Bestandsnotizen "best effort";
     * ausschalten ist trivial.
     */
    fun setMarkdownAutoSync(enabled: Boolean) {
        if (!enabled) {
            _markdownAutoSync.value = false
            setPrefs(false)
            // 🆕 v2.19.0: Badge verschwindet beim Zurückkehren (refreshSyncStatus im Resume).
            ExportProblems.clearMarkdown(prefs)
            ctx.scope.launch { ctx.toast(ctx.string(R.string.toast_markdown_disabled)) }
            return
        }
        // 🆕 v1.10.0: Optimistic Update: Toggle springt sofort auf ON, der Dialog zeigt
        // "Verbindung wird geprüft...". Bei Fehler wird beides zurückgesetzt.
        _markdownAutoSync.value = true
        _markdownExportProgress.value = MarkdownExportProgress(0, 0, isChecking = true)
        ctx.scope.launch {
            try {
                enableAndExport()
            } catch (e: CancellationException) {
                // 🔧 v2.2.1 (Issue #50): Scope beendet (Activity weg). Prefs sind schon gesetzt,
                // das Feature funktioniert unabhängig vom Erst-Export.
                throw e
            } catch (e: Exception) {
                // 🔧 v2.2.1: Echter Fehler (z.B. Auth) → Feature aus, Prefs zurück.
                _markdownAutoSync.value = false
                _markdownExportProgress.value = null
                setPrefs(false)
                val userMessage = WebDavSyncService(ctx.app).mapSyncExceptionToMessage(e)
                ctx.toast(ctx.string(R.string.toast_export_failed, userMessage))
            }
        }
    }

    private suspend fun enableAndExport() {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
        val username = CredentialStore.getUsername(ctx.app).orEmpty()
        val password = CredentialStore.getPassword(ctx.app).orEmpty()
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _markdownAutoSync.value = false
            _markdownExportProgress.value = null
            ctx.toast(ctx.string(R.string.toast_configure_server_first))
            return
        }

        // 🔧 v2.2.1 (Issue #50): Prefs SOFORT setzen. NoteUploader liest KEY_MARKDOWN_EXPORT beim
        // on-save Sync; scheitert der Erst-Export (bewCloud 405, Timeout, Abbruch), muss der
        // reguläre Sync trotzdem .md-Dateien schreiben.
        setPrefs(true)

        // 🆕 v2.11.0: Zähl-Parität mit MarkdownSyncManager.exportAll (nicht getrasht, archivierte inklusive)
        val noteCount = withContext(ioDispatcher) { NotesStorage(ctx.app).loadNonTrashedNotes().size }
        if (noteCount == 0) {
            _markdownExportProgress.value = null
            ctx.toast(ctx.string(R.string.toast_markdown_enabled))
            return
        }

        val syncService = WebDavSyncService(ctx.app)
        // 🔧 v1.10.0: Schnell-Fail: ein Erreichbarkeits-Check statt N×Timeout beim Export.
        if (!withContext(ioDispatcher) { syncService.isServerReachable(userAction = true) }) {
            // 🔧 v2.2.1: Prefs bleiben an, der nächste erfolgreiche Sync exportiert.
            _markdownExportProgress.value = null
            ctx.toast(ctx.string(R.string.snackbar_server_unreachable))
            return
        }

        _markdownExportProgress.value = MarkdownExportProgress(0, noteCount)
        // 🆕 v1.10.0: Gesamt-Timeout: je Notiz 2× der konfigurierte Timeout plus Overhead.
        val perNoteTimeoutMs = prefs.getInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS)
            .coerceIn(Constants.MIN_CONNECTION_TIMEOUT_SECONDS, Constants.MAX_CONNECTION_TIMEOUT_SECONDS) * 2 * 1000L
        val totalTimeoutMs = (noteCount * perNoteTimeoutMs) + EXPORT_OVERHEAD_TIMEOUT_MS
        try {
            val exportedCount = withTimeout(totalTimeoutMs) {
                withContext(ioDispatcher) {
                    syncService.exportAllNotesToMarkdown(serverUrl, username, password) { current, total ->
                        _markdownExportProgress.value = MarkdownExportProgress(current, total)
                    }
                }
            }
            _markdownExportProgress.value = MarkdownExportProgress(noteCount, noteCount, isComplete = true)
            // 🔧 v2.2.1: 0 exportiert → Hinweis, Feature bleibt an; Bestand folgt beim nächsten Sync.
            ctx.toast(
                if (exportedCount > 0) {
                    ctx.string(R.string.toast_markdown_exported, exportedCount)
                } else {
                    ctx.string(R.string.toast_markdown_enabled)
                }
            )
        } catch (e: TimeoutCancellationException) {
            // 🔧 v2.2.1: Timeout → Feature bleibt aktiv (Prefs bereits gesetzt).
            Logger.w(TAG, "Markdown initial export timed out: ${e.message}")
            _markdownExportProgress.value = null
            ctx.toast(ctx.string(R.string.toast_export_timeout))
        }
        delay(PROGRESS_CLEAR_DELAY_MS)
        _markdownExportProgress.value = null
    }

    fun performManualMarkdownSync() {
        if (isOffline()) {
            Logger.d(TAG, "⏭️ Manual Markdown sync blocked: Offline mode enabled")
            return
        }
        ctx.scope.launch {
            try {
                ctx.toast(ctx.string(R.string.toast_markdown_syncing))
                val result = WebDavSyncService(ctx.app).manualMarkdownSync()
                ctx.toast(ctx.string(R.string.toast_markdown_result, result.exportedCount, result.importedCount))
            } catch (e: Exception) {
                ctx.toast(ctx.string(R.string.toast_error, e.message.orEmpty()))
            }
        }
    }

    fun reloadFromPrefs() {
        _markdownAutoSync.value = readAutoSync()
    }

    private fun readAutoSync() =
        prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false) && prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)

    private fun setPrefs(enabled: Boolean) {
        prefs.edit {
            putBoolean(Constants.KEY_MARKDOWN_EXPORT, enabled)
            putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, enabled)
        }
    }

    private companion object {
        const val TAG = "MarkdownSettings"
        const val PROGRESS_CLEAR_DELAY_MS = 500L

        // 🆕 v1.10.0: Overhead-Timeout für Markdown-Export (Ordner-Erstellung, Listing etc.)
        const val EXPORT_OVERHEAD_TIMEOUT_MS = 10_000L
    }
}
