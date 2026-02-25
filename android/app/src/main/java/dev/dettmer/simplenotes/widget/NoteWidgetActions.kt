package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🆕 v1.8.0: ActionParameter Keys für Widget-Interaktionen
 *
 * Shared Keys für alle ActionCallback-Klassen.
 */
object NoteWidgetActionKeys {
    val KEY_NOTE_ID = ActionParameters.Key<String>("noteId")
    val KEY_ITEM_ID = ActionParameters.Key<String>("itemId")
    val KEY_GLANCE_ID = ActionParameters.Key<String>("glanceId")
}

/**
 * 🐛 FIX: Checklist-Item abhaken/enthaken
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 *
 * - Toggelt isChecked im JSON-File
 * - Setzt SyncStatus auf PENDING
 * - Aktualisiert Widget sofort
 */
class ToggleChecklistItemAction : ActionCallback {
    companion object {
        private const val TAG = "ToggleChecklistItem"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val noteId = parameters[NoteWidgetActionKeys.KEY_NOTE_ID] ?: return
        val itemId = parameters[NoteWidgetActionKeys.KEY_ITEM_ID] ?: return

        val storage = NotesStorage(context)
        val note = storage.loadNote(noteId) ?: return

        val updatedItems = note.checklistItems?.map { item ->
            if (item.id == itemId) {
                item.copy(isChecked = !item.isChecked)
            } else item
        }?.run {
            // 🆕 v1.9.0 (F04): Backward compat — old notes (pre-F04) have all originalOrder == 0
            // (Gson default). Only patch if ALL items are 0, meaning F04 was never active for this note.
            // If at least one item has originalOrder != 0, the note was saved with F04 → don't touch.
            val isPreF04Note = all { it.originalOrder == 0 }
            if (isPreF04Note) map { it.copy(originalOrder = it.order) }
            else this
        } ?: return

        // 🆕 v1.8.1 (IMPL_04): Auto-Sort nach Toggle
        // Konsistent mit NoteEditorViewModel.updateChecklistItemChecked
        val sortOption = try {
            note.checklistSortOption?.let { ChecklistSortOption.valueOf(it) }
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) { null }
            ?: ChecklistSortOption.MANUAL

        // 🆕 v1.9.0 (F04): Restore position on un-check using originalOrder
        val sortedItems = if (sortOption == ChecklistSortOption.MANUAL ||
                             sortOption == ChecklistSortOption.UNCHECKED_FIRST) {
            val unchecked = updatedItems.filter { !it.isChecked }.sortedBy { it.originalOrder }
            val checked = updatedItems.filter { it.isChecked }.sortedBy { it.originalOrder }
            (unchecked + checked).mapIndexed { index, item ->
                item.copy(order = index)
            }
        } else {
            updatedItems.mapIndexed { index, item -> item.copy(order = index) }
        }

        val updatedNote = note.copy(
            checklistItems = sortedItems,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )

        storage.saveNote(updatedNote)
        Logger.d(TAG, "Toggled + auto-sorted checklist item '$itemId' in widget")

        // 🐛 FIX: Glance-State ändern um Re-Render zu erzwingen
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NoteWidgetState.KEY_LAST_UPDATED] = System.currentTimeMillis()
        }

        // Widget aktualisieren — Glance erkennt jetzt den State-Change
        NoteWidget().update(context, glanceId)
    }
}

/**
 * 🐛 FIX: Widget sperren/entsperren
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 */
class ToggleLockAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val currentLock = prefs[NoteWidgetState.KEY_IS_LOCKED] ?: false
            prefs[NoteWidgetState.KEY_IS_LOCKED] = !currentLock
            // Options ausblenden nach Toggle
            prefs[NoteWidgetState.KEY_SHOW_OPTIONS] = false
        }

        NoteWidget().update(context, glanceId)
    }
}

/**
 * 🐛 FIX: Optionsleiste ein-/ausblenden
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 */
class ShowOptionsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val currentShow = prefs[NoteWidgetState.KEY_SHOW_OPTIONS] ?: false
            prefs[NoteWidgetState.KEY_SHOW_OPTIONS] = !currentShow
        }

        NoteWidget().update(context, glanceId)
    }
}

/**
 * 🐛 FIX: Widget-Daten neu laden
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Options ausblenden
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NoteWidgetState.KEY_SHOW_OPTIONS] = false
        }

        NoteWidget().update(context, glanceId)
    }
}

/**
 * 🆕 v1.8.0: Widget-Konfiguration öffnen (Reconfigure)
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 * Öffnet die Config-Activity im Reconfigure-Modus.
 */
class OpenConfigAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Options ausblenden
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[NoteWidgetState.KEY_SHOW_OPTIONS] = false
        }
        
        // Config-Activity als Reconfigure öffnen
        val glanceManager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)
        
        val intent = android.content.Intent(context, NoteWidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // 🐛 FIX: Eigener Task, damit finish() nicht die MainActivity zeigt
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
