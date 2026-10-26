package dev.dettmer.simplenotes.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Abhaken im Notiz-Widget hat bisher nur das eigene Widget neu gezeichnet: Listen-Widgets und ein
 * zweites Widget auf dieselbe Notiz zeigten den alten Stand. In Nur-lokal-Ordnern wurde die Notiz
 * zudem fälschlich PENDING.
 */
class ToggleChecklistItemActionTest {
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        // Seit Glance 1.2.0 suspendiert getGlanceIds() gegen den gemockten Context für immer
        mockkObject(WidgetUpdateHelper)
        coEvery { WidgetUpdateHelper.refreshAllWidgets(any()) } just runs
        tmpDir = Files.createTempDirectory("widget-toggle-test").toFile()
    }

    @After
    fun tearDown() {
        unmockkObject(WidgetUpdateHelper)
        tmpDir.deleteRecursively()
    }

    private fun context(localOnlyFolders: Set<String>): Context {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getStringSet("local_only_folders", any()) } returns localOnlyFolders
        }
        return mockk(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
            every { applicationContext } returns this
        }
    }

    private suspend fun toggle(context: Context, folder: String?): Note {
        val item = ChecklistItem(id = "i1", text = "Milch", order = 0)
        val note = Note(
            id = "n1",
            title = "Einkauf",
            content = "",
            deviceId = "test",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(item),
            folderName = folder,
            syncStatus = SyncStatus.SYNCED
        )
        val storage = NotesStorage(context)
        storage.saveNote(note)

        ToggleChecklistItemAction().onAction(
            context,
            mockk<GlanceId>(),
            actionParametersOf(
                NoteWidgetActionKeys.KEY_NOTE_ID to "n1",
                NoteWidgetActionKeys.KEY_ITEM_ID to "i1"
            )
        )
        return storage.loadNote("n1") ?: error("Notiz fehlt")
    }

    @Test
    fun `Toggle aktualisiert alle Widgets`() = runTest {
        val ctx = context(emptySet())
        val saved = toggle(ctx, folder = null)

        assertTrue(saved.checklistItems!!.single().isChecked)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)
        coVerify(exactly = 1) { WidgetUpdateHelper.refreshAllWidgets(ctx) }
    }

    @Test
    fun `Toggle in Nur-lokal-Ordner bleibt LOCAL_ONLY`() = runTest {
        val saved = toggle(context(setOf("Privat")), folder = "privat")

        assertEquals(SyncStatus.LOCAL_ONLY, saved.syncStatus)
    }
}
