package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.noteimport.NotesImportWizard
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.SyncException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E2EE-Slice 1: Der echte Sync gegen [FakeWebDav]. Vorbelegt ist alles, was sonst schriebe:
 * eine PENDING-Notiz, eine wartende Server-Löschung, geänderte Ordner-Metadaten.
 */
class WebDavSyncServiceE2eeGateTest {
    private lateinit var h: SyncHarness
    private val note = Note(title = "Lokal", content = "geändert", deviceId = "test", syncStatus = SyncStatus.PENDING)
    private val gone = Note(title = "Weg", content = "x", deviceId = "test", syncStatus = SyncStatus.SYNCED)

    @Before fun setUp() = runBlocking {
        h = SyncHarness()
        h.storage.saveNote(note)
        h.dav.putFile("/notes/${gone.id}.json", gone.toJson())
        PendingServerDeletions(h.context).add(listOf(PendingServerDeletions.PendingDeletion(gone.id)))
        h.prefs.edit().putBoolean(Constants.KEY_FOLDERS_DIRTY, true).apply()
    }

    @After fun tearDown() = h.close()

    private fun lockServer() = h.dav.putFile(MARKER, """{"format":"simple-notes-e2ee","version":1}""")

    private fun TestScope.service() = h.service(UnconfinedTestDispatcher(testScheduler))

    private fun ops() = h.activityLog().map { it.op }

    private fun storedMarker() = h.prefs.getString(Constants.KEY_E2EE_BLOCKED_MARKER, null)

    @Test fun `active marker - one GET, no write, state and log once`() = runTest {
        lockServer()
        val service = service()

        val result = service.syncNotes(trigger = null)

        assertTrue(result.e2eeBlocked)
        assertFalse(result.isSuccess)
        assertEquals(listOf("GET $MARKER"), h.dav.requests)
        assertEquals(h.server.url(MARKER).toString(), storedMarker())
        assertEquals(listOf(ActivityLog.Op.SYNC_BLOCKED), ops())
        assertEquals(SyncStatus.PENDING, h.storage.loadNote(note.id)?.syncStatus)

        // Zweiter gesperrter Lauf: wieder nur der GET, kein zweiter Protokolleintrag
        h.dav.clearRequests()
        assertTrue(service.syncNotes(trigger = null).e2eeBlocked)
        assertEquals(listOf("GET $MARKER"), h.dav.requests)
        assertEquals(listOf(ActivityLog.Op.SYNC_BLOCKED), ops())
    }

    @Test fun `missing marker - GET first, then the normal sync`() = runTest {
        val result = service().syncNotes(trigger = null)

        assertTrue(result.isSuccess)
        assertEquals("GET $MARKER", h.dav.requests.first())
        assertTrue(h.dav.exists("/notes/${note.id}.json"))
        assertFalse(h.dav.exists("/notes/${gone.id}.json"))
        assertNull(storedMarker())
    }

    @Test fun `probe errors fail the run without writing or changing state`() = runTest {
        for (code in listOf(500, 429, 401)) {
            h.dav.respond(MARKER, code)
            h.dav.clearRequests()

            val result = service().syncNotes(trigger = null)

            assertFalse("$code", result.isSuccess)
            assertFalse("$code", result.e2eeBlocked)
            assertEquals("$code", listOf("GET $MARKER"), h.dav.requests)
            assertNull("$code", storedMarker())
        }
        // 401 bleibt ein Auth-Fehler
        assertEquals(h.context.getString(R.string.sync_error_auth_failed), h.activityLog().last().err)
        assertFalse(ActivityLog.Op.SYNC_BLOCKED in ops())
    }

    @Test fun `marker removed - state cleared, unblocked logged, pending note uploaded`() = runTest {
        lockServer()
        val service = service()
        service.syncNotes(trigger = null)
        h.dav.respond(MARKER, 404)

        val result = service.syncNotes(trigger = null)

        assertTrue(result.isSuccess)
        assertNull(storedMarker())
        assertEquals(listOf(ActivityLog.Op.SYNC_BLOCKED, ActivityLog.Op.SYNC_UNBLOCKED), ops().take(2))
        assertTrue(h.dav.exists("/notes/${note.id}.json"))
    }

