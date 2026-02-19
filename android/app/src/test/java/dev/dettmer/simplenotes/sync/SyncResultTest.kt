package dev.dettmer.simplenotes.sync

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für SyncResult.
 */
class SyncResultTest {

    @Test
    fun `successful result with defaults`() {
        val result = SyncResult(isSuccess = true)
        assertTrue(result.isSuccess)
        assertEquals(0, result.syncedCount)
        assertEquals(0, result.conflictCount)
        assertEquals(0, result.deletedOnServerCount)
        assertNull(result.errorMessage)
        assertNull(result.infoMessage)
    }

    @Test
    fun `hasConflicts is true when conflictCount greater than 0`() {
        val result = SyncResult(isSuccess = true, conflictCount = 2)
        assertTrue(result.hasConflicts)
    }

    @Test
    fun `hasConflicts is false when conflictCount is 0`() {
        val result = SyncResult(isSuccess = true, conflictCount = 0)
        assertFalse(result.hasConflicts)
    }

    @Test
    fun `hasServerDeletions is true when deletedOnServerCount greater than 0`() {
        val result = SyncResult(isSuccess = true, deletedOnServerCount = 1)
        assertTrue(result.hasServerDeletions)
    }

    @Test
    fun `hasServerDeletions is false when deletedOnServerCount is 0`() {
        val result = SyncResult(isSuccess = true, deletedOnServerCount = 0)
        assertFalse(result.hasServerDeletions)
    }

    @Test
    fun `error result with message`() {
        val result = SyncResult(
            isSuccess = false,
            errorMessage = "Connection failed"
        )
        assertFalse(result.isSuccess)
        assertEquals("Connection failed", result.errorMessage)
    }

    @Test
    fun `infoMessage is preserved`() {
        val result = SyncResult(
            isSuccess = true,
            syncedCount = 5,
            infoMessage = "5 notes synced"
        )
        assertEquals("5 notes synced", result.infoMessage)
    }

    @Test
    fun `full result with all fields`() {
        val result = SyncResult(
            isSuccess = true,
            syncedCount = 10,
            conflictCount = 2,
            deletedOnServerCount = 3,
            errorMessage = null,
            infoMessage = "Sync complete"
        )
        assertEquals(10, result.syncedCount)
        assertEquals(2, result.conflictCount)
        assertEquals(3, result.deletedOnServerCount)
        assertTrue(result.hasConflicts)
        assertTrue(result.hasServerDeletions)
    }
}
