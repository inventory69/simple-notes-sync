package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
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
import javax.net.SocketFactory

class WebDavSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebDavSyncService"
    }
    
    private val storage: NotesStorage
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "🏗️ WebDavSyncService INIT")
        android.util.Log.d(TAG, "Context: ${context.javaClass.simpleName}")
        android.util.Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        
        try {
            android.util.Log.d(TAG, "    Creating NotesStorage...")
            storage = NotesStorage(context)
            android.util.Log.d(TAG, "    ✅ NotesStorage created successfully")
            android.util.Log.d(TAG, "    Notes dir: ${storage.getNotesDir()}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 CRASH in NotesStorage creation!", e)
            android.util.Log.e(TAG, "Exception: ${e.javaClass.name}: ${e.message}")
            e.printStackTrace()
            throw e
        }
        
        android.util.Log.d(TAG, "    SharedPreferences: $prefs")
        android.util.Log.d(TAG, "✅ WebDavSyncService INIT complete")
        android.util.Log.d(TAG, "═══════════════════════════════════════")
    }
    
    /**
     * Findet WiFi Interface IP-Adresse (um VPN zu umgehen)
     */
    private fun getWiFiInetAddress(): InetAddress? {
        try {
            android.util.Log.d(TAG, "🔍 getWiFiInetAddress() called")
            
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            android.util.Log.d(TAG, "    Active network: $network")
            
            if (network == null) {
                android.util.Log.d(TAG, "❌ No active network")
                return null
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            android.util.Log.d(TAG, "    Network capabilities: $capabilities")
            
            if (capabilities == null) {
                android.util.Log.d(TAG, "❌ No network capabilities")
                return null
            }
            
            // Nur wenn WiFi aktiv
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                android.util.Log.d(TAG, "⚠️ Not on WiFi, using default routing")
                return null
            }
            
            android.util.Log.d(TAG, "✅ Network is WiFi, searching for interface...")
            
            // Finde WiFi Interface
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                
                android.util.Log.d(TAG, "    Checking interface: ${iface.name}, isUp=${iface.isUp}")
                
                // WiFi Interfaces: wlan0, wlan1, etc.
                if (!iface.name.startsWith("wlan")) continue
                if (!iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    
                    android.util.Log.d(TAG, "        Address: ${addr.hostAddress}, IPv4=${addr is Inet4Address}, loopback=${addr.isLoopbackAddress}, linkLocal=${addr.isLinkLocalAddress}")
                    
                    // Nur IPv4, nicht loopback, nicht link-local
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        android.util.Log.d(TAG, "✅ Found WiFi IP: ${addr.hostAddress} on ${iface.name}")
                        return addr
                    }
                }
            }
            
            android.util.Log.w(TAG, "⚠️ No WiFi interface found, using default routing")
            return null
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Failed to get WiFi interface", e)
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
            android.util.Log.d(TAG, "🔌 Socket bound to WiFi IP: ${wifiAddress.hostAddress}")
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
        
        android.util.Log.d(TAG, "🔧 Creating OkHttpSardine with WiFi binding")
        android.util.Log.d(TAG, "    Context: ${context.javaClass.simpleName}")
        
        // Versuche WiFi-IP zu finden
        val wifiAddress = getWiFiInetAddress()
        
        val okHttpClient = if (wifiAddress != null) {
            android.util.Log.d(TAG, "✅ Using WiFi-bound socket factory")
            OkHttpClient.Builder()
                .socketFactory(WiFiSocketFactory(wifiAddress))
                .build()
        } else {
            android.util.Log.d(TAG, "⚠️ Using default OkHttpClient (no WiFi binding)")
            OkHttpClient.Builder().build()
        }
        
        return OkHttpSardine(okHttpClient).apply {
            setCredentials(username, password)
        }
    }
    
    private fun getServerUrl(): String? {
        return prefs.getString(Constants.KEY_SERVER_URL, null)
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
        android.util.Log.d(TAG, "═══════════════════════════════════════")
        android.util.Log.d(TAG, "🔄 syncNotes() ENTRY")
        android.util.Log.d(TAG, "Context: ${context.javaClass.simpleName}")
        android.util.Log.d(TAG, "Thread: ${Thread.currentThread().name}")
        
        return@withContext try {
            android.util.Log.d(TAG, "📍 Step 1: Getting Sardine client")
            
            val sardine = try {
                getSardine()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH in getSardine()!", e)
                e.printStackTrace()
                throw e
            }
            
            if (sardine == null) {
                android.util.Log.e(TAG, "❌ Sardine is null - credentials missing")
                return@withContext SyncResult(
                    isSuccess = false,
                    errorMessage = "Server-Zugangsdaten nicht konfiguriert"
                )
            }
            android.util.Log.d(TAG, "    ✅ Sardine client created")
            
            android.util.Log.d(TAG, "📍 Step 2: Getting server URL")
            val serverUrl = getServerUrl()
            if (serverUrl == null) {
                android.util.Log.e(TAG, "❌ Server URL is null")
                return@withContext SyncResult(
                    isSuccess = false,
                    errorMessage = "Server-URL nicht konfiguriert"
                )
            }
            
            android.util.Log.d(TAG, "📡 Server URL: $serverUrl")
            android.util.Log.d(TAG, "🔐 Credentials configured: ${prefs.getString(Constants.KEY_USERNAME, null) != null}")
            
            var syncedCount = 0
            var conflictCount = 0
            
            android.util.Log.d(TAG, "📍 Step 3: Checking server directory")
            // Ensure server directory exists
            try {
                android.util.Log.d(TAG, "🔍 Checking if server directory exists...")
                if (!sardine.exists(serverUrl)) {
                    android.util.Log.d(TAG, "📁 Creating server directory...")
                    sardine.createDirectory(serverUrl)
                }
                android.util.Log.d(TAG, "    ✅ Server directory ready")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH checking/creating server directory!", e)
                e.printStackTrace()
                throw e
            }
            
            android.util.Log.d(TAG, "📍 Step 4: Uploading local notes")
            // Upload local notes
            try {
                android.util.Log.d(TAG, "⬆️ Uploading local notes...")
                val uploadedCount = uploadLocalNotes(sardine, serverUrl)
                syncedCount += uploadedCount
                android.util.Log.d(TAG, "✅ Uploaded: $uploadedCount notes")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH in uploadLocalNotes()!", e)
                e.printStackTrace()
                throw e
            }
            
            android.util.Log.d(TAG, "📍 Step 5: Downloading remote notes")
            // Download remote notes
            try {
                android.util.Log.d(TAG, "⬇️ Downloading remote notes...")
                val downloadResult = downloadRemoteNotes(sardine, serverUrl)
                syncedCount += downloadResult.downloadedCount
                conflictCount += downloadResult.conflictCount
                android.util.Log.d(TAG, "✅ Downloaded: ${downloadResult.downloadedCount} notes, Conflicts: ${downloadResult.conflictCount}")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH in downloadRemoteNotes()!", e)
                e.printStackTrace()
                throw e
            }
            
            android.util.Log.d(TAG, "📍 Step 6: Saving sync timestamp")
            // Update last sync timestamp
            try {
                saveLastSyncTimestamp()
                android.util.Log.d(TAG, "    ✅ Timestamp saved")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "💥 CRASH saving timestamp!", e)
                e.printStackTrace()
                // Non-fatal, continue
            }
            
            android.util.Log.d(TAG, "🎉 Sync completed successfully - Total synced: $syncedCount")
            android.util.Log.d(TAG, "═══════════════════════════════════════")
            
            SyncResult(
                isSuccess = true,
                syncedCount = syncedCount,
                conflictCount = conflictCount
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "═══════════════════════════════════════")
            android.util.Log.e(TAG, "💥💥💥 FATAL EXCEPTION in syncNotes() 💥💥💥")
            android.util.Log.e(TAG, "Exception type: ${e.javaClass.name}")
            android.util.Log.e(TAG, "Exception message: ${e.message}")
            android.util.Log.e(TAG, "Stack trace:")
            e.printStackTrace()
            android.util.Log.e(TAG, "═══════════════════════════════════════")
            
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
        
        for (note in localNotes) {
            try {
                if (note.syncStatus == SyncStatus.LOCAL_ONLY || note.syncStatus == SyncStatus.PENDING) {
                    val noteUrl = "$serverUrl/${note.id}.json"
                    val jsonBytes = note.toJson().toByteArray()
                    
                    sardine.put(noteUrl, jsonBytes, "application/json")
                    
                    // Update sync status
                    val updatedNote = note.copy(syncStatus = SyncStatus.SYNCED)
                    storage.saveNote(updatedNote)
                    uploadedCount++
                }
            } catch (e: Exception) {
                // Mark as pending for retry
                val updatedNote = note.copy(syncStatus = SyncStatus.PENDING)
                storage.saveNote(updatedNote)
            }
        }
        
        return uploadedCount
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
        prefs.edit()
            .putLong(Constants.KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }
    
    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(Constants.KEY_LAST_SYNC, 0)
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
}

data class RestoreResult(
    val isSuccess: Boolean,
    val errorMessage: String?,
    val restoredCount: Int
)