    @Test fun `switching away from a blocked folder clears the state without an unblocked entry`() = runTest {
        lockServer()
        val service = service()
        service.syncNotes(trigger = null)
        h.prefs.edit().putString(Constants.KEY_SYNC_FOLDER_NAME, "other").apply()
        assertFalse(service.isE2eeBlocked())

        assertTrue(service.syncNotes(trigger = null).isSuccess)

        assertNull(storedMarker())
        assertFalse(ActivityLog.Op.SYNC_UNBLOCKED in ops())
    }

    @Test fun `blocked counts as unsynced change even without server check`() = runTest {
        val service = service()
        service.syncNotes(trigger = null)
        h.prefs.edit().putBoolean(Constants.KEY_ALWAYS_CHECK_SERVER, false).apply()
        assertFalse(service.hasUnsyncedChanges())

        lockServer()
        service.syncNotes(trigger = null)

        assertTrue(service.hasUnsyncedChanges())
    }

    @Test fun `restore refuses before deleting anything and records nothing`() = runTest {
        lockServer()

        val result = service().restoreFromServer(RestoreMode.REPLACE)

        assertFalse(result.isSuccess)
        assertEquals(h.context.getString(R.string.restore_e2ee_blocked), result.errorMessage)
        assertEquals(listOf("GET $MARKER"), h.dav.requests)
        assertEquals(note, h.storage.loadNote(note.id))
        assertNull(storedMarker())
        assertTrue(ops().isEmpty())
    }

    // ── Direktpfade außerhalb des Syncs: frisch, ein GET pro Aktion, fail-closed, kein Zustand ──

    @Test fun `isServerLocked fails closed`() = runTest {
        val service = service()
        assertFalse(service.isServerLocked())
        h.dav.respond(MARKER, 500)
        assertTrue(service.isServerLocked())
        h.dav.respond(MARKER, 200, """{"format":"simple-notes-e2ee"}""")
        assertTrue(service.isServerLocked())
        assertEquals(List(3) { "GET $MARKER" }, h.dav.requests)
        assertNull(storedMarker())
    }

    @Test fun `markdown export and manual markdown sync refuse before writing`() = runTest {
        lockServer()
        val service = service()

        val export = runCatching { service.exportAllNotesToMarkdown(h.server.url("/").toString(), "user", "pw") }
        val manual = runCatching { service.manualMarkdownSync() }

        assertTrue(export.exceptionOrNull() is SyncException)
        assertTrue(manual.exceptionOrNull() is SyncException)
        assertEquals(List(2) { "GET $MARKER" }, h.dav.requests)
    }

    @Test fun `connection test reports the block without writing`() = runTest {
        lockServer()

        val result = service().testConnection()

        assertTrue(result.isSuccess)
        assertEquals(h.context.getString(R.string.test_connection_e2ee_blocked), result.infoMessage)
        assertTrue(h.dav.writes().isEmpty())
        assertNull(storedMarker())
    }

    @Test fun `conflict resolver does not hand out the stale plaintext version`() = runTest {
        h.dav.putFile("/notes/${note.id}.json", note.copy(content = "Server").toJson())
        val resolver = SyncConflictResolver(h.context, h.storage, UnconfinedTestDispatcher(testScheduler))
        assertNotNull(resolver.fetchServerVersion(note.id))

        lockServer()

        assertNull(resolver.fetchServerVersion(note.id))
        assertFalse(resolver.useServer(note.id))
        assertEquals("geändert", h.storage.loadNote(note.id)?.content)
    }

    @Test fun `import wizard never offers the e2ee folder`() = runTest {
        lockServer()
        h.dav.putFile("/other/a.json", """{"title":"Fremd","content":"x"}""")
        val webdav = service().getOrCreateWebDavClient()!!

        val names = NotesImportWizard(h.storage, h.context).scanWebDavFolder(webdav, h.server.url("/").toString()).map { it.name }

        assertTrue("other/a.json" in names)
        assertTrue(names.none { it.startsWith("notes-e2ee/") })
    }

    private companion object {
        const val MARKER = "/notes-e2ee/e2ee.json"
    }
}
