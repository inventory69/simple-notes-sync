package dev.dettmer.simplenotes.sync

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für SyncProgress und SyncPhase.
 */
class SyncProgressTest {

    // ═══════════════════════════════════════════════
    // SyncPhase
    // ═══════════════════════════════════════════════

    @Test
    fun `SyncPhase has all 8 values`() {
        assertEquals(8, SyncPhase.entries.size)
    }

    @Test
    fun `SyncPhase values exist`() {
        assertNotNull(SyncPhase.IDLE)
        assertNotNull(SyncPhase.PREPARING)
        assertNotNull(SyncPhase.UPLOADING)
        assertNotNull(SyncPhase.DOWNLOADING)
        assertNotNull(SyncPhase.IMPORTING_MARKDOWN)
        assertNotNull(SyncPhase.COMPLETED)
        assertNotNull(SyncPhase.ERROR)
        assertNotNull(SyncPhase.INFO)
    }

    // ═══════════════════════════════════════════════
    // progress & percentComplete
    // ═══════════════════════════════════════════════

    @Test
    fun `progress is 0 when total is 0`() {
        val p = SyncProgress(phase = SyncPhase.DOWNLOADING, current = 0, total = 0)
        assertEquals(0f, p.progress, 0.001f)
        assertEquals(0, p.percentComplete)
    }

    @Test
    fun `progress is calculated correctly`() {
        val p = SyncProgress(phase = SyncPhase.DOWNLOADING, current = 5, total = 10)
        assertEquals(0.5f, p.progress, 0.001f)
        assertEquals(50, p.percentComplete)
    }

    @Test
    fun `progress at 100 percent`() {
        val p = SyncProgress(phase = SyncPhase.DOWNLOADING, current = 10, total = 10)
        assertEquals(1.0f, p.progress, 0.001f)
        assertEquals(100, p.percentComplete)
    }

    @Test
    fun `progress at 1 of 3`() {
        val p = SyncProgress(phase = SyncPhase.UPLOADING, current = 1, total = 3)
        assertEquals(33, p.percentComplete)
    }

    // ═══════════════════════════════════════════════
    // estimatedRemainingMs
    // ═══════════════════════════════════════════════

    @Test
    fun `estimatedRemainingMs is null when current is 0`() {
        val p = SyncProgress(phase = SyncPhase.DOWNLOADING, current = 0, total = 10)
        assertNull(p.estimatedRemainingMs)
    }

    @Test
    fun `estimatedRemainingMs is null when total is 0`() {
        val p = SyncProgress(phase = SyncPhase.DOWNLOADING, current = 5, total = 0)
        assertNull(p.estimatedRemainingMs)
    }

    @Test
    fun `estimatedRemainingMs returns positive value during active sync`() {
        val startTime = System.currentTimeMillis() - 5000 // 5s ago
        val p = SyncProgress(
            phase = SyncPhase.DOWNLOADING,
            current = 5,
            total = 10,
            startTime = startTime
        )
        val remaining = p.estimatedRemainingMs
        assertNotNull(remaining)
        assertTrue(remaining!! > 0)
    }

    // ═══════════════════════════════════════════════
    // isVisible
    // ═══════════════════════════════════════════════

    @Test
    fun `IDLE is not visible`() {
        assertFalse(SyncProgress.IDLE.isVisible)
    }

    @Test
    fun `PREPARING is visible when not silent`() {
        val p = SyncProgress(phase = SyncPhase.PREPARING, silent = false)
        assertTrue(p.isVisible)
    }

    @Test
    fun `PREPARING is not visible when silent`() {
        val p = SyncProgress(phase = SyncPhase.PREPARING, silent = true)
        assertFalse(p.isVisible)
    }

    @Test
    fun `INFO is always visible regardless of silent`() {
        val p = SyncProgress(phase = SyncPhase.INFO, silent = true)
        assertTrue(p.isVisible)
    }

    @Test
    fun `ERROR is visible when not silent`() {
        val p = SyncProgress(phase = SyncPhase.ERROR, silent = false)
        assertTrue(p.isVisible)
    }

    @Test
    fun `COMPLETED is visible when not silent`() {
        val p = SyncProgress(phase = SyncPhase.COMPLETED, silent = false)
        assertTrue(p.isVisible)
    }

    // ═══════════════════════════════════════════════
    // isActiveSync
    // ═══════════════════════════════════════════════

    @Test
    fun `PREPARING is active sync`() {
        assertTrue(SyncProgress(phase = SyncPhase.PREPARING).isActiveSync)
    }

    @Test
    fun `UPLOADING is active sync`() {
        assertTrue(SyncProgress(phase = SyncPhase.UPLOADING).isActiveSync)
    }

    @Test
    fun `DOWNLOADING is active sync`() {
        assertTrue(SyncProgress(phase = SyncPhase.DOWNLOADING).isActiveSync)
    }

    @Test
    fun `IMPORTING_MARKDOWN is active sync`() {
        assertTrue(SyncProgress(phase = SyncPhase.IMPORTING_MARKDOWN).isActiveSync)
    }

    @Test
    fun `IDLE is NOT active sync`() {
        assertFalse(SyncProgress(phase = SyncPhase.IDLE).isActiveSync)
    }

    @Test
    fun `COMPLETED is NOT active sync`() {
        assertFalse(SyncProgress(phase = SyncPhase.COMPLETED).isActiveSync)
    }

    @Test
    fun `ERROR is NOT active sync`() {
        assertFalse(SyncProgress(phase = SyncPhase.ERROR).isActiveSync)
    }

    @Test
    fun `INFO is NOT active sync`() {
        assertFalse(SyncProgress(phase = SyncPhase.INFO).isActiveSync)
    }

    // ═══════════════════════════════════════════════
    // IDLE companion
    // ═══════════════════════════════════════════════

    @Test
    fun `IDLE has correct defaults`() {
        val idle = SyncProgress.IDLE
        assertEquals(SyncPhase.IDLE, idle.phase)
        assertEquals(0, idle.current)
        assertEquals(0, idle.total)
        assertNull(idle.currentFileName)
        assertNull(idle.resultMessage)
        assertFalse(idle.silent)
    }
}
