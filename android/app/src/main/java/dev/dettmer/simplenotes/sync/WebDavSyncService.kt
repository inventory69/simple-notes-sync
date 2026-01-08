package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.Date
import javax.net.SocketFactory

/**
 * Result of manual Markdown sync operation
 */
data class ManualMarkdownSyncResult(
    val exportedCount: Int,
    val importedCount: Int
)

class WebDavSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebDavSyncService"
        
        // 🔒 v1.3.1: Mutex um parallele Syncs zu verhindern
        private val syncMutex = Mutex()
    }
    
    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private var markdownDirEnsured = false  // Cache für Ordner-Existenz
    private var notesDirEnsured = false     // ⚡ v1.3.1: Cache für /notes/ Ordner-Existenz
    
    // ⚡ v1.3.1 Performance: Session-Caches (werden am Ende von syncNotes() geleert)
    private var sessionSardine: Sardine? = null
    private var sessionWifiAddress: InetAddress? = null
    private var sessionWifiAddressChecked = false  // Flag ob WiFi-Check bereits durchgeführt
    
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
     * ⚡ v1.3.1: Gecachte WiFi-Adresse zurückgeben oder berechnen
     */
    private fun getOrCacheWiFiAddress(): InetAddress? {
        // Return cached if already checked this session
        if (sessionWifiAddressChecked) {
            return sessionWifiAddress
        }
        
        // Calculate and cache
        sessionWifiAddress = getWiFiInetAddressInternal()
        sessionWifiAddressChecked = true
        return sessionWifiAddress
    }
    
    /**
     * Findet WiFi Interface IP-Adresse (um VPN zu umgehen)
     */
    private fun getWiFiInetAddressInternal(): InetAddress? {
        try {
            Logger.d(TAG, "🔍 getWiFiInetAddress() called")
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            Logger.d(TAG, "    Active network: $network")
            
            if (network == null) {
                Logger.d(TAG, "❌ No active network")
                return null
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Logger.d(TAG, "    Network capabilities: $capabilities")
            
            if (capabilities == null) {
                Logger.d(TAG, "❌ No network capabilities")
                return null
            }
            
            // Nur wenn WiFi aktiv
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Logger.d(TAG, "⚠️ Not on WiFi, using default routing")
                return null
            }
            
            Logger.d(TAG, "✅ Network is WiFi, searching for interface...")
            
            // Finde WiFi Interface
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                
                Logger.d(TAG, "    Checking interface: ${iface.name}, isUp=${iface.isUp}")
                
                // WiFi Interfaces: wlan0, wlan1, etc.
                if (!iface.name.startsWith("wlan")) continue
                if (!iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    
                    Logger.d(TAG, "        Address: ${addr.hostAddress}, IPv4=${addr is Inet4Address}, loopback=${addr.isLoopbackAddress}, linkLocal=${addr.isLinkLocalAddress}")
                    
                    // Nur IPv4, nicht loopback, nicht link-local
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        Logger.d(TAG, "✅ Found WiFi IP: ${addr.hostAddress} on ${iface.name}")
                        return addr
                    }
                }
            }
            
            Logger.w(TAG, "⚠️ No WiFi interface found, using default routing")
            return null
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Failed to get WiFi interface", e)
            return null
        }
    }
    
    /**
     * Custom SocketFactory die an WiFi-IP bindet (VPN Fix)
     */
    private inner class WiFiSocketFactory(private val wifiAddress: InetAddress) : SocketFactory() {
        override fun createSocket(): Socket {
            val socket = Socket()
            socket.bind(InetSocketAddress(wifiAddress, 0))
            Logger.d(TAG, "🔌 Socket bound to WiFi IP: ${wifiAddress.hostAddress}")
            return socket
        }
        
        override fun createSocket(host: String, port: Int): Socket {
            val socket = createSocket()
            socket.connect(InetSocketAddress(host, port))
            return socket
        }
        
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
            return createSocket(host, port)
        }
        
        override fun createSocket(host: InetAddress, port: Int): Socket {
            val socket = createSocket()
            socket.connect(InetSocketAddress(host, port))
            return socket
        }
        
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
            return createSocket(address, port)
        }
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
     */
    private fun createSardineClient(): Sardine? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        
        Logger.d(TAG, "🔧 Creating OkHttpSardine with WiFi binding")
        Logger.d(TAG, "    Context: ${context.javaClass.simpleName}")
        
        // ⚡ v1.3.1: Verwende gecachte WiFi-Adresse
        val wifiAddress = getOrCacheWiFiAddress()
        
        val okHttpClient = if (wifiAddress != null) {
            Logger.d(TAG, "✅ Using WiFi-bound socket factory")
            OkHttpClient.Builder()
                .socketFactory(WiFiSocketFactory(wifiAddress))
                .build()
        } else {
            Logger.d(TAG, "⚠️ Using default OkHttpClient (no WiFi binding)")
            OkHttpClient.Builder().build()
        }
        
        return OkHttpSardine(okHttpClient).apply {
            setCredentials(username, password)
        }
    }
    
    /**
     * ⚡ v1.3.1: Session-Caches leeren (am Ende von syncNotes)
     */
    private fun clearSessionCache() {
        sessionSardine = null
        sessionWifiAddress = null
        sessionWifiAddressChecked = false
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
    private suspend fun checkServerForChanges(sardine: Sardine, serverUrl: String): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            val lastSyncTime = getLastSyncTimestamp()
            
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - assuming server has changes")
                return true
            }
            
            val notesUrl = getNotesUrl(serverUrl)
            if (!sardine.exists(notesUrl)) {
                Logger.d(TAG, "📁 /notes/ doesn't exist - no server changes")
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
                                Logger.d(TAG, "   📄 ${resource.name}: modified=${resource.modified}, lastSync=$lastSyncTime")
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
            
            // Check 2: Local changes
            val storage = NotesStorage(context)
            val allNotes = storage.loadAllNotes()
            val hasLocalChanges = allNotes.any { note ->
                note.updatedAt > lastSyncTime
            }
            
            if (hasLocalChanges) {
                val unsyncedCount = allNotes.count { it.updatedAt > lastSyncTime }
                Logger.d(TAG, "📝 Local changes: $unsyncedCount notes modified")
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
            Logger.e(TAG, "Failed to check for unsynced changes", e)
            true  // Safe default
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
            
            // Socket-Check mit 2s Timeout
            // Gibt dem Netzwerk Zeit für Initialisierung (DHCP, Routing, Gateway)
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 2000)
            socket.close()
            
            Logger.d(TAG, "✅ Server is reachable")
            true
        } catch (e: Exception) {
            Logger.d(TAG, "❌ Server not reachable: ${e.message}")
            false
        }
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
                    is java.net.UnknownHostException -> "Server nicht erreichbar"
                    is java.net.SocketTimeoutException -> "Verbindungs-Timeout"
                    is javax.net.ssl.SSLException -> "SSL-Fehler"
                    is com.thegrizzlylabs.sardineandroid.impl.SardineException -> {
                        when (e.statusCode) {
                            401 -> "Authentifizierung fehlgeschlagen"
                            403 -> "Zugriff verweigert"
                            404 -> "Server-Pfad nicht gefunden"
                            500 -> "Server-Fehler"
                            else -> "HTTP-Fehler: ${e.statusCode}"
                        }
                    }
                    else -> e.message ?: "Unbekannter Fehler"
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
            
            Logger.d(TAG, "📍 Step 4: Uploading local notes")
            // Upload local notes
            try {
                Logger.d(TAG, "⬆️ Uploading local notes...")
                val uploadedCount = uploadLocalNotes(sardine, serverUrl)
                syncedCount += uploadedCount
                Logger.d(TAG, "✅ Uploaded: $uploadedCount notes")
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in uploadLocalNotes()!", e)
                e.printStackTrace()
                throw e
            }
            
            Logger.d(TAG, "📍 Step 5: Downloading remote notes")
            // Download remote notes
            try {
                Logger.d(TAG, "⬇️ Downloading remote notes...")
                val downloadResult = downloadRemoteNotes(
                    sardine, 
                    serverUrl,
                    includeRootFallback = true  // ✅ v1.3.0: Enable for v1.2.0 compatibility
                )
                syncedCount += downloadResult.downloadedCount
                conflictCount += downloadResult.conflictCount
                Logger.d(TAG, "✅ Downloaded: ${downloadResult.downloadedCount} notes, Conflicts: ${downloadResult.conflictCount}")
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
                    Logger.d(TAG, "📥 Auto-importing Markdown files...")
                    markdownImportedCount = importMarkdownFiles(sardine, serverUrl)
                    Logger.d(TAG, "✅ Auto-imported: $markdownImportedCount Markdown files")
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
            Logger.d(TAG, "═══════════════════════════════════════")
            
            SyncResult(
                isSuccess = true,
                syncedCount = effectiveSyncedCount,
                conflictCount = conflictCount
            )
            
        } catch (e: Exception) {
            Logger.e(TAG, "═══════════════════════════════════════")
            Logger.e(TAG, "💥💥💥 FATAL EXCEPTION in syncNotes() 💥💥💥")
            Logger.e(TAG, "Exception type: ${e.javaClass.name}")
            Logger.e(TAG, "Exception message: ${e.message}")
            Logger.e(TAG, "Stack trace:")
            e.printStackTrace()
            Logger.e(TAG, "═══════════════════════════════════════")
            
            SyncResult(
                isSuccess = false,
                errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Server nicht erreichbar: ${e.message}"
                    is java.net.SocketTimeoutException -> "Verbindungs-Timeout: ${e.message}"
                    is javax.net.ssl.SSLException -> "SSL-Fehler"
                    is com.thegrizzlylabs.sardineandroid.impl.SardineException -> {
                        when (e.statusCode) {
                            401 -> "Authentifizierung fehlgeschlagen"
                            403 -> "Zugriff verweigert"
                            404 -> "Server-Pfad nicht gefunden"
                            500 -> "Server-Fehler"
                            else -> "HTTP-Fehler: ${e.statusCode}"
                        }
                    }
                    else -> e.message ?: "Unbekannter Fehler"
                }
            )
        }
        } finally {
            // ⚡ v1.3.1: Session-Caches leeren
            clearSessionCache()
            // 🔒 v1.3.1: Sync-Mutex freigeben
            syncMutex.unlock()
        }
    }
    
    private fun uploadLocalNotes(sardine: Sardine, serverUrl: String): Int {
        var uploadedCount = 0
        val localNotes = storage.loadAllNotes()
        val markdownExportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        
        for (note in localNotes) {
            try {
                // 1. JSON-Upload (Task #1.2.1-13: nutzt getNotesUrl())
                if (note.syncStatus == SyncStatus.LOCAL_ONLY || note.syncStatus == SyncStatus.PENDING) {
                    val notesUrl = getNotesUrl(serverUrl)
                    val noteUrl = "$notesUrl${note.id}.json"
                    val jsonBytes = note.toJson().toByteArray()
                    
                    Logger.d(TAG, "   📤 Uploading: ${note.id}.json (${note.title})")
                    sardine.put(noteUrl, jsonBytes, "application/json")
                    Logger.d(TAG, "      ✅ Upload successful")
                    
                    // Update sync status
                    val updatedNote = note.copy(syncStatus = SyncStatus.SYNCED)
                    storage.saveNote(updatedNote)
                    uploadedCount++
                    
                    // ⚡ v1.3.1: Refresh E-Tag after upload to prevent re-download
                    // Get new E-Tag from server via PROPFIND
                    try {
                        val uploadedResource = sardine.list(noteUrl, 0).firstOrNull()
                        val newETag = uploadedResource?.etag
                        if (newETag != null) {
                            prefs.edit().putString("etag_json_${note.id}", newETag).apply()
                            Logger.d(TAG, "      ⚡ Cached new E-Tag: ${newETag.take(8)}")
                        } else {
                            // Fallback: invalidate if server doesn't provide E-Tag
                            prefs.edit().remove("etag_json_${note.id}").apply()
                            Logger.d(TAG, "      ⚠️ No E-Tag from server, invalidated cache")
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG, "      ⚠️ Failed to refresh E-Tag: ${e.message}")
                        prefs.edit().remove("etag_json_${note.id}").apply()
                    }
                    
                    // 2. Markdown-Export (NEU in v1.2.0)
                    // Läuft NACH erfolgreichem JSON-Upload
                    if (markdownExportEnabled) {
                        try {
                            exportToMarkdown(sardine, serverUrl, note)
                            Logger.d(TAG, "   📝 MD exported: ${note.title}")
                        } catch (e: Exception) {
                            Logger.e(TAG, "MD-Export failed for ${note.id}: ${e.message}")
                            // Kein throw! JSON-Sync darf nicht blockiert werden
                        }
                    }
                }
            } catch (e: Exception) {
                // Mark as pending for retry
                val updatedNote = note.copy(syncStatus = SyncStatus.PENDING)
                storage.saveNote(updatedNote)
            }
        }
        
        return uploadedCount
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
        val filename = sanitizeFilename(note.title) + ".md"
        val noteUrl = "$mdUrl/$filename"
        
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
            .take(200)                                 // Max 200 Zeichen (Reserve für .md)
            .trim('_', ' ')                            // Trim Underscores/Spaces
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
        
        // ⚡ v1.3.1: Use cached WiFi address
        val wifiAddress = getOrCacheWiFiAddress()
        
        val okHttpClient = if (wifiAddress != null) {
            Logger.d(TAG, "✅ Using WiFi-bound socket factory")
            OkHttpClient.Builder()
                .socketFactory(WiFiSocketFactory(wifiAddress))
                .build()
        } else {
            Logger.d(TAG, "⚠️ Using default OkHttpClient (no WiFi binding)")
            OkHttpClient.Builder().build()
        }
        
        val sardine = OkHttpSardine(okHttpClient).apply {
            setCredentials(username, password)
        }
        
        val mdUrl = getMarkdownUrl(serverUrl)
        
        // Ordner sollte bereits existieren (durch #1.2.1-00), aber Sicherheitscheck
        ensureMarkdownDirectoryExists(sardine, serverUrl)
        
        // Hole ALLE lokalen Notizen (inklusive SYNCED)
        val allNotes = storage.loadAllNotes()
        val totalCount = allNotes.size
        var exportedCount = 0
        
        Logger.d(TAG, "📝 Found $totalCount notes to export")
        
        allNotes.forEachIndexed { index, note ->
            try {
                // Progress-Callback
                onProgress(index + 1, totalCount)
                
                // Sanitize Filename
                val filename = sanitizeFilename(note.title) + ".md"
                val noteUrl = "$mdUrl/$filename"
                
                // Konvertiere zu Markdown
                val mdContent = note.toMarkdown().toByteArray()
                
                // Upload (überschreibt falls vorhanden)
                sardine.put(noteUrl, mdContent, "text/markdown")
                
                exportedCount++
                Logger.d(TAG, "   ✅ Exported [${index + 1}/$totalCount]: ${note.title}")
                
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
    }
    
    private data class DownloadResult(
        val downloadedCount: Int,
        val conflictCount: Int
    )
    
    private fun downloadRemoteNotes(
        sardine: Sardine, 
        serverUrl: String,
        includeRootFallback: Boolean = false,  // 🆕 v1.2.2: Only for restore from server
        forceOverwrite: Boolean = false,  // 🆕 v1.3.0: For OVERWRITE_DUPLICATES mode
        deletionTracker: DeletionTracker = storage.loadDeletionTracker()  // 🆕 v1.3.0: Allow passing fresh tracker
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
                
                for (resource in jsonFiles) {
                    
                    val noteId = resource.name.removeSuffix(".json")
                    val noteUrl = notesUrl.trimEnd('/') + "/" + resource.name
                    
                    // ⚡ v1.3.1: HYBRID PERFORMANCE - Timestamp + E-Tag (like Markdown!)
                    val serverETag = resource.etag
                    val cachedETag = prefs.getString("etag_json_$noteId", null)
                    val serverModified = resource.modified?.time ?: 0L
                    
                    // 🐛 DEBUG: Log every file check to diagnose performance
                    val serverETagPreview = serverETag?.take(8) ?: "null"
                    val cachedETagPreview = cachedETag?.take(8) ?: "null"
                    Logger.d(TAG, "   🔍 [$noteId] etag=$serverETagPreview/$cachedETagPreview modified=$serverModified lastSync=$lastSyncTime")
                    
                    // PRIMARY: Timestamp check (works on first sync!)
                    // Same logic as Markdown sync - skip if not modified since last sync
                    if (!forceOverwrite && lastSyncTime > 0 && serverModified <= lastSyncTime) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: Not modified since last sync (timestamp)")
                        processedIds.add(noteId)
                        continue
                    }
                    
                    // SECONDARY: E-Tag check (for performance after first sync)
                    // Catches cases where file was re-uploaded with same content
                    if (!forceOverwrite && serverETag != null && serverETag == cachedETag) {
                        skippedUnchanged++
                        Logger.d(TAG, "   ⏭️ Skipping $noteId: E-Tag match (content unchanged)")
                        processedIds.add(noteId)
                        continue
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
                    
                    // Download and process
                    val jsonContent = sardine.get(noteUrl).bufferedReader().use { it.readText() }
                    val remoteNote = Note.fromJson(jsonContent) ?: continue
                    
                    // NEW: Check if note was deleted locally
                    if (deletionTracker.isDeleted(remoteNote.id)) {
                        val deletedAt = deletionTracker.getDeletionTimestamp(remoteNote.id)
                        
                        // Smart check: Was note re-created on server after deletion?
                        if (deletedAt != null && remoteNote.updatedAt > deletedAt) {
                            Logger.d(TAG, "   📝 Note re-created on server after deletion: ${remoteNote.id}")
                            deletionTracker.removeDeletion(remoteNote.id)
                            trackerModified = true
                            // Continue with download below
                        } else {
                            Logger.d(TAG, "   ⏭️ Skipping deleted note: ${remoteNote.id}")
                            skippedDeleted++
                            processedIds.add(remoteNote.id)
                            continue
                        }
                    }
                    
                    processedIds.add(remoteNote.id)  // 🆕 Mark as processed
                    
                    val localNote = storage.loadNote(remoteNote.id)
                    
                    when {
                        localNote == null -> {
                            // New note from server
                            storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                            downloadedCount++
                            Logger.d(TAG, "   ✅ Downloaded from /notes/: ${remoteNote.id}")
                            
                            // ⚡ Cache E-Tag for next sync
                            if (serverETag != null) {
                                prefs.edit().putString("etag_json_$noteId", serverETag).apply()
                            }
                        }
                        forceOverwrite -> {
                            // OVERWRITE mode: Always replace regardless of timestamps
                            storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                            downloadedCount++
                            Logger.d(TAG, "   ♻️ Overwritten from /notes/: ${remoteNote.id}")
                            
                            // ⚡ Cache E-Tag for next sync
                            if (serverETag != null) {
                                prefs.edit().putString("etag_json_$noteId", serverETag).apply()
                            }
                        }
                        localNote.updatedAt < remoteNote.updatedAt -> {
                            // Remote is newer
                            if (localNote.syncStatus == SyncStatus.PENDING) {
                                // Conflict detected
                                storage.saveNote(localNote.copy(syncStatus = SyncStatus.CONFLICT))
                                conflictCount++
                            } else {
                                // Safe to overwrite
                                storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                                downloadedCount++
                                Logger.d(TAG, "   ✅ Updated from /notes/: ${remoteNote.id}")
                                
                                // ⚡ Cache E-Tag for next sync
                                if (serverETag != null) {
                                    prefs.edit().putString("etag_json_$noteId", serverETag).apply()
                                }
                            }
                        }
                    }
                }
                Logger.d(TAG, "   📊 Phase 1: $downloadedCount downloaded, $skippedDeleted skipped (deleted), $skippedUnchanged skipped (unchanged)")
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
        
        Logger.d(TAG, "📊 Total: $downloadedCount downloaded, $conflictCount conflicts, $skippedDeleted deleted")
        return DownloadResult(downloadedCount, conflictCount)
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
            Logger.d(TAG, "📡 Calling downloadRemoteNotes() - includeRootFallback: true, forceOverwrite: $forceOverwrite")
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
            
            val sardine = OkHttpSardine()
            sardine.setCredentials(username, password)
            
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
                    
                    // Parse to Note
                    val mdNote = Note.fromMarkdown(mdContent)
                    if (mdNote == null) {
                        Logger.w(TAG, "      ⚠️ Failed to parse ${resource.name} - fromMarkdown returned null")
                        continue
                    }
                    Logger.d(TAG, "      Parsed: id=${mdNote.id}, title=${mdNote.title}, updatedAt=${Date(mdNote.updatedAt)}")
                    
                    val localNote = storage.loadNote(mdNote.id)
                    Logger.d(TAG, "      Local note: ${if (localNote == null) "NOT FOUND" else "exists, updatedAt=${Date(localNote.updatedAt)}, syncStatus=${localNote.syncStatus}"}")
                    
                    // ⚡ v1.3.1: Content-basierte Erkennung
                    // Wichtig: Vergleiche IMMER den Inhalt, wenn die Datei seit letztem Sync geändert wurde!
                    // Der YAML-Timestamp kann veraltet sein (z.B. bei externer Bearbeitung ohne Obsidian)
                    Logger.d(TAG, "      Comparison: mdUpdatedAt=${mdNote.updatedAt}, localUpdated=${localNote?.updatedAt ?: 0L}")
                    
                    // Content-Vergleich: Ist der Inhalt tatsächlich unterschiedlich?
                    val contentChanged = localNote != null && (
                        mdNote.content != localNote.content || 
                        mdNote.title != localNote.title
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
                        localNote.syncStatus == SyncStatus.SYNCED && !contentChanged && localNote.updatedAt >= mdNote.updatedAt -> {
                            // Inhalt identisch UND Timestamps passen → Skip
                            skippedCount++
                            Logger.d(TAG, "   ⏭️ Skipped ${mdNote.title}: content identical (local=${localNote.updatedAt}, md=${mdNote.updatedAt})")
                        }
                        // ⚡ v1.3.1 FIX: Content geändert aber YAML-Timestamp nicht aktualisiert → Importieren!
                        contentChanged && localNote.syncStatus == SyncStatus.SYNCED -> {
                            // Inhalt wurde extern geändert ohne YAML-Update → mit aktuellem Timestamp importieren
                            val newTimestamp = System.currentTimeMillis()
                            storage.saveNote(mdNote.copy(
                                updatedAt = newTimestamp,
                                syncStatus = SyncStatus.SYNCED
                            ))
                            importedCount++
                            Logger.d(TAG, "   ✅ Imported changed content (YAML timestamp outdated): ${mdNote.title}")
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
                            Logger.d(TAG, "   ⏭️ Skipped ${mdNote.title}: local is newer or pending (local=${localNote.updatedAt}, md=${mdNote.updatedAt})")
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
     * @param noteId The ID of the note to delete
     * @return true if at least one file was deleted, false otherwise
     */
    suspend fun deleteNoteFromServer(noteId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val sardine = getOrCreateSardine() ?: return@withContext false
            val serverUrl = getServerUrl() ?: return@withContext false
            
            var deletedJson = false
            var deletedMd = false
            
            // Delete JSON
            val jsonUrl = getNotesUrl(serverUrl) + "$noteId.json"
            if (sardine.exists(jsonUrl)) {
                sardine.delete(jsonUrl)
                deletedJson = true
                Logger.d(TAG, "🗑️ Deleted from server: $noteId.json")
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
            val sardine = getOrCreateSardine() ?: throw Exception("Sardine client konnte nicht erstellt werden")
            val serverUrl = getServerUrl() ?: throw Exception("Server-URL nicht konfiguriert")
            
            val username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
            val password = prefs.getString(Constants.KEY_PASSWORD, "") ?: ""
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                throw Exception("WebDAV-Server nicht vollständig konfiguriert")
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
