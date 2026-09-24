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
    val trashedFromServerCount: Int = 0, // Notizen, die vom Server bereits mit trashedAt ankamen
    val foldersChanged: Boolean = false, // 🆕 v2.7.0 (Folders): Ordner-Metadaten haben sich geändert
    val foldersReconciled: Boolean = false, // 🆕 v2.7.2: folderName einer Notiz an Server-Pfad geheilt
    val restoredCount: Int = 0, // 🆕 Issue #128: falsches DELETED_ON_SERVER zurückgenommen
    val deletionDetectionSkipped: Boolean = false, // 🆕 Issue #128: Listing unvollständig → nichts getrasht
    // 🆕 v2.19.0: offene MD-Kopien, Ist-Zustand wie conflictCount (Notiz-JSON trotzdem synchron)
    val markdownFailedCount: Int = 0,
    val markdownImportFailed: Boolean = false, // 🆕 v2.19.0: MD-Auto-Import abgebrochen
    val assetFailedCount: Int = 0, // 🆕 v2.19.0: Bilder nicht übertragen
    val errorMessage: String? = null,
    val infoMessage: String? = null // 🆕 v1.9.0 Issue #21: Non-error status info
) {
    val hasConflicts: Boolean get() = conflictCount > 0
    val hasServerDeletions: Boolean get() = deletedOnServerCount > 0 // 🆕 v1.8.0
    val hasPurgedFromServer: Boolean get() = purgedFromServerCount > 0
    val hasExportProblems: Boolean get() = markdownFailedCount + assetFailedCount > 0 || markdownImportFailed

    /** 🆕 v2.19.0: Warn-Banner statt Erfolgs-Stil — Notizen synchron, aber etwas braucht Aufmerksamkeit. */
    val isWarning: Boolean get() = hasConflicts || hasExportProblems
}
