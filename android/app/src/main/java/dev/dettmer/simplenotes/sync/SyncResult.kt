package dev.dettmer.simplenotes.sync

/**
 * Ergebnis eines Sync-Vorgangs
 * 
 * v1.7.0: Initial
 * v1.8.0: deletedOnServerCount hinzugefügt
 */
data class SyncResult(
    val isSuccess: Boolean,
    val syncedCount: Int = 0,
    val conflictCount: Int = 0,
    val deletedOnServerCount: Int = 0,  // 🆕 v1.8.0
    val errorMessage: String? = null
) {
    val hasConflicts: Boolean get() = conflictCount > 0
    val hasServerDeletions: Boolean get() = deletedOnServerCount > 0  // 🆕 v1.8.0
}
