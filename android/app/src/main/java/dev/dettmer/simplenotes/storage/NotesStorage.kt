package dev.dettmer.simplenotes.storage

import android.content.Context
import dev.dettmer.simplenotes.models.Note
import java.io.File

class NotesStorage(private val context: Context) {
    
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
        return file.delete()
    }
    
    fun deleteAllNotes(): Boolean {
        return try {
            notesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getNotesDir(): File = notesDir
}
