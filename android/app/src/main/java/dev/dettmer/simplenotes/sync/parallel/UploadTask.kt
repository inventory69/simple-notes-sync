package dev.dettmer.simplenotes.sync.parallel

import dev.dettmer.simplenotes.models.Note

/**
 * 🆕 v1.9.0: Repräsentiert einen einzelnen Upload-Task
 *
 * @param note Die hochzuladende Notiz
 * @param noteUrl Vollständige URL zur JSON-Datei (z.B. https://server/notes/<uuid>.json)
 * @param markdownExportEnabled Ob Markdown-Export für diese Notiz aktiviert ist
 * @param markdownDirExists Ob notes-md/ Ordner bereits existiert
 */
data class UploadTask(val note: Note, val noteUrl: String, val markdownExportEnabled: Boolean, val markdownDirExists: Boolean)

/**
 * 🆕 v1.9.0: Ergebnis eines einzelnen Uploads
 */
sealed class UploadTaskResult {
    /**
     * Upload erfolgreich
     *
     * @param noteId Die ID der hochgeladenen Notiz
     * @param etag E-Tag vom Server nach Upload (null wenn nicht verfügbar)
     * @param markdownExported 🆕 v1.11.0: Flag ob MD-Export für diese Notiz durchgeführt wurde
     */
    data class Success(
        val noteId: String,
        val etag: String?,
        val markdownExported: Boolean = false // 🆕 v1.11.0: Flag ob MD-Export durchgeführt wurde
    ) : UploadTaskResult()

    /**
     * Upload fehlgeschlagen
     *
     * @param noteId Die ID der nicht hochgeladenen Notiz
     * @param error Der aufgetretene Fehler
     */
    data class Failure(val noteId: String, val error: Throwable) : UploadTaskResult()

    /**
     * 🆕 v2.16.0: Der Server hat die Datei seit dem letzten Sync geändert — das PUT lief mit
     * `If-Match` und kam als `412` zurück. Die lokale Fassung bleibt unangetastet und wird
     * als [dev.dettmer.simplenotes.models.SyncStatus.CONFLICT] markiert, statt die fremde
     * Änderung zu überschreiben.
     *
     * @param noteId Die ID der nicht hochgeladenen Notiz
     */
    data class Conflict(val noteId: String) : UploadTaskResult()

    /**
     * Upload übersprungen (z.B. Content unverändert)
     *
     * @param noteId Die ID der übersprungenen Notiz
     * @param reason Grund für das Überspringen
     */
    data class Skipped(val noteId: String, val reason: String) : UploadTaskResult()
}
