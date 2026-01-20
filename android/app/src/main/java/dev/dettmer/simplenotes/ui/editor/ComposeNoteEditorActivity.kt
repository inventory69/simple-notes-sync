@file:Suppress("DEPRECATION") // AbstractSavedStateViewModelFactory deprecated, will migrate to viewModelFactory in v2.0.0

package dev.dettmer.simplenotes.ui.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme

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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
