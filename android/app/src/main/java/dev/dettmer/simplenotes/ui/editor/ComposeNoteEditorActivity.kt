@file:Suppress("DEPRECATION") // AbstractSavedStateViewModelFactory deprecated, will migrate to viewModelFactory in v2.0.0

package dev.dettmer.simplenotes.ui.editor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.launch

/**
 * Compose-based Note Editor Activity
 * 
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * Replaces the old NoteEditorActivity with a modern Compose implementation.
 * 
 * Supports:
 * - TEXT notes with title and content
 * - CHECKLIST notes with drag & drop reordering
 * - Auto-keyboard focus for new checklist items
 */
class ComposeNoteEditorActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TYPE = "extra_note_type"
        private const val TAG = "ComposeNoteEditorActivity"  // 🆕 v1.10.0-Papa
    }
    
    private val viewModel: NoteEditorViewModel by viewModels {
        NoteEditorViewModelFactory(
            application = application,
            owner = this,
            noteId = intent.getStringExtra(EXTRA_NOTE_ID),
            noteType = intent.getStringExtra(EXTRA_NOTE_TYPE) ?: NoteType.TEXT.name
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)
        
        enableEdgeToEdge()
        
        // v1.5.0: Handle back button with slide animation
        // 🔧 v1.10.0: Save unsaved changes before navigating back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.saveOnBack()  // 🆕 v1.10.0: Silent save before exit
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(
                    dev.dettmer.simplenotes.R.anim.slide_in_left,
                    dev.dettmer.simplenotes.R.anim.slide_out_right
                )
            }
        })
        
        setContent {
            SimpleNotesTheme {
                NoteEditorScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        viewModel.saveOnBack()  // 🆕 v1.10.0: Silent save before exit
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(
                            dev.dettmer.simplenotes.R.anim.slide_in_left,
                            dev.dettmer.simplenotes.R.anim.slide_out_right
                        )
                    }
                )
            }
        }

        // 🆕 v1.10.0-Papa: Collect calendar/share events from ViewModel
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is NoteEditorEvent.OpenCalendar -> handleCalendarExport(event)
                    is NoteEditorEvent.ShareAsText -> handleShareAsText(event)
                    is NoteEditorEvent.ShareAsPdf -> handleShareAsPdf(event)
                    else -> { /* handled by Composable */ }
                }
            }
        }
    }

    /**
     * 🆕 v1.10.0-Papa: Reload Checklist-State falls Widget Änderungen gemacht hat.
     *
     * Wenn die Activity aus dem Hintergrund zurückkehrt (z.B. nach Widget-Toggle),
     * wird der aktuelle Note-Stand von Disk geladen und der ViewModel-State
     * für Checklist-Items aktualisiert.
     */
    override fun onResume() {
        super.onResume()
        viewModel.reloadFromStorage()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0-Papa: Calendar Export & Share Handlers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Opens a calendar app with the note data pre-filled.
     * Uses ACTION_INSERT — no calendar permissions required.
     */
    private fun handleCalendarExport(event: NoteEditorEvent.OpenCalendar) {
        val beginTime = System.currentTimeMillis()
        val endTime = beginTime + 60 * 60 * 1000L  // +1 hour
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No calendar app found: ${e.message}")
            Toast.makeText(this, getString(R.string.share_no_calendar_app), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens the Android share sheet with the note as plain text.
     */
    private fun handleShareAsText(event: NoteEditorEvent.ShareAsText) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, event.title)
            putExtra(Intent.EXTRA_TEXT, event.text)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No share target found: ${e.message}")
            Toast.makeText(this, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🔜 v1.10.0-Papa: PDF share stub — replaced in Commit 5 (PdfExporter + FileProvider).
     */
    @Suppress("UnusedParameter")
    private fun handleShareAsPdf(event: NoteEditorEvent.ShareAsPdf) {
        Toast.makeText(this, getString(R.string.share_pdf_error), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Custom ViewModelFactory to pass SavedStateHandle with intent extras
 */
class NoteEditorViewModelFactory(
    private val application: android.app.Application,
    owner: SavedStateRegistryOwner,
    private val noteId: String?,
    private val noteType: String
) : AbstractSavedStateViewModelFactory(owner, null) {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        // Populate SavedStateHandle with intent extras
        handle[NoteEditorViewModel.ARG_NOTE_ID] = noteId
        handle[NoteEditorViewModel.ARG_NOTE_TYPE] = noteType
        
        return NoteEditorViewModel(application, handle) as T
    }
}
