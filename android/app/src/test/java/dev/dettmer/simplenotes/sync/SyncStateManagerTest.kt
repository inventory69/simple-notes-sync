package dev.dettmer.simplenotes.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit-Tests für SyncStateManager.
 *
 * Hinweis: SyncStateManager ist ein Singleton-Objekt.
 * Tests müssen den State nach jedem Test zurücksetzen.
 * InstantTaskExecutorRule sorgt dafür, dass LiveData.postValue() synchron ausgeführt wird.
 */
class SyncStateManagerTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        SyncStateManager.reset()
    }

    @After
    fun teardown() {
        SyncStateManager.reset()
    }

    // ═══════════════════════════════════════════════
    // Initial State
    // ═══════════════════════════════════════════════

    @Test
    fun `initial state is not syncing`() {
        assertFalse(SyncStateManager.isSyncing)
    }

    @Test
    fun `initial progress is IDLE`() {
        assertEquals(SyncPhase.IDLE, SyncStateManager.syncProgress.value.phase)
    }

    // ═══════════════════════════════════════════════
    // tryStartSync
    // ═══════════════════════════════════════════════

    @Test
    fun `tryStartSync succeeds when idle`() {
        assertTrue(SyncStateManager.tryStartSync("test"))
        assertTrue(SyncStateManager.isSyncing)
    }

    @Test
    fun `tryStartSync fails when already syncing`() {
        SyncStateManager.tryStartSync("first")
        assertFalse(SyncStateManager.tryStartSync("second"))
    }

    @Test
    fun `tryStartSync sets PREPARING phase`() {
        SyncStateManager.tryStartSync("test")
        assertEquals(SyncPhase.PREPARING, SyncStateManager.syncProgress.value.phase)
    }

    @Test
    fun `tryStartSync silent sets silent flag`() {
        SyncStateManager.tryStartSync("test", silent = true)
        assertTrue(SyncStateManager.syncProgress.value.silent)
    }

    @Test
    fun `tryStartSync non-silent sets silent false`() {
        SyncStateManager.tryStartSync("test", silent = false)
        assertFalse(SyncStateManager.syncProgress.value.silent)
    }

    @Test
    fun `tryStartSync sets startTime`() {
        val before = System.currentTimeMillis()
        SyncStateManager.tryStartSync("test")
        val after = System.currentTimeMillis()
        val startTime = SyncStateManager.syncProgress.value.startTime
        assertTrue(startTime >= before)
        assertTrue(startTime <= after)
    }

    // ═══════════════════════════════════════════════
    // markCompleted
    // ═══════════════════════════════════════════════

    @Test
    fun `markCompleted after sync sets COMPLETED`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.markCompleted("Done")
        assertEquals(SyncPhase.COMPLETED, SyncStateManager.syncProgress.value.phase)
    }

    @Test
    fun `markCompleted preserves result message`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.markCompleted("5 notes synced")
        assertEquals("5 notes synced", SyncStateManager.syncProgress.value.resultMessage)
    }

    @Test
    fun `markCompleted silent sync resets to IDLE`() {
        SyncStateManager.tryStartSync("test", silent = true)
        SyncStateManager.markCompleted("Done")
        assertEquals(SyncPhase.IDLE, SyncStateManager.syncProgress.value.phase)
    }

    @Test
    fun `markCompleted allows new sync`() {
        SyncStateManager.tryStartSync("first")
        SyncStateManager.markCompleted()
        // After completion, isSyncing should be false
        assertFalse(SyncStateManager.isSyncing)
    }

    // ═══════════════════════════════════════════════
    // markError
    // ═══════════════════════════════════════════════

    @Test
    fun `markError sets ERROR phase`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.markError("Connection failed")
        assertEquals(SyncPhase.ERROR, SyncStateManager.syncProgress.value.phase)
    }

    @Test
    fun `markError preserves error message`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.markError("Timeout")
        assertEquals("Timeout", SyncStateManager.syncProgress.value.resultMessage)
    }

    @Test
    fun `markError stops syncing`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.markError("Error")
        assertFalse(SyncStateManager.isSyncing)
    }

    // ═══════════════════════════════════════════════
    // updateProgress
    // ═══════════════════════════════════════════════

    @Test
    fun `updateProgress updates phase and counts`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 3, total = 10)

        val progress = SyncStateManager.syncProgress.value
        assertEquals(SyncPhase.DOWNLOADING, progress.phase)
        assertEquals(3, progress.current)
        assertEquals(10, progress.total)
    }

    @Test
    fun `updateProgress preserves silent flag`() {
        SyncStateManager.tryStartSync("test", silent = true)
        SyncStateManager.updateProgress(SyncPhase.UPLOADING, current = 1, total = 5)
        assertTrue(SyncStateManager.syncProgress.value.silent)
    }

    @Test
    fun `updateProgress sets currentFileName`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 1, total = 5, currentFileName = "note1.json")
        assertEquals("note1.json", SyncStateManager.syncProgress.value.currentFileName)
    }

    // ═══════════════════════════════════════════════
    // incrementProgress
    // ═══════════════════════════════════════════════

    @Test
    fun `incrementProgress increases current by 1`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 2, total = 10)
        SyncStateManager.incrementProgress()

        assertEquals(3, SyncStateManager.syncProgress.value.current)
    }

    @Test
    fun `incrementProgress updates currentFileName`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 0, total = 5)
        SyncStateManager.incrementProgress("note-xyz.json")

        assertEquals("note-xyz.json", SyncStateManager.syncProgress.value.currentFileName)
    }

    // ═══════════════════════════════════════════════
    // promoteToVisible
    // ═══════════════════════════════════════════════

    @Test
    fun `promoteToVisible changes silent sync to visible`() {
        SyncStateManager.tryStartSync("test", silent = true)
        assertTrue(SyncStateManager.syncProgress.value.silent)

        val result = SyncStateManager.promoteToVisible()
        assertTrue(result)
        assertFalse(SyncStateManager.syncProgress.value.silent)
    }

    @Test
    fun `promoteToVisible returns false when not silent`() {
        SyncStateManager.tryStartSync("test", silent = false)
        assertFalse(SyncStateManager.promoteToVisible())
    }

    @Test
    fun `promoteToVisible returns false when idle`() {
        assertFalse(SyncStateManager.promoteToVisible())
    }

    // ═══════════════════════════════════════════════
    // reset
    // ═══════════════════════════════════════════════

    @Test
    fun `reset clears sync state`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 5, total = 10)

        SyncStateManager.reset()

        assertFalse(SyncStateManager.isSyncing)
        assertEquals(SyncPhase.IDLE, SyncStateManager.syncProgress.value.phase)
    }

    // ═══════════════════════════════════════════════
    // showInfo / showError
    // ═══════════════════════════════════════════════

    @Test
    fun `showInfo sets INFO phase with message`() {
        SyncStateManager.showInfo("No notes to sync")
        assertEquals(SyncPhase.INFO, SyncStateManager.syncProgress.value.phase)
        assertEquals("No notes to sync", SyncStateManager.syncProgress.value.resultMessage)
    }

    @Test
    fun `showInfo suppressed during active sync`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.updateProgress(SyncPhase.DOWNLOADING, current = 1, total = 10)

        SyncStateManager.showInfo("This should be suppressed")
        // Phase should still be DOWNLOADING, not INFO
        assertEquals(SyncPhase.DOWNLOADING, SyncStateManager.syncProgress.value.phase)
    }

    @Test
    fun `showError sets ERROR phase with message when idle`() {
        SyncStateManager.showError("Configuration error")
        assertEquals(SyncPhase.ERROR, SyncStateManager.syncProgress.value.phase)
        assertEquals("Configuration error", SyncStateManager.syncProgress.value.resultMessage)
    }

    @Test
    fun `showError suppressed during active sync`() {
        SyncStateManager.tryStartSync("test")
        SyncStateManager.showError("Should not overwrite sync")
        // Phase should still be PREPARING
        assertEquals(SyncPhase.PREPARING, SyncStateManager.syncProgress.value.phase)
    }
}
