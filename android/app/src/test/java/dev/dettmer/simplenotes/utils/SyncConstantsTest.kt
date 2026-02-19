package dev.dettmer.simplenotes.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für SyncConstants und Constants.
 * Stellt sicher, dass wichtige Konstanten korrekte Werte haben.
 */
class SyncConstantsTest {

    // ═══════════════════════════════════════════════
    // SyncConstants
    // ═══════════════════════════════════════════════

    @Test
    fun `search debounce is 300ms`() {
        assertEquals(300L, SyncConstants.SEARCH_DEBOUNCE_MS)
    }

    @Test
    fun `sync debounce is 500ms`() {
        assertEquals(500L, SyncConstants.SYNC_DEBOUNCE_MS)
    }

    @Test
    fun `connection test timeout is 5000ms`() {
        assertEquals(5000L, SyncConstants.CONNECTION_TEST_TIMEOUT_MS)
    }

    // ═══════════════════════════════════════════════
    // Constants (SharedPrefs Keys & Defaults)
    // ═══════════════════════════════════════════════

    @Test
    fun `default sync interval is 30 minutes`() {
        assertEquals(30L, Constants.DEFAULT_SYNC_INTERVAL_MINUTES)
    }

    @Test
    fun `sync warning threshold is 24 hours`() {
        assertEquals(24 * 60 * 60 * 1000L, Constants.SYNC_WARNING_THRESHOLD_MS)
    }

    @Test
    fun `default display mode is grid`() {
        assertEquals("grid", Constants.DEFAULT_DISPLAY_MODE)
    }

    @Test
    fun `default parallel downloads is 5`() {
        assertEquals(5, Constants.DEFAULT_MAX_PARALLEL_DOWNLOADS)
    }

    @Test
    fun `parallel downloads range is 1-10`() {
        assertEquals(1, Constants.MIN_PARALLEL_DOWNLOADS)
        assertEquals(10, Constants.MAX_PARALLEL_DOWNLOADS)
    }

    @Test
    fun `default sort option is updatedAt`() {
        assertEquals("updatedAt", Constants.DEFAULT_SORT_OPTION)
    }

    @Test
    fun `default sort direction is desc`() {
        assertEquals("desc", Constants.DEFAULT_SORT_DIRECTION)
    }

    @Test
    fun `global sync cooldown is 30 seconds`() {
        assertEquals(30_000L, Constants.MIN_GLOBAL_SYNC_INTERVAL_MS)
    }

    @Test
    fun `onSave throttle is 5 seconds`() {
        assertEquals(5_000L, Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS)
    }

    @Test
    fun `default trigger values`() {
        assertTrue(Constants.DEFAULT_TRIGGER_ON_SAVE)
        assertTrue(Constants.DEFAULT_TRIGGER_ON_RESUME)
        assertTrue(Constants.DEFAULT_TRIGGER_WIFI_CONNECT)
        assertFalse(Constants.DEFAULT_TRIGGER_PERIODIC)
        assertFalse(Constants.DEFAULT_TRIGGER_BOOT)
    }

    @Test
    fun `default wifi-only sync is false`() {
        assertFalse(Constants.DEFAULT_WIFI_ONLY_SYNC)
    }

    @Test
    fun `grid columns is 2`() {
        assertEquals(2, Constants.GRID_COLUMNS)
    }
}
