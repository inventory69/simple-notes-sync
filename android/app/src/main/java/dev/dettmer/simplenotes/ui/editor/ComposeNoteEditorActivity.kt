package dev.dettmer.simplenotes.ui.editor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.DynamicColors
import androidx.core.content.FileProvider
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.PdfExporter
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
        // 🆕 v1.10.0-P2: Result codes for deletion forwarding to MainViewModel
        const val RESULT_NOTE_DELETED = 10
        const val RESULT_EXTRA_NOTE_ID = "result_note_id"
        const val RESULT_EXTRA_DELETE_FROM_SERVER = "result_delete_from_server"
    }
    
    private val viewModel: NoteEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                handle[NoteEditorViewModel.ARG_NOTE_ID] = intent.getStringExtra(EXTRA_NOTE_ID)
                handle[NoteEditorViewModel.ARG_NOTE_TYPE] =
                    intent.getStringExtra(EXTRA_NOTE_TYPE) ?: NoteType.TEXT.name
                NoteEditorViewModel(application, handle)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)
        
        enableEdgeToEdge()

        // v2.0.0: Register enter/exit transitions in onCreate for Predictive Back (API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }

        setContent {
            SimpleNotesTheme {
                NoteEditorScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        finishWithSlideAnimation()
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
                    // 🆕 v1.10.0-P2: Forward deletion to ComposeMainActivity so it can
                    // show the undo snackbar via MainViewModel.deleteNoteFromEditor()
                    is NoteEditorEvent.NoteDeleteRequested -> {
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_EXTRA_NOTE_ID, event.noteId)
                            putExtra(RESULT_EXTRA_DELETE_FROM_SERVER, event.deleteFromServer)
                        }
                        setResult(RESULT_NOTE_DELETED, resultIntent)
                        finishWithSlideAnimation()
                    }
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

    // v2.0.0: Save unsaved changes when activity stops (Back gesture, Home, task switch)
    // Replaces OnBackPressedCallback — enables Predictive Back cross-activity preview
    override fun onStop() {
        super.onStop()
        viewModel.saveOnBack()
    }

    private fun finishWithSlideAnimation() {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        // API 34+: overrideActivityTransition(CLOSE, ...) registered in onCreate handles this
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
     * 🆕 v1.10.0-Papa: Generates a PDF from the current note and opens the share dialog.
     *
     * Uses android.graphics.pdf.PdfDocument (no external libraries).
     * PDF is saved to cacheDir/shared_pdfs/ and shared via FileProvider.
     */
    private fun handleShareAsPdf(event: NoteEditorEvent.ShareAsPdf) {
        val state = viewModel.uiState.value
        val checklistItems = viewModel.checklistItems.value

        val pdfFile = PdfExporter.generatePdf(
            context = this,
            title = event.title,
            noteType = state.noteType,
            textContent = state.content,
            checklistItems = checklistItems
        )

        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, getString(R.string.share_pdf_error), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationInfo.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, event.title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No PDF share target found: ${e.message}")
            Toast.makeText(this, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
        }
    }
}


