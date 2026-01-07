package dev.dettmer.simplenotes.storage

import android.content.Context
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import java.io.File

class NotesStorage(private val context: Context) {
    
    companion object {
        private const val TAG = "NotesStorage"
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
    
    fun loadAllNotes(): List<Note> {
        return notesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { Note.fromJson(it.readText()) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
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

    
    fun getNotesDir(): File = notesDir
}
