package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * v2.5.0 — Zentrale Throttle-/Gate-Logik für `onSave`-artige Sync-Trigger.
 *
 * Vor v2.5.0 war diese Logik in `NoteEditorViewModel.triggerOnSaveSync()`
 * (Z. 1316-1356) und `MainViewModel.triggerOnSaveSync()` (Z. 594-625)
 * dupliziert. Beide ViewModels delegieren nun hierher; das Keep-Import-
 * Feature nutzt denselben Scheduler nach Abschluss eines Imports.
 *
 * Verhalten unverändert:
 *  - Setting-Gate ([Constants.KEY_SYNC_TRIGGER_ON_SAVE]) — wenn aus, no-op.
 *  - Connectivity/Server-Gate via [WebDavSyncService.canSync].
 *  - Throttle ([Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS], default 5 s).
 *  - Persistierter Last-Run-Timestamp ([Constants.PREF_LAST_ON_SAVE_SYNC_TIME]).
 *  - Enqueue als `OneTimeWorkRequest` mit Standard- + `SYNC_ONSAVE_TAG`.
 *
 * @return `true` wenn Sync enqueued wurde, `false` bei Skip.
 */
class SyncScheduler(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(
        Constants.PREFS_NAME, Context.MODE_PRIVATE,
    ),
    private val nowMsProvider: () -> Long = { System.currentTimeMillis() },
) {

    fun triggerOnSaveSync(reason: String = "onSave"): Boolean {
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, Constants.DEFAULT_TRIGGER_ON_SAVE)) {
            Logger.d(TAG, "⏭️ $reason sync disabled - skipping")
            return false
        }

        val syncService = WebDavSyncService(context)
        val gate = syncService.canSync()
        if (!gate.canSync) {
            if (gate.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ $reason sync blocked: WiFi-only mode, not on WiFi")
            } else {
                Logger.d(TAG, "⏭️ $reason sync blocked: ${gate.blockReason ?: "offline/no server"}")
            }
            return false
        }

        val now = nowMsProvider()
        val last = prefs.getLong(Constants.PREF_LAST_ON_SAVE_SYNC_TIME, 0)
        val elapsed = now - last
        if (elapsed < Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS) {
            val remaining = (Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS - elapsed) / 1000
            Logger.d(TAG, "⏳ $reason sync throttled - wait ${remaining}s")
            return false
        }

        prefs.edit { putLong(Constants.PREF_LAST_ON_SAVE_SYNC_TIME, now) }

        Logger.d(TAG, "📤 Triggering $reason sync")
        try {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(Constants.SYNC_WORK_TAG)
                .addTag(Constants.SYNC_ONSAVE_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return true
        } catch (e: Exception) {
            Logger.w(TAG, "WorkManager enqueue failed for $reason: ${e.message}")
            return false
        }
    }

    companion object {
        private const val TAG = "SyncScheduler"
    }
}
