package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.ActivityLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Request-Ökonomie: der Leerlauf-Sync kostet genau diese vier. Der Marker-GET kam mit
        // v2.20.0 (E2EE-Slice 1) dazu und bleibt immer der erste.
        assertEquals(
            listOf("GET /notes-e2ee/e2ee.json", "GET /notes/deletions.json", "PROPFIND /notes/", "GET /notes/folders.json"),
            h.dav.requests
        )
    }

    @Test fun `a folder missing on the server is recreated and uploaded in the same sync`() = runTest {
        val old = Note(title = "Alt", content = "a", deviceId = "test", syncStatus = SyncStatus.PENDING)
        h.storage.saveNote(old)
        h.service(UnconfinedTestDispatcher(testScheduler)).syncNotes(trigger = null) // setzt das notes/-Flag
        h.dav.wipe()
        val note = Note(title = "Neu", content = "n", deviceId = "test", syncStatus = SyncStatus.PENDING)
        h.storage.saveNote(note)

        // Neuer Service: der alte hält die Notizliste im Cache und sähe „Neu“ nicht.
        val result = h.service(UnconfinedTestDispatcher(testScheduler)).syncNotes(trigger = null)

        assertTrue(result.isSuccess)
        assertFalse(result.isWarning)
        assertEquals(0, result.uploadFailedCount)
        assertTrue(h.dav.exists("/notes/${note.id}.json"))
        assertEquals(SyncStatus.SYNCED, h.storage.loadNote(note.id)?.syncStatus)
        // Die schon synchronisierte Notiz kommt wieder hoch, statt als serverseitig gelöscht im Papierkorb zu landen.
        assertTrue(h.dav.exists("/notes/${old.id}.json"))
        assertEquals(SyncStatus.SYNCED, h.storage.loadNote(old.id)?.syncStatus)
        assertEquals(0, result.deletedOnServerCount)
    }

    @Test fun `a note the server keeps rejecting ends the sync as a warning`() = runTest {
        val note = Note(title = "Voll", content = "x", deviceId = "test", syncStatus = SyncStatus.PENDING)
        h.storage.saveNote(note)
        h.dav.respond("/notes/${note.id}.json", 507)

        val result = h.service(UnconfinedTestDispatcher(testScheduler)).syncNotes(trigger = null)

        assertTrue(result.isSuccess)
        assertTrue(result.isWarning)
        assertEquals(1, result.uploadFailedCount)
        assertEquals(SyncStatus.PENDING, h.storage.loadNote(note.id)?.syncStatus)
    }
}
