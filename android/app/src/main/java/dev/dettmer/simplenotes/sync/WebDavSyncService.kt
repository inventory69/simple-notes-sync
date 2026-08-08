package dev.dettmer.simplenotes.sync

import android.content.Context
import androidx.core.content.edit
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.PendingServerDeletions.PendingDeletion
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.AssetReferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.SyncException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Result of manual Markdown sync operation
 */
data class ManualMarkdownSyncResult(val exportedCount: Int, val importedCount: Int)

/**
 * 🆕 v1.11.0: Ergebnis eines Upload-Durchlaufs.
 * Enthält neben der Anzahl auch die IDs der Notizen, für die ein Markdown-Export
 * durchgeführt wurde. Diese werden an importMarkdownFiles() weitergegeben, um
 * Re-Import der soeben exportierten Dateien zu verhindern.
 */
data class UploadBatchResult(val uploadedCount: Int, val markdownExportedNoteIds: Set<String>)

// Abbau: TECH_DEBT_ROADMAP.md Slice 4
@Suppress("LargeClass", "TooManyFunctions") // Functions extracted into NoteUploader/NoteDownloader/MarkdownSyncManager (v2.0.0)
class WebDavSyncService(private val context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    companion object {
        private const val TAG = "WebDavSyncService"
        private const val HTTP_UNAUTHORIZED = 401

        // � v1.3.1: Mutex um parallele Syncs zu verhindern
        private val syncMutex = Mutex()

        /**
         * Darf [url] gelöscht werden? Nur wenn das PROPFIND das Verzeichnis geliefert hat und es
         * ausser sich selbst nichts enthält.
         *
         * - `entries == null` heisst 404 (siehe [WebDavClient.listOrNull]) — das Verzeichnis
         *   existiert nicht und ist damit *nicht* "leer"; ein DELETE liefe ins Leere (🔧 v2.14.0).
         * - depth=1 liefert das Verzeichnis selbst als Eintrag mit → "leer" = nur Self. Der
         *   Vergleich läuft über den dekodierten Pfad, damit Ordnernamen mit Sonderzeichen
         *   ("Test neu" → "Test%20neu" im href) korrekt als Self erkannt werden.
         */
        internal fun isDeletableEmptyDir(url: String, entries: List<WebDavResource>?): Boolean {
            if (entries == null) return false
            val dirPath = java.net.URI(url).path.trimEnd('/')
            return entries.none { it.href.path.trimEnd('/') != dirPath }
        }
    }

    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gateChecker = SyncGateChecker(context, prefs, ioDispatcher)
    private val eTagCache = ETagCache(prefs)
    private val timestampManager = SyncTimestampManager(prefs)
    private val exceptionMapper = SyncExceptionMapper(context)
    private val urlBuilder = SyncUrlBuilder(prefs)
    private val connectionManager = ConnectionManager(context, prefs)

    /** 🆕 v1.9.0: Configured sync folder name (loaded at sync start). */
    private var activeSyncFolderName: String = Constants.DEFAULT_SYNC_FOLDER_NAME

    init {
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "═══════════════════════════════════════")
            Logger.d(TAG, "🏗️ WebDavSyncService INIT")
            Logger.d(TAG, "Context: ${context.javaClass.simpleName}")
            Logger.d(TAG, "Thread: ${Thread.currentThread().name}")
        }

        try {
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "    Creating NotesStorage...")
            }
            storage = NotesStorage(context)
            if (BuildConfig.DEBUG) {
                Logger.d(TAG, "    ✅ NotesStorage created successfully")
                Logger.d(TAG, "    Notes dir: ${storage.getNotesDir()}")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "💥 CRASH in NotesStorage creation!", e)
            Logger.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
            throw e
        }

        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "    SharedPreferences: $prefs")
            Logger.d(TAG, "✅ WebDavSyncService INIT complete")
            Logger.d(TAG, "═══════════════════════════════════════")
        }
    }

    private val folderStore = dev.dettmer.simplenotes.storage.FolderStore(context) // 🆕 v2.7.0 (Folders)

    private val markdownSyncManager = MarkdownSyncManager(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        connectionManager = connectionManager,
        timestampManager = timestampManager,
        ioDispatcher = ioDispatcher,
        folderStore = folderStore
    )

    private val noteUploader = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        ioDispatcher = ioDispatcher,
        folderStore = folderStore, // 🆕 v2.8.0 (Local-Only Folders)
        markdownExporter = { webdav, serverUrl, note, mdDirExists ->
            markdownSyncManager.exportSingle(webdav, serverUrl, note, mdDirExists)
        },
        // 🆕 v2.9.0 (Trash): getrashte Notiz → Server-MD löschen statt exportieren.
        markdownDeleter = { webdav, serverUrl, note ->
            markdownSyncManager.deleteSingle(webdav, serverUrl, note)
        },
        // 🆕 v2.14.0 Self-Heal: PUT-404/409 → das persistierte notes/-Flag ist stale.
        onMissingServerDir = { connectionManager.notesDirEnsured = false },
        connectionManager = connectionManager
    )

    private val noteDownloader = NoteDownloader(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        connectionManager = connectionManager,
        markdownSyncManager = markdownSyncManager,
        ioDispatcher = ioDispatcher,
        folderStore = folderStore
    )

    private val folderSyncManager = FolderSyncManager( // 🆕 v2.7.0 (Folders)
        urlBuilder = urlBuilder,
        folderStore = folderStore,
        prefs = prefs
    )

    private val deletionSyncManager = DeletionSyncManager(urlBuilder) // 🆕 shared ledger

    // 🆕 Bild-Attachments
    private val assetStore = AssetStore(context)
    private val assetSyncManager = AssetSyncManager(
        prefs = prefs,
        assetStore = assetStore,
        urlBuilder = urlBuilder,
        connectionManager = connectionManager,
        ioDispatcher = ioDispatcher
    )

    /**
     * ⚡ v1.3.1: Gecachten WebDavClient-Client zurückgeben oder erstellen
     * Spart ~100ms pro Aufruf durch Wiederverwendung
     * 🆕 Issue #21: internal für NotesImportWizard-Zugriff
     */
    internal fun getOrCreateWebDavClient(): WebDavClient? = connectionManager.getOrCreateClient()

    /**
     * 🆕 v2.0.0: Delegiert an SyncUrlBuilder (extrahiert in Commit 17).
     */
    internal fun getServerUrl(): String? = urlBuilder.getServerUrl()

    /**
     * 🔧 v2.3.0: Checks if an IOException signals an HTTP 401 authentication failure.
     * Used in directory-ensure methods to let auth errors propagate
     * instead of being masked by the exists/list fallback logic.
     * See: bug-401-error-mapping.md
     */
    private fun isAuthException(e: IOException): Boolean {
        if (e is WebDavException) {
            return e.statusCode == HTTP_UNAUTHORIZED
        }
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("authentication failed")
    }

    /**
     * Stellt sicher dass notes-md/ Ordner existiert
     *
     * Wird beim ersten erfolgreichen Sync aufgerufen (unabhängig von MD-Feature).
     * Cached in Memory - nur einmal pro App-Session.
     */

    /** MD-Export ODER MD-Auto-Import aktiv — Gate für alle Requests gegen `notes-md/`. */
    private fun markdownFeaturesEnabled(): Boolean =
        prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false) ||
            prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)

    private fun ensureMarkdownDirectoryExists(webdav: WebDavClient, serverUrl: String) {
        if (connectionManager.markdownDirEnsured) return

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            // 🔧 v2.0.0 (Issue #44): exists() may throw IOException on servers with auth quirks.
            // Fallback: try list() — if it succeeds, the directory exists.
            val dirExists = try {
                webdav.exists(mdUrl)
            } catch (e: IOException) {
                if (isAuthException(e)) throw e
                Logger.w(TAG, "⚠️ notes-md/ exists() check failed: ${e.message}, trying list()")
                try {
                    webdav.list(mdUrl)
                    true
                } catch (listEx: IOException) {
                    if (isAuthException(listEx)) throw listEx
                    Logger.w(TAG, "⚠️ notes-md/ list() fallback also failed: ${listEx.message}")
                    false
                }
            }

            if (!dirExists) {
                webdav.createDirectory(mdUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory (for future use)")
            }

            connectionManager.markdownDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create notes-md/: ${e.message}")
            // Nicht kritisch - User kann später manuell erstellen
        }
    }

    /**
     * ⚡ v1.3.1: Stellt sicher dass notes/ Ordner existiert (mit Cache)
     *
     * Spart ~500ms pro Sync durch Caching
     * 🔧 v2.0.0 (Issue #44): Fallback auf list() wenn exists() fehlschlägt
     */
    @Suppress("ThrowsCount") // Auth re-throws in exists/list fallback + outer re-throw
    private fun ensureNotesDirectoryExists(webdav: WebDavClient, notesUrl: String) {
        if (connectionManager.notesDirEnsured) {
            Logger.d(TAG, "⚡ $activeSyncFolderName/ directory already verified (cached)")
            return
        }

        try {
            Logger.d(TAG, "🔍 Checking if $activeSyncFolderName/ directory exists...")
            // 🔧 v2.0.0 (Issue #44): exists() may throw if server returns unexpected HTTP code.
            // Fallback: try list() — PROPFIND works universally (Jianguoyun, Nextcloud, Apache).
            val dirExists = try {
                webdav.exists(notesUrl)
            } catch (e: IOException) {
                if (isAuthException(e)) throw e
                Logger.w(TAG, "⚠️ exists() check failed: ${e.message}, trying list()")
                try {
                    webdav.list(notesUrl)
                    true
                } catch (listEx: IOException) {
                    if (isAuthException(listEx)) throw listEx
                    Logger.w(TAG, "⚠️ list() fallback also failed: ${listEx.message}")
                    false
                }
            }
            if (!dirExists) {
                Logger.d(TAG, "📁 Creating $activeSyncFolderName/ directory...")
                webdav.createDirectory(notesUrl)
            }
            Logger.d(TAG, "    ✅ $activeSyncFolderName/ directory ready")
            connectionManager.notesDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "💥 CRASH checking/creating $activeSyncFolderName/ directory!", e)
            throw e
        }
    }

    /**
     * Prüft ob lokale Änderungen seit letztem Sync vorhanden sind (v1.1.2)
     * Performance-Optimierung: Vermeidet unnötige Sync-Operationen
     *
     * @return true wenn unsynced changes vorhanden, false sonst
     */
    suspend fun hasUnsyncedChanges(): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            val lastSyncTime = getLastSyncTimestamp()

            // Check 1: Never synced
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - has changes: true")
                return@withContext true
            }

            // 🆕 v2.7.0 (Folders): ungesyncte Ordner-Metadaten (Farbe/Anlage/Rename/Löschung)
            if (prefs.getBoolean(Constants.KEY_FOLDERS_DIRTY, false)) {
                Logger.d(TAG, "📁 Folder metadata dirty - has changes: true")
                return@withContext true
            }

            // 🆕 v2.8.0 (Local-Only Folders): ausstehende „Vom Server entfernen"-Tombstones
            if (folderStore.getServerRemovalQueue().isNotEmpty()) {
                Logger.d(TAG, "📁 Folder server-removal queue pending - has changes: true")
                return@withContext true
            }

            // 🆕 v2.9.x (Trash): ausstehende Server-Löschungen aus „Papierkorb leeren"
            if (PendingServerDeletions(context).getAll().isNotEmpty()) {
                Logger.d(TAG, "🗑️ Pending server deletions - has changes: true")
                return@withContext true
            }

            // Check 2: Local changes (Timestamp ODER SyncStatus)
            // 🛡️ v1.8.2 (IMPL_19a): Klassen-Feld nutzen statt neue Instanz
            val allNotes = storage.loadAllNotes()
            // 🆕 v2.8.0: local-only-Ordner-Notizen ausschließen — PENDING-Notizen dort werden
            // nie hochgeladen und würden sonst eine Endlosschleife im Sync-Scheduler auslösen.
            val localOnlyFolders = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
            // 🛡️ v1.8.2 (IMPL_22): Auch PENDING-Status prüfen —
            // nach Server-Wechsel wird syncStatus auf PENDING gesetzt, aber updatedAt bleibt gleich
            val hasLocalChanges = allNotes.any { note ->
                note.folderName?.lowercase() !in localOnlyFolders &&
                    (
                        note.updatedAt > lastSyncTime ||
                            note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.PENDING
                        )
            }

            if (hasLocalChanges) {
                val unsyncedByTime = allNotes.count { it.updatedAt > lastSyncTime }
                val unsyncedByStatus = allNotes.count {
                    it.syncStatus ==
                        dev.dettmer.simplenotes.models.SyncStatus.PENDING
                }
                Logger.d(TAG, "📝 Local changes: $unsyncedByTime by timestamp, $unsyncedByStatus PENDING")
                return@withContext true
            }

            // Check 3: Server changes (respects user preference)
            val alwaysCheckServer = prefs.getBoolean(Constants.KEY_ALWAYS_CHECK_SERVER, true)

            if (!alwaysCheckServer) {
                Logger.d(TAG, "⏭️ Server check disabled by user - has changes: false")
                return@withContext false
            }

            // 🆕 v2.14.0: Kein Pre-Check-Request mehr. Der frühere checkServerForChanges()
            // konnte nie false liefern (JSON galt immer als potenziell geändert) — seine
            // 2–3 Requests waren reine Kosten. Die eigentliche Ersparnis liefern die
            // Datei-E-Tags während des Downloads.
            if (getServerUrl() == null || !CredentialStore.hasCredentials(context)) {
                Logger.w(TAG, "⚠️ Cannot check server - no credentials")
                return@withContext false
            }

            Logger.d(TAG, "📊 Final check: local=false, server assumed changed (file E-Tags decide)")
            true
        } catch (e: Exception) {
            // 🔧 v1.7.2 KRITISCH: Bei Server-Fehler (Timeout, etc.) return TRUE!
            // Grund: Besser fälschlich synchen als "Already synced" zeigen obwohl Server nicht erreichbar
            Logger.e(TAG, "❌ Failed to check server for changes: ${e.message}")
            Logger.d(TAG, "⚠️ Returning TRUE (will attempt sync) - server check failed")
            true // Sicherheitshalber TRUE → Sync wird versucht und gibt dann echte Fehlermeldung
        }
    }

    /**
     * Prüft ob WebDAV-Server erreichbar ist (ohne Sync zu starten)
     * Verwendet Socket-Check für schnelle Erreichbarkeitsprüfung
     *
     * @return true wenn Server erreichbar ist, false sonst
     */
    suspend fun isServerReachable(): Boolean = gateChecker.isServerReachable()

    fun isOnWiFi(): Boolean = gateChecker.isOnWiFi()

    fun canSync(): SyncGateResult = gateChecker.canSync()

    suspend fun testConnection(): SyncResult = withContext(ioDispatcher) {
        return@withContext try {
            val webdav = getOrCreateWebDavClient() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-Zugangsdaten nicht konfiguriert"
            )

            val serverUrl = getServerUrl() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-URL nicht konfiguriert"
            )

            // Only test if directory exists or can be created
            // 🔧 v2.0.0 (Issue #44): exists() may throw on Jianguoyun — let it propagate;
            // the outer catch maps it to an error message properly.
            val exists = webdav.exists(serverUrl)
            if (!exists) {
                webdav.createDirectory(serverUrl)
            }

            // 🔧 v2.3.0 (Issue #55): Verify WebDAV capability with PROPFIND.
            // HEAD/exists() can return 200 on any HTTP server, but PROPFIND verifies
            // the URL is an actual WebDAV collection. Without this check, testConnection()
            // reports "Reachable" for non-WebDAV URLs, and the subsequent MKCOL fails with 404.
            try {
                webdav.list(serverUrl)
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ PROPFIND failed on base URL: ${e.message}")
                return@withContext SyncResult(
                    isSuccess = false,
                    errorMessage = context.getString(R.string.sync_error_not_webdav)
                )
            }

            // 🔧 v1.9.0 Fix: activeSyncFolderName VOR getNotesUrl() laden
            activeSyncFolderName = prefs.getString(
                Constants.KEY_SYNC_FOLDER_NAME,
                Constants.DEFAULT_SYNC_FOLDER_NAME
            ) ?: Constants.DEFAULT_SYNC_FOLDER_NAME

            // 🆕 Issue #21: Sync-Ordner prüfen und Status mit Ordnernamen kommunizieren
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            val notesExist = try {
                webdav.exists(notesUrl)
            } catch (e: Exception) {
                Logger.d(TAG, "exists() check failed during testConnection: ${e.message}")
                false
            }
            val folderName = activeSyncFolderName
            val infoMessage = if (notesExist) {
                val mdExists = try {
                    webdav.exists(urlBuilder.getMarkdownUrl(serverUrl))
                } catch (e: Exception) {
                    Logger.d(TAG, "md exists() check failed during testConnection: ${e.message}")
                    false
                }
                if (mdExists) {
                    context.getString(R.string.test_connection_success_md_exists, folderName)
                } else {
                    context.getString(R.string.test_connection_success_with_notes, folderName)
                }
            } else {
                context.getString(R.string.test_connection_success_first_sync, folderName)
            }

            SyncResult(
                isSuccess = true,
                syncedCount = 0,
                errorMessage = null,
                infoMessage = infoMessage
            )
        } catch (e: Exception) {
            SyncResult(
                isSuccess = false,
                errorMessage = mapSyncExceptionToMessage(e)
            )
        }
    }

    suspend fun syncNotes(trigger: ActivityLog.Trigger?): SyncResult =
        syncNotesInternal().also { logSyncOutcome(it, trigger) }

    // Ein Ort für alle vier Aufrufer (Worker, pullToRefresh, onResume, Einstellungen):
    // vorher schrieb nur der Worker, ein manueller Sync blieb unsichtbar.
    private fun logSyncOutcome(result: SyncResult, trigger: ActivityLog.Trigger?) {
        when {
            !result.isSuccess -> ActivityLog.log(
                ActivityLog.Op.SYNC_FAIL,
                ActivityLog.Src.LOCAL,
                err = result.errorMessage ?: "unknown",
                trigger = trigger
            )
            // Guard bleibt: Leerlauf-Syncs verdrängen sonst die Zeilen, wegen derer es das Log gibt.
            result.syncedCount > 0 -> ActivityLog.log(
                ActivityLog.Op.SYNC_OK,
                ActivityLog.Src.LOCAL,
                why = "synced=${result.syncedCount}",
                trigger = trigger
            )
        }
    }

    // Abbau: TECH_DEBT_ROADMAP.md Slice 4
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun syncNotesInternal(): SyncResult = withContext(ioDispatcher) {
        // 🔒 v1.3.1: Verhindere parallele Syncs
        if (!syncMutex.tryLock()) {
            Logger.d(TAG, "⏭️ Sync already in progress - skipping")
            return@withContext SyncResult(
                isSuccess = true,
                syncedCount = 0,
                errorMessage = null
            )
        }

        try {
            Logger.d(TAG, "═══════════════════════════════════════")
            Logger.d(TAG, "🔄 syncNotes() ENTRY")
            Logger.d(TAG, "Context: ${context.javaClass.simpleName}")
            Logger.d(TAG, "Thread: ${Thread.currentThread().name}")

            return@withContext try {
                // 🆕 v1.8.0: Banner bleibt in PREPARING bis echte Arbeit (Upload/Download) anfällt

                Logger.d(TAG, "📍 Step 1: Getting WebDavClient client")

                val webdav = try {
                    getOrCreateWebDavClient()
                } catch (e: Exception) {
                    Logger.e(TAG, "💥 CRASH in getOrCreateWebDavClient()!", e)
                    e.printStackTrace()
                    throw e
                }

                if (webdav == null) {
                    Logger.e(TAG, "❌ WebDavClient is null - credentials missing")
                    return@withContext SyncResult(
                        isSuccess = false,
                        errorMessage = "Server-Zugangsdaten nicht konfiguriert"
                    )
                }
                Logger.d(TAG, "    ✅ WebDavClient client created")

                Logger.d(TAG, "📍 Step 2: Getting server URL")
                val serverUrl = getServerUrl()
                if (serverUrl == null) {
                    Logger.e(TAG, "❌ Server URL is null")
                    return@withContext SyncResult(
                        isSuccess = false,
                        errorMessage = "Server-URL nicht konfiguriert"
                    )
                }

                Logger.d(TAG, "📡 Server URL: $serverUrl")
                // 🆕 v1.9.0: Load configured sync folder name at sync start
                activeSyncFolderName =
                    prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
                        ?: Constants.DEFAULT_SYNC_FOLDER_NAME
                Logger.d(TAG, "📁 Sync folder: $activeSyncFolderName")
                Logger.d(TAG, "🔐 Credentials configured: ${CredentialStore.getUsername(context) != null}")

                var syncedCount = 0
                var conflictCount = 0
                var purgedFromServerCount = 0

                Logger.d(TAG, "📍 Step 3: Checking server directory")
                // ⚡ v1.3.1: Verwende gecachte Directory-Checks
                val notesUrl = urlBuilder.getNotesUrl(serverUrl)
                ensureNotesDirectoryExists(webdav, notesUrl)

                // Ensure notes-md/ directory exists (for Markdown export).
                // 🆕 v2.14.0: nur wenn ein MD-Feature aktiv ist — alle nachgelagerten MD-Pfade
                // legen das Verzeichnis bei Bedarf selbst an.
                if (markdownFeaturesEnabled()) {
                    markdownSyncManager.beginSyncCycle()
                    ensureMarkdownDirectoryExists(webdav, serverUrl)
                }

                // 🆕 Bild-Attachments: -assets/ sicherstellen + einziges PROPFIND für alle drei Diffs
                val referencedAssets = AssetReferences.extractAllReferenced(storage.loadAllNotes())
                val serverAssets =
                    assetSyncManager.listServerAssetsIfNeeded(webdav, serverUrl, referencedAssets)

                uploadReferencedAssets(webdav, serverUrl, referencedAssets, serverAssets)

                Logger.d(TAG, "📍 Step 4: Uploading local notes")
                // Upload local notes
                // 🆕 v1.11.0: UploadBatchResult enthält zusätzlich MD-Export-IDs für Import-Exclusion
                var markdownExportedNoteIds: Set<String> = emptySet()
                // 🆕 IDs whose JSON was adopted at a tied timestamp (server edit wins) — these
                // must be excluded from MD import so a divergent MD mirror cannot override JSON.
                var adoptedFromDownloadIds: Set<String> = emptySet()
                val mdExport = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
                try {
                    Logger.d(TAG, "⬆️ Uploading local notes...")
                    val uploadResult = uploadLocalNotes(
                        webdav,
                        serverUrl,
                        onProgress = { current, total, noteTitle ->
                            SyncStateManager.updateProgress(
                                phase = if (mdExport) SyncPhase.UPLOADING_EXPORTING_MARKDOWN else SyncPhase.UPLOADING,
                                current = current,
                                total = total,
                                currentFileName = noteTitle
                            )
                        }
                    )
                    syncedCount += uploadResult.uploadedCount
                    markdownExportedNoteIds = uploadResult.markdownExportedNoteIds
                    Logger.d(TAG, "✅ Uploaded: ${uploadResult.uploadedCount} notes")
                } catch (e: Exception) {
                    Logger.e(TAG, "💥 CRASH in uploadLocalNotes()!", e)
                    e.printStackTrace()
                    throw e
                }

                // Step 4.5: Process pending server deletions (queued from offline deletes)
                Logger.d(TAG, "📍 Step 4.5: Processing pending server deletions")
                val pendingOutcome = processPendingServerDeletions(webdav, serverUrl)
                purgedFromServerCount = pendingOutcome.purgedCount

                // Re-Seed ist Pflicht: deleteFromServer entfernt die ID lokal, das Seeding fügt
                // sie aus dem Ledger wieder ein (nötig für detectDeletions.purgedRemotely).
                seedDeletionTrackerFromSharedLedger(webdav, serverUrl, pendingOutcome.ledger)

                // 🆕 v1.8.0: Phase 3 - Downloading (Phase wird nur bei echten Downloads gesetzt)
                Logger.d(TAG, "📍 Step 5: Downloading remote notes")
                // Download remote notes
                var deletedOnServerCount = 0 // 🆕 v1.8.0
                var folderReconciledCount = 0 // 🆕 v2.7.2
                var trashedFromServerCount = 0
                var restoredCount = 0 // 🆕 Issue #128
                var deletionDetectionSkipped = false // 🆕 Issue #128
                // 🆕 v2.14.0: aus dem Root-Listing für den folders.json-Fast-Path.
                var foldersJsonEtag: String? = null
                var newFoldersDiscovered = false
                try {
                    Logger.d(TAG, "⬇️ Downloading remote notes...")
                    val downloadResult = downloadRemoteNotes(
                        webdav,
                        serverUrl,
                        // 🔧 v2.3.0 (Issue #62): Normal sync must NOT scan WebDAV root.
                        // v1.2.0 compat-scan caused phantom "Untitled" notes when foreign
                        // JSONs (info.json, google-services.json, …) sat in the root and
                        // were parsed into Notes with a fresh random UUID each sync.
                        // Legacy v1.2.0 users can still migrate via restoreFromServer().
                        includeRootFallback = false,
                        onProgress = { current, total, noteTitle ->
                            // 🆕 v1.10.0-P2: Pass actual total from ParallelDownloader for determinate progress
                            SyncStateManager.updateProgress(
                                phase = SyncPhase.DOWNLOADING,
                                current = current,
                                total = total,
                                currentFileName = noteTitle
                            )
                        }
                    )
                    syncedCount += downloadResult.downloadedCount
                    conflictCount += downloadResult.conflictCount
                    deletedOnServerCount = downloadResult.deletedOnServerCount // 🆕 v1.8.0
                    folderReconciledCount = downloadResult.folderReconciledCount // 🆕 v2.7.2
                    trashedFromServerCount = downloadResult.trashedDownloadedCount
                    restoredCount = downloadResult.healedCount // 🆕 Issue #128
                    deletionDetectionSkipped = downloadResult.deletionDetectionSkipped // 🆕 Issue #128
                    adoptedFromDownloadIds = downloadResult.adoptedNoteIds
                    foldersJsonEtag = downloadResult.foldersJsonEtag
                    newFoldersDiscovered = downloadResult.newFoldersDiscovered
                    Logger.d(
                        TAG,
                        "✅ Downloaded: ${downloadResult.downloadedCount} notes, " +
                            "Conflicts: ${downloadResult.conflictCount}, " +
                            "Deleted on server: ${downloadResult.deletedOnServerCount}" // 🆕 v1.8.0
                    )

                    // 🛡️ v1.8.2 (IMPL_21): Download-Fehler nicht verschlucken
                    if (downloadResult.downloadFailed) {
                        Logger.e(TAG, "⚠️ Download hatte Fehler — Sync wird als fehlgeschlagen gemeldet")
                        throw IOException(
                            "Download failed: ${downloadResult.downloadError ?: "Unknown error"}"
                        )
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "💥 CRASH in downloadRemoteNotes()!", e)
                    e.printStackTrace()
                    throw e
                }

                downloadAndGcAssets(webdav, serverUrl, serverAssets)

                // 🆕 v2.7.0 (Folders): Step 5.6 — Ordner-Metadaten syncen (Namen + Farben + Tombstones).
                // 🆕 v2.14.0: Skip nur, wenn der Download sauber lief UND keine reinen
                // Discovery-Ordner dazukamen (die setzen kein dirty-Flag, müssten aber hoch).
                val foldersChanged = syncFolderMetadataSafe(
                    webdav,
                    serverUrl,
                    remoteEtag = foldersJsonEtag,
                    // Ein fehlgeschlagener Download hat oben bereits geworfen — hier zählt nur noch,
                    // ob Discovery-Ordner injiziert werden müssen.
                    allowSkip = !newFoldersDiscovered
                )

                Logger.d(TAG, "📍 Step 6: Auto-import Markdown (if enabled)")

                // Auto-import Markdown files from server
                var markdownImportedCount = 0
                try {
                    val markdownAutoImportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
                    if (markdownAutoImportEnabled) {
                        // 🆕 v1.10.0-P2: Cancel checkpoint before potentially long Markdown import
                        currentCoroutineContext().ensureActive()

                        // 🔧 v1.11.0: Phase IMPORTING_MARKDOWN wird jetzt erst innerhalb von
                        // importMarkdownFiles() gesetzt, und nur wenn tatsächlich Dateien
                        // verarbeitet werden müssen (nicht beim Fast-Path).

                        Logger.d(TAG, "📥 Auto-importing Markdown files...")
                        // 🆕 v1.11.0: Pass exported note IDs to prevent re-import of just-exported files.
                        // 🆕 Also exclude IDs adopted from a server JSON edit at a tied timestamp so a
                        // divergent MD mirror cannot override the authoritative JSON in the same cycle.
                        markdownImportedCount = importMarkdownFiles(
                            webdav,
                            serverUrl,
                            markdownExportedNoteIds + adoptedFromDownloadIds
                        )
                        Logger.d(TAG, "✅ Auto-imported: $markdownImportedCount Markdown files")

                        // 🔧 v1.7.2 (IMPL_014): Re-upload notes that were updated from Markdown
                        if (markdownImportedCount > 0) {
                            Logger.d(TAG, "📤 Re-uploading notes updated from Markdown (JSON sync)...")
                            val reUploadResult = uploadLocalNotes(webdav, serverUrl)
                            Logger.d(
                                TAG,
                                "✅ Re-uploaded: ${reUploadResult.uploadedCount} notes (JSON updated on server)"
                            )
                            // 🔧 v1.11.0: Re-Uploads NICHT zum syncedCount addieren.
                            // Re-Uploads sind ein technisches Artefakt der MD→JSON-Sync-Kette,
                            // keine vom User initiierten Aktionen. Die importierten Markdown-Änderungen
                            // werden bereits über markdownImportedCount in effectiveSyncedCount berücksichtigt.
                            // Vorher: syncedCount += reUploadedCount → führte zu Doppelzählung.
                        }
                    } else {
                        // 🔧 v2.2.1 (Issue #50): Include pref values for easier diagnosis
                        val mdExport = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
                        val mdImport = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
                        Logger.d(
                            TAG,
                            "⏭️ Markdown auto-import disabled " +
                                "(KEY_MARKDOWN_EXPORT=$mdExport, KEY_MARKDOWN_AUTO_IMPORT=$mdImport)"
                        )
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "⚠️ Markdown auto-import failed (non-fatal)", e)
                    // Non-fatal, continue
                }

                Logger.d(TAG, "📍 Step 7: Saving sync timestamp")

                // Update last sync timestamp
                try {
                    saveLastSyncTimestamp()
                    Logger.d(TAG, "    ✅ Timestamp saved")
                } catch (e: Exception) {
                    Logger.e(TAG, "💥 CRASH saving timestamp!", e)
                    e.printStackTrace()
                    // Non-fatal, continue
                }

                // ✅ v1.3.0 / 🔧 v1.11.0: Hybrid counting to prevent double-counting
                // - syncedCount = JSON uploads + downloads (unique notes)
                // - markdownImportedCount = .md files that introduced NEW content from desktop editors
                // - Re-uploads (JSON sync after MD import) are NOT counted to prevent inflation
                //
                // 🔧 v1.11.0: Addiere markdownImportedCount nur wenn die Notizen NICHT bereits
                // im syncedCount enthalten sind (= nicht vom Upload stammen). Desktop-Edited
                // Notes werden durch importMarkdownFiles() importiert und via Re-Upload gesynct,
                // aber markdownImportedCount zählt nur echte externe Änderungen.
                val effectiveSyncedCount = syncedCount + markdownImportedCount

                Logger.d(TAG, "🎉 Sync completed successfully: $effectiveSyncedCount notes")
                if (markdownImportedCount > 0 && syncedCount > 0) {
                    Logger.d(TAG, "📝 Including $markdownImportedCount Markdown file updates")
                }
                if (deletedOnServerCount > 0) { // 🆕 v1.8.0
                    Logger.d(TAG, "🗑️ Detected $deletedOnServerCount notes deleted on server")
                }
                Logger.d(TAG, "═══════════════════════════════════════")

                // 🆕 v1.8.0: Phase 6 - Completed
                SyncStateManager.updateProgress(
                    phase = SyncPhase.COMPLETED,
                    current = effectiveSyncedCount,
                    total = effectiveSyncedCount
                )

                SyncResult(
                    isSuccess = true,
                    syncedCount = effectiveSyncedCount,
                    conflictCount = conflictCount,
                    deletedOnServerCount = deletedOnServerCount, // 🆕 v1.8.0
                    purgedFromServerCount = purgedFromServerCount, // 🆕 v2.9.x (Trash)
                    trashedFromServerCount = trashedFromServerCount,
                    foldersChanged = foldersChanged, // 🆕 v2.7.0 (Folders)
                    foldersReconciled = folderReconciledCount > 0, // 🆕 v2.7.2
                    restoredCount = restoredCount, // 🆕 Issue #128
                    deletionDetectionSkipped = deletionDetectionSkipped // 🆕 Issue #128
                )
            } catch (e: Exception) {
                Logger.e(TAG, "═══════════════════════════════════════")
                Logger.e(TAG, "💥💥💥 FATAL EXCEPTION in syncNotes() 💥💥💥")
                Logger.e(TAG, "Exception type: ${e.javaClass.name}")
                Logger.e(TAG, "Exception message: ${e.message}")
                Logger.e(TAG, "Stack trace:")
                e.printStackTrace()
                Logger.e(TAG, "═══════════════════════════════════════")

                // 🆕 v1.8.0: Phase ERROR
                SyncStateManager.updateProgress(phase = SyncPhase.ERROR)

                SyncResult(
                    isSuccess = false,
                    errorMessage = mapSyncExceptionToMessage(e)
                )
            }
        } finally {
            // ⚡ v1.3.1: Session-Caches leeren
            // 🛡️ v1.8.2 (IMPL_13): try-catch verhindert dass eine Exception in
            // clearSessionCache() den syncMutex.unlock() blockiert → permanenter Deadlock
            try {
                connectionManager.clearSession()
                Logger.d(TAG, "🧹 Session caches cleared")
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ Session cache clear failed (non-fatal): ${e.message}")
            }
            // 🆕 v1.8.0: Reset progress state
            SyncStateManager.resetProgress()
            // 🔒 v1.3.1: Sync-Mutex freigeben
            syncMutex.unlock()
        }
    }

    /**
     * @param ledger der frisch gemergte Lösch-Ledger, wenn dieser Sync welche geschrieben hat —
     * spart dem nachgelagerten Seeding ein erneutes GET.
     */
    private data class PendingDeletionsOutcome(val purgedCount: Int, val ledger: DeletionTracker?)

    private suspend fun processPendingServerDeletions(
        webdav: WebDavClient,
        serverUrl: String
    ): PendingDeletionsOutcome {
        return try {
            val pendingDeletions = PendingServerDeletions(context)
            val pendingIds = pendingDeletions.getAll()
            if (pendingIds.isEmpty()) {
                Logger.d(TAG, "    ✅ No pending deletions")
                return PendingDeletionsOutcome(0, null)
            }
            Logger.d(TAG, "🗑️ Processing ${pendingIds.size} pending server deletions")
            val successIds = mutableListOf<String>()
            // Nur echte Löschungen zählen für die UI ("X vom Server gelöscht"). Move-Cleanups
            // (Ordner-Rename / Notiz verschieben) räumen nur den alten Pfad auf und sind keine
            // benutzer­sichtbare Löschung — sonst irreführende Sync-Meldung.
            val purgedIds = mutableListOf<String>()
            pendingIds.forEach { pd ->
                try {
                    val deleted = noteDownloader.deleteFromServer(pd.id, pd.folderName, pd.isMove)
                    if (!deleted) return@forEach
                    successIds.add(pd.id)
                    // 🔒 Moves räumen nur den alten Pfad auf — die Note-ID darf NICHT ins geteilte
                    // Lösch-Ledger, sonst wird die nur verschobene Notiz geräteweit als gelöscht
                    // markiert und beim Download wieder getilgt (Datenverlust bei Ordner-Rename).
                    if (!pd.isMove) purgedIds.add(pd.id)
                } catch (e: Exception) {
                    Logger.w(TAG, "⚠️ Failed to delete pending note ${pd.id} from server: ${e.message}")
                }
            }
            // Ein GET + ein PUT für alle Löschungen statt read-modify-write pro Notiz.
            val ledger = if (purgedIds.isEmpty()) {
                null
            } else {
                deletionSyncManager.appendAllAndUpload(
                    webdav,
                    deletionSyncManager.deletionsFileUrl(serverUrl),
                    purgedIds,
                    DeviceIdGenerator.getDeviceId(context)
                )
            }
            if (successIds.isNotEmpty()) {
                pendingDeletions.remove(successIds)
                Logger.d(
                    TAG,
                    "✅ Processed ${successIds.size}/${pendingIds.size} pending " +
                        "(${purgedIds.size} deletions, ${successIds.size - purgedIds.size} moves)"
                )
                cleanupEmptyFolderDirs(pendingIds, successIds)
            }
            PendingDeletionsOutcome(purgedIds.size, ledger)
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Pending deletions step failed (non-fatal)", e)
            PendingDeletionsOutcome(0, null)
        }
    }

    private suspend fun cleanupEmptyFolderDirs(
        pendingIds: List<PendingDeletion>,
        successIds: List<String>
    ) {
        val folderNames = pendingIds
            .filter { it.id in successIds && !it.folderName.isNullOrBlank() }
            .mapNotNull { it.folderName }
            .toSet()
        if (folderNames.isEmpty()) return
        Logger.d(TAG, "📍 Step 4.6: Cleaning up empty folder directories (${folderNames.size})")
        folderNames.forEach { folderName ->
            try {
                deleteServerFolderIfEmpty(folderName)
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ Folder dir cleanup '$folderName' failed (non-fatal): ${e.message}")
            }
        }
    }

    /** @param prefetched frisch gemergter Ledger aus [processPendingServerDeletions] — spart das GET. */
    private fun seedDeletionTrackerFromSharedLedger(
        webdav: WebDavClient,
        serverUrl: String,
        prefetched: DeletionTracker? = null
    ) {
        try {
            val url = deletionSyncManager.deletionsFileUrl(serverUrl)
            val remoteLedger = prefetched ?: deletionSyncManager.downloadRemote(webdav, url)
            if (remoteLedger.deletedNotes.isEmpty()) return
            val localTracker = storage.loadDeletionTracker()
            val now = System.currentTimeMillis()
            remoteLedger.deletedNotes.forEach { record -> localTracker.upsertIfNewer(record) }
            localTracker.pruneOlderThan(Constants.TRASH_RETENTION_MS, now)
            storage.saveDeletionTracker(localTracker)
            Logger.d(TAG, "📋 Seeded local DeletionTracker from shared ledger (${remoteLedger.deletedNotes.size} remote records)")
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ Shared deletions ledger seed failed (non-fatal): ${e.message}")
        }
    }

    private suspend fun syncFolderMetadataSafe(
        webdav: WebDavClient,
        serverUrl: String,
        remoteEtag: String? = null,
        allowSkip: Boolean = false
    ): Boolean =
        try {
            folderSyncManager.sync(webdav, serverUrl, remoteEtag, allowSkip)
        } catch (e: Exception) {
            Logger.w(TAG, "folder metadata sync failed (non-fatal): ${e.message}")
            false
        }

    /**
     * 🆕 Bild-Attachments: Assets-first (E1) — kein Gerät sieht eine Notiz ohne deren Bilder.
     * Best-effort: ein einzelner fehlgeschlagener Upload blockiert den Notiz-Upload nicht,
     * der Markdown-Renderer zeigt bis zum nächsten Sync einen Platzhalter.
     */
    private suspend fun uploadReferencedAssets(
        webdav: WebDavClient,
        serverUrl: String,
        referenced: Set<String>,
        serverAssets: Map<String, WebDavResource>?
    ) {
        if (serverAssets == null) return
        try {
            val uploadedCount = assetSyncManager.uploadMissing(webdav, serverUrl, referenced, serverAssets)
            Logger.d(TAG, "✅ Assets uploaded: $uploadedCount")
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Asset upload failed (non-fatal)", e)
        }
    }

    /**
     * 🆕 Bild-Attachments: Download referenzierter, lokal fehlender Assets — Referenzen aus dem
     * frisch aktualisierten Korpus (inkl. der soeben heruntergeladenen Notizen). Danach
     * Mark-and-Sweep-GC. Best-effort, wie [uploadReferencedAssets].
     */
    private suspend fun downloadAndGcAssets(
        webdav: WebDavClient,
        serverUrl: String,
        serverAssets: Map<String, WebDavResource>?
    ) {
        try {
            val freshNotes = storage.loadAllNotes(forceReload = true)
            val referenced = AssetReferences.extractAllReferenced(freshNotes)
            // Late-List-Pflicht: der Skip oben galt für den Stand VOR dem Download. Bringen die
            // frisch geladenen Notizen Asset-Referenzen mit, muss jetzt gelistet werden — sonst
            // zeigt der Renderer bis zum nächsten Sync Platzhalter statt Bilder.
            val assets = serverAssets
                ?: assetSyncManager.listServerAssetsIfNeeded(webdav, serverUrl, referenced)
                ?: return
            val downloadedCount = assetSyncManager.downloadMissing(webdav, serverUrl, referenced, assets)
            Logger.d(TAG, "✅ Assets downloaded: $downloadedCount")

            // Guard analog ALL_DELETED_GUARD_THRESHOLD: eine leere Notizliste deutet auf einen
            // fehlgeschlagenen Load hin, nicht auf "alle Assets sind Waisen" — Remote-GC würde
            // sonst serverseitige Assets aller Geräte fälschlich löschen.
            assetSyncManager.garbageCollect(
                webdav,
                serverUrl,
                freshNotes,
                assets,
                allowRemoteSweep = freshNotes.isNotEmpty()
            )
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Asset download/GC failed (non-fatal)", e)
        }
    }

    /**
     * 🔧 v1.9.0: Parallele Uploads mit bounded concurrency
     * Analog zu ParallelDownloader-Pattern, aber für Uploads.
     */
    private suspend fun uploadLocalNotes(
        webdav: WebDavClient,
        serverUrl: String,
        onProgress: (current: Int, total: Int, noteTitle: String) -> Unit = { _, _, _ -> }
    ): UploadBatchResult = noteUploader.uploadAll(webdav, serverUrl, onProgress)

    /**
     * 🆕 v1.9.0 (Opt 5): Berechnet SHA-256-Hash des JSON-Inhalts einer Notiz.
     * Sichtbarkeit `internal` für Testbarkeit aus dem test-Source-Set.
     */
    internal fun computeNoteContentHash(note: Note): String = noteUploader.computeContentHash(note)

    /**
     * Exportiert ALLE lokalen Notizen als Markdown — delegiert an MarkdownSyncManager.
     */
    suspend fun exportAllNotesToMarkdown(
        serverUrl: String,
        username: String,
        password: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = markdownSyncManager.exportAll(serverUrl, username, password, onProgress)

    /**
     * Delegiert an NoteDownloader (v2.0.0 Commit 21).
     */
    private suspend fun downloadRemoteNotes(
        webdav: WebDavClient,
        serverUrl: String,
        includeRootFallback: Boolean = false,
        forceOverwrite: Boolean = false,
        deletionTracker: DeletionTracker = storage.loadDeletionTracker(),
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): DownloadResult = noteDownloader.downloadAll(
        webdav = webdav,
        serverUrl = serverUrl,
        includeRootFallback = includeRootFallback,
        forceOverwrite = forceOverwrite,
        deletionTracker = deletionTracker,
        onProgress = onProgress
    )

    private fun saveLastSyncTimestamp() = timestampManager.save()

    fun getLastSyncTimestamp(): Long = timestampManager.getLast()

    fun getLastSuccessfulSyncTimestamp(): Long = timestampManager.getLastSuccessful()

    /**
     * 🆕 v1.10.0: Zentrale Exception-zu-Fehlermeldung-Konvertierung.
     * Delegiert an SyncExceptionMapper (extrahiert in v2.0.0, Commit 16).
     */
    internal fun mapSyncExceptionToMessage(e: Exception): String = exceptionMapper.mapToUserMessage(e)

    /**
     * Restore all notes from server with different modes (v1.3.0)
     * @param mode RestoreMode (REPLACE, MERGE, or OVERWRITE_DUPLICATES)
     * @return RestoreResult with count of restored notes
     */
    // Abbau: TECH_DEBT_ROADMAP.md Slice 4
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun restoreFromServer(
        mode: dev.dettmer.simplenotes.backup.RestoreMode = dev.dettmer.simplenotes.backup.RestoreMode.REPLACE,
        // 🆕 v2.11.0: Ordnerwechsel-Dialog ("Nicht mitnehmen") — ein legitim leerer
        // neuer Ordner ist kein Fehler. Default false lässt den Backup-Restore-Aufrufer
        // (echte "keine Notizen auf Server" Situation) unverändert.
        emptyIsSuccess: Boolean = false
    ): RestoreResult = withContext(ioDispatcher) {
        return@withContext try {
            val webdav = getOrCreateWebDavClient() ?: return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Server-Zugangsdaten nicht konfiguriert",
                restoredCount = 0
            )

            val serverUrl = getServerUrl() ?: return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Server-URL nicht konfiguriert",
                restoredCount = 0
            )

            Logger.d(TAG, "═══════════════════════════════════════")
            Logger.d(TAG, "🔄 restoreFromServer() ENTRY")
            Logger.d(TAG, "Mode: $mode")
            Logger.d(TAG, "Thread: ${Thread.currentThread().name}")

            // ✅ v1.3.0 FIX: WICHTIG - Deletion Tracker bei ALLEN Modi clearen!
            // Restore bedeutet: "Server ist die Quelle der Wahrheit"
            // → Lokale Deletion-History ist irrelevant
            Logger.d(TAG, "🗑️ Clearing deletion tracker (restore mode)")
            storage.clearDeletionTracker()

            // ⚡ v1.3.1 FIX: Clear lastSyncTimestamp to force download ALL files
            // Restore = "Server ist die Quelle" → Ignore lokale Sync-History
            val previousSyncTime = getLastSyncTimestamp()
            prefs.edit { putLong("last_sync_timestamp", 0) }
            Logger.d(TAG, "🔄 Cleared lastSyncTimestamp (was: $previousSyncTime) - will download all files")

            // ⚡ v1.3.1 FIX: Clear E-Tag caches to force re-download
            eTagCache.clearAll()
            // 🆕 v1.9.0: Auch Content-Hashes löschen (damit alle Notizen neu hochgeladen werden)
            prefs.edit {
                prefs.all.keys.filter { it.startsWith("content_hash_") }.forEach { key ->
                    remove(key)
                }
            }
            Logger.d(TAG, "🔄 Cleared E-Tag + content hash caches - will re-download all files")

            // Determine forceOverwrite flag
            val forceOverwrite = (mode == dev.dettmer.simplenotes.backup.RestoreMode.OVERWRITE_DUPLICATES)
            Logger.d(TAG, "forceOverwrite: $forceOverwrite")

            // Mode-specific preparation
            when (mode) {
                dev.dettmer.simplenotes.backup.RestoreMode.REPLACE -> {
                    // Clear everything
                    Logger.d(TAG, "🗑️ REPLACE mode: Clearing local storage...")
                    storage.deleteAllNotes()
                    // Tracker already cleared above
                    // 🐛 Fix: lokale Ordner-Metadaten wurden bei REPLACE nie zurückgesetzt — NoteDownloader
                    // fügt beim Re-Download nur neue Namen hinzu (folderStore.addFolders), entfernt nie alte.
                    // REPLACE = Server ist die Quelle der Wahrheit → Ordnerliste komplett zurücksetzen,
                    // bevor der Download sie aus den Server-Daten neu aufbaut.
                    Logger.d(TAG, "🗑️ REPLACE mode: Clearing local folder store...")
                    folderStore.clear()
                }
                dev.dettmer.simplenotes.backup.RestoreMode.MERGE -> {
                    // Keep local notes, just add from server
                    Logger.d(TAG, "🔀 MERGE mode: Keeping local notes...")
                    // ✅ Tracker cleared → Server notes will NOT be skipped
                }
                dev.dettmer.simplenotes.backup.RestoreMode.OVERWRITE_DUPLICATES -> {
                    // Will overwrite in downloadRemoteNotes if needed
                    Logger.d(TAG, "♻️ OVERWRITE mode: Will force update duplicates...")
                    // ✅ Tracker cleared → Server notes will NOT be skipped
                }
            }

            // 🆕 v1.2.2: Use downloadRemoteNotes() with Root fallback + forceOverwrite
            // 🆕 v1.3.0: Pass FRESH empty tracker to avoid loading stale cached data
            Logger.d(
                TAG,
                "📡 Calling downloadRemoteNotes() - " +
                    "includeRootFallback: true, forceOverwrite: $forceOverwrite"
            )
            val emptyTracker = DeletionTracker() // Fresh empty tracker after clear
            val result = downloadRemoteNotes(
                webdav = webdav,
                serverUrl = serverUrl,
                includeRootFallback = true, // ✅ Enable backward compatibility for restore
                forceOverwrite = forceOverwrite, // ✅ v1.3.0: Force overwrite for OVERWRITE_DUPLICATES mode
                deletionTracker = emptyTracker // ✅ v1.3.0: Use fresh tracker to prevent skipping
            )

            Logger.d(TAG, "📊 Download result: downloaded=${result.downloadedCount}, conflicts=${result.conflictCount}")

            if (result.downloadedCount == 0 && mode == dev.dettmer.simplenotes.backup.RestoreMode.REPLACE) {
                if (emptyIsSuccess) {
                    Logger.d(TAG, "📭 No notes found on server — treated as success (emptyIsSuccess)")
                    return@withContext RestoreResult(isSuccess = true, errorMessage = null, restoredCount = 0)
                }
                Logger.w(TAG, "⚠️ No notes found on server!")
                return@withContext RestoreResult(
                    isSuccess = false,
                    errorMessage = "Keine Notizen auf Server gefunden",
                    restoredCount = 0
                )
            }

            // NOTE: Code that removes restored notes from deletion tracker is now REDUNDANT
            // because we cleared the tracker at the start. But keep it for safety:
            if (result.downloadedCount > 0) {
                val deletionTracker = storage.loadDeletionTracker()
                val allNotes = storage.loadAllNotes()
                var trackingModified = false

                allNotes.forEach { note ->
                    if (deletionTracker.isDeleted(note.id)) {
                        deletionTracker.removeDeletion(note.id)
                        trackingModified = true
                        Logger.d(TAG, "🔓 Removed from deletion tracker: ${note.id} (restored from server)")
                    }
                }

                if (trackingModified) {
                    storage.saveDeletionTracker(deletionTracker)
                    Logger.d(TAG, "💾 Updated deletion tracker after restore")
                }
            }

            // 🐛 Fix: Ordner-Metadaten (Farben, Tombstones, leere Ordner) aus folders.json anwenden.
            // downloadRemoteNotes() legt nur nackte Ordnernamen an — Farben/leere Ordner kommen nur hierüber.
            syncFolderMetadataSafe(webdav, serverUrl)

            saveLastSyncTimestamp()

            Logger.d(TAG, "✅ Restore completed: ${result.downloadedCount} notes")
            Logger.d(TAG, "═══════════════════════════════════════")

            RestoreResult(
                isSuccess = true,
                errorMessage = null,
                restoredCount = result.downloadedCount
            )
        } catch (e: Exception) {
            Logger.e(TAG, "═══════════════════════════════════════")
            Logger.e(TAG, "💥 restoreFromServer() EXCEPTION")
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            Logger.e(TAG, "═══════════════════════════════════════")
            RestoreResult(
                isSuccess = false,
                errorMessage = e.message ?: "Unbekannter Fehler",
                restoredCount = 0
            )
        }
    }

    /**
     * Manueller Markdown-Sync — delegiert an MarkdownSyncManager.
     */
    suspend fun syncMarkdownFiles(serverUrl: String, username: String, password: String): Int =
        markdownSyncManager.syncAll(serverUrl, username, password)

    /**
     * Auto-Import Markdown files during regular sync — delegiert an MarkdownSyncManager.
     */
    private suspend fun importMarkdownFiles(webdav: WebDavClient, serverUrl: String, excludeNoteIds: Set<String> = emptySet()): Int =
        markdownSyncManager.importAll(webdav, serverUrl, excludeNoteIds)

    /**
     * Deletes a note from the server (JSON + Markdown)
     * Does NOT delete from local storage!
     *
     * v1.4.1: Now supports v1.2.0 compatibility mode - also checks ROOT folder
     * for notes that were created before the /notes/ directory structure.
     *
     * @param noteId The ID of the note to delete
     * @return true if at least one file was deleted, false otherwise
     */
    suspend fun deleteNoteFromServer(noteId: String, folderName: String? = null): Boolean =
        noteDownloader.deleteFromServer(noteId, folderName)

    /**
     * 🆕 v2.7.0 (Folders): Löscht das JSON- und Markdown-Server-Verzeichnis eines Ordners,
     * aber nur wenn beide leer sind. Nicht-leere Verzeichnisse bleiben unangetastet.
     */
    suspend fun deleteServerFolderIfEmpty(folderName: String): Boolean = withContext(Dispatchers.IO) {
        val webdav = connectionManager.getOrCreateClient() ?: return@withContext false
        val serverUrl = urlBuilder.getServerUrl() ?: return@withContext false
        var ok = true
        // 🔧 v2.14.0: MD-Spiegel nur anfassen, wenn ein MD-Feature aktiv ist — dasselbe Gate wie
        // in NoteDownloader.deleteFromServer. Ohne das kostet jeder Ordner-Cleanup einen PROPFIND
        // plus ein 404-DELETE gegen ein Verzeichnis, das es nie gab.
        val urls = buildList {
            add(urlBuilder.getNotesFolderUrl(serverUrl, folderName))
            if (markdownFeaturesEnabled()) add(urlBuilder.getMarkdownFolderUrl(serverUrl, folderName))
        }
        for (url in urls) {
            try {
                if (isDeletableEmptyDir(url, webdav.listOrNull(url))) {
                    webdav.delete(url)
                    Logger.d(TAG, "🗑️ Deleted empty server folder: $url")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "deleteServerFolderIfEmpty($url) skipped: ${e.message}")
                ok = false
            }
        }
        ok
    }

    /**
     * Manual Markdown sync: Export all notes + Import all MD files
     * Used by manual sync button in settings (when Auto-Sync is OFF)
     *
     * @return ManualMarkdownSyncResult with export and import counts
     */
    suspend fun manualMarkdownSync(): ManualMarkdownSyncResult = withContext(ioDispatcher) {
        return@withContext try {
            val webdav = getOrCreateWebDavClient()
                ?: throw SyncException(context.getString(R.string.error_sardine_client_failed))
            val serverUrl = getServerUrl()
                ?: throw SyncException(context.getString(R.string.error_server_url_not_configured))

            val username = CredentialStore.getUsername(context).orEmpty()
            val password = CredentialStore.getPassword(context).orEmpty()

            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                throw SyncException(context.getString(R.string.error_server_not_configured))
            }

            Logger.d(TAG, "🔄 Manual Markdown Sync START")

            // Step 1: Export alle lokalen Notizen nach Markdown
            val exportedCount = exportAllNotesToMarkdown(
                serverUrl = serverUrl,
                username = username,
                password = password
            )
            Logger.d(TAG, "   ✅ Export: $exportedCount notes")

            // Step 2: Import alle Server-Markdown-Dateien
            val importedCount = importMarkdownFiles(webdav, serverUrl)
            Logger.d(TAG, "   ✅ Import: $importedCount notes")

            Logger.d(TAG, "🎉 Manual Markdown Sync COMPLETE: exported=$exportedCount, imported=$importedCount")

            ManualMarkdownSyncResult(
                exportedCount = exportedCount,
                importedCount = importedCount
            )
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Manual Markdown Sync FAILED", e)
            throw e
        }
    }
}

data class RestoreResult(val isSuccess: Boolean, val errorMessage: String?, val restoredCount: Int)
