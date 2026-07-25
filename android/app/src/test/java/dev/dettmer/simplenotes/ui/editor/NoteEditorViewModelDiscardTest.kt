package dev.dettmer.simplenotes.ui.editor

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 Issue #114: Eine nie gespeicherte Notiz (`existingNote == null`) hat nichts im Papierkorb zu
 * suchen — [NoteEditorViewModel.deleteNote] muss sie nur verwerfen (`NavigateBack`), nicht als
 * `NoteDeleteRequested` an den MainViewModel weiterreichen. Der `isDiscarded`-Guard verhindert
 * zusätzlich, dass ein danach folgender `saveOnBack()` (z. B. aus `ComposeNoteEditorActivity.onPause()`)
 * die verworfene Notiz doch noch auf Platte schreibt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelDiscardTest {
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tmpDir = Files.createTempDirectory("note-editor-vm-discard-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tmpDir.deleteRecursively()
    }

    /** Neue, leere TEXT-Notiz. Autosave ist AN, damit der Discard-Guard tatsächlich etwas verhindert. */
    private fun viewModel(): NoteEditorViewModel {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(Constants.KEY_AUTOSAVE_ENABLED, any()) } returns true
        }
        val app = mockk<Application>(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
            every { applicationContext } returns this
        }
        return NoteEditorViewModel(
            app,
            SavedStateHandle(mapOf(NoteEditorViewModel.ARG_NOTE_TYPE to NoteType.TEXT.name))
        )
    }

    @Test
    fun `deleteNote auf ungespeicherter Notiz verwirft statt zu loeschen`() = runTest {
        val vm = viewModel()
        vm.updateContent("nie gespeichert")

        vm.events.test {
            vm.deleteNote()
            assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun `saveOnBack nach Discard schreibt keine Datei`() = runTest {
        val vm = viewModel()
        vm.updateContent("nie gespeichert")

        vm.events.test {
            vm.deleteNote()
            awaitItem() // NavigateBack
        }

        assertTrue(vm.saveOnBack())
        val notesDir = File(tmpDir, "notes")
        val files = notesDir.listFiles()?.filter { it.extension == "json" }.orEmpty()
        assertTrue("Erwartete keine gespeicherte Notiz, fand: $files", files.isEmpty())
    }
}
