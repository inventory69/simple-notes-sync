package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Eine offline eingereihte Server-Löschung lief nach einem Wechsel des Sync-Ordners gegen den
 * neuen Ordner: DELETE dort und die ID in dessen `deletions.json`. Sie muss auf ihr Ziel warten.
 */
class WebDavSyncServicePendingDeletionTargetTest {
    private lateinit var h: SyncHarness
    private val gone = Note(title = "Weg", content = "x", deviceId = "test", syncStatus = SyncStatus.SYNCED)

    @Before fun setUp() = runBlocking {
        h = SyncHarness()
        h.dav.putFile("/notes/${gone.id}.json", gone.toJson())
        h.dav.putFile("/other/${gone.id}.json", gone.toJson())
        PendingServerDeletions(h.context).add(listOf(PendingServerDeletions.PendingDeletion(gone.id)))
    }

    @After fun tearDown() = h.close()

    private fun TestScope.service() = h.service(UnconfinedTestDispatcher(testScheduler))

    private fun switchFolder(name: String) = h.prefs.edit().putString(Constants.KEY_SYNC_FOLDER_NAME, name).apply()

    @Test fun `queued deletion waits for its own folder`() = runTest {
        switchFolder("other")

        assertTrue(service().syncNotes(trigger = null).isSuccess)

        assertFalse(h.dav.requests.any { it.startsWith("DELETE") })
        assertTrue(h.dav.exists("/other/${gone.id}.json"))
        assertFalse(h.dav.text("/other/deletions.json").orEmpty().contains(gone.id))
        assertEquals(listOf(gone.id), PendingServerDeletions(h.context).getAll().map { it.id })

        switchFolder("notes")
        assertTrue(service().syncNotes(trigger = null).isSuccess)

        assertFalse(h.dav.exists("/notes/${gone.id}.json"))
        assertTrue(h.dav.text("/notes/deletions.json").orEmpty().contains(gone.id))
        assertEquals(emptyList<PendingServerDeletions.PendingDeletion>(), PendingServerDeletions(h.context).getAll())
    }
}
