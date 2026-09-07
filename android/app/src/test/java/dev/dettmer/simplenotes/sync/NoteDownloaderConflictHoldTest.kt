package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.16.0: Eine als [SyncStatus.CONFLICT] markierte Notiz darf beim nächsten Sync **nicht**
 * still von der Server-Version ersetzt werden.
 *
 * Vorher prüften beide Konflikt-Zweige nur auf [SyncStatus.PENDING]; eine bereits markierte
 * Notiz fiel in den Else-Zweig ("safe to overwrite") und die lokale Fassung ging verloren —
 * das Warn-Icon hielt genau einen Sync-Zyklus. Ohne den Fix schlagen die beiden ersten Tests
 * fehl (Inhalt "SERVER" statt "LOCAL"), der dritte hält den Else-Zweig fest, der weiterhin
 * überschreiben *muss*.
 */
class NoteDownloaderConflictHoldTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader

    private val serverUrl = "http://server:8080"
    private val noteId = "3c1c3e2a-91f4-4b41-9a55-6a1b0d5e77aa"
    private val lastSync = 2_000_000L
    private val serverModified = 3_000_000L // > lastSync → kein mtime-Skip
    private val localUpdatedAt = 5_000_000L
    private val remoteUpdatedAt = 6_000_000L // Server ist neuer → Konflikt-Zweig
    private val folder = "Einkauf"

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-conflicthold").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getLong("last_sync_timestamp", 0L) } returns lastSync
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every {
            prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS)
        } returns Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        every { prefs.getString("etag_json_$noteId", null) } returns "etag-old"
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
        downloader = NoteDownloader(
            prefs = prefs,
            storage = storage,
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = mockk(relaxed = true),
            markdownSyncManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(context)
        )
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun localNote(status: SyncStatus, inFolder: String?) = Note(
        id = noteId,
        title = "Liste",
        content = "LOCAL",
        deviceId = "",
        updatedAt = localUpdatedAt,
        syncStatus = status,
        folderName = inFolder
    )

    private fun remoteJson(inFolder: String?) = Note(
        id = noteId,
        title = "Liste",
        content = "SERVER",
        deviceId = "",
        updatedAt = remoteUpdatedAt,
        syncStatus = SyncStatus.SYNCED,
        folderName = inFolder
    ).toJson()

    /** `name` und `path` leitet [WebDavResource] aus dem href ab — kein Mock nötig. */
    private fun resource(path: String, isDir: Boolean) = WebDavResource(
        href = URI(path),
        modified = Date(serverModified),
        contentLength = 1,
        isDirectory = isDir,
        etag = "\"etag-new\""
    )

    /** Konflikt im regulären Ordner-Pfad (NoteDownloader Phase 1). */
    private fun folderWebDav(): WebDavClient = mockk<WebDavClient>(relaxed = true).also { webdav ->
        every { webdav.listOrNull(match { it.endsWith("/notes/") }) } returns
            listOf(resource("/notes/$folder/", isDir = true))
        every { webdav.listOrNull(match { it.contains(folder) }) } returns
            listOf(resource("/notes/$folder/$noteId.json", isDir = false))
        every { webdav.get(any<String>()) } answers { ByteArrayInputStream(remoteJson(folder).toByteArray()) }
    }

    /** Konflikt im ROOT-Kompatibilitätspfad (Phase 2, nur bei `includeRootFallback`). */
    private fun rootWebDav(): WebDavClient = mockk<WebDavClient>(relaxed = true).also { webdav ->
        every { webdav.listOrNull(any<String>()) } returns emptyList()
        every { webdav.list(any<String>()) } returns listOf(resource("/$noteId.json", isDir = false))
        every { webdav.get(any<String>()) } answers { ByteArrayInputStream(remoteJson(null).toByteArray()) }
    }

    @Test fun `a note already marked CONFLICT is not overwritten by the server version`() = runTest {
        storage.saveNote(localNote(SyncStatus.CONFLICT, folder))

        val result = downloader.downloadAll(folderWebDav(), serverUrl)

        val kept = storage.loadNote(noteId)!!
        assertEquals("local version must survive a second sync", "LOCAL", kept.content)
        assertEquals(SyncStatus.CONFLICT, kept.syncStatus)
        assertEquals("conflict must still be reported", 1, result.conflictCount)
        assertEquals(0, result.downloadedCount)
    }

    @Test fun `CONFLICT also holds in the ROOT compatibility path`() = runTest {
        storage.saveNote(localNote(SyncStatus.CONFLICT, null))

        val result = downloader.downloadAll(rootWebDav(), serverUrl, includeRootFallback = true)

        val kept = storage.loadNote(noteId)!!
        assertEquals("LOCAL", kept.content)
        assertEquals(SyncStatus.CONFLICT, kept.syncStatus)
        assertEquals(1, result.conflictCount)
    }

    @Test fun `a SYNCED note is still overwritten by a newer server version`() = runTest {
        storage.saveNote(localNote(SyncStatus.SYNCED, folder))

        val result = downloader.downloadAll(folderWebDav(), serverUrl)

        val updated = storage.loadNote(noteId)!!
        assertEquals("SERVER", updated.content)
        assertEquals(SyncStatus.SYNCED, updated.syncStatus)
        assertEquals(0, result.conflictCount)
        assertEquals(1, result.downloadedCount)
    }
}
