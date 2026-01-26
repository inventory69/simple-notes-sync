package dev.dettmer.simplenotes.models

import dev.dettmer.simplenotes.models.NoteSize.Companion.SMALL_CHECKLIST_THRESHOLD
import dev.dettmer.simplenotes.models.NoteSize.Companion.SMALL_TEXT_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🎨 v1.7.0: Tests for Note Size Classification (Staggered Grid Layout)
 */
class NoteSizeTest {
    
    @Test
    fun `text note with less than 80 chars is SMALL`() {
        val note = Note(
            id = "test1",
            title = "Test",
            content = "Short content",  // 13 chars
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `text note with exactly 79 chars is SMALL`() {
        val content = "x".repeat(79)  // Exactly threshold - 1
        val note = Note(
            id = "test2",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `text note with exactly 80 chars is LARGE`() {
        val content = "x".repeat(SMALL_TEXT_THRESHOLD)  // Exactly at threshold
        val note = Note(
            id = "test3",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )
        
        assertEquals(NoteSize.LARGE, note.getSize())
    }
    
    @Test
    fun `text note with more than 80 chars is LARGE`() {
        val content = "This is a long note with more than 80 characters. " +
                "It should be classified as LARGE for grid layout display."
        val note = Note(
            id = "test4",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )
        
        assertEquals(NoteSize.LARGE, note.getSize())
    }
    
    @Test
    fun `checklist with 1 item is SMALL`() {
        val note = Note(
            id = "test5",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false)
            )
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `checklist with 4 items is SMALL`() {
        val note = Note(
            id = "test6",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false),
                ChecklistItem("id2", "Bread", false),
                ChecklistItem("id3", "Eggs", false),
                ChecklistItem("id4", "Butter", false)
            )
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `checklist with 5 items is LARGE`() {
        val note = Note(
            id = "test7",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false),
                ChecklistItem("id2", "Bread", false),
                ChecklistItem("id3", "Eggs", false),
                ChecklistItem("id4", "Butter", false),
                ChecklistItem("id5", "Cheese", false)  // 5th item -> LARGE
            )
        )
        
        assertEquals(NoteSize.LARGE, note.getSize())
    }
    
    @Test
    fun `checklist with many items is LARGE`() {
        val items = (1..10).map { ChecklistItem("id$it", "Item $it", false) }
        val note = Note(
            id = "test8",
            title = "Long List",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = items
        )
        
        assertEquals(NoteSize.LARGE, note.getSize())
    }
    
    @Test
    fun `empty checklist is SMALL`() {
        val note = Note(
            id = "test9",
            title = "Empty",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = emptyList()
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `checklist with null items is SMALL`() {
        val note = Note(
            id = "test10",
            title = "Null Items",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = null
        )
        
        assertEquals(NoteSize.SMALL, note.getSize())
    }
    
    @Test
    fun `constants have expected values`() {
        assertEquals(80, SMALL_TEXT_THRESHOLD)
        assertEquals(4, SMALL_CHECKLIST_THRESHOLD)
    }
}
