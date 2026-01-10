package dev.dettmer.simplenotes

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.dettmer.simplenotes.adapters.ChecklistEditorAdapter
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.showToast

/**
 * Editor Activity für Notizen und Checklisten
 * 
 * v1.4.0: Unterstützt jetzt sowohl TEXT als auch CHECKLIST Notizen
 */
class NoteEditorActivity : AppCompatActivity() {
    
    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tilTitle: TextInputLayout
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var tilContent: TextInputLayout
    private lateinit var editTextContent: TextInputEditText
    private lateinit var checklistContainer: LinearLayout
    private lateinit var rvChecklistItems: RecyclerView
    private lateinit var btnAddItem: MaterialButton
    
    private lateinit var storage: NotesStorage
    
    // State
    private var existingNote: Note? = null
    private var currentNoteType: NoteType = NoteType.TEXT
    private val checklistItems = mutableListOf<ChecklistItem>()
    private var checklistAdapter: ChecklistEditorAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    
    companion object {
        private const val TAG = "NoteEditorActivity"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TYPE = "extra_note_type"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)
        
        setContentView(R.layout.activity_editor)
        
        storage = NotesStorage(this)
        
        findViews()
        setupToolbar()
        loadNoteOrDetermineType()
        setupUIForNoteType()
    }
    
    private fun findViews() {
        toolbar = findViewById(R.id.toolbar)
        tilTitle = findViewById(R.id.tilTitle)
        editTextTitle = findViewById(R.id.editTextTitle)
        tilContent = findViewById(R.id.tilContent)
        editTextContent = findViewById(R.id.editTextContent)
        checklistContainer = findViewById(R.id.checklistContainer)
        rvChecklistItems = findViewById(R.id.rvChecklistItems)
        btnAddItem = findViewById(R.id.btnAddItem)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun loadNoteOrDetermineType() {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        
        if (noteId != null) {
            // Existierende Notiz laden
            existingNote = storage.loadNote(noteId)
            existingNote?.let { note ->
                editTextTitle.setText(note.title)
                currentNoteType = note.noteType
                
                when (note.noteType) {
                    NoteType.TEXT -> {
                        editTextContent.setText(note.content)
                        supportActionBar?.title = getString(R.string.edit_note)
                    }
                    NoteType.CHECKLIST -> {
                        note.checklistItems?.let { items ->
                            checklistItems.clear()
                            checklistItems.addAll(items.sortedBy { it.order })
                        }
                        supportActionBar?.title = getString(R.string.edit_checklist)
                    }
                }
            }
        } else {
            // Neue Notiz - Typ aus Intent
            val typeString = intent.getStringExtra(EXTRA_NOTE_TYPE) ?: NoteType.TEXT.name
            currentNoteType = try {
                NoteType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Invalid note type '$typeString', defaulting to TEXT: ${e.message}")
                NoteType.TEXT
            }
            
            when (currentNoteType) {
                NoteType.TEXT -> {
                    supportActionBar?.title = getString(R.string.new_note)
                }
                NoteType.CHECKLIST -> {
                    supportActionBar?.title = getString(R.string.new_checklist)
                    // Erstes leeres Item hinzufügen
                    checklistItems.add(ChecklistItem.createEmpty(0))
                }
            }
        }
    }
    
    private fun setupUIForNoteType() {
        when (currentNoteType) {
            NoteType.TEXT -> {
                tilContent.visibility = View.VISIBLE
                checklistContainer.visibility = View.GONE
            }
            NoteType.CHECKLIST -> {
                tilContent.visibility = View.GONE
                checklistContainer.visibility = View.VISIBLE
                setupChecklistRecyclerView()
            }
        }
    }
    
    private fun setupChecklistRecyclerView() {
        checklistAdapter = ChecklistEditorAdapter(
            items = checklistItems,
            onItemCheckedChanged = { position, isChecked ->
                if (position in checklistItems.indices) {
                    checklistItems[position].isChecked = isChecked
                }
            },
            onItemTextChanged = { position, newText ->
                if (position in checklistItems.indices) {
                    checklistItems[position] = checklistItems[position].copy(text = newText)
                }
            },
            onItemDeleted = { position ->
                deleteChecklistItem(position)
            },
            onAddNewItem = { position ->
                addChecklistItemAt(position)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        )
        
        rvChecklistItems.apply {
            layoutManager = LinearLayoutManager(this@NoteEditorActivity)
            adapter = checklistAdapter
        }
        
        // Drag & Drop Setup
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0 // Kein Swipe
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                checklistAdapter?.moveItem(from, to)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Nicht verwendet
            }
            
            override fun isLongPressDragEnabled(): Boolean = false // Nur via Handle
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(rvChecklistItems)
        
        // Add Item Button
        btnAddItem.setOnClickListener {
            addChecklistItemAt(checklistItems.size)
        }
    }
    
    private fun addChecklistItemAt(position: Int) {
        val newItem = ChecklistItem.createEmpty(position)
        checklistAdapter?.insertItem(position, newItem)
        
        // Zum neuen Item scrollen und fokussieren
        rvChecklistItems.scrollToPosition(position)
        checklistAdapter?.focusItem(rvChecklistItems, position)
    }
    
    private fun deleteChecklistItem(position: Int) {
        checklistAdapter?.removeItem(position)
        
        // Wenn letztes Item gelöscht, automatisch neues hinzufügen
        if (checklistItems.isEmpty()) {
            addChecklistItemAt(0)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        // Delete nur für existierende Notizen
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
        
        when (currentNoteType) {
            NoteType.TEXT -> {
                val content = editTextContent.text?.toString()?.trim() ?: ""
                
                if (title.isEmpty() && content.isEmpty()) {
                    showToast(getString(R.string.note_is_empty))
                    return
                }
                
                val note = if (existingNote != null) {
                    existingNote!!.copy(
                        title = title,
                        content = content,
                        noteType = NoteType.TEXT,
                        checklistItems = null,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING
                    )
                } else {
                    Note(
                        title = title,
                        content = content,
                        noteType = NoteType.TEXT,
                        checklistItems = null,
                        deviceId = DeviceIdGenerator.getDeviceId(this),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                }
                
                storage.saveNote(note)
            }
            
            NoteType.CHECKLIST -> {
                // Leere Items filtern
                val validItems = checklistItems.filter { it.text.isNotBlank() }
                
                if (title.isEmpty() && validItems.isEmpty()) {
                    showToast(getString(R.string.note_is_empty))
                    return
                }
                
                // Order neu setzen
                val orderedItems = validItems.mapIndexed { index, item ->
                    item.copy(order = index)
                }
                
                val note = if (existingNote != null) {
                    existingNote!!.copy(
                        title = title,
                        content = "", // Leer für Checklisten
                        noteType = NoteType.CHECKLIST,
                        checklistItems = orderedItems,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING
                    )
                } else {
                    Note(
                        title = title,
                        content = "",
                        noteType = NoteType.CHECKLIST,
                        checklistItems = orderedItems,
                        deviceId = DeviceIdGenerator.getDeviceId(this),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                }
                
                storage.saveNote(note)
            }
        }
        
        showToast(getString(R.string.note_saved))
        finish()
    }
    
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_note_title))
            .setMessage(getString(R.string.delete_note_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteNote()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteNote() {
        existingNote?.let {
            storage.deleteNote(it.id)
            showToast(getString(R.string.note_deleted))
            finish()
        }
    }
}
