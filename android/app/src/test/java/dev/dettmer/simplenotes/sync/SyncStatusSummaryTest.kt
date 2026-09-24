package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.SyncStatusSummary.State
import dev.dettmer.simplenotes.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 🆕 v2.19.0: Regeln hinter Sync-Status-Dialog und Badge. */
class SyncStatusSummaryTest {
    private val now = 10 * Constants.SYNC_WARNING_THRESHOLD_MS

    private fun note(
        id: String,
        status: SyncStatus = SyncStatus.SYNCED,
        folder: String? = null,
        trashedAt: Long? = null
    ) = Note(
        id = id,
        title = "T$id",
        content = "",
        deviceId = "d",
        syncStatus = status,
        folderName = folder,
        trashedAt = trashedAt
    )

    private fun summary(
        notes: List<Note> = emptyList(),
        localOnly: Set<String> = emptySet(),
        export: ExportProblems? = null,
        lastSuccessAt: Long = now - 1000,
        lastError: String? = null,
        lastErrorAt: Long = 0L
    ) = SyncStatusSummary.from(notes, localOnly, export, lastSuccessAt, lastError, lastErrorAt, now)

    @Test
    fun `state priority is STALE over FAILED over ATTENTION over PENDING over OK`() {
        val conflict = note("c", SyncStatus.CONFLICT)
        val pending = note("p", SyncStatus.PENDING)
        assertEquals(State.OK, summary(listOf(note("s"))).state)
        assertEquals(State.PENDING, summary(listOf(pending)).state)
        assertEquals(State.ATTENTION, summary(listOf(pending, conflict)).state)
        assertEquals(
            State.ATTENTION,
            summary(listOf(pending), export = ExportProblems(assetsFailed = 1)).state
        )
        assertEquals(
            State.FAILED,
            summary(listOf(pending, conflict), lastError = "boom", lastErrorAt = now).state
        )
        assertEquals(
            State.STALE,
            summary(listOf(pending, conflict), lastSuccessAt = 1L, lastError = "boom", lastErrorAt = now).state
        )
    }

    @Test
    fun `failed sync turns STALE only after 24 hours and only after a first success`() {
        val threshold = Constants.SYNC_WARNING_THRESHOLD_MS
        assertEquals(State.STALE, summary(lastSuccessAt = now - threshold - 1, lastError = "x", lastErrorAt = now).state)
        assertEquals(State.FAILED, summary(lastSuccessAt = now - threshold + 1, lastError = "x", lastErrorAt = now).state)
        val never = summary(lastSuccessAt = 0L, lastError = "x", lastErrorAt = now)
        assertEquals(State.FAILED, never.state)
        assertEquals(1, never.badgeCount)
        // Alter allein reicht nicht
        assertEquals(State.OK, summary(lastSuccessAt = now - threshold - 1).state)
    }

    @Test
    fun `error older than the last success does not count`() {
        val s = summary(lastSuccessAt = now - 10, lastError = "old", lastErrorAt = now - 20)
        assertEquals(State.OK, s.state)
        assertNull(s.lastError)
    }

    @Test
    fun `badge counts a failed sync only after 24 hours without success`() {
        val fresh = summary(lastSuccessAt = now - 1000, lastError = "x", lastErrorAt = now)
        assertEquals(0, fresh.badgeCount)
        val stale = summary(
            lastSuccessAt = now - Constants.SYNC_WARNING_THRESHOLD_MS - 1,
            lastError = "x",
            lastErrorAt = now
        )
        assertEquals(1, stale.badgeCount)
    }

    @Test
    fun `badge adds conflicts and export problems`() {
        val export = ExportProblems(markdownFailedIds = setOf("a", "b"), markdownImportFailed = true, assetsFailed = 2)
        val s = summary(listOf(note("c", SyncStatus.CONFLICT)), export = export)
        assertEquals(1 + 3 + 2, s.badgeCount)
    }

    @Test
    fun `pendingCount ignores local-only folders case-insensitively`() {
        val notes = listOf(
            note("1", SyncStatus.PENDING),
            note("2", SyncStatus.LOCAL_ONLY, folder = "Work"),
            note("3", SyncStatus.PENDING, folder = "private"),
            note("4", SyncStatus.SYNCED)
        )
        assertEquals(2, summary(notes, localOnly = setOf("Private")).pendingCount)
    }

    @Test
    fun `markdownNotes skips missing and trashed notes but the count stays`() {
        val notes = listOf(note("a"), note("t", trashedAt = 1L))
        val export = ExportProblems(markdownFailedIds = setOf("a", "t", "gone"))
        val s = summary(notes, export = export)
        assertEquals(listOf(NoteRef("a", "Ta")), s.markdownNotes)
        assertEquals(3, s.badgeCount)
    }
}
