package dev.dettmer.simplenotes.sync

import android.content.Context
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.SyncException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Result of manual Markdown sync operation
 */
data class ManualMarkdownSyncResult(
    val exportedCount: Int,
    val importedCount: Int
)

/**
 * 🆕 v1.11.0: Ergebnis eines Upload-Durchlaufs.
 * Enthält neben der Anzahl auch die IDs der Notizen, für die ein Markdown-Export
 * durchgeführt wurde. Diese werden an importMarkdownFiles() weitergegeben, um
 * Re-Import der soeben exportierten Dateien zu verhindern.
 */
data class UploadBatchResult(
    val uploadedCount: Int,
    val markdownExportedNoteIds: Set<String>
)

@Suppress("LargeClass") // Functions extracted into NoteUploader/NoteDownloader/MarkdownSyncManager (v2.0.0)
class WebDavSyncService(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    companion object {
        private const val TAG = "WebDavSyncService"

        // � v1.3.1: Mutex um parallele Syncs zu verhindern
        private val syncMutex = Mutex()
    }
    
    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val gateChecker = SyncGateChecker(context, prefs, ioDispatcher)
    private val eTagCache = ETagCache(prefs)
    private val timestampManager = SyncTimestampManager(prefs)
    private val exceptionMapper = SyncExceptionMapper(context)
    private val urlBuilder = SyncUrlBuilder(prefs)
    private val connectionManager = ConnectionManager(prefs)
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
    
    private val markdownSyncManager = MarkdownSyncManager(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        connectionManager = connectionManager,
        timestampManager = timestampManager,
        ioDispatcher = ioDispatcher
    )

    private val noteUploader = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        ioDispatcher = ioDispatcher,
        markdownExporter = { sardine, serverUrl, note, mdDirExists ->
            markdownSyncManager.exportSingle(sardine, serverUrl, note, mdDirExists)
        }
    )

    private val noteDownloader = NoteDownloader(
        prefs = prefs,
        storage = storage,
        eTagCache = eTagCache,
        urlBuilder = urlBuilder,
        connectionManager = connectionManager,
        markdownSyncManager = markdownSyncManager,
        ioDispatcher = ioDispatcher
    )

    /**
     * ⚡ v1.3.1: Gecachten Sardine-Client zurückgeben oder erstellen
     * Spart ~100ms pro Aufruf durch Wiederverwendung
     * 🆕 Issue #21: internal für NotesImportWizard-Zugriff
     */
    internal fun getOrCreateSardine(): Sardine? = connectionManager.getOrCreateClient()
    
    /**
     * 🆕 v2.0.0: Delegiert an SyncUrlBuilder (extrahiert in Commit 17).
     */
    internal fun getServerUrl(): String? = urlBuilder.getServerUrl()
    
    /**
     * Stellt sicher dass notes-md/ Ordner existiert
     * 
     * Wird beim ersten erfolgreichen Sync aufgerufen (unabhängig von MD-Feature).
     * Cached in Memory - nur einmal pro App-Session.
     */
    private fun ensureMarkdownDirectoryExists(sardine: Sardine, serverUrl: String) {
        if (connectionManager.markdownDirEnsured) return

        try {
            val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)

            if (!sardine.exists(mdUrl)) {
                sardine.createDirectory(mdUrl)
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
     */
    private fun ensureNotesDirectoryExists(sardine: Sardine, notesUrl: String) {
        if (connectionManager.notesDirEnsured) {
            Logger.d(TAG, "⚡ $activeSyncFolderName/ directory already verified (cached)")
            return
        }
        
        try {
            Logger.d(TAG, "🔍 Checking if $activeSyncFolderName/ directory exists...")
            if (!sardine.exists(notesUrl)) {
                Logger.d(TAG, "📁 Creating $activeSyncFolderName/ directory...")
                sardine.createDirectory(notesUrl)
            }
            Logger.d(TAG, "    ✅ $activeSyncFolderName/ directory ready")
            connectionManager.notesDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "💥 CRASH checking/creating $activeSyncFolderName/ directory!", e)
            throw e
        }
    }
    
    /**
     * Checks if server has changes using E-Tag caching
     * 
     * v1.3.0: Also checks /notes-md/ if Markdown Auto-Import enabled
     * 
     * Performance: ~100-200ms (E-Tag cache hit)
     *              ~300-500ms (E-Tag miss, needs PROPFIND)
     * 
     * Strategy:
     * 1. Store E-Tag of /notes/ collection after each sync
     * 2. HEAD request to check if E-Tag changed
     * 3. If changed → server has updates
     * 4. If unchanged → skip sync
     */
    @Suppress("ReturnCount") // Early returns for conditional checks
    private fun checkServerForChanges(sardine: Sardine, serverUrl: String): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            val lastSyncTime = getLastSyncTimestamp()
            
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - assuming server has changes")
                return true
            }
            
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            // 🔧 v1.7.2: Exception wird NICHT gefangen - muss nach oben propagieren!
            // Wenn sardine.exists() timeout hat, soll hasUnsyncedChanges() das behandeln
            // 🐛 Fix #21: Wenn /notes/ nicht existiert → true zurückgeben, damit syncNotes()
            // aufgerufen wird und ensureNotesDirectoryExists() das Verzeichnis anlegen kann.
            // Vorher: return false → Deadlock (Verzeichnis wird nie erstellt, Sync nie gestartet)
            if (!sardine.exists(notesUrl)) {
                Logger.d(TAG, "📁 /notes/ doesn't exist yet - will create on sync")
                return true
            }
            
            // ====== JSON FILES CHECK (/notes/) ======
            
            // ⚡ v1.3.1: File-level E-Tag check in downloadRemoteNotes() is optimal!
            // Collection E-Tag doesn't work (server-dependent, doesn't track file changes)
            // → Always proceed to download phase where file-level E-Tags provide fast skips
            
            // For hasUnsyncedChanges(): Conservative approach - assume changes may exist
            // Actual file-level E-Tag checks in downloadRemoteNotes() will skip unchanged files (0ms each)
            val hasJsonChanges = true  // Assume yes, let file E-Tags optimize
            
            // ====== MARKDOWN FILES CHECK (/notes-md/) ======
            // IMPORTANT: E-Tag for collections does NOT work for content changes!
            // → Use hybrid approach: If-Modified-Since + Timestamp fallback
            
            val markdownAutoImportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
            if (!markdownAutoImportEnabled) {
                Logger.d(TAG, "⏭️ Markdown check skipped (auto-import disabled)")
            } else {
                val mdUrl = urlBuilder.getMarkdownUrl(serverUrl)
                
                if (!sardine.exists(mdUrl)) {
                    Logger.d(TAG, "📁 /notes-md/ doesn't exist - no markdown changes")
                } else {
                    Logger.d(TAG, "📝 Checking Markdown files (hybrid approach)...")
                    
                    // Strategy: Timestamp-based check (reliable, always works)
                    // Note: If-Modified-Since support varies by WebDAV server
                    // We use timestamp comparison which is universal
                    val mdResources = sardine.list(mdUrl, 1)
                    val mdHasNewer = mdResources.any { resource ->
                        !resource.isDirectory && 
                        resource.name.endsWith(".md") &&
                        resource.modified?.time?.let { 
                            val hasNewer = it > lastSyncTime
                            if (hasNewer) {
                                Logger.d(
                                    TAG,
                                    "   📄 ${resource.name}: modified=${resource.modified}, " +
                                        "lastSync=$lastSyncTime"
                                )
                            }
                            hasNewer
                        } ?: false
                    }
                    
                    if (mdHasNewer) {
                        val mdCount = mdResources.count { !it.isDirectory && it.name.endsWith(".md") }
                        Logger.d(TAG, "📝 Markdown files have changes ($mdCount files checked)")
                        return true
                    } else {
                        Logger.d(TAG, "✅ Markdown files up-to-date (timestamp check)")
                    }
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            
            // Return TRUE if JSON or Markdown have potential changes
            // (File-level E-Tags will do the actual skip optimization during sync)
            if (hasJsonChanges) {
                Logger.d(TAG, "✅ JSON may have changes - will check file E-Tags (${elapsed}ms)")
                return true
            }
            
            Logger.d(TAG, "✅ No changes detected (Markdown checked, ${elapsed}ms)")
            return false
            
        } catch (e: Exception) {
            Logger.w(TAG, "Server check failed: ${e.message} - assuming changes exist")
            true  // Safe default: check anyway
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
            
            // Check 2: Local changes (Timestamp ODER SyncStatus)
            // 🛡️ v1.8.2 (IMPL_19a): Klassen-Feld nutzen statt neue Instanz
            val allNotes = storage.loadAllNotes()
            // 🛡️ v1.8.2 (IMPL_22): Auch PENDING-Status prüfen —
            // nach Server-Wechsel wird syncStatus auf PENDING gesetzt, aber updatedAt bleibt gleich
            val hasLocalChanges = allNotes.any { note ->
                note.updatedAt > lastSyncTime ||
                note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.PENDING
            }
            
            if (hasLocalChanges) {
                val unsyncedByTime = allNotes.count { it.updatedAt > lastSyncTime }
                val unsyncedByStatus = allNotes.count { it.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.PENDING }
                Logger.d(TAG, "📝 Local changes: $unsyncedByTime by timestamp, $unsyncedByStatus PENDING")
                return@withContext true
            }
            
            // Check 3: Server changes (respects user preference)
            val alwaysCheckServer = prefs.getBoolean(Constants.KEY_ALWAYS_CHECK_SERVER, true)
            
            if (!alwaysCheckServer) {
                Logger.d(TAG, "⏭️ Server check disabled by user - has changes: false")
                return@withContext false
            }
            
            // Perform intelligent server check
            val sardine = getOrCreateSardine()
            val serverUrl = getServerUrl()
            
            if (sardine == null || serverUrl == null) {
                Logger.w(TAG, "⚠️ Cannot check server - no credentials")
                return@withContext false
            }
            
            val hasServerChanges = checkServerForChanges(sardine, serverUrl)
            Logger.d(TAG, "📊 Final check: local=$hasLocalChanges, server=$hasServerChanges")
            
            hasServerChanges
            
        } catch (e: Exception) {
            // 🔧 v1.7.2 KRITISCH: Bei Server-Fehler (Timeout, etc.) return TRUE!
            // Grund: Besser fälschlich synchen als "Already synced" zeigen obwohl Server nicht erreichbar
            Logger.e(TAG, "❌ Failed to check server for changes: ${e.message}")
            Logger.d(TAG, "⚠️ Returning TRUE (will attempt sync) - server check failed")
            true  // Sicherheitshalber TRUE → Sync wird versucht und gibt dann echte Fehlermeldung
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
            val sardine = getOrCreateSardine() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-Zugangsdaten nicht konfiguriert"
            )
            
            val serverUrl = getServerUrl() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-URL nicht konfiguriert"
            )
            
            // Only test if directory exists or can be created
            val exists = sardine.exists(serverUrl)
            if (!exists) {
                sardine.createDirectory(serverUrl)
            }

            // 🔧 v1.9.0 Fix: activeSyncFolderName VOR getNotesUrl() laden
            activeSyncFolderName = prefs.getString(
                Constants.KEY_SYNC_FOLDER_NAME,
                Constants.DEFAULT_SYNC_FOLDER_NAME
            ) ?: Constants.DEFAULT_SYNC_FOLDER_NAME

            // 🆕 Issue #21: Sync-Ordner prüfen und Status mit Ordnernamen kommunizieren
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            val notesExist = try { sardine.exists(notesUrl) } catch (_: Exception) { false }
            val folderName = activeSyncFolderName
            val infoMessage = if (notesExist) {
                context.getString(R.string.test_connection_success_with_notes, folderName)
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
    
    suspend fun syncNotes(): SyncResult = withContext(ioDispatcher) {
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
            
            Logger.d(TAG, "📍 Step 1: Getting Sardine client")
            
            val sardine = try {
                getOrCreateSardine()
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in getOrCreateSardine()!", e)
                e.printStackTrace()
                throw e
            }
            
            if (sardine == null) {
                Logger.e(TAG, "❌ Sardine is null - credentials missing")
                return@withContext SyncResult(
                    isSuccess = false,
                    errorMessage = "Server-Zugangsdaten nicht konfiguriert"
                )
            }
            Logger.d(TAG, "    ✅ Sardine client created")
            
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
            activeSyncFolderName = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
                ?: Constants.DEFAULT_SYNC_FOLDER_NAME
            Logger.d(TAG, "📁 Sync folder: $activeSyncFolderName")
            Logger.d(TAG, "🔐 Credentials configured: ${prefs.getString(Constants.KEY_USERNAME, null) != null}")
            
            var syncedCount = 0
            var conflictCount = 0
            
            Logger.d(TAG, "📍 Step 3: Checking server directory")
            // ⚡ v1.3.1: Verwende gecachte Directory-Checks
            val notesUrl = urlBuilder.getNotesUrl(serverUrl)
            ensureNotesDirectoryExists(sardine, notesUrl)
            
            // Ensure notes-md/ directory exists (for Markdown export)
            ensureMarkdownDirectoryExists(sardine, serverUrl)
            
            // 🆕 v1.8.0: Phase 2 - Uploading (Phase wird nur bei echten Uploads gesetzt)
            Logger.d(TAG, "📍 Step 4: Uploading local notes")
            // Upload local notes
            // 🆕 v1.11.0: UploadBatchResult enthält zusätzlich MD-Export-IDs für Import-Exclusion
            var markdownExportedNoteIds: Set<String> = emptySet()
            try {
                Logger.d(TAG, "⬆️ Uploading local notes...")
                val uploadResult = uploadLocalNotes(
                    sardine,
                    serverUrl,
                    onProgress = { current, total, noteTitle ->
                        SyncStateManager.updateProgress(
                            phase = SyncPhase.UPLOADING,
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
            
            // 🆕 v1.8.0: Phase 3 - Downloading (Phase wird nur bei echten Downloads gesetzt)
            Logger.d(TAG, "📍 Step 5: Downloading remote notes")
            // Download remote notes
            var deletedOnServerCount = 0  // 🆕 v1.8.0
            try {
                Logger.d(TAG, "⬇️ Downloading remote notes...")
                val downloadResult = downloadRemoteNotes(
                    sardine, 
                    serverUrl,
                    includeRootFallback = true,  // ✅ v1.3.0: Enable for v1.2.0 compatibility
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
                deletedOnServerCount = downloadResult.deletedOnServerCount  // 🆕 v1.8.0
                Logger.d(
                    TAG,
                    "✅ Downloaded: ${downloadResult.downloadedCount} notes, " +
                        "Conflicts: ${downloadResult.conflictCount}, " +
                        "Deleted on server: ${downloadResult.deletedOnServerCount}"  // 🆕 v1.8.0
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
                    // 🆕 v1.11.0: Pass exported note IDs to prevent re-import of just-exported files
                    markdownImportedCount = importMarkdownFiles(sardine, serverUrl, markdownExportedNoteIds)
                    Logger.d(TAG, "✅ Auto-imported: $markdownImportedCount Markdown files")
                    
                    // 🔧 v1.7.2 (IMPL_014): Re-upload notes that were updated from Markdown
                    if (markdownImportedCount > 0) {
                        Logger.d(TAG, "📤 Re-uploading notes updated from Markdown (JSON sync)...")
                        val reUploadResult = uploadLocalNotes(sardine, serverUrl)
                        Logger.d(TAG, "✅ Re-uploaded: ${reUploadResult.uploadedCount} notes (JSON updated on server)")
                        // 🔧 v1.11.0: Re-Uploads NICHT zum syncedCount addieren.
                        // Re-Uploads sind ein technisches Artefakt der MD→JSON-Sync-Kette,
                        // keine vom User initiierten Aktionen. Die importierten Markdown-Änderungen
                        // werden bereits über markdownImportedCount in effectiveSyncedCount berücksichtigt.
                        // Vorher: syncedCount += reUploadedCount → führte zu Doppelzählung.
                    }
                } else {
                    Logger.d(TAG, "⏭️ Markdown auto-import disabled")
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
            if (deletedOnServerCount > 0) {  // 🆕 v1.8.0
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
                deletedOnServerCount = deletedOnServerCount  // 🆕 v1.8.0
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
     * 🔧 v1.9.0: Parallele Uploads mit bounded concurrency
     * Analog zu ParallelDownloader-Pattern, aber für Uploads.
     */
    private suspend fun uploadLocalNotes(
        sardine: Sardine,
        serverUrl: String,
        onProgress: (current: Int, total: Int, noteTitle: String) -> Unit = { _, _, _ -> }
    ): UploadBatchResult = noteUploader.uploadAll(sardine, serverUrl, onProgress)

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
        sardine: Sardine,
        serverUrl: String,
        includeRootFallback: Boolean = false,
        forceOverwrite: Boolean = false,
        deletionTracker: DeletionTracker = storage.loadDeletionTracker(),
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): DownloadResult = noteDownloader.downloadAll(
        sardine = sardine,
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
    suspend fun restoreFromServer(
        mode: dev.dettmer.simplenotes.backup.RestoreMode = dev.dettmer.simplenotes.backup.RestoreMode.REPLACE
    ): RestoreResult = withContext(ioDispatcher) {
        return@withContext try {
            val sardine = getOrCreateSardine() ?: return@withContext RestoreResult(
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
            prefs.edit().putLong("last_sync_timestamp", 0).apply()
            Logger.d(TAG, "🔄 Cleared lastSyncTimestamp (was: $previousSyncTime) - will download all files")
            
            // ⚡ v1.3.1 FIX: Clear E-Tag caches to force re-download
            eTagCache.clearAll()
            // 🆕 v1.9.0: Auch Content-Hashes löschen (damit alle Notizen neu hochgeladen werden)
            val contentHashEditor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("content_hash_") }.forEach { key ->
                contentHashEditor.remove(key)
            }
            contentHashEditor.apply()
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
            val emptyTracker = DeletionTracker()  // Fresh empty tracker after clear
            val result = downloadRemoteNotes(
                sardine = sardine, 
                serverUrl = serverUrl,
                includeRootFallback = true,  // ✅ Enable backward compatibility for restore
                forceOverwrite = forceOverwrite,  // ✅ v1.3.0: Force overwrite for OVERWRITE_DUPLICATES mode
                deletionTracker = emptyTracker  // ✅ v1.3.0: Use fresh tracker to prevent skipping
            )
            
            Logger.d(TAG, "📊 Download result: downloaded=${result.downloadedCount}, conflicts=${result.conflictCount}")
            
            if (result.downloadedCount == 0 && mode == dev.dettmer.simplenotes.backup.RestoreMode.REPLACE) {
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
    suspend fun syncMarkdownFiles(
        serverUrl: String,
        username: String,
        password: String
    ): Int = markdownSyncManager.syncAll(serverUrl, username, password)
    
    /**
     * Auto-Import Markdown files during regular sync — delegiert an MarkdownSyncManager.
     */
    private fun importMarkdownFiles(
        sardine: Sardine,
        serverUrl: String,
        excludeNoteIds: Set<String> = emptySet()
    ): Int = markdownSyncManager.importAll(sardine, serverUrl, excludeNoteIds)

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
    suspend fun deleteNoteFromServer(noteId: String): Boolean =
        noteDownloader.deleteFromServer(noteId)

    /**
     * Manual Markdown sync: Export all notes + Import all MD files
     * Used by manual sync button in settings (when Auto-Sync is OFF)
     * 
     * @return ManualMarkdownSyncResult with export and import counts
     */
    suspend fun manualMarkdownSync(): ManualMarkdownSyncResult = withContext(ioDispatcher) {
        return@withContext try {
            val sardine = getOrCreateSardine()
                ?: throw SyncException(context.getString(R.string.error_sardine_client_failed))
            val serverUrl = getServerUrl()
                ?: throw SyncException(context.getString(R.string.error_server_url_not_configured))
            
            val username = prefs.getString(Constants.KEY_USERNAME, "").orEmpty()
            val password = prefs.getString(Constants.KEY_PASSWORD, "").orEmpty()
            
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
            val importedCount = importMarkdownFiles(sardine, serverUrl)
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

data class RestoreResult(
    val isSuccess: Boolean,
    val errorMessage: String?,
    val restoredCount: Int
)
