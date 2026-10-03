package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.ActivityLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Baseline für [SyncHarness]: ein ganzer Sync-Zyklus gegen den Mini-WebDAV. */
class WebDavSyncServiceSyncCycleTest {
    private lateinit var h: SyncHarness

    @Before fun setUp() {
        h = SyncHarness()
    }

    @After fun tearDown() = h.close()

    @Test fun `first sync uploads a pending note and a second sync writes nothing`() = runTest {
        val note = Note(title = "Hallo", content = "Welt", deviceId = "test", syncStatus = SyncStatus.PENDING)
        h.storage.saveNote(note)
        val service = h.service(UnconfinedTestDispatcher(testScheduler))

        val first = service.syncNotes(trigger = null)

        assertTrue(first.isSuccess)
        assertEquals(1, first.syncedCount)
        assertTrue(h.dav.text("/notes/${note.id}.json").orEmpty().contains("\"Welt\""))
        assertEquals(SyncStatus.SYNCED, h.storage.loadNote(note.id)?.syncStatus)
        assertEquals(listOf(ActivityLog.Op.UPLOAD, ActivityLog.Op.SYNC_OK), h.activityLog().map { it.op })

        h.dav.clearRequests()
        val second = service.syncNotes(trigger = null)

        assertTrue(second.isSuccess)
        assertEquals(0, second.syncedCount)
        // Request-Ökonomie: der Leerlauf-Sync kostet genau diese drei (Stand v2.19.0).
        assertEquals(
            listOf("GET /notes/deletions.json", "PROPFIND /notes/", "GET /notes/folders.json"),
            h.dav.requests
        )
    }
}
