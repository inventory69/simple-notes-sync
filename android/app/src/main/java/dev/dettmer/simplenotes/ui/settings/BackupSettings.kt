package dev.dettmer.simplenotes.ui.settings

import android.net.Uri
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.BackupManager
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.BackupOutcome
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup-Screen: Backup anlegen, aus Datei oder vom Server wiederherstellen.
 *
 * [onSettingsRestored] läuft nach einem Restore, der App-Einstellungen mitgebracht hat: dann
 * lesen alle Einstellungs-Domänen ihre Prefs neu.
 */
class BackupSettings(
    private val ctx: SettingsContext,
    private val onSettingsRestored: () -> Unit
) {
    private val backupManager = BackupManager(ctx.app)

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    // v1.8.0: Descriptive backup status text
    private val _backupStatusText = MutableStateFlow("")
    val backupStatusText: StateFlow<String> = _backupStatusText.asStateFlow()

    /**
     * Ergebnis der letzten Backup-/Restore-Aktion. Bleibt gesetzt, bis der Backup-Screen verlassen
     * wird ([clearBackupOutcome]): ein Statustext für 2–3 Sekunden war weg, bevor man ihn las.
     */
    private val _backupOutcome = MutableStateFlow<BackupOutcome?>(null)
    val backupOutcome: StateFlow<BackupOutcome?> = _backupOutcome.asStateFlow()

    fun createBackup(uri: Uri, password: String? = null, includeServerSettings: Boolean = false) {
        runWithProgress(R.string.backup_progress_creating, R.string.backup_progress_failed, "Failed to create backup") {
            val result = backupManager.createBackup(uri, password, includeServerSettings)
            BackupOutcome(
                isSuccess = result.success,
                title = ctx.string(if (result.success) R.string.backup_progress_complete else R.string.backup_progress_failed),
                detail = if (result.success) result.message else result.error
            )
        }
    }

    fun restoreFromFile(uri: Uri, mode: RestoreMode, password: String? = null, restoreServerSettings: Boolean = false) {
        runWithProgress(R.string.backup_progress_restoring, R.string.restore_progress_failed, "Failed to restore backup from file") {
            val result = backupManager.restoreBackup(uri, mode, password, restoreServerSettings)
            // v1.9.0: mitgebrachte Einstellungen in die StateFlows übernehmen
            if (restoreServerSettings && result.success) onSettingsRestored()
            // Grund mitgeben: er steckte bisher nur im RestoreResult und wurde verworfen.
            BackupOutcome(
                isSuccess = result.success,
                title = ctx.string(if (result.success) R.string.restore_progress_complete else R.string.restore_progress_failed),
                detail = if (result.success) result.message else result.error
            )
        }
    }

    fun restoreFromServer(mode: RestoreMode) {
        runWithProgress(
            R.string.backup_progress_restoring_server,
            R.string.restore_server_progress_failed,
            "Failed to restore from server"
        ) {
            val result = withContext(Dispatchers.IO) { WebDavSyncService(ctx.app).restoreFromServer(mode) }
            BackupOutcome(
                isSuccess = result.isSuccess,
                title = ctx.string(
                    if (result.isSuccess) R.string.restore_server_progress_complete else R.string.restore_server_progress_failed
                ),
                detail = if (result.isSuccess) {
                    ctx.app.resources.getQuantityString(R.plurals.restore_server_notes_restored, result.restoredCount, result.restoredCount)
                } else {
                    result.errorMessage
                }
            )
        }
    }

    /** Löscht eine vom Speichern-Dialog angelegte Backup-Datei, wenn das Backup abgebrochen wurde. */
    fun discardBackupFile(uri: Uri) {
        ctx.scope.launch { backupManager.deleteIncompleteBackup(uri) }
    }

    fun checkBackupContainsAppSettings(uri: Uri, password: String? = null, onResult: (Boolean) -> Unit) {
        ctx.scope.launch { onResult(backupManager.backupContainsAppSettings(uri, password)) }
    }

    /** 🔐 v1.7.0: Verschlüsselt oder nicht? Im Fehlerfall gilt Klartext. */
    fun checkBackupEncryption(uri: Uri, onEncrypted: () -> Unit, onPlaintext: () -> Unit) {
        ctx.scope.launch {
            val isEncrypted = try {
                backupManager.isBackupEncrypted(uri)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check encryption status", e)
                false
            }
            if (isEncrypted) onEncrypted() else onPlaintext()
        }
    }

    /** Verwirft das Backup-/Restore-Ergebnis: aufgerufen beim Verlassen des Backup-Screens. */
    fun clearBackupOutcome() {
        _backupOutcome.value = null
    }

    private fun runWithProgress(statusRes: Int, failedTitleRes: Int, logMessage: String, action: suspend () -> BackupOutcome) {
        ctx.scope.launch {
            _isBackupInProgress.value = true
            _backupOutcome.value = null
            _backupStatusText.value = ctx.string(statusRes)
            try {
                _backupOutcome.value = action()
            } catch (e: Exception) {
                Logger.e(TAG, logMessage, e)
                _backupOutcome.value = BackupOutcome(isSuccess = false, title = ctx.string(failedTitleRes), detail = e.message)
            } finally {
                _isBackupInProgress.value = false
                _backupStatusText.value = ""
            }
        }
    }

    private companion object {
        const val TAG = "BackupSettings"
    }
}
