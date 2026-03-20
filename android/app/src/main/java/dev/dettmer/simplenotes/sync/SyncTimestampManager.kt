package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * Manages sync timestamp persistence.
 *
 * Extracted from WebDavSyncService in v2.0.0 (Commit 15).
 */
class SyncTimestampManager(private val prefs: SharedPreferences) {
    companion object {
        private const val TAG = "SyncTimestampManager"
    }

    /**
     * Saves the current time as both last-sync and last-successful-sync timestamp.
     */
    fun save() {
        val now = System.currentTimeMillis()

        // ⚡ v1.3.1: Simplified - file-level E-Tags cached individually in downloadRemoteNotes()
        // No need for collection E-Tag (doesn't work reliably across WebDAV servers)
        prefs.edit {
            putLong(Constants.KEY_LAST_SYNC, now)
            putLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, now)
        }

        Logger.d(TAG, "💾 Saved sync timestamp (file E-Tags cached individually)")
    }

    /** Returns the timestamp of the last sync attempt (0 if never synced). */
    fun getLast(): Long = prefs.getLong(Constants.KEY_LAST_SYNC, 0)

    /** Returns the timestamp of the last *successful* sync (0 if never synced). */
    fun getLastSuccessful(): Long = prefs.getLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, 0)
}
