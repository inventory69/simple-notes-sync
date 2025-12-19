package dev.dettmer.simplenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.dettmer.simplenotes.adapters.NotesAdapter
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.utils.showToast
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewNotes: RecyclerView
    private lateinit var textViewEmpty: TextView
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar
    
    private lateinit var adapter: NotesAdapter
    private val storage by lazy { NotesStorage(this) }
    
    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Notification Channel erstellen
        NotificationHelper.createNotificationChannel(this)
        
        // Permission für Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        
        findViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        
        loadNotes()
    }
    
    override fun onResume() {
        super.onResume()
        loadNotes()
    }
    
    private fun findViews() {
        recyclerViewNotes = findViewById(R.id.recyclerViewNotes)
        textViewEmpty = findViewById(R.id.textViewEmpty)
        fabAddNote = findViewById(R.id.fabAddNote)
        toolbar = findViewById(R.id.toolbar)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
    }
    
    private fun setupRecyclerView() {
        adapter = NotesAdapter { note ->
            openNoteEditor(note.id)
        }
        recyclerViewNotes.adapter = adapter
        recyclerViewNotes.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupFab() {
        fabAddNote.setOnClickListener {
            openNoteEditor(null)
        }
    }
    
    private fun loadNotes() {
        val notes = storage.loadAllNotes()
        adapter.submitList(notes)
        
        // Empty state
        textViewEmpty.visibility = if (notes.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun openNoteEditor(noteId: String?) {
        val intent = Intent(this, NoteEditorActivity::class.java)
        noteId?.let {
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, it)
        }
        startActivity(intent)
    }
    
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            R.id.action_sync -> {
                // Manual sync trigger could be added here
                showToast("Sync wird in den Einstellungen gestartet")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showToast("Benachrichtigungen aktiviert")
                } else {
                    showToast("Benachrichtigungen deaktiviert. " +
                        "Du kannst sie in den Einstellungen aktivieren.")
                }
            }
        }
    }
}