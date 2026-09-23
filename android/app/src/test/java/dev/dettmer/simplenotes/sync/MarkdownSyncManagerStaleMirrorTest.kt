package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.net.URI
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.19.0: Ein veralteter, von uns selbst geschriebener MD-Spiegel darf eine neuere
 * Server-JSON nicht überschreiben.
 *
 * Am Emulator belegt (23.9.2026): Nur die JSON einer Notiz wird serverseitig geändert, der
 * Download übernimmt sie, danach importierte der Force-Zweig den alten Spiegel (PENDING,
 * updatedAt = now) und der Re-Upload schrieb die alte Fassung zurück. Durch den
 * `mtime > lastSync`-Filter kam der Spiegel, weil die Server-Uhr der Geräte-Uhr vorausging
 * (H1) bzw. `lastSync == 0` war (H2).
 */
class MarkdownSyncManagerStaleMirrorTest {
    private lateinit var storage: NotesStorage
    private lateinit var eTagCache: ETagCache
    private lateinit var timestampManager: SyncTimestampManager
    private lateinit var manager: MarkdownSyncManager
    private val savedNotes = mutableListOf<Note>()

    private val noteId = "c2e8d7a1-5b4f-4c3e-9a6d-7f1b2e3c4d5e"
    private val mirrorUpdatedAt = 5_000_000L
    private val localUpdatedAt = 6_000_000L // Server-JSON ist neuer als der Spiegel
    private val lastSync = 5_500_000L
    private val mirrorMtime = 5_510_000L // Server-Uhr geht vor → mtime > lastSync

    @Before fun setUp() {
        savedNotes.clear()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        }
        val saved = slot<Note>()
        storage = mockk(relaxed = true)
        coEvery { storage.saveNote(capture(saved)) } answers { savedNotes.add(saved.captured) }
        coEvery { storage.loadNote(noteId) } returns Note(
            id = noteId,
            title = "Liste",
            content = "NEW-FROM-SERVER-JSON",
            deviceId = "dev",
            updatedAt = localUpdatedAt,
            syncStatus = SyncStatus.SYNCED
        )
        eTagCache = mockk(relaxed = true)
        timestampManager = mockk(relaxed = true)
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = storage,
            eTagCache = eTagCache,
            urlBuilder = mockk(relaxed = true) {
                every { getMarkdownUrl(any()) } returns MD_URL
                every { getMarkdownFolderUrl(any(), null) } returns MD_URL
            },
            connectionManager = mockk(relaxed = true),
            timestampManager = timestampManager,
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
    }

    private fun webdavWithMirror(content: String, etag: String): WebDavClient =
        mockk<WebDavClient>(relaxed = true).also { webdav ->
            val md = Note(
                id = noteId,
                title = "Liste",
                content = content,
                deviceId = "dev",
                updatedAt = mirrorUpdatedAt
            ).toMarkdown()
            every { webdav.exists(MD_URL) } returns true
            every { webdav.list(MD_URL) } returns listOf(
                WebDavResource(
                    href = URI("/notes-md/Liste.md"),
                    modified = Date(mirrorMtime),
                    contentLength = 1,
                    isDirectory = false,
                    etag = etag
                )
            )
            every { webdav.get(any<String>()) } answers { md.byteInputStream() }
        }

    @Test fun `our own unchanged mirror does not override a newer server JSON`() = runTest {
        every { timestampManager.getLast() } returns lastSync
        every { eTagCache.getMdETag(noteId) } returns "\"md-ours\""

        // PROPFIND liefert den ETag mit W/-Präfix, gespeichert ist der PUT-Header
        val imported = manager.importAll(webdavWithMirror("OLD-MIRROR", "W/\"md-ours\""), SERVER)

        assertEquals(0, imported)
        assertTrue("stale mirror must not reach storage", savedNotes.isEmpty())
    }

    @Test fun `an externally edited mirror is still imported`() = runTest {
        every { timestampManager.getLast() } returns lastSync
        every { eTagCache.getMdETag(noteId) } returns "\"md-ours\""

        val imported = manager.importAll(webdavWithMirror("EDITED-IN-OBSIDIAN", "\"md-foreign\""), SERVER)

        assertEquals(1, imported)
        assertEquals("EDITED-IN-OBSIDIAN", savedNotes.single().content.trim())
        assertEquals(SyncStatus.PENDING, savedNotes.single().syncStatus)
    }

    @Test fun `without a sync baseline an older mirror does not override a newer server JSON`() = runTest {
        // H2: lastSync und MD-ETag sind geräumt (restoreFromServer / clearServerCaches)
        every { timestampManager.getLast() } returns 0L
        every { eTagCache.getMdETag(noteId) } returns null

        val imported = manager.importAll(webdavWithMirror("OLD-MIRROR", "\"md-any\""), SERVER)

        assertEquals(0, imported)
        assertTrue("stale mirror must not reach storage", savedNotes.isEmpty())
    }

    companion object {
        private const val SERVER = "http://server"
        private const val MD_URL = "http://server/notes-md/"
    }
}
