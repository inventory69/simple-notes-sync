package dev.dettmer.simplenotes.utils

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for #101: PDF export must actually render Markdown
 * formatting (headings, bold/italic, task lists), not just raw `**`/`#`/`[ ]` tags.
 *
 * Runs on-device since [android.graphics.pdf.PdfDocument]/[android.text.StaticLayout]/
 * [android.text.Html] are stubbed to no-ops under the project's local JVM unit test
 * config (`unitTests.isReturnDefaultValues = true`), so a real rendering pipeline can
 * only be exercised here.
 *
 * Run via adb:
 *   adb shell am instrument -w -r \
 *     -e class dev.dettmer.simplenotes.utils.PdfExporterInstrumentedTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class PdfExporterInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun textNoteWithMarkdown_generatesNonBlankPdf() {
        val content = """
            # Heading

            This is **bold** and *italic* text.

            - [ ] open task
            - [x] done task
        """.trimIndent()

        val file = PdfExporter.generatePdf(
            context = context,
            title = "Markdown Note",
            noteType = NoteType.TEXT,
            textContent = content,
            checklistItems = emptyList()
        )

        assertNotNull("PDF generation must not fail", file)
        assertTrue("PDF file must exist", file!!.exists())
        assertTrue("PDF file must be non-empty", file.length() > 0)
        assertTrue("Rendered page must contain visible (non-blank) content", firstPageHasContent(file))
    }

    @Test fun textNoteWithFencedBlockAndInlineCode_generatesNonBlankPdf() {
        // Fenced block has a blank line in the middle — regression for the gappy-panel bug
        // where drawWrappedTextIndented early-returned on empty lines without painting
        // the background or advancing the cursor.
        val content = """
            Some `inline code` in a paragraph.

            ```
            first line

            last line
            ```
        """.trimIndent()

        val file = PdfExporter.generatePdf(
            context = context,
            title = "Fenced",
            noteType = NoteType.TEXT,
            textContent = content,
            checklistItems = emptyList()
        )

        assertNotNull("PDF generation must not fail", file)
        assertTrue("PDF file must exist", file!!.exists())
        assertTrue("Rendered page must contain visible (non-blank) content", firstPageHasContent(file))
    }

    @Test fun checklistNoteWithBoldItem_generatesNonBlankPdf() {
        val items = listOf(
            ChecklistItemState(text = "**bold** item", isChecked = false, order = 0),
            ChecklistItemState(text = "done item", isChecked = true, order = 1)
        )

        val file = PdfExporter.generatePdf(
            context = context,
            title = "Checklist",
            noteType = NoteType.CHECKLIST,
            textContent = "",
            checklistItems = items
        )

        assertNotNull("PDF generation must not fail", file)
        assertTrue("PDF file must be non-empty", file!!.length() > 0)
        assertTrue("Rendered page must contain visible (non-blank) content", firstPageHasContent(file))
    }

    /** Opens the first page of [file] and checks it contains non-background pixels. */
    private fun firstPageHasContent(file: java.io.File): Boolean {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    for (y in 0 until bitmap.height) {
                        for (x in 0 until bitmap.width) {
                            if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) {
                                return true
                            }
                        }
                    }
                    return false
                }
            }
        }
    }
}
