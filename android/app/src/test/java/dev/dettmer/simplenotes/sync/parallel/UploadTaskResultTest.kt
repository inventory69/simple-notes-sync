package dev.dettmer.simplenotes.sync.parallel

import org.junit.Assert.*
import org.junit.Test

/**
 * 🆕 v1.9.0: Unit-Tests für UploadTaskResult sealed class.
 *
 * Testet:
 * - Data class Konstruktion und Feld-Zugriff
 * - Sealed class Typ-Unterscheidung (when expression)
 * - Equality und Copy
 * - Filtering / Counting (wie in uploadLocalNotes verwendet)
 */
class UploadTaskResultTest {

    // ═══════════════════════════════════════════════
    // Success
    // ═══════════════════════════════════════════════

    @Test
    fun `Success contains correct noteId and etag`() {
        val result = UploadTaskResult.Success(
            noteId = "abc-123",
            etag = "\"etag-value\""
        )
        assertEquals("abc-123", result.noteId)
        assertEquals("\"etag-value\"", result.etag)
    }

    @Test
    fun `Success with null etag`() {
        val result = UploadTaskResult.Success(
            noteId = "abc-123",
            etag = null
        )
        assertEquals("abc-123", result.noteId)
        assertNull(result.etag)
    }

    // ═══════════════════════════════════════════════
    // Failure
    // ═══════════════════════════════════════════════

    @Test
    fun `Failure contains noteId and error`() {
        val error = java.io.IOException("Connection refused")
        val result = UploadTaskResult.Failure(
            noteId = "def-456",
            error = error
        )
        assertEquals("def-456", result.noteId)
        assertEquals("Connection refused", result.error.message)
        assertTrue(result.error is java.io.IOException)
    }

    // ═══════════════════════════════════════════════
    // Skipped
    // ═══════════════════════════════════════════════

    @Test
    fun `Skipped contains noteId and reason`() {
        val result = UploadTaskResult.Skipped(
            noteId = "ghi-789",
            reason = "Content unchanged (hash match)"
        )
        assertEquals("ghi-789", result.noteId)
        assertEquals("Content unchanged (hash match)", result.reason)
    }

    // ═══════════════════════════════════════════════
    // Typ-Unterscheidung
    // ═══════════════════════════════════════════════

    @Test
    fun `types are distinguishable via is-checks`() {
        val success: UploadTaskResult = UploadTaskResult.Success("id1", "etag")
        val failure: UploadTaskResult = UploadTaskResult.Failure("id2", Exception("err"))
        val skipped: UploadTaskResult = UploadTaskResult.Skipped("id3", "reason")

        assertTrue(success is UploadTaskResult.Success)
        assertFalse(success is UploadTaskResult.Failure)
        assertFalse(success is UploadTaskResult.Skipped)

        assertTrue(failure is UploadTaskResult.Failure)
        assertFalse(failure is UploadTaskResult.Success)

        assertTrue(skipped is UploadTaskResult.Skipped)
        assertFalse(skipped is UploadTaskResult.Success)
    }

    @Test
    fun `when expression covers all types`() {
        val results = listOf(
            UploadTaskResult.Success("id1", "etag"),
            UploadTaskResult.Failure("id2", Exception("error")),
            UploadTaskResult.Skipped("id3", "reason")
        )

        val labels = results.map { result ->
            when (result) {
                is UploadTaskResult.Success -> "success"
                is UploadTaskResult.Failure -> "failure"
                is UploadTaskResult.Skipped -> "skipped"
            }
        }

        assertEquals(listOf("success", "failure", "skipped"), labels)
    }

    // ═══════════════════════════════════════════════
    // Filtering / Counting (wie in uploadLocalNotes verwendet)
    // ═══════════════════════════════════════════════

    @Test
    fun `filterIsInstance counts correctly for mixed results`() {
        val results: List<UploadTaskResult> = listOf(
            UploadTaskResult.Success("a", "e1"),
            UploadTaskResult.Success("b", "e2"),
            UploadTaskResult.Failure("c", Exception()),
            UploadTaskResult.Skipped("d", "unchanged"),
            UploadTaskResult.Skipped("e", "unchanged")
        )

        assertEquals(2, results.count { it is UploadTaskResult.Success })
        assertEquals(1, results.count { it is UploadTaskResult.Failure })
        assertEquals(2, results.count { it is UploadTaskResult.Skipped })

        val successIds = results.filterIsInstance<UploadTaskResult.Success>().map { it.noteId }.toSet()
        assertEquals(setOf("a", "b"), successIds)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = UploadTaskResult.Success("id1", "etag1")
        val b = UploadTaskResult.Success("id1", "etag1")
        val c = UploadTaskResult.Success("id1", "etag2")

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `successfulNoteIds extraction works like in uploadLocalNotes`() {
        val results: List<UploadTaskResult> = listOf(
            UploadTaskResult.Success("note-1", null),
            UploadTaskResult.Success("note-2", "etag"),
            UploadTaskResult.Failure("note-3", Exception("err")),
            UploadTaskResult.Skipped("note-4", "unchanged")
        )

        val successfulNoteIds = results
            .filterIsInstance<UploadTaskResult.Success>()
            .map { it.noteId }
            .toSet()

        assertEquals(setOf("note-1", "note-2"), successfulNoteIds)
        assertFalse(successfulNoteIds.contains("note-3"))
        assertFalse(successfulNoteIds.contains("note-4"))
    }

    @Test
    fun `Failure stores correct error type and message`() {
        val originalError = RuntimeException("Upload timeout")
        val result = UploadTaskResult.Failure("note-xyz", originalError)

        assertTrue(result.error is RuntimeException)
        assertEquals("Upload timeout", result.error.message)
        assertEquals("note-xyz", result.noteId)
    }
}
