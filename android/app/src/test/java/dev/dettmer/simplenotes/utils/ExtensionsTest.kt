package dev.dettmer.simplenotes.utils

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit-Tests für Extension Functions.
 *
 * Hinweis: Context-abhängige Extensions (showToast, toReadableTime(Context))
 * können hier nicht getestet werden. Die parameterlose toReadableTime() und
 * truncate() sind pure Logik.
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

    // ═══════════════════════════════════════════════
    // Long.toReadableTime() — German, no context
    // ═══════════════════════════════════════════════

    @Test
    fun `toReadableTime just now`() {
        val now = System.currentTimeMillis()
        assertEquals("Gerade eben", now.toReadableTime())
    }

    @Test
    fun `toReadableTime minutes ago`() {
        val tenMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        val result = tenMinutesAgo.toReadableTime()
        assertTrue("Should contain 'Vor' and 'Min': $result", result.contains("Vor") && result.contains("Min"))
    }

    @Test
    fun `toReadableTime hours ago`() {
        val twoHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        val result = twoHoursAgo.toReadableTime()
        assertTrue("Should contain 'Vor' and 'Std': $result", result.contains("Vor") && result.contains("Std"))
    }

    @Test
    fun `toReadableTime days ago`() {
        val threeDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        val result = threeDaysAgo.toReadableTime()
        assertTrue("Should contain 'Vor' and 'Tagen': $result", result.contains("Vor") && result.contains("Tagen"))
    }

    @Test
    fun `toReadableTime old date shows date format`() {
        val veryOld = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val result = veryOld.toReadableTime()
        // Should be in dd.MM.yyyy format
        assertTrue("Should match date pattern: $result", result.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")))
    }
}
