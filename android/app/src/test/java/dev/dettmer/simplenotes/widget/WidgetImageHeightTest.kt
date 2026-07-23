package dev.dettmer.simplenotes.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetImageHeightTest {
    @Test
    fun `size presets map to proportional heights`() {
        assertEquals(55, widgetImageHeightDp(25))
        assertEquals(110, widgetImageHeightDp(50))
        assertEquals(165, widgetImageHeightDp(75))
        assertEquals(220, widgetImageHeightDp(100))
    }

    @Test
    fun `tiny percentages are clamped to a visible minimum`() {
        assertEquals(24, widgetImageHeightDp(1))
    }

    @Test
    fun `out-of-range percentages are clamped to the full height`() {
        assertEquals(220, widgetImageHeightDp(150))
    }
}
