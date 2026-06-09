package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🔧 v2.3.0: Shared utility to refresh all active NoteWidget instances.
 * Extracted from NoteEditorViewModel, ComposeMainActivity, and SyncWorker (REF-027).
 *
 * Audit: 3-05
 */
object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdateHelper"

    suspend fun refreshAllNoteWidgets(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(NoteWidget::class.java)
            if (ids.isEmpty()) {
                Logger.d(TAG, "No note widgets active — skipping refresh")
                return
            }
            Logger.d(TAG, "Refreshing ${ids.size} note widget(s)")
            ids.forEach { id ->
                // Touch the last-updated timestamp to force Glance recomposition.
                // Without a state change, provideContent won't re-execute even
                // though the underlying note file has changed on disk.
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[NoteWidgetState.KEY_LAST_UPDATED] = System.currentTimeMillis()
                    }
                }
                NoteWidget().update(context, id)
            }
            Logger.d(TAG, "Widget refresh completed")
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to refresh note widgets: ${e.message}")
        }
    }

    suspend fun refreshAllNotesListWidgets(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(NotesListWidget::class.java)
            if (ids.isEmpty()) {
                Logger.d(TAG, "No notes-list widgets active — skipping refresh")
                return
            }
            Logger.d(TAG, "Refreshing ${ids.size} notes-list widget(s)")
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[NotesListWidgetState.KEY_LAST_UPDATED] = System.currentTimeMillis()
                    }
                }
                NotesListWidget().update(context, id)
            }
            Logger.d(TAG, "Notes-list widget refresh completed")
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to refresh notes-list widgets: ${e.message}")
        }
    }

    suspend fun refreshAllWidgets(context: Context) {
        refreshAllNoteWidgets(context)
        refreshAllNotesListWidgets(context)
    }
}
