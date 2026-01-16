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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.dettmer.simplenotes.adapters.NotesAdapter
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.utils.showToast
import dev.dettmer.simplenotes.utils.Constants
import android.widget.TextView
import android.widget.CheckBox
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.sync.SyncStateManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.View
import android.widget.LinearLayout
import android.view.Gravity
import android.widget.PopupMenu
import dev.dettmer.simplenotes.models.NoteType

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerViewNotes: RecyclerView
    private lateinit var emptyStateCard: MaterialCardView
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    // 🔄 v1.3.1: Sync Status Banner
    private lateinit var syncStatusBanner: LinearLayout
    private lateinit var syncStatusText: TextView
    
    private lateinit var adapter: NotesAdapter
    private val storage by lazy { NotesStorage(this) }
    
    // Menu reference for sync button state
    private var optionsMenu: Menu? = null
    
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
        private const val SYNC_COMPLETED_DELAY_MS = 1500L
        private const val ERROR_DISPLAY_DELAY_MS = 3000L
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
        
        // Logger initialisieren und File-Logging aktivieren wenn eingestellt
        Logger.init(this)
        if (prefs.getBoolean(Constants.KEY_FILE_LOGGING_ENABLED, false)) {
            Logger.setFileLoggingEnabled(true)
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
        
        // v1.4.1: Migrate checklists for backwards compatibility
        migrateChecklistsForBackwardsCompat()
        
        loadNotes()
        
        // 🔄 v1.3.1: Observe sync state for UI updates
        setupSyncStateObserver()
    }
    
    /**
     * 🔄 v1.3.1: Beobachtet Sync-Status für UI-Feedback
     */
    private fun setupSyncStateObserver() {
        SyncStateManager.syncStatus.observe(this) { status ->
            when (status.state) {
                SyncStateManager.SyncState.SYNCING -> {
                    // Disable sync controls
                    setSyncControlsEnabled(false)
                    // 🔄 v1.3.1: Show sync status banner (ersetzt SwipeRefresh-Animation)
                    syncStatusText.text = getString(R.string.sync_status_syncing)
                    syncStatusBanner.visibility = View.VISIBLE
                }
                SyncStateManager.SyncState.COMPLETED -> {
                    // Re-enable sync controls
                    setSyncControlsEnabled(true)
                    swipeRefreshLayout.isRefreshing = false
                    // Show completed briefly, then hide
                    syncStatusText.text = status.message ?: getString(R.string.sync_status_completed)
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(SYNC_COMPLETED_DELAY_MS)
                        syncStatusBanner.visibility = View.GONE
                        SyncStateManager.reset()
                    }
                }
                SyncStateManager.SyncState.ERROR -> {
                    // Re-enable sync controls
                    setSyncControlsEnabled(true)
                    swipeRefreshLayout.isRefreshing = false
                    // Show error briefly, then hide
                    syncStatusText.text = status.message ?: getString(R.string.sync_status_error)
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(ERROR_DISPLAY_DELAY_MS)
                        syncStatusBanner.visibility = View.GONE
                        SyncStateManager.reset()
                    }
                }
                SyncStateManager.SyncState.IDLE -> {
                    setSyncControlsEnabled(true)
                    swipeRefreshLayout.isRefreshing = false
                    syncStatusBanner.visibility = View.GONE
                }
                // v1.5.0: Silent-Sync - Banner nicht anzeigen, aber Sync-Controls deaktivieren
                SyncStateManager.SyncState.SYNCING_SILENT -> {
                    setSyncControlsEnabled(false)
                    // Kein Banner anzeigen bei Silent-Sync (z.B. onResume Auto-Sync)
                }
            }
        }
    }
    
    /**
     * 🔄 v1.3.1: Aktiviert/deaktiviert Sync-Controls (Button + SwipeRefresh)
     */
    private fun setSyncControlsEnabled(enabled: Boolean) {
        // Menu Sync-Button
        optionsMenu?.findItem(R.id.action_sync)?.isEnabled = enabled
        // SwipeRefresh
        swipeRefreshLayout.isEnabled = enabled
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
     * v1.5.0: Silent-Sync - kein Banner während des Syncs, Fehler werden trotzdem angezeigt
     */
    private fun triggerAutoSync(source: String = "unknown") {
        // Throttling: Max 1 Sync pro Minute
        if (!canTriggerAutoSync()) {
            return
        }
        
        // 🔄 v1.3.1: Check if sync already running
        // v1.5.0: silent=true - kein Banner bei Auto-Sync
        if (!SyncStateManager.tryStartSync("auto-$source", silent = true)) {
            Logger.d(TAG, "⏭️ Auto-sync ($source): Another sync already in progress")
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
                    SyncStateManager.reset()
                    return@launch
                }
                
                // ⭐ WICHTIG: Server-Erreichbarkeits-Check VOR Sync (wie in SyncWorker)
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): Server not reachable - skipping silently")
                    SyncStateManager.reset()
                    return@launch
                }
                
                // Server ist erreichbar → Sync durchführen
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                // Feedback abhängig von Source
                if (result.isSuccess && result.syncedCount > 0) {
                    Logger.d(TAG, "✅ Auto-sync successful ($source): ${result.syncedCount} notes")
                    SyncStateManager.markCompleted("${result.syncedCount} Notizen")
                    
                    // onResume: Nur Success-Toast
                    showToast("✅ Gesynct: ${result.syncedCount} Notizen")
                    loadNotes()
                    
                } else if (result.isSuccess) {
                    Logger.d(TAG, "ℹ️ Auto-sync ($source): No changes")
                    SyncStateManager.markCompleted()
                    
                } else {
                    Logger.e(TAG, "❌ Auto-sync failed ($source): ${result.errorMessage}")
                    SyncStateManager.markError(result.errorMessage)
                    // Kein Toast - App ist im Hintergrund
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "💥 Auto-sync exception ($source): ${e.message}")
                SyncStateManager.markError(e.message)
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
        
        // 🔄 v1.3.1: Sync Status Banner
        syncStatusBanner = findViewById(R.id.syncStatusBanner)
        syncStatusText = findViewById(R.id.syncStatusText)
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
            
            // 🔄 v1.3.1: Check if sync already running (Banner zeigt Status)
            if (!SyncStateManager.tryStartSync("pullToRefresh")) {
                swipeRefreshLayout.isRefreshing = false
                return@setOnRefreshListener
            }
            
            lifecycleScope.launch {
                try {
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
                    
                    if (serverUrl.isNullOrEmpty()) {
                        showToast("⚠️ Server noch nicht konfiguriert")
                        SyncStateManager.reset()
                        return@launch
                    }
                    
                    val syncService = WebDavSyncService(this@MainActivity)
                    
                    // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                    if (!syncService.hasUnsyncedChanges()) {
                        Logger.d(TAG, "⏭️ No unsynced changes, skipping server reachability check")
                        SyncStateManager.markCompleted("Bereits synchronisiert")
                        return@launch
                    }
                    
                    // Check if server is reachable
                    if (!syncService.isServerReachable()) {
                        SyncStateManager.markError("Server nicht erreichbar")
                        return@launch
                    }
                    
                    // Perform sync
                    val result = syncService.syncNotes()
                    
                    if (result.isSuccess) {
                        SyncStateManager.markCompleted("${result.syncedCount} Notizen")
                        loadNotes()
                    } else {
                        SyncStateManager.markError(result.errorMessage)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Pull-to-Refresh sync failed", e)
                    SyncStateManager.markError(e.message)
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
                val position = viewHolder.bindingAdapterPosition
                val swipedNote = adapter.currentList[position]
                
                // Store original list BEFORE removing note
                val originalList = adapter.currentList.toList()
                
                // Remove from list for visual feedback (NOT from storage yet!)
                val listWithoutNote = originalList.toMutableList().apply {
                    removeAt(position)
                }
                adapter.submitList(listWithoutNote)
                
                // Show dialog with ability to restore
                showServerDeletionDialog(swipedNote, originalList)
            }
            
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                // Require 80% swipe to trigger
                return 0.8f
            }
        })
        
        itemTouchHelper.attachToRecyclerView(recyclerViewNotes)
    }
    
    private fun showServerDeletionDialog(note: Note, originalList: List<Note>) {
        val alwaysDeleteFromServer = prefs.getBoolean(Constants.KEY_ALWAYS_DELETE_FROM_SERVER, false)
        
        if (alwaysDeleteFromServer) {
            // Auto-delete from server without asking
            deleteNoteLocally(note, deleteFromServer = true)
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_deletion, null)
        val checkboxAlways = dialogView.findViewById<CheckBox>(R.id.checkboxAlwaysDeleteFromServer)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.legacy_delete_dialog_title))
            .setMessage(getString(R.string.legacy_delete_dialog_message, note.title))
            .setView(dialogView)
            .setNeutralButton(getString(R.string.cancel)) { _, _ ->
                // RESTORE: Re-submit original list (note is NOT deleted from storage)
                adapter.submitList(originalList)
            }
            .setOnCancelListener {
                // User pressed back - also restore
                adapter.submitList(originalList)
            }
            .setPositiveButton("Nur lokal") { _, _ ->
                if (checkboxAlways.isChecked) {
                    prefs.edit().putBoolean(Constants.KEY_ALWAYS_DELETE_FROM_SERVER, false).apply()
                }
                // NOW actually delete from storage
                deleteNoteLocally(note, deleteFromServer = false)
            }
            .setNegativeButton(getString(R.string.legacy_delete_from_server)) { _, _ ->
                if (checkboxAlways.isChecked) {
                    prefs.edit().putBoolean(Constants.KEY_ALWAYS_DELETE_FROM_SERVER, true).apply()
                }
                deleteNoteLocally(note, deleteFromServer = true)
            }
            .setCancelable(true)
            .show()
    }
    
    private fun deleteNoteLocally(note: Note, deleteFromServer: Boolean) {
        // Track pending deletion to prevent flicker
        pendingDeletions.add(note.id)
        
        // Delete from storage
        storage.deleteNote(note.id)
        
        // Reload to reflect changes
        loadNotes()
        
        // Show Snackbar with UNDO option
        val message = if (deleteFromServer) {
            getString(R.string.legacy_delete_with_server, note.title)
        } else {
            getString(R.string.legacy_delete_local_only, note.title)
        }
        
        Snackbar.make(recyclerViewNotes, message, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.snackbar_undo)) {
                // UNDO: Restore note
                storage.saveNote(note)
                pendingDeletions.remove(note.id)
                loadNotes()
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION) {
                        // Snackbar dismissed without UNDO
                        pendingDeletions.remove(note.id)
                        
                        // Delete from server if requested
                        if (deleteFromServer) {
                            lifecycleScope.launch {
                                try {
                                    val webdavService = WebDavSyncService(this@MainActivity)
                                    val success = webdavService.deleteNoteFromServer(note.id)
                                    if (success) {
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(R.string.snackbar_deleted_from_server),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(R.string.snackbar_server_delete_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Server-Fehler: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }).show()
    }
    
    /**
     * v1.4.0: Setup FAB mit Dropdown für Notiz-Typ Auswahl
     */
    private fun setupFab() {
        fabAddNote.setOnClickListener { view ->
            showNoteTypePopup(view)
        }
    }
    
    /**
     * v1.4.0: Zeigt Popup-Menü zur Auswahl des Notiz-Typs
     */
    private fun showNoteTypePopup(anchor: View) {
        val popupMenu = PopupMenu(this, anchor, Gravity.END)
        popupMenu.inflate(R.menu.menu_fab_note_types)
        
        // Icons im Popup anzeigen (via Reflection, da standardmäßig ausgeblendet)
        try {
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Could not force show icons in popup menu: ${e.message}")
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val noteType = when (menuItem.itemId) {
                R.id.action_create_text_note -> NoteType.TEXT
                R.id.action_create_checklist -> NoteType.CHECKLIST
                else -> return@setOnMenuItemClickListener false
            }
            
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_TYPE, noteType.name)
            startActivity(intent)
            true
        }
        
        popupMenu.show()
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
        // v1.5.0: Use new Jetpack Compose Settings
        val intent = Intent(this, dev.dettmer.simplenotes.ui.settings.ComposeSettingsActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SETTINGS)
    }
    
    private fun triggerManualSync() {
        // 🔄 v1.3.1: Check if sync already running (Banner zeigt Status)
        if (!SyncStateManager.tryStartSync("manual")) {
            return
        }
        
        lifecycleScope.launch {
            try {
                // Create sync service
                val syncService = WebDavSyncService(this@MainActivity)
                
                // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Manual Sync: No unsynced changes - skipping")
                    SyncStateManager.markCompleted("Bereits synchronisiert")
                    return@launch
                }
                
                // ⭐ WICHTIG: Server-Erreichbarkeits-Check VOR Sync (wie in SyncWorker)
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Manual Sync: Server not reachable - aborting")
                    SyncStateManager.markError("Server nicht erreichbar")
                    return@launch
                }
                
                // Server ist erreichbar → Sync durchführen
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                // Show result
                if (result.isSuccess) {
                    SyncStateManager.markCompleted("${result.syncedCount} Notizen")
                    loadNotes() // Reload notes
                } else {
                    SyncStateManager.markError(result.errorMessage)
                }
                
            } catch (e: Exception) {
                SyncStateManager.markError(e.message)
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        optionsMenu = menu  // 🔄 v1.3.1: Store reference for sync button state
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
    
    /**
     * v1.4.1: Migriert bestehende Checklisten für Abwärtskompatibilität.
     * 
     * Problem: v1.4.0 Checklisten haben leeren "content", was auf älteren
     * App-Versionen (v1.3.x) als leere Notiz angezeigt wird.
     * 
     * Lösung: Alle Checklisten ohne Fallback-Content als PENDING markieren,
     * damit sie beim nächsten Sync mit Fallback-Content hochgeladen werden.
     * 
     * TODO: Diese Migration kann entfernt werden, sobald v1.4.0 nicht mehr
     * im Umlauf ist (ca. 6 Monate nach v1.4.1 Release, also ~Juli 2026).
     * Tracking: https://github.com/inventory69/simple-notes-sync/issues/XXX
     */
    private fun migrateChecklistsForBackwardsCompat() {
        val migrationKey = "v1.4.1_checklist_migration_done"
        
        // Nur einmal ausführen
        if (prefs.getBoolean(migrationKey, false)) {
            return
        }
        
        val allNotes = storage.loadAllNotes()
        val checklistsToMigrate = allNotes.filter { note ->
            note.noteType == NoteType.CHECKLIST && 
            note.content.isBlank() &&
            note.checklistItems?.isNotEmpty() == true
        }
        
        if (checklistsToMigrate.isNotEmpty()) {
            Logger.d(TAG, "🔄 v1.4.1 Migration: Found ${checklistsToMigrate.size} checklists without fallback content")
            
            for (note in checklistsToMigrate) {
                // Als PENDING markieren, damit beim nächsten Sync der Fallback-Content 
                // generiert und hochgeladen wird
                val updatedNote = note.copy(
                    syncStatus = dev.dettmer.simplenotes.models.SyncStatus.PENDING
                )
                storage.saveNote(updatedNote)
                Logger.d(TAG, "   📝 Marked for re-sync: ${note.title}")
            }
            
            Logger.d(TAG, "✅ v1.4.1 Migration: ${checklistsToMigrate.size} checklists marked for re-sync")
        }
        
        // Migration als erledigt markieren
        prefs.edit().putBoolean(migrationKey, true).apply()
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
                    showToast(getString(R.string.toast_notifications_enabled))
                } else {
                    showToast(getString(R.string.toast_notifications_disabled))
                }
            }
        }
    }
}
