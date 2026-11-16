package dev.dettmer.simplenotes.ui.settings

import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncUrlBuilder
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.FolderChangePrompt
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.RemoteChangeKind
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Server-Screen: Adresse, Zugangsdaten, Sync-Ordner, Offline-Modus, Verbindungstest und das
 * Bestätigen eines Wechsels des Remote-Ziels (Server und/oder Ordner).
 *
 * Eingaben gehen sofort in die Prefs ([WebDavSyncService] liest sie dort), das Remote-Ziel gilt aber
 * erst nach [requestRemoteChangeDecision] und einer Entscheidung im Dialog als bestätigt.
 * Verbindungstest und Status liegen in [ServerConnection].
 */
class ServerSettings(
    private val ctx: SettingsContext,
    private val onOfflineModeChanged: () -> Unit
) {
    private val prefs = ctx.prefs
    private val ioDispatcher = Dispatchers.IO
    private val notesStorage = NotesStorage(ctx.app)

    // 🔧 v1.7.0 / 🆕 v2.12.0: Zuletzt bestätigtes Remote-Ziel. Verhindert falsche "Server
    // geändert"-Meldungen während des Tippens; remoteTargetChangePending beobachtet es.
    private val confirmedServerUrl = MutableStateFlow(prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty())
    private val confirmedSyncFolderName = MutableStateFlow(readSyncFolderName())

    private val storedUrl = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()

    // 🌟 v1.6.0: Protokoll und Host getrennt; editierbar ist nur der Host.
    private val _isHttps = MutableStateFlow(storedUrl.startsWith("https://"))
    val isHttps: StateFlow<Boolean> = _isHttps.asStateFlow()

    private val _serverHost = MutableStateFlow(extractHostFromUrl(storedUrl))
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    val serverUrl: StateFlow<String> = combine(_isHttps, _serverHost) { https, host ->
        fullUrl(https, host)
    }.stateIn(ctx.scope, SharingStarted.Eagerly, storedUrl)

    private val _username = MutableStateFlow(CredentialStore.getUsername(ctx.app).orEmpty())
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(CredentialStore.getPassword(ctx.app).orEmpty())
    val password: StateFlow<String> = _password.asStateFlow()

    /**
     * 🆕 v2.17.0: Der KeyStore konnte die Zugangsdaten nicht verschlüsseln, sie liegen im Klartext
     * in einer (backup-ausgeschlossenen) Datei. Treibt die dauerhafte Warnzeile im Server-Screen.
     */
    private val _credentialsUnencrypted = MutableStateFlow(CredentialStore.isStoredUnencrypted(ctx.app))
    val credentialsUnencrypted: StateFlow<Boolean> = _credentialsUnencrypted.asStateFlow()

    // 🌟 v1.6.0: Default an für neue Nutzer (kein Server), aus für bestehende (Server konfiguriert)
    private val _offlineMode = MutableStateFlow(
        if (prefs.contains(Constants.KEY_OFFLINE_MODE)) {
            prefs.getBoolean(Constants.KEY_OFFLINE_MODE, Constants.DEFAULT_OFFLINE_MODE)
        } else {
            !hasExistingServerConfig()
        }
    )
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    val isServerConfigured: StateFlow<Boolean> = combine(offlineMode, serverUrl) { offline, url ->
        !offline && url.isNotEmpty()
    }.stateIn(ctx.scope, SharingStarted.Eagerly, initialValue = !_offlineMode.value && serverUrl.value.isNotEmpty())

    // 🆕 v1.9.0: Configurable WebDAV Sync Folder
    private val _syncFolderName = MutableStateFlow(readSyncFolderName())
    val syncFolderName: StateFlow<String> = _syncFolderName.asStateFlow()

    /**
     * Markdown-Ordner für die Anzeige, mit derselben Logik wie der Sync. Vorher selbst
     * zusammengesetzt: `dav//notes-md` bei Slash am Ende, `notes/notes-md` bei URL auf den Ordner.
     * Der Ordnername kommt aus den Prefs, `_syncFolderName` löst nur die Neuberechnung aus.
     */
    val markdownFolderUrl: StateFlow<String> = combine(serverUrl, _syncFolderName) { url, _ ->
        SyncUrlBuilder(prefs).getMarkdownUrl(url)
    }.stateIn(ctx.scope, SharingStarted.Eagerly, SyncUrlBuilder(prefs).getMarkdownUrl(storedUrl))

    private val _showBatteryOptimizationDialog = MutableStateFlow(false)
    val showBatteryOptimizationDialog: StateFlow<Boolean> = _showBatteryOptimizationDialog.asStateFlow()

    // 🆕 v2.12.0: Remote-Ziel (Ordner + Server) geändert? Bestätigungsdialog per Zurück-Intercept.
    // serverChanged nur „echt" bei bestehender Verbindung (siehe isServerReallyChanged).
    val remoteTargetChangePending: StateFlow<Boolean> = combine(
        _syncFolderName,
        confirmedSyncFolderName,
        _serverHost,
        _isHttps,
        confirmedServerUrl
    ) { folder, confirmedFolder, host, https, confirmedServer ->
        // Ohne je bestätigte Verbindung gibt es kein Remote-Notizset, von dem man wechseln könnte.
        val folderChanged = confirmedServer.isNotEmpty() &&
            folder.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME } != confirmedFolder
        folderChanged || isServerReallyChanged(confirmedServer, fullUrl(https, host))
    }.stateIn(ctx.scope, SharingStarted.Eagerly, false)

    private val _folderChangePrompt = MutableStateFlow<FolderChangePrompt?>(null)
    val folderChangePrompt: StateFlow<FolderChangePrompt?> = _folderChangePrompt.asStateFlow()

    // 🆕 v2.11.0: Ordnerwechsel läuft noch: Dialog bleibt offen, Navigation wartet auf Completion
    private val _folderChangeInProgress = MutableStateFlow(false)
    val folderChangeInProgress: StateFlow<Boolean> = _folderChangeInProgress.asStateFlow()

    private val _folderChangeCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val folderChangeCompleted: SharedFlow<Unit> = _folderChangeCompleted.asSharedFlow()

    /** v1.6.0: Offline-Modus schaltet alle Netzwerkfunktionen ab. */
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit { putBoolean(Constants.KEY_OFFLINE_MODE, enabled) }
        _offlineMode.value = enabled
        onOfflineModeChanged()
        // 🆕 v2.3.0: Beim Verlassen des Offline-Modus nach der Akku-Optimierung fragen.
        if (!enabled) checkAndPromptBatteryOptimization()
    }

    fun dismissBatteryOptimizationDialog() {
        _showBatteryOptimizationDialog.value = false
    }

    private fun checkAndPromptBatteryOptimization() {
        val powerManager = ctx.app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isIgnoringBatteryOptimizations(ctx.app.packageName)) {
            _showBatteryOptimizationDialog.value = true
        }
    }

    // 🔧 v1.7.0: Sofort in die Prefs (für WebDavSyncService), Wechsel-Erkennung erst beim Verlassen.
    fun updateServerHost(host: String) {
        _serverHost.value = host
        prefs.edit { putString(Constants.KEY_SERVER_URL, currentFullServerUrl()) }
    }

    fun updateProtocol(useHttps: Boolean) {
        _isHttps.value = useHttps
        prefs.edit { putString(Constants.KEY_SERVER_URL, currentFullServerUrl()) }
    }

    fun updateUsername(value: String) {
        _username.value = value
        reportCredentialStorage(CredentialStore.setCredentials(ctx.app, value, _password.value))
    }

    fun updatePassword(value: String) {
        _password.value = value
        reportCredentialStorage(CredentialStore.setCredentials(ctx.app, _username.value, value))
    }

    /**
     * 🆕 v2.17.0: Klartext-Downgrade sichtbar machen. Der Nutzer tippt in diesem Moment gerade im
     * Server-Screen: die Snackbar erreicht ihn dort, die Warnzeile bleibt danach stehen.
     */
    private fun reportCredentialStorage(encrypted: Boolean) {
        if (_credentialsUnencrypted.value == !encrypted) return
        _credentialsUnencrypted.value = !encrypted
        if (!encrypted) ctx.showSnackbar(ctx.string(R.string.server_credentials_unencrypted_snackbar))
    }

    fun updateSyncFolderName(name: String) {
        val sanitized = name.replace(Regex("[^a-zA-Z0-9_-]"), "").take(Constants.MAX_SYNC_FOLDER_NAME_LENGTH)
        _syncFolderName.value = sanitized
        prefs.edit { putString(Constants.KEY_SYNC_FOLDER_NAME, sanitized.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME }) }
    }

    /**
     * Zurück-Intercept auf `ServerSettingsScreen`: ermittelt, was sich geändert hat (Ordner,
     * Server oder beides), zählt unwiederbringliche Notizen (PENDING/LOCAL_ONLY: nur die können
     * bei "Nicht mitnehmen" verloren gehen) und öffnet den Bestätigungsdialog.
     */
    fun requestRemoteChangeDecision() {
        val newFolder = _syncFolderName.value.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME }
        val folderChanged = newFolder != confirmedSyncFolderName.value
        val serverChanged = isServerReallyChanged(confirmedServerUrl.value, currentFullServerUrl())
        val kind = when {
            folderChanged && serverChanged -> RemoteChangeKind.BOTH
            serverChanged -> RemoteChangeKind.SERVER
            else -> RemoteChangeKind.FOLDER
        }
        val (oldLabel, newLabel) = when (kind) {
            RemoteChangeKind.FOLDER -> confirmedSyncFolderName.value to newFolder
            RemoteChangeKind.SERVER -> extractHostFromUrl(confirmedServerUrl.value) to _serverHost.value
            RemoteChangeKind.BOTH -> "" to ""
        }
        ctx.scope.launch {
            val notes = withContext(ioDispatcher) { notesStorage.loadAllNotes(forceReload = true) }
            val unsyncedCount = notes.count { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.LOCAL_ONLY }
            val localOnlyCount = notes.count { it.syncStatus == SyncStatus.LOCAL_ONLY }
            _folderChangePrompt.value = FolderChangePrompt(kind, oldLabel, newLabel, unsyncedCount, localOnlyCount)
        }
    }

    /** "Notizen mitnehmen": Merge über resetAllSyncStatusToPending(). */
    fun onFolderChangeConfirmedMigrate() {
        val newFolder = _syncFolderName.value.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME }
        val newServerUrl = currentFullServerUrl()
        val kind = _folderChangePrompt.value?.kind ?: RemoteChangeKind.FOLDER
        _folderChangeInProgress.value = true
        ctx.scope.launch {
            try {
                WebDavSyncService.clearServerCaches(prefs, notesStorage)
                val count = notesStorage.resetAllSyncStatusToPending()
                confirmedSyncFolderName.value = newFolder
                confirmedServerUrl.value = newServerUrl
                val messageRes = when (kind) {
                    RemoteChangeKind.FOLDER -> R.string.toast_folder_migrated
                    else -> R.string.toast_server_changed_sync_reset
                }
                ctx.toast(ctx.string(messageRes, count))
            } finally {
                finishFolderChange()
            }
        }
    }

    /**
     * "Nicht mitnehmen": die lokale Ansicht wird durch den neuen Ordner/Server ersetzt. Offline-
     * Modus und Erreichbarkeit werden VOR dem destruktiven `restoreFromServer(REPLACE)` geprüft
     * (das ruft `deleteAllNotes()` vor dem Download).
     *
     * 🔧 v2.11.0: Navigiert wird ausschließlich über [folderChangeCompleted], das erst nach dem
     * Restore feuert: sonst sah man kurz eine leere Liste.
     */
    fun onFolderChangeConfirmedSwitch() {
        val newFolder = _syncFolderName.value.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME }
        val newServerUrl = currentFullServerUrl()
        val kind = _folderChangePrompt.value?.kind ?: RemoteChangeKind.FOLDER

        if (_offlineMode.value) {
            _folderChangePrompt.value = null
            revertRemoteTargetToConfirmed()
            ctx.showSnackbar(ctx.string(R.string.toast_folder_switch_offline))
            _folderChangeCompleted.tryEmit(Unit)
            return
        }

        _folderChangeInProgress.value = true
        ctx.scope.launch {
            try {
                val syncService = WebDavSyncService(ctx.app)
                val reachable = withContext(ioDispatcher) { syncService.isServerReachable(userAction = true) }
                if (!reachable) {
                    revertRemoteTargetToConfirmed()
                    ctx.toast(ctx.string(R.string.snackbar_server_unreachable))
                    return@launch
                }
                val result = withContext(ioDispatcher) { syncService.restoreFromServer(RestoreMode.REPLACE, emptyIsSuccess = true) }
                if (result.isSuccess) {
                    confirmedSyncFolderName.value = newFolder
                    confirmedServerUrl.value = newServerUrl
                    val messageRes = if (kind == RemoteChangeKind.FOLDER) R.string.toast_folder_switched else R.string.toast_server_switched
                    ctx.toast(ctx.string(messageRes, result.restoredCount))
                } else {
                    revertRemoteTargetToConfirmed()
                    ctx.toast(ctx.string(R.string.toast_error, result.errorMessage.orEmpty()))
                }
            } finally {
                finishFolderChange()
            }
        }
    }

    /** Abbrechen: Ordner + Server auf bestätigte Werte zurücksetzen, auf dem Screen bleiben. */
    fun onFolderChangeCancelled() {
        _folderChangePrompt.value = null
        revertRemoteTargetToConfirmed()
    }

    private fun finishFolderChange() {
        _folderChangeInProgress.value = false
        _folderChangePrompt.value = null
        _folderChangeCompleted.tryEmit(Unit)
    }

    private fun revertRemoteTargetToConfirmed() {
        // updateSyncFolderName/updateServerHost persistieren bei jedem Tastendruck: zurück auch dort.
        val confirmedFolder = confirmedSyncFolderName.value
        _syncFolderName.value = confirmedFolder
        prefs.edit { putString(Constants.KEY_SYNC_FOLDER_NAME, confirmedFolder) }

        val confirmedUrl = confirmedServerUrl.value
        _isHttps.value = confirmedUrl.startsWith("https://")
        _serverHost.value = extractHostFromUrl(confirmedUrl)
        prefs.edit { putString(Constants.KEY_SERVER_URL, confirmedUrl) }
    }

    /** v1.9.0: Nach einem Restore mit App-Einstellungen alles aus den Prefs neu lesen. */
    fun reloadFromPrefs() {
        val url = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
        _isHttps.value = url.startsWith("https://")
        _serverHost.value = extractHostFromUrl(url)
        _username.value = CredentialStore.getUsername(ctx.app).orEmpty()
        _password.value = CredentialStore.getPassword(ctx.app).orEmpty()
        // 🆕 v2.17.0: Auch beim Restore kann der KeyStore scheitern: Warnzeile + Snackbar.
        reportCredentialStorage(!CredentialStore.isStoredUnencrypted(ctx.app))
        confirmedServerUrl.value = url
        confirmedSyncFolderName.value = readSyncFolderName()
        _syncFolderName.value = confirmedSyncFolderName.value
        _offlineMode.value = prefs.getBoolean(Constants.KEY_OFFLINE_MODE, Constants.DEFAULT_OFFLINE_MODE)
    }

    /** Adresse aus dem aktuellen (noch unbestätigten) Protokoll und Host, ohne Umweg über [serverUrl]. */
    fun currentFullServerUrl(): String = fullUrl(_isHttps.value, _serverHost.value)

    private fun hasExistingServerConfig(): Boolean {
        val url = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !url.isNullOrEmpty() && url != "http://" && url != "https://"
    }

    private fun readSyncFolderName(): String =
        prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME) ?: Constants.DEFAULT_SYNC_FOLDER_NAME

    companion object {
        private const val TAG = "ServerSettings"

        private fun fullUrl(https: Boolean, host: String): String =
            if (host.isEmpty()) "" else (if (https) "https://" else "http://") + host

        internal fun extractHostFromUrl(url: String): String = when {
            url.startsWith("https://") -> url.removePrefix("https://")
            else -> url.removePrefix("http://")
        }

        /**
         * 🔧 v1.7.0: Nur eine echte Änderung zählt. Erstes Einrichten (leer → gefüllt), Entfernen
         * (gefüllt → leer), reiner Protokollwechsel, Groß-/Kleinschreibung und abschließender
         * Slash sind KEIN Wechsel; anderer Host, Port oder Pfad schon.
         */
        internal fun isServerReallyChanged(confirmedUrl: String, newUrl: String): Boolean {
            if (confirmedUrl.isEmpty() || newUrl.isEmpty() || confirmedUrl == newUrl) return false
            val normalize = { url: String -> url.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/").lowercase() }
            val changed = normalize(confirmedUrl) != normalize(newUrl)
            if (changed) Logger.d(TAG, "Server URL changed: '${normalize(confirmedUrl)}' → '${normalize(newUrl)}'")
            return changed
        }
    }
}
