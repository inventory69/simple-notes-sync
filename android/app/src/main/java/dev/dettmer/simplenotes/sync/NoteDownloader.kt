package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
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
    val folderReconciledCount: Int = 0,
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
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class NoteDownloader(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val connectionManager: ConnectionManager,
    private val markdownSyncManager: MarkdownSyncManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val folderStore: FolderStore // 🆕 v2.7.0 (Folders)
) {
    companion object {
        private const val TAG = "NoteDownloader"
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val FOLDERS_FILE_NAME = "folders.json" // 🆕 v2.7.0 (Folders): von serverNoteIds ausschließen
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
        includeRootFallback: Boolean = false, // 🆕 v1.2.2: Only for restore from server
        forceOverwrite: Boolean = false, // 🆕 v1.3.0: For OVERWRITE_DUPLICATES mode
        deletionTracker: DeletionTracker = storage.loadDeletionTracker(), // 🆕 v1.3.0: Allow passing fresh tracker
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> } // 🆕 v1.8.0
    ): DownloadResult {
        var downloadedCount = 0
        var conflictCount = 0
        var skippedDeleted = 0 // Track skipped deleted notes
        var folderReconciledCount = 0
        val processedIds = mutableSetOf<String>() // 🆕 v1.2.2: Track already loaded notes

        Logger.d(TAG, "📥 downloadAll() called:")
        Logger.d(TAG, "   includeRootFallback: $includeRootFallback")
        Logger.d(TAG, "   forceOverwrite: $forceOverwrite")

        // Use provided deletion tracker (allows fresh tracker from restore)
        var trackerModified = false

        // 🆕 v1.8.0: Collect server note IDs for deletion detection
        val serverNoteIds = mutableSetOf<String>()
        // 🛡️ v1.8.2 (IMPL_21): Track download errors statt sie zu verschlucken
        var downloadException: Exception? = null

        // 🆕 v2.7.0 (Folders): Server-Pfad ist autoritativ für folderName.
        val folderByNoteId = mutableMapOf<String, String?>()
        val discoveredFolders = mutableSetOf<String>()

        try {
            // 🆕 PHASE 1: Download from /{syncFolder}/ (configurable since v1.9.0)
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            Logger.d(TAG, "🔍 Phase 1: Checking /$activeSyncFolderName/ at: $notesUrl")

            // ⚡ v1.3.1: Performance - Get last sync time for skip optimization
            val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
            var skippedUnchanged = 0

            // 🔧 v2.0.0 (Issue #44): Use listOrNull() instead of exists()+list() to avoid
            // false-negative exists() on servers that return 403 for HEAD on collections (Jianguoyun).
            // PROPFIND (list) works universally on all WebDAV servers.
            val notesResources: List<com.thegrizzlylabs.sardineandroid.DavResource>? = when (sardine) {
                is SafeSardineWrapper -> sardine.listOrNull(notesUrl)
                else -> try {
                    sardine.list(notesUrl)
                } catch (_: java.io.IOException) {
                    null
                }
            }

            if (notesResources != null) {
                Logger.d(TAG, "   ✅ /$activeSyncFolderName/ exists, scanning...")

                // 🆕 v2.7.0 (Folders): Root-JSONs (folderName=null) + je Subdir ein zweiter list().
                data class ScanItem(val resource: com.thegrizzlylabs.sardineandroid.DavResource, val folder: String?)
                val scanItems = mutableListOf<ScanItem>()

                // Root-Ebene: nur Top-Level-JSONs der Notes-Basis (ohne href-Self-Eintrag).
                val rootBaseUrl = urlBuilder.getNotesUrl(serverUrl)
                notesResources
                    .filter { !it.isDirectory && it.name.endsWith(".json") && it.name != FOLDERS_FILE_NAME }
                    .forEach { scanItems.add(ScanItem(it, null)) }

                // Subdirectories (Collections), die nicht die Basis selbst sind.
                val subDirs = notesResources.filter { res ->
                    res.isDirectory &&
                        res.name.isNotBlank() &&
                        res.name != "/" &&
                        !rootBaseUrl.trimEnd('/').endsWith("/" + res.name)
                }
                // 🆕 v2.8.0 (Local-Only Folders): einmalig lesen, alle Subdirs damit filtern.
                // Case-insensitiv — Server-Verzeichnisname kann in der Groß-/Kleinschreibung abweichen.
                val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
                for (dir in subDirs) {
                    val folder = dev.dettmer.simplenotes.utils.FolderNameValidator.sanitize(dir.name) ?: continue
                    if (folder.lowercase() in localOnlyFolders) {
                        Logger.d(TAG, "   ⏭️ Skipping local-only folder: $folder")
                        continue
                    }
                    discoveredFolders.add(folder)
                    val folderUrl = urlBuilder.getNotesFolderUrl(serverUrl, folder)
                    val folderResources = when (sardine) {
                        is SafeSardineWrapper -> sardine.listOrNull(folderUrl)
                        else -> try { sardine.list(folderUrl) } catch (_: java.io.IOException) { null }
                    } ?: continue
                    folderResources
                        .filter { !it.isDirectory && it.name.endsWith(".json") }
                        .forEach { scanItems.add(ScanItem(it, folder)) }
                }

                // Deduplizieren: gleiche noteId in mehreren Ordnern (Überbleibsel nach Ordner-Verschiebung)
                // → nur den neuesten Eintrag behalten, damit kein PENDING-Loop entsteht.
                val deduplicatedItems = scanItems
                    .groupBy { it.resource.name.removeSuffix(".json") }
                    .values
                    .mapNotNull { group -> group.maxByOrNull { it.resource.modified?.time ?: 0L } }

                val jsonFiles = deduplicatedItems.map { it.resource }
                Logger.d(TAG, "   📊 Found ${jsonFiles.size} JSON files on server (incl. subfolders)")

                // 🆕 v1.8.0 + v2.6.0: serverNoteIds + folderByNoteId füllen
                deduplicatedItems.forEach { item ->
                    val noteId = item.resource.name.removeSuffix(".json")
                    serverNoteIds.add(noteId)
                    folderByNoteId[noteId] = item.folder
                }

                // ════════════════════════════════════════════════════════════════
                // 🆕 v1.8.0: PHASE 1A - Collect Download Tasks
                // ════════════════════════════════════════════════════════════════
                val downloadTasks = mutableListOf<DownloadTask>()

                for (resource in jsonFiles) {
                    currentCoroutineContext().ensureActive() // 🆕 v1.10.0-P2: FGS cancel checkpoint
                    val noteId = resource.name.removeSuffix(".json")
                    val folderForNote = folderByNoteId[noteId]
                    val noteUrl = urlBuilder.getNotesFolderUrl(serverUrl, folderForNote)
                        .trimEnd('/') + "/" + resource.name

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
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: E-Tag match (content unchanged) [localStatus=${localNote?.syncStatus}]")
                        if (reconcileSkippedNote(localNote, folderByNoteId[noteId])) folderReconciledCount++
                        processedIds.add(noteId)
                        continue
                    }

                    // SECONDARY: Timestamp fallback — nur wenn kein E-Tag vorhanden
                    // (Erster Sync oder Server liefert keine E-Tags)
                    val noETagAndTimestampUnchanged = !forceOverwrite &&
                        fileExistsLocally &&
                        serverETag == null &&
                        lastSyncTime > 0 &&
                        serverModified <= lastSyncTime
                    if (noETagAndTimestampUnchanged) {
                        skippedUnchanged++
                        Logger.d(
                            TAG,
                            "   ⏭️ Skipping $noteId: No E-Tag, timestamp unchanged (fallback)" +
                                " [localStatus=${localNote?.syncStatus}]"
                        )
                        if (reconcileSkippedNote(localNote, folderByNoteId[noteId])) folderReconciledCount++
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
                    downloadTasks.add(
                        DownloadTask(
                            noteId = noteId,
                            url = noteUrl,
                            resource = resource,
                            serverETag = serverETag,
                            serverModified = serverModified
                        )
                    )
                }

                Logger.d(
                    TAG,
                    "   📋 ${downloadTasks.size} files to download, $skippedDeleted skipped (deleted), " +
                        "$skippedUnchanged skipped (unchanged)"
                )

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
                                    Logger.w(
                                        TAG,
                                        "   ⚠️ Skipping foreign JSON: ${result.noteId}.json " +
                                            "(parsed ID '${remoteNote.id}' doesn't match filename)"
                                    )
                                    processedIds.add(result.noteId) // Prevent re-download attempts
                                    continue
                                }

                                processedIds.add(remoteNote.id)
                                val localNote = storage.loadNote(remoteNote.id)

                                // 🆕 v2.7.0 (Folders): Server-Pfad ist autoritativ.
                                val remoteNoteFoldered = remoteNote.copy(
                                    folderName = folderByNoteId[result.noteId]
                                )

                                when {
                                    localNote == null -> {
                                        // New note from server
                                        storage.saveNote(remoteNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ✅ Downloaded from /$activeSyncFolderName/: ${remoteNote.id}")

                                        // ⚡ Batch E-Tag for later
                                        if (result.etag != null) {
                                            etagUpdates["etag_json_${result.noteId}"] = result.etag
                                        }
                                    }
                                    forceOverwrite -> {
                                        // OVERWRITE mode: Always replace regardless of timestamps
                                        storage.saveNote(remoteNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(
                                            TAG,
                                            "   ♻️ Overwritten from /$activeSyncFolderName/: ${remoteNote.id}"
                                        )

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
                                            storage.saveNote(remoteNoteFoldered.copy(syncStatus = SyncStatus.SYNCED))
                                            downloadedCount++
                                            Logger.d(TAG, "   ✅ Updated from /$activeSyncFolderName/: ${remoteNote.id}")

                                            if (result.etag != null) {
                                                etagUpdates["etag_json_${result.noteId}"] = result.etag
                                            }
                                        }
                                    }
                                    else -> {
                                        // Local timestamp is newer or equal to server.
                                        // Only mark PENDING if content actually differs — otherwise
                                        // just refresh the E-Tag. Without this check the upload
                                        // phase skips unchanged notes (hash match) but never
                                        // updates the cached E-Tag, causing an infinite
                                        // download → PENDING → skip-upload → download loop.
                                        if (localNote.syncStatus == SyncStatus.SYNCED ||
                                            localNote.syncStatus == SyncStatus.PENDING
                                        ) {
                                            if (noteContentDiffers(localNote, remoteNote)) {
                                                if (localNote.syncStatus == SyncStatus.SYNCED) {
                                                    storage.saveNote(
                                                        localNote.copy(syncStatus = SyncStatus.PENDING)
                                                    )
                                                    Logger.d(
                                                        TAG,
                                                        "   🔄 ${result.noteId}: local newer with different content " +
                                                            "— marked PENDING for re-upload"
                                                    )
                                                }
                                                // Already PENDING → uploader will handle it next cycle
                                            } else {
                                                // Content identical — server E-Tag changed (e.g. file
                                                // re-saved on another client). Just sync the E-Tag so
                                                // future syncs can skip this note correctly.
                                                if (result.etag != null) {
                                                    etagUpdates["etag_json_${result.noteId}"] = result.etag
                                                }
                                                Logger.d(
                                                    TAG,
                                                    "   ✅ ${result.noteId}: local newer but same content " +
                                                        "— E-Tag refreshed, no upload needed"
                                                )
                                            }
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
                Logger.w(TAG, "   ⚠️ /$activeSyncFolderName/ does not exist (404), skipping Phase 1")
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
                        // Backward compat (v1.2.0): Filter for legacy notes in the default
                        // folder "/notes/" and "/notes-md/". These paths are intentionally
                        // hardcoded to the PRE-custom-folder default, not the current folder
                        // configuration. Legacy notes were always stored in /notes/.
                        val legacyNotesPath = "/${Constants.DEFAULT_SYNC_FOLDER_NAME}/"
                        val legacyMarkdownPath = "/${Constants.DEFAULT_SYNC_FOLDER_NAME}${SyncUrlBuilder.MARKDOWN_SUFFIX}/"
                        !resource.isDirectory &&
                            resource.name.endsWith(".json") &&
                            !resource.path.contains(legacyNotesPath) &&
                            !resource.path.contains(legacyMarkdownPath)
                    }

                    Logger.d(TAG, "   🔎 Filtered to ${oldNotes.size} .json files (excluding legacy paths)")

                    if (oldNotes.isNotEmpty()) {
                        Logger.w(TAG, "⚠️ Found ${oldNotes.size} notes in ROOT (old v1.2.0 structure)")

                        for (resource in oldNotes) {
                            // 🔧 Fix: Build full URL instead of using href directly
                            val noteUrl = rootUrl.trimEnd('/') + "/" + resource.name
                            Logger.d(TAG, "   📄 Processing: ${resource.name} from ${resource.path}")

                            // 🔧 v2.3.0 (Issue #62): UUID-Format-Check auf Dateinamen —
                            // spiegelt Phase 1 (siehe UUID_REGEX-Check oben). Verhindert
                            // Parsing von Fremd-JSONs im Root (info.json, google-services.json, …).
                            val rootNoteId = resource.name.removeSuffix(".json")
                            if (!UUID_REGEX.matches(rootNoteId)) {
                                Logger.d(TAG, "   ⏭️ Skipping non-note JSON in ROOT: ${resource.name}")
                                continue
                            }

                            val jsonContent = sardine.get(noteUrl).use { it.bufferedReader().readText() }
                            val remoteNote = Note.fromJson(jsonContent) ?: continue

                            // 🔧 v2.3.0 (Issue #62): ID-Mismatch-Check — spiegelt Phase 1C.
                            // Legitimate notes: filename "{noteId}.json" → parsed ID == filename.
                            // Foreign JSON with random UUID default from NoteRaw → parsed ID ≠ filename.
                            if (remoteNote.id != rootNoteId) {
                                Logger.w(
                                    TAG,
                                    "   ⚠️ Skipping foreign JSON in ROOT: ${resource.name} " +
                                        "(parsed ID '${remoteNote.id}' doesn't match filename)"
                                )
                                processedIds.add(rootNoteId) // Prevent re-processing
                                continue
                            }

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

        // 🆕 v2.7.0 (Folders): entdeckte Server-Ordner lokal registrieren (auch leere).
        if (discoveredFolders.isNotEmpty()) {
            folderStore.addFolders(discoveredFolders)
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
            folderReconciledCount = folderReconciledCount,
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
    suspend fun detectDeletions(serverNoteIds: Set<String>, localNotes: List<Note>): Int {
        // 🆕 v2.8.0 (Local-Only Folders): Notizen in lokalen Ordnern nie als server-gelöscht markieren.
        val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
        val syncedNotes = localNotes.filter {
            it.syncStatus == SyncStatus.SYNCED && it.folderName?.lowercase() !in localOnlyFolders
        }

        // 🔧 v1.8.1 SAFETY: Wenn serverNoteIds leer ist, NIEMALS Notizen als gelöscht markieren!
        // Ein leeres Set bedeutet wahrscheinlich: PROPFIND fehlgeschlagen, /notes/ nicht gefunden,
        // oder Netzwerkfehler — NICHT dass alle Notizen gelöscht wurden.
        if (serverNoteIds.isEmpty()) {
            Logger.w(
                TAG,
                "⚠️ detectDeletions: serverNoteIds is EMPTY! " +
                    "Skipping deletion detection to prevent data loss. " +
                    "localSynced=${syncedNotes.size}, localTotal=${localNotes.size}"
            )
            return 0
        }

        // 🔧 v1.9.0: Guard-Schwellenwert auf ≥10 angehoben
        // Vorher: syncedNotes.size > 1 — blockierte legitime Massenlöschung bei 2–5 Notizen
        // User mit wenigen Notizen, die alle über Nextcloud-Web-UI löschen, bekamen nie
        // DELETED_ON_SERVER. Bei ≥10 Notizen ist "alle gleichzeitig gelöscht" sehr unwahrscheinlich.
        // 🆕 v2.8.0: Schwellenwert bewusst auf die GEFILTERTE Menge (ohne local-only-Ordner) —
        // nur sie kann markiert werden. Ein Gesamt-Count würde z. B. bei 1 fehlenden Notiz +
        // vielen local-only-Notizen legitime Einzellöschungen dauerhaft blockieren.
        val potentialDeletions = syncedNotes.count { it.id !in serverNoteIds }
        if (syncedNotes.size >= ALL_DELETED_GUARD_THRESHOLD && potentialDeletions == syncedNotes.size) {
            Logger.e(
                TAG,
                "🚨 detectDeletions: ALL ${syncedNotes.size} synced notes " +
                    "would be marked as deleted! This is almost certainly a bug. " +
                    "serverNoteIds=${serverNoteIds.size}. ABORTING deletion detection."
            )
            return 0
        }

        // 🆕 v1.8.0 (IMPL_022): Statistik-Log für Debugging
        Logger.d(
            TAG,
            "🔍 detectDeletions: " +
                "serverNotes=${serverNoteIds.size}, " +
                "localSynced=${syncedNotes.size}, " +
                "localTotal=${localNotes.size}"
        )

        var deletedCount = 0
        for (note in syncedNotes) {
            // Nur SYNCED-Notizen prüfen:
            // - LOCAL_ONLY: War nie auf Server → irrelevant
            // - PENDING: Soll hochgeladen werden → nicht überschreiben
            // - CONFLICT: Wird separat behandelt
            // - DELETED_ON_SERVER: Bereits markiert
            if (note.id !in serverNoteIds) {
                if (note.trashedAt != null) {
                    // 🆕 v2.9.0 (Trash): Die Notiz lag bereits im Papierkorb und ist jetzt auch vom
                    // Server verschwunden → ein anderes Gerät hat sie endgültig gelöscht (purge).
                    // Lokal hart löschen; der DeletionTracker-Eintrag verhindert Zombie-Wiederkehr.
                    storage.deleteNote(note.id)
                    Logger.d(
                        TAG,
                        "🔥 Trashed note '${note.title}' (${note.id}) purged on another device → " +
                            "hard-deleted locally"
                    )
                } else {
                    // 🆕 v2.9.0 (Trash): Echte Server-Löschung → in den Papierkorb verschieben.
                    // KEIN updatedAt-Bump und KEIN PENDING: Ein Upload würde die Notiz auf dem Server
                    // wiederbeleben und mit dem DeletionTracker des anderen Clients ping-pongen. Ein
                    // bewusstes Restore durch den Nutzer setzt später PENDING + neuen Timestamp.
                    val updatedNote = note.copy(
                        syncStatus = SyncStatus.DELETED_ON_SERVER,
                        trashedAt = System.currentTimeMillis()
                    )
                    storage.saveNote(updatedNote)
                    deletedCount++

                    Logger.d(
                        TAG,
                        "🗑️ Note '${note.title}' (${note.id}) " +
                            "was deleted on server → moved to trash (DELETED_ON_SERVER)"
                    )
                }
            }
        }

        if (deletedCount > 0) {
            Logger.d(
                TAG,
                "📊 Server deletion detection complete: " +
                    "$deletedCount of ${syncedNotes.size} synced notes moved to trash"
            )
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
    suspend fun deleteFromServer(noteId: String, folderName: String? = null): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            val sardine = connectionManager.getOrCreateClient() ?: return@withContext false
            val serverUrl = urlBuilder.getServerUrl() ?: return@withContext false

            var deletedJson = false
            var deletedMd = false

            // v1.4.1: Try to delete JSON from configured sync folder first (standard path)
            // 🔧 v2.0.0 (Issue #44): Use try/delete instead of exists()+delete() to avoid
            // false-negative exists() on servers returning 403 for HEAD on collections.
            val jsonUrl = urlBuilder.getNotesFolderUrl(serverUrl, folderName) + "$noteId.json"
            try {
                sardine.delete(jsonUrl)
                deletedJson = true
                Logger.d(TAG, "🗑️ Deleted from server: $noteId.json (from /$activeSyncFolderName/)")
            } catch (e: java.io.IOException) {
                if (e.message?.contains("404") == true) {
                    // v1.4.1: Fallback - check ROOT folder for v1.2.0 compatibility
                    val rootJsonUrl = serverUrl.trimEnd('/') + "/$noteId.json"
                    Logger.d(TAG, "🔍 JSON not in /$activeSyncFolderName/, checking ROOT: $rootJsonUrl")
                    try {
                        sardine.delete(rootJsonUrl)
                        deletedJson = true
                        Logger.d(TAG, "🗑️ Deleted from server: $noteId.json (from ROOT - v1.2.0 compat)")
                    } catch (e2: java.io.IOException) {
                        if (e2.message?.contains("404") != true) throw e2
                        Logger.d(TAG, "ℹ️ $noteId.json not found on server (already gone)")
                    }
                } else {
                    throw e
                }
            }

            // Delete Markdown (v1.3.0: YAML-scan based approach)
            val mdBaseUrl = urlBuilder.getMarkdownFolderUrl(serverUrl, folderName)
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
                // 🔧 v2.0.0 (Issue #44): try/delete instead of exists()+delete()
                try {
                    sardine.delete(mdUrl)
                    deletedMd = true
                    Logger.d(TAG, "🗑️ Deleted from server: $mdFilenameToDelete")
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("404") == true) {
                        Logger.w(TAG, "⚠️ MD file not found on server: $mdFilenameToDelete")
                    } else {
                        throw e
                    }
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
            prefs.edit {
                remove("content_hash_$noteId")
                remove("content_hash_md_$noteId")
            }
            Logger.d(TAG, "🗑️ Cleared E-Tag + content hash for deleted note: $noteId")

            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete note from server: $noteId", e)
            false
        }
    }

    /**
     * Heilt eine übersprungene, lokal vorhandene Notiz, deren Server-File gerade
     * verarbeitet wird (→ die Notiz ist nachweislich auf dem Server vorhanden), ohne
     * Datei-Download:
     *
     * - DELETED_ON_SERVER: Die Notiz liegt in der aktuellen Server-Liste, wurde aber
     *   lokal fälschlich als „gelöscht" markiert (z. B. durch einen v2.7.0-Ordner-Move-
     *   Race auf E-Tag-losen Servern, der den Eintrag für einen Sync-Durchlauf aus der
     *   PROPFIND-Liste fallen ließ; nichts setzt dieses Flag je zurück). Falsch-Flag
     *   löschen: zurück auf SYNCED + autoritativen Server-Ordner übernehmen. Lokaler
     *   Inhalt bleibt erhalten. Echt gelöschte Notizen sind NICHT in der Liste → ihr
     *   File wird nie verarbeitet → dieser Zweig läuft für sie nie.
     * - SYNCED: veraltetes folderName an den Server-Pfad angleichen.
     * - PENDING / LOCAL_ONLY / CONFLICT: echter lokaler Zustand → unangetastet.
     *
     * @return true wenn auf Platte geschrieben wurde (→ UI muss neu laden).
     */
    private suspend fun reconcileSkippedNote(localNote: Note?, serverFolder: String?): Boolean {
        if (localNote == null) return false
        return when (localNote.syncStatus) {
            SyncStatus.DELETED_ON_SERVER -> {
                // updatedAt NICHT bumpen, Status nicht PENDING → kein Re-Upload, keine Schleife
                // 🆕 v2.9.0 (Trash): trashedAt ebenfalls löschen — die Notiz ist nachweislich auf dem
                // Server vorhanden, die „in den Papierkorb verschoben"-Markierung war also falsch.
                storage.saveNote(
                    localNote.copy(
                        syncStatus = SyncStatus.SYNCED,
                        folderName = serverFolder,
                        trashedAt = null
                    )
                )
                Logger.d(
                    TAG,
                    "   🔧 Cleared false DELETED_ON_SERVER for ${localNote.id}: " +
                        "present in server listing → SYNCED, folder '$serverFolder'"
                )
                true
            }
            SyncStatus.SYNCED -> {
                if (localNote.folderName == serverFolder) return false // kein unnötiger Write
                storage.saveNote(localNote.copy(folderName = serverFolder))
                Logger.d(
                    TAG,
                    "   📁 Reconciled folder for ${localNote.id}: " +
                        "'${localNote.folderName}' → '$serverFolder'"
                )
                true
            }
            else -> false // PENDING / LOCAL_ONLY / CONFLICT → lokaler Zustand gewinnt
        }
    }

    /**
     * Returns true if the user-visible content of [a] and [b] differs.
     * Intentionally excludes [Note.updatedAt] and [Note.syncStatus] — only the
     * fields a user can read or interact with are compared.
     */
    private fun noteContentDiffers(a: Note, b: Note): Boolean {
        if (a.noteType != b.noteType) return true
        if (a.title != b.title) return true
        if (a.isPinned != b.isPinned) return true
        if (a.color != b.color) return true
        if (a.folderName != b.folderName) return true
        // 🆕 v2.9.0 (Trash): Trash/Restore ist ein echter Unterschied — sonst schluckt der
        // E-Tag-Refresh-Branch ein Trash/Restore und die Notiz erscheint nicht im Papierkorb.
        if (a.trashedAt != b.trashedAt) return true

        return when (a.noteType) {
            NoteType.CHECKLIST -> {
                val aItems = a.checklistItems.orEmpty()
                val bItems = b.checklistItems.orEmpty()
                if (aItems.size != bItems.size) return true
                aItems.zip(bItems).any { (ai, bi) ->
                    ai.text != bi.text || ai.isChecked != bi.isChecked || ai.order != bi.order
                }
            }
            else -> a.content.trim() != b.content.trim()
        }
    }
}
