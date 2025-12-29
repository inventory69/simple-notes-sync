package dev.dettmer.simplenotes

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import dev.dettmer.simplenotes.utils.Logger
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.card.MaterialCardView
import dev.dettmer.simplenotes.adapters.NotesAdapter
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.utils.showToast
import dev.dettmer.simplenotes.utils.Constants
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.dettmer.simplenotes.sync.WebDavSyncService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewNotes: RecyclerView
    private lateinit var emptyStateCard: MaterialCardView
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    private lateinit var adapter: NotesAdapter
    private val storage by lazy { NotesStorage(this) }
    
    // Track pending deletions to prevent flicker when notes reload
    private val pendingDeletions = mutableSetOf<String>()
    
    private val prefs by lazy {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        private const val REQUEST_SETTINGS = 1002
        private const val MIN_AUTO_SYNC_INTERVAL_MS = 60_000L // 1 Minute
        private const val PREF_LAST_AUTO_SYNC_TIME = "last_auto_sync_timestamp"
    }
    
    /**
     * BroadcastReceiver für Background-Sync Completion (Periodic Sync)
     */
    private val syncCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra("success", false) ?: false
            val count = intent?.getIntExtra("count", 0) ?: 0
            
            Logger.d(TAG, "📡 Sync completed broadcast received: success=$success, count=$count")
            
            // UI refresh
            if (success && count > 0) {
                loadNotes()
                Logger.d(TAG, "🔄 Notes reloaded after background sync")
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Splash Screen (Android 12+)
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)
        
        setContentView(R.layout.activity_main)
        
        // File Logging aktivieren wenn eingestellt
        if (prefs.getBoolean("file_logging_enabled", false)) {
            Logger.enableFileLogging(this)
        }
        
        // Alte Sync-Notifications beim App-Start löschen
        NotificationHelper.clearSyncNotifications(this)
        
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
        
        Logger.d(TAG, "📱 MainActivity.onResume() - Registering receivers")
        
        // Register BroadcastReceiver für Background-Sync
        LocalBroadcastManager.getInstance(this).registerReceiver(
            syncCompletedReceiver,
            IntentFilter(SyncWorker.ACTION_SYNC_COMPLETED)
        )
        
        Logger.d(TAG, "📡 BroadcastReceiver registered (sync-completed)")
        
        // Reload notes (scroll to top wird in loadNotes() gemacht)
        loadNotes()
        
        // Trigger Auto-Sync beim App-Wechsel in Vordergrund (Toast)
        triggerAutoSync("onResume")
    }
    
    /**
     * Automatischer Sync (onResume)
     * - Nutzt WiFi-gebundenen Socket (VPN Fix!)
     * - Nur Success-Toast (kein "Auto-Sync..." Toast)
     * 
     * NOTE: WiFi-Connect Sync nutzt WorkManager (auch wenn App geschlossen!)
     */
    private fun triggerAutoSync(source: String = "unknown") {
        // Throttling: Max 1 Sync pro Minute
        if (!canTriggerAutoSync()) {
            return
        }
        
        Logger.d(TAG, "🔄 Auto-sync triggered ($source)")
        
        // Update last sync timestamp
        prefs.edit().putLong(PREF_LAST_AUTO_SYNC_TIME, System.currentTimeMillis()).apply()
        
        lifecycleScope.launch {
            try {
                val syncService = WebDavSyncService(this@MainActivity)
                
                // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): No unsynced changes - skipping")
                    return@launch
                }
                
                // ⭐ WICHTIG: Server-Erreichbarkeits-Check VOR Sync (wie in SyncWorker)
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): Server not reachable - skipping silently")
                    return@launch
                }
                
                // Server ist erreichbar → Sync durchführen
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                // Feedback abhängig von Source
                if (result.isSuccess && result.syncedCount > 0) {
                    Logger.d(TAG, "✅ Auto-sync successful ($source): ${result.syncedCount} notes")
                    
                    // onResume: Nur Success-Toast
                    showToast("✅ Gesynct: ${result.syncedCount} Notizen")
                    loadNotes()
                    
                } else if (result.isSuccess) {
                    Logger.d(TAG, "ℹ️ Auto-sync ($source): No changes")
                    
                } else {
                    Logger.e(TAG, "❌ Auto-sync failed ($source): ${result.errorMessage}")
                    // Kein Toast - App ist im Hintergrund
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "💥 Auto-sync exception ($source): ${e.message}")
                // Kein Toast - App ist im Hintergrund
            }
        }
    }
    
    /**
     * Prüft ob Auto-Sync getriggert werden darf (Throttling)
     */
    private fun canTriggerAutoSync(): Boolean {
        val lastSyncTime = prefs.getLong(PREF_LAST_AUTO_SYNC_TIME, 0)
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastSyncTime
        
        if (timeSinceLastSync < MIN_AUTO_SYNC_INTERVAL_MS) {
            val remainingSeconds = (MIN_AUTO_SYNC_INTERVAL_MS - timeSinceLastSync) / 1000
            Logger.d(TAG, "⏳ Auto-sync throttled - wait ${remainingSeconds}s")
            return false
        }
        
        return true
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister BroadcastReceiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncCompletedReceiver)
        Logger.d(TAG, "📡 BroadcastReceiver unregistered")
    }
    
    private fun findViews() {
        recyclerViewNotes = findViewById(R.id.recyclerViewNotes)
        emptyStateCard = findViewById(R.id.emptyStateCard)
        fabAddNote = findViewById(R.id.fabAddNote)
        toolbar = findViewById(R.id.toolbar)
        swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
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
        
        // 🔥 v1.1.2: Setup Pull-to-Refresh
        setupPullToRefresh()
        
        // Setup Swipe-to-Delete
        setupSwipeToDelete()
    }
    
    /**
     * Setup Pull-to-Refresh für manuellen Sync (v1.1.2)
     */
    private fun setupPullToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Logger.d(TAG, "🔄 Pull-to-Refresh triggered - starting manual sync")
            
            lifecycleScope.launch {
                try {
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
                    
                    if (serverUrl.isNullOrEmpty()) {
                        showToast("⚠️ Server noch nicht konfiguriert")
                        swipeRefreshLayout.isRefreshing = false
                        return@launch
                    }
                    
                    val syncService = WebDavSyncService(this@MainActivity)
                    
                    // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                    if (!syncService.hasUnsyncedChanges()) {
                        Logger.d(TAG, "⏭️ No unsynced changes, skipping server reachability check")
                        showToast("✅ Bereits synchronisiert")
                        swipeRefreshLayout.isRefreshing = false
                        return@launch
                    }
                    
                    // Check if server is reachable
                    if (!syncService.isServerReachable()) {
                        showToast("⚠️ Server nicht erreichbar")
                        swipeRefreshLayout.isRefreshing = false
                        return@launch
                    }
                    
                    // Perform sync
                    val result = syncService.syncNotes()
                    
                    if (result.isSuccess) {
                        showToast("✅ ${result.syncedCount} Notizen synchronisiert")
                        loadNotes()
                    } else {
                        showToast("❌ Sync fehlgeschlagen: ${result.errorMessage}")
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Pull-to-Refresh sync failed", e)
                    showToast("❌ Fehler: ${e.message}")
                } finally {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
        
        // Set Material 3 color scheme
        swipeRefreshLayout.setColorSchemeResources(
            com.google.android.material.R.color.material_dynamic_primary50
        )
    }
    
    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, // No drag
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Swipe left or right
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = adapter.currentList[position]
                val notesCopy = adapter.currentList.toMutableList()
                
                // Track pending deletion to prevent flicker
                pendingDeletions.add(note.id)
                
                // Remove from list immediately for visual feedback
                notesCopy.removeAt(position)
                adapter.submitList(notesCopy)
                
                // Show Snackbar with UNDO
                Snackbar.make(
                    recyclerViewNotes,
                    "Notiz gelöscht",
                    Snackbar.LENGTH_LONG
                ).setAction("RÜCKGÄNGIG") {
                    // UNDO: Remove from pending deletions and restore
                    pendingDeletions.remove(note.id)
                    loadNotes()
                }.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            // Snackbar dismissed without UNDO → Actually delete the note
                            storage.deleteNote(note.id)
                            pendingDeletions.remove(note.id)
                            loadNotes()
                        }
                    }
                }).show()
            }
            
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                // Require 80% swipe to trigger
                return 0.8f
            }
        })
        
        itemTouchHelper.attachToRecyclerView(recyclerViewNotes)
    }
    
    private fun setupFab() {
        fabAddNote.setOnClickListener {
            openNoteEditor(null)
        }
    }
    
    private fun loadNotes() {
        val notes = storage.loadAllNotes()
        
        // Filter out notes that are pending deletion (prevent flicker)
        val filteredNotes = notes.filter { it.id !in pendingDeletions }
        
        // Submit list with callback to scroll to top after list is updated
        adapter.submitList(filteredNotes) {
            // Scroll to top after list update is complete
            // Wichtig: Nach dem Erstellen/Bearbeiten einer Notiz
            if (filteredNotes.isNotEmpty()) {
                recyclerViewNotes.scrollToPosition(0)
            }
        }
        
        // Material 3 Empty State Card
        emptyStateCard.visibility = if (filteredNotes.isEmpty()) {
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
        val intent = Intent(this, SettingsActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SETTINGS)
    }
    
    private fun triggerManualSync() {
        lifecycleScope.launch {
            try {
                // Create sync service
                val syncService = WebDavSyncService(this@MainActivity)
                
                // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Manual Sync: No unsynced changes - skipping")
                    showToast("✅ Bereits synchronisiert")
                    return@launch
                }
                
                showToast("Starte Synchronisation...")
                
                // ⭐ WICHTIG: Server-Erreichbarkeits-Check VOR Sync (wie in SyncWorker)
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Manual Sync: Server not reachable - aborting")
                    showToast("Server nicht erreichbar")
                    return@launch
                }
                
                // Server ist erreichbar → Sync durchführen
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                // Show result
                if (result.isSuccess) {
                    showToast("Sync erfolgreich: ${result.syncedCount} Notizen")
                    loadNotes() // Reload notes
                } else {
                    showToast("Sync Fehler: ${result.errorMessage}")
                }
                
            } catch (e: Exception) {
                showToast("Sync Fehler: ${e.message}")
            }
        }
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
                triggerManualSync()
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
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            // Restore was successful, reload notes
            loadNotes()
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