package dev.dettmer.simplenotes.ui.main.components

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistSortOption

/**
 * 🆕 v1.8.1 (IMPL_03): Helper-Funktionen für die Checklisten-Vorschau in Main Activity.
 *
 * Stellt sicher, dass die Sortierung aus dem Editor konsistent
 * in allen Preview-Components (NoteCard, NoteCardCompact, NoteCardGrid) 
 * angezeigt wird.
 */

/**
 * Sortiert Checklist-Items für die Vorschau basierend auf der
 * gespeicherten Sortier-Option.
 */
fun sortChecklistItemsForPreview(
    items: List<ChecklistItem>,
    sortOptionName: String?
): List<ChecklistItem> {
    val sortOption = try {
        sortOptionName?.let { ChecklistSortOption.valueOf(it) }
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        null
    } ?: ChecklistSortOption.MANUAL

    return when (sortOption) {
        ChecklistSortOption.MANUAL,
        ChecklistSortOption.UNCHECKED_FIRST ->
            items.sortedBy { it.isChecked }

        ChecklistSortOption.CHECKED_FIRST ->
            items.sortedByDescending { it.isChecked }

        ChecklistSortOption.ALPHABETICAL_ASC ->
            items.sortedBy { it.text.lowercase() }

        ChecklistSortOption.ALPHABETICAL_DESC ->
            items.sortedByDescending { it.text.lowercase() }

        ChecklistSortOption.CREATION_DATE -> {
            // 🆕 v1.11.0: Unchecked first, then checked — both sorted by creation timestamp (ascending)
            val unchecked = items.filter { !it.isChecked }.sortedBy { it.createdAt }
            val checked = items.filter { it.isChecked }.sortedBy { it.createdAt }
            unchecked + checked
        }

        ChecklistSortOption.CREATION_DATE_DESC -> {
            // 🆕 v1.11.0: Unchecked first, then checked — both sorted by creation timestamp (descending)
            val unchecked = items.filter { !it.isChecked }.sortedByDescending { it.createdAt }
            val checked = items.filter { it.isChecked }.sortedByDescending { it.createdAt }
            unchecked + checked
        }
    }
}

/**
 * Generiert den Vorschau-Text für eine Checkliste mit korrekter
 * Sortierung und passenden Emojis.
 *
 * @param items Die Checklisten-Items
 * @param sortOptionName Der Name der ChecklistSortOption (oder null für MANUAL)
 * @return Formatierter Preview-String mit Emojis und Zeilenumbrüchen
 *
 * 🆕 v1.8.1 (IMPL_06): Emoji-Änderung (☑️ statt ✅ für checked items)
 */
fun generateChecklistPreview(
    items: List<ChecklistItem>,
    sortOptionName: String?
): String {
    val sorted = sortChecklistItemsForPreview(items, sortOptionName)
    return sorted.joinToString("\n") { item ->
        val prefix = if (item.isChecked) "☑️" else "☐"
        "$prefix ${item.text}"
    }
}
