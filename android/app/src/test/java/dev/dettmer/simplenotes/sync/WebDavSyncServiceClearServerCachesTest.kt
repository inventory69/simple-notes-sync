package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 🆕 v2.19.0: [WebDavSyncService.clearServerCaches] räumt alles Server-Spezifische, sonst nichts. */
class WebDavSyncServiceClearServerCachesTest {
    @Test fun `clears etags, content hashes, timestamps and tracker but keeps unrelated keys`() {
        val tmpDir = Files.createTempDirectory("clear-server-caches-test").toFile()
        try {
            val prefs = FakeSharedPreferences()
            val context = mockk<Context> {
                every { filesDir } returns tmpDir
                every { getSharedPreferences(any(), any()) } returns prefs
            }
            val storage = NotesStorage(context)
            storage.saveDeletionTracker(DeletionTracker().apply { addDeletion("gone", "dev") })
            prefs.edit()
                .putString("etag_json_a", "1")
                .putString(Constants.KEY_FOLDERS_JSON_ETAG, "2")
                .putString("etag_md_a", "3")
                .putString("etag_md_path_a", "https://x/notes-md/a.md")
                .putString("content_hash_a", "4")
                .putString("content_hash_md_a", "5")
                .putLong(Constants.KEY_LAST_SYNC, 10L)
                .putLong(Constants.KEY_LAST_SUCCESSFUL_SYNC, 11L)
                .putString(Constants.KEY_SYNC_FOLDER_NAME, "archive")
                .apply()

            WebDavSyncService.clearServerCaches(prefs, storage)

            assertEquals(setOf(Constants.KEY_SYNC_FOLDER_NAME), prefs.all.keys)
            assertFalse(storage.loadDeletionTracker().isDeleted("gone"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
