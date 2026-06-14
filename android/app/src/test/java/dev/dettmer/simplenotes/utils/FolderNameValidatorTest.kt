package dev.dettmer.simplenotes.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNameValidatorTest {
    @Test fun `valid simple names`() {
        assertTrue(FolderNameValidator.isValid("Rezepte"))
        assertTrue(FolderNameValidator.isValid("Work 2024"))
        assertTrue(FolderNameValidator.isValid("a_b-c"))
        assertTrue(FolderNameValidator.isValid("notes")) // erlaubt: lebt als /notes/notes/
    }

    @Test fun `rejects blank and reserved`() {
        assertFalse(FolderNameValidator.isValid(""))
        assertFalse(FolderNameValidator.isValid("   "))
        assertFalse(FolderNameValidator.isValid("."))
        assertFalse(FolderNameValidator.isValid(".."))
    }

    @Test fun `rejects path separators and forbidden chars`() {
        assertFalse(FolderNameValidator.isValid("a/b"))
        assertFalse(FolderNameValidator.isValid("a\\b"))
        assertFalse(FolderNameValidator.isValid("a:b"))
        assertFalse(FolderNameValidator.isValid("a*b"))
        assertFalse(FolderNameValidator.isValid("a?b"))
    }

    @Test fun `rejects too long`() {
        assertFalse(FolderNameValidator.isValid("x".repeat(65)))
        assertTrue(FolderNameValidator.isValid("x".repeat(64)))
    }

    @Test fun `sanitize strips separators and trims`() {
        assertEquals("Rezepte", FolderNameValidator.sanitize("/Rezepte/"))
        assertEquals("ab", FolderNameValidator.sanitize("a/b"))
        assertNull(FolderNameValidator.sanitize("/"))
        assertNull(FolderNameValidator.sanitize(".."))
    }
}
