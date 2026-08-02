package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val SERVER_URL = "https://host"
private const val NOTES_URL = "https://host/notes/"
private const val WORK_URL = "https://host/notes/Work/"
private const val PRIVATE_URL = "https://host/notes/Private/"
private const val WORK_NOTE_ID = "11111111-2222-3333-4444-555555555555"
private const val PRIVATE_NOTE_ID = "99999999-8888-7777-6666-555555555555"

/**
 * Issue #128: Die Löscherkennung lief „fail-open" — sie trashte Notizen auf Basis einer
 * Server-Liste, von der sie nicht wusste, ob sie vollständig ist. Fällt ein einzelner
 * Unterordner aus dem Listing (500, 404, unparsbarer href), fehlen dessen Notizen in
 * `serverNoteIds` und landen im Papierkorb, obwohl sie auf dem Server unangetastet sind.
 *
 * Die vorhandenen Wächter greifen nicht: sie decken nur „Listing komplett leer" und
 * „**alle** ≥10 SYNCED-Notizen betroffen" ab — nicht „ein Ordner fehlt".
 */
class NoteDownloaderIncompleteListingTest {
    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader
    private lateinit var webdav: WebDavClient

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-incomplete-listing-test").toFile()
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, any()) } returns
            Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
        // Deep-PROPFIND aus dem Weg räumen — dieser Test zielt auf das 1+N-Listing.
        val connectionManager = mockk<ConnectionManager>(relaxed = true) {
            every { deepPropfindRefused } returns true
        }
        downloader = NoteDownloader(
            prefs = prefs,
            storage = storage,
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = connectionManager,
            markdownSyncManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(context)
        )
        webdav = mockk(relaxed = true)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun res(path: String, isDirectory: Boolean = false) = WebDavResource(
        href = URI(path),
        modified = Date(1_000L),
        contentLength = if (isDirectory) -1 else 1,
        isDirectory = isDirectory,
        etag = "\"e\""
    )

    private fun syncedNote(id: String, folder: String) = Note(
        id = id,
        title = "T-$id",
        content = "C",
        deviceId = "dev",
        syncStatus = SyncStatus.SYNCED,
        folderName = folder,
        updatedAt = 1_000L
    )

    /** Root sieht beide Ordner; „Work" listet sauber, „Private" wird gleich sabotiert. */
    private suspend fun givenTwoFoldersOnServer() {
        val workNote = syncedNote(WORK_NOTE_ID, "Work")
        storage.saveNote(workNote)
        storage.saveNote(syncedNote(PRIVATE_NOTE_ID, "Private"))

        every { webdav.listOrNull(NOTES_URL) } returns listOf(
            res("/notes/", isDirectory = true),
            res("/notes/Work/", isDirectory = true),
            res("/notes/Private/", isDirectory = true)
        )
        every { webdav.listOrNull(WORK_URL) } returns listOf(res("/notes/Work/$WORK_NOTE_ID.json"))
        every { webdav.get("$WORK_URL$WORK_NOTE_ID.json") } answers { workNote.toJson().byteInputStream() }
    }

    private suspend fun assertPrivateNoteUntouched(result: DownloadResult) {
        val stored = storage.loadNote(PRIVATE_NOTE_ID)!!
        assertEquals(
            "incomplete listing must not mark the note as deleted on server",
            SyncStatus.SYNCED,
            stored.syncStatus
        )
        assertNull("note must not be moved to trash", stored.trashedAt)
        assertEquals(0, result.deletedOnServerCount)
    }

    /** Repro A: PROPFIND eines Unterordners scheitert hart (500). */
    @Test fun `subfolder listing failing with 500 must not trash that folders notes`() = runTest {
        givenTwoFoldersOnServer()
        every { webdav.listOrNull(PRIVATE_URL) } throws WebDavException("boom", 500)

        val result = downloader.downloadAll(webdav, SERVER_URL)

        assertPrivateNoteUntouched(result)
    }

    /** Repro A2: Unterordner fällt still aus dem Scan (404 → `listOrNull` liefert `null`). */
    @Test fun `subfolder missing from the listing must not trash that folders notes`() = runTest {
        givenTwoFoldersOnServer()
        every { webdav.listOrNull(PRIVATE_URL) } returns null

        val result = downloader.downloadAll(webdav, SERVER_URL)

        assertPrivateNoteUntouched(result)
    }

    /**
     * Repro B: Ein fehlgeschlagener GET darf nichts trashen — `serverNoteIds` kommt aus dem
     * Listing, nicht aus den erfolgreich geladenen Notizen (im Desktop-Client war genau das
     * der Unterschied). Regressionsschutz, dieser Fall ist heute schon korrekt.
     */
    @Test fun `a failed note download must not trash the note`() = runTest {
        givenTwoFoldersOnServer()
        every { webdav.listOrNull(PRIVATE_URL) } returns listOf(res("/notes/Private/$PRIVATE_NOTE_ID.json"))
        every { webdav.get("$PRIVATE_URL$PRIVATE_NOTE_ID.json") } throws WebDavException("timeout", 500)

        val result = downloader.downloadAll(webdav, SERVER_URL)

        assertPrivateNoteUntouched(result)
    }

    /**
     * Self-Heal: Eine Notiz, die ein früherer Sync fälschlich als server-gelöscht getrasht hat,
     * kehrt zurück, sobald ihre Datei wieder im Listing auftaucht. Ohne diesen Pfad bliebe sie
     * für immer im Papierkorb — und `TrashManager.purgeExpired()` löschte sie nach 30 Tagen hart.
     */
    @Test fun `a falsely trashed note is healed when it shows up on the server again`() = runTest {
        givenTwoFoldersOnServer()
        val victim = syncedNote(PRIVATE_NOTE_ID, "Private")
        // Zustand nach der Falsch-Trashung: detectDeletions bumpt updatedAt nicht.
        storage.saveNote(
            victim.copy(syncStatus = SyncStatus.DELETED_ON_SERVER, trashedAt = 5_000L)
        )
        every { webdav.listOrNull(PRIVATE_URL) } returns listOf(res("/notes/Private/$PRIVATE_NOTE_ID.json"))
        every { webdav.get("$PRIVATE_URL$PRIVATE_NOTE_ID.json") } answers { victim.toJson().byteInputStream() }

        val result = downloader.downloadAll(webdav, SERVER_URL)

        assertEquals(1, result.healedCount)
        val stored = storage.loadNote(PRIVATE_NOTE_ID)!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertNull("false trash marker must be cleared", stored.trashedAt)
        assertEquals("Private", stored.folderName)
    }

    /** Regressionsschutz: sauberes Listing ⇒ eine echt gelöschte Notiz landet weiterhin im Papierkorb. */
    @Test fun `clean listing still trashes a note that is really gone`() = runTest {
        givenTwoFoldersOnServer()
        every { webdav.listOrNull(PRIVATE_URL) } returns emptyList()

        val result = downloader.downloadAll(webdav, SERVER_URL)

        assertEquals(1, result.deletedOnServerCount)
        val stored = storage.loadNote(PRIVATE_NOTE_ID)!!
        assertEquals(SyncStatus.DELETED_ON_SERVER, stored.syncStatus)
    }
}
