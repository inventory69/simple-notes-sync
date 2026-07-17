package dev.dettmer.simplenotes.ui.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dettmer.simplenotes.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the Markdown toolbar's wrapSelection behaviour.
 *
 * Covers the half of the bold+italic path that JVM tests cannot reach: `wrapSelection`
 * is private and only exercisable through the UI. The complementary half — turning
 * `***hello***` into spans — is asserted in MarkdownOutputTransformationTest.
 *
 * Run via adb:
 *   adb shell am instrument -w -r \
 *     -e class dev.dettmer.simplenotes.ui.editor.MarkdownToolbarInstrumentedTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class MarkdownToolbarInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeNoteEditorActivity>()

    /** Content text field = second editable node (first = title). */
    private fun contentField() = composeTestRule.onAllNodes(hasSetTextAction())[1]

    /**
     * Types [text] into the content field and selects all of it. The toolbar only
     * exists while the content field is focused, so the click has to come first.
     */
    private fun typeAndSelectAll(text: String) {
        contentField().performClick()
        contentField().performTextInput(text)
        composeTestRule.waitForIdle()
        contentField().performSemanticsAction(SemanticsActions.SetSelection) { it(0, text.length, true) }
        composeTestRule.waitForIdle()
    }

    /** Toolbar sits in a horizontalScroll — scroll the button into view before clicking. */
    private fun clickToolbarButton(labelRes: Int) {
        val label = composeTestRule.activity.getString(labelRes)
        composeTestRule.onNodeWithContentDescription(label).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test fun bold_then_italic_wraps_selection_in_triple_asterisks() {
        typeAndSelectAll("hello")
        clickToolbarButton(R.string.md_toolbar_bold)
        clickToolbarButton(R.string.md_toolbar_italic)
        contentField().assertTextContains("***hello***", substring = true)
    }

    @Test fun italic_then_bold_wraps_selection_in_triple_asterisks() {
        typeAndSelectAll("hello")
        clickToolbarButton(R.string.md_toolbar_italic)
        clickToolbarButton(R.string.md_toolbar_bold)
        contentField().assertTextContains("***hello***", substring = true)
    }

    @Test fun bold_alone_wraps_selection_in_double_asterisks() {
        typeAndSelectAll("hello")
        clickToolbarButton(R.string.md_toolbar_bold)
        contentField().assertTextContains("**hello**", substring = true)
    }

    @Test fun italic_alone_wraps_selection_in_single_asterisks() {
        typeAndSelectAll("hello")
        clickToolbarButton(R.string.md_toolbar_italic)
        contentField().assertTextContains("*hello*", substring = true)
    }
}
