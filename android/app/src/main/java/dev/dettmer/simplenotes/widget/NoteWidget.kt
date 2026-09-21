package dev.dettmer.simplenotes.widget

import android.content.Context
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
 * Das Layout richtet sich nach der echten Widget-Größe (`DpSize.toSizeClass()` in
 * [NoteWidgetContent]): nur Titel, Vorschau, scrollbare Liste oder voller Inhalt,
 * je schmal und breit.
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
    /**
     * Issue #154: `SizeMode.Responsive` übersetzte die Komposition **einmal je Breakpoint** und
     * packte alle sieben Varianten in eine RemoteViews. Das war der Multiplikator, der die
     * Transaktion über den Binder-Puffer des Launchers trieb und dort den AppWidget-Host riss.
     * `Exact` rendert genau die Größe, die das Widget gerade hat — dafür bei jeder
     * Größenänderung neu, was billiger ist als der Vorrat für sechs ungenutzte Layouts.
     * Bonus: `LocalSize` liefert jetzt die echte Breite statt des Breakpoints.
     * Gleiches Verfahren wie in [NotesListWidget].
     */
    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storage = NotesStorage(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val noteId = prefs[NoteWidgetState.KEY_NOTE_ID]
            val isLocked = prefs[NoteWidgetState.KEY_IS_LOCKED] ?: false
            val showOptions = prefs[NoteWidgetState.KEY_SHOW_OPTIONS] ?: false
            val bgOpacity = prefs[NoteWidgetState.KEY_BACKGROUND_OPACITY] ?: 1.0f
            val fontSizeScale = prefs[NoteWidgetState.KEY_FONT_SIZE_SCALE] ?: 1.0f

            // Load note synchronously inside provideContent so it re-reads on every
            // widget update (state change). provideContent runs on a Glance SessionWorker
            // thread, not the main thread, so synchronous file I/O is safe here.
            // 🆕 v2.9.0 (Trash): getrashte Notiz wie gelöscht behandeln → „nicht gefunden"-Fallback.
            val note = noteId?.let { storage.loadNoteSync(it) }?.takeIf { it.trashedAt == null }

            GlanceTheme {
                NoteWidgetContent(
                    note = note,
                    isLocked = isLocked,
                    showOptions = showOptions,
                    bgOpacity = bgOpacity,
                    fontSizeScale = fontSizeScale,
                    glanceId = id
                )
            }
        }
    }
}
