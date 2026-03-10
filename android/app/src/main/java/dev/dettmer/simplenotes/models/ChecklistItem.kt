package dev.dettmer.simplenotes.models

import java.util.UUID

/**
 * Repräsentiert ein einzelnes Item in einer Checkliste
 * 
 * v1.4.0: Checklisten-Feature
 * v1.9.0 (F04): originalOrder für Position-Restore bei Un-check
 * v1.11.0: createdAt für Sort-by-Creation-Date
 * 
 * @property id Eindeutige ID für Sync-Konflikterkennung
 * @property text Der Text des Items
 * @property isChecked Ob das Item abgehakt ist
 * @property order Sortierreihenfolge (0-basiert)
 * @property originalOrder Ursprüngliche Position bei Erstellung — für Restore bei Un-check
 * @property createdAt Erstellungszeitpunkt in ms — für Sort-by-Creation-Date
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    var isChecked: Boolean = false,
    var order: Int = 0,
    val originalOrder: Int = order,  // 🆕 v1.9.0 (F04): Remembers creation position for restore on un-check
    val createdAt: Long = System.currentTimeMillis()  // 🆕 v1.11.0: Timestamp for sort-by-creation-date
) {
    companion object {
        // 🆕 v1.11.0: Monoton steigender Timestamp — verhindert gleiche createdAt-Werte
        // wenn mehrere Items in derselben Millisekunde erstellt werden.
        private var lastCreatedAt = 0L

        /**
         * Erstellt ein neues leeres ChecklistItem
         */
        fun createEmpty(order: Int): ChecklistItem {
            val now = System.currentTimeMillis()
            lastCreatedAt = maxOf(now, lastCreatedAt + 1)
            return ChecklistItem(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                order = order,
                originalOrder = order,
                createdAt = lastCreatedAt
            )
        }
    }
}
