package dev.dettmer.simplenotes

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.HtmlToMarkdown
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke + undo/regression tests for HTML→Markdown smart paste.
 * Launches ComposeNoteEditorActivity directly (new empty text note).
 *
 * Run via adb:
 *   adb shell am instrument -w -r \
 *     -e class dev.dettmer.simplenotes.HtmlPasteInstrumentedTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class HtmlPasteInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeNoteEditorActivity>()

    private lateinit var clipboard: ClipboardManager

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        clipboard = ctx.getSystemService(ClipboardManager::class.java)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun setHtmlClip(html: String, plain: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clipboard.setPrimaryClip(ClipData.newHtmlText("test", plain, html))
        }
    }

    private fun setPlainClip(text: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clipboard.setPrimaryClip(ClipData.newPlainText("test", text))
        }
    }

    /** Content text field = second editable node (first = title). */
    private fun contentField() = composeTestRule.onAllNodes(hasSetTextAction())[1]

    /**
     * Clicks Undo — locale-safe (reads the actual string resource) and handles
     * both toolbar (wide) and overflow-menu (narrow/compact) layouts.
     */
    private fun clickUndo() {
        val undoLabel = composeTestRule.activity.getString(R.string.editor_undo)
        val overflowLabel = composeTestRule.activity.getString(R.string.share_overflow_menu)
        val inToolbar = composeTestRule
            .onAllNodes(hasContentDescription(undoLabel))
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (inToolbar) {
            composeTestRule.onNodeWithContentDescription(undoLabel).performClick()
        } else {
            composeTestRule.onNodeWithContentDescription(overflowLabel).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(undoLabel).performClick()
        }
    }

    private fun assertNoText(text: String) {
        composeTestRule.onAllNodes(hasText(text, substring = true))
            .fetchSemanticsNodes()
            .also { assert(it.isEmpty()) { "Expected '$text' to be absent but found it in the tree" } }
    }

    // ── Paste Tests ────────────────────────────────────────────────────────

    @Test fun bold_html_pastes_as_markdown() {
        setHtmlClip(html = "<b>bold</b> text", plain = "bold text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**bold** text", substring = true)
    }

    @Test fun italic_html_pastes_as_markdown() {
        setHtmlClip(html = "<em>italic</em> text", plain = "italic text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("*italic* text", substring = true)
    }

    @Test fun anchor_html_pastes_as_markdown_link() {
        setHtmlClip(
            html = """Visit <a href="https://f-droid.org">F-Droid</a> now""",
            plain = "Visit F-Droid now"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("[F-Droid](https://f-droid.org)", substring = true)
    }

    @Test fun plain_text_clipboard_pastes_unchanged() {
        setPlainClip("just plain text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("just plain text", substring = true)
    }

    @Test fun mixed_html_pastes_combined_markdown() {
        setHtmlClip(
            html = "<b>fett</b> und <a href=\"https://example.com\">Link</a>",
            plain = "fett und Link"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**fett**", substring = true)
        contentField().assertTextContains("[Link](https://example.com)", substring = true)
    }

    @Test fun html_without_rich_tags_pastes_as_plain() {
        setHtmlClip(html = "<span>no formatting</span>", plain = "no formatting")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("no formatting", substring = true)
        assertNoText("**")
    }

    // ── Undo after HTML paste ──────────────────────────────────────────────

    /**
     * After HTML paste (two-step insert), one Undo via toolbar restores the
     * pre-paste state. Since the custom UndoRedoManager debounces content
     * changes (500 ms), both steps are merged into a single undo entry —
     * one Undo clears the field completely.
     */
    @Test fun undo_after_html_paste_removes_all_pasted_content() {
        setHtmlClip(html = "<b>bold</b>", plain = "bold")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**bold**", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("**bold**")
        assertNoText("bold")
    }

    @Test fun undo_after_link_html_paste_removes_all_pasted_content() {
        setHtmlClip(
            html = """<a href="https://example.com">Example</a>""",
            plain = "Example"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("[Example](https://example.com)", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("[Example]")
        assertNoText("Example")
    }

    /**
     * Pre-existing content must survive an Undo after paste.
     * Type first, wait for the 500 ms debounce to expire so the "after typing"
     * state is captured as a separate snapshot, then paste HTML. One Undo
     * should restore "existing " rather than clearing the whole field.
     */
    @Test fun undo_after_html_paste_restores_pre_existing_content() {
        contentField().performClick()
        contentField().performTextInput("existing ")
        composeTestRule.waitForIdle()
        // ponytail: sleep > UNDO_SNAPSHOT_DEBOUNCE_MS (500 ms) so the paste
        // gets its own snapshot rather than merging with the typing burst.
        Thread.sleep(700)

        setHtmlClip(html = "<b>appended</b>", plain = "appended")
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**appended**", substring = true)
        contentField().assertTextContains("existing", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        // Paste undone; pre-existing text still there
        contentField().assertTextContains("existing", substring = true)
        assertNoText("**appended**")
    }

    // ── Undo regressions (normal paste / typing) ───────────────────────────

    /** Regression: plain-text paste also creates an undo entry. */
    @Test fun undo_after_plain_paste_removes_pasted_content() {
        setPlainClip("plain paste")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("plain paste", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("plain paste")
    }

    /** Regression: keyboard typing still creates an undo entry. */
    @Test fun undo_after_typing_removes_typed_content() {
        contentField().performClick()
        contentField().performTextInput("typed content")
        composeTestRule.waitForIdle()
        contentField().assertTextContains("typed content", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("typed content")
    }

    /** Regression: multiple pastes each create separate undo entries. */
    @Test fun undo_steps_through_multiple_paste_operations() {
        // First paste
        setHtmlClip(html = "<b>first</b>", plain = "first")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        Thread.sleep(700) // let debounce expire so second paste gets its own entry

        // Second paste
        setHtmlClip(html = "<em>second</em>", plain = "second")
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**first**", substring = true)
        contentField().assertTextContains("*second*", substring = true)

        // First undo → second paste gone, first paste still there
        clickUndo()
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**first**", substring = true)
        assertNoText("*second*")

        // Second undo → first paste gone too
        clickUndo()
        composeTestRule.waitForIdle()
        assertNoText("**first**")
    }

    // ── Additional use cases: block-level constructs ────────────────────────

    @Test fun horizontal_rule_pastes_as_markdown_divider() {
        setHtmlClip(html = "before<hr>after", plain = "beforeafter")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("before", substring = true)
        contentField().assertTextContains("---", substring = true)
        contentField().assertTextContains("after", substring = true)
    }

    @Test fun undo_after_horizontal_rule_paste_removes_all_pasted_content() {
        setHtmlClip(html = "before<hr>after", plain = "beforeafter")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("---", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("---")
        assertNoText("before")
    }

    @Test fun nested_list_html_pastes_as_flat_markdown_list() {
        setHtmlClip(
            html = "<ul><li>one<ul><li>two</li></ul></li><li>three</li></ul>",
            plain = "one two three"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("- one", substring = true)
        contentField().assertTextContains("- two", substring = true)
        contentField().assertTextContains("- three", substring = true)
    }

    @Test fun undo_after_nested_list_paste_removes_all_pasted_content() {
        setHtmlClip(html = "<ul><li>one<ul><li>two</li></ul></li></ul>", plain = "one two")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("- two", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("- one")
        assertNoText("- two")
    }

    @Test fun checkbox_list_html_pastes_as_task_list_markdown() {
        setHtmlClip(
            html = """<ul><li><input type="checkbox"> Buy milk</li>""" +
                """<li><input type="checkbox" checked> Call dentist</li></ul>""",
            plain = "Buy milk Call dentist"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("- [ ] Buy milk", substring = true)
        contentField().assertTextContains("- [x] Call dentist", substring = true)
    }

    @Test fun undo_after_checkbox_list_paste_removes_all_pasted_content() {
        setHtmlClip(html = """<li><input type="checkbox" checked>Done task</li>""", plain = "Done task")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("- [x] Done task", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("Done task")
    }

    @Test fun google_docs_style_span_pastes_as_markdown() {
        setHtmlClip(
            html = """<span style="font-weight:700">bold</span> and """ +
                """<span style="font-style:italic">italic</span>""",
            plain = "bold and italic"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**bold**", substring = true)
        contentField().assertTextContains("*italic*", substring = true)
    }

    @Test fun undo_after_google_docs_style_span_paste_removes_all_pasted_content() {
        setHtmlClip(html = """<span style="font-weight:700">bold</span>""", plain = "bold")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**bold**", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("**bold**")
        assertNoText("bold")
    }

    @Test fun google_docs_normal_weight_wrapper_is_not_pasted_as_bold() {
        setHtmlClip(
            html = """<b style="font-weight:normal" id="docs-internal-guid-x">""" +
                """<span style="font-weight:700">Bold Word</span> normal word</b>""",
            plain = "Bold Word normal word"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("**Bold Word**", substring = true)
        contentField().assertTextContains("normal word", substring = true)
        assertNoText("normal word**")
    }

    @Test fun fenced_code_block_html_pastes_preserving_line_breaks() {
        setHtmlClip(
            html = "<pre>fun main() {\n    println(1)\n}</pre>",
            plain = "fun main() {     println(1) }"
        )
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("```", substring = true)
        contentField().assertTextContains("println(1)", substring = true)
    }

    @Test fun undo_after_code_block_paste_removes_all_pasted_content() {
        setHtmlClip(html = "<pre>code line</pre>", plain = "code line")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("```", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("```")
        assertNoText("code line")
    }

    @Test fun blockquote_html_pastes_with_quote_prefix() {
        setHtmlClip(html = "<blockquote>Quoted text</blockquote>", plain = "Quoted text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("> Quoted text", substring = true)
    }

    @Test fun undo_after_blockquote_paste_removes_all_pasted_content() {
        setHtmlClip(html = "<blockquote>Quoted text</blockquote>", plain = "Quoted text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("> Quoted text", substring = true)

        clickUndo()
        composeTestRule.waitForIdle()

        assertNoText("Quoted text")
    }

    // ── Regression: constructs that intentionally do NOT get special markup ──

    @Test fun underline_html_pastes_as_plain_text_without_markers() {
        setHtmlClip(html = "<u>underlined</u> text", plain = "underlined text")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("underlined text", substring = true)
    }

    @Test fun ordered_list_html_pastes_as_dash_list_without_numbers() {
        setHtmlClip(html = "<ol><li>first</li><li>second</li></ol>", plain = "first second")
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains("- first", substring = true)
        contentField().assertTextContains("- second", substring = true)
        assertNoText("1.")
    }

    @Test fun oversized_html_pastes_as_plain_text() {
        val big = "x".repeat(HtmlToMarkdown.MAX_HTML_LENGTH + 1)
        setHtmlClip(html = "<b>$big</b>", plain = big)
        contentField().performClick()
        contentField().performSemanticsAction(SemanticsActions.PasteText)
        composeTestRule.waitForIdle()
        contentField().assertTextContains(big.take(20), substring = true)
        assertNoText("**")
    }
}
