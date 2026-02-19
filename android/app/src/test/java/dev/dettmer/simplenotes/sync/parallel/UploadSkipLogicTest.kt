package dev.dettmer.simplenotes.sync.parallel

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * 🆕 v1.9.0: Unit-Tests für Upload-Skip-Logik (Opt. 5 + 6).
 *
 * Testet die Entscheidungslogik, ob ein Upload übersprungen werden kann:
 * - Content-Hash-Vergleich (JSON Upload-Skip, Opt. 5)
 * - MD-Hash-Vergleich (Markdown Upload-Skip, Opt. 6)
 * - Edge Cases: Kein gecachter Hash, kein E-Tag, Hash-Mismatch
 * - Kombination beider Skip-Mechanismen
 *
 * SharedPreferences wird durch eine einfache Map simuliert (kein Android-Context nötig).
 */
class UploadSkipLogicTest {

    // Simuliert SharedPreferences als einfache Map
    private lateinit var prefsCache: MutableMap<String, String?>

    @Before
    fun setup() {
        prefsCache = mutableMapOf()
    }

    // ═══════════════════════════════════════════════
    // Hilfsfunktionen (replizieren App-Logik)
    // ═══════════════════════════════════════════════

    /**
     * Repliziert computeNoteContentHash() aus WebDavSyncService
     */
    private fun computeHash(note: Note): String {
        val normalized = note.copy(syncStatus = SyncStatus.SYNCED)
        val jsonBytes = normalized.toJson().toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Repliziert MD-Hash-Berechnung aus exportToMarkdown()
     */
    private fun computeMdHash(mdContent: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(mdContent.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Repliziert die Skip-Entscheidung aus uploadSingleNoteParallel() (Opt. 5):
     * Überspringen wenn Hash und E-Tag übereinstimmen.
     * @return true wenn Upload übersprungen werden kann
     */
    private fun shouldSkipJsonUpload(note: Note): Boolean {
        val currentHash = computeHash(note)
        val cachedHash = prefsCache["content_hash_${note.id}"]
        val cachedETag = prefsCache["etag_json_${note.id}"]
        return currentHash == cachedHash && cachedETag != null
    }

    /**
     * Repliziert die Skip-Entscheidung aus exportToMarkdown() (Opt. 6):
     * Überspringen wenn MD-Hash und MD-E-Tag übereinstimmen.
     * @return true wenn MD-Upload übersprungen werden kann
     */
    private fun shouldSkipMdUpload(note: Note): Boolean {
        val mdContent = note.toMarkdown()
        val mdHash = computeMdHash(mdContent)
        val cachedMdHash = prefsCache["content_hash_md_${note.id}"]
        val cachedMdETag = prefsCache["etag_md_${note.id}"]
        return mdHash == cachedMdHash && cachedMdETag != null
    }

    private fun createNote(
        id: String = "test-id",
        title: String = "Test",
        content: String = "Hello World",
        syncStatus: SyncStatus = SyncStatus.PENDING,
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
    // JSON Upload-Skip (Opt. 5)
    // ═══════════════════════════════════════════════

    @Test
    fun `skip when hash matches and etag exists`() {
        val note = createNote()
        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"etag-abc\""

        assertTrue("Should skip: content unchanged, E-Tag cached", shouldSkipJsonUpload(note))
    }

    @Test
    fun `no skip when hash matches but etag is missing`() {
        val note = createNote()
        prefsCache["content_hash_${note.id}"] = computeHash(note)
        // Kein E-Tag gecacht

        assertFalse("Should not skip: no E-Tag means server state unknown", shouldSkipJsonUpload(note))
    }

    @Test
    fun `no skip when hash does not match`() {
        val note = createNote(content = "New content")
        prefsCache["content_hash_${note.id}"] = "old-hash-value-that-does-not-match"
        prefsCache["etag_json_${note.id}"] = "\"etag-abc\""

        assertFalse("Should not skip: content changed", shouldSkipJsonUpload(note))
    }

    @Test
    fun `no skip when no cached hash exists`() {
        val note = createNote()
        prefsCache["etag_json_${note.id}"] = "\"etag-abc\""
        // Kein cached hash

        assertFalse("Should not skip: first upload, no cached hash", shouldSkipJsonUpload(note))
    }

    @Test
    fun `no skip when both hash and etag are missing`() {
        val note = createNote()
        // Vollständig leerer Cache
        assertFalse("Should not skip: no cache at all", shouldSkipJsonUpload(note))
    }

    @Test
    fun `skip works regardless of syncStatus`() {
        val note = createNote(syncStatus = SyncStatus.PENDING)
        val hash = computeHash(note)

        prefsCache["content_hash_${note.id}"] = hash
        prefsCache["etag_json_${note.id}"] = "\"etag-abc\""

        // PENDING-Note sollte trotzdem geskippt werden (SyncStatus wird normalisiert)
        assertTrue(shouldSkipJsonUpload(note))

        // Auch LOCAL_ONLY sollte matchen
        val localOnly = note.copy(syncStatus = SyncStatus.LOCAL_ONLY)
        assertTrue(shouldSkipJsonUpload(localOnly))
    }

    @Test
    fun `skip is specific to note id`() {
        val note1 = createNote(id = "note-1", content = "Content 1")
        val note2 = createNote(id = "note-2", content = "Content 2")

        // Nur note-1 hat gecachten Hash
        prefsCache["content_hash_${note1.id}"] = computeHash(note1)
        prefsCache["etag_json_${note1.id}"] = "\"etag-1\""

        assertTrue(shouldSkipJsonUpload(note1))
        assertFalse(shouldSkipJsonUpload(note2))  // note-2 hat keinen Cache
    }

    // ═══════════════════════════════════════════════
    // MD Upload-Skip (Opt. 6)
    // ═══════════════════════════════════════════════

    @Test
    fun `md skip when md hash matches and md etag exists`() {
        val note = createNote()
        val mdContent = note.toMarkdown()
        prefsCache["content_hash_md_${note.id}"] = computeMdHash(mdContent)
        prefsCache["etag_md_${note.id}"] = "\"md-etag-xyz\""

        assertTrue("Should skip MD: content unchanged, E-Tag cached", shouldSkipMdUpload(note))
    }

    @Test
    fun `md no skip when md hash matches but md etag missing`() {
        val note = createNote()
        val mdContent = note.toMarkdown()
        prefsCache["content_hash_md_${note.id}"] = computeMdHash(mdContent)
        // Kein MD E-Tag

        assertFalse("Should not skip MD: no E-Tag", shouldSkipMdUpload(note))
    }

    @Test
    fun `md no skip when title changes`() {
        val noteOld = createNote(title = "Old Title")
        val noteNew = createNote(title = "New Title")

        prefsCache["content_hash_md_${noteNew.id}"] = computeMdHash(noteOld.toMarkdown())
        prefsCache["etag_md_${noteNew.id}"] = "\"md-etag\""

        assertFalse(
            "Should not skip MD: title changed → different markdown",
            shouldSkipMdUpload(noteNew)
        )
    }

    @Test
    fun `md no skip when content changes`() {
        val noteOld = createNote(content = "Old content")
        val noteNew = createNote(content = "New content")

        prefsCache["content_hash_md_${noteNew.id}"] = computeMdHash(noteOld.toMarkdown())
        prefsCache["etag_md_${noteNew.id}"] = "\"md-etag\""

        assertFalse("Should not skip MD: content changed", shouldSkipMdUpload(noteNew))
    }

    @Test
    fun `md no skip on first upload`() {
        val note = createNote()
        // Leerer Cache
        assertFalse("Should not skip MD: first upload", shouldSkipMdUpload(note))
    }

    // ═══════════════════════════════════════════════
    // Kombination JSON + MD Skip
    // ═══════════════════════════════════════════════

    @Test
    fun `both json and md can be skipped independently`() {
        val note = createNote()

        // JSON-Cache gesetzt, MD-Cache nicht
        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"json-etag\""

        assertTrue("JSON should be skipped", shouldSkipJsonUpload(note))
        assertFalse("MD should NOT be skipped (no cache)", shouldSkipMdUpload(note))
    }

    @Test
    fun `both json and md can be skipped simultaneously`() {
        val note = createNote()
        val mdContent = note.toMarkdown()

        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"json-etag\""
        prefsCache["content_hash_md_${note.id}"] = computeMdHash(mdContent)
        prefsCache["etag_md_${note.id}"] = "\"md-etag\""

        assertTrue("Both JSON and MD should be skipped", shouldSkipJsonUpload(note))
        assertTrue("Both JSON and MD should be skipped", shouldSkipMdUpload(note))
    }

    @Test
    fun `cache invalidation forces re-upload`() {
        val note = createNote()

        // Vollständiger Cache
        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"json-etag\""
        assertTrue(shouldSkipJsonUpload(note))

        // Simuliere E-Tag-Invalidierung (z.B. nach Conflict oder Deletion)
        prefsCache.remove("etag_json_${note.id}")
        assertFalse("Should not skip after E-Tag invalidation", shouldSkipJsonUpload(note))
    }

    @Test
    fun `content hash invalidation forces re-upload`() {
        val note = createNote()

        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"json-etag\""
        assertTrue(shouldSkipJsonUpload(note))

        // Simuliere Hash-Invalidierung (z.B. nach App-Update)
        prefsCache.remove("content_hash_${note.id}")
        assertFalse("Should not skip after hash invalidation", shouldSkipJsonUpload(note))
    }

    // ═══════════════════════════════════════════════
    // Cache population nach erfolgreichem Upload
    // ═══════════════════════════════════════════════

    @Test
    fun `after simulating upload – subsequent skip returns true`() {
        val note = createNote()

        // Vor dem Upload: kein Skip
        assertFalse(shouldSkipJsonUpload(note))

        // Simula Upload-Erfolg: Cache befüllen (wie in uploadLocalNotes nach Batch-E-Tag-Fetch)
        prefsCache["content_hash_${note.id}"] = computeHash(note)
        prefsCache["etag_json_${note.id}"] = "\"new-server-etag\""

        // Nach dem Upload: Skip möglich
        assertTrue("Skip should work after cache population", shouldSkipJsonUpload(note))

        // Note mit gleicher ID und gleichem Content → immer noch skip
        val sameNote = note.copy(syncStatus = SyncStatus.PENDING)
        assertTrue("Same note (different syncStatus) should still skip", shouldSkipJsonUpload(sameNote))
    }

    @Test
    fun `after simulating md upload – subsequent md skip returns true`() {
        val note = createNote()
        val mdContent = note.toMarkdown()

        assertFalse(shouldSkipMdUpload(note))

        // Simula MD-Export-Erfolg: Cache befüllen (wie in exportToMarkdown)
        prefsCache["content_hash_md_${note.id}"] = computeMdHash(mdContent)
        prefsCache["etag_md_${note.id}"] = "\"md-server-etag\""

        assertTrue("MD skip should work after cache population", shouldSkipMdUpload(note))
    }
}
