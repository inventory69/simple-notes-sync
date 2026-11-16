package dev.dettmer.simplenotes.storage

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.PendingServerDeletions
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
 * [NoteMutations] ändert den Stand auf der Platte, nicht die übergebene Kopie aus der Liste.
 * Echte [NotesStorage]/[FolderStore]/[PendingServerDeletions] auf einem Temp-Verzeichnis.
 */
class NoteMutationsTest {
    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage
    private lateinit var pendingDeletions: PendingServerDeletions
    private lateinit var mutations: NoteMutations
    private val localOnlyFolders = mutableSetOf<String>()
    private val now = 1_000_000L

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("note-mutations-test").toFile()
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString("device_id", null) } returns "test-device-id"
            every { getStringSet(any(), any()) } answers { localOnlyFolders }
        }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns sharedPrefs
        }
        storage = NotesStorage(context)
        pendingDeletions = PendingServerDeletions(context)
        mutations = NoteMutations(storage, FolderStore(context), pendingDeletions, clock = { now })
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun note(id: String, status: SyncStatus = SyncStatus.SYNCED, folderName: String? = null) =
        Note(id = id, title = "T-$id", content = "Body", deviceId = "dev", syncStatus = status, folderName = folderName)

    @Test fun `setColor keeps content saved after the list was loaded`() = runBlocking {
        storage.saveNote(note("a"))
        storage.updateNote("a") { it.copy(content = "saved by the editor") }

        mutations.setColor(listOf("a"), "#F28B82")

        val a = storage.loadNote("a")!!
        assertEquals("saved by the editor", a.content)
        assertEquals("#F28B82", a.color)
        assertEquals(now, a.updatedAt)
        assertEquals(SyncStatus.PENDING, a.syncStatus)
    }

    @Test fun `an edit in a local-only folder stays LOCAL_ONLY`() = runBlocking {
        localOnlyFolders += "Private"
        storage.saveNote(note("p", SyncStatus.LOCAL_ONLY, folderName = "Private"))

        mutations.setPinned(listOf("p"), true)

        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("p")!!.syncStatus)
    }

    @Test fun `restoreArchived only resets archivedAt`() = runBlocking {
        storage.saveNote(note("r"))
        val originals = mutations.setArchived(listOf("r"), archived = true)
        assertEquals(now, storage.loadNote("r")!!.archivedAt)
        storage.updateNote("r") { it.copy(title = "renamed meanwhile") }

        mutations.restoreArchived(originals)

        val r = storage.loadNote("r")!!
        assertNull(r.archivedAt)
        assertEquals("renamed meanwhile", r.title)
    }

    @Test fun `missing notes are skipped`() = runBlocking {
        assertTrue(mutations.setArchived(listOf("gone"), archived = true).isEmpty())
        assertNull(storage.loadNote("gone"))
    }

    @Test fun `makeLocalOnly converts notes and reports the server-resident ones`() = runBlocking {
        storage.saveNote(note("s", SyncStatus.SYNCED, "Work"))
        storage.saveNote(note("l", SyncStatus.LOCAL_ONLY, "Work"))
        storage.saveNote(note("d", SyncStatus.DELETED_ON_SERVER, "Work"))
        storage.saveNote(note("o", SyncStatus.SYNCED, "Other"))

        val deletions = mutations.makeLocalOnly(setOf("Work"))

        assertEquals(listOf("s"), deletions.map { it.id })
        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("s")!!.syncStatus)
        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("d")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, storage.loadNote("o")!!.syncStatus)
    }

    @Test fun `includeInSync marks LOCAL_ONLY notes PENDING and drops their queued deletions`() = runBlocking {
        storage.saveNote(note("x", SyncStatus.LOCAL_ONLY, "Work"))
        storage.saveNote(note("y", SyncStatus.SYNCED, "Work"))
        pendingDeletions.add(listOf(PendingServerDeletions.PendingDeletion("x", "Work")))

        mutations.includeInSync(setOf("Work"))

        assertEquals(SyncStatus.PENDING, storage.loadNote("x")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, storage.loadNote("y")!!.syncStatus)
        assertTrue(pendingDeletions.getAll().isEmpty())
    }

    @Test fun `moveNotes queues the old server path as a relocation`() = runBlocking {
        storage.saveNote(note("m", SyncStatus.SYNCED, "Work"))
        storage.saveNote(note("n", SyncStatus.LOCAL_ONLY, "Work"))
        storage.saveNote(note("same", SyncStatus.SYNCED, "Home"))

        mutations.moveNotes(listOf("m", "n", "same"), target = "Home")

        assertEquals("Home", storage.loadNote("m")!!.folderName)
        assertEquals(SyncStatus.PENDING, storage.loadNote("m")!!.syncStatus)
        assertEquals("never uploaded → nothing to remove", listOf("m"), pendingDeletions.getAll().map { it.id })
        val queued = pendingDeletions.getAll().single()
        assertEquals("Work", queued.folderName)
        assertTrue(queued.isMove)
        assertEquals("already there → untouched", SyncStatus.SYNCED, storage.loadNote("same")!!.syncStatus)
    }

    @Test fun `moving into a local-only folder queues a real deletion`() = runBlocking {
        storage.saveNote(note("m", SyncStatus.SYNCED, "Work"))

        mutations.moveNotes(listOf("m"), target = "Private", targetLocalOnly = true)

        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("m")!!.syncStatus)
        assertEquals(false, pendingDeletions.getAll().single().isMove)
    }

    @Test fun `renameFolder moves active notes and leaves trashed ones`() = runBlocking {
        storage.saveNote(note("a", SyncStatus.SYNCED, "Old"))
        storage.saveNote(note("t", SyncStatus.SYNCED, "Old").copy(trashedAt = 5L))

        mutations.renameFolder("Old", "New", localOnly = false)

        assertEquals("New", storage.loadNote("a")!!.folderName)
        assertEquals("Old", storage.loadNote("t")!!.folderName)
        assertEquals(listOf("a"), pendingDeletions.getAll().map { it.id })
    }

    @Test fun `renaming a local-only folder queues nothing`() = runBlocking {
        storage.saveNote(note("a", SyncStatus.LOCAL_ONLY, "Old"))

        mutations.renameFolder("Old", "New", localOnly = true)

        assertEquals(SyncStatus.LOCAL_ONLY, storage.loadNote("a")!!.syncStatus)
        assertTrue(pendingDeletions.getAll().isEmpty())
    }
}
