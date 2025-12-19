package dev.dettmer.simplenotes.sync

data class SyncResult(
    val isSuccess: Boolean,
    val syncedCount: Int = 0,
    val conflictCount: Int = 0,
    val errorMessage: String? = null
) {
    val hasConflicts: Boolean
        get() = conflictCount > 0
}
