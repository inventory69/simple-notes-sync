package dev.dettmer.simplenotes

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.showToast

class NoteEditorActivity : AppCompatActivity() {
    
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextContent: TextInputEditText
    private lateinit var storage: NotesStorage
    
    private var existingNote: Note? = null
    
    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        
        storage = NotesStorage(this)
        
        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(android.R.drawable.ic_menu_close_clear_cancel)
        }
        
        // Find views
        editTextTitle = findViewById(R.id.editTextTitle)
        editTextContent = findViewById(R.id.editTextContent)
        
        // Load existing note if editing
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        if (noteId != null) {
            existingNote = storage.loadNote(noteId)
            existingNote?.let {
                editTextTitle.setText(it.title)
                editTextContent.setText(it.content)
                supportActionBar?.title = "Notiz bearbeiten"
            }
        } else {
            supportActionBar?.title = "Neue Notiz"
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        
        // Show delete only for existing notes
        menu.findItem(R.id.action_delete)?.isVisible = existingNote != null
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveNote()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveNote() {
        val title = editTextTitle.text?.toString()?.trim() ?: ""
        val content = editTextContent.text?.toString()?.trim() ?: ""
        
        if (title.isEmpty() && content.isEmpty()) {
            showToast("Titel oder Inhalt darf nicht leer sein")
            return
        }
        
        val note = if (existingNote != null) {
            // Update existing note
            existingNote!!.copy(
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
        } else {
            // Create new note
            Note(
                title = title,
                content = content,
                deviceId = DeviceIdGenerator.getDeviceId(this),
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        }
        
        storage.saveNote(note)
        showToast("Notiz gespeichert")
        finish()
    }
    
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Notiz löschen?")
            .setMessage("Diese Aktion kann nicht rückgängig gemacht werden.")
            .setPositiveButton("Löschen") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
    
    private fun deleteNote() {
        existingNote?.let {
            storage.deleteNote(it.id)
            showToast("Notiz gelöscht")
            finish()
        }
    }
}
