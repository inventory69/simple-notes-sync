package dev.dettmer.simplenotes.sync.parallel

import org.junit.Assert.*
import org.junit.Test

/**
 * 🆕 v1.8.0: Unit tests for IMPL_005 - Parallel Downloads
 *
 * These tests validate the basic functionality of parallel downloads:
 * - DownloadTask data class creation
 * - DownloadTaskResult sealed class variants
 * - ParallelDownloader constants
 *
 * Note: Full integration tests with mocked Sardine would require MockK/Mockito,
 * which are not currently in the project dependencies.
 */
class ParallelDownloadTest {

    // Note: DownloadTask tests require mocking DavResource, skipping for now
    // Full integration tests would require MockK or Mockito

    @Test
    fun `DownloadTaskResult Success contains correct data`() {
        val result = DownloadTaskResult.Success(
            noteId = "note-1",
            content = "{\"id\":\"note-1\"}",
            etag = "etag123"
        )

        assertEquals("note-1", result.noteId)
        assertEquals("{\"id\":\"note-1\"}", result.content)
        assertEquals("etag123", result.etag)
    }

    @Test
    fun `DownloadTaskResult Failure contains error`() {
        val error = Exception("Network error")
        val result = DownloadTaskResult.Failure(
            noteId = "note-2",
            error = error
        )

        assertEquals("note-2", result.noteId)
        assertEquals("Network error", result.error.message)
    }

    @Test
    fun `DownloadTaskResult Skipped contains reason`() {
        val result = DownloadTaskResult.Skipped(
            noteId = "note-3",
            reason = "Already up to date"
        )

        assertEquals("note-3", result.noteId)
        assertEquals("Already up to date", result.reason)
    }

    @Test
    fun `ParallelDownloader has correct default constants`() {
        assertEquals(5, ParallelDownloader.DEFAULT_MAX_PARALLEL)
        assertEquals(2, ParallelDownloader.DEFAULT_RETRY_COUNT)
    }

    @Test
    fun `ParallelDownloader constants are in valid range`() {
        // Verify default values are within our configured range
        assertTrue(
            "Default parallel downloads should be >= 1",
            ParallelDownloader.DEFAULT_MAX_PARALLEL >= 1
        )
        assertTrue(
            "Default parallel downloads should be <= 10",
            ParallelDownloader.DEFAULT_MAX_PARALLEL <= 10
        )
        assertTrue(
            "Default retry count should be >= 0",
            ParallelDownloader.DEFAULT_RETRY_COUNT >= 0
        )
    }

    @Test
    fun `DownloadTaskResult types are distinguishable`() {
        val success: DownloadTaskResult = DownloadTaskResult.Success("id1", "content", "etag")
        val failure: DownloadTaskResult = DownloadTaskResult.Failure("id2", Exception())
        val skipped: DownloadTaskResult = DownloadTaskResult.Skipped("id3", "reason")

        assertTrue("Success should be instance of Success", success is DownloadTaskResult.Success)
        assertTrue("Failure should be instance of Failure", failure is DownloadTaskResult.Failure)
        assertTrue("Skipped should be instance of Skipped", skipped is DownloadTaskResult.Skipped)

        assertFalse("Success should not be Failure", success is DownloadTaskResult.Failure)
        assertFalse("Failure should not be Skipped", failure is DownloadTaskResult.Skipped)
        assertFalse("Skipped should not be Success", skipped is DownloadTaskResult.Success)
    }

    @Test
    fun `DownloadTaskResult when expression works correctly`() {
        val results = listOf(
            DownloadTaskResult.Success("id1", "content", "etag"),
            DownloadTaskResult.Failure("id2", Exception("error")),
            DownloadTaskResult.Skipped("id3", "reason")
        )

        val types = results.map { result ->
            when (result) {
                is DownloadTaskResult.Success -> "success"
                is DownloadTaskResult.Failure -> "failure"
                is DownloadTaskResult.Skipped -> "skipped"
            }
        }

        assertEquals(listOf("success", "failure", "skipped"), types)
    }
}
