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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * 🔧 Issue #124: Der Speichern-Button ging bisher immer durch `performSave()` und hat damit
 * `updatedAt` (und `syncStatus`) neu gesetzt, auch wenn seit dem letzten Save nichts geändert
 * wurde. Die Notiz sprang dadurch in der Liste nach oben und wurde erneut synchronisiert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelSaveTest {
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tmpDir = Files.createTempDirectory("note-editor-vm-save-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tmpDir.deleteRecursively()
    }

    /** Neue TEXT-Notiz, Autosave AUS — nur der Speichern-Button schreibt. */
    private fun viewModel(): NoteEditorViewModel {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(Constants.KEY_AUTOSAVE_ENABLED, any()) } returns false
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

    private fun savedNoteFile(): File =
        File(tmpDir, "notes").listFiles()?.singleOrNull { it.extension == "json" }
            ?: error("Keine gespeicherte Notiz gefunden")

    @Test
    fun `zweiter Speichern-Klick ohne Aenderung schreibt die Notiz nicht neu`() = runTest {
        val vm = viewModel()
        vm.updateTitle("Titel")
        vm.updateContent("Inhalt")

        vm.events.test {
            vm.saveNote()
            assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
        }
        val afterFirstSave = savedNoteFile().readText()

        // Uhr muss weiterlaufen, sonst wäre ein erneutes updatedAt zufällig identisch
        Thread.sleep(5)

        vm.events.test {
            vm.saveNote()
            assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
        }

        assertEquals(
            "Speichern ohne Änderung darf die Notiz auf Platte nicht anfassen",
            afterFirstSave,
            savedNoteFile().readText()
        )
    }

    @Test
    fun `Speichern nach einer Aenderung schreibt weiterhin`() = runTest {
        val vm = viewModel()
        vm.updateTitle("Titel")
        vm.updateContent("Inhalt")

        vm.events.test {
            vm.saveNote()
            assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
        }
        val afterFirstSave = savedNoteFile().readText()

        Thread.sleep(5)
        vm.updateContent("Inhalt, geändert")

        vm.events.test {
            vm.saveNote()
            assertEquals(NoteEditorEvent.NavigateBack, awaitItem())
        }

        val afterSecondSave = savedNoteFile().readText()
        assertNotNull(afterSecondSave)
        assert(afterSecondSave != afterFirstSave) {
            "Echte Änderung muss gespeichert werden"
        }
        assert(afterSecondSave.contains("Inhalt, geändert"))
    }
}
