package dev.dettmer.simplenotes.markdown

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lesemodus mit einem 250 000 Zeichen langen Wort ohne Leerraum (Base64). Vorher wuchs das
 * native Textlayout beim Öffnen so einer Notiz auf über 2 GB, Android beendete die App.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownPreviewLongWordInstrumentedTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun huge_word_renders_in_reading_mode() {
        val content = "start " + "x".repeat(250_000) + "\n\nende"
        composeTestRule.setContent { MarkdownPreview(blocks = MarkdownEngine.parse(content)) }
        composeTestRule.onNodeWithText("ende").assertExists()
    }

    @Test fun copied_text_leaves_the_marks_behind() {
        val copied = ClipEntry(ClipData.newPlainText("", "abc${LAYOUT_BREAK}def")).withoutLayoutBreaks()
        assertEquals("abcdef", copied.clipData.getItemAt(0).text.toString())
    }
}
