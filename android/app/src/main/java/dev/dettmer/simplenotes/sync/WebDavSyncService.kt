package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.net.SocketFactory

class WebDavSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebDavSyncService"
    }
    
    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
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
     * Findet WiFi Interface IP-Adresse (um VPN zu umgehen)
     */
    private fun getWiFiInetAddress(): InetAddress? {
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
    
    private fun getSardine(): Sardine? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        
        Logger.d(TAG, "🔧 Creating OkHttpSardine with WiFi binding")
        Logger.d(TAG, "    Context: ${context.javaClass.simpleName}")
        
        // Versuche WiFi-IP zu finden
        val wifiAddress = getWiFiInetAddress()
        
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
    
    private fun getServerUrl(): String? {
        return prefs.getString(Constants.KEY_SERVER_URL, null)
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
            
            // Wenn noch nie gesynct, dann haben wir Änderungen
            if (lastSyncTime == 0L) {
                Logger.d(TAG, "📝 Never synced - assuming changes exist")
                return@withContext true
            }
            
            // Prüfe ob Notizen existieren die neuer sind als letzter Sync
            val storage = dev.dettmer.simplenotes.storage.NotesStorage(context)
            val allNotes = storage.loadAllNotes()
            
            val hasChanges = allNotes.any { note ->
                note.updatedAt > lastSyncTime
            }
            
            Logger.d(TAG, "📊 Unsynced changes check: $hasChanges (${allNotes.size} notes total)")
            if (hasChanges) {
                val unsyncedCount = allNotes.count { note -> note.updatedAt > lastSyncTime }
                Logger.d(TAG, "   → $unsyncedCount notes modified since last sync")
            }
            
            hasChanges
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check for unsynced changes - assuming changes exist", e)
            // Bei Fehler lieber sync durchführen (safe default)
            true
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
            val sardine = getSardine() ?: return@withContext SyncResult(
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
        Logger.d(TAG, "═══════════════════════════════════════")
        Logger.d(TAG, "🔄 syncNotes() ENTRY")
        Logger.d(TAG, "Context: ${context.javaClass.simpleName}")
        Logger.d(TAG, "Thread: ${Thread.currentThread().name}")
        
        return@withContext try {
            Logger.d(TAG, "📍 Step 1: Getting Sardine client")
            
            val sardine = try {
                getSardine()
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in getSardine()!", e)
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
            // Ensure server directory exists
            try {
                Logger.d(TAG, "🔍 Checking if server directory exists...")
                if (!sardine.exists(serverUrl)) {
                    Logger.d(TAG, "📁 Creating server directory...")
                    sardine.createDirectory(serverUrl)
                }
                Logger.d(TAG, "    ✅ Server directory ready")
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH checking/creating server directory!", e)
                e.printStackTrace()
                throw e
            }
            
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
                val downloadResult = downloadRemoteNotes(sardine, serverUrl)
                syncedCount += downloadResult.downloadedCount
                conflictCount += downloadResult.conflictCount
                Logger.d(TAG, "✅ Downloaded: ${downloadResult.downloadedCount} notes, Conflicts: ${downloadResult.conflictCount}")
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH in downloadRemoteNotes()!", e)
                e.printStackTrace()
                throw e
            }
            
            Logger.d(TAG, "📍 Step 6: Saving sync timestamp")
            // Update last sync timestamp
            try {
                saveLastSyncTimestamp()
                Logger.d(TAG, "    ✅ Timestamp saved")
            } catch (e: Exception) {
                Logger.e(TAG, "💥 CRASH saving timestamp!", e)
                e.printStackTrace()
                // Non-fatal, continue
            }
            
            Logger.d(TAG, "🎉 Sync completed successfully - Total synced: $syncedCount")
            Logger.d(TAG, "═══════════════════════════════════════")
            
            SyncResult(
                isSuccess = true,
                syncedCount = syncedCount,
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
    }
    
    private fun uploadLocalNotes(sardine: Sardine, serverUrl: String): Int {
        var uploadedCount = 0
        val localNotes = storage.loadAllNotes()
        val markdownExportEnabled = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        
        for (note in localNotes) {
            try {
                // 1. JSON-Upload (bestehend, unverändert)
                if (note.syncStatus == SyncStatus.LOCAL_ONLY || note.syncStatus == SyncStatus.PENDING) {
                    val noteUrl = "$serverUrl/${note.id}.json"
                    val jsonBytes = note.toJson().toByteArray()
                    
                    sardine.put(noteUrl, jsonBytes, "application/json")
                    
                    // Update sync status
                    val updatedNote = note.copy(syncStatus = SyncStatus.SYNCED)
                    storage.saveNote(updatedNote)
                    uploadedCount++
                    
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
        val mdUrl = serverUrl.replace("/notes", "/notes-md")
        
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
    
    private data class DownloadResult(
        val downloadedCount: Int,
        val conflictCount: Int
    )
    
    private fun downloadRemoteNotes(sardine: Sardine, serverUrl: String): DownloadResult {
        var downloadedCount = 0
        var conflictCount = 0
        
        try {
            val resources = sardine.list(serverUrl)
            
            for (resource in resources) {
                if (resource.isDirectory || !resource.name.endsWith(".json")) {
                    continue
                }
                
                val noteUrl = resource.href.toString()
                val jsonContent = sardine.get(noteUrl).bufferedReader().use { it.readText() }
                val remoteNote = Note.fromJson(jsonContent) ?: continue
                
                val localNote = storage.loadNote(remoteNote.id)
                
                when {
                    localNote == null -> {
                        // New note from server
                        storage.saveNote(remoteNote.copy(syncStatus = SyncStatus.SYNCED))
                        downloadedCount++
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
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail entire sync
        }
        
        return DownloadResult(downloadedCount, conflictCount)
    }
    
    private fun saveLastSyncTimestamp() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(Constants.KEY_LAST_SYNC, now)
            .putLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, now)  // 🔥 v1.1.2: Track successful sync
            .apply()
    }
    
    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(Constants.KEY_LAST_SYNC, 0)
    }
    
    fun getLastSuccessfulSyncTimestamp(): Long {
        return prefs.getLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, 0)
    }
    
    /**
     * Restore all notes from server - overwrites local storage
     * @return RestoreResult with count of restored notes
     */
    suspend fun restoreFromServer(): RestoreResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val sardine = getSardine() ?: return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Server-Zugangsdaten nicht konfiguriert",
                restoredCount = 0
            )
            
            val serverUrl = getServerUrl() ?: return@withContext RestoreResult(
                isSuccess = false,
                errorMessage = "Server-URL nicht konfiguriert",
                restoredCount = 0
            )
            
            Logger.d(TAG, "🔄 Starting restore from server...")
            
            // List all files on server
            val resources = sardine.list(serverUrl)
            val jsonFiles = resources.filter { 
                !it.isDirectory && it.name.endsWith(".json")
            }
            
            Logger.d(TAG, "📂 Found ${jsonFiles.size} files on server")
            
            val restoredNotes = mutableListOf<Note>()
            
            // Download and parse each file
            for (resource in jsonFiles) {
                try {
                    val fileUrl = serverUrl.trimEnd('/') + "/" + resource.name
                    val content = sardine.get(fileUrl).bufferedReader().use { it.readText() }
                    
                    val note = Note.fromJson(content)
                    if (note != null) {
                        restoredNotes.add(note)
                        Logger.d(TAG, "✅ Downloaded: ${note.title}")
                    } else {
                        Logger.e(TAG, "❌ Failed to parse ${resource.name}: Note.fromJson returned null")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "❌ Failed to download ${resource.name}", e)
                    // Continue with other files
                }
            }
            
            if (restoredNotes.isEmpty()) {
                return@withContext RestoreResult(
                    isSuccess = false,
                    errorMessage = "Keine Notizen auf Server gefunden",
                    restoredCount = 0
                )
            }
            
            // Clear local storage
            Logger.d(TAG, "🗑️ Clearing local storage...")
            storage.deleteAllNotes()
            
            // Save all restored notes
            Logger.d(TAG, "💾 Saving ${restoredNotes.size} notes...")
            restoredNotes.forEach { note ->
                storage.saveNote(note.copy(syncStatus = SyncStatus.SYNCED))
            }
            
            Logger.d(TAG, "✅ Restore completed: ${restoredNotes.size} notes")
            
            RestoreResult(
                isSuccess = true,
                errorMessage = null,
                restoredCount = restoredNotes.size
            )
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Restore failed", e)
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
            
            val mdUrl = serverUrl.replace("/notes", "/notes-md")
            
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
}

data class RestoreResult(
    val isSuccess: Boolean,
    val errorMessage: String?,
    val restoredCount: Int
)
