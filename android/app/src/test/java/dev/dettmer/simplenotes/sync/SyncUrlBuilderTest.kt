package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncUrlBuilderTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var builder: SyncUrlBuilder

    @Before fun setUp() {
        prefs = mockk {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME) } returns "notes"
        }
        builder = SyncUrlBuilder(prefs)
    }

    @Test fun `folderUrl null equals notesUrl`() {
        val base = "http://s:8080/"
        assertEquals(builder.getNotesUrl(base), builder.getNotesFolderUrl(base, null))
    }

    @Test fun `folderUrl appends segment`() {
        assertEquals("http://s:8080/notes/Rezepte/", builder.getNotesFolderUrl("http://s:8080/", "Rezepte"))
    }

    @Test fun `folderUrl encodes spaces`() {
        assertEquals("http://s:8080/notes/Reise%202024/", builder.getNotesFolderUrl("http://s:8080/", "Reise 2024"))
    }

    @Test fun `markdownFolderUrl is nested and not corrupted by folder named notes`() {
        assertEquals("http://s:8080/notes-md/notes/", builder.getMarkdownFolderUrl("http://s:8080/", "notes"))
    }
}
