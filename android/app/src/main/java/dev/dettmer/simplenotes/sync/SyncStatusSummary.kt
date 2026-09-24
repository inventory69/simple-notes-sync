package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.Constants

/** 🆕 v2.19.0: Notiz, die der Sync-Status-Dialog verlinkt. */
data class NoteRef(val id: String, val title: String)

/**
 * 🆕 v2.19.0: Alles, was der Sync-Status-Dialog und der Badge am „?"-Icon brauchen.
 *
 * Rein und ohne Android, damit die Regeln testbar bleiben. Gebaut wird es in `MainViewModel.refreshSyncStatus`.
 */
data class SyncStatusSummary(
    val state: State,
    val lastSuccessAt: Long, // 0 = noch nie
    val lastError: String?, // nur wenn neuer als der letzte Erfolg
    val conflicts: List<NoteRef>,
    val pendingCount: Int,
    val exportProblems: ExportProblems?,
    val markdownNotes: List<NoteRef>,
    val badgeCount: Int
) {
    /**
     * Priorität von oben nach unten: der schwerste Zustand gewinnt.
     * STALE = Fehler und über [Constants.SYNC_WARNING_THRESHOLD_MS] ohne Erfolg, gleiche Regel wie
     * `SyncWorker.checkAndShowSyncWarning`. Nie am Alter allein, sonst Fehlalarm, wenn einfach niemand synct.
     */
    enum class State { STALE, FAILED, ATTENTION, PENDING, OK }

    companion object {
        val EMPTY = SyncStatusSummary(State.OK, 0L, null, emptyList(), 0, null, emptyList(), 0)

        @Suppress("LongParameterList") // Alle Eingaben kommen aus verschiedenen Quellen, ein Wrapper wäre reine Zeremonie
        fun from(
            notes: List<Note>,
            localOnlyFolders: Set<String>,
            exportProblems: ExportProblems?,
            lastSuccessAt: Long,
            lastError: String?,
            lastErrorAt: Long,
            now: Long
        ): SyncStatusSummary {
            // Settings-Sync umgeht den SyncStateManager und löscht den Fehler nicht. Der Zeitvergleich fängt das ab.
            val error = lastError?.takeIf { lastErrorAt > lastSuccessAt }
            val conflicts = notes.filter { it.syncStatus == SyncStatus.CONFLICT }.map { it.toRef() }
            // Wie WebDavSyncService.hasUnsyncedChanges: Local-only-Ordner zählen nicht, Vergleich in Kleinbuchstaben.
            val localOnly = localOnlyFolders.map { it.lowercase() }.toSet()
            val pendingCount = notes.count {
                (it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.LOCAL_ONLY) &&
                    it.folderName?.lowercase() !in localOnly
            }
            // Fehlende oder getrashte Notizen im Editor zu öffnen hilft niemandem. Der Zähler kommt weiter aus dem Set.
            val byId = notes.associateBy { it.id }
            val markdownNotes = exportProblems?.markdownFailedIds.orEmpty()
                .mapNotNull { byId[it] }
                .filterNot { it.isTrashed }
                .map { it.toRef() }
            // Ein gescheiterter Sync zählt erst, wenn der letzte Erfolg wirklich einen Tag her ist.
            val staleFailure = error != null && now - lastSuccessAt > Constants.SYNC_WARNING_THRESHOLD_MS
            val state = when {
                // „Noch nie" passt nicht zu „länger nicht", wie bei der Benachrichtigung.
                staleFailure && lastSuccessAt > 0 -> State.STALE
                error != null -> State.FAILED
                conflicts.isNotEmpty() || exportProblems != null -> State.ATTENTION
                pendingCount > 0 -> State.PENDING
                else -> State.OK
            }
            val badge = conflicts.size +
                (exportProblems?.let { it.markdownFailedCount + it.assetsFailed } ?: 0) +
                if (staleFailure) 1 else 0
            return SyncStatusSummary(
                state,
                lastSuccessAt,
                error,
                conflicts,
                pendingCount,
                exportProblems,
                markdownNotes,
                badge
            )
        }

        private fun Note.toRef() = NoteRef(id, title)
    }
}
