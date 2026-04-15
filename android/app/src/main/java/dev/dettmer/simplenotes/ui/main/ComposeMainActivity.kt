package dev.dettmer.simplenotes.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.dettmer.simplenotes.R
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncEvent
import dev.dettmer.simplenotes.sync.SyncEventBus
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.ui.settings.ComposeSettingsActivity
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import android.os.PowerManager
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.BatteryOptimizationHelper
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.widget.NoteWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val messageRes = if (granted) R.string.toast_notifications_enabled else R.string.toast_notifications_disabled
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComposeNoteEditorActivity.RESULT_NOTE_DELETED) {
            val noteId =
                result.data?.getStringExtra(ComposeNoteEditorActivity.RESULT_EXTRA_NOTE_ID)
                    ?: return@registerForActivityResult
            val deleteFromServer = result.data?.getBooleanExtra(
                ComposeNoteEditorActivity.RESULT_EXTRA_DELETE_FROM_SERVER,
                false
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

    // v2.0.0: Theme state — initialized in onCreate, refreshed in onResume after returning from Settings
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var colorTheme by mutableStateOf(ColorTheme.DYNAMIC)

    // 🆕 v1.10.0: Separate Job for banner auto-hide — survives collect re-emissions
    private var bannerAutoHideJob: kotlinx.coroutines.Job? = null

    // Phase 3: Track if coming from editor to scroll to top
    private var cameFromEditor = false

    // v2.0.0: Track if coming from settings (to suppress onResume sync)
    private var cameFromSettings = false

    // 🆕 v2.3.0: State-driven battery optimization dialog for migration prompt
    private var showBatteryOptDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Splash Screen — keep visible until notes are loaded (v2.0.0 fix)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }

        super.onCreate(savedInstanceState)

        // v2.0.0: Load theme from prefs (context available after super.onCreate)
        themeMode = ThemePreferences.getThemeMode(prefs)
        colorTheme = ThemePreferences.getColorTheme(prefs)

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
        lifecycleScope.launch {
            migrateChecklistsForBackwardsCompat()
        }

        // 🆕 v2.3.0: One-time battery optimization migration for existing users
        checkBatteryOptimizationMigration()

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
            SimpleNotesTheme(themeMode = themeMode, colorTheme = colorTheme) {
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

                // 🆕 v2.3.0: Battery optimization migration dialog
                if (showBatteryOptDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryOptDialog = false },
                        title = { Text(stringResource(R.string.battery_optimization_dialog_title)) },
                        text = { Text(stringResource(R.string.battery_optimization_dialog_full_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showBatteryOptDialog = false
                                BatteryOptimizationHelper.openBatteryOptimizationSettings(this)
                            }) {
                                Text(stringResource(R.string.battery_optimization_open_settings))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatteryOptDialog = false }) {
                                Text(stringResource(R.string.battery_optimization_later))
                            }
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

        // v2.0.0: Refresh theme state when returning from Settings
        themeMode = ThemePreferences.getThemeMode(prefs)
        colorTheme = ThemePreferences.getColorTheme(prefs)

        // 🌟 v1.6.0: Refresh offline mode state FIRST (before any sync checks)
        // This ensures UI reflects current offline mode when returning from Settings
        viewModel.refreshOfflineModeState()

        // 🎨 v1.7.0: Refresh display mode when returning from Settings
        viewModel.refreshDisplayMode()
        viewModel.refreshCustomAppTitle() // 🆕 v1.9.0 (F05)
        viewModel.refreshGridSettings() // 🆕 v2.1.0 (F46)

        // Reload notes
        viewModel.loadNotes()

        // Phase 3: Scroll to top if coming from editor (new/edited note)
        // v2.0.0: Track whether we're returning from an in-app child activity
        val returningFromChild = cameFromEditor || cameFromSettings
        if (cameFromEditor) {
            viewModel.scrollToTop()
            cameFromEditor = false
            Logger.d(TAG, "📜 Came from editor - scrolling to top")
        }
        if (cameFromSettings) {
            cameFromSettings = false
            Logger.d(TAG, "📜 Came from settings")
        }

        // Trigger Auto-Sync on app resume — but not when returning from editor/settings
        if (!returningFromChild) {
            viewModel.triggerAutoSync("onResume")
        }

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SyncStateManager.syncStatus.collect { status ->
                    viewModel.updateSyncState(status)
                }
            }
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
                            dev.dettmer.simplenotes.sync.SyncPhase.PREPARING,
                            dev.dettmer.simplenotes.sync.SyncPhase.UPLOADING,
                            dev.dettmer.simplenotes.sync.SyncPhase.DOWNLOADING,
                            dev.dettmer.simplenotes.sync.SyncPhase.DELETING,
                            dev.dettmer.simplenotes.sync.SyncPhase.IMPORTING_MARKDOWN,
                            dev.dettmer.simplenotes.sync.SyncPhase.IDLE -> 0L
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
            dev.dettmer.simplenotes.R.anim.shared_axis_x_enter,
            dev.dettmer.simplenotes.R.anim.shared_axis_x_exit
        )
        editorLauncher.launch(intent, options)
    }

    private fun createNote(noteType: NoteType) {
        cameFromEditor = true
        val intent = Intent(this, ComposeNoteEditorActivity::class.java)
        intent.putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_TYPE, noteType.name)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.shared_axis_x_enter,
            dev.dettmer.simplenotes.R.anim.shared_axis_x_exit
        )
        editorLauncher.launch(intent, options)
    }

    private fun openSettings() {
        cameFromSettings = true
        val intent = Intent(this, ComposeSettingsActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            dev.dettmer.simplenotes.R.anim.shared_axis_x_enter,
            dev.dettmer.simplenotes.R.anim.shared_axis_x_exit
        )
        settingsLauncher.launch(intent, options)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * v1.4.1: Migrates existing checklists for backwards compatibility.
     */
    private suspend fun migrateChecklistsForBackwardsCompat() {
        val migrationKey = "v1.4.1_checklist_migration_done"

        // Only run once
        if (prefs.getBoolean(migrationKey, false)) {
            return
        }

        val storage = NotesStorage(this)
        val allNotes = withContext(Dispatchers.IO) { storage.loadAllNotes() }
        val checklistsToMigrate = allNotes.filter { note ->
            note.noteType == NoteType.CHECKLIST &&
                note.content.isBlank() &&
                note.checklistItems?.isNotEmpty() == true
        }

        if (checklistsToMigrate.isNotEmpty()) {
            Logger.d(TAG, "🔄 v1.4.1 Migration: Found ${checklistsToMigrate.size} checklists without fallback content")

            withContext(Dispatchers.IO) {
                for (note in checklistsToMigrate) {
                    val updatedNote = note.copy(
                        syncStatus = SyncStatus.PENDING
                    )
                    storage.saveNote(updatedNote)
                    Logger.d(TAG, "   📝 Marked for re-sync: ${note.title}")
                }
            }

            Logger.d(TAG, "✅ v1.4.1 Migration: ${checklistsToMigrate.size} checklists marked for re-sync")
        }

        // Mark migration as done
        prefs.edit { putBoolean(migrationKey, true) }
    }

    /**
     * 🆕 v2.3.0: One-time battery optimization check for existing users.
     *
     * Existing users who already have sync enabled (offline mode disabled) but haven't
     * been prompted for battery optimization exemption should see this dialog once.
     * New users will be prompted via Trigger A (setOfflineMode → checkAndPromptBatteryOptimization).
     */
    private fun checkBatteryOptimizationMigration() {
        // Only run if offline mode is disabled (sync is active)
        if (prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)) return

        // Only run once
        if (prefs.getBoolean(Constants.KEY_BATTERY_OPT_MIGRATION_SHOWN, false)) return

        // Mark as shown immediately (regardless of whether the dialog is needed)
        prefs.edit { putBoolean(Constants.KEY_BATTERY_OPT_MIGRATION_SHOWN, true) }

        // Check if already exempt from battery optimization
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Logger.d(TAG, "🔋 Battery optimization already ignored — migration prompt not needed")
            return
        }

        // Show migration dialog
        Logger.d(TAG, "🔋 Showing one-time battery optimization migration prompt")
        showBatteryOptDialog = true
    }

}

/**
 * Delete confirmation dialog
 */
@Composable
private fun DeleteConfirmationDialog(noteTitle: String, onDismiss: () -> Unit, onDeleteLocal: () -> Unit, onDeleteFromServer: () -> Unit) {
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
