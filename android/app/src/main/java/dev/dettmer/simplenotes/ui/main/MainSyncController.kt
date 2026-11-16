package dev.dettmer.simplenotes.ui.main

import android.app.Application
import android.content.Context
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.E2eeGate
import dev.dettmer.simplenotes.sync.ExportProblems
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.sync.Reachability
import dev.dettmer.simplenotes.sync.SyncExceptionMapper
import dev.dettmer.simplenotes.sync.SyncPhase
import dev.dettmer.simplenotes.sync.SyncProgress
import dev.dettmer.simplenotes.sync.SyncResult
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.SyncStatusSummary
import dev.dettmer.simplenotes.sync.SyncUrlBuilder
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.sync.buildSyncResultBanner
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Alles, was die Hauptliste mit dem Server bespricht: manueller und Auto-Sync, das Sync-Banner,
 * der Sync-Status-Dialog und sofortige Server-Löschungen (oder deren Einreihen).
 *
 * Die Sync-Gates (E2EE, WLAN, Offline) liegen in [WebDavSyncService]; hier wird nur entschieden,
 * was der Nutzer davon sieht. Nach einem Sync lädt der ViewModel über [reloadNotes] und
 * [reloadFolders] nach.
 */
class MainSyncController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val pendingServerDeletions: PendingServerDeletions,
    private val reloadNotes: () -> Unit,
    private val reloadFolders: () -> Unit,
    private val showSnackbar: suspend (MainViewModel.SnackbarData) -> Unit
) {
    private val ioDispatcher = Dispatchers.IO
    private val prefs = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val storage = NotesStorage(app)
    private val folderStore = FolderStore(app)

    // 🆕 v1.8.0 / 🔧 v1.10.0: Banner-System: min. Anzeigedauer pro Phase
    val syncProgress: StateFlow<SyncProgress> = SyncStateManager.syncProgress
        .withMinPhaseDuration(Constants.BANNER_PHASE_MIN_MS)
        .stateIn(scope, SharingStarted.Eagerly, SyncProgress.IDLE)

    // SyncState für den PullToRefresh-Indikator
    private val _syncState = MutableStateFlow(SyncStateManager.SyncState.IDLE)
    val syncState: StateFlow<SyncStateManager.SyncState> = _syncState.asStateFlow()

    /** 🆕 v2.19.0: Inhalt des Sync-Status-Dialogs und Badge am Hilfe-Icon. */
    private val _syncStatusSummary = MutableStateFlow(SyncStatusSummary.EMPTY)
    val syncStatusSummary: StateFlow<SyncStatusSummary> = _syncStatusSummary.asStateFlow()

    private val _showSyncStatus = MutableStateFlow(false)
    val showSyncStatus: StateFlow<Boolean> = _showSyncStatus.asStateFlow()

    // 🆕 v1.9.0 (F13): Scroll-to-top nach einem manuellen Sync
    private val _syncCompletedScrollToTop = MutableStateFlow(false)
    val syncCompletedScrollToTop: StateFlow<Boolean> = _syncCompletedScrollToTop.asStateFlow()

    fun resetSyncCompletedScrollToTop() {
        _syncCompletedScrollToTop.value = false
    }

    fun updateSyncState(status: SyncStateManager.SyncStatus) {
        _syncState.value = status.state
        // Jeder Sync-Abschluss (UI oder Worker) läuft über SyncStateManager: die Prefs sind dann frisch.
        refreshSyncStatus()
    }

    fun refreshSyncStatus() {
        scope.launch(ioDispatcher) {
            _syncStatusSummary.value = SyncStatusSummary.from(
                // Ungefiltert wie unresolvedConflictCount: Ein Ordner- oder Suchfilter darf nichts verstecken.
                notes = storage.loadAllNotes(),
                localOnlyFolders = folderStore.getLocalOnlyFolderNames(),
                exportProblems = ExportProblems.load(prefs),
                lastSuccessAt = prefs.getLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, 0L),
                lastError = prefs.getString(Constants.KEY_LAST_SYNC_ERROR, null),
                lastErrorAt = prefs.getLong(Constants.KEY_LAST_SYNC_ERROR_AT, 0L),
                now = System.currentTimeMillis(),
                e2eeBlocked = E2eeGate.isBlocked(prefs, SyncUrlBuilder(prefs))
            )
        }
    }

    fun openSyncStatus() {
        refreshSyncStatus()
        _showSyncStatus.value = true
    }

    fun closeSyncStatus() {
        _showSyncStatus.value = false
    }

    /**
     * Manueller Sync (Toolbar oder Pull-to-Refresh). Das Banner erscheint sofort (PREPARING),
     * ein laufender stiller Sync wird sichtbar gemacht statt abgelehnt.
     */
    fun triggerManualSync(trigger: ActivityLog.Trigger) {
        // ponytail: Alias hält die bestehenden "$source"-Logzeilen unverändert.
        val source = trigger.name
        val syncService = WebDavSyncService(app)
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            if (gateResult.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ $source Sync blocked: WiFi-only mode, not on WiFi")
                SyncStateManager.markError(app.getString(R.string.sync_wifi_only_error))
            } else {
                Logger.d(TAG, "⏭️ $source Sync blocked: ${gateResult.blockReason ?: "offline/no server"}")
            }
            return
        }

        if (!SyncStateManager.tryStartSync(source)) {
            if (SyncStateManager.isSyncing) {
                // 🛡️ v1.8.2 (IMPL_24): Läuft ein stiller Sync, wird er sichtbar statt abgelehnt.
                if (SyncStateManager.promoteToVisible()) {
                    Logger.d(TAG, "📢 $source: Promoted silent sync to visible")
                } else {
                    Logger.d(TAG, "⏭️ $source Sync blocked: Another sync in progress")
                    scope.launch {
                        showSnackbar(
                            MainViewModel.SnackbarData(
                                message = app.getString(R.string.sync_already_running),
                                actionLabel = "",
                                onAction = {}
                            )
                        )
                    }
                }
            }
            return
        }

        // 🆕 v1.8.1 (IMPL_08): Globalen Cooldown markieren (verhindert Auto-Sync direkt danach).
        // Der manuelle Sync selbst prüft ihn nicht: der Nutzer will explizit synchronisieren.
        SyncStateManager.markGlobalSyncStarted(prefs)
        scope.launch { runManualSync(syncService, trigger, source) }
    }

    private suspend fun runManualSync(syncService: WebDavSyncService, trigger: ActivityLog.Trigger, source: String) {
        try {
            if (!syncService.hasUnsyncedChanges()) {
                Logger.d(TAG, "⏭️ $source Sync: No unsynced changes")
                // 🆕 v2.19.0: offene Konflikte im Warn-Stil, wie nach einem echten Sync
                SyncStateManager.markCompleted(alreadySyncedBanner(), isWarning = unresolvedConflictCount() > 0)
                reloadNotes()
                reloadFolders()
                _syncCompletedScrollToTop.value = true
                return
            }

            val isReachable = withContext(ioDispatcher) { syncService.isServerReachable(userAction = true) }
            if (!isReachable) {
                Logger.d(TAG, "⏭️ $source Sync: Server not reachable")
                SyncStateManager.markError(app.getString(R.string.snackbar_server_unreachable))
                return
            }

            val result = withContext(ioDispatcher) { syncService.syncNotes(trigger) }
            when {
                result.isSuccess -> {
                    SyncStateManager.markCompleted(completionBanner(result), result.isWarning)
                    reloadNotes()
                    reloadFolders()
                    if (result.syncedCount > 0 || result.deletedOnServerCount > 0) {
                        _syncCompletedScrollToTop.value = true
                    }
                }
                result.e2eeBlocked -> SyncStateManager.markCompleted(result.errorMessage, isWarning = true) // 🆕 v2.20.0
                else -> SyncStateManager.markError(result.errorMessage)
            }
        } catch (e: Exception) {
            SyncStateManager.markError(e.message)
        }
    }

    /**
     * Auto-Sync beim Zurückkehren in die App. Still (kein Banner), Fehler werden trotzdem gezeigt.
     * Läuft nur, wenn der onResume-Trigger an ist und das zentrale Sync-Gate durchlässt.
     */
    fun triggerAutoSync(trigger: ActivityLog.Trigger) {
        // ponytail: Alias hält die bestehenden "$source"-Logzeilen unverändert.
        val source = trigger.name
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)) {
            Logger.d(TAG, "⏭️ onResume sync disabled - skipping")
            return
        }

        val syncService = WebDavSyncService(app)
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            if (gateResult.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ Auto-sync ($source) blocked: WiFi-only mode, not on WiFi")
            } else {
                Logger.d(TAG, "⏭️ Auto-sync ($source) blocked: ${gateResult.blockReason ?: "offline/no server"}")
            }
            return
        }

        // 🆕 v1.8.0: silent=true → SyncProgress.silent=true → Banner unsichtbar
        if (!SyncStateManager.tryStartSync("auto-$source", silent = true)) {
            Logger.d(TAG, "⏭️ Auto-sync ($source): Another sync already in progress")
            return
        }

        Logger.d(TAG, "🔄 Auto-sync triggered ($source)")

        scope.launch {
            try {
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): No unsynced changes - skipping")
                    // 🔧 v2.4.0 (FIX-SSBE-003): silent → IDLE; promoted → COMPLETED-Banner
                    SyncStateManager.markCompleted(app.getString(R.string.toast_already_synced))
                    return@launch
                }

                val reachability = withContext(ioDispatcher) { syncService.reachability() }
                if (reachability != Reachability.Reachable) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): Server not reachable - skipping silently")
                    // 🔧 v2.4.0 (FIX-SSBE-002): promoted → Fehler im Banner, sonst still IDLE.
                    // 🆕 v2.21.0: Zertifikatsfehler mit eigener Meldung, landet im Sync-Status
                    val skipMessage = (reachability as? Reachability.TlsError)
                        ?.let { SyncExceptionMapper.sslMessage(it.kind) }
                        ?: R.string.snackbar_server_unreachable
                    SyncStateManager.errorIfVisible(app.getString(skipMessage))
                    return@launch
                }

                val result = withContext(ioDispatcher) { syncService.syncNotes(trigger) }
                onAutoSyncResult(source, result)
            } catch (e: Exception) {
                Logger.e(TAG, "💥 Auto-sync exception ($source): ${e.message}")
                SyncStateManager.markError(e.message)
            }
        }
    }

    private suspend fun onAutoSyncResult(source: String, result: SyncResult) {
        when {
            result.isSuccess && result.syncedCount > 0 -> {
                Logger.d(TAG, "✅ Auto-sync successful ($source): ${result.syncedCount} notes")
                // 🆕 v2.19.0: completionBanner enthält toast_sync_success plus Konflikte/Export-Probleme
                SyncStateManager.markCompleted(completionBanner(result), result.isWarning)
                reloadNotes()
                reloadFolders()
            }
            result.isSuccess && result.purgedFromServerCount > 0 -> {
                Logger.d(TAG, "✅ Auto-sync ($source): ${result.purgedFromServerCount} purged from server")
                SyncStateManager.promoteToVisible()
                SyncStateManager.markCompleted(buildSyncResultBanner(app, result), result.isWarning)
                reloadNotes()
            }
            result.isSuccess -> {
                Logger.d(TAG, "ℹ️ Auto-sync ($source): No changes")
                // 🆕 v2.16.0: Meldung statt null: ein sichtbar gemachter stiller Sync endete sonst mit
                // dem generischen „Sync abgeschlossen", auch bei frischem Konflikt.
                SyncStateManager.markCompleted(completionBanner(result), result.isWarning)
                // 🆕 v2.7.2 / Issue #128: geheilte Ordner-Zuordnung oder zurückgenommenes
                // DELETED_ON_SERVER → Liste neu laden, sonst bleibt die Notiz bis zum Reload unsichtbar.
                if (result.foldersReconciled || result.restoredCount > 0) reloadNotes()
                if (result.foldersChanged || result.foldersReconciled) reloadFolders()
            }
            // 🆕 v2.20.0: still → IDLE, kein Fehler-Banner bei jedem App-Start
            result.e2eeBlocked -> SyncStateManager.markCompleted(result.errorMessage, isWarning = true)
            else -> {
                Logger.e(TAG, "❌ Auto-sync failed ($source): ${result.errorMessage}")
                SyncStateManager.markError(result.errorMessage) // Fehler immer zeigen, auch still
            }
        }
    }

    /**
     * Ordner vom Sync ausgeschlossen mit „vom Server entfernen": löscht die Server-Kopien sofort
     * und räumt danach die leeren [folderNames]-Verzeichnisse ab. Ist der Server gerade nicht
     * erreichbar oder gesperrt, landen die Löschungen in der Queue; die Ordner räumt dann der Sync.
     */
    suspend fun removeFromServer(deletions: List<PendingServerDeletions.PendingDeletion>, folderNames: Set<String>) {
        val webdavService = WebDavSyncService(app)
        queueReason(webdavService)?.let { reason ->
            pendingServerDeletions.add(deletions)
            SyncStateManager.showInfo(app.getString(reason))
            return
        }
        val total = deletions.size
        var successCount = 0
        var failCount = 0
        if (total > 1) {
            SyncStateManager.updateProgress(phase = SyncPhase.DELETING, current = 0, total = total, currentFileName = null)
        }
        for (deletion in deletions) {
            if (total > 1) SyncStateManager.incrementProgress(currentFileName = null)
            try {
                val ok = withContext(ioDispatcher) { webdavService.deleteNoteFromServer(deletion.id, deletion.folderName) }
                if (ok) successCount++ else failCount++
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to delete ${deletion.id} from server: ${e.message}")
                failCount++
            }
        }
        val message = when {
            failCount == 0 -> app.resources.getQuantityString(
                R.plurals.snackbar_notes_deleted_from_server,
                successCount,
                successCount
            )
            successCount == 0 -> app.getString(R.string.snackbar_server_delete_failed)
            else -> app.getString(R.string.snackbar_notes_deleted_from_server_partial, successCount, successCount + failCount)
        }
        if (failCount == 0) SyncStateManager.showInfo(message) else SyncStateManager.showError(message)
        deleteFolders(webdavService, folderNames)
    }

    /**
     * Räumt leere Ordner-Verzeichnisse auf dem Server ab. 🆕 v2.20.0 (E2EE-Slice 1): ein
     * Marker-GET für alle Ordner, gesperrt → nichts räumen.
     */
    suspend fun deleteEmptyServerFolders(folderNames: Collection<String>) {
        if (folderNames.isEmpty()) return
        val service = WebDavSyncService(app)
        // ponytail: ein leeres Server-Verzeichnis bleibt dann liegen, der Tombstone in folders.json gilt trotzdem.
        if (!service.isServerLocked()) deleteFolders(service, folderNames)
    }

    private suspend fun deleteFolders(service: WebDavSyncService, folderNames: Collection<String>) {
        folderNames.forEach { folderName ->
            try {
                withContext(ioDispatcher) { service.deleteServerFolderIfEmpty(folderName) }
            } catch (e: Exception) {
                Logger.w(TAG, "deleteServerFolderIfEmpty('$folderName') failed: ${e.message}")
            }
        }
    }

    /** Warum Server-Löschungen jetzt nicht gehen (Hinweistext), oder null, wenn sie gehen. */
    private suspend fun queueReason(service: WebDavSyncService): Int? = withContext(ioDispatcher) {
        val isReachable = try {
            service.isServerReachable()
        } catch (e: Exception) {
            Logger.d(TAG, "isServerReachable check failed during server deletion: ${e.message}")
            false
        }
        when {
            !isReachable -> R.string.snackbar_delete_queued_for_sync
            // 🆕 v2.20.0 (E2EE-Slice 1): verschlüsselter Ordner, erreichbar, aber gesperrt
            service.isServerLocked() -> R.string.snackbar_delete_queued_e2ee
            else -> null
        }
    }

    /**
     * 🆕 v2.16.0: Die Abschlussmeldung eines Syncs. `conflictCount` zählt den Ist-Zustand (offene
     * Notizen), weil ein vorauslaufender stiller Sync den Konflikt oft schon erkannt hat.
     */
    private suspend fun completionBanner(result: SyncResult): String =
        buildSyncResultBanner(app, result) ?: app.getString(R.string.snackbar_nothing_to_sync)

    /** „Bereits synchronisiert": es sei denn, eine Notiz wartet auf eine Entscheidung. */
    private suspend fun alreadySyncedBanner(): String {
        val count = unresolvedConflictCount()
        if (count == 0) return app.getString(R.string.toast_already_synced)
        return app.resources.getQuantityString(R.plurals.sync_conflict_count, count, count)
    }

    /**
     * 🆕 v2.16.0: Wie viele Notizen auf eine Konfliktentscheidung warten. Aus dem Storage, nicht
     * aus der gefilterten Liste: ein aktiver Ordner- oder Suchfilter würde die Zahl verkleinern.
     */
    private suspend fun unresolvedConflictCount(): Int =
        storage.loadAllNotes().count { it.syncStatus == SyncStatus.CONFLICT }

    /**
     * 🔧 v1.10.0: Jede aktive Sync-Phase bleibt mindestens [minMs] sichtbar. Übergänge von und zu
     * IDLE werden nicht verzögert, das Banner erscheint und verschwindet also weiter sofort.
     */
    @Suppress("NestedBlockDepth") // inherently nested: flow → collect → if
    private fun Flow<SyncProgress>.withMinPhaseDuration(minMs: Long): Flow<SyncProgress> = flow {
        var lastEmitTime = 0L
        var lastPhase = SyncPhase.IDLE
        collect { value ->
            if (value.phase != SyncPhase.IDLE && lastPhase != SyncPhase.IDLE && value.phase != lastPhase) {
                val remaining = minMs - (System.currentTimeMillis() - lastEmitTime)
                if (remaining > 0) delay(remaining)
            }
            emit(value)
            lastEmitTime = System.currentTimeMillis()
            lastPhase = value.phase
        }
    }

    private companion object {
        const val TAG = "MainSyncController"
    }
}
