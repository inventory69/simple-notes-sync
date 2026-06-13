package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.parallel.UploadTaskResult
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 🆕 v2.0.0: Extrahiert Upload-Logik aus WebDavSyncService.
 * Verantwortlich für Batch- und Einzel-Uploads von Notizen auf den WebDAV-Server.
 */
@Suppress("LongParameterList") // Parameter spiegeln NoteDownloader — beide benötigen folderStore für Local-Only-Filter
internal class NoteUploader(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val ioDispatcher: CoroutineDispatcher,
    private val folderStore: FolderStore, // 🆕 v2.8.0 (Local-Only Folders)
    private val markdownExporter: ((Sardine, String, Note, Boolean) -> Unit)? = null,
    // 🆕 v2.9.0 (Trash): löscht den Server-MD-Spiegel getrashter Notizen statt sie zu exportieren.
    private val markdownDeleter: ((Sardine, String, Note) -> Unit)? = null
) {
    companion object {
        private const val TAG = "NoteUploader"
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val LOG_PREVIEW_IDS_MAX = 3
    }

    /**
     * Upload aller lokalen Notizen mit Status LOCAL_ONLY oder PENDING.
     *
     * Optimierungen:
     * - Opt 1: Einmalige notes-md/ Exists-Prüfung statt N × exists()
     * - Opt 3: Parallelisierung mit Semaphore über alle Notizen
     * - Opt 4: Batch-E-Tag-Fetch per list(depth=1) nach allen Uploads
     * - Opt 5: Upload-Skip per Content-Hash
     */
    @Suppress("NestedBlockDepth")
    suspend fun uploadAll(
        sardine: Sardine,
        serverUrl: String,
        onProgress: (current: Int, total: Int, noteTitle: String) -> Unit = { _, _, _ -> }
    ): UploadBatchResult {
        val localNotes = storage.loadAllNotes()
        val markdownExportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        Logger.d(TAG, "📋 Markdown export enabled: $markdownExportEnabled") // 🔧 v2.2.1 (Issue #50)

        // 🆕 v1.9.0 (Opt 1): Einmalige Prüfung statt N × exists(notes-md/)
        val markdownDirExists: Boolean = if (markdownExportEnabled) {
            try {
                val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)
                val exists = sardine.exists(mdUrl)
                if (!exists) {
                    sardine.createDirectory(mdUrl)
                    Logger.d(TAG, "📁 Created notes-md/ directory (one-time check)")
                }
                true
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ notes-md/ check failed, falling back to per-note check: ${e.message}")
                false
            }
        } else {
            true
        }

        // 🆕 v2.8.0 (Local-Only Folders): Notizen in "nur lokal"-Ordnern nie hochladen.
        // Case-insensitiver Vergleich — Ordnernamen sind im FolderStore case-insensitiv eindeutig.
        val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
        val pendingNotes = localNotes.filter {
            (it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.PENDING)
                && it.folderName?.lowercase() !in localOnlyFolders
        }
        val totalToUpload = pendingNotes.size

        if (totalToUpload == 0) {
            Logger.d(TAG, "⏭️ No notes to upload")
            return UploadBatchResult(uploadedCount = 0, markdownExportedNoteIds = emptySet())
        }

        // 🆕 v2.7.0 (Folders): benötigte Subdirectories einmalig anlegen (vor parallelem Upload).
        // SafeSardineWrapper.createDirectory toleriert 405 (existiert) und macht list()-Fallback bei 404.
        val foldersToCreate = pendingNotes.mapNotNull { it.folderName }.distinct()
        for (folder in foldersToCreate) {
            try {
                sardine.createDirectory(urlBuilder.getNotesFolderUrl(serverUrl, folder))
                Logger.d(TAG, "📁 Ensured folder dir: $folder")
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ createDirectory for folder '$folder' failed: ${e.message}")
            }
        }

        Logger.d(TAG, "🚀 Starting parallel upload: $totalToUpload notes")

        // 🔧 v1.9.0: Unified parallel setting, capped for uploads
        val maxParallelSetting = prefs.getInt(
            Constants.KEY_MAX_PARALLEL_CONNECTIONS,
            Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        )
        val maxParallel = maxParallelSetting.coerceAtMost(Constants.MAX_PARALLEL_UPLOADS_CAP)
        val semaphore = Semaphore(maxParallel)
        val completedCount = AtomicInteger(0)

        // 🔒 v1.9.0: Mutex für thread-sichere storage.saveNote()-Aufrufe
        val storageMutex = Mutex()

        // 🔒 v1.9.0 (Bug B): Mutex für thread-sicheren MD-Export
        // Verhindert Race Condition wenn 2+ Notizen denselben Titel haben:
        // Ohne Mutex: beide prüfen exists() → false → beide schreiben → Überschreibung
        val mdExportMutex = Mutex()

        val results: List<UploadTaskResult> = coroutineScope {
            val jobs = pendingNotes.map { note ->
                async(ioDispatcher) {
                    semaphore.withPermit {
                        val result = uploadSingle(
                            sardine = sardine,
                            serverUrl = serverUrl,
                            note = note,
                            markdownExportEnabled = markdownExportEnabled,
                            markdownDirExists = markdownDirExists,
                            storageMutex = storageMutex,
                            mdExportMutex = mdExportMutex
                        )

                        // Progress-Update thread-safe via AtomicInteger
                        val completed = completedCount.incrementAndGet()
                        onProgress(completed, totalToUpload, note.title)

                        result
                    }
                }
            }

            jobs.awaitAll()
        }

        // Statistiken
        val successCount = results.count { it is UploadTaskResult.Success }
        val failureCount = results.count { it is UploadTaskResult.Failure }
        val skippedCount = results.count { it is UploadTaskResult.Skipped }
        Logger.d(TAG, "📊 Upload complete: $successCount success, $failureCount failed, $skippedCount skipped")

        // 🆕 v1.9.0 (Opt 4): Batch-E-Tag-Fetch per list(depth=1)
        val successfulNoteIds = results
            .filterIsInstance<UploadTaskResult.Success>()
            .map { it.noteId }
            .toSet()

        if (successfulNoteIds.isNotEmpty()) {
            try {
                // 🆕 v2.7.0 (Folders): pro Ordner (inkl. Root = null) listen, der erfolgreiche Uploads hatte.
                val foldersWithUploads: Set<String?> = results
                    .filterIsInstance<UploadTaskResult.Success>()
                    .mapNotNull { res -> pendingNotes.find { it.id == res.noteId } }
                    .map { it.folderName }
                    .toSet()

                Logger.d(
                    TAG,
                    "⚡ Batch-fetching E-Tags via list(depth=1) for ${successfulNoteIds.size} notes " +
                        "across ${foldersWithUploads.size} folder(s)"
                )

                val batchEtagUpdates = mutableMapOf<String, String?>()
                for (folder in foldersWithUploads) {
                    val folderUrl = urlBuilder.getNotesFolderUrl(serverUrl, folder)
                    val allResources = sardine.list(folderUrl, 1)
                    for (resource in allResources) {
                        val filename = resource.name
                        if (!filename.endsWith(".json")) continue
                        val noteId = filename.removeSuffix(".json")
                        if (noteId in successfulNoteIds) {
                            val etag = resource.etag
                            batchEtagUpdates["etag_json_$noteId"] = etag
                            if (etag != null) {
                                Logger.d(TAG, "   ⚡ E-Tag: $noteId → ${etag.take(ETAG_PREVIEW_LENGTH)}")
                            }
                        }
                    }
                }

                // Fehlende E-Tags invalidieren
                val foundIds = batchEtagUpdates.keys.map { it.removePrefix("etag_json_") }.toSet()
                val missingEtags = successfulNoteIds - foundIds
                if (missingEtags.isNotEmpty()) {
                    Logger.w(
                        TAG,
                        "⚠️ No E-Tag found for ${missingEtags.size} notes: ${missingEtags.take(LOG_PREVIEW_IDS_MAX)}"
                    )
                    for (noteId in missingEtags) {
                        batchEtagUpdates["etag_json_$noteId"] = null
                    }
                }

                // Content-Hashes für erfolgreiche Uploads speichern (unverändert)
                for (noteId in successfulNoteIds) {
                    val note = pendingNotes.find { it.id == noteId }
                    if (note != null) {
                        batchEtagUpdates["content_hash_$noteId"] = computeContentHash(note)
                    }
                }

                eTagCache.batchUpdate(batchEtagUpdates)
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ Batch E-Tag fetch failed: ${e.message}")
                val invalidationMap = successfulNoteIds.associate { "etag_json_$it" to null as String? }
                eTagCache.batchUpdate(invalidationMap)
            }
        }

        // 🆕 v1.11.0: IDs der Notizen sammeln, für die ein Markdown-Export durchgeführt wurde
        val mdExportedIds = results
            .filterIsInstance<UploadTaskResult.Success>()
            .filter { it.markdownExported }
            .map { it.noteId }
            .toSet()

        if (mdExportedIds.isNotEmpty()) {
            Logger.d(
                TAG,
                "📝 Markdown exported for ${mdExportedIds.size} notes: ${mdExportedIds.take(LOG_PREVIEW_IDS_MAX)}"
            )
        }

        return UploadBatchResult(
            uploadedCount = successCount,
            markdownExportedNoteIds = mdExportedIds
        )
    }

    /**
     * 🆕 v1.9.0: Upload einer einzelnen Notiz für parallele Ausführung.
     *
     * Optimierungen:
     * - Opt 3: Thread-sichere storage.saveNote() via Mutex
     * - Opt 3: Retry mit Exponential Backoff
     * - Opt 5: Upload-Skip per Content-Hash
     * - Opt 6: MD-Upload-Skip per Content-Hash (in exportToMarkdown)
     *
     * @return UploadTaskResult mit Erfolgs-/Fehler-Info
     */
    private suspend fun uploadSingle(
        sardine: Sardine,
        serverUrl: String,
        note: Note,
        markdownExportEnabled: Boolean,
        markdownDirExists: Boolean,
        storageMutex: Mutex,
        mdExportMutex: Mutex
    ): UploadTaskResult {
        val maxRetries = 2
        val retryDelayMs = 500L
        var lastError: Throwable? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val notesUrl = urlBuilder.getNotesFolderUrl(serverUrl, note.folderName)
                val noteUrl = "$notesUrl${note.id}.json"

                // 🆕 v1.9.0 (Opt 5): Skip-Logik per Content-Hash
                val currentHash = computeContentHash(note)
                val cachedHash = prefs.getString("content_hash_${note.id}", null)
                val cachedETag = eTagCache.getJsonETag(note.id)

                if (currentHash == cachedHash && cachedETag != null) {
                    Logger.d(
                        TAG,
                        "   ⏭️ Skipping ${note.id} (content unchanged, hash=${currentHash.take(ETAG_PREVIEW_LENGTH)})"
                    )
                    // Status trotzdem auf SYNCED setzen (war evtl. fälschlich PENDING)
                    if (note.syncStatus != SyncStatus.SYNCED) {
                        storageMutex.withLock {
                            storage.saveNote(note.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                    return UploadTaskResult.Skipped(
                        noteId = note.id,
                        reason = "Content unchanged (hash match)"
                    )
                }

                val noteToUpload = note.copy(syncStatus = SyncStatus.SYNCED)
                val jsonBytes = noteToUpload.toJson().toByteArray()

                Logger.d(TAG, "   📤 Uploading: ${note.id}.json (${note.title}) [attempt ${attempt + 1}]")
                sardine.put(noteUrl, jsonBytes, "application/json")
                Logger.d(TAG, "      ✅ Upload successful")

                // 🔒 Thread-sicherer Storage-Write via Mutex
                storageMutex.withLock {
                    storage.saveNote(noteToUpload)
                }

                // MD-Export (optional, Opt 6: Skip via MD-Hash in exportToMarkdown)
                // 🔒 v1.9.0 (Bug B): Mutex serialisiert MD-Export um Race Condition
                // bei gleichen Titeln zu verhindern (exists+put muss atomar sein)
                var didExportMarkdown = false // 🆕 v1.11.0
                if (markdownExportEnabled) {
                    mdExportMutex.withLock {
                        try {
                            if (noteToUpload.isTrashed) {
                                // 🆕 v2.9.0 (Trash): MD-Export überspringen, Server-MD stattdessen löschen.
                                markdownDeleter?.invoke(sardine, serverUrl, noteToUpload)
                                Logger.d(TAG, "   🗑️ MD deleted (trashed): ${noteToUpload.title}")
                            } else {
                                markdownExporter?.invoke(sardine, serverUrl, noteToUpload, markdownDirExists)
                                didExportMarkdown = true // 🆕 v1.11.0
                                Logger.d(TAG, "   📝 MD exported: ${noteToUpload.title}")
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "MD-Export/-Delete failed for ${noteToUpload.id}: ${e.message}")
                        }
                    }
                }

                // 🆕 v1.11.0: markdownExported-Flag für Import-Exclusion
                return UploadTaskResult.Success(
                    noteId = note.id,
                    etag = null,
                    markdownExported = didExportMarkdown
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 🛡️ Cancellation nie verschlucken — sofort propagieren
                throw e
            } catch (e: Exception) {
                lastError = e
                Logger.w(TAG, "⚠️ Upload failed ${note.id} (attempt ${attempt + 1}): ${e.message}")

                if (attempt < maxRetries) {
                    delay(retryDelayMs * (attempt + 1))
                }
            }
        }

        // Alle Retries fehlgeschlagen → Note als PENDING markieren
        Logger.e(TAG, "❌ Upload failed after ${maxRetries + 1} attempts: ${note.id}")
        try {
            storageMutex.withLock {
                storage.saveNote(note.copy(syncStatus = SyncStatus.PENDING))
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to mark note as PENDING: ${e.message}")
        }
        return UploadTaskResult.Failure(note.id, lastError ?: Exception("Unknown upload error"))
    }

    /**
     * 🆕 v1.9.0 (Opt 5): Berechnet SHA-256-Hash des JSON-Inhalts einer Notiz.
     *
     * Verwendet den serialisierten JSON-String (nicht den Rohinhalt),
     * damit Strukturänderungen (z.B. neues Feld) erkannt werden.
     * SyncStatus wird auf SYNCED normalisiert, damit der Hash unabhängig
     * vom aktuellen syncStatus ist.
     *
     * Sichtbarkeit `internal` für Testbarkeit aus dem test-Source-Set.
     *
     * @param note Die Notiz
     * @return Hex-String des SHA-256-Hash (64 Zeichen)
     */
    internal fun computeContentHash(note: Note): String {
        val normalizedNote = note.copy(syncStatus = SyncStatus.SYNCED)
        val jsonBytes = normalizedNote.toJson().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
