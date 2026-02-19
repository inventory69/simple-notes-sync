package dev.dettmer.simplenotes.backup

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für Backup-Datenklassen.
 * Reine Datenklassen-Tests ohne Android-Abhängigkeiten.
 */
class BackupDataClassesTest {

    // ═══════════════════════════════════════════════
    // BackupResult
    // ═══════════════════════════════════════════════

    @Test
    fun `BackupResult successful`() {
        val result = BackupResult(success = true, notesCount = 10, message = "Backup OK")
        assertTrue(result.success)
        assertEquals(10, result.notesCount)
        assertEquals("Backup OK", result.message)
        assertNull(result.error)
    }

    @Test
    fun `BackupResult failed`() {
        val result = BackupResult(success = false, error = "IO Error")
        assertFalse(result.success)
        assertEquals(0, result.notesCount)
        assertEquals("IO Error", result.error)
    }

    // ═══════════════════════════════════════════════
    // RestoreResult
    // ═══════════════════════════════════════════════

    @Test
    fun `RestoreResult with merge`() {
        val result = RestoreResult(
            success = true,
            importedNotes = 5,
            skippedNotes = 3,
            message = "5 imported, 3 skipped"
        )
        assertTrue(result.success)
        assertEquals(5, result.importedNotes)
        assertEquals(3, result.skippedNotes)
        assertEquals(0, result.overwrittenNotes)
    }

    @Test
    fun `RestoreResult with overwrite`() {
        val result = RestoreResult(
            success = true,
            importedNotes = 2,
            skippedNotes = 0,
            overwrittenNotes = 8
        )
        assertEquals(2, result.importedNotes)
        assertEquals(8, result.overwrittenNotes)
    }

    @Test
    fun `RestoreResult failed`() {
        val result = RestoreResult(success = false, error = "Corrupt backup")
        assertFalse(result.success)
        assertEquals("Corrupt backup", result.error)
    }

    // ═══════════════════════════════════════════════
    // ValidationResult
    // ═══════════════════════════════════════════════

    @Test
    fun `ValidationResult valid`() {
        val result = ValidationResult(isValid = true)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `ValidationResult invalid with error`() {
        val result = ValidationResult(isValid = false, errorMessage = "Version too new")
        assertFalse(result.isValid)
        assertEquals("Version too new", result.errorMessage)
    }

    // ═══════════════════════════════════════════════
    // RestoreMode
    // ═══════════════════════════════════════════════

    @Test
    fun `RestoreMode has all 3 values`() {
        assertEquals(3, RestoreMode.entries.size)
        assertNotNull(RestoreMode.MERGE)
        assertNotNull(RestoreMode.REPLACE)
        assertNotNull(RestoreMode.OVERWRITE_DUPLICATES)
    }

    // ═══════════════════════════════════════════════
    // BackupData (via Gson roundtrip)
    // ═══════════════════════════════════════════════

    @Test
    fun `BackupData Gson roundtrip`() {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val note = dev.dettmer.simplenotes.models.Note(
            id = "backup-test-1",
            title = "Backup Note",
            content = "Content",
            deviceId = "dev-1"
        )
        val original = BackupData(
            backupVersion = 1,
            createdAt = 1700000000000L,
            notesCount = 1,
            appVersion = "1.8.2",
            notes = listOf(note)
        )
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, BackupData::class.java)

        assertEquals(1, restored.backupVersion)
        assertEquals(1700000000000L, restored.createdAt)
        assertEquals(1, restored.notesCount)
        assertEquals("1.8.2", restored.appVersion)
        assertEquals(1, restored.notes.size)
        assertEquals("backup-test-1", restored.notes[0].id)
    }

    @Test
    fun `BackupData uses SerializedName annotations`() {
        val gson = com.google.gson.Gson()
        val data = BackupData(
            backupVersion = 1,
            createdAt = 100L,
            notesCount = 0,
            appVersion = "1.0",
            notes = emptyList()
        )
        val json = gson.toJson(data)
        assertTrue("Should use snake_case key", json.contains("backup_version"))
        assertTrue("Should use snake_case key", json.contains("created_at"))
        assertTrue("Should use snake_case key", json.contains("notes_count"))
        assertTrue("Should use snake_case key", json.contains("app_version"))
    }
}
