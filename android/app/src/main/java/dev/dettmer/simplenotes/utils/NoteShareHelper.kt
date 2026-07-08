package dev.dettmer.simplenotes.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState

/**
 * 🆕 v1.10.0-Papa: Shared formatting utilities for calendar export, share, and PDF features.
 *
 * Provides consistent note content formatting across all export paths.
 * Used by NoteEditorViewModel (calendar/share events) and PdfExporter (PDF generation).
 */
object NoteShareHelper {
    /**
     * Formats note content as plain text for sharing/calendar description.
     *
     * - TEXT notes: Returns content as-is (trimmed).
     * - CHECKLIST notes: Formats each item with bullet symbols.
     *   Checked items:   "✓ Item text"
     *   Unchecked items: "• Item text"
     *   Empty items are filtered out.
     *
     * @param noteType The type of the note
     * @param textContent The text content (for TEXT notes)
     * @param checklistItems The checklist items (for CHECKLIST notes)
     * @return Formatted plain-text string
     */
    fun formatAsPlainText(noteType: NoteType, textContent: String, checklistItems: List<ChecklistItemState>): String {
        return when (noteType) {
            NoteType.TEXT -> textContent.trim()
            NoteType.CHECKLIST -> {
                checklistItems
                    .filter { it.text.isNotBlank() }
                    .sortedBy { it.order }
                    .joinToString("\n") { item ->
                        if (item.isChecked) "✓ ${item.text}" else "• ${item.text}"
                    }
            }
        }
    }

    /**
     * Formats checklist items for PDF rendering with structured data.
     *
     * Returns a list of pairs: (text, isChecked) — filtered and sorted.
     * Used by PdfExporter for rendering individual lines with checkbox symbols.
     *
     * @param checklistItems The raw checklist items from the editor
     * @return List of (text, isChecked) pairs, sorted by order
     */
    fun formatChecklistForPdf(checklistItems: List<ChecklistItemState>): List<Pair<String, Boolean>> {
        return checklistItems
            .filter { it.text.isNotBlank() }
            .sortedBy { it.order }
            .map { it.text to it.isChecked }
    }

    /**
     * 🆕 Bild-Attachments: Löst `.assets/<name>`-Bildlinks in [textContent] zu teilbaren
     * `content://`-URIs via FileProvider auf (für `ACTION_SEND_MULTIPLE`). Fehlende Assets
     * werden übersprungen statt den Share-Vorgang abzubrechen.
     */
    fun resolveShareableImageUris(context: Context, textContent: String): List<Uri> {
        val assetStore = AssetStore(context)
        return AssetReferences.extractAssetNames(textContent).mapNotNull { name ->
            val file = assetStore.getAssetFile(name)
            if (!file.exists()) return@mapNotNull null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
