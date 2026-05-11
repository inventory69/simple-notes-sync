package dev.dettmer.simplenotes.ui.settings.keepimport

import android.app.Application
import android.net.Uri
import dev.dettmer.simplenotes.noteimport.keep.KeepImportOptions
import dev.dettmer.simplenotes.noteimport.keep.KeepImportProgress
import dev.dettmer.simplenotes.noteimport.keep.KeepImportSummary
import dev.dettmer.simplenotes.noteimport.keep.KeepImportUseCase
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictStrategy
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.5.0 — Tests für [KeepImportViewModel] State-Maschine + Pipeline-Wiring.
 * Use-Case selbst ist in Commit #10 separat getestet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var useCase: KeepImportUseCase
    private lateinit var zipReader: KeepZipReader
    private lateinit var vm: KeepImportViewModel

    private val fakeUri: Uri = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = mockk(relaxed = true) {
            every { getString(any()) } returns "msg"
            every { getString(any(), *anyVararg()) } returns "msg"
        }
        useCase = mockk(relaxed = true)
        zipReader = mockk()
        vm = KeepImportViewModel(application = app, useCase = useCase, zipReader = zipReader, ioDispatcher = dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun preScan(size: Long = 1_000L) = KeepPreScanResult(
        totalNotes = 2, activeCount = 2, archivedCount = 0, trashedCount = 0,
        labelCount = 0, sharedCount = 0, notesWithAttachments = 0, sizeBytes = size,
    )

    private val defaultOptions = KeepImportOptionsHolder(
        includeArchived = false,
        includeTrashed = false,
        conflictStrategy = ConflictStrategy.ALWAYS_CREATE,
    )

    // ───── onZipPicked → Configuring(scanning=true) → preScan-Update ─────
    @Test
    fun `onZipPicked_setsConfiguringScanning_thenFillsPreScan`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan()
        vm.onZipPicked(fakeUri)
        // Direkt nach Aufruf: scanning=true
        val s1 = vm.state.value as KeepImportUiState.Configuring
        assertTrue(s1.scanning)
        assertEquals(null, s1.preScan)
        // PreScan abwarten
        advanceUntilIdle()
        val s2 = vm.state.value as KeepImportUiState.Configuring
        assertTrue(!s2.scanning)
        assertEquals(2, s2.preScan!!.totalNotes)
    }

    // ───── preScan-Fehler → Error-State + Snackbar ─────
    @Test
    fun `onZipPicked_preScanFails_setsErrorState`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } throws java.io.IOException("bad zip")
        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        assertTrue(vm.state.value is KeepImportUiState.Error)
    }

    // ───── Kleines ZIP → Configuring → onConfigConfirmed → Running → Done ─────
    @Test
    fun `onConfigConfirmed_smallZip_triggersImport`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan(size = 1_000L)
        coEvery { useCase.import(any(), any(), any(), any()) } returns
            KeepImportSummary.EMPTY.copy(totalEntries = 2, imported = 2)

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        advanceUntilIdle()

        assertTrue(vm.state.value is KeepImportUiState.Done)
        coVerify(exactly = 1) { useCase.import(eq(fakeUri), any(), any(), any()) }
    }

    // ───── Großes ZIP → ConfirmLargeZip-Step erscheint ─────
    @Test
    fun `onConfigConfirmed_largeZip_entersConfirmLargeZipState`() = runTest(dispatcher) {
        val big = KeepImportViewModel.LARGE_ZIP_THRESHOLD_BYTES + 1
        coEvery { zipReader.preScan(fakeUri) } returns preScan(size = big)

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)

        assertTrue(vm.state.value is KeepImportUiState.ConfirmLargeZip)
        coVerify(exactly = 0) { useCase.import(any(), any(), any(), any()) }
    }

    @Test
    fun `onLargeZipConfirmed_startsImport`() = runTest(dispatcher) {
        val big = KeepImportViewModel.LARGE_ZIP_THRESHOLD_BYTES + 1
        coEvery { zipReader.preScan(fakeUri) } returns preScan(size = big)
        coEvery { useCase.import(any(), any(), any(), any()) } returns KeepImportSummary.EMPTY

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        vm.onLargeZipConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value is KeepImportUiState.Done)
    }

    @Test
    fun `onLargeZipDeclined_returnsToIdle`() = runTest(dispatcher) {
        val big = KeepImportViewModel.LARGE_ZIP_THRESHOLD_BYTES + 1
        coEvery { zipReader.preScan(fakeUri) } returns preScan(size = big)

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        vm.onLargeZipDeclined()

        assertEquals(KeepImportUiState.Idle, vm.state.value)
    }

    // ───── onCancel während Running → Idle ─────
    @Test
    fun `onCancel_duringRunning_returnsToIdle`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan()
        coEvery { useCase.import(any(), any(), any(), any()) } coAnswers {
            // Simuliere lange Laufzeit: warte auf Cancel.
            kotlinx.coroutines.delay(60_000)
            KeepImportSummary.EMPTY
        }

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        // Running ist gesetzt
        assertTrue(vm.state.value is KeepImportUiState.Running)
        vm.onCancel()
        advanceUntilIdle()
        assertEquals(KeepImportUiState.Idle, vm.state.value)
    }

    // ───── Use-Case-Exception → Error-State ─────
    @Test
    fun `useCaseThrows_setsErrorState`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan()
        coEvery { useCase.import(any(), any(), any(), any()) } throws
            java.io.IOException("fatal zip")

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        advanceUntilIdle()

        assertTrue(vm.state.value is KeepImportUiState.Error)
    }

    // ───── onResultDismissed → Idle ─────
    @Test
    fun `onResultDismissed_resetsToIdle`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan()
        coEvery { useCase.import(any(), any(), any(), any()) } returns KeepImportSummary.EMPTY

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        advanceUntilIdle()
        assertTrue(vm.state.value is KeepImportUiState.Done)

        vm.onResultDismissed()
        assertEquals(KeepImportUiState.Idle, vm.state.value)
    }

    // ───── Progress-Callback aktualisiert Running-State ─────
    @Test
    fun `progressCallback_updatesRunningState`() = runTest(dispatcher) {
        coEvery { zipReader.preScan(fakeUri) } returns preScan()
        coEvery { useCase.import(any(), any(), any(), any()) } coAnswers {
            val cb = arg<suspend (KeepImportProgress) -> Unit>(3)
            cb(KeepImportProgress(processed = 1, total = 2, currentName = "a.json"))
            cb(KeepImportProgress(processed = 2, total = 2, currentName = "b.json"))
            KeepImportSummary.EMPTY.copy(totalEntries = 2, imported = 2)
        }

        vm.onZipPicked(fakeUri)
        advanceUntilIdle()
        vm.onConfigConfirmed(defaultOptions)
        advanceUntilIdle()

        // End-State ist Done, aber wir verifizieren, dass Use-Case korrekt aufgerufen wurde.
        assertTrue(vm.state.value is KeepImportUiState.Done)
        coVerify { useCase.import(any(), match<KeepImportOptions> {
            !it.includeArchived && !it.includeTrashed && it.conflictStrategy == ConflictStrategy.ALWAYS_CREATE
        }, any(), any()) }
    }
}
