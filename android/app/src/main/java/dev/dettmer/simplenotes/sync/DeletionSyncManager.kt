package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.DeletionRecord
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * Synchronises the shared `<syncFolder>/deletions.json` ledger with the server.
 * Mirrors the FolderSyncManager pattern: all failures are non-fatal.
 */
internal class DeletionSyncManager(private val urlBuilder: SyncUrlBuilder) {
    fun deletionsFileUrl(serverUrl: String): String =
        urlBuilder.getNotesUrl(serverUrl).trimEnd('/') + "/" + DELETIONS_FILE_NAME

    /** GET deletions.json; 404 or parse error → empty tracker. */
    fun downloadRemote(webdav: WebDavClient, url: String): DeletionTracker = try {
        val exists = try {
            webdav.exists(url)
        } catch (_: Exception) {
            false
        }
        if (!exists) {
            DeletionTracker()
        } else {
            webdav.get(url).use { input ->
                DeletionTracker.fromJson(input.reader().readText()) ?: DeletionTracker()
            }
        }
    } catch (e: Exception) {
        Logger.w(TAG, "download deletions.json failed (non-fatal): ${e.message}")
        DeletionTracker()
    }

    /**
     * Read-modify-write: adds the deletion record for [noteId], dedupes by id
     * (keeps newest deletedAt), prunes entries older than [Constants.TRASH_RETENTION_MS],
     * then PUTs the result back. Best-effort — logs on failure, never throws.
     */
    fun appendAndUpload(webdav: WebDavClient, url: String, noteId: String, deviceId: String) {
        try {
            val tracker = downloadRemote(webdav, url)
            val now = System.currentTimeMillis()
            tracker.upsertIfNewer(DeletionRecord(noteId, now, deviceId))
            tracker.pruneOlderThan(Constants.TRASH_RETENTION_MS, now)
            webdav.put(url, tracker.toJson().toByteArray(Charsets.UTF_8), "application/json")
            Logger.d(TAG, "📝 deletions.json updated: added $noteId, ${tracker.deletedNotes.size} entries")
        } catch (e: Exception) {
            Logger.w(TAG, "appendAndUpload deletions.json for $noteId failed (non-fatal): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeletionSyncManager"
        const val DELETIONS_FILE_NAME = "deletions.json"
    }
}
