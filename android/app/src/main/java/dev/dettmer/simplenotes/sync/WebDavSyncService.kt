package dev.dettmer.simplenotes.sync

import android.content.Context
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "WebDavSyncService"
    }
    
    private val storage = NotesStorage(context)
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    private fun getSardine(): Sardine? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        
        // Einfach standard OkHttpSardine - funktioniert im manuellen Sync!
        android.util.Log.d(TAG, "🔧 Creating OkHttpSardine")
        
        return OkHttpSardine().apply {
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
        android.util.Log.d(TAG, "🔄 syncNotes() called")
        android.util.Log.d(TAG, "Context: ${context.javaClass.simpleName}")
        
        return@withContext try {
            val sardine = getSardine()
            if (sardine == null) {
                android.util.Log.e(TAG, "❌ Sardine is null - credentials missing")
                return@withContext SyncResult(
                    isSuccess = false,
                    errorMessage = "Server-Zugangsdaten nicht konfiguriert"
                )
            }
            
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
            
            // Ensure server directory exists
            android.util.Log.d(TAG, "🔍 Checking if server directory exists...")
            if (!sardine.exists(serverUrl)) {
                android.util.Log.d(TAG, "📁 Creating server directory...")
                sardine.createDirectory(serverUrl)
            }
            
            // Upload local notes
            android.util.Log.d(TAG, "⬆️ Uploading local notes...")
            val uploadedCount = uploadLocalNotes(sardine, serverUrl)
            syncedCount += uploadedCount
            android.util.Log.d(TAG, "✅ Uploaded: $uploadedCount notes")
            
            // Download remote notes
            android.util.Log.d(TAG, "⬇️ Downloading remote notes...")
            val downloadResult = downloadRemoteNotes(sardine, serverUrl)
            syncedCount += downloadResult.downloadedCount
            conflictCount += downloadResult.conflictCount
            android.util.Log.d(TAG, "✅ Downloaded: ${downloadResult.downloadedCount} notes, Conflicts: ${downloadResult.conflictCount}")
            
            // Update last sync timestamp
            saveLastSyncTimestamp()
            
            android.util.Log.d(TAG, "🎉 Sync completed successfully - Total synced: $syncedCount")
            
            SyncResult(
                isSuccess = true,
                syncedCount = syncedCount,
                conflictCount = conflictCount
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Sync exception: ${e.message}", e)
            android.util.Log.e(TAG, "Exception type: ${e.javaClass.name}")
            
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
