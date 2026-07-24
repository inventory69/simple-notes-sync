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
 * 🔧 v2.13.0: Regressionstests für [NoteEditorViewModel.updateChecklistItemChecked].
 *
 * Die Atomizität von isChecked-Flip **und** Sort in einem einzigen State-Snapshot ist die
 * zentrale Invariante aus v2.5.0: ein früherer 350-ms-Sort-Delay ließ den Separator sofort
 * springen, während die Items nachhingen (Separator-Artefakte). Der Collapse-Pfad aus v2.13.0
 * verschiebt nur den *Zeitpunkt* des Aufrufs, nie den Ablauf im ViewModel — dieser Test hält
 * das fest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelChecklistTest {
    private lateinit var tmpDir: File
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tmpDir = Files.createTempDirectory("note-editor-vm-checklist-test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tmpDir.deleteRecursively()
    }

    /**
     * ViewModel mit neuer, leerer Checkliste. `scrollTopOnUncheck` ist die einzige Pref, die
     * hier variiert; alle anderen (u. a. Autosave) bleiben auf dem mockk-Default `false`, damit
     * kein Hintergrund-Save läuft.
     */
    private fun viewModel(scrollTopOnUncheck: Boolean): NoteEditorViewModel {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(Constants.KEY_CHECKLIST_SCROLL_TOP_ON_UNCHECK, any()) } returns scrollTopOnUncheck
            every { getBoolean(Constants.KEY_AUTOSAVE_ENABLED, any()) } returns false
        }
        app = mockk(relaxed = true) {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
            every { applicationContext } returns this
        }
        return NoteEditorViewModel(
            app,
            SavedStateHandle(mapOf(NoteEditorViewModel.ARG_NOTE_TYPE to NoteType.CHECKLIST.name))
        )
    }

    /** Legt [count] Items an und gibt ihre IDs in Listenreihenfolge zurück. */
    private fun NoteEditorViewModel.seedItems(count: Int): List<String> {
        // initNewNote hat bereits ein leeres Item angelegt.
        repeat(count - 1) { addChecklistItemAtEnd() }
        return checklistItems.value.map { it.id }
    }

    @Test
    fun `Check emittiert genau einen State mit Flip UND Sort`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = false)
        val ids = vm.seedItems(4)
        val toggled = ids[1]

        vm.checklistItems.test {
            assertEquals(ids, awaitItem().map { it.id })

            vm.updateChecklistItemChecked(toggled, true)

            // Genau EIN Emit — und darin ist das Item bereits gecheckt UND ans Ende sortiert.
            val after = awaitItem()
            assertEquals(listOf(ids[0], ids[2], ids[3], toggled), after.map { it.id })
            assertTrue(after.single { it.id == toggled }.isChecked)
            // order wird im selben Snapshot neu vergeben
            assertEquals(listOf(0, 1, 2, 3), after.map { it.order })

            expectNoEvents()
        }
    }

    @Test
    fun `Uncheck emittiert genau einen State mit Flip UND Sort`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = false)
        val ids = vm.seedItems(4)
        val toggled = ids[0]
        vm.updateChecklistItemChecked(toggled, true) // → ans Ende

        vm.checklistItems.test {
            assertEquals(listOf(ids[1], ids[2], ids[3], toggled), awaitItem().map { it.id })

            vm.updateChecklistItemChecked(toggled, false)

            // MANUAL sortiert unchecked nach originalOrder → das Item kehrt an seine
            // Ausgangsposition zurück (v1.9.0 F04). Genau dieser Sprung nach oben ist der
            // Grund für den Collapse-Pfad, wenn Scroll-to-Top aus ist.
            val after = awaitItem()
            assertEquals(ids, after.map { it.id })
            assertTrue(after.none { it.isChecked })

            expectNoEvents()
        }
    }

    @Test
    fun `No-Op-Guard bei identischem Zustand`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = false)
        val ids = vm.seedItems(3)

        vm.checklistItems.test {
            awaitItem()
            vm.updateChecklistItemChecked(ids[0], false) // war schon unchecked
            expectNoEvents()
        }
    }

    @Test
    fun `Uncheck emittiert ScrollToTop wenn die Pref an ist`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = true)
        val ids = vm.seedItems(3)
        vm.updateChecklistItemChecked(ids[0], true)

        vm.checklistScrollAction.test {
            vm.updateChecklistItemChecked(ids[0], false)
            assertEquals(NoteEditorViewModel.ChecklistScrollAction.ScrollToTop, awaitItem())
        }
    }

    @Test
    fun `Uncheck emittiert NoScroll wenn die Pref aus ist`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = false)
        val ids = vm.seedItems(3)
        vm.updateChecklistItemChecked(ids[0], true)

        vm.checklistScrollAction.test {
            vm.updateChecklistItemChecked(ids[0], false)
            assertEquals(NoteEditorViewModel.ChecklistScrollAction.NoScroll, awaitItem())
        }
    }

    @Test
    fun `Check emittiert immer NoScroll`() = runTest {
        val vm = viewModel(scrollTopOnUncheck = true)
        val ids = vm.seedItems(3)

        vm.checklistScrollAction.test {
            vm.updateChecklistItemChecked(ids[0], true)
            assertEquals(NoteEditorViewModel.ChecklistScrollAction.NoScroll, awaitItem())
        }
    }
}
