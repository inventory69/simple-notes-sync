package dev.dettmer.simplenotes.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitParentheticalTest {
    @Test
    fun `splits latin and full-width parentheses, keeps plain names`() {
        assertEquals("System" to "follow device", splitParenthetical("System (follow device)"))
        assertEquals("系统" to "跟随设备", splitParenthetical("系统（跟随设备）"))
        assertEquals("Dark" to null, splitParenthetical("Dark"))
    }
}
