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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.16.0: Gegenstück zu [NoteDownloaderConflictHoldTest] für den Markdown-Import.
 *
 * Eine bereits als [SyncStatus.CONFLICT] markierte Notiz darf auch von einer neueren
 * MD-Datei nicht überschrieben werden — vorher prüfte der Zweig nur auf
 * [SyncStatus.PENDING] und der Import ersetzte die lokale Fassung still.
 */
class MarkdownSyncManagerConflictHoldTest {
    private lateinit var storage: NotesStorage
    private lateinit var manager: MarkdownSyncManager
    private val savedNotes = mutableListOf<Note>()

    private val noteId = "b7f1a0c4-3d9e-4a10-8f22-51ce0d4b9a01"
    private val localUpdatedAt = 5_000_000L
    private val mdUpdatedAt = 6_000_000L // MD ist neuer → Konflikt-Zweig

    @Before fun setUp() {
        savedNotes.clear()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        }
        val saved = slot<Note>()
        storage = mockk(relaxed = true)
        coEvery { storage.saveNote(capture(saved)) } answers { savedNotes.add(saved.captured) }
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = storage,
            eTagCache = mockk(relaxed = true),
            urlBuilder = mockk(relaxed = true) {
                every { getMarkdownUrl(any()) } returns MD_URL
                every { getMarkdownFolderUrl(any(), null) } returns MD_URL
            },
            connectionManager = mockk(relaxed = true),
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
    }

    private fun localNote(status: SyncStatus) = Note(
        id = noteId,
        title = "Liste",
        content = "LOCAL",
        deviceId = "dev",
        updatedAt = localUpdatedAt,
        syncStatus = status
    )

    private fun webdavWithNewerMarkdown(): WebDavClient = mockk<WebDavClient>(relaxed = true).also { webdav ->
        val md = Note(
            id = noteId,
            title = "Liste",
            content = "SERVER-MD",
            deviceId = "dev",
            updatedAt = mdUpdatedAt
        ).toMarkdown()
        every { webdav.exists(MD_URL) } returns true
        every { webdav.list(MD_URL) } returns listOf(
            WebDavResource(
                href = URI("/notes-md/Liste.md"),
                modified = Date(mdUpdatedAt),
                contentLength = 1,
                isDirectory = false,
                etag = "\"md-new\""
            )
        )
        every { webdav.get(any<String>()) } answers { md.byteInputStream() }
    }

    @Test fun `a note already marked CONFLICT is not replaced by a newer markdown file`() = runTest {
        coEvery { storage.loadNote(noteId) } returns localNote(SyncStatus.CONFLICT)

        val imported = manager.importAll(webdavWithNewerMarkdown(), SERVER)

        assertEquals("nothing may be imported over a conflict", 0, imported)
        assertFalse(
            "the markdown version must not reach storage",
            savedNotes.any { it.content.contains("SERVER-MD") }
        )
    }

    @Test fun `a SYNCED note is still replaced by a newer markdown file`() = runTest {
        coEvery { storage.loadNote(noteId) } returns localNote(SyncStatus.SYNCED)

        val imported = manager.importAll(webdavWithNewerMarkdown(), SERVER)

        assertEquals(1, imported)
        assertEquals("SERVER-MD", savedNotes.single().content.trim())
    }

    companion object {
        private const val SERVER = "http://server"
        private const val MD_URL = "http://server/notes-md/"
    }
}
