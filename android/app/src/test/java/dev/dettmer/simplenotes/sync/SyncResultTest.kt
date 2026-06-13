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
        assertEquals(0, result.purgedFromServerCount)
        assertFalse(result.foldersChanged)
        assertFalse(result.hasPurgedFromServer)
        assertNull(result.errorMessage)
        assertNull(result.infoMessage)
    }

    @Test
    fun `foldersChanged is propagated`() {
        val result = SyncResult(isSuccess = true, foldersChanged = true)
        assertTrue(result.foldersChanged)
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
    fun `hasPurgedFromServer is true when purgedFromServerCount greater than 0`() {
        val result = SyncResult(isSuccess = true, purgedFromServerCount = 2)
        assertTrue(result.hasPurgedFromServer)
    }

    @Test
    fun `hasPurgedFromServer is false when purgedFromServerCount is 0`() {
        val result = SyncResult(isSuccess = true, purgedFromServerCount = 0)
        assertFalse(result.hasPurgedFromServer)
    }

    @Test
    fun `full result with all fields`() {
        val result = SyncResult(
            isSuccess = true,
            syncedCount = 10,
            conflictCount = 2,
            deletedOnServerCount = 3,
            purgedFromServerCount = 5,
            errorMessage = null,
            infoMessage = "Sync complete"
        )
        assertEquals(10, result.syncedCount)
        assertEquals(2, result.conflictCount)
        assertEquals(3, result.deletedOnServerCount)
        assertEquals(5, result.purgedFromServerCount)
        assertTrue(result.hasConflicts)
        assertTrue(result.hasServerDeletions)
        assertTrue(result.hasPurgedFromServer)
    }
}
