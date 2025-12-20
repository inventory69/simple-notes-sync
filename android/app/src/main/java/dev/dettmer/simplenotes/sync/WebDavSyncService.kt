package dev.dettmer.simplenotes.sync

import android.content.Context
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavSyncService(private val context: Context) {
    
    private val storage = NotesStorage(context)
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    private fun getSardine(): Sardine? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        
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
        return@withContext try {
            val sardine = getSardine() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-Zugangsdaten nicht konfiguriert"
            )
            
            val serverUrl = getServerUrl() ?: return@withContext SyncResult(
                isSuccess = false,
                errorMessage = "Server-URL nicht konfiguriert"
            )
            
            var syncedCount = 0
            var conflictCount = 0
            
            // Ensure server directory exists
            if (!sardine.exists(serverUrl)) {
                sardine.createDirectory(serverUrl)
            }
            
            // Upload local notes
            val uploadedCount = uploadLocalNotes(sardine, serverUrl)
            syncedCount += uploadedCount
            
            // Download remote notes
            val downloadResult = downloadRemoteNotes(sardine, serverUrl)
            syncedCount += downloadResult.downloadedCount
            conflictCount += downloadResult.conflictCount
            
            // Update last sync timestamp
            saveLastSyncTimestamp()
            
            SyncResult(
                isSuccess = true,
                syncedCount = syncedCount,
                conflictCount = conflictCount
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
}
