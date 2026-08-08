package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.DeletionRecord
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.isWebDavNotFound
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * Synchronises the shared `<syncFolder>/deletions.json` ledger with the server.
 * Mirrors the FolderSyncManager pattern: all failures are non-fatal.
 */
internal class DeletionSyncManager(private val urlBuilder: SyncUrlBuilder) {
    fun deletionsFileUrl(serverUrl: String): String =
        urlBuilder.getNotesUrl(serverUrl).trimEnd('/') + "/" + DELETIONS_FILE_NAME

    /**
     * GET deletions.json; 404 or parse error → empty tracker.
     * Kein vorheriges `exists()` — der catch-all deckt den 404-Fall mit ab und spart einen Request.
     */
    fun downloadRemote(webdav: WebDavClient, url: String): DeletionTracker = try {
        webdav.get(url).use { input ->
            DeletionTracker.fromJson(input.reader().readText()) ?: DeletionTracker()
        }
    } catch (e: Exception) {
        // 404 ist der Normalfall, solange auf dem Server noch nie gelöscht wurde — sonst würde
        // jeder Sync eine WARN-Zeile in den Beta-Log schreiben.
        val msg = "download deletions.json failed (non-fatal): ${e.message}"
        if (e.isWebDavNotFound()) Logger.d(TAG, msg) else Logger.w(TAG, msg)
        DeletionTracker()
    }

    /**
     * Read-modify-write für alle [noteIds] auf einmal: ein GET, alle upserten (dedupe by id,
     * neuestes deletedAt gewinnt), Einträge älter als [Constants.TRASH_RETENTION_MS] prunen,
     * ein PUT. Best-effort — loggt bei Fehlern, wirft nie.
     *
     * Gibt den gemergten Tracker zurück — auch wenn der PUT scheitert (dann ist er nur lokal
     * korrekt, was für das nachgelagerte Seeding reicht).
     */
    fun appendAllAndUpload(
        webdav: WebDavClient,
        url: String,
        noteIds: Collection<String>,
        deviceId: String
    ): DeletionTracker {
        val tracker = downloadRemote(webdav, url)
        val now = System.currentTimeMillis()
        noteIds.forEach { tracker.upsertIfNewer(DeletionRecord(it, now, deviceId)) }
        tracker.pruneOlderThan(Constants.TRASH_RETENTION_MS, now)
        try {
            webdav.put(url, tracker.toJson().toByteArray(Charsets.UTF_8), "application/json")
            Logger.d(TAG, "📝 deletions.json updated: added ${noteIds.size}, ${tracker.deletedNotes.size} entries")
        } catch (e: Exception) {
            Logger.w(TAG, "appendAllAndUpload deletions.json failed (non-fatal): ${e.message}")
        }
        return tracker
    }

    companion object {
        private const val TAG = "DeletionSyncManager"
        const val DELETIONS_FILE_NAME = "deletions.json"
    }
}
