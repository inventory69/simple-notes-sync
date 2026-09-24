package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.19.0: Der einmalige Aufräumlauf löscht nur eindeutige Altlasten (älterer Zwilling einer
 * lokalen Notiz, Kopie einer gelöschten Notiz) und lässt alles Unklare stehen.
 */
class MarkdownSyncManagerHealTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var eTagCache: ETagCache
    private lateinit var storage: NotesStorage
    private lateinit var connectionManager: ConnectionManager
    private lateinit var manager: MarkdownSyncManager
    private lateinit var webdav: WebDavClient
    private lateinit var md: String
    private var healed = false

    /** Dateiname → Frontmatter-ID (null = Datei ohne ID). */
    private val files = mutableMapOf<String, String?>()
    private val modified = mutableMapOf<String, Long>()

    @Before fun setUp() {
        prefs = mapPrefs(mutableMapOf(Constants.KEY_SYNC_FOLDER_NAME to "notes"))
        eTagCache = ETagCache(prefs)
        storage = mockk(relaxed = true)
        coEvery { storage.loadDeletionTracker() } returns DeletionTracker().apply { addDeletion("dead-0001", "dev") }
        connectionManager = mockk(relaxed = true) {
            every { deepPropfindRefused } returns true
            every { mdMirrorsHealed } answers { healed }
            every { mdMirrorsHealed = any() } answers { healed = firstArg() }
        }
        val urlBuilder = SyncUrlBuilder(prefs)
        md = urlBuilder.getMarkdownUrl(SERVER)
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = storage,
            eTagCache = eTagCache,
            urlBuilder = urlBuilder,
            connectionManager = connectionManager,
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
        webdav = mockk(relaxed = true)
        every { webdav.list(md) } answers {
            files.keys.map { mdResource("/notes-md/$it", modified[it] ?: 1_000L) }
        }
        every { webdav.get(any()) } answers {
            val id = files[firstArg<String>().removePrefix(md)]
            (if (id == null) "# no frontmatter" else "---\nid: $id\n---\n\n# x").byteInputStream()
        }
    }

    private fun localNotes(vararg notes: Note) = coEvery { storage.loadAllNotes() } returns notes.toList()

    private fun note(id: String, title: String) = Note(id = id, title = title, content = "c", deviceId = "dev")

    private fun file(name: String, id: String?, at: Long = 1_000L) {
        files[name] = id
        modified[name] = at
    }

    @Test fun `older twin is deleted, a newer one stays`() = runTest {
        localNotes(note("a1", "B"))
        file("B.md", "a1", at = 2_000)
        file("A.md", "a1", at = 1_000)
        file("C.md", "a1", at = 3_000)

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 1) { webdav.delete("${md}A.md") }
        verify(exactly = 1) { webdav.delete(any()) }
        assertEquals("${md}B.md", eTagCache.getMdPath("a1"))
    }

    @Test fun `copy of a deleted note is deleted`() = runTest {
        localNotes()
        file("Old.md", "dead-0001")

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 1) { webdav.delete("${md}Old.md") }
    }

    @Test fun `unknown id and file without id stay`() = runTest {
        localNotes()
        file("Foreign.md", "cafe-0002")
        file("Plain.md", null)

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 0) { webdav.delete(any()) }
        assertTrue(healed)
    }

    @Test fun `only copy under an old name stays and is remembered`() = runTest {
        localNotes(note("a2", "New"))
        file("Old.md", "a2")

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 0) { webdav.delete(any()) }
        assertEquals("${md}Old.md", eTagCache.getMdPath("a2"))
    }

    @Test fun `twin is kept when the expected file belongs to another note`() = runTest {
        localNotes(note("a1", "Same"))
        file("Same.md", "beef-0003", at = 2_000)
        file("Older.md", "a1", at = 1_000)

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 0) { webdav.delete(any()) }
    }

    @Test fun `second run with the flag set sends no request`() = runTest {
        localNotes(note("a1", "B"))
        file("B.md", "a1")
        manager.healStaleMirrors(webdav, SERVER)
        io.mockk.clearMocks(webdav, answers = false)

        manager.healStaleMirrors(webdav, SERVER)

        verify(exactly = 0) { webdav.list(any()) }
        verify(exactly = 0) { webdav.get(any()) }
    }

    @Test fun `failed listing leaves the flag unset`() = runTest {
        every { webdav.list(md) } throws java.io.IOException("offline")

        manager.healStaleMirrors(webdav, SERVER)

        assertEquals(false, healed)
    }

    companion object {
        private const val SERVER = "http://server/notes/"
    }
}
