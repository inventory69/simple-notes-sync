package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.R

/**
 * Builds the sync result banner message from a [SyncResult].
 * Returns null when all counts are zero (caller should fall back to "nothing to sync").
 */
fun buildSyncResultBanner(context: Context, result: SyncResult): String? {
    val parts = buildList {
        if (result.syncedCount > 0) {
            add(context.getString(R.string.toast_sync_success, result.syncedCount))
        }
        if (result.deletedOnServerCount > 0) {
            add(context.getString(R.string.sync_moved_to_trash_count, result.deletedOnServerCount))
        }
        if (result.purgedFromServerCount > 0) {
            add(context.getString(R.string.sync_deleted_from_server_count, result.purgedFromServerCount))
        }
        if (result.trashedFromServerCount > 0) {
            add(context.getString(R.string.sync_trashed_from_server_count, result.trashedFromServerCount))
        }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
