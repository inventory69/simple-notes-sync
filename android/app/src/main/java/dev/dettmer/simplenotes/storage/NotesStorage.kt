package dev.dettmer.simplenotes.storage

import android.content.Context
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class NotesStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "NotesStorage"
        // 🔒 v1.7.2 (IMPL_001): Mutex für thread-sichere Deletion Tracker Operationen
        private val deletionTrackerMutex = Mutex()
    }
    
    private val notesDir: File = File(context.filesDir, "notes").apply {
        if (!exists()) mkdirs()
    }
    
    fun saveNote(note: Note) {
        val file = File(notesDir, "${note.id}.json")
        file.writeText(note.toJson())
    }
    
    fun loadNote(id: String): Note? {
        val file = File(notesDir, "$id.json")
        return if (file.exists()) {
            Note.fromJson(file.readText())
        } else {
            null
        }
    }
    
    /**
     * Lädt alle Notizen aus dem lokalen Speicher.
     * 
     * 🔀 v1.8.0: Sortierung entfernt — wird jetzt im ViewModel durchgeführt,
     * damit der User die Sortierung konfigurieren kann.
     */
    fun loadAllNotes(): List<Note> {
        return notesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { Note.fromJson(it.readText()) }
            .orEmpty()
    }
    
    fun deleteNote(id: String): Boolean {
        val file = File(notesDir, "$id.json")
        val deleted = file.delete()
        
        if (deleted) {
            Logger.d(TAG, "🗑️ Deleted note: $id")
            
            // Track deletion to prevent zombie notes
            val deviceId = DeviceIdGenerator.getDeviceId(context)
            trackDeletion(id, deviceId)
        }
        
        return deleted
    }
    
    fun deleteAllNotes(): Boolean {
        return try {
            val notes = loadAllNotes()
            val deviceId = DeviceIdGenerator.getDeviceId(context)
            
            for (note in notes) {
                deleteNote(note.id)  // Uses trackDeletion() automatically
            }
            
            Logger.d(TAG, "🗑️ Deleted all notes (${notes.size} notes)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete all notes", e)
            false
        }
    }
    
    // === Deletion Tracking ===
    
    private fun getDeletionTrackerFile(): File {
        return File(context.filesDir, "deleted_notes.json")
    }
    
    fun loadDeletionTracker(): DeletionTracker {
        val file = getDeletionTrackerFile()
        if (!file.exists()) {
            return DeletionTracker()
        }
        
        return try {
            val json = file.readText()
            DeletionTracker.fromJson(json) ?: DeletionTracker()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load deletion tracker", e)
            DeletionTracker()
        }
    }
    
    fun saveDeletionTracker(tracker: DeletionTracker) {
        try {
            val file = getDeletionTrackerFile()
            file.writeText(tracker.toJson())
            
            if (tracker.deletedNotes.size > 1000) {
                Logger.w(TAG, "⚠️ Deletion tracker large: ${tracker.deletedNotes.size} entries")
            }
            
            Logger.d(TAG, "✅ Deletion tracker saved (${tracker.deletedNotes.size} entries)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save deletion tracker", e)
        }
    }
    
    /**
     * 🔒 v1.7.2 (IMPL_001): Thread-sichere Deletion-Tracking mit Mutex
     * 
     * Verhindert Race Conditions bei Batch-Deletes durch exklusiven Zugriff
     * auf den Deletion Tracker.
     * 
     * @param noteId ID der gelöschten Notiz
     * @param deviceId Geräte-ID für Konflikt-Erkennung
     */
    suspend fun trackDeletionSafe(noteId: String, deviceId: String) {
        deletionTrackerMutex.withLock {
            val tracker = loadDeletionTracker()
            tracker.addDeletion(noteId, deviceId)
            saveDeletionTracker(tracker)
            Logger.d(TAG, "📝 Tracked deletion (mutex-protected): $noteId")
        }
    }
    
    /**
     * Legacy-Methode ohne Mutex-Schutz.
     * Verwendet für synchrone Aufrufe wo Coroutines nicht verfügbar sind.
     * 
     * @deprecated Verwende trackDeletionSafe() für Thread-Safety wo möglich
     */
    fun trackDeletion(noteId: String, deviceId: String) {
        val tracker = loadDeletionTracker()
        tracker.addDeletion(noteId, deviceId)
        saveDeletionTracker(tracker)
        Logger.d(TAG, "📝 Tracked deletion: $noteId")
    }
    
    fun isNoteDeleted(noteId: String): Boolean {
        val tracker = loadDeletionTracker()
        return tracker.isDeleted(noteId)
    }
    
    fun clearDeletionTracker() {
        saveDeletionTracker(DeletionTracker())
        Logger.d(TAG, "🗑️ Deletion tracker cleared")
    }
    
    /**
     * 🔄 v1.7.0: Reset all sync statuses to PENDING when server changes
     * This ensures notes are uploaded to the new server on next sync
     */
    fun resetAllSyncStatusToPending(): Int {
        val notes = loadAllNotes()
        var updatedCount = 0
        
        notes.forEach { note ->
            // 🔧 v1.9.0: Auch DELETED_ON_SERVER → PENDING zurücksetzen
            // Notizen die auf dem alten Server gelöscht wurden, müssen auf den neuen Server
            // hochgeladen werden — der neue Server hat keine Kenntnis der alten Löschung.
            if (note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.SYNCED ||
                note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.DELETED_ON_SERVER) {
                val updatedNote = note.copy(syncStatus = dev.dettmer.simplenotes.models.SyncStatus.PENDING)
                saveNote(updatedNote)
                updatedCount++
            }
        }
        
        Logger.d(TAG, "🔄 Reset sync status for $updatedCount notes to PENDING")
        return updatedCount
    }

    
    fun getNotesDir(): File = notesDir
}
