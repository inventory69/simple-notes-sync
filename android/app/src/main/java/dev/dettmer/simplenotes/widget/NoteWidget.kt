package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.dettmer.simplenotes.storage.NotesStorage

/**
 * 🆕 v1.8.0: Homescreen Widget für Notizen und Checklisten
 *
 * Unterstützt fünf responsive Größen für breite und schmale Layouts:
 * - SMALL (110x80dp): Nur Titel
 * - NARROW_MEDIUM (110x110dp): Schmal + Vorschau / kompakte Checkliste
 * - NARROW_LARGE (110x250dp): Schmal + voller Inhalt
 * - WIDE_MEDIUM (250x110dp): Breit + Vorschau
 * - WIDE_LARGE (250x250dp): Breit + voller Inhalt / interaktive Checkliste
 *
 * Features:
 * - Material You Dynamic Colors
 * - Interaktive Checklist-Checkboxen
 * - Sperr-Funktion gegen versehentliches Bearbeiten
 * - Tap-to-Edit (öffnet NoteEditor)
 * - Einstellbare Hintergrund-Transparenz
 * - Permanenter Options-Button (⋮)
 * - NoteType-differenzierte Icons
 */
class NoteWidget : GlanceAppWidget() {

    companion object {
        // Responsive Breakpoints — schmale + breite Spalten
        val SIZE_SMALL = DpSize(110.dp, 80.dp)          // Schmal+kurz: nur Titel
        val SIZE_NARROW_MEDIUM = DpSize(110.dp, 110.dp)  // Schmal+mittel: Vorschau
        val SIZE_NARROW_LARGE = DpSize(110.dp, 250.dp)   // Schmal+groß: voller Inhalt
        val SIZE_WIDE_MEDIUM = DpSize(250.dp, 110.dp)    // Breit+mittel: Vorschau
        val SIZE_WIDE_LARGE = DpSize(250.dp, 250.dp)     // Breit+groß: voller Inhalt
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_SMALL, SIZE_NARROW_MEDIUM, SIZE_NARROW_LARGE, SIZE_WIDE_MEDIUM, SIZE_WIDE_LARGE)
    )

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storage = NotesStorage(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val noteId = prefs[NoteWidgetState.KEY_NOTE_ID]
            val isLocked = prefs[NoteWidgetState.KEY_IS_LOCKED] ?: false
            val showOptions = prefs[NoteWidgetState.KEY_SHOW_OPTIONS] ?: false
            val bgOpacity = prefs[NoteWidgetState.KEY_BACKGROUND_OPACITY] ?: 1.0f

            val note = noteId?.let { nId ->
                storage.loadNote(nId)
            }

            GlanceTheme {
                NoteWidgetContent(
                    note = note,
                    isLocked = isLocked,
                    showOptions = showOptions,
                    bgOpacity = bgOpacity,
                    glanceId = id
                )
            }
        }
    }
}
