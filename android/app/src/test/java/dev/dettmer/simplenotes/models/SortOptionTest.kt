package dev.dettmer.simplenotes.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für SortOption, SortDirection und ChecklistSortOption.
 */
class SortOptionTest {

    // ═══════════════════════════════════════════════
    // SortOption
    // ═══════════════════════════════════════════════

    @Test
    fun `SortOption has correct prefsValues`() {
        assertEquals("updatedAt", SortOption.UPDATED_AT.prefsValue)
        assertEquals("createdAt", SortOption.CREATED_AT.prefsValue)
        assertEquals("title", SortOption.TITLE.prefsValue)
        assertEquals("noteType", SortOption.NOTE_TYPE.prefsValue)
    }

    @Test
    fun `SortOption fromPrefsValue returns correct option`() {
        assertEquals(SortOption.UPDATED_AT, SortOption.fromPrefsValue("updatedAt"))
        assertEquals(SortOption.CREATED_AT, SortOption.fromPrefsValue("createdAt"))
        assertEquals(SortOption.TITLE, SortOption.fromPrefsValue("title"))
        assertEquals(SortOption.NOTE_TYPE, SortOption.fromPrefsValue("noteType"))
    }

    @Test
    fun `SortOption fromPrefsValue unknown value defaults to UPDATED_AT`() {
        assertEquals(SortOption.UPDATED_AT, SortOption.fromPrefsValue("unknown"))
        assertEquals(SortOption.UPDATED_AT, SortOption.fromPrefsValue(""))
    }

    @Test
    fun `SortOption has exactly 4 entries`() {
        assertEquals(4, SortOption.entries.size)
    }

    // ═══════════════════════════════════════════════
    // SortDirection
    // ═══════════════════════════════════════════════

    @Test
    fun `SortDirection has correct prefsValues`() {
        assertEquals("asc", SortDirection.ASCENDING.prefsValue)
        assertEquals("desc", SortDirection.DESCENDING.prefsValue)
    }

    @Test
    fun `SortDirection fromPrefsValue returns correct direction`() {
        assertEquals(SortDirection.ASCENDING, SortDirection.fromPrefsValue("asc"))
        assertEquals(SortDirection.DESCENDING, SortDirection.fromPrefsValue("desc"))
    }

    @Test
    fun `SortDirection fromPrefsValue unknown defaults to DESCENDING`() {
        assertEquals(SortDirection.DESCENDING, SortDirection.fromPrefsValue("unknown"))
        assertEquals(SortDirection.DESCENDING, SortDirection.fromPrefsValue(""))
    }

    @Test
    fun `SortDirection toggle works correctly`() {
        assertEquals(SortDirection.DESCENDING, SortDirection.ASCENDING.toggle())
        assertEquals(SortDirection.ASCENDING, SortDirection.DESCENDING.toggle())
    }

    @Test
    fun `SortDirection double toggle returns original`() {
        assertEquals(SortDirection.ASCENDING, SortDirection.ASCENDING.toggle().toggle())
        assertEquals(SortDirection.DESCENDING, SortDirection.DESCENDING.toggle().toggle())
    }

    // ═══════════════════════════════════════════════
    // ChecklistSortOption
    // ═══════════════════════════════════════════════

    @Test
    fun `ChecklistSortOption has all 5 values`() {
        assertEquals(5, ChecklistSortOption.entries.size)
    }

    @Test
    fun `ChecklistSortOption values exist`() {
        assertNotNull(ChecklistSortOption.MANUAL)
        assertNotNull(ChecklistSortOption.ALPHABETICAL_ASC)
        assertNotNull(ChecklistSortOption.ALPHABETICAL_DESC)
        assertNotNull(ChecklistSortOption.UNCHECKED_FIRST)
        assertNotNull(ChecklistSortOption.CHECKED_FIRST)
    }

    @Test
    fun `ChecklistSortOption valueOf works for all values`() {
        assertEquals(ChecklistSortOption.MANUAL, ChecklistSortOption.valueOf("MANUAL"))
        assertEquals(ChecklistSortOption.ALPHABETICAL_ASC, ChecklistSortOption.valueOf("ALPHABETICAL_ASC"))
        assertEquals(ChecklistSortOption.ALPHABETICAL_DESC, ChecklistSortOption.valueOf("ALPHABETICAL_DESC"))
        assertEquals(ChecklistSortOption.UNCHECKED_FIRST, ChecklistSortOption.valueOf("UNCHECKED_FIRST"))
        assertEquals(ChecklistSortOption.CHECKED_FIRST, ChecklistSortOption.valueOf("CHECKED_FIRST"))
    }

    // ═══════════════════════════════════════════════
    // SyncStatus
    // ═══════════════════════════════════════════════

    @Test
    fun `SyncStatus has all 5 values`() {
        assertEquals(5, SyncStatus.entries.size)
    }

    @Test
    fun `SyncStatus values include DELETED_ON_SERVER`() {
        assertNotNull(SyncStatus.LOCAL_ONLY)
        assertNotNull(SyncStatus.SYNCED)
        assertNotNull(SyncStatus.PENDING)
        assertNotNull(SyncStatus.CONFLICT)
        assertNotNull(SyncStatus.DELETED_ON_SERVER)
    }

    // ═══════════════════════════════════════════════
    // NoteType
    // ═══════════════════════════════════════════════

    @Test
    fun `NoteType has exactly 2 values`() {
        assertEquals(2, NoteType.entries.size)
    }

    @Test
    fun `NoteType values are TEXT and CHECKLIST`() {
        assertNotNull(NoteType.TEXT)
        assertNotNull(NoteType.CHECKLIST)
    }
}
