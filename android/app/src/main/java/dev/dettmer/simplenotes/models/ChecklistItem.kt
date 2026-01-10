package dev.dettmer.simplenotes.models

import java.util.UUID

/**
 * Repräsentiert ein einzelnes Item in einer Checkliste
 * 
 * v1.4.0: Checklisten-Feature
 * 
 * @property id Eindeutige ID für Sync-Konflikterkennung
 * @property text Der Text des Items
 * @property isChecked Ob das Item abgehakt ist
 * @property order Sortierreihenfolge (0-basiert)
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    var isChecked: Boolean = false,
    var order: Int = 0
) {
    companion object {
        /**
         * Erstellt ein neues leeres ChecklistItem
         */
        fun createEmpty(order: Int): ChecklistItem {
            return ChecklistItem(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                order = order
            )
        }
    }
}
