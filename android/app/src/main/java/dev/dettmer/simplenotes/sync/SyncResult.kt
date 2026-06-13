package dev.dettmer.simplenotes.sync

/**
 * Ergebnis eines Sync-Vorgangs
 *
 * v1.7.0: Initial
 * v1.8.0: deletedOnServerCount hinzugefügt
 * v1.9.0: infoMessage hinzugefügt (Issue #21)
 */
data class SyncResult(
    val isSuccess: Boolean,
    val syncedCount: Int = 0,
    val conflictCount: Int = 0,
    val deletedOnServerCount: Int = 0, // 🆕 v1.8.0
    val purgedFromServerCount: Int = 0, // 🆕 v2.9.x (Trash): via „Papierkorb leeren" ausgelöste Server-Löschungen
    val foldersChanged: Boolean = false, // 🆕 v2.7.0 (Folders): Ordner-Metadaten haben sich geändert
    val foldersReconciled: Boolean = false, // 🆕 v2.7.2: folderName einer Notiz an Server-Pfad geheilt
    val errorMessage: String? = null,
    val infoMessage: String? = null // 🆕 v1.9.0 Issue #21: Non-error status info
) {
    val hasConflicts: Boolean get() = conflictCount > 0
    val hasServerDeletions: Boolean get() = deletedOnServerCount > 0 // 🆕 v1.8.0
    val hasPurgedFromServer: Boolean get() = purgedFromServerCount > 0
}
