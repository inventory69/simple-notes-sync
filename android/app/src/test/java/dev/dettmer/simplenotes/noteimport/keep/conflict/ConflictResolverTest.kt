package dev.dettmer.simplenotes.noteimport.keep.conflict

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.5.0 — Tests #27 bis #32 aus Analyseplan §5.1.
 *
 * Wir mocken `NotesStorage` mit MockK (bereits Pflicht-Test-Lib im Projekt).
 */
class ConflictResolverTest {

    private fun textNote(
        id: String = "n1",
        title: String = "T",
        content: String = "Hello",
    ) = Note(
        id = id, title = title, content = content,
        createdAt = 1L, updatedAt = 2L, deviceId = "dev",
        syncStatus = SyncStatus.LOCAL_ONLY, noteType = NoteType.TEXT,
    )

    private fun checklistNote(
        id: String = "c1",
        title: String = "L",
        items: List<ChecklistItem>,
    ) = Note(
        id = id, title = title, content = "",
        createdAt = 1L, updatedAt = 2L, deviceId = "dev",
        syncStatus = SyncStatus.LOCAL_ONLY, noteType = NoteType.CHECKLIST,
        checklistItems = items,
    )

    private fun item(text: String, checked: Boolean, order: Int) = ChecklistItem(
        id = "i$order", text = text, isChecked = checked, order = order, originalOrder = order,
    )

    // ───── #27 ───────────────────────────────────────────────────────
    @Test
    fun `resolve_alwaysCreate_returnsCreate`() = runBlocking {
        val storage = mockk<NotesStorage>()
        // ALWAYS_CREATE darf storage.loadAllNotes() gar nicht erst aufrufen → kein coEvery nötig.
        val resolver = ConflictResolver(storage)
        val r = resolver.resolve(textNote(), ConflictStrategy.ALWAYS_CREATE)
        assertEquals(ConflictResolver.Resolution.Create, r)
    }

    // ───── #28 ───────────────────────────────────────────────────────
    @Test
    fun `resolve_skip_existingHashMatch_returnsSkip`() = runBlocking {
        val candidate = textNote(id = "new", title = "T", content = "Hello")
        val existing = textNote(id = "old", title = "T", content = "Hello")
        val storage = mockk<NotesStorage> { coEvery { loadAllNotes() } returns listOf(existing) }
        val resolver = ConflictResolver(storage)
        val r = resolver.resolve(candidate, ConflictStrategy.SKIP)
        assertTrue(r is ConflictResolver.Resolution.Skip)
    }

    // ───── #29 ───────────────────────────────────────────────────────
    @Test
    fun `resolve_skip_noMatch_returnsCreate`() = runBlocking {
        val candidate = textNote(title = "T", content = "Hello")
        val existing = textNote(id = "old", title = "T", content = "Different")
        val storage = mockk<NotesStorage> { coEvery { loadAllNotes() } returns listOf(existing) }
        val resolver = ConflictResolver(storage)
        val r = resolver.resolve(candidate, ConflictStrategy.SKIP)
        assertEquals(ConflictResolver.Resolution.Create, r)
    }

    // ───── #30 ───────────────────────────────────────────────────────
    @Test
    fun `resolve_replace_existingMatch_returnsReplaceWithExistingId`() = runBlocking {
        val candidate = textNote(id = "new", title = "T", content = "Hello")
        val existing = textNote(id = "OLD-ID", title = "T", content = "Hello")
        val storage = mockk<NotesStorage> { coEvery { loadAllNotes() } returns listOf(existing) }
        val resolver = ConflictResolver(storage)
        val r = resolver.resolve(candidate, ConflictStrategy.REPLACE)
        assertEquals(ConflictResolver.Resolution.Replace(existingId = "OLD-ID"), r)
    }

    // ───── #31 ───────────────────────────────────────────────────────
    @Test
    fun `computeContentHash_emptyTitleSameContent_consistent`() {
        val storage = mockk<NotesStorage>()
        val resolver = ConflictResolver(storage)
        val a = textNote(title = "", content = "abc")
        val b = textNote(id = "other", title = "", content = "abc")
        assertEquals(resolver.computeContentHash(a), resolver.computeContentHash(b))
    }

    // ───── #32 ───────────────────────────────────────────────────────
    @Test
    fun `computeContentHash_checklistOrderMatters`() {
        val storage = mockk<NotesStorage>()
        val resolver = ConflictResolver(storage)
        val a = checklistNote(items = listOf(
            item("Milch", false, 0),
            item("Brot", false, 1),
        ))
        val b = checklistNote(items = listOf(
            item("Brot", false, 0),
            item("Milch", false, 1),
        ))
        assertNotEquals(resolver.computeContentHash(a), resolver.computeContentHash(b))
    }

    // ───── Defensiv: storage.loadAllNotes wirft → Create ────────────
    @Test
    fun `resolve_storageThrows_returnsCreate`() = runBlocking {
        val storage = mockk<NotesStorage> {
            coEvery { loadAllNotes() } throws RuntimeException("disk full")
        }
        val resolver = ConflictResolver(storage)
        val r = resolver.resolve(textNote(), ConflictStrategy.SKIP)
        assertEquals(ConflictResolver.Resolution.Create, r)
    }

    // ───── Defensiv: Whitespace-Toleranz für TEXT ───────────────────
    @Test
    fun `computeContentHash_textTrimsTrailingWhitespace`() {
        val storage = mockk<NotesStorage>()
        val resolver = ConflictResolver(storage)
        val a = textNote(content = "Hello")
        val b = textNote(content = "Hello   \n")
        assertEquals(resolver.computeContentHash(a), resolver.computeContentHash(b))
    }
}
