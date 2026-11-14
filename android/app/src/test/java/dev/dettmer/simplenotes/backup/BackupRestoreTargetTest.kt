package dev.dettmer.simplenotes.backup

import android.net.Uri
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.SyncHarness
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Restore mit Server-Einstellungen: Caches des alten Ziels dürfen das neue nicht bestimmen. */
class BackupRestoreTargetTest {
    private lateinit var h: SyncHarness
    private val uri = mockk<Uri>()
    private val backupBytes = ByteArrayOutputStream()

    @Before fun setUp() {
        h = SyncHarness()
        every { CredentialStore.setCredentials(any(), any(), any()) } returns true
        every { h.context.contentResolver.openOutputStream(uri) } returns backupBytes
        every { h.context.contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(backupBytes.toByteArray()) }
    }

    @After fun tearDown() = h.close()

    private suspend fun backupWithSettings(backup: BackupManager) {
        h.storage.saveNote(Note(title = "Gesichert", content = "x", deviceId = "test", syncStatus = SyncStatus.SYNCED))
        assertTrue(backup.createBackup(uri, includeServerSettings = true).success)
    }

    private fun seedCaches() = h.prefs.edit()
        .putString("etag_json_a", "\"e1\"")
        .putString("content_hash_a", "h")
        .putLong(Constants.KEY_LAST_SYNC, 1L)
        .apply()

    @Test fun `restoring settings for another sync folder clears the old target's caches`() = runTest {
        val backup = BackupManager(h.context, UnconfinedTestDispatcher(testScheduler))
        backupWithSettings(backup)
        h.prefs.edit().putString(Constants.KEY_SYNC_FOLDER_NAME, "anderer").apply()
        seedCaches()

        assertTrue(backup.restoreBackup(uri, RestoreMode.MERGE, restoreServerSettings = true).success)

        assertEquals("notes", h.prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, null))
        assertNull(h.prefs.getString("etag_json_a", null))
        assertNull(h.prefs.getString("content_hash_a", null))
        assertEquals(0L, h.prefs.getLong(Constants.KEY_LAST_SYNC, 0L))
    }

    @Test fun `restoring settings for the same target keeps the caches`() = runTest {
        val backup = BackupManager(h.context, UnconfinedTestDispatcher(testScheduler))
        backupWithSettings(backup)
        seedCaches()

        assertTrue(backup.restoreBackup(uri, RestoreMode.MERGE, restoreServerSettings = true).success)

        assertEquals("\"e1\"", h.prefs.getString("etag_json_a", null))
        assertEquals(1L, h.prefs.getLong(Constants.KEY_LAST_SYNC, 0L))
    }
}
