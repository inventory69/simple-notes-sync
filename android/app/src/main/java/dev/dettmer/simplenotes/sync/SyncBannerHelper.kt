package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.R

/**
 * Builds the sync result banner message from a [SyncResult].
 * Returns null when all counts are zero (caller should fall back to "nothing to sync").
 */
fun buildSyncResultBanner(context: Context, result: SyncResult): String? {
    val parts = buildList {
        // 🆕 v2.16.0: Konflikte standen bisher in keinem Zweig — ein Sync, der gerade eine Notiz
        // als CONFLICT markiert hatte, endete mit „Nichts zu synchronisieren". Das war das
        // Gegenteil der Wahrheit, und zwar genau in dem Fall, der eine Entscheidung braucht.
        // Steht vorn, damit es beim Zusammenschneiden mehrerer Teile nicht hinten abfällt.
        if (result.conflictCount > 0) {
            add(
                context.resources.getQuantityString(
                    R.plurals.sync_conflict_count,
                    result.conflictCount,
                    result.conflictCount
                )
            )
        }
        if (result.syncedCount > 0) {
            // 🆕 v2.19.0: Plural ohne ✅ — das Banner hat sein eigenes Icon, und „1 notes" ist falsch.
            add(context.resources.getQuantityString(R.plurals.sync_notes_synced_count, result.syncedCount, result.syncedCount))
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
        // 🆕 Issue #128
        if (result.restoredCount > 0) {
            add(context.getString(R.string.sync_restored_count, result.restoredCount))
        }
        if (result.deletionDetectionSkipped) {
            add(context.getString(R.string.sync_deletion_check_skipped))
        }
        // 🆕 v2.19.0: Die Notizen sind synchron, nur ihre Spiegel/Bilder nicht.
        addAll(exportProblemParts(context, result))
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** 🆕 v2.19.0: Banner-Teile für Export-Probleme — auch Text der Hintergrund-Benachrichtigung. */
fun exportProblemParts(context: Context, result: SyncResult): List<String> = buildList {
    val res = context.resources
    if (result.markdownFailedCount > 0) {
        add(res.getQuantityString(R.plurals.sync_markdown_failed_count, result.markdownFailedCount, result.markdownFailedCount))
    }
    if (result.markdownImportFailed) add(context.getString(R.string.sync_markdown_import_failed))
    if (result.assetFailedCount > 0) {
        add(res.getQuantityString(R.plurals.sync_assets_failed_count, result.assetFailedCount, result.assetFailedCount))
    }
}
