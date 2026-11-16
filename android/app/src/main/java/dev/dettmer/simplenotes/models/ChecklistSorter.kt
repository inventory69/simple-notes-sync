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
        // Unterpunkte: sortiert werden Gruppen nach ihrem Oberpunkt, Unterpunkte bleiben in ihrer
        // Gruppe in ihrer Reihenfolge (ChecklistNesting). Flache Listen = Gruppen der Größe 1.
        val groups = ChecklistNesting.groups(ChecklistNesting.normalize(items))
        val open = groups.filter { !it.first().isChecked }
        val done = groups.filter { it.first().isChecked }
        val sorted = when (option) {
            ChecklistSortOption.MANUAL -> {
                // v1.9.0 (F04): Sort by originalOrder to restore un-checked item's original position
                open.sortedBy { it.first().originalOrder } + done.sortedBy { it.first().originalOrder }
            }
            ChecklistSortOption.UNCHECKED_FIRST -> {
                // Stable filter: keep current relative order within each group.
                // After any previous sort the list is [unchecked 0..k, checked k+1..n], so:
                //   check   → item was at p≤k, appears first in checked group (top after separator)
                //   uncheck → item was at q>k, appears last in unchecked group (bottom before separator)
                open + done
            }
            ChecklistSortOption.CREATION_DATE -> {
                // v1.11.0: Unchecked oldest-first, then checked oldest-first
                open.sortedBy { it.first().createdAt } + done.sortedBy { it.first().createdAt }
            }
            ChecklistSortOption.CREATION_DATE_DESC -> {
                // v1.11.0: Unchecked newest-first, then checked newest-first
                open.sortedByDescending { it.first().createdAt } + done.sortedByDescending { it.first().createdAt }
            }
            ChecklistSortOption.CHECKED_FIRST -> done + open
            ChecklistSortOption.ALPHABETICAL_ASC ->
                groups.sortedBy { it.first().text.lowercase() }
            ChecklistSortOption.ALPHABETICAL_DESC ->
                groups.sortedByDescending { it.first().text.lowercase() }
        }
        return sorted.flatten().mapIndexed { index, item -> item.copy(order = index) }
    }
}
