package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.parallel.UploadTaskResult
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.sync.webdav.etagsMatch
import dev.dettmer.simplenotes.sync.webdav.toIfMatchValue
import dev.dettmer.simplenotes.utils.ActivityLog
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
    private val markdownExporter: ((WebDavClient, String, Note, Boolean) -> Unit)? = null,
    // 🆕 v2.9.0 (Trash): löscht den Server-MD-Spiegel getrashter Notizen statt sie zu exportieren.
    private val markdownDeleter: ((WebDavClient, String, Note) -> Unit)? = null,
    // 🆕 v2.14.0 Self-Heal: 404/409 beim PUT heißt, das persistierte "notes/ existiert"-Flag ist stale.
    private val onMissingServerDir: () -> Unit = {},
    // 🆕 v2.14.0: Quelle der persistierten Dir-Flags. null in Tests, die den Flag-Pfad nicht brauchen.
    private val connectionManager: ConnectionManager? = null
) {
    companion object {
        private const val TAG = "NoteUploader"
        private const val ETAG_PREVIEW_LENGTH = 8

        // 🆕 v2.16.0: Server hat die Datei seit dem letzten Sync geändert (If-Match schlug fehl).
        private const val HTTP_PRECONDITION_FAILED = 412

        // 🆕 v2.16.0: Server kennt das If-Match nicht — einmal ohne Precondition wiederholen
        // und das für diese Server-Config merken.
        private val PRECONDITION_UNSUPPORTED_CODES = setOf(400, 501)
        private const val LOG_PREVIEW_IDS_MAX = 3

        /** 404 = Parent fehlt, 409 = Conflict (Parent-Collection existiert nicht). */
        private val MISSING_DIR_STATUS_CODES = setOf(404, 409)
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
    // Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod", "LongMethod")
    suspend fun uploadAll(
        webdav: WebDavClient,
        serverUrl: String,
        onProgress: (current: Int, total: Int, noteTitle: String) -> Unit = { _, _, _ -> }
    ): UploadBatchResult {
        val localNotes = storage.loadAllNotes()
        val markdownExportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        Logger.d(TAG, "📋 Markdown export enabled: $markdownExportEnabled") // 🔧 v2.2.1 (Issue #50)

        // 🆕 v2.8.0 (Local-Only Folders): Notizen in "nur lokal"-Ordnern nie hochladen.
        // Case-insensitiver Vergleich — Ordnernamen sind im FolderStore case-insensitiv eindeutig.
        val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
        val pendingNotes = localNotes.filter {
            (it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.PENDING) &&
                it.folderName?.lowercase() !in localOnlyFolders
        }
        val totalToUpload = pendingNotes.size

        if (totalToUpload == 0) {
            Logger.d(TAG, "⏭️ No notes to upload")
            return UploadBatchResult(uploadedCount = 0, markdownExportedNoteIds = emptySet())
        }

        // 🆕 v1.9.0 (Opt 1): Einmalige Prüfung statt N × exists(notes-md/)
        // 🆕 v2.14.0: entfällt komplett, wenn das Verzeichnis für die aktuelle Server-Config
        // bereits verifiziert ist (persistiertes Flag) — der Steady-State-Sync macht dann
        // gar keinen Dir-Ensure-Request mehr. Ein 404/409 beim MD-PUT heilt das Flag.
        val markdownDirExists: Boolean = if (markdownExportEnabled && connectionManager?.markdownDirEnsured != true) {
            try {
                val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)
                val exists = webdav.exists(mdUrl)
                if (!exists) {
                    webdav.createDirectory(mdUrl)
                    Logger.d(TAG, "📁 Created notes-md/ directory (one-time check)")
                }
                connectionManager?.markdownDirEnsured = true
                true
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ notes-md/ check failed, falling back to per-note check: ${e.message}")
                false
            }
        } else {
            true
        }

        // 🆕 v2.16.0: Stand des Servers holen, BEVOR irgendetwas geschrieben wird — sonst
        // gewinnt, wer als Zweiter synct, und der Download sieht den Konflikt nie.
        val serverSnapshot = fetchServerSnapshot(webdav, serverUrl, pendingNotes)

        // 🆕 v2.7.0 (Folders): benötigte Subdirectories einmalig anlegen (vor parallelem Upload).
        // WebDavClient.createDirectory toleriert 405 (existiert) und macht list()-Fallback bei 404.
        val foldersToCreate = pendingNotes.mapNotNull { it.folderName }.distinct()
        for (folder in foldersToCreate) {
            try {
                webdav.createDirectory(urlBuilder.getNotesFolderUrl(serverUrl, folder))
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
                            webdav = webdav,
                            serverUrl = serverUrl,
                            note = note,
                            markdownExportEnabled = markdownExportEnabled,
                            markdownDirExists = markdownDirExists,
                            storageMutex = storageMutex,
                            mdExportMutex = mdExportMutex,
                            serverSnapshot = serverSnapshot // 🆕 v2.16.0
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
        val conflictCount = results.count { it is UploadTaskResult.Conflict } // 🆕 v2.16.0
        Logger.d(
            TAG,
            "📊 Upload complete: $successCount success, $failureCount failed, " +
                "$skippedCount skipped, $conflictCount conflicts"
        )

        val successes = results.filterIsInstance<UploadTaskResult.Success>()
        val successfulNoteIds = successes.map { it.noteId }.toSet()

        if (successfulNoteIds.isNotEmpty()) {
            try {
                // 🆕 v2.14.0: ETags aus den PUT-Antworten übernehmen. Nur für Uploads ohne
                // PUT-ETag bleibt der Batch-PROPFIND nötig — und nur in deren Ordnern.
                val batchEtagUpdates = mutableMapOf<String, String?>()
                successes.filter { it.etag != null }.forEach { batchEtagUpdates["etag_json_${it.noteId}"] = it.etag }

                // 🆕 v2.7.0 (Folders): pro Ordner (inkl. Root = null) listen, der erfolgreiche Uploads hatte.
                val foldersWithUploads: Set<String?> = successes
                    .filter { it.etag == null }
                    .mapNotNull { res -> pendingNotes.find { it.id == res.noteId } }
                    .map { it.folderName }
                    .toSet()

                Logger.d(
                    TAG,
                    "⚡ E-Tags: ${batchEtagUpdates.size} from PUT responses, " +
                        "batch-fetching the rest across ${foldersWithUploads.size} folder(s)"
                )

                for (folder in foldersWithUploads) {
                    val folderUrl = urlBuilder.getNotesFolderUrl(serverUrl, folder)
                    val allResources = webdav.list(folderUrl, 1)
                    for (resource in allResources) {
                        val filename = resource.name
                        if (!filename.endsWith(".json")) continue
                        val noteId = filename.removeSuffix(".json")
                        if (noteId in successfulNoteIds) {
                            val etag = resource.etag
                            batchEtagUpdates["etag_json_$noteId"] = etag
                            if (etag != null) {
                                Logger.d(TAG, "   ⚡ E-Tag: $noteId → ${etag.takeLast(ETAG_PREVIEW_LENGTH)}")
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
            markdownExportedNoteIds = mdExportedIds,
            conflictCount = conflictCount // 🆕 v2.16.0
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
    // Trash-Zustand wird schon lokal geloggt (TrashManager) — hier nur echte Content-Uploads.
    private fun logUpload(note: Note) {
        if (note.isTrashed) return
        ActivityLog.log(ActivityLog.Op.UPLOAD, ActivityLog.Src.LOCAL, id = note.id, title = note.title, folder = note.folderName)
    }

    private suspend fun uploadSingle(
        webdav: WebDavClient,
        serverUrl: String,
        note: Note,
        markdownExportEnabled: Boolean,
        markdownDirExists: Boolean,
        storageMutex: Mutex,
        mdExportMutex: Mutex,
        serverSnapshot: ServerSnapshot
    ): UploadTaskResult {
        val maxRetries = 2
        val retryDelayMs = 500L
        var lastError: Throwable? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val notesUrl = urlBuilder.getNotesFolderUrl(serverUrl, note.folderName)
                val noteUrl = "$notesUrl${note.id}.json"

                val cachedETag = eTagCache.getJsonETag(note.id)

                // Skip (Inhalt unverändert) und Konflikt (Server hat sich bewegt) werden beide
                // entschieden, BEVOR irgendetwas geschrieben wird.
                preflight(note, cachedETag, serverSnapshot, storageMutex)?.let { return it }

                val noteToUpload = note.copy(syncStatus = SyncStatus.SYNCED)
                val jsonBytes = noteToUpload.toJson().toByteArray()

                Logger.d(TAG, "   📤 Uploading: ${note.id}.json (${note.title}) [attempt ${attempt + 1}]")
                // 🆕 v2.14.0: ETag direkt aus der PUT-Antwort — spart den Batch-PROPFIND.
                // 🆕 v2.16.0: Mit dem gecachten ETag als If-Match. Ohne die Precondition gewinnt,
                // wer als Zweiter synct — der blinde PUT überschreibt die fremde Fassung, bevor
                // der Download sie überhaupt zu sehen bekommt.
                val putEtag = try {
                    putWithPrecondition(webdav, noteUrl, jsonBytes, cachedETag)
                } catch (e: WebDavException) {
                    // 412 heißt: auf dem Server steht eine Fassung, die diese Änderung nicht kennt.
                    // Kein Retry — der nächste Versuch bekäme dieselbe Antwort.
                    if (e.statusCode == HTTP_PRECONDITION_FAILED) return markConflict(note, storageMutex)
                    throw e
                }
                Logger.d(TAG, "      ✅ Upload successful")

                // 🔒 Thread-sicherer Storage-Write via Mutex
                storageMutex.withLock {
                    storage.saveNote(noteToUpload)
                }
                logUpload(noteToUpload)

                val didExportMarkdown = if (markdownExportEnabled) {
                    syncMarkdownMirror(webdav, serverUrl, noteToUpload, markdownDirExists, mdExportMutex)
                } else {
                    false
                }

                // 🆕 v1.11.0: markdownExported-Flag für Import-Exclusion
                return UploadTaskResult.Success(
                    noteId = note.id,
                    etag = putEtag,
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
        healServerDirFlagIfMissing(lastError)
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
     * Entscheidet vor dem PUT, ob überhaupt hochgeladen wird.
     *
     * @return [UploadTaskResult.Skipped] wenn der Inhalt unverändert ist (v1.9.0, Opt 5),
     *   [UploadTaskResult.Conflict] wenn auf dem Server inzwischen eine andere Fassung liegt
     *   (🆕 v2.16.0), sonst `null` — dann läuft der Upload.
     */
    private suspend fun preflight(
        note: Note,
        cachedETag: String?,
        serverSnapshot: ServerSnapshot,
        storageMutex: Mutex
    ): UploadTaskResult? {
        val currentHash = computeContentHash(note)
        val cachedHash = prefs.getString("content_hash_${note.id}", null)

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
            return UploadTaskResult.Skipped(noteId = note.id, reason = "Content unchanged (hash match)")
        }

        // 🆕 v2.16.0: Der eigentliche Konfliktschutz — vor dem ersten geschriebenen Byte.
        if (isStaleAgainstServer(note, cachedETag, serverSnapshot)) {
            return markConflict(note, storageMutex, why = "server_etag_changed")
        }
        return null
    }

    /**
     * 🆕 v2.16.0: Stand des Servers vor dem Upload, so weit er günstig zu haben ist.
     *
     * @param etags Note-ID → ETag der Server-Datei, aus den gelisteten Ordnern.
     * @param listedFolders Ordner, deren Listing **erfolgreich** war. Nur für die darf aus
     *   "ID fehlt in [etags]" geschlossen werden, dass die Datei serverseitig nicht existiert.
     *   Ein fehlgeschlagenes Listing darf niemals einen Upload blockieren.
     */
    private data class ServerSnapshot(
        val etags: Map<String, String?> = emptyMap(),
        val listedFolders: Set<String?> = emptySet()
    )

    /**
     * 🆕 v2.16.0: Holt die aktuellen Server-ETags der Ordner, in denen etwas hochzuladen ist.
     *
     * Der eigentliche Schutz gegen "wer als Zweiter synct, gewinnt" — und zwar server**unabhängig**:
     * `If-Match` wertet der in `server/README.md` empfohlene Server (hacdias/webdav) beim PUT
     * gar nicht aus, ein falscher Wert kommt dort als `201` zurück. Der Vergleich muss deshalb
     * hier passieren, mit Daten, die jeder WebDAV-Server liefert.
     *
     * Kosten: ein PROPFIND je Ordner, der eine hochzuladende Notiz **mit** gecachtem ETag
     * enthält. Der No-Op-Sync erreicht diese Stelle gar nicht (früher Ausstieg oben), und ein
     * Erst-Upload lauter neuer Notizen listet ebenfalls nichts — ohne gecachten ETag gibt es
     * nichts zu vergleichen.
     */
    private fun fetchServerSnapshot(
        webdav: WebDavClient,
        serverUrl: String,
        pendingNotes: List<Note>
    ): ServerSnapshot {
        val foldersToCheck = pendingNotes
            .filter { !eTagCache.getJsonETag(it.id).isNullOrBlank() }
            .map { it.folderName }
            .toSet()
        if (foldersToCheck.isEmpty()) return ServerSnapshot()

        val etags = mutableMapOf<String, String?>()
        val listed = mutableSetOf<String?>()
        for (folder in foldersToCheck) {
            val resources = try {
                webdav.listOrNull(urlBuilder.getNotesFolderUrl(serverUrl, folder))
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ Pre-upload listing failed for folder=$folder: ${e.message}")
                null
            } ?: continue
            listed.add(folder)
            for (resource in resources) {
                if (resource.isDirectory || !resource.name.endsWith(".json")) continue
                etags[resource.name.removeSuffix(".json")] = resource.etag
            }
        }
        Logger.d(TAG, "🔍 Pre-upload snapshot: ${etags.size} server file(s) in ${listed.size} folder(s)")
        return ServerSnapshot(etags, listed)
    }

    /**
     * 🆕 v2.16.0: Steht auf dem Server eine Fassung, die diese Änderung nicht kennt?
     *
     * Nur dann `true`, wenn das sicher entscheidbar ist. Jeder Zweifelsfall lässt den Upload
     * laufen wie bisher — ein blockierter Upload wäre schlimmer als der fehlende Schutz.
     */
    private fun isStaleAgainstServer(note: Note, cachedETag: String?, snapshot: ServerSnapshot): Boolean {
        // Ohne gecachten ETag gibt es keinen Bezugspunkt: neue Notiz, oder der Cache wurde
        // geleert (clearServerCaches). Beides ist kein Konflikt.
        if (cachedETag.isNullOrBlank()) return false
        // Ordner nicht (erfolgreich) gelistet → kein Urteil.
        if (note.folderName !in snapshot.listedFolders) return false
        // Datei serverseitig nicht vorhanden → es gibt nichts zu überschreiben.
        val serverETag = snapshot.etags[note.id] ?: return false
        // Formattolerant: PUT-Header und PROPFIND-getetag unterscheiden sich in W/ und Quotes.
        return !etagsMatch(serverETag, cachedETag)
    }

    /**
     * MD-Spiegel der Notiz nachziehen (optional, Opt 6: Skip via MD-Hash in exportToMarkdown).
     *
     * 🔒 v1.9.0 (Bug B): Der Mutex serialisiert den MD-Export gegen die Race Condition bei
     * gleichen Titeln — `exists` + `put` müssen atomar sein.
     *
     * @return `true`, wenn tatsächlich exportiert wurde (für die Import-Exclusion, v1.11.0).
     */
    private suspend fun syncMarkdownMirror(
        webdav: WebDavClient,
        serverUrl: String,
        note: Note,
        markdownDirExists: Boolean,
        mdExportMutex: Mutex
    ): Boolean = mdExportMutex.withLock {
        try {
            if (note.isTrashed) {
                // 🆕 v2.9.0 (Trash): MD-Export überspringen, Server-MD stattdessen löschen.
                markdownDeleter?.invoke(webdav, serverUrl, note)
                Logger.d(TAG, "   🗑️ MD deleted (trashed): ${note.title}")
                false
            } else {
                markdownExporter?.invoke(webdav, serverUrl, note, markdownDirExists)
                Logger.d(TAG, "   📝 MD exported: ${note.title}")
                true
            }
        } catch (e: Exception) {
            Logger.e(TAG, "MD-Export/-Delete failed for ${note.id}: ${e.message}")
            false
        }
    }

    /**
     * 🆕 v2.16.0: PUT mit `If-Match` aus dem gecachten ETag.
     *
     * Ein `412` fliegt bewusst weiter zum Aufrufer — nur der weiß, dass daraus ein Konflikt
     * wird. `null` bleibt damit die normale Bedeutung "Server schickt keinen ETag".
     *
     * @return den ETag der Antwort, wie [WebDavClient.put].
     */
    private fun putWithPrecondition(
        webdav: WebDavClient,
        noteUrl: String,
        jsonBytes: ByteArray,
        cachedETag: String?
    ): String? {
        val ifMatch = cachedETag
            // Leerer ETag ist kein ETag — ein `If-Match: ""` würde der Server zu Recht ablehnen.
            ?.takeIf { it.isNotBlank() && connectionManager?.preconditionsUnsupported != true }
            ?.toIfMatchValue()
        if (ifMatch == null) return webdav.put(noteUrl, jsonBytes, "application/json")

        return try {
            webdav.put(noteUrl, jsonBytes, "application/json", ifMatch)
        } catch (e: WebDavException) {
            when (e.statusCode) {
                in PRECONDITION_UNSUPPORTED_CODES -> {
                    // Der Server kann die Precondition nicht auswerten. Einmal ohne wiederholen
                    // und für diese Server-Config merken — ein dauerhaft blockierter Upload wäre
                    // schlimmer als der fehlende Konfliktschutz.
                    Logger.w(TAG, "⚠️ Server rejects If-Match (${e.statusCode}) — retrying without precondition")
                    connectionManager?.preconditionsUnsupported = true
                    webdav.put(noteUrl, jsonBytes, "application/json")
                }
                else -> throw e
            }
        }
    }

    /**
     * 🆕 v2.16.0: Die Server-Fassung ist neuer als die, auf der diese Änderung aufsetzt.
     * Lokale Fassung behalten und markieren — der Nutzer entscheidet (siehe Konflikt-Dialog).
     */
    private suspend fun markConflict(
        note: Note,
        storageMutex: Mutex,
        why: String = "if_match_412"
    ): UploadTaskResult {
        Logger.w(TAG, "   ⚠️ Conflict on upload ($why): ${note.id} — server copy differs")
        if (note.syncStatus != SyncStatus.CONFLICT) {
            storageMutex.withLock {
                storage.saveNote(note.copy(syncStatus = SyncStatus.CONFLICT))
            }
            ActivityLog.log(
                ActivityLog.Op.CONFLICT,
                ActivityLog.Src.LOCAL,
                id = note.id,
                title = note.title,
                folder = note.folderName,
                why = why
            )
        }
        return UploadTaskResult.Conflict(note.id)
    }

    /**
     * 🆕 v2.14.0 Self-Heal: 404/409 vom PUT heißt "Zielverzeichnis fehlt". Hier gezielt auf
     * [WebDavException] prüfen, nicht auf IOException — die erbt davon, und ein Timeout darf
     * das persistierte Flag nicht löschen.
     */
    private fun healServerDirFlagIfMissing(error: Throwable?) {
        if (error is WebDavException && error.statusCode in MISSING_DIR_STATUS_CODES) {
            Logger.d(TAG, "🔄 Server dir flag cleared (PUT → ${error.statusCode})")
            onMissingServerDir()
        }
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
