package dev.dettmer.simplenotes.ui.main

import android.Manifest
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.ui.settings.ComposeSettingsActivity
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
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
        private const val REQUEST_SETTINGS = 1002
    }
    
    private val viewModel: MainViewModel by viewModels()
    
    private val prefs by lazy {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // Phase 3: Track if coming from editor to scroll to top
    private var cameFromEditor = false
    
    /**
     * BroadcastReceiver for Background-Sync Completion (Periodic Sync)
     */
    private val syncCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra("success", false) ?: false
            val count = intent?.getIntExtra("count", 0) ?: 0
            
            Logger.d(TAG, "📡 Sync completed broadcast received: success=$success, count=$count")
            
            // UI refresh
            if (success && count > 0) {
                viewModel.loadNotes()
                Logger.d(TAG, "🔄 Notes reloaded after background sync")
            }
        }
    }
    
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
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        Logger.d(TAG, "📱 ComposeMainActivity.onResume() - Registering receivers")
        
        // 🌟 v1.6.0: Refresh offline mode state FIRST (before any sync checks)
        // This ensures UI reflects current offline mode when returning from Settings
        viewModel.refreshOfflineModeState()
        
        // Register BroadcastReceiver for Background-Sync
        LocalBroadcastManager.getInstance(this).registerReceiver(
            syncCompletedReceiver,
            IntentFilter(SyncWorker.ACTION_SYNC_COMPLETED)
        )
        
        Logger.d(TAG, "📡 BroadcastReceiver registered (sync-completed)")
        
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
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister BroadcastReceiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncCompletedReceiver)
        Logger.d(TAG, "📡 BroadcastReceiver unregistered")
    }
    
    private fun setupSyncStateObserver() {
        SyncStateManager.syncStatus.observe(this) { status ->
            viewModel.updateSyncState(status)
            
            // Hide banner after delay for completed/error states
            when (status.state) {
                SyncStateManager.SyncState.COMPLETED -> {
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(1500L)
                        SyncStateManager.reset()
                    }
                }
                SyncStateManager.SyncState.ERROR -> {
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(3000L)
                        SyncStateManager.reset()
                    }
                }
                else -> { /* No action needed */ }
            }
        }
    }
    
    private fun openNoteEditor(noteId: String?) {
        cameFromEditor = true
        val intent = Intent(this, ComposeNoteEditorActivity::class.java)
        noteId?.let {
            intent.putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_ID, it)
        }
        
        // v1.5.0: Add slide animation
        val options = ActivityOptions.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        startActivity(intent, options.toBundle())
    }
    
    private fun createNote(noteType: NoteType) {
        cameFromEditor = true
        val intent = Intent(this, ComposeNoteEditorActivity::class.java)
        intent.putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_TYPE, noteType.name)
        
        // v1.5.0: Add slide animation
        val options = ActivityOptions.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        startActivity(intent, options.toBundle())
    }
    
    private fun openSettings() {
        val intent = Intent(this, ComposeSettingsActivity::class.java)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.slide_in_right,
            dev.dettmer.simplenotes.R.anim.slide_out_left
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_SETTINGS, options.toBundle())
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
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            // Settings changed, reload notes
            viewModel.loadNotes()
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
