package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ein Move-Eintrag räumt nur den alten Pfad auf. Er darf die Server-Kopie nicht löschen, solange
 * die Notiz am neuen Platz nicht oben ist, und nicht, wenn der alte Platz wieder der aktuelle ist.
 */
class WebDavSyncServiceMoveCleanupTest {
    private lateinit var h: SyncHarness
    private val note = Note(title = "Umzug", content = "x", deviceId = "test", syncStatus = SyncStatus.SYNCED)
    private val oldPath get() = "/notes/work/${note.id}.json"

    @Before fun setUp() = runBlocking {
        h = SyncHarness()
        h.dav.putFile(oldPath, note.copy(folderName = "work").toJson())
        PendingServerDeletions(h.context).add(listOf(PendingServerDeletions.PendingDeletion(note.id, "work", isMove = true)))
    }

    @After fun tearDown() = h.close()

    private fun TestScope.service() = h.service(UnconfinedTestDispatcher(testScheduler))

    private suspend fun pending() = PendingServerDeletions(h.context).getAll().map { it.id }

    // Ordner gelöscht ("Notizen behalten"), dann Undo: die Notiz liegt wieder in "work".
    @Test fun `a note moved back keeps its file and drops the queued cleanup`() = runTest {
        h.storage.saveNote(note.copy(folderName = "work", syncStatus = SyncStatus.PENDING))

        assertTrue(service().syncNotes(trigger = null).isSuccess)

        assertTrue(h.dav.exists(oldPath))
        assertEquals(SyncStatus.SYNCED, h.storage.loadNote(note.id)!!.syncStatus)
        assertEquals(emptyList<String>(), pending())
    }

    // Upload an den neuen Platz gescheitert (412, Konflikt): die alte Kopie ist die einzige.
    @Test fun `the old copy stays while the moved note is not on the server`() = runTest {
        h.storage.saveNote(note.copy(folderName = null, syncStatus = SyncStatus.CONFLICT))

        assertTrue(service().syncNotes(trigger = null).isSuccess)

        assertTrue(h.dav.exists(oldPath))
        assertEquals(listOf(note.id), pending())
    }
}
