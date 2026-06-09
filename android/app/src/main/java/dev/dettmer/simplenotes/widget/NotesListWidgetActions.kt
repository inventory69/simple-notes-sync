package dev.dettmer.simplenotes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.utils.Logger

class RefreshNotesListAction : ActionCallback {
    companion object {
        private const val TAG = "RefreshNotesListAction"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NotesListWidgetState.KEY_LAST_UPDATED] = System.currentTimeMillis()
            prefs[NotesListWidgetState.KEY_FAB_EXPANDED] = false
        }
        NotesListWidget().update(context, glanceId)
        Logger.d(TAG, "Notes list widget refreshed")
    }
}

class OpenNotesListConfigAction : ActionCallback {
    companion object {
        private const val TAG = "OpenNotesListConfigAction"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val glanceManager = GlanceAppWidgetManager(context)
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)

        val intent = Intent(context, NotesListWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        Logger.d(TAG, "Opened config for notes list widget $appWidgetId")
    }
}

class ToggleFabExpandedAction : ActionCallback {
    companion object {
        private const val TAG = "ToggleFabExpandedAction"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NotesListWidgetState.KEY_FAB_EXPANDED] = !(prefs[NotesListWidgetState.KEY_FAB_EXPANDED] ?: false)
        }
        NotesListWidget().update(context, glanceId)
        Logger.d(TAG, "FAB expanded toggled")
    }
}

class CreateNoteAndCollapseAction : ActionCallback {
    companion object {
        private const val TAG = "CreateNoteAndCollapseAction"
        val KEY_NOTE_TYPE = ActionParameters.Key<String>("note_type")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteTypeName = parameters[KEY_NOTE_TYPE] ?: NoteType.TEXT.name
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NotesListWidgetState.KEY_FAB_EXPANDED] = false
        }
        NotesListWidget().update(context, glanceId)
        val intent = Intent(context, ComposeNoteEditorActivity::class.java).apply {
            putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_TYPE, noteTypeName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Logger.d(TAG, "Creating $noteTypeName note from widget FAB")
    }
}
