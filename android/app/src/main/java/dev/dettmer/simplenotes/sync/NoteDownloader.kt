package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.parallel.DownloadTask
import dev.dettmer.simplenotes.sync.parallel.DownloadTaskResult
import dev.dettmer.simplenotes.sync.parallel.ParallelDownloader
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Result of a remote download pass.
 * Moved from WebDavSyncService (v2.0.0, Commit 21).
 */
internal data class DownloadResult(
    val downloadedCount: Int,
    val conflictCount: Int,
    val deletedOnServerCount: Int = 0,
    val downloadFailed: Boolean = false,
    val downloadError: String? = null
)

/**
 * 🆕 v2.0.0: Extracts download, deletion-detection and server-delete logic
 * from WebDavSyncService (Commit 21).
 *
 * Responsibilities:
 * - downloadAll()      ← downloadRemoteNotes()
 * - detectDeletions()  ← detectServerDeletions()
 * - deleteFromServer() ← deleteNoteFromServer()
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class NoteDownloader(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val connectionManager: ConnectionManager,
    private val markdownSyncManager: MarkdownSyncManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "NoteDownloader"
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val ALL_DELETED_GUARD_THRESHOLD = 10
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    /** Current configured sync folder name — read fresh from prefs each access. */
    private val activeSyncFolderName: String
        get() = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Suppress(
        "NestedBlockDepth",
        "LoopWithTooManyJumpStatements",
        "LongMethod",
        "ComplexMethod"
    )
    // Sync logic requires nested conditions for comprehensive error handling and conflict resolution
    // 🛡️ v1.8.2 (IMPL_19b): suspend fun ermöglicht coroutineScope statt runBlocking
    suspend fun downloadAll(
        sardine: Sardine,
        serverUrl: String,
        includeRootFallback: Boolean = false,  // 🆕 v1.2.2: Only for restore from server
        forceOverwrite: Boolean = false,  // 🆕 v1.3.0: For OVERWRITE_DUPLICATES mode
        deletionTracker: DeletionTracker = storage.loadDeletionTracker(),  // 🆕 v1.3.0: Allow passing fresh tracker
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }  // 🆕 v1.8.0
    ): DownloadResult {
        var downloadedCount = 0
        var conflictCount = 0
        var skippedDeleted = 0  // Track skipped deleted notes
        val processedIds = mutableSetOf<String>()  // 🆕 v1.2.2: Track already loaded notes

        Logger.d(TAG, "📥 downloadAll() called:")
        Logger.d(TAG, "   includeRootFallback: $includeRootFallback")
        Logger.d(TAG, "   forceOverwrite: $forceOverwrite")

        // Use provided deletion tracker (allows fresh tracker from restore)
        var trackerModified = false

        // 🆕 v1.8.0: Collect server note IDs for deletion detection
        val serverNoteIds = mutableSetOf<String>()
        // 🛡️ v1.8.2 (IMPL_21): Track download errors statt sie zu verschlucken
        var downloadException: Exception? = null

        try {
            // 🆕 PHASE 1: Download from /{syncFolder}/ (configurable since v1.9.0)
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            Logger.d(TAG, "🔍 Phase 1: Checking /$activeSyncFolderName/ at: $notesUrl")

            // ⚡ v1.3.1: Performance - Get last sync time for skip optimization
            val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
            var skippedUnchanged = 0

            if (sardine.exists(notesUrl)) {
                Logger.d(TAG, "   ✅ /$activeSyncFolderName/ exists, scanning...")
                val resources = sardine.list(notesUrl)
                val jsonFiles = resources.filter { !it.isDirectory && it.name.endsWith(".json") }
                Logger.d(TAG, "   📊 Found ${jsonFiles.size} JSON files on server")

                // 🆕 v1.8.0: Extract server note IDs
                jsonFiles.forEach { resource ->
                    val noteId = resource.name.removeSuffix(".json")
                    serverNoteIds.add(noteId)
                }

                // ════════════════════════════════════════════════════════════════
                // 🆕 v1.8.0: PHASE 1A - Collect Download Tasks
                // ════════════════════════════════════════════════════════════════
                val downloadTasks = mutableListOf<DownloadTask>()

                for (resource in jsonFiles) {
                    currentCoroutineContext().ensureActive()  // 🆕 v1.10.0-P2: FGS cancel checkpoint
                    val noteId = resource.name.removeSuffix(".json")
                    val noteUrl = notesUrl.trimEnd('/') + "/" + resource.name

                    // 🔧 v1.10.0: UUID-Format-Check — fremde JSONs (z.B. google-services.json) überspringen
                    if (!UUID_REGEX.matches(noteId)) {
                        Logger.d(TAG, "   ⏭️ Skipping non-note JSON: ${resource.name}")
                        continue
                    }

                    // ⚡ v1.3.1: HYBRID PERFORMANCE - Timestamp + E-Tag (like Markdown!)
                    val serverETag = resource.etag
                    val cachedETag = eTagCache.getJsonETag(noteId)
                    val serverModified = resource.modified?.time ?: 0L

                    // 🐛 DEBUG: Log every file check to diagnose performance
                    val serverETagPreview = serverETag?.take(ETAG_PREVIEW_LENGTH) ?: "null"
                    val cachedETagPreview = cachedETag?.take(ETAG_PREVIEW_LENGTH) ?: "null"
                    Logger.d(
                        TAG,
                        "   🔍 [$noteId] etag=$serverETagPreview/$cachedETagPreview " +
                            "modified=$serverModified lastSync=$lastSyncTime"
                    )

                    // FIRST: Check deletion tracker - if locally deleted, skip unless re-created on server
                    if (deletionTracker.isDeleted(noteId)) {
                        val deletedAt = deletionTracker.getDeletionTimestamp(noteId)

                        // Smart check: Was note re-created on server after deletion?
                        if (deletedAt != null && serverModified > deletedAt) {
                            Logger.d(TAG, "   📝 Note re-created on server after deletion: $noteId")
                            deletionTracker.removeDeletion(noteId)
                            trackerModified = true
                            // Continue with download below
                        } else {
                            Logger.d(TAG, "   ⏭️ Skipping deleted note: $noteId")
                            skippedDeleted++
                            processedIds.add(noteId)
                            continue
                        }
                    }

                    // Check if file exists locally
                    val localNote = storage.loadNote(noteId)
                    val fileExistsLocally = localNote != null

                    // 🛡️ v1.8.2 (IMPL_23): E-Tag ist PRIMARY — Timestamp nur Fallback
                    // E-Tag ist die einzige zuverlässige Methode um Inhaltsänderungen zu erkennen.
                    // Timestamp-Check kann Änderungen von anderen Geräten verpassen wenn
                    // serverModified < lastSyncTime (Uhren-Drift, Granularität).

                    // PRIMARY: E-Tag check — erkennt Inhaltsänderungen zuverlässig
                    if (!forceOverwrite && fileExistsLocally && serverETag != null && serverETag == cachedETag) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: E-Tag match (content unchanged)")
                        processedIds.add(noteId)
                        continue
                    }

                    // SECONDARY: Timestamp fallback — nur wenn kein E-Tag vorhanden
                    // (Erster Sync oder Server liefert keine E-Tags)
                    val noETagAndTimestampUnchanged = !forceOverwrite && fileExistsLocally &&
                        serverETag == null && lastSyncTime > 0 && serverModified <= lastSyncTime
                    if (noETagAndTimestampUnchanged) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: No E-Tag, timestamp unchanged (fallback)")
                        processedIds.add(noteId)
                        continue
                    }

                    // If file doesn't exist locally, always download
                    if (!fileExistsLocally) {
                        Logger.d(TAG, "   📥 File missing locally - forcing download")
                    }

                    // 🐛 DEBUG: Log download reason
                    val downloadReason = when {
                        lastSyncTime == 0L -> "First sync ever"
                        serverModified > lastSyncTime && serverETag == null -> "Modified + no server E-Tag"
                        serverModified > lastSyncTime && cachedETag == null -> "Modified + no cached E-Tag"
                        serverModified > lastSyncTime -> "Modified + E-Tag changed"
                        serverETag == null -> "No server E-Tag"
                        cachedETag == null -> "No cached E-Tag"
                        else -> "E-Tag changed"
                    }
                    Logger.d(TAG, "   📥 Downloading $noteId: $downloadReason")

                    // 🆕 v1.8.0: Add to download tasks
                    downloadTasks.add(DownloadTask(
                        noteId = noteId,
                        url = noteUrl,
                        resource = resource,
                        serverETag = serverETag,
                        serverModified = serverModified
                    ))
                }

                Logger.d(TAG, "   📋 ${downloadTasks.size} files to download, $skippedDeleted skipped (deleted), " +
                    "$skippedUnchanged skipped (unchanged)")

                // ════════════════════════════════════════════════════════════════
                // 🆕 v1.8.0: PHASE 1B - Parallel Download
                // ════════════════════════════════════════════════════════════════
                if (downloadTasks.isNotEmpty()) {
                    // 🔧 v1.9.0: Unified parallel setting
                    val maxParallel = prefs.getInt(
                        Constants.KEY_MAX_PARALLEL_CONNECTIONS,
                        Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
                    )

                    val downloader = ParallelDownloader(
                        sardine = sardine,
                        maxParallelDownloads = maxParallel
                    )

                    downloader.onProgress = { completed, total, currentFile ->
                        onProgress(completed, total, currentFile ?: "?")
                    }

                    // 🛡️ v1.8.2 (IMPL_19b): coroutineScope statt runBlocking — ermöglicht Cancellation-Propagation
                    val downloadResults: List<DownloadTaskResult> = coroutineScope {
                        downloader.downloadAll(downloadTasks)
                    }

                    // ════════════════════════════════════════════════════════════════
                    // 🆕 v1.8.0: PHASE 1C - Process Results
                    // ════════════════════════════════════════════════════════════════
                    Logger.d(TAG, "   🔄 Processing ${downloadResults.size} download results")

                    // Batch-collect E-Tags for single write
                    val etagUpdates = mutableMapOf<String, String>()

                    for (result in downloadResults) {
                        when (result) {
                            is DownloadTaskResult.Success -> {
                                val remoteNote = Note.fromJson(result.content)
                                if (remoteNote == null) {
                                    Logger.w(TAG, "   ⚠️ Failed to parse JSON: ${result.noteId}")
                                    continue
                                }

                                // 🔧 v1.9.0 Fix: Validate note ID matches filename
                                // Legitimate notes: filename = "{noteId}.json" → parsed ID == filename
                                // Foreign JSON (e.g. google-services.json) → random UUID ≠ filename
                                if (remoteNote.id != result.noteId) {
                                    Logger.w(TAG, "   ⚠️ Skipping foreign JSON: ${result.noteId}.json " +
                                        "(parsed ID '${remoteNote.id}' doesn't match filename)")
                                    processedIds.add(result.noteId)  // Prevent re-download attempts
                                    continue
                                }

                                processedIds.add(remoteNote.id)
                                val localNote = storage.loadNote(remoteNote.id)

                                when {
                                    localNote == null -> {
                                        // New note from server
                                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ✅ Downloaded from /$activeSyncFolderName/: ${remoteNote.id}")

                                        // ⚡ Batch E-Tag for later
                                        if (result.etag != null) {
                                            etagUpdates["etag_json_${result.noteId}"] = result.etag
                                        }
                                    }
                                    forceOverwrite -> {
                                        // OVERWRITE mode: Always replace regardless of timestamps
                                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ♻️ Overwritten from /$activeSyncFolderName/: ${remoteNote.id}")

                                        if (result.etag != null) {
                                            etagUpdates["etag_json_${result.noteId}"] = result.etag
                                        }
                                    }
                                    localNote.updatedAt < remoteNote.updatedAt -> {
                                        // Remote is newer
                                        if (localNote.syncStatus == SyncStatus.PENDING) {
                                            // Conflict detected
                                            storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                            conflictCount++
                                            Logger.w(TAG, "   ⚠️ Conflict: ${remoteNote.id}")
                                        } else {
                                            // Safe to overwrite
                                            storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                            downloadedCount++
                                            Logger.d(TAG, "   ✅ Updated from /$activeSyncFolderName/: ${remoteNote.id}")

                                            if (result.etag != null) {
                                                etagUpdates["etag_json_${result.noteId}"] = result.etag
                                            }
                                        }
                                    }
                                    else -> {
                                        // 🔧 v1.9.0 (Bug C): E-Tag auch bei "local newer"-Skip cachen
                                        // Verhindert erneutes Herunterladen beim nächsten Sync,
                                        // wenn Server-Inhalt sich nicht geändert hat
                                        if (result.etag != null) {
                                            etagUpdates["etag_json_${result.noteId}"] = result.etag
                                        }
                                    }
                                }
                            }
                            is DownloadTaskResult.Failure -> {
                                Logger.e(TAG, "   ❌ Download failed: ${result.noteId} - ${result.error.message}")
                                // Fehlerhafte Downloads nicht als verarbeitet markieren
                                // → werden beim nächsten Sync erneut versucht
                            }
                            is DownloadTaskResult.Skipped -> {
                                Logger.d(TAG, "   ⏭️ Skipped: ${result.noteId} - ${result.reason}")
                                processedIds.add(result.noteId)
                            }
                        }
                    }

                    // ⚡ Batch-save E-Tags (IMPL_004 optimization)
                    if (etagUpdates.isNotEmpty()) {
                        eTagCache.batchUpdate(etagUpdates)
                    }
                }

                Logger.d(
                    TAG,
                    "   📊 Phase 1: $downloadedCount downloaded, $conflictCount conflicts, " +
                        "$skippedDeleted skipped (deleted), $skippedUnchanged skipped (unchanged)"
                )
            } else {
                Logger.w(TAG, "   ⚠️ /notes/ does not exist, skipping Phase 1")
            }

            // 🆕 PHASE 2: BACKWARD-COMPATIBILITY - Download from Root (old structure v1.2.0)
            // ⚠️ ONLY for restore from server! Normal sync should NOT scan Root
            if (includeRootFallback) {
                val rootUrl = serverUrl.trimEnd('/')
                Logger.d(TAG, "🔍 Phase 2: Checking ROOT at: $rootUrl (v1.2.0 compat)")

                try {
                    val rootResources = sardine.list(rootUrl)
                    Logger.d(TAG, "   📂 Found ${rootResources.size} resources in ROOT")

                    val oldNotes = rootResources.filter { resource ->
                        !resource.isDirectory &&
                        resource.name.endsWith(".json") &&
                        !resource.path.contains("/notes/") &&  // Not from /notes/ subdirectory
                        !resource.path.contains("/notes-md/")  // Not from /notes-md/
                    }

                    Logger.d(TAG, "   🔎 Filtered to ${oldNotes.size} .json files (excluding /notes/ and /notes-md/)")

                    if (oldNotes.isNotEmpty()) {
                        Logger.w(TAG, "⚠️ Found ${oldNotes.size} notes in ROOT (old v1.2.0 structure)")

                        for (resource in oldNotes) {
                            // 🔧 Fix: Build full URL instead of using href directly
                            val noteUrl = rootUrl.trimEnd('/') + "/" + resource.name
                            Logger.d(TAG, "   📄 Processing: ${resource.name} from ${resource.path}")

                            val jsonContent = sardine.get(noteUrl).bufferedReader().use { it.readText() }
                            val remoteNote = Note.fromJson(jsonContent) ?: continue

                            // Skip if already loaded from /notes/
                            if (processedIds.contains(remoteNote.id)) {
                                Logger.d(TAG, "   ⏭️ Skipping ${remoteNote.id} (already loaded from /notes/)")
                                continue
                            }

                            // Check deletion tracker
                            if (deletionTracker.isDeleted(remoteNote.id)) {
                                val deletedAt = deletionTracker.getDeletionTimestamp(remoteNote.id)
                                if (deletedAt != null && remoteNote.updatedAt > deletedAt) {
                                    deletionTracker.removeDeletion(remoteNote.id)
                                    trackerModified = true
                                } else {
                                    Logger.d(TAG, "   ⏭️ Skipping deleted note: ${remoteNote.id}")
                                    skippedDeleted++
                                    continue
                                }
                            }

                            processedIds.add(remoteNote.id)
                            val localNote = storage.loadNote(remoteNote.id)

                            when {
                                localNote == null -> {
                                    storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                    downloadedCount++
                                    Logger.d(TAG, "   ✅ Downloaded from ROOT: ${remoteNote.id}")
                                }
                                forceOverwrite -> {
                                    // OVERWRITE mode: Always replace regardless of timestamps
                                    storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                    downloadedCount++
                                    Logger.d(TAG, "   ♻️ Overwritten from ROOT: ${remoteNote.id}")
                                }
                                localNote.updatedAt < remoteNote.updatedAt -> {
                                    if (localNote.syncStatus == SyncStatus.PENDING) {
                                        storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                        conflictCount++
                                    } else {
                                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ✅ Updated from ROOT: ${remoteNote.id}")
                                    }
                                }
                                else -> {
                                    // Local is newer - do nothing
                                    Logger.d(TAG, "   ⏭️ Local is newer: ${remoteNote.id}")
                                }
                            }
                        }
                        Logger.d(TAG, "   📊 Phase 2 complete: downloaded ${oldNotes.size} notes from ROOT")
                    } else {
                        Logger.d(TAG, "   ℹ️ No old notes found in ROOT")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "⚠️ Failed to scan ROOT directory: ${e.message}", e)
                    Logger.e(TAG, "   Stack trace: ${e.stackTraceToString()}")
                    // Not fatal - new users may not have root access
                }
            } else {
                Logger.d(TAG, "⏭️ Skipping Phase 2 (Root scan) - only enabled for restore from server")
            }

        } catch (e: Exception) {
            Logger.e(TAG, "❌ downloadAll failed", e)
            // 🛡️ v1.8.2 (IMPL_21): Exception merken statt verschlucken —
            // Deletion-Detection + Tracker-Save laufen trotzdem (Safety-Guards greifen)
            downloadException = e
        }

        // Save deletion tracker if modified
        if (trackerModified) {
            storage.saveDeletionTracker(deletionTracker)
            Logger.d(TAG, "💾 Deletion tracker updated")
        }

        // 🆕 v1.8.0: Server-Deletions erkennen (nach Downloads)
        val allLocalNotes = storage.loadAllNotes()
        val deletedOnServerCount = detectDeletions(serverNoteIds, allLocalNotes)

        if (deletedOnServerCount > 0) {
            Logger.d(TAG, "$deletedOnServerCount note(s) detected as deleted on server")
        }

        Logger.d(TAG, "📊 Total: $downloadedCount downloaded, $conflictCount conflicts, $skippedDeleted deleted")
        return DownloadResult(
            downloadedCount = downloadedCount,
            conflictCount = conflictCount,
            deletedOnServerCount = deletedOnServerCount,
            downloadFailed = downloadException != null,
            downloadError = downloadException?.message
        )
    }

    /**
     * 🆕 v1.8.0: Erkennt Notizen, die auf dem Server gelöscht wurden.
     * 🔧 v1.8.1: Safety-Guard gegen leere serverNoteIds (verhindert Massenlöschung).
     *
     * Keine zusätzlichen HTTP-Requests! Nutzt die bereits geladene
     * serverNoteIds-Liste aus dem PROPFIND-Request.
     *
     * @param serverNoteIds Set aller Note-IDs auf dem Server (aus PROPFIND)
     * @param localNotes Alle lokalen Notizen
     * @return Anzahl der als DELETED_ON_SERVER markierten Notizen
     */
    fun detectDeletions(
        serverNoteIds: Set<String>,
        localNotes: List<Note>
    ): Int {
        val syncedNotes = localNotes.filter { it.syncStatus == SyncStatus.SYNCED }

        // 🔧 v1.8.1 SAFETY: Wenn serverNoteIds leer ist, NIEMALS Notizen als gelöscht markieren!
        // Ein leeres Set bedeutet wahrscheinlich: PROPFIND fehlgeschlagen, /notes/ nicht gefunden,
        // oder Netzwerkfehler — NICHT dass alle Notizen gelöscht wurden.
        if (serverNoteIds.isEmpty()) {
            Logger.w(TAG, "⚠️ detectDeletions: serverNoteIds is EMPTY! " +
                "Skipping deletion detection to prevent data loss. " +
                "localSynced=${syncedNotes.size}, localTotal=${localNotes.size}")
            return 0
        }

        // 🔧 v1.9.0: Guard-Schwellenwert auf ≥10 angehoben
        // Vorher: syncedNotes.size > 1 — blockierte legitime Massenlöschung bei 2–5 Notizen
        // User mit wenigen Notizen, die alle über Nextcloud-Web-UI löschen, bekamen nie
        // DELETED_ON_SERVER. Bei ≥10 Notizen ist "alle gleichzeitig gelöscht" sehr unwahrscheinlich.
        val potentialDeletions = syncedNotes.count { it.id !in serverNoteIds }
        if (syncedNotes.size >= ALL_DELETED_GUARD_THRESHOLD && potentialDeletions == syncedNotes.size) {
            Logger.e(TAG, "🚨 detectDeletions: ALL ${syncedNotes.size} synced notes " +
                "would be marked as deleted! This is almost certainly a bug. " +
                "serverNoteIds=${serverNoteIds.size}. ABORTING deletion detection.")
            return 0
        }

        // 🆕 v1.8.0 (IMPL_022): Statistik-Log für Debugging
        Logger.d(TAG, "🔍 detectDeletions: " +
            "serverNotes=${serverNoteIds.size}, " +
            "localSynced=${syncedNotes.size}, " +
            "localTotal=${localNotes.size}")

        var deletedCount = 0
        syncedNotes.forEach { note ->
            // Nur SYNCED-Notizen prüfen:
            // - LOCAL_ONLY: War nie auf Server → irrelevant
            // - PENDING: Soll hochgeladen werden → nicht überschreiben
            // - CONFLICT: Wird separat behandelt
            // - DELETED_ON_SERVER: Bereits markiert
            if (note.id !in serverNoteIds) {
                val updatedNote = note.copy(syncStatus = SyncStatus.DELETED_ON_SERVER)
                storage.saveNote(updatedNote)
                deletedCount++

                Logger.d(TAG, "🗑️ Note '${note.title}' (${note.id}) " +
                    "was deleted on server, marked as DELETED_ON_SERVER")
            }
        }

        if (deletedCount > 0) {
            Logger.d(TAG, "📊 Server deletion detection complete: " +
                "$deletedCount of ${syncedNotes.size} synced notes deleted on server")
        }

        return deletedCount
    }

    /**
     * Deletes a note from the server (JSON + Markdown).
     * Does NOT delete from local storage!
     *
     * v1.4.1: Supports v1.2.0 compatibility mode — also checks ROOT folder
     * for notes created before the /notes/ directory structure.
     *
     * Moved from WebDavSyncService.deleteNoteFromServer() in v2.0.0 (Commit 21).
     *
     * @param noteId The ID of the note to delete
     * @return true if at least one file was deleted (or already absent), false on error
     */
    suspend fun deleteFromServer(noteId: String): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            val sardine = connectionManager.getOrCreateClient() ?: return@withContext false
            val serverUrl = urlBuilder.getServerUrl() ?: return@withContext false

            var deletedJson = false
            var deletedMd = false

            // v1.4.1: Try to delete JSON from configured sync folder first (standard path)
            val jsonUrl = urlBuilder.getNotesUrl(serverUrl) + "$noteId.json"
            if (sardine.exists(jsonUrl)) {
                sardine.delete(jsonUrl)
                deletedJson = true
                Logger.d(TAG, "🗑️ Deleted from server: $noteId.json (from /$activeSyncFolderName/)")
            } else {
                // v1.4.1: Fallback - check ROOT folder for v1.2.0 compatibility
                val rootJsonUrl = serverUrl.trimEnd('/') + "/$noteId.json"
                Logger.d(TAG, "🔍 JSON not in /notes/, checking ROOT: $rootJsonUrl")
                if (sardine.exists(rootJsonUrl)) {
                    sardine.delete(rootJsonUrl)
                    deletedJson = true
                    Logger.d(TAG, "🗑️ Deleted from server: $noteId.json (from ROOT - v1.2.0 compat)")
                }
            }

            // Delete Markdown (v1.3.0: YAML-scan based approach)
            val mdBaseUrl = urlBuilder.getMarkdownUrl(serverUrl)
            val note = storage.loadNote(noteId)
            var mdFilenameToDelete: String? = null

            if (note != null) {
                // Fast path: Note still exists locally, use title
                mdFilenameToDelete = markdownSyncManager.sanitizeFilename(note.title) + ".md"
                Logger.d(TAG, "🔍 MD deletion: Using title from local note: $mdFilenameToDelete")
            } else {
                // Fallback: Note deleted locally, scan YAML frontmatter
                Logger.d(TAG, "⚠️ MD deletion: Note not found locally, scanning YAML...")
                mdFilenameToDelete = markdownSyncManager.findByNoteId(sardine, mdBaseUrl, noteId)
            }

            if (mdFilenameToDelete != null) {
                val mdUrl = mdBaseUrl.trimEnd('/') + "/" + mdFilenameToDelete
                if (sardine.exists(mdUrl)) {
                    sardine.delete(mdUrl)
                    deletedMd = true
                    Logger.d(TAG, "🗑️ Deleted from server: $mdFilenameToDelete")
                } else {
                    Logger.w(TAG, "⚠️ MD file not found: $mdFilenameToDelete")
                }
            } else {
                Logger.w(TAG, "⚠️ Could not determine MD filename for note $noteId")
            }

            if (!deletedJson && !deletedMd) {
                // 🔧 v1.9.0 Fix: Note nicht auf Server = bereits gelöscht = Ziel erreicht
                Logger.w(TAG, "⚠️ Note $noteId not found on server (treating as already deleted)")
                return@withContext true
            }

            // Remove from deletion tracker (was explicitly deleted from server)
            val deletionTracker = storage.loadDeletionTracker()
            if (deletionTracker.isDeleted(noteId)) {
                deletionTracker.removeDeletion(noteId)
                storage.saveDeletionTracker(deletionTracker)
                Logger.d(TAG, "🔓 Removed from deletion tracker: $noteId")
            }

            // 🆕 v1.9.0 (Opt 5): Content-Hash und E-Tag bei Deletion invalidieren
            eTagCache.clearForNote(noteId)
            prefs.edit()
                .remove("content_hash_$noteId")
                .remove("content_hash_md_$noteId")
                .apply()
            Logger.d(TAG, "🗑️ Cleared E-Tag + content hash for deleted note: $noteId")

            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete note from server: $noteId", e)
            false
        }
    }
}
