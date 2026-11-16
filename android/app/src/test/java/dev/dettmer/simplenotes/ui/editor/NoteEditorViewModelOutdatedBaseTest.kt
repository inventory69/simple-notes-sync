package dev.dettmer.simplenotes.ui.editor

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.widget.WidgetUpdateHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lädt der Sync eine neuere Fassung herunter, während die Notiz im Editor offen ist, zeigt der Editor
 * weiter den alten Stand. Speichern darf die neue Fassung dann nicht still überschreiben: Der Download
 * hat den E-Tag schon aufgefrischt, der Upload sähe keinen Konflikt. Stattdessen wird die Notiz
 * CONFLICT, und die Auflösung im Editor entscheidet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelOutdatedBaseTest {
    private lateinit var tmpDir: File
    private lateinit var app: Application
    private lateinit var storage: NotesStorage

    private val id = "c3d4e5f6-0718-4a29-8b3c-4d5e6f708192"
    private val opened = Note(id = id, title = "T", content = "base", updatedAt = 1_000L, deviceId = "d", syncStatus = SyncStatus.SYNCED)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WidgetUpdateHelper)
        coEvery { WidgetUpdateHelper.refreshAllWidgets(any()) } just runs
        tmpDir = Files.createTempDirectory("editor-outdated-base").toFile()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(Constants.KEY_AUTOSAVE_ENABLED, any()) } returns true
        }
        app = mockk(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
            every { applicationContext } returns this
        }
        storage = NotesStorage(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(WidgetUpdateHelper)
        tmpDir.deleteRecursively()
    }

    private suspend fun open(note: Note = opened): NoteEditorViewModel {
        storage.saveNote(note)
        val vm = NoteEditorViewModel(app, SavedStateHandle(mapOf(NoteEditorViewModel.ARG_NOTE_ID to id)))
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.uiState.value.isLoading && System.currentTimeMillis() < deadline) Thread.sleep(5)
        return vm
    }

    private suspend fun NoteEditorViewModel.saveViaButton() = events.test {
        saveNote()
        assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
    }

    private suspend fun syncDownloads(content: String) =
        storage.saveNote(opened.copy(content = content, updatedAt = 5_000L, deviceId = "other", syncStatus = SyncStatus.SYNCED))

    @Test
    fun `saving over a version downloaded meanwhile marks the note CONFLICT`() = runTest {
        val vm = open()
        syncDownloads("from the other device")

        vm.updateContent("base plus my edit")
        vm.saveViaButton()

        val onDisk = storage.loadNote(id)!!
        assertEquals("the editor text is kept", "base plus my edit", onDisk.content)
        assertEquals(SyncStatus.CONFLICT, onDisk.syncStatus)
        assertTrue(vm.uiState.value.hasConflict)
    }

    @Test
    fun `the same applies when leaving the editor`() = runTest {
        val vm = open()
        syncDownloads("from the other device")

        vm.updateContent("base plus my edit")
        vm.saveOnBack()

        assertEquals(SyncStatus.CONFLICT, storage.loadNote(id)!!.syncStatus)
    }

    @Test
    fun `a later save keeps the conflict until it is resolved`() = runTest {
        val vm = open()
        syncDownloads("from the other device")
        vm.updateContent("first edit")
        vm.saveOnBack()

        vm.updateContent("second edit")
        vm.saveOnBack()

        val onDisk = storage.loadNote(id)!!
        assertEquals("second edit", onDisk.content)
        assertEquals(SyncStatus.CONFLICT, onDisk.syncStatus)
    }

    @Test
    fun `without a download the save is PENDING as before`() = runTest {
        val vm = open()

        vm.updateContent("base plus my edit")
        vm.saveViaButton()

        assertEquals(SyncStatus.PENDING, storage.loadNote(id)!!.syncStatus)
        assertFalse(vm.uiState.value.hasConflict)
    }

    @Test
    fun `the upload of the own save is not mistaken for a download`() = runTest {
        val vm = open()
        vm.updateContent("first edit")
        vm.saveOnBack()
        // Der Upload setzt nur den Status, der Inhalt ist der eigene
        storage.updateNote(id) { it.copy(syncStatus = SyncStatus.SYNCED) }

        vm.updateContent("second edit")
        vm.saveOnBack()

        assertEquals(SyncStatus.PENDING, storage.loadNote(id)!!.syncStatus)
    }

    @Test
    fun `a local change by another writer does not raise a conflict`() = runTest {
        val local = opened.copy(syncStatus = SyncStatus.LOCAL_ONLY)
        val vm = open(local)
        // z. B. Schnellbearbeitung im Widget: lokal, nicht vom Server
        storage.saveNote(local.copy(content = "widget", updatedAt = 5_000L))

        vm.updateContent("base plus my edit")
        vm.saveOnBack()

        val onDisk = storage.loadNote(id)!!
        assertEquals("base plus my edit", onDisk.content)
        assertFalse(onDisk.syncStatus == SyncStatus.CONFLICT)
    }

    @Test
    fun `a note deleted meanwhile is written again so the text survives`() = runTest {
        val vm = open()
        storage.deleteNote(id)

        vm.updateContent("base plus my edit")
        vm.saveOnBack()

        assertEquals("base plus my edit", storage.loadNote(id)!!.content)
    }
}
