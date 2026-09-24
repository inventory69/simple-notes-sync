package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** SharedPreferences-Fake mit echter Backing-Map, damit [ETagCache] und Content-Hashes echt laufen. */
internal fun mapPrefs(backing: MutableMap<String, Any?> = mutableMapOf()): SharedPreferences {
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    every { editor.putString(any(), any()) } answers {
        backing[firstArg()] = secondArg<String?>()
        editor
    }
    every { editor.putBoolean(any(), any()) } answers {
        backing[firstArg()] = secondArg<Boolean>()
        editor
    }
    every { editor.remove(any()) } answers {
        backing.remove(firstArg<String>())
        editor
    }
    return mockk<SharedPreferences>(relaxed = true).also { p ->
        every { p.edit() } returns editor
        every { p.all } answers { backing.toMap() }
        every { p.getString(any(), any()) } answers { backing[firstArg()] as? String ?: secondArg() }
        every { p.getBoolean(any(), any()) } answers { backing[firstArg()] as? Boolean ?: secondArg() }
        every { p.getLong(any(), any()) } answers { backing[firstArg()] as? Long ?: secondArg() }
    }
}

internal fun mdResource(path: String, modified: Long = 1_000L) = WebDavResource(
    href = URI(path.replace(" ", "%20")),
    modified = Date(modified),
    contentLength = 1,
    isDirectory = false,
    etag = "\"$path\""
)

/**
 * 🆕 v2.19.0: Die MD-Kopie folgt der Notiz. Jeder Export merkt sich den Pfad und löscht die alte
 * Datei, wenn sich der Pfad geändert hat. Vorher blieb bei jeder Umbenennung eine Kopie liegen.
 */
class MarkdownSyncManagerMirrorPathTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var eTagCache: ETagCache
    private lateinit var storage: NotesStorage
    private lateinit var urlBuilder: SyncUrlBuilder
    private lateinit var manager: MarkdownSyncManager
    private lateinit var webdav: WebDavClient
    private lateinit var md: String

    @Before fun setUp() {
        prefs = mapPrefs(mutableMapOf(Constants.KEY_SYNC_FOLDER_NAME to "notes"))
        eTagCache = ETagCache(prefs)
        storage = mockk(relaxed = true)
        urlBuilder = SyncUrlBuilder(prefs)
        md = urlBuilder.getMarkdownUrl(SERVER)
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = storage,
            eTagCache = eTagCache,
            urlBuilder = urlBuilder,
            connectionManager = mockk(relaxed = true) {
                every { deepPropfindRefused } returns true
                every { markdownDirEnsured } returns true
                every { staleRootCleaned } returns true
            },
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
        webdav = mockk(relaxed = true)
        every { webdav.exists(any()) } returns false
        every { webdav.put(any(), any(), any()) } returns "\"etag\""
        // Echter Stream statt relaxed Mock, ein gemockter InputStream liefe endlos.
        every { webdav.get(any()) } answers { "---\nid: a1\n---\n".byteInputStream() }
    }

    private fun note(title: String, folder: String? = null, content: String = "body") =
        Note(id = "a1", title = title, content = content, deviceId = "dev", folderName = folder)

    @Test fun `rename deletes the old file and remembers the new one`() {
        manager.exportSingle(webdav, SERVER, note("A"))
        manager.exportSingle(webdav, SERVER, note("B"))

        verify { webdav.put("${md}B.md", any(), any()) }
        verify(exactly = 1) { webdav.delete("${md}A.md") }
        assertEquals("${md}B.md", eTagCache.getMdPath("a1"))
    }

    @Test fun `unchanged content sends neither PUT nor DELETE`() {
        manager.exportSingle(webdav, SERVER, note("A"))
        manager.exportSingle(webdav, SERVER, note("A"))

        verify(exactly = 1) { webdav.put(any(), any(), any()) }
        verify(exactly = 0) { webdav.delete(any()) }
    }

    @Test fun `a previous file that now carries another id is kept`() {
        manager.exportSingle(webdav, SERVER, note("A"))
        every { webdav.get("${md}A.md") } answers { "---\nid: b0\n---\n".byteInputStream() }

        manager.exportSingle(webdav, SERVER, note("B"))

        verify(exactly = 0) { webdav.delete(any()) }
        assertEquals("${md}B.md", eTagCache.getMdPath("a1"))
    }

    @Test fun `a remembered path outside the markdown root is never deleted`() {
        eTagCache.setMdPath("a1", "http://other-server/notes-md/A.md")

        manager.exportSingle(webdav, SERVER, note("B"))

        verify(exactly = 0) { webdav.delete(any()) }
        assertEquals("${md}B.md", eTagCache.getMdPath("a1"))
    }

    @Test fun `folder change deletes the copy in the old folder`() {
        manager.exportSingle(webdav, SERVER, note("A", folder = "Work"))
        manager.exportSingle(webdav, SERVER, note("A", folder = "Home"))

        val oldCopy = urlBuilder.getMarkdownFolderUrl(SERVER, "Work") + "A.md"
        verify(exactly = 1) { webdav.delete(oldCopy) }
    }

    @Test fun `trash deletes the remembered copy, not the title path`() {
        manager.exportSingle(webdav, SERVER, note("A"))

        manager.deleteSingle(webdav, SERVER, note("Renamed but not exported"))

        verify(exactly = 1) { webdav.delete("${md}A.md") }
        verify(exactly = 1) { webdav.delete(any()) }
        assertEquals(null, eTagCache.getMdPath("a1"))
    }

    @Test fun `an imported editor file is replaced by the title file on the next export`() = runTest {
        val imported = note("Einkaufsliste", content = "Milch")
        every { webdav.list(md) } returns listOf(mdResource("/notes-md/einkauf.md"))
        every { webdav.get("${md}einkauf.md") } answers { imported.toMarkdown().byteInputStream() }
        coEvery { storage.loadNote("a1") } returns null

        manager.importAll(webdav, SERVER)
        assertEquals("${md}einkauf.md", eTagCache.getMdPath("a1"))

        manager.exportSingle(webdav, SERVER, imported)

        verify { webdav.put("${md}Einkaufsliste.md", any(), any()) }
        verify(exactly = 1) { webdav.delete("${md}einkauf.md") }
    }

    @Test fun `frontmatter id is found in the first line and ignores lookalike keys`() {
        assertEquals("abc-1", manager.frontmatterId("---\nid: abc-1\ntitle: x\n---\n"))
        assertEquals("abc-2", manager.frontmatterId("---\r\ncreated: 1\r\nid: abc-2\r\n---"))
        assertEquals(null, manager.frontmatterId("---\nnoteid: abc-3\n---"))
        assertEquals(null, manager.frontmatterId("# no frontmatter\nid: abc-4"))
    }

    companion object {
        private const val SERVER = "http://server/notes/"
    }
}
