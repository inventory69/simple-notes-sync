package dev.dettmer.simplenotes.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.noteimport.NotesImportWizard
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictStrategy
import dev.dettmer.simplenotes.security.AppLock
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.CertificateInfo
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ViewModel des Einstellungs-Graphen und Kompositionspunkt der Domänen (Refactoring-Slice 1):
 * [server], [connection], [sync], [markdown], [backup], [debug], [appearance], [editor]. Jeder Screen greift
 * auf seine Domäne zu; hier bleiben nur die gemeinsamen Kanäle (Snackbar, Events), App-Sperre und
 * der Import-Assistent.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val notesStorage = NotesStorage(application)

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val _showSnackbar = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val showSnackbar: SharedFlow<String> = _showSnackbar.asSharedFlow()

    private val ctx = SettingsContext(application, viewModelScope, prefs, _showSnackbar, _events)

    val server: ServerSettings = ServerSettings(ctx) { connection.checkServerStatus() }
    val connection: ServerConnection = ServerConnection(ctx, server::currentFullServerUrl) { server.offlineMode.value }
    val sync = SyncSettings(ctx)
    val markdown = MarkdownSettings(ctx) { server.offlineMode.value }
    val backup = BackupSettings(ctx, onSettingsRestored = ::reloadFromPrefs)
    val debug = DebugSettings(ctx)
    val appearance = AppearanceSettings(ctx)
    val editor = EditorSettings(ctx)

    // v2.10.0: App lock
    private val _appLockEnabled = MutableStateFlow(AppLock.isEnabled(application))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appLockGraceMs = MutableStateFlow(AppLock.graceMillis(application))
    val appLockGraceMs: StateFlow<Long> = _appLockGraceMs.asStateFlow()

    fun setAppLockEnabled(on: Boolean) {
        AppLock.setEnabled(getApplication(), on)
        _appLockEnabled.value = on
    }

    fun setAppLockGraceMs(ms: Long) {
        AppLock.setGraceMillis(getApplication(), ms)
        _appLockGraceMs.value = ms
    }

    /** Zeigt eine Snackbar über den SettingsNavHost-Collector an, auch aus Click-Handlern. */
    fun showSnackbar(message: String) = ctx.showSnackbar(message)

    /** v1.9.0: Nach einem Restore mit App-Einstellungen alle Domänen aus den Prefs neu lesen. */
    private fun reloadFromPrefs() {
        server.reloadFromPrefs()
        connection.reloadFromPrefs()
        sync.reloadFromPrefs()
        markdown.reloadFromPrefs()
        appearance.reloadFromPrefs()
        editor.reloadFromPrefs()
        Logger.d(TAG, "🔄 App settings reloaded from prefs after backup restore")
    }

    // ─── 🆕 Issue #21: Notes Import Wizard ─────────────────────────────────

    /** Scannt den Basis-Ordner des Servers (NICHT /notes/) nach importierbaren Dateien. */
    suspend fun scanWebDavForImport(): List<NotesImportWizard.ImportCandidate> = withContext(Dispatchers.IO) {
        try {
            val syncService = WebDavSyncService(getApplication())
            val webdav = syncService.getOrCreateWebDavClient() ?: return@withContext emptyList()
            val serverUrl = syncService.getServerUrl() ?: return@withContext emptyList()
            NotesImportWizard(notesStorage, getApplication()).scanWebDavFolder(webdav, serverUrl)
        } catch (e: Exception) {
            Logger.e(TAG, "Import scan failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun importCandidates(
        candidates: List<NotesImportWizard.ImportCandidate>,
        strategy: ConflictStrategy = ConflictStrategy.SKIP
    ): NotesImportWizard.ImportSummary = withContext(Dispatchers.IO) {
        NotesImportWizard(notesStorage, getApplication()).importFiles(candidates, strategy)
    }

    /** Importiert lokale Dateien via Android Content-URI. */
    suspend fun importLocalFiles(
        uris: List<Uri>,
        strategy: ConflictStrategy = ConflictStrategy.SKIP
    ): NotesImportWizard.ImportSummary = withContext(Dispatchers.IO) {
        val wizard = NotesImportWizard(notesStorage, getApplication())
        val candidates = uris.mapNotNull { uri ->
            try {
                val name = queryColumn(uri, OpenableColumns.DISPLAY_NAME) { getString(it) } ?: "unknown.txt"
                NotesImportWizard.ImportCandidate(
                    name = name,
                    source = NotesImportWizard.ImportSource.LocalFile(uri),
                    size = queryColumn(uri, OpenableColumns.SIZE) { getLong(it) } ?: 0L,
                    modified = System.currentTimeMillis(),
                    fileType = wizard.detectFileType(name)
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Cannot process URI $uri: ${e.message}")
                null
            }
        }
        wizard.importFiles(candidates, strategy)
    }

    private fun <T> queryColumn(uri: Uri, column: String, read: android.database.Cursor.(Int) -> T): T? =
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use {
            val index = it.getColumnIndex(column)
            if (it.moveToFirst() && index >= 0) it.read(index) else null
        }

    // ─── Typen der Screens ───────────────────────────────────────────────────

    /** Ergebnis einer Backup-/Restore-Aktion für die persistente Ergebniskarte. */
    data class BackupOutcome(val isSuccess: Boolean, val title: String, val detail: String?)

    sealed class ServerStatus {
        data object Unknown : ServerStatus()

        data object OfflineMode : ServerStatus() // 🌟 v1.6.0

        data object NotConfigured : ServerStatus()

        data object Checking : ServerStatus()

        data object Reachable : ServerStatus()

        data class Unreachable(val error: String?) : ServerStatus()

        data object CertificateProblem : ServerStatus() // 🆕 v2.21.0: antwortet, Zertifikat scheitert
    }

    /** Aktionen, die die Activity ausführen muss. */
    sealed class SettingsEvent {
        data object RestartNetworkMonitor : SettingsEvent()
    }

    /** v1.5.0: Fortschritt des Markdown-Erst-Exports. */
    data class MarkdownExportProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean = false,
        val isChecking: Boolean = false // 🆕 v1.10.0: Server-Check-Phase (indeterminate)
    )

    enum class RemoteChangeKind { FOLDER, SERVER, BOTH }

    data class FolderChangePrompt(
        val kind: RemoteChangeKind,
        val oldLabel: String,
        val newLabel: String,
        val unsyncedCount: Int,
        val localOnlyCount: Int
    )

    /** 🆕 v2.21.0: Zertifikat aus dem Verbindungstest. [replacesTrusted] = dieser Host hatte schon einen Pin. */
    data class CertificatePrompt(val certificate: CertificateInfo, val replacesTrusted: Boolean)

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
