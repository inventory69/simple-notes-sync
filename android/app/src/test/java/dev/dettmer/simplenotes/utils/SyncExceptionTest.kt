package dev.dettmer.simplenotes.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für SyncException und ValidationException.
 */
class SyncExceptionTest {

    @Test
    fun `SyncException with message only`() {
        val ex = SyncException("Sync failed")
        assertEquals("Sync failed", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun `SyncException with message and cause`() {
        val cause = RuntimeException("Connection lost")
        val ex = SyncException("Sync failed", cause)
        assertEquals("Sync failed", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `SyncException is instanceof Exception`() {
        val ex = SyncException("test")
        assertTrue(ex is Exception)
    }

    @Test
    fun `ValidationException with message`() {
        val ex = ValidationException("Invalid URL")
        assertEquals("Invalid URL", ex.message)
    }

    @Test
    fun `ValidationException is instanceof IllegalArgumentException`() {
        val ex = ValidationException("test")
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `SyncException can be caught as Exception`() {
        var caught = false
        try {
            throw SyncException("test error")
        } catch (e: Exception) {
            caught = true
            assertEquals("test error", e.message)
        }
        assertTrue(caught)
    }
}
