package dev.dettmer.simplenotes.sync.parallel

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * 🆕 v1.9.0: Unit-Tests für Content-Hash-Berechnung (Opt. 5).
 *
 * Testet:
 * - Determinismus: Gleiche Note → gleicher Hash
 * - Sensitivität: Verschiedener Content → verschiedener Hash
 * - SyncStatus-Unabhängigkeit: Hash ignoriert SyncStatus
 * - Checklist-Support: Hash berücksichtigt Checklist-Items
 * - Hash-Format: 64 Zeichen Hex-String (SHA-256)
 *
 * Hinweis: computeNoteContentHash() ist in WebDavSyncService als `internal` deklariert,
 * aber da WebDavSyncService einen Context-Parameter erfordert, wird die identische Logik
 * hier als pure JVM-Funktion repliziert (Option B gemäß Implementierungsplan §12.7).
 */
class ContentHashTest {

    /**
     * Repliziert computeNoteContentHash() aus WebDavSyncService.
     * Identische Logik: SyncStatus → SYNCED, toJson(), SHA-256.
     */
    private fun computeNoteContentHash(note: Note): String {
        val normalizedNote = note.copy(syncStatus = SyncStatus.SYNCED)
        val jsonBytes = normalizedNote.toJson().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun createNote(
        id: String = "test-id",
        title: String = "Test",
        content: String = "Hello",
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
        deviceId: String = "device-1",
        createdAt: Long = 1700000000000L,
        updatedAt: Long = 1700001000000L
    ) = Note(
        id = id,
        title = title,
        content = content,
        syncStatus = syncStatus,
        deviceId = deviceId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ═══════════════════════════════════════════════
    // Determinismus
    // ═══════════════════════════════════════════════

    @Test
    fun `same note produces same hash`() {
        val note = createNote()
        val hash1 = computeNoteContentHash(note)
        val hash2 = computeNoteContentHash(note)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hash is deterministic across multiple calls`() {
        val note = createNote(title = "Determinism Test", content = "Same content")
        val hashes = (1..10).map { computeNoteContentHash(note) }
        assertTrue("All hashes should be identical", hashes.distinct().size == 1)
    }

    // ═══════════════════════════════════════════════
    // Sensitivität
    // ═══════════════════════════════════════════════

    @Test
    fun `different title produces different hash`() {
        val note1 = createNote(title = "Title A")
        val note2 = createNote(title = "Title B")
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `different content produces different hash`() {
        val note1 = createNote(content = "Content A")
        val note2 = createNote(content = "Content B")
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `different id produces different hash`() {
        val note1 = createNote(id = "id-aaa")
        val note2 = createNote(id = "id-bbb")
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `different updatedAt produces different hash`() {
        val note1 = createNote(updatedAt = 1000L)
        val note2 = createNote(updatedAt = 2000L)
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `different createdAt produces different hash`() {
        val note1 = createNote(createdAt = 1000L)
        val note2 = createNote(createdAt = 2000L)
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `different deviceId produces different hash`() {
        val note1 = createNote(deviceId = "device-A")
        val note2 = createNote(deviceId = "device-B")
        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    // ═══════════════════════════════════════════════
    // SyncStatus-Unabhängigkeit
    // ═══════════════════════════════════════════════

    @Test
    fun `hash is independent of syncStatus`() {
        val noteLocalOnly = createNote(syncStatus = SyncStatus.LOCAL_ONLY)
        val notePending = createNote(syncStatus = SyncStatus.PENDING)
        val noteSynced = createNote(syncStatus = SyncStatus.SYNCED)
        val noteConflict = createNote(syncStatus = SyncStatus.CONFLICT)

        val hashes = listOf(noteLocalOnly, notePending, noteSynced, noteConflict)
            .map { computeNoteContentHash(it) }

        assertEquals(
            "All SyncStatus variants should produce the same hash",
            1,
            hashes.distinct().size
        )
    }

    // ═══════════════════════════════════════════════
    // Checklist-Notes
    // ═══════════════════════════════════════════════

    @Test
    fun `checklist note produces valid hash`() {
        val note = Note(
            id = "checklist-1",
            title = "Shopping",
            content = "",
            deviceId = "device-1",
            createdAt = 1700000000000L,
            updatedAt = 1700001000000L,
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("item-1", "Milk", false, 0),
                ChecklistItem("item-2", "Bread", true, 1)
            )
        )
        val hash = computeNoteContentHash(note)
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `changed checklist item produces different hash`() {
        val items1 = listOf(ChecklistItem("item-1", "Milk", false, 0))
        val items2 = listOf(ChecklistItem("item-1", "Milk", true, 0))  // isChecked changed

        val note1 = Note(
            id = "cl-1", title = "List", content = "", deviceId = "d",
            createdAt = 1000L, updatedAt = 2000L,
            noteType = NoteType.CHECKLIST, checklistItems = items1
        )
        val note2 = note1.copy(checklistItems = items2)

        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    @Test
    fun `added checklist item produces different hash`() {
        val items1 = listOf(ChecklistItem("item-1", "Milk", false, 0))
        val items2 = listOf(
            ChecklistItem("item-1", "Milk", false, 0),
            ChecklistItem("item-2", "Eggs", false, 1)
        )

        val note1 = Note(
            id = "cl-2", title = "List", content = "", deviceId = "d",
            createdAt = 1000L, updatedAt = 2000L,
            noteType = NoteType.CHECKLIST, checklistItems = items1
        )
        val note2 = note1.copy(checklistItems = items2)

        assertNotEquals(computeNoteContentHash(note1), computeNoteContentHash(note2))
    }

    // ═══════════════════════════════════════════════
    // Hash-Format
    // ═══════════════════════════════════════════════

    @Test
    fun `hash is 64 character lowercase hex string`() {
        val hash = computeNoteContentHash(createNote())
        assertEquals(64, hash.length)
        assertTrue("Hash should be lowercase hex", hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `empty content note produces valid hash`() {
        val note = createNote(title = "", content = "")
        val hash = computeNoteContentHash(note)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `large content note produces valid hash`() {
        val largeContent = "A".repeat(100_000)
        val note = createNote(content = largeContent)
        val hash = computeNoteContentHash(note)
        assertEquals(64, hash.length)
    }

    @Test
    fun `unicode content produces valid and distinct hash`() {
        val note1 = createNote(content = "Hello 🌍")
        val note2 = createNote(content = "Hello 🌎")
        val hash1 = computeNoteContentHash(note1)
        val hash2 = computeNoteContentHash(note2)
        assertEquals(64, hash1.length)
        assertNotEquals(hash1, hash2)
    }
}
