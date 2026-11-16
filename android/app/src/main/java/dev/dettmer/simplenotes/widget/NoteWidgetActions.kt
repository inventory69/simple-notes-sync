package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.dettmer.simplenotes.models.ChecklistNesting
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.ChecklistSorter
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncScheduler
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
 * - Setzt SyncStatus auf PENDING (bzw. LOCAL_ONLY in Nur-lokal-Ordnern)
 * - Aktualisiert alle Widgets sofort (auch Listen-Widgets und weitere Widgets derselben Notiz)
 */
class ToggleChecklistItemAction : ActionCallback {
    companion object {
        private const val TAG = "ToggleChecklistItem"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NoteWidgetActionKeys.KEY_NOTE_ID] ?: return
        val itemId = parameters[NoteWidgetActionKeys.KEY_ITEM_ID] ?: return

        // Lesen und Schreiben in einem Zug: zwei schnelle Tipps (oder ein Tipp während der Editor
        // speichert) laden sonst beide denselben Stand, und der zweite überschreibt den ersten.
        val folderStore = FolderStore(context)
        NotesStorage(context).updateNote(noteId) { note ->
            val status = if (folderStore.isLocalOnly(note.folderName)) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING
            toggled(note, itemId, status)
        } ?: return
        Logger.d(TAG, "Toggled + auto-sorted checklist item '$itemId' in widget")

        SyncScheduler(context).triggerOnSaveSync(reason = "widgetToggle")
        // Bumpt KEY_LAST_UPDATED aller Widgets, sonst rendern Listen-Widgets den alten Stand
        WidgetUpdateHelper.refreshAllWidgets(context)
    }

    /** [note] mit umgeschaltetem Eintrag [itemId], oder null wenn es ihn (oder die Checkliste) nicht gibt. */
    private fun toggled(note: Note, itemId: String, status: SyncStatus): Note? {
        val items = note.checklistItems ?: return null
        val toggled = items.find { it.id == itemId } ?: return null
        // Unterpunkte: dieselben Gruppenregeln wie im Editor (Oberpunkt abhaken hakt Unterpunkte mit ab)
        val updatedItems = ChecklistNesting.toggle(items.sortedBy { it.order }, itemId, !toggled.isChecked).run {
            // 🆕 v1.9.0 (F04): Backward compat — old notes (pre-F04) have all originalOrder == 0
            // (Gson default). Only patch if ALL items are 0, meaning F04 was never active for this note.
            // If at least one item has originalOrder != 0, the note was saved with F04 → don't touch.
            val isPreF04Note = all { it.originalOrder == 0 }
            if (isPreF04Note) {
                map { it.copy(originalOrder = it.order) }
            } else {
                this
            }
        }

        // 🆕 v1.8.1 (IMPL_04): Auto-Sort nach Toggle
        // Konsistent mit NoteEditorViewModel.updateChecklistItemChecked
        val sortOption = try {
            note.checklistSortOption?.let { ChecklistSortOption.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            Logger.d(TAG, "Unknown checklistSortOption '${note.checklistSortOption}': ${e.message}")
            null
        }
            ?: ChecklistSortOption.MANUAL

        return note.copy(
            // v2.3.0 (FIX-004): Delegate to ChecklistSorter for all sort options
            checklistItems = ChecklistSorter.sort(updatedItems, sortOption),
            updatedAt = System.currentTimeMillis(),
            syncStatus = status
        ).withContentTouched(note)
    }
}

/**
 * 🐛 FIX: Widget sperren/entsperren
 *
 * Top-Level-Klasse (statt nested) für Class.forName()-Kompatibilität.
 */
class ToggleLockAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
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
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
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
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
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
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
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
