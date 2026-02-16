package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.parallel.DownloadTask
import dev.dettmer.simplenotes.sync.parallel.DownloadTaskResult
import dev.dettmer.simplenotes.sync.parallel.ParallelDownloader
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.SyncException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Date

/**
 * Result of manual Markdown sync operation
 */
data class ManualMarkdownSyncResult(
    val exportedCount: Int,
    val importedCount: Int
)

@Suppress("LargeClass") 
// TODO v2.0.0: Split into SyncOrchestrator, NoteUploader, NoteDownloader, ConflictResolver
class WebDavSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebDavSyncService"
        private const val SOCKET_TIMEOUT_MS = 10000  // 🔧 v1.7.2: 10s für stabile Verbindungen (1s war zu kurz)
        private const val MAX_FILENAME_LENGTH = 200
        private const val ETAG_PREVIEW_LENGTH = 8
        private const val CONTENT_PREVIEW_LENGTH = 50
        
        // 🔒 v1.3.1: Mutex um parallele Syncs zu verhindern
        private val syncMutex = Mutex()
    }
    
    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private var markdownDirEnsured = false  // Cache für Ordner-Existenz
    private var notesDirEnsured = false     // ⚡ v1.3.1: Cache für /notes/ Ordner-Existenz
    
    // ⚡ v1.3.1 Performance: Session-Caches (werden am Ende von syncNotes() geleert)
    private var sessionSardine: SafeSardineWrapper? = null
    
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
    
    /**
     * 🔒 v1.7.1: Checks if any VPN/Wireguard interface is active.
     * 
     * Wireguard VPNs run as separate network interfaces (tun*, wg*, *-wg-*),
     * and are NOT detected via NetworkCapabilities.TRANSPORT_VPN!
     * 
     * @return true if VPN interface is detected
     */
    @Suppress("unused") // Reserved for future VPN detection feature
    private fun isVpnInterfaceActive(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp) continue
                
                val name = iface.name.lowercase()
                // Check for VPN/Wireguard interface patterns:
                // - tun0, tun1, etc. (OpenVPN, generic VPN)
                // - wg0, wg1, etc. (Wireguard)
                // - *-wg-* (Mullvad, ProtonVPN style: se-sto-wg-202)
                if (name.startsWith("tun") || 
                    name.startsWith("wg") || 
                    name.contains("-wg-") ||
                    name.startsWith("ppp")) {
                    Logger.d(TAG, "🔒 VPN interface detected: ${iface.name}")
                    return true
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ Failed to check VPN interfaces: ${e.message}")
        }
        return false
    }
    
    /**
     * ⚡ v1.3.1: Gecachten Sardine-Client zurückgeben oder erstellen
     * Spart ~100ms pro Aufruf durch Wiederverwendung
     */
    private fun getOrCreateSardine(): Sardine? {
        // Return cached if available
        sessionSardine?.let { 
            Logger.d(TAG, "⚡ Reusing cached Sardine client")
            return it 
        }
        
        // Create new client
        val sardine = createSardineClient()
        sessionSardine = sardine
        return sardine
    }
    
    /**
     * Erstellt einen neuen Sardine-Client (intern)
     * 
     * 🆕 v1.7.2: Intelligentes Routing basierend auf Ziel-Adresse
     * - Lokale Server: WiFi-Binding (bypass VPN)
     * - Externe Server: Default-Routing (nutzt VPN wenn aktiv)
     * 
     * 🔧 v1.7.1: Verwendet SafeSardineWrapper statt OkHttpSardine
     * - Verhindert Connection Leaks durch proper Response-Cleanup
     * - Preemptive Authentication für weniger 401-Round-Trips
     */
    private fun createSardineClient(): SafeSardineWrapper? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        
        Logger.d(TAG, "🔧 Creating SafeSardineWrapper")
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(SOCKET_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        
        return SafeSardineWrapper.create(okHttpClient, username, password)
    }
    
    /**
     * ⚡ v1.3.1: Session-Caches leeren (am Ende von syncNotes)
     * 🔧 v1.7.2 (IMPL_003): Schließt Sardine-Client explizit für Resource-Cleanup
     */
    private fun clearSessionCache() {
        // 🆕 v1.7.2: Explizites Schließen des Sardine-Clients
        sessionSardine?.let { sardine ->
            try {
                sardine.close()
                Logger.d(TAG, "🧹 Sardine client closed")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to close Sardine client: ${e.message}")
            }
        }
        
        sessionSardine = null
        notesDirEnsured = false
        markdownDirEnsured = false
        Logger.d(TAG, "🧹 Session caches cleared")
    }
    
    private fun getServerUrl(): String? {
        return prefs.getString(Constants.KEY_SERVER_URL, null)
    }
    
    /**
     * Erzeugt notes/ URL aus Base-URL mit Smart Detection (Task #1.2.1-12)
     * 
     * Beispiele:
     * - http://server:8080/ → http://server:8080/notes/
     * - http://server:8080/notes/ → http://server:8080/notes/
     * - http://server:8080/notes → http://server:8080/notes/
     * - http://server:8080/my-path/ → http://server:8080/my-path/notes/
     * 
     * @param baseUrl Base Server-URL
     * @return notes/ Ordner-URL (mit trailing /)
     */
    private fun getNotesUrl(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        
        // Wenn URL bereits mit /notes endet → direkt nutzen
        return if (normalized.endsWith("/notes")) {
            "$normalized/"
        } else {
            "$normalized/notes/"
        }
    }
    
    /**
     * Erzeugt Markdown-Ordner-URL basierend auf getNotesUrl() (Task #1.2.1-14)
     * 
     * Beispiele:
     * - http://server:8080/ → http://server:8080/notes-md/
     * - http://server:8080/notes/ → http://server:8080/notes-md/
     * - http://server:8080/notes → http://server:8080/notes-md/
     * 
     * @param baseUrl Base Server-URL
     * @return Markdown-Ordner-URL (mit trailing /)
     */
    private fun getMarkdownUrl(baseUrl: String): String {
        val notesUrl = getNotesUrl(baseUrl)
        val normalized = notesUrl.trimEnd('/')
        
        // Ersetze /notes mit /notes-md
        return normalized.replace("/notes", "/notes-md") + "/"
    }
    
    /**
     * Stellt sicher dass notes-md/ Ordner existiert
     * 
     * Wird beim ersten erfolgreichen Sync aufgerufen (unabhängig von MD-Feature).
     * Cached in Memory - nur einmal pro App-Session.
     */
    private fun ensureMarkdownDirectoryExists(sardine: Sardine, serverUrl: String) {
        if (markdownDirEnsured) return
        
        try {
            val mdUrl = getMarkdownUrl(serverUrl)
            
            if (!sardine.exists(mdUrl)) {
                sardine.createDirectory(mdUrl)
                Logger.d(TAG, "📁 Created notes-md/ directory (for future use)")
            }
            
            markdownDirEnsured = true
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
        if (notesDirEnsured) {
            Logger.d(TAG, "⚡ notes/ directory already verified (cached)")
            return
        }
        
        try {
            Logger.d(TAG, "🔍 Checking if notes/ directory exists...")
            if (!sardine.exists(notesUrl)) {
                Logger.d(TAG, "📁 Creating notes/ directory...")
                sardine.createDirectory(notesUrl)
            }
            Logger.d(TAG, "    ✅ notes/ directory ready")
            notesDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "💥 CRASH checking/creating notes/ directory!", e)
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
    private suspend fun checkServerForChanges(sardine: Sardine, serverUrl: String): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            val lastSyncTime = getLastSyncTimestamp()
            
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - assuming server has changes")
                return true
            }
            
            val notesUrl = getNotesUrl(serverUrl)
            // 🔧 v1.7.2: Exception wird NICHT gefangen - muss nach oben propagieren!
            // Wenn sardine.exists() timeout hat, soll hasUnsyncedChanges() das behandeln
            if (!sardine.exists(notesUrl)) {
                Logger.d(TAG, "📁 /notes/ doesn't exist - assuming no server changes")
                return false
            }
            
            // ====== JSON FILES CHECK (/notes/) ======
            
            // ⚡ v1.3.1: File-level E-Tag check in downloadRemoteNotes() is optimal!
            // Collection E-Tag doesn't work (server-dependent, doesn't track file changes)
            // → Always proceed to download phase where file-level E-Tags provide fast skips
            
            // For hasUnsyncedChanges(): Conservative approach - assume changes may exist
            // Actual file-level E-Tag checks in downloadRemoteNotes() will skip unchanged files (0ms each)
            var hasJsonChanges = true  // Assume yes, let file E-Tags optimize
            
            // ====== MARKDOWN FILES CHECK (/notes-md/) ======
            // IMPORTANT: E-Tag for collections does NOT work for content changes!
            // → Use hybrid approach: If-Modified-Since + Timestamp fallback
            
            val markdownAutoImportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
            if (!markdownAutoImportEnabled) {
                Logger.d(TAG, "⏭️ Markdown check skipped (auto-import disabled)")
            } else {
                val mdUrl = getMarkdownUrl(serverUrl)
                
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
    suspend fun hasUnsyncedChanges(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val lastSyncTime = getLastSyncTimestamp()
            
            // Check 1: Never synced
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - has changes: true")
                return@withContext true
            }
            
            // Check 2: Local changes (Timestamp ODER SyncStatus)
            val storage = NotesStorage(context)
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
    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val serverUrl = getServerUrl()
            if (serverUrl == null) {
                Logger.d(TAG, "❌ Server URL not configured")
                return@withContext false
            }
            
            val url = URL(serverUrl)
            val host = url.host
            val port = if (url.port > 0) url.port else url.defaultPort
            
            Logger.d(TAG, "🔍 Checking server reachability: $host:$port")
            
            // Socket-Check mit Timeout
            // Gibt dem Netzwerk Zeit für Initialisierung (DHCP, Routing, Gateway)
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
            socket.close()
            
            Logger.d(TAG, "✅ Server is reachable")
            true
        } catch (e: Exception) {
            Logger.d(TAG, "❌ Server not reachable: ${e.message}")
            false
        }
    }
    
    /**
     * 🆕 v1.7.0: Prüft ob Gerät aktuell im WLAN ist
     * Für schnellen Pre-Check VOR dem langsamen Socket-Check
     * 
     * @return true wenn WLAN verbunden, false sonst (mobil oder kein Netzwerk)
     */
    fun isOnWiFi(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as? ConnectivityManager ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check WiFi state", e)
            false
        }
    }
    
    /**
     * 🆕 v1.7.0: Zentrale Sync-Gate Prüfung
     * Prüft ALLE Voraussetzungen bevor ein Sync gestartet wird.
     * Diese Funktion sollte VOR jedem syncNotes() Aufruf verwendet werden.
     * 
     * @return SyncGateResult mit canSync flag und optionalem Blockierungsgrund
     */
    fun canSync(): SyncGateResult {
        // 1. Offline Mode Check
        if (prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)) {
            return SyncGateResult(canSync = false, blockReason = null) // Silent skip
        }
        
        // 2. Server configured?
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            return SyncGateResult(canSync = false, blockReason = null) // Silent skip
        }
        
        // 3. WiFi-Only Check
        val wifiOnlySync = prefs.getBoolean(Constants.KEY_WIFI_ONLY_SYNC, Constants.DEFAULT_WIFI_ONLY_SYNC)
        if (wifiOnlySync && !isOnWiFi()) {
            return SyncGateResult(canSync = false, blockReason = "wifi_only")
        }
        
        return SyncGateResult(canSync = true, blockReason = null)
    }
    
    /**
     * 🆕 v1.7.0: Result-Klasse für canSync()
     */
    data class SyncGateResult(
        val canSync: Boolean,
        val blockReason: String? = null
    ) {
        val isBlockedByWifiOnly: Boolean get() = blockReason == "wifi_only"
    }
    
    suspend fun testConnection(): SyncResult = withContext(Dispatchers.IO) {
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
            
            SyncResult(
                isSuccess = true,
                syncedCount = 0,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            SyncResult(
                isSuccess = false,
                errorMessage = when (e) {
                    is java.net.UnknownHostException -> context.getString(R.string.snackbar_server_unreachable)
                    is java.net.SocketTimeoutException -> context.getString(R.string.snackbar_connection_timeout)
                    is javax.net.ssl.SSLException -> context.getString(R.string.sync_error_ssl)
                    is com.thegrizzlylabs.sardineandroid.impl.SardineException -> {
                        when (e.statusCode) {
                            401 -> context.getString(R.string.sync_error_auth_failed)
                            403 -> context.getString(R.string.sync_error_access_denied)
                            404 -> context.getString(R.string.sync_error_path_not_found)
                            500 -> context.getString(R.string.sync_error_server)
                            else -> context.getString(R.string.sync_error_http, e.statusCode)
                        }
                    }
                    else -> e.message ?: context.getString(R.string.sync_error_unknown)
                }
            )
        }
    }
    
    suspend fun syncNotes(): SyncResult = withContext(Dispatchers.IO) {
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
            Logger.d(TAG, "🔐 Credentials configured: ${prefs.getString(Constants.KEY_USERNAME, null) != null}")
            
            var syncedCount = 0
            var conflictCount = 0
            
            Logger.d(TAG, "📍 Step 3: Checking server directory")
            // ⚡ v1.3.1: Verwende gecachte Directory-Checks
            val notesUrl = getNotesUrl(serverUrl)
            ensureNotesDirectoryExists(sardine, notesUrl)
            
            // Ensure notes-md/ directory exists (for Markdown export)
            ensureMarkdownDirectoryExists(sardine, serverUrl)
            
            // 🆕 v1.8.0: Phase 2 - Uploading (Phase wird nur bei echten Uploads gesetzt)
            Logger.d(TAG, "📍 Step 4: Uploading local notes")
            // Upload local notes
            try {
                Logger.d(TAG, "⬆️ Uploading local notes...")
                val uploadedCount = uploadLocalNotes(
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
                syncedCount += uploadedCount
                Logger.d(TAG, "✅ Uploaded: $uploadedCount notes")
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
                    onProgress = { current, _, noteTitle ->
                        // 🆕 v1.8.0: Phase wird erst beim ersten echten Download gesetzt
                        // current = laufender Zähler (downloadedCount), kein Total → kein irreführender x/y Counter
                        SyncStateManager.updateProgress(
                            phase = SyncPhase.DOWNLOADING,
                            current = current,
                            total = 0,
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
                    // 🆕 v1.8.0: Phase nur setzen wenn Feature aktiv
                    SyncStateManager.updateProgress(phase = SyncPhase.IMPORTING_MARKDOWN)
                    
                    Logger.d(TAG, "📥 Auto-importing Markdown files...")
                    markdownImportedCount = importMarkdownFiles(sardine, serverUrl)
                    Logger.d(TAG, "✅ Auto-imported: $markdownImportedCount Markdown files")
                    
                    // 🔧 v1.7.2 (IMPL_014): Re-upload notes that were updated from Markdown
                    if (markdownImportedCount > 0) {
                        Logger.d(TAG, "📤 Re-uploading notes updated from Markdown (JSON sync)...")
                        val reUploadedCount = uploadLocalNotes(sardine, serverUrl)
                        Logger.d(TAG, "✅ Re-uploaded: $reUploadedCount notes (JSON updated on server)")
                        syncedCount += reUploadedCount
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
            
            // ✅ v1.3.0: Hybrid counting to prevent double-counting
            // - If JSON sync occurred, it represents unique notes (JSON is source of truth)
            // - If ONLY Markdown edits (no JSON), use Markdown count
            val effectiveSyncedCount = if (syncedCount > 0) {
                syncedCount  // JSON-based count is authoritative
            } else {
                markdownImportedCount  // Fallback: Markdown-only edits
            }
            
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
                errorMessage = when (e) {
                    is java.net.UnknownHostException -> "${context.getString(R.string.snackbar_server_unreachable)}: ${e.message}"
                    is java.net.SocketTimeoutException -> "${context.getString(R.string.snackbar_connection_timeout)}: ${e.message}"
                    is javax.net.ssl.SSLException -> context.getString(R.string.sync_error_ssl)
                    is com.thegrizzlylabs.sardineandroid.impl.SardineException -> {
                        when (e.statusCode) {
                            401 -> context.getString(R.string.sync_error_auth_failed)
                            403 -> context.getString(R.string.sync_error_access_denied)
                            404 -> context.getString(R.string.sync_error_path_not_found)
                            500 -> context.getString(R.string.sync_error_server)
                            else -> context.getString(R.string.sync_error_http, e.statusCode)
                        }
                    }
                    else -> e.message ?: context.getString(R.string.sync_error_unknown)
                }
            )
        }
        } finally {
            // ⚡ v1.3.1: Session-Caches leeren
            // 🛡️ v1.8.2 (IMPL_13): try-catch verhindert dass eine Exception in
            // clearSessionCache() den syncMutex.unlock() blockiert → permanenter Deadlock
            try {
                clearSessionCache()
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ clearSessionCache() failed (non-fatal): ${e.message}")
            }
            // 🆕 v1.8.0: Reset progress state
            SyncStateManager.resetProgress()
            // 🔒 v1.3.1: Sync-Mutex freigeben
            syncMutex.unlock()
        }
    }
    
    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements") 
    // Sync logic requires nested conditions for comprehensive error handling and state management
    private fun uploadLocalNotes(
        sardine: Sardine,
        serverUrl: String,
        onProgress: (current: Int, total: Int, noteTitle: String) -> Unit = { _, _, _ -> }  // 🆕 v1.8.0
    ): Int {
        var uploadedCount = 0
        val localNotes = storage.loadAllNotes()
        val markdownExportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        
        // 🆕 v1.8.0: Zähle zu uploadende Notizen für Progress
        val pendingNotes = localNotes.filter { 
            it.syncStatus == SyncStatus.LOCAL_ONLY || it.syncStatus == SyncStatus.PENDING 
        }
        val totalToUpload = pendingNotes.size
        
        // 🔧 v1.7.2 (IMPL_004): Batch E-Tag Updates für Performance
        val etagUpdates = mutableMapOf<String, String?>()
        
        for (note in localNotes) {
            try {
                // 1. JSON-Upload (Task #1.2.1-13: nutzt getNotesUrl())
                if (note.syncStatus == SyncStatus.LOCAL_ONLY || note.syncStatus == SyncStatus.PENDING) {
                    val notesUrl = getNotesUrl(serverUrl)
                    val noteUrl = "$notesUrl${note.id}.json"
                    
                    // 🔧 v1.7.2 FIX (IMPL_015): Status VOR Serialisierung auf SYNCED setzen
                    // Verhindert dass Server-JSON "syncStatus": "PENDING" enthält
                    val noteToUpload = note.copy(syncStatus = SyncStatus.SYNCED)
                    val jsonBytes = noteToUpload.toJson().toByteArray()
                    
                    Logger.d(TAG, "   📤 Uploading: ${note.id}.json (${note.title})")
                    sardine.put(noteUrl, jsonBytes, "application/json")
                    Logger.d(TAG, "      ✅ Upload successful")
                    
                    // Lokale Kopie auch mit SYNCED speichern
                    storage.saveNote(noteToUpload)
                    uploadedCount++
                    
                    // 🆕 v1.8.0: Progress mit Notiz-Titel
                    onProgress(uploadedCount, totalToUpload, note.title)
                    
                    // ⚡ v1.3.1: Refresh E-Tag after upload to prevent re-download
                    // 🔧 v1.7.2 (IMPL_004): Sammle E-Tags für Batch-Update
                    try {
                        val uploadedResource = sardine.list(noteUrl, 0).firstOrNull()
                        val newETag = uploadedResource?.etag
                        etagUpdates["etag_json_${note.id}"] = newETag
                        if (newETag != null) {
                            Logger.d(TAG, "      ⚡ Queued E-Tag: ${newETag.take(ETAG_PREVIEW_LENGTH)}")
                        } else {
                            Logger.d(TAG, "      ⚠️ No E-Tag from server, will invalidate")
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG, "      ⚠️ Failed to get E-Tag: ${e.message}")
                        etagUpdates["etag_json_${note.id}"] = null
                    }
                    
                    // 2. Markdown-Export (NEU in v1.2.0)
                    // Läuft NACH erfolgreichem JSON-Upload
                    if (markdownExportEnabled) {
                        try {
                            exportToMarkdown(sardine, serverUrl, noteToUpload)
                            Logger.d(TAG, "   📝 MD exported: ${noteToUpload.title}")
                        } catch (e: Exception) {
                            Logger.e(TAG, "MD-Export failed for ${noteToUpload.id}: ${e.message}")
                            // Kein throw! JSON-Sync darf nicht blockiert werden
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Upload failed for note ${note.id}, marking as pending: ${e.message}")
                // Mark as pending for retry
                val updatedNote = note.copy(syncStatus = SyncStatus.PENDING)
                storage.saveNote(updatedNote)
            }
        }
        
        // 🔧 v1.7.2 (IMPL_004): Batch-Update aller E-Tags in einer Operation
        if (etagUpdates.isNotEmpty()) {
            batchUpdateETags(etagUpdates)
        }
        
        return uploadedCount
    }
    
    /**
     * 🔧 v1.7.2 (IMPL_004): Batch-Update von E-Tags
     * 
     * Schreibt alle E-Tags in einer einzelnen I/O-Operation statt einzeln.
     * Performance-Gewinn: ~50-100ms pro Batch (statt N × apply())
     * 
     * @param updates Map von E-Tag Keys zu Values (null = remove)
     */
    private fun batchUpdateETags(updates: Map<String, String?>) {
        try {
            val editor = prefs.edit()
            var putCount = 0
            var removeCount = 0
            
            updates.forEach { (key, value) ->
                if (value != null) {
                    editor.putString(key, value)
                    putCount++
                } else {
                    editor.remove(key)
                    removeCount++
                }
            }
            
            editor.apply()
            Logger.d(TAG, "⚡ Batch-updated E-Tags: $putCount saved, $removeCount removed")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to batch-update E-Tags", e)
        }
    }
    
    /**
     * Exportiert einzelne Note als Markdown (Task #1.2.0-11)
     * 
     * @param sardine Sardine-Client
     * @param serverUrl Server-URL (notes/ Ordner)
     * @param note Note zum Exportieren
     */
    private fun exportToMarkdown(sardine: Sardine, serverUrl: String, note: Note) {
        val mdUrl = getMarkdownUrl(serverUrl)
        
        // Erstelle notes-md/ Ordner falls nicht vorhanden
        if (!sardine.exists(mdUrl)) {
            sardine.createDirectory(mdUrl)
            Logger.d(TAG, "📁 Created notes-md/ directory")
        }
        
        // Sanitize Filename (Task #1.2.0-12)
        val baseFilename = sanitizeFilename(note.title)
        var filename = "$baseFilename.md"
        var noteUrl = "$mdUrl/$filename"
        
        // Prüfe ob Datei bereits existiert und von anderer Note stammt
        try {
            if (sardine.exists(noteUrl)) {
                // Lese existierende Datei und prüfe ID im YAML-Header
                val existingContent = sardine.get(noteUrl).bufferedReader().use { it.readText() }
                val existingIdMatch = Regex("^---\\n.*?\\nid:\\s*([a-f0-9-]+)", RegexOption.DOT_MATCHES_ALL)
                    .find(existingContent)
                val existingId = existingIdMatch?.groupValues?.get(1)
                
                if (existingId != null && existingId != note.id) {
                    // Andere Note hat gleichen Titel - verwende ID-Suffix
                    val shortId = note.id.take(8)
                    filename = "${baseFilename}_$shortId.md"
                    noteUrl = "$mdUrl/$filename"
                    Logger.d(TAG, "📝 Duplicate title, using: $filename")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "⚠️ Could not check existing file: ${e.message}")
            // Continue with default filename
        }
        
        // Konvertiere zu Markdown
        val mdContent = note.toMarkdown().toByteArray()
        
        // Upload
        sardine.put(noteUrl, mdContent, "text/markdown")
    }
    
    /**
     * Sanitize Filename für sichere Dateinamen (Task #1.2.0-12)
     * 
     * Entfernt Windows/Linux-verbotene Zeichen, begrenzt Länge
     * 
     * @param title Original-Titel
     * @return Sicherer Filename
     */
    private fun sanitizeFilename(title: String): String {
        return title
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")  // Ersetze verbotene Zeichen
            .replace(Regex("\\s+"), " ")              // Normalisiere Whitespace
            .take(MAX_FILENAME_LENGTH)                 // Max Zeichen (Reserve für .md)
            .trim('_', ' ')                            // Trim Underscores/Spaces
    }
    
    /**
     * Generiert eindeutigen Markdown-Dateinamen für eine Notiz.
     * Bei Duplikaten wird die Note-ID als Suffix angehängt.
     * 
     * @param note Die Notiz
     * @param usedFilenames Set der bereits verwendeten Dateinamen (ohne .md)
     * @return Eindeutiger Dateiname (ohne .md Extension)
     */
    private fun getUniqueMarkdownFilename(note: Note, usedFilenames: MutableSet<String>): String {
        val baseFilename = sanitizeFilename(note.title)
        
        return if (usedFilenames.contains(baseFilename)) {
            // Duplikat - hänge gekürzte ID an
            val shortId = note.id.take(8)
            val uniqueFilename = "${baseFilename}_$shortId"
            usedFilenames.add(uniqueFilename)
            uniqueFilename
        } else {
            usedFilenames.add(baseFilename)
            baseFilename
        }
    }
    
    /**
     * Exportiert ALLE lokalen Notizen als Markdown (Initial-Export)
     * 
     * Wird beim ersten Aktivieren der Desktop-Integration aufgerufen.
     * Exportiert auch bereits synchronisierte Notizen.
     * 
     * @return Anzahl exportierter Notizen
     */
    suspend fun exportAllNotesToMarkdown(
        serverUrl: String,
        username: String,
        password: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        Logger.d(TAG, "🔄 Starting initial Markdown export for all notes...")
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(SOCKET_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        
        val sardine = SafeSardineWrapper.create(okHttpClient, username, password)
        
        try {
            val mdUrl = getMarkdownUrl(serverUrl)
            
            // Ordner sollte bereits existieren (durch #1.2.1-00), aber Sicherheitscheck
            ensureMarkdownDirectoryExists(sardine, serverUrl)
            
            // Hole ALLE lokalen Notizen (inklusive SYNCED)
            val allNotes = storage.loadAllNotes()
            val totalCount = allNotes.size
            var exportedCount = 0
            
            // Track used filenames to handle duplicates
            val usedFilenames = mutableSetOf<String>()
            
            Logger.d(TAG, "📝 Found $totalCount notes to export")
            
            allNotes.forEachIndexed { index, note ->
                try {
                    // Progress-Callback
                    onProgress(index + 1, totalCount)
                    
                    // Eindeutiger Filename (mit Duplikat-Handling)
                    val filename = getUniqueMarkdownFilename(note, usedFilenames) + ".md"
                    val noteUrl = "$mdUrl/$filename"
                    
                    // Konvertiere zu Markdown
                    val mdContent = note.toMarkdown().toByteArray()
                    
                    // Upload (überschreibt falls vorhanden)
                    sardine.put(noteUrl, mdContent, "text/markdown")
                    
                    exportedCount++
                    Logger.d(TAG, "   ✅ Exported [${index + 1}/$totalCount]: ${note.title} -> $filename")
                    
                } catch (e: Exception) {
                    Logger.e(TAG, "❌ Failed to export ${note.title}: ${e.message}")
                    // Continue mit nächster Note (keine Abbruch bei Einzelfehlern)
                }
            }
            
            Logger.d(TAG, "✅ Initial export completed: $exportedCount/$totalCount notes")
            
            // ⚡ v1.3.1: Set lastSyncTimestamp to enable timestamp-based skip on next sync
            // This prevents re-downloading all MD files on the first manual sync after initial export
            if (exportedCount > 0) {
                val timestamp = System.currentTimeMillis()
                prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
                Logger.d(TAG, "💾 Set lastSyncTimestamp after initial export (enables fast next sync)")
            }
            
            return@withContext exportedCount
        } finally {
            // 🐛 FIX: Connection Leak — SafeSardineWrapper explizit schließen
            sardine.close()
        }
    }
    
    private data class DownloadResult(
        val downloadedCount: Int,
        val conflictCount: Int,
        val deletedOnServerCount: Int = 0  // 🆕 v1.8.0
    )
    
    /**
     * 🆕 v1.8.0: Erkennt Notizen, die auf dem Server gelöscht wurden
     * 🔧 v1.8.1: Safety-Guard gegen leere serverNoteIds (verhindert Massenlöschung)
     * 
     * Keine zusätzlichen HTTP-Requests! Nutzt die bereits geladene
     * serverNoteIds-Liste aus dem PROPFIND-Request.
     * 
     * Prüft ALLE Notizen (Notes + Checklists), da beide als
     * JSON in /notes/{id}.json gespeichert werden.
     * NoteType (NOTE vs CHECKLIST) spielt keine Rolle für die Detection.
     * 
     * @param serverNoteIds Set aller Note-IDs auf dem Server (aus PROPFIND)
     * @param localNotes Alle lokalen Notizen
     * @return Anzahl der als DELETED_ON_SERVER markierten Notizen
     */
    private fun detectServerDeletions(
        serverNoteIds: Set<String>,
        localNotes: List<Note>
    ): Int {
        val syncedNotes = localNotes.filter { it.syncStatus == SyncStatus.SYNCED }
        
        // 🔧 v1.8.1 SAFETY: Wenn serverNoteIds leer ist, NIEMALS Notizen als gelöscht markieren!
        // Ein leeres Set bedeutet wahrscheinlich: PROPFIND fehlgeschlagen, /notes/ nicht gefunden,
        // oder Netzwerkfehler — NICHT dass alle Notizen gelöscht wurden.
        if (serverNoteIds.isEmpty()) {
            Logger.w(TAG, "⚠️ detectServerDeletions: serverNoteIds is EMPTY! " +
                "Skipping deletion detection to prevent data loss. " +
                "localSynced=${syncedNotes.size}, localTotal=${localNotes.size}")
            return 0
        }
        
        // 🔧 v1.8.1 SAFETY: Wenn ALLE lokalen SYNCED-Notizen als gelöscht erkannt werden,
        // ist das fast sicher ein Fehler (z.B. falsche Server-URL oder partieller PROPFIND).
        // Maximal 50% der Notizen dürfen als gelöscht markiert werden.
        val potentialDeletions = syncedNotes.count { it.id !in serverNoteIds }
        if (syncedNotes.size > 1 && potentialDeletions == syncedNotes.size) {
            Logger.e(TAG, "🚨 detectServerDeletions: ALL ${syncedNotes.size} synced notes " +
                "would be marked as deleted! This is almost certainly a bug. " +
                "serverNoteIds=${serverNoteIds.size}. ABORTING deletion detection.")
            return 0
        }
        
        // 🆕 v1.8.0 (IMPL_022): Statistik-Log für Debugging
        Logger.d(TAG, "🔍 detectServerDeletions: " +
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
    
    @Suppress(
        "NestedBlockDepth",
        "LoopWithTooManyJumpStatements",
        "LongMethod",
        "ComplexMethod"
    )
    // Sync logic requires nested conditions for comprehensive error handling and conflict resolution
    // TODO: Refactor into smaller functions in v1.9.0/v2.0.0 (see LINT_DETEKT_FEHLER_BEHEBUNG_PLAN.md)
    private fun downloadRemoteNotes(
        sardine: Sardine, 
        serverUrl: String,
        includeRootFallback: Boolean = false,  // 🆕 v1.2.2: Only for restore from server
        forceOverwrite: Boolean = false,  // 🆕 v1.3.0: For OVERWRITE_DUPLICATES mode
        deletionTracker: DeletionTracker = storage.loadDeletionTracker(),  // 🆕 v1.3.0: Allow passing fresh tracker
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }  // 🆕 v1.8.0
    ): DownloadResult {
        var downloadedCount = 0
        var conflictCount = 0
        var skippedDeleted = 0  // NEW: Track skipped deleted notes
        val processedIds = mutableSetOf<String>()  // 🆕 v1.2.2: Track already loaded notes
        
        Logger.d(TAG, "📥 downloadRemoteNotes() called:")
        Logger.d(TAG, "   includeRootFallback: $includeRootFallback")
        Logger.d(TAG, "   forceOverwrite: $forceOverwrite")
        
        // Use provided deletion tracker (allows fresh tracker from restore)
        var trackerModified = false
        
        // 🆕 v1.8.0: Collect server note IDs for deletion detection
        val serverNoteIds = mutableSetOf<String>()
        
        try {
            // 🆕 PHASE 1: Download from /notes/ (new structure v1.2.1+)
            val notesUrl = getNotesUrl(serverUrl)
            Logger.d(TAG, "🔍 Phase 1: Checking /notes/ at: $notesUrl")
            
            // ⚡ v1.3.1: Performance - Get last sync time for skip optimization
            val lastSyncTime = getLastSyncTimestamp()
            var skippedUnchanged = 0
            
            if (sardine.exists(notesUrl)) {
                Logger.d(TAG, "   ✅ /notes/ exists, scanning...")
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
                    val noteId = resource.name.removeSuffix(".json")
                    val noteUrl = notesUrl.trimEnd('/') + "/" + resource.name

                    // ⚡ v1.3.1: HYBRID PERFORMANCE - Timestamp + E-Tag (like Markdown!)
                    val serverETag = resource.etag
                    val cachedETag = prefs.getString("etag_json_$noteId", null)
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

                    // PRIMARY: Timestamp check (works on first sync!)
                    // Same logic as Markdown sync - skip if not modified since last sync
                    // BUT: Always download if file doesn't exist locally!
                    if (!forceOverwrite && fileExistsLocally && lastSyncTime > 0 && serverModified <= lastSyncTime) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: Not modified since last sync (timestamp)")
                        processedIds.add(noteId)
                        continue
                    }

                    // SECONDARY: E-Tag check (for performance after first sync)
                    // Catches cases where file was re-uploaded with same content
                    // BUT: Always download if file doesn't exist locally!
                    if (!forceOverwrite && fileExistsLocally && serverETag != null && serverETag == cachedETag) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: E-Tag match (content unchanged)")
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
                    // Konfigurierbare Parallelität aus Settings
                    val maxParallel = prefs.getInt(
                        Constants.KEY_MAX_PARALLEL_DOWNLOADS,
                        Constants.DEFAULT_MAX_PARALLEL_DOWNLOADS
                    )

                    val downloader = ParallelDownloader(
                        sardine = sardine,
                        maxParallelDownloads = maxParallel
                    )

                    downloader.onProgress = { completed, total, currentFile ->
                        onProgress(completed, total, currentFile ?: "?")
                    }

                    val downloadResults = runBlocking {
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

                                processedIds.add(remoteNote.id)
                                val localNote = storage.loadNote(remoteNote.id)

                                when {
                                    localNote == null -> {
                                        // New note from server
                                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ✅ Downloaded from /notes/: ${remoteNote.id}")

                                        // ⚡ Batch E-Tag for later
                                        if (result.etag != null) {
                                            etagUpdates["etag_json_${result.noteId}"] = result.etag
                                        }
                                    }
                                    forceOverwrite -> {
                                        // OVERWRITE mode: Always replace regardless of timestamps
                                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                        downloadedCount++
                                        Logger.d(TAG, "   ♻️ Overwritten from /notes/: ${remoteNote.id}")

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
                                            Logger.d(TAG, "   ✅ Updated from /notes/: ${remoteNote.id}")

                                            if (result.etag != null) {
                                                etagUpdates["etag_json_${result.noteId}"] = result.etag
                                            }
                                        }
                                    }
                                    // else: Local is newer or same → skip silently
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
                        prefs.edit().apply {
                            etagUpdates.forEach { (key, value) -> putString(key, value) }
                        }.apply()
                        Logger.d(TAG, "   💾 Batch-saved ${etagUpdates.size} E-Tags")
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
                        
                        // NEW: Check deletion tracker
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
            Logger.e(TAG, "❌ downloadRemoteNotes failed", e)
        }
        
        // NEW: Save deletion tracker if modified
        if (trackerModified) {
            storage.saveDeletionTracker(deletionTracker)
            Logger.d(TAG, "💾 Deletion tracker updated")
        }
        
        // 🆕 v1.8.0: Server-Deletions erkennen (nach Downloads)
        val allLocalNotes = storage.loadAllNotes()
        val deletedOnServerCount = detectServerDeletions(serverNoteIds, allLocalNotes)
        
        if (deletedOnServerCount > 0) {
            Logger.d(TAG, "$deletedOnServerCount note(s) detected as deleted on server")
        }
        
        Logger.d(TAG, "📊 Total: $downloadedCount downloaded, $conflictCount conflicts, $skippedDeleted deleted")
        return DownloadResult(downloadedCount, conflictCount, deletedOnServerCount)
    }
    
    private fun saveLastSyncTimestamp() {
        val now = System.currentTimeMillis()
        
        // ⚡ v1.3.1: Simplified - file-level E-Tags cached individually in downloadRemoteNotes()
        // No need for collection E-Tag (doesn't work reliably across WebDAV servers)
        prefs.edit()
            .putLong(Constants.KEY_LAST_SYNC, now)
            .putLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, now)
            .apply()
        
        Logger.d(TAG, "💾 Saved sync timestamp (file E-Tags cached individually)")
    }
    
    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(Constants.KEY_LAST_SYNC, 0)
    }
    
    fun getLastSuccessfulSyncTimestamp(): Long {
        return prefs.getLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, 0)
    }
    
    /**
     * Restore all notes from server with different modes (v1.3.0)
     * @param mode RestoreMode (REPLACE, MERGE, or OVERWRITE_DUPLICATES)
     * @return RestoreResult with count of restored notes
     */
    suspend fun restoreFromServer(
        mode: dev.dettmer.simplenotes.backup.RestoreMode = dev.dettmer.simplenotes.backup.RestoreMode.REPLACE
    ): RestoreResult = withContext(Dispatchers.IO) {
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
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("etag_json_") }.forEach { key ->
                editor.remove(key)
            }
            editor.apply()
            Logger.d(TAG, "🔄 Cleared E-Tag caches - will re-download all files")
            
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
     * Synchronisiert Markdown-Dateien (Import von Desktop-Programmen) (Task #1.2.0-14)
     * 
     * Last-Write-Wins Konfliktauflösung basierend auf updatedAt Timestamp
     * 
     * @param serverUrl WebDAV Server-URL (notes/ Ordner)
     * @param username WebDAV Username
     * @param password WebDAV Password
     * @return Anzahl importierter Notizen
     */
    suspend fun syncMarkdownFiles(
        serverUrl: String, 
        username: String, 
        password: String
    ): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            Logger.d(TAG, "📝 Starting Markdown sync...")
            
            val okHttpClient = OkHttpClient.Builder().build()
            val sardine = SafeSardineWrapper.create(okHttpClient, username, password)
            
            try {
                val mdUrl = getMarkdownUrl(serverUrl)
                
                // Check if notes-md/ exists
                if (!sardine.exists(mdUrl)) {
                    Logger.d(TAG, "⚠️ notes-md/ directory not found - skipping MD import")
                    return@withContext 0
                }
                
                val localNotes = storage.loadAllNotes()
                val mdResources = sardine.list(mdUrl).filter { it.name.endsWith(".md") }
                var importedCount = 0
                
                Logger.d(TAG, "📂 Found ${mdResources.size} markdown files")
                
                for (resource in mdResources) {
                    try {
                        // Download MD-File
                        val mdContent = sardine.get(resource.href.toString())
                            .bufferedReader().use { it.readText() }
                        
                        // Parse zu Note
                        val mdNote = Note.fromMarkdown(mdContent) ?: continue
                        
                        val localNote = localNotes.find { it.id == mdNote.id }
                        
                        // Konfliktauflösung: Last-Write-Wins
                        when {
                            localNote == null -> {
                                // Neue Notiz vom Desktop
                                storage.saveNote(mdNote)
                                importedCount++
                                Logger.d(TAG, "   ✅ Imported new: ${mdNote.title}")
                            }
                            mdNote.updatedAt > localNote.updatedAt -> {
                                // Desktop-Version ist neuer (Last-Write-Wins)
                                storage.saveNote(mdNote)
                                importedCount++
                                Logger.d(TAG, "   ✅ Updated from MD: ${mdNote.title}")
                            }
                            // Sonst: Lokale Version behalten
                            else -> {
                                Logger.d(TAG, "   ⏭️ Local newer, skipping: ${mdNote.title}")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to import ${resource.name}", e)
                        // Continue with other files
                    }
                }
                
                Logger.d(TAG, "✅ Markdown sync completed: $importedCount imported")
                importedCount
            } finally {
                // 🐛 FIX: Connection Leak — SafeSardineWrapper explizit schließen
                sardine.close()
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Markdown sync failed", e)
            0
        }
    }
    
    /**
     * Auto-import Markdown files during regular sync (v1.3.0)
     * Called automatically if KEY_MARKDOWN_AUTO_IMPORT is enabled
     * 
     * ⚡ v1.3.1: Performance-Optimierung - Skip unveränderte Dateien
     */
    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements") 
    // Import logic requires nested conditions for file validation and duplicate handling
    private fun importMarkdownFiles(sardine: Sardine, serverUrl: String): Int {
        return try {
            Logger.d(TAG, "📝 Importing Markdown files...")
            
            val mdUrl = getMarkdownUrl(serverUrl)
            
            // Check if notes-md/ exists
            if (!sardine.exists(mdUrl)) {
                Logger.d(TAG, "   ⚠️ notes-md/ directory not found - skipping")
                return 0
            }
            
            val mdResources = sardine.list(mdUrl).filter { !it.isDirectory && it.name.endsWith(".md") }
            var importedCount = 0
            var skippedCount = 0  // ⚡ v1.3.1: Zähle übersprungene Dateien
            
            Logger.d(TAG, "   📂 Found ${mdResources.size} markdown files")
            
            // ⚡ v1.3.1: Performance-Optimierung - Letzten Sync-Zeitpunkt holen
            val lastSyncTime = getLastSyncTimestamp()
            Logger.d(TAG, "   📅 Last sync: ${Date(lastSyncTime)}")
            
            for (resource in mdResources) {
                try {
                    val serverModifiedTime = resource.modified?.time ?: 0L
                    
                    // ⚡ v1.3.1: PERFORMANCE - Skip wenn Datei seit letztem Sync nicht geändert wurde
                    // Das ist der Haupt-Performance-Fix! Spart ~500ms pro Datei bei Nextcloud.
                    if (lastSyncTime > 0 && serverModifiedTime <= lastSyncTime) {
                        skippedCount++
                        Logger.d(TAG, "   ⏭️ Skipping ${resource.name}: not modified since last sync")
                        continue
                    }
                    
                    Logger.d(TAG, "   🔍 Processing: ${resource.name}, modified=${resource.modified}")
                    
                    // Build full URL
                    val mdFileUrl = mdUrl.trimEnd('/') + "/" + resource.name
                    
                    // Download MD content
                    val mdContent = sardine.get(mdFileUrl).bufferedReader().use { it.readText() }
                    Logger.d(TAG, "      Downloaded ${mdContent.length} chars")
                    
                    // 🔧 v1.7.2 (IMPL_014): Server mtime übergeben für korrekte Timestamp-Sync
                    val mdNote = Note.fromMarkdown(mdContent, serverModifiedTime)
                    if (mdNote == null) {
                        Logger.w(TAG, "      ⚠️ Failed to parse ${resource.name} - fromMarkdown returned null")
                        continue
                    }
                    
                    // v1.4.0 FIX: Validierung - leere TEXT-Notizen nicht importieren wenn lokal Content existiert
                    val localNote = storage.loadNote(mdNote.id)
                    if (mdNote.noteType == dev.dettmer.simplenotes.models.NoteType.TEXT &&
                        mdNote.content.isBlank() && 
                        localNote != null && localNote.content.isNotBlank()) {
                        Logger.w(
                            TAG,
                            "      ⚠️ Skipping ${resource.name}: " +
                                "MD content empty but local has content - likely parse error!"
                        )
                        continue
                    }
                    
                    Logger.d(
                        TAG,
                        "      Parsed: id=${mdNote.id}, title=${mdNote.title}, " +
                            "updatedAt=${Date(mdNote.updatedAt)}, " +
                            "content=${mdNote.content.take(CONTENT_PREVIEW_LENGTH)}..."
                    )
                    
                    Logger.d(
                        TAG,
                        "      Local note: " + if (localNote == null) {
                            "NOT FOUND"
                        } else {
                            "exists, updatedAt=${Date(localNote.updatedAt)}, " +
                                "syncStatus=${localNote.syncStatus}"
                        }
                    )
                    
                    // ⚡ v1.3.1: Content-basierte Erkennung
                    // Wichtig: Vergleiche IMMER den Inhalt, wenn die Datei seit letztem Sync geändert wurde!
                    // Der YAML-Timestamp kann veraltet sein (z.B. bei externer Bearbeitung ohne Obsidian)
                    Logger.d(
                        TAG,
                        "      Comparison: mdUpdatedAt=${mdNote.updatedAt}, " +
                            "localUpdated=${localNote?.updatedAt ?: 0L}"
                    )
                    
                    // Content-Vergleich: Ist der Inhalt tatsächlich unterschiedlich?
                    val contentChanged = localNote != null && (
                        mdNote.content != localNote.content || 
                        mdNote.title != localNote.title ||
                        mdNote.checklistItems != localNote.checklistItems
                    )
                    
                    if (contentChanged) {
                        Logger.d(TAG, "      📝 Content differs from local!")
                    }
                    
                    // Conflict resolution: Content-First, dann Timestamp
                    when {
                        localNote == null -> {
                            // New note from desktop
                            storage.saveNote(mdNote.copy(syncStatus = SyncStatus.SYNCED))
                            importedCount++
                            Logger.d(TAG, "   ✅ Imported new from Markdown: ${mdNote.title}")
                        }
                        // ⚡ v1.3.1 FIX: Content-basierter Skip - nur wenn Inhalt UND Timestamp gleich
                        localNote.syncStatus == SyncStatus.SYNCED &&
                            !contentChanged &&
                            localNote.updatedAt >= mdNote.updatedAt -> {
                            // Inhalt identisch UND Timestamps passen → Skip
                            skippedCount++
                            Logger.d(
                                TAG,
                                "   ⏭️ Skipped ${mdNote.title}: content identical " +
                                    "(local=${localNote.updatedAt}, md=${mdNote.updatedAt})"
                            )
                        }
                        // 🔧 v1.7.2 (IMPL_014): Content geändert → Importieren UND als PENDING markieren!
                        // PENDING triggert JSON-Upload beim nächsten Sync-Zyklus
                        contentChanged && localNote.syncStatus == SyncStatus.SYNCED -> {
                            storage.saveNote(mdNote.copy(
                                updatedAt = serverModifiedTime,  // Server mtime verwenden
                                syncStatus = SyncStatus.PENDING  // ⬅️ KRITISCH: Triggert JSON-Upload
                            ))
                            importedCount++
                            Logger.d(TAG, "   ✅ Imported changed content (marked PENDING for JSON sync): ${mdNote.title}")
                        }
                        mdNote.updatedAt > localNote.updatedAt -> {
                            // Markdown has newer YAML timestamp
                            Logger.d(TAG, "      Decision: Markdown has newer timestamp!")
                            if (localNote.syncStatus == SyncStatus.PENDING) {
                                // Conflict: local has pending changes
                                storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                Logger.w(TAG, "   ⚠️ Conflict: Markdown vs local pending: ${mdNote.id}")
                            } else {
                                // Import with the newer YAML timestamp
                                storage.saveNote(mdNote.copy(syncStatus = SyncStatus.SYNCED))
                                importedCount++
                                Logger.d(TAG, "   ✅ Updated from Markdown (newer timestamp): ${mdNote.title}")
                            }
                        }
                        else -> {
                            // Local has pending changes but MD is older - keep local
                            skippedCount++
                            Logger.d(
                                TAG,
                                "   ⏭️ Skipped ${mdNote.title}: local is newer or pending " +
                                    "(local=${localNote.updatedAt}, md=${mdNote.updatedAt})"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "   ⚠️ Failed to import ${resource.name}", e)
                    // Continue with other files
                }
            }
            
            // ⚡ v1.3.1: Verbessertes Logging mit Skip-Count
            Logger.d(TAG, "   📊 Markdown import complete: $importedCount imported, $skippedCount skipped (unchanged)")
            importedCount
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Markdown import failed", e)
            0
        }
    }
    
    /**
     * Finds a Markdown file by scanning YAML frontmatter for note ID
     * Used when local note is deleted and title is unavailable
     * 
     * @param sardine Sardine client
     * @param mdUrl Base URL of notes-md/ directory
     * @param noteId The note ID to search for
     * @return Filename if found, null otherwise
     */
    private suspend fun findMarkdownFileByNoteId(
        sardine: Sardine,
        mdUrl: String,
        noteId: String
    ): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            Logger.d(TAG, "🔍 Scanning MD files for ID: $noteId")
            val resources = sardine.list(mdUrl)
            
            for (resource in resources) {
                if (resource.isDirectory || !resource.name.endsWith(".md")) {
                    continue
                }
                
                try {
                    // Download MD content
                    val mdFileUrl = mdUrl.trimEnd('/') + "/" + resource.name
                    val mdContent = sardine.get(mdFileUrl).bufferedReader().use { it.readText() }
                    
                    // Parse YAML frontmatter for ID
                    val idMatch = Regex("""^---\s*\n.*?id:\s*([a-f0-9-]+)""", RegexOption.DOT_MATCHES_ALL)
                        .find(mdContent)
                    
                    if (idMatch?.groupValues?.get(1) == noteId) {
                        Logger.d(TAG, "   ✅ Found MD file: ${resource.name}")
                        return@withContext resource.name
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "   ⚠️ Failed to parse ${resource.name}: ${e.message}")
                    // Continue with next file
                }
            }
            
            Logger.w(TAG, "   ❌ No MD file found for ID: $noteId")
            null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan MD files: ${e.message}")
            null
        }
    }
    
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
    suspend fun deleteNoteFromServer(noteId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val sardine = getOrCreateSardine() ?: return@withContext false
            val serverUrl = getServerUrl() ?: return@withContext false
            
            var deletedJson = false
            var deletedMd = false
            
            // v1.4.1: Try to delete JSON from /notes/ first (standard path)
            val jsonUrl = getNotesUrl(serverUrl) + "$noteId.json"
            if (sardine.exists(jsonUrl)) {
                sardine.delete(jsonUrl)
                deletedJson = true
                Logger.d(TAG, "🗑️ Deleted from server: $noteId.json (from /notes/)")
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
            val mdBaseUrl = getMarkdownUrl(serverUrl)
            val note = storage.loadNote(noteId)
            var mdFilenameToDelete: String? = null
            
            if (note != null) {
                // Fast path: Note still exists locally, use title
                mdFilenameToDelete = sanitizeFilename(note.title) + ".md"
                Logger.d(TAG, "🔍 MD deletion: Using title from local note: $mdFilenameToDelete")
            } else {
                // Fallback: Note deleted locally, scan YAML frontmatter
                Logger.d(TAG, "⚠️ MD deletion: Note not found locally, scanning YAML...")
                mdFilenameToDelete = findMarkdownFileByNoteId(sardine, mdBaseUrl, noteId)
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
                Logger.w(TAG, "⚠️ Note $noteId not found on server")
                return@withContext false
            }
            
            // Remove from deletion tracker (was explicitly deleted from server)
            val deletionTracker = storage.loadDeletionTracker()
            if (deletionTracker.isDeleted(noteId)) {
                deletionTracker.removeDeletion(noteId)
                storage.saveDeletionTracker(deletionTracker)
                Logger.d(TAG, "🔓 Removed from deletion tracker: $noteId")
            }
            
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete note from server: $noteId", e)
            false
        }
    }
    
    /**
     * Manual Markdown sync: Export all notes + Import all MD files
     * Used by manual sync button in settings (when Auto-Sync is OFF)
     * 
     * @return ManualMarkdownSyncResult with export and import counts
     */
    suspend fun manualMarkdownSync(): ManualMarkdownSyncResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val sardine = getOrCreateSardine()
                ?: throw SyncException(context.getString(R.string.error_sardine_client_failed))
            val serverUrl = getServerUrl()
                ?: throw SyncException(context.getString(R.string.error_server_url_not_configured))
            
            val username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
            val password = prefs.getString(Constants.KEY_PASSWORD, "") ?: ""
            
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
