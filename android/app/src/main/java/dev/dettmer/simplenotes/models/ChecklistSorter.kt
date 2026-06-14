package dev.dettmer.simplenotes.models

/**
 * Shared checklist sorting utility — single source of truth for toggle-based sorting.
 *
 * Used by both [dev.dettmer.simplenotes.widget.NoteWidgetActions.ToggleChecklistItemAction]
 * and [dev.dettmer.simplenotes.ui.editor.NoteEditorViewModel] to ensure consistent
 * sort behavior across all seven sort options after a checklist item toggle.
 *
 * NOTE: This logic mirrors the private sortChecklistItems(items) in NoteEditorViewModel
 * (the toggle path). It does NOT mirror the public sortChecklistItems(option) variant,
 * which resets originalOrder — that variant is only used on explicit sort-option changes.
 *
 * v2.3.0: Audit 3-01, 12-01
 */
object ChecklistSorter {
    /**
     * Sorts [items] according to [option], identical to the toggle path in NoteEditorViewModel.
     *
     * Returns a new list with [ChecklistItem.order] reassigned to 0-based sequential indices.
     */
    fun sort(items: List<ChecklistItem>, option: ChecklistSortOption): List<ChecklistItem> {
        val sorted = when (option) {
            ChecklistSortOption.MANUAL -> {
                // v1.9.0 (F04): Sort by originalOrder to restore un-checked item's original position
                val unchecked = items.filter { !it.isChecked }.sortedBy { it.originalOrder }
                val checked = items.filter { it.isChecked }.sortedBy { it.originalOrder }
                unchecked + checked
            }
            ChecklistSortOption.UNCHECKED_FIRST -> {
                // Stable filter: keep current relative order within each group.
                // After any previous sort the list is [unchecked 0..k, checked k+1..n], so:
                //   check   → item was at p≤k, appears first in checked group (top after separator)
                //   uncheck → item was at q>k, appears last in unchecked group (bottom before separator)
                val unchecked = items.filter { !it.isChecked }
                val checked = items.filter { it.isChecked }
                unchecked + checked
            }
            ChecklistSortOption.CREATION_DATE -> {
                // v1.11.0: Unchecked oldest-first, then checked oldest-first
                val unchecked = items.filter { !it.isChecked }.sortedBy { it.createdAt }
                val checked = items.filter { it.isChecked }.sortedBy { it.createdAt }
                unchecked + checked
            }
            ChecklistSortOption.CREATION_DATE_DESC -> {
                // v1.11.0: Unchecked newest-first, then checked newest-first
                val unchecked = items.filter { !it.isChecked }.sortedByDescending { it.createdAt }
                val checked = items.filter { it.isChecked }.sortedByDescending { it.createdAt }
                unchecked + checked
            }
            ChecklistSortOption.CHECKED_FIRST ->
                items.sortedByDescending { it.isChecked }
            ChecklistSortOption.ALPHABETICAL_ASC ->
                items.sortedBy { it.text.lowercase() }
            ChecklistSortOption.ALPHABETICAL_DESC ->
                items.sortedByDescending { it.text.lowercase() }
        }
        return sorted.mapIndexed { index, item -> item.copy(order = index) }
    }
}
