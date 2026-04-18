package dev.dettmer.simplenotes.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für Extension Functions.
 *
 * Hinweis: Context-abhängige Extensions (showToast, toReadableTime(Context))
 * können hier nicht getestet werden – sie benötigen Android-Ressourcen.
 * truncate() ist pure Logik und wird hier abgedeckt.
 */
class ExtensionsTest {
    // ═══════════════════════════════════════════════
    // String.truncate()
    // ═══════════════════════════════════════════════

    @Test
    fun `truncate shorter string unchanged`() {
        assertEquals("Hello", "Hello".truncate(10))
    }

    @Test
    fun `truncate exact length unchanged`() {
        assertEquals("Hello", "Hello".truncate(5))
    }

    @Test
    fun `truncate longer string adds ellipsis`() {
        val result = "Hello World!".truncate(8)
        assertEquals("Hello...", result)
        assertEquals(8, result.length)
    }

    @Test
    fun `truncate with maxLength 3 returns only ellipsis`() {
        val result = "Hello".truncate(3)
        assertEquals("...", result)
    }

    @Test
    fun `truncate empty string unchanged`() {
        assertEquals("", "".truncate(10))
    }

    @Test
    fun `truncate single char with maxLength 4 returns one char plus ellipsis`() {
        val result = "Hello".truncate(4)
        assertEquals("H...", result)
    }
}
