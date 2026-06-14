package dev.dettmer.simplenotes.storage

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.9.0 (Trash): Unit-Tests für [TrashManager].
 *
 * Nutzt echte [NotesStorage]/[FolderStore]/[PendingServerDeletions] auf einem Temp-Verzeichnis,
 * damit die Storage-Interaktion (Speichern, DeletionTracker, Queue) realistisch geprüft wird.
 * Die [TrashManager.clock] ist gemockt → deterministische 30-Tage-Grenze.
 */
class TrashManagerTest {
    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage
    private lateinit var folderStore: FolderStore
    private lateinit var pendingDeletions: PendingServerDeletions
    private lateinit var localOnlyFolders: MutableSet<String>

    private var fakeNow: Long = 1_000_000_000_000L

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("trash-manager-test").toFile()
        localOnlyFolders = mutableSetOf()
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString("device_id", null) } returns "test-device-id"
            // FolderStore.getLocalOnlyFolderNames() liest dieses Set.
            every { getStringSet(any(), any()) } answers { localOnlyFolders }
        }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns sharedPrefs
        }
        storage = NotesStorage(context)
        folderStore = FolderStore(context)
        pendingDeletions = PendingServerDeletions(context)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun manager() = TrashManager(storage, pendingDeletions, folderStore) { fakeNow }

    private fun note(
        id: String = "n",
        status: SyncStatus = SyncStatus.SYNCED,
        folderName: String? = null,
        trashedAt: Long? = null
    ) = Note(
        id = id,
        title = "T-$id",
        content = "Body",
        deviceId = "dev",
        syncStatus = status,
        folderName = folderName,
        trashedAt = trashedAt
    )

    // ─── moveToTrash status matrix ──────────────────────────────────────────

    @Test fun `moveToTrash synced note sets trashedAt and PENDING`() = runBlocking {
        storage.saveNote(note(id = "a", status = SyncStatus.SYNCED))
        manager().moveToTrash(listOf(storage.loadNote("a")!!))
        val t = storage.loadNote("a")!!
        assertEquals(fakeNow, t.trashedAt)
        assertEquals(fakeNow, t.updatedAt)
        assertEquals(SyncStatus.PENDING, t.syncStatus)
    }

    @Test fun `moveToTrash keeps LOCAL_ONLY status`() = runBlocking {
        storage.saveNote(note(id = "b", status = SyncStatus.LOCAL_ONLY))
        manager().moveToTrash(listOf(storage.loadNote("b")!!))
        val t = storage.loadNote("b")!!
        assertEquals(fakeNow, t.trashedAt)
        assertEquals(SyncStatus.LOCAL_ONLY, t.syncStatus)
    }

    @Test fun `moveToTrash in local-only folder stays local`() = runBlocking {
        localOnlyFolders.add("Secret")
        storage.saveNote(note(id = "c", status = SyncStatus.SYNCED, folderName = "Secret"))
        manager().moveToTrash(listOf(storage.loadNote("c")!!))
        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("c")!!.syncStatus)
    }

    @Test fun `moveToTrash returns originals as undo payload`() = runBlocking {
        val original = note(id = "d", status = SyncStatus.SYNCED)
        storage.saveNote(original)
        val payload = manager().moveToTrash(listOf(storage.loadNote("d")!!))
        assertEquals(1, payload.size)
        assertNull("payload must be pre-trash original", payload.first().trashedAt)
    }

    // ─── restore ────────────────────────────────────────────────────────────

    @Test fun `restore clears trashedAt and bumps to PENDING`() = runBlocking {
        storage.saveNote(note(id = "r", status = SyncStatus.PENDING, folderName = "Work", trashedAt = 500L))
        manager().restore(storage.loadNote("r")!!, knownFolders = listOf("Work"))
        val r = storage.loadNote("r")!!
        assertNull(r.trashedAt)
        assertEquals("Work", r.folderName)
        assertEquals(SyncStatus.PENDING, r.syncStatus)
        assertEquals(fakeNow, r.updatedAt)
    }

    @Test fun `restore falls back to root when folder gone`() = runBlocking {
        storage.saveNote(note(id = "r2", folderName = "Deleted", trashedAt = 500L))
        manager().restore(storage.loadNote("r2")!!, knownFolders = listOf("Other"))
        assertNull(storage.loadNote("r2")!!.folderName)
    }

    @Test fun `restore folder match is case-insensitive`() = runBlocking {
        storage.saveNote(note(id = "r3", folderName = "Work", trashedAt = 500L))
        manager().restore(storage.loadNote("r3")!!, knownFolders = listOf("work"))
        assertEquals("Work", storage.loadNote("r3")!!.folderName)
    }

    // ─── purge queue logic ───────────────────────────────────────────────────

    @Test fun `purge synced note queues server deletion and deletes locally`() = runBlocking {
        storage.saveNote(note(id = "p", status = SyncStatus.SYNCED, folderName = "Work", trashedAt = 500L))
        manager().purge(listOf(storage.loadNote("p")!!))
        assertNull("note removed locally", storage.loadNote("p"))
        val queued = pendingDeletions.getAll()
        assertEquals(1, queued.size)
        assertEquals("p", queued.first().id)
        assertEquals("Work", queued.first().folderName)
    }

    @Test fun `purge LOCAL_ONLY note does not queue server deletion`() = runBlocking {
        storage.saveNote(note(id = "p2", status = SyncStatus.LOCAL_ONLY, trashedAt = 500L))
        manager().purge(listOf(storage.loadNote("p2")!!))
        assertNull(storage.loadNote("p2"))
        assertTrue("no server deletion queued", pendingDeletions.isEmpty())
    }

    @Test fun `purge DELETED_ON_SERVER note does not queue server deletion`() = runBlocking {
        storage.saveNote(note(id = "p3", status = SyncStatus.DELETED_ON_SERVER, trashedAt = 500L))
        manager().purge(listOf(storage.loadNote("p3")!!))
        assertNull(storage.loadNote("p3"))
        assertTrue(pendingDeletions.isEmpty())
    }

    @Test fun `purge note in local-only folder does not queue server deletion`() = runBlocking {
        localOnlyFolders.add("Secret")
        storage.saveNote(note(id = "p4", status = SyncStatus.SYNCED, folderName = "Secret", trashedAt = 500L))
        manager().purge(listOf(storage.loadNote("p4")!!))
        assertNull(storage.loadNote("p4"))
        assertTrue(pendingDeletions.isEmpty())
    }

    // ─── purgeExpired 30-day boundary ────────────────────────────────────────

    @Test fun `purgeExpired removes notes past retention and keeps fresh ones`() = runBlocking {
        val justExpired = fakeNow - Constants.TRASH_RETENTION_MS
        val notYet = fakeNow - Constants.TRASH_RETENTION_MS + 1
        storage.saveNote(note(id = "old", status = SyncStatus.SYNCED, trashedAt = justExpired))
        storage.saveNote(note(id = "young", status = SyncStatus.SYNCED, trashedAt = notYet))
        storage.saveNote(note(id = "active", status = SyncStatus.SYNCED, trashedAt = null))

        val count = manager().purgeExpired()

        assertEquals(1, count)
        assertNull("expired purged", storage.loadNote("old"))
        assertTrue("not-yet-expired kept", storage.loadNote("young") != null)
        assertTrue("active note untouched", storage.loadNote("active") != null)
    }

    // ─── emptyTrash ──────────────────────────────────────────────────────────

    @Test fun `emptyTrash purges all trashed and leaves active notes`() = runBlocking {
        storage.saveNote(note(id = "t1", trashedAt = 100L))
        storage.saveNote(note(id = "t2", trashedAt = 200L))
        storage.saveNote(note(id = "active", trashedAt = null))

        val count = manager().emptyTrash()

        assertEquals(2, count)
        assertNull(storage.loadNote("t1"))
        assertNull(storage.loadNote("t2"))
        assertTrue(storage.loadNote("active") != null)
    }
}
