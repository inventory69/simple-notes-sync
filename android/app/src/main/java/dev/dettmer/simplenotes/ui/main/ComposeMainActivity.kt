package dev.dettmer.simplenotes.ui.main

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncEventBus
import dev.dettmer.simplenotes.sync.SyncEvent
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.settings.ComposeSettingsActivity
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.widget.NoteWidget
import kotlinx.coroutines.launch

/**
 * Main Activity with Jetpack Compose UI
 * v1.5.0: Complete MainActivity Redesign with Compose
 * 
 * Replaces the old 805-line MainActivity.kt with a modern
 * Compose-based implementation featuring:
 * - Notes list with swipe-to-delete
 * - Pull-to-refresh for sync
 * - FAB with note type selection
 * - Material 3 Design with Dynamic Colors (Material You)
 * - Design consistent with ComposeSettingsActivity
 */
class ComposeMainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "ComposeMainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
    
    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComposeNoteEditorActivity.RESULT_NOTE_DELETED) {
            val noteId = result.data?.getStringExtra(ComposeNoteEditorActivity.RESULT_EXTRA_NOTE_ID) ?: return@registerForActivityResult
            val deleteFromServer = result.data?.getBooleanExtra(
                ComposeNoteEditorActivity.RESULT_EXTRA_DELETE_FROM_SERVER, false
            ) ?: false
            viewModel.deleteNoteFromEditor(noteId, deleteFromServer)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.loadNotes()
        }
    }

    private val viewModel: MainViewModel by viewModels()
    
    private val prefs by lazy {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // 🆕 v1.10.0: Separate Job for banner auto-hide — survives collect re-emissions
    private var bannerAutoHideJob: kotlinx.coroutines.Job? = null

    // Phase 3: Track if coming from editor to scroll to top
    private var cameFromEditor = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Splash Screen (Android 12+)
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Material You (Android 12+)
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Initialize Logger and enable file logging if configured
        Logger.init(this)
        if (prefs.getBoolean(Constants.KEY_FILE_LOGGING_ENABLED, false)) {
            Logger.setFileLoggingEnabled(true)
        }
        
        // Clear old sync notifications on app start
        NotificationHelper.clearSyncNotifications(this)
        
        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
        
        // v1.4.1: Migrate checklists for backwards compatibility
        migrateChecklistsForBackwardsCompat()
        
        // Setup Sync State Observer
        setupSyncStateObserver()

        // v2.0.0: Collect SyncEventBus events (replaces LocalBroadcastManager)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncEventBus.events.collect { event ->
                    when (event) {
                        is SyncEvent.SyncCompleted -> {
                            Logger.d(TAG, "📡 Sync completed event: success=${event.success}, count=${event.count}")
                            if (event.success && event.count > 0) {
                                viewModel.loadNotes()
                                Logger.d(TAG, "🔄 Notes reloaded after background sync")
                            }
                        }
                    }
                }
            }
        }
        
        setContent {
            SimpleNotesTheme {
                val context = LocalContext.current
                
                // Dialog state for delete confirmation
                var deleteDialogData by remember { mutableStateOf<MainViewModel.DeleteDialogData?>(null) }
                
                // Handle delete dialog events
                LaunchedEffect(Unit) {
                    viewModel.showDeleteDialog.collect { data ->
                        deleteDialogData = data
                    }
                }
                
                // Handle toast events
                LaunchedEffect(Unit) {
                    viewModel.showToast.collect { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Delete confirmation dialog
                deleteDialogData?.let { data ->
                    DeleteConfirmationDialog(
                        noteTitle = data.note.title,
                        onDismiss = {
                            viewModel.restoreNoteAfterSwipe(data.originalList)
                            deleteDialogData = null
                        },
                        onDeleteLocal = {
                            viewModel.deleteNoteConfirmed(data.note, deleteFromServer = false)
                            deleteDialogData = null
                        },
                        onDeleteFromServer = {
                            viewModel.deleteNoteConfirmed(data.note, deleteFromServer = true)
                            deleteDialogData = null
                        }
                    )
                }
                
                MainScreen(
                    viewModel = viewModel,
                    onOpenNote = { noteId -> openNoteEditor(noteId) },
                    onOpenSettings = { openSettings() },
                    onCreateNote = { noteType -> createNote(noteType) }
                )
                
                // v1.8.0: Post-Update Changelog (shows once after update)
                UpdateChangelogSheet()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        Logger.d(TAG, "📱 ComposeMainActivity.onResume()")
        
        // 🌟 v1.6.0: Refresh offline mode state FIRST (before any sync checks)
        // This ensures UI reflects current offline mode when returning from Settings
        viewModel.refreshOfflineModeState()
        
        // 🎨 v1.7.0: Refresh display mode when returning from Settings
        viewModel.refreshDisplayMode()
        viewModel.refreshCustomAppTitle()  // 🆕 v1.9.0 (F05)
        
        // Reload notes
        viewModel.loadNotes()
        
        // Phase 3: Scroll to top if coming from editor (new/edited note)
        if (cameFromEditor) {
            viewModel.scrollToTop()
            cameFromEditor = false
            Logger.d(TAG, "📜 Came from editor - scrolling to top")
        }
        
        // Trigger Auto-Sync on app resume
        viewModel.triggerAutoSync("onResume")

        // 🆕 v1.10.0-P2: Show one-time hint if last sync was stopped by quota/standby
        val quotaReason = SyncStateManager.consumeQuotaStopNotification()
        if (quotaReason != null) {
            Logger.w(TAG, "⚠️ Showing quota-stop notification (reason: $quotaReason)")
            SyncStateManager.showInfo(getString(R.string.sync_quota_warning))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.9.0 (F09): Widget refresh on leaving app
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 🆕 v1.9.0 (F09): Refresh all active homescreen widgets.
     *
     * Iterates every GlanceId belonging to NoteWidget and calls update().
     * Glance internally deduplicates — calling update() when data has not
     * changed is a no-op at the RemoteViews level, so this is safe to call
     * on every onStop without battery concern.
     */
    private fun refreshAllWidgets() {
        lifecycleScope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(this@ComposeMainActivity)
                val glanceIds = glanceManager.getGlanceIds(NoteWidget::class.java)
                if (glanceIds.isEmpty()) return@launch
                Logger.d(TAG, "🔄 F09: Refreshing ${glanceIds.size} widget(s) on onStop")
                glanceIds.forEach { id ->
                    NoteWidget().update(this@ComposeMainActivity, id)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "F09: Failed to refresh widgets on onStop: ${e.message}")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 🆕 v1.9.0 (F09): Refresh widgets when the user leaves the app.
        // cameFromEditor is true when navigating to the editor (in-app); the
        // editor already updates widgets on save — skip here to avoid double-update.
        // When the user presses Home or switches apps, cameFromEditor is false.
        if (!cameFromEditor) {
            refreshAllWidgets()
        }
        Logger.d(TAG, "📱 ComposeMainActivity.onStop() - cameFromEditor=$cameFromEditor")
    }

    private fun setupSyncStateObserver() {
        // 🆕 v1.8.0: SyncStatus nur noch für PullToRefresh-Indikator (intern)
        SyncStateManager.syncStatus.observe(this) { status ->
            viewModel.updateSyncState(status)
        }

        // 🆕 v1.10.0: Auto-Hide via separatem Job — garantierte Mindest-Anzeigedauer
        // Reads from viewModel.syncProgress (has min-phase-duration applied) so auto-hide
        // timer is aligned with what the user actually sees in the banner.
        lifecycleScope.launch {
            viewModel.syncProgress.collect { progress ->
                when (progress.phase) {
                    dev.dettmer.simplenotes.sync.SyncPhase.COMPLETED,
                    dev.dettmer.simplenotes.sync.SyncPhase.INFO,
                    dev.dettmer.simplenotes.sync.SyncPhase.ERROR -> {
                        bannerAutoHideJob?.cancel()
                        val delayMs = when (progress.phase) {
                            dev.dettmer.simplenotes.sync.SyncPhase.COMPLETED -> Constants.BANNER_DELAY_COMPLETED_MS
                            dev.dettmer.simplenotes.sync.SyncPhase.INFO -> Constants.BANNER_DELAY_INFO_MS
                            dev.dettmer.simplenotes.sync.SyncPhase.ERROR -> Constants.BANNER_DELAY_ERROR_MS
                            else -> 0L
                        }
                        bannerAutoHideJob = lifecycleScope.launch {
                            kotlinx.coroutines.delay(delayMs)
                            SyncStateManager.reset()
                        }
                    }
                    // Cancel pending auto-hide if a new sync starts
                    dev.dettmer.simplenotes.sync.SyncPhase.PREPARING,
                    dev.dettmer.simplenotes.sync.SyncPhase.UPLOADING,
                    dev.dettmer.simplenotes.sync.SyncPhase.DOWNLOADING,
                    dev.dettmer.simplenotes.sync.SyncPhase.DELETING,
                    dev.dettmer.simplenotes.sync.SyncPhase.IMPORTING_MARKDOWN -> {
                        bannerAutoHideJob?.cancel()
                    }
                    dev.dettmer.simplenotes.sync.SyncPhase.IDLE -> { /* nothing */ }
                }
            }
        }
    }
    
    private fun openNoteEditor(noteId: String?) {
        cameFromEditor = true
        val intent = Intent(this, ComposeNoteEditorActivity::class.java)
        noteId?.let {
            intent.putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_ID, it)
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        editorLauncher.launch(intent, options)
    }
    
    private fun createNote(noteType: NoteType) {
        cameFromEditor = true
        val intent = Intent(this, ComposeNoteEditorActivity::class.java)
        intent.putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_TYPE, noteType.name)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        editorLauncher.launch(intent, options)
    }
    
    private fun openSettings() {
        val intent = Intent(this, ComposeSettingsActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        settingsLauncher.launch(intent, options)
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
    
    /**
     * v1.4.1: Migrates existing checklists for backwards compatibility.
     */
    private fun migrateChecklistsForBackwardsCompat() {
        val migrationKey = "v1.4.1_checklist_migration_done"
        
        // Only run once
        if (prefs.getBoolean(migrationKey, false)) {
            return
        }
        
        val storage = NotesStorage(this)
        val allNotes = storage.loadAllNotes()
        val checklistsToMigrate = allNotes.filter { note ->
            note.noteType == NoteType.CHECKLIST && 
            note.content.isBlank() &&
            note.checklistItems?.isNotEmpty() == true
        }
        
        if (checklistsToMigrate.isNotEmpty()) {
            Logger.d(TAG, "🔄 v1.4.1 Migration: Found ${checklistsToMigrate.size} checklists without fallback content")
            
            for (note in checklistsToMigrate) {
                val updatedNote = note.copy(
                    syncStatus = SyncStatus.PENDING
                )
                storage.saveNote(updatedNote)
                Logger.d(TAG, "   📝 Marked for re-sync: ${note.title}")
            }
            
            Logger.d(TAG, "✅ v1.4.1 Migration: ${checklistsToMigrate.size} checklists marked for re-sync")
        }
        
        // Mark migration as done
        prefs.edit().putBoolean(migrationKey, true).apply()
    }
    
    
    @Deprecated("Deprecated in API 23", ReplaceWith("Use ActivityResultContracts"))
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
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
                    Toast.makeText(this, getString(R.string.toast_notifications_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, 
                        getString(R.string.toast_notifications_disabled), 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
private fun DeleteConfirmationDialog(
    noteTitle: String,
    onDismiss: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteFromServer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legacy_delete_dialog_title)) },
        text = { 
            Text(stringResource(R.string.legacy_delete_dialog_message, noteTitle)) 
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onDeleteLocal) {
                Text(stringResource(R.string.delete_local_only))
            }
            TextButton(onClick = onDeleteFromServer) {
                Text(stringResource(R.string.legacy_delete_from_server))
            }
        }
    )
}
