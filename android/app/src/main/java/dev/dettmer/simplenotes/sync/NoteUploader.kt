package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.parallel.UploadTaskResult
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🆕 v2.0.0: Extrahiert Upload-Logik aus WebDavSyncService.
 * Verantwortlich für Batch- und Einzel-Uploads von Notizen auf den WebDAV-Server.
 */
internal class NoteUploader(
    private val prefs: SharedPreferences,
    private val storage: NotesStorage,
    private val eTagCache: ETagCache,
    private val urlBuilder: SyncUrlBuilder,
    private val ioDispatcher: CoroutineDispatcher,
    private val markdownExporter: ((Sardine, String, Note, Boolean) -> Unit)? = null
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

        val pendingNotes = localNotes.filter {
            it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.PENDING
        }
        val totalToUpload = pendingNotes.size

        if (totalToUpload == 0) {
            Logger.d(TAG, "⏭️ No notes to upload")
            return UploadBatchResult(uploadedCount = 0, markdownExportedNoteIds = emptySet())
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
                val notesUrl = urlBuilder.getNotesUrl(serverUrl)
                Logger.d(TAG, "⚡ Batch-fetching E-Tags via list(depth=1) for ${successfulNoteIds.size} notes")
                val allResources = sardine.list(notesUrl, 1)

                val batchEtagUpdates = mutableMapOf<String, String?>()
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

                // Fehlende E-Tags invalidieren
                val foundIds = batchEtagUpdates.keys.map { it.removePrefix("etag_json_") }.toSet()
                val missingEtags = successfulNoteIds - foundIds
                if (missingEtags.isNotEmpty()) {
                    Logger.w(TAG, "⚠️ No E-Tag found for ${missingEtags.size} notes: ${missingEtags.take(LOG_PREVIEW_IDS_MAX)}")
                    for (noteId in missingEtags) {
                        batchEtagUpdates["etag_json_$noteId"] = null
                    }
                }

                // 🆕 v1.9.0 (Opt 5): Content-Hashes für erfolgreiche Uploads speichern
                for (noteId in successfulNoteIds) {
                    val note = pendingNotes.find { it.id == noteId }
                    if (note != null) {
                        batchEtagUpdates["content_hash_$noteId"] = computeContentHash(note)
                    }
                }

                eTagCache.batchUpdate(batchEtagUpdates)
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ Batch E-Tag fetch failed: ${e.message}")
                // Fallback: Invalidiere alle E-Tags → nächster Sync holt sie einzeln
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
            Logger.d(TAG, "📝 Markdown exported for ${mdExportedIds.size} notes: ${mdExportedIds.take(LOG_PREVIEW_IDS_MAX)}")
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
                val notesUrl = urlBuilder.getNotesUrl(serverUrl)
                val noteUrl = "$notesUrl${note.id}.json"

                // 🆕 v1.9.0 (Opt 5): Skip-Logik per Content-Hash
                val currentHash = computeContentHash(note)
                val cachedHash = prefs.getString("content_hash_${note.id}", null)
                val cachedETag = eTagCache.getJsonETag(note.id)

                if (currentHash == cachedHash && cachedETag != null) {
                    Logger.d(TAG, "   ⏭️ Skipping ${note.id} (content unchanged, hash=${currentHash.take(ETAG_PREVIEW_LENGTH)})")
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
                var didExportMarkdown = false  // 🆕 v1.11.0
                if (markdownExportEnabled && markdownExporter != null) {
                    mdExportMutex.withLock {
                        try {
                            markdownExporter.invoke(sardine, serverUrl, noteToUpload, markdownDirExists)
                            didExportMarkdown = true  // 🆕 v1.11.0
                            Logger.d(TAG, "   📝 MD exported: ${noteToUpload.title}")
                        } catch (e: Exception) {
                            Logger.e(TAG, "MD-Export failed for ${noteToUpload.id}: ${e.message}")
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
