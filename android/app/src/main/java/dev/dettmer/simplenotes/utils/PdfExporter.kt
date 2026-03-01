package dev.dettmer.simplenotes.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState
import java.io.File
import java.io.FileOutputStream

/**
 * 🆕 v1.10.0-Papa: Generates PDF documents from notes using Android's native PdfDocument API.
 *
 * Supports both TEXT and CHECKLIST note types.
 * - TEXT: Renders title + body text with word wrapping and automatic page breaks.
 * - CHECKLIST: Renders title + each item with checkbox symbol (☐ / ☑) and proper formatting.
 *
 * No external dependencies — uses only android.graphics.pdf.PdfDocument and Canvas.
 */
object PdfExporter {

    // ═══════════════════════════════════════════════════════════════════════
    // Page Layout Constants (A4 at 72 DPI)
    // ═══════════════════════════════════════════════════════════════════════

    /** A4 width in PostScript points (72 DPI). */
    private const val PAGE_WIDTH = 595

    /** A4 height in PostScript points (72 DPI). */
    private const val PAGE_HEIGHT = 842

    /** Left/right margin in points. */
    private const val MARGIN_HORIZONTAL = 50f

    /** Top margin in points. */
    private const val MARGIN_TOP = 60f

    /** Bottom margin — stop writing before this Y coordinate. */
    private const val MARGIN_BOTTOM = 60f

    /** Maximum usable width for text. */
    private const val TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN_HORIZONTAL

    // ═══════════════════════════════════════════════════════════════════════
    // Font Sizes & Spacing
    // ═══════════════════════════════════════════════════════════════════════

    /** Title font size in points. */
    private const val TITLE_FONT_SIZE = 20f

    /** Body text font size in points. */
    private const val BODY_FONT_SIZE = 12f

    /** Checklist item font size in points. */
    private const val CHECKLIST_FONT_SIZE = 12f

    /** Checkbox symbol font size (slightly larger for visibility). */
    private const val CHECKBOX_FONT_SIZE = 14f

    /** Line height multiplier (font size × this = line spacing). */
    private const val LINE_HEIGHT_MULTIPLIER = 1.5f

    /** Vertical gap between title and body content. */
    private const val TITLE_BODY_GAP = 20f

    /** Indent for checklist items (space for checkbox + gap). */
    private const val CHECKLIST_INDENT = 25f

    /** Max characters for sanitized filename. */
    private const val FILENAME_MAX_LENGTH = 50

    /** Half line-height multiplier for paragraph spacing. */
    private const val PARAGRAPH_BREAK_MULTIPLIER = 0.5f

    // ═══════════════════════════════════════════════════════════════════════
    // Paint Objects (reused across pages)
    // ═══════════════════════════════════════════════════════════════════════

    private val titlePaint = Paint().apply {
        isAntiAlias = true
        textSize = TITLE_FONT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }

    private val bodyPaint = Paint().apply {
        isAntiAlias = true
        textSize = BODY_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }

    private val checkboxPaint = Paint().apply {
        isAntiAlias = true
        textSize = CHECKBOX_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.DKGRAY
    }

    private val checkedTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = CHECKLIST_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.GRAY
        isStrikeThruText = true  // Visual distinction for completed items
    }

    private val uncheckedTextPaint = Paint().apply {
        isAntiAlias = true
        textSize = CHECKLIST_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Generates a PDF file from the given note data.
     *
     * @param context Android context for file access
     * @param title Note title (may be empty)
     * @param noteType TEXT or CHECKLIST
     * @param textContent Text content (for TEXT notes, may be empty for CHECKLIST)
     * @param checklistItems Checklist items (for CHECKLIST notes)
     * @return The generated PDF file, or null if generation failed
     */
    fun generatePdf(
        context: Context,
        title: String,
        noteType: NoteType,
        textContent: String,
        checklistItems: List<ChecklistItemState>
    ): File? {
        return try {
            val document = PdfDocument()
            val renderer = PageRenderer(document)

            // Render title
            if (title.isNotBlank()) {
                renderer.drawWrappedText(title, titlePaint, TEXT_WIDTH)
                renderer.advanceY(TITLE_BODY_GAP)
            }

            // Render body based on note type
            when (noteType) {
                NoteType.TEXT -> renderTextNote(renderer, textContent)
                NoteType.CHECKLIST -> renderChecklist(renderer, checklistItems)
            }

            // Finalize
            renderer.finishCurrentPage()

            // Save to cache directory
            val outputDir = File(context.cacheDir, "shared_pdfs")
            outputDir.mkdirs()

            // Sanitize filename: remove special characters, limit length
            val safeTitle = title.ifBlank { "note" }
                .replace(Regex("[^a-zA-Z0-9äöüÄÖÜß _-]"), "")
                .take(FILENAME_MAX_LENGTH)
                .trim()
                .ifBlank { "note" }
            val outputFile = File(outputDir, "$safeTitle.pdf")

            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()

            outputFile
        } catch (e: Exception) {
            Logger.e("PdfExporter", "PDF generation failed: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Rendering Methods
    // ═══════════════════════════════════════════════════════════════════════

    private fun renderTextNote(renderer: PageRenderer, content: String) {
        if (content.isBlank()) return

        val lines = content.split("\n")
        for (line in lines) {
            if (line.isBlank()) {
                // Empty line = paragraph break (half line-height)
                renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
            } else {
                renderer.drawWrappedText(line, bodyPaint, TEXT_WIDTH)
            }
        }
    }

    private fun renderChecklist(renderer: PageRenderer, items: List<ChecklistItemState>) {
        val formattedItems = NoteShareHelper.formatChecklistForPdf(items)

        for ((text, isChecked) in formattedItems) {
            val symbol = if (isChecked) "☑ " else "☐ "
            val textPaint = if (isChecked) checkedTextPaint else uncheckedTextPaint

            // Ensure one full line of space before drawing
            renderer.ensureSpace(CHECKLIST_FONT_SIZE * LINE_HEIGHT_MULTIPLIER)

            // Draw checkbox symbol at left margin
            renderer.drawTextDirect(symbol, MARGIN_HORIZONTAL, checkboxPaint)

            // Draw item text with wrapping (indented past checkbox)
            val textWidth = TEXT_WIDTH - CHECKLIST_INDENT
            renderer.drawWrappedTextIndented(text, textPaint, CHECKLIST_INDENT, textWidth)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PageRenderer — Manages multi-page rendering
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Internal helper class that manages page creation and the current Y cursor.
     * Automatically creates new pages when the current page runs out of space.
     */
    private class PageRenderer(private val document: PdfDocument) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var currentY = MARGIN_TOP

        init {
            startNewPage()
        }

        private fun startNewPage() {
            currentPage?.let { document.finishPage(it) }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
            currentY = MARGIN_TOP
        }

        fun finishCurrentPage() {
            currentPage?.let { document.finishPage(it) }
            currentPage = null
            canvas = null
        }

        /**
         * Ensures at least [height] points of vertical space on the current page.
         * Starts a new page if not enough space remains.
         */
        fun ensureSpace(height: Float) {
            if (currentY + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
                startNewPage()
            }
        }

        /** Advances the Y cursor by [amount] points, creating a new page if necessary. */
        fun advanceY(amount: Float) {
            currentY += amount
            if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM) {
                startNewPage()
            }
        }

        /**
         * Draws a single line of text at the left margin without wrapping.
         * Does not advance the Y cursor (caller must handle advancement).
         */
        fun drawTextDirect(text: String, x: Float, paint: Paint) {
            canvas?.drawText(text, x, currentY, paint)
        }

        /**
         * Draws text with word wrapping starting at [MARGIN_HORIZONTAL].
         * Advances the Y cursor for each rendered line.
         */
        fun drawWrappedText(text: String, paint: Paint, maxWidth: Float) {
            drawWrappedTextIndented(text, paint, 0f, maxWidth)
        }

        /**
         * Draws text with word wrapping, indented by [indent] from the left margin.
         * Advances the Y cursor after each line.
         * Automatically creates new pages when space runs out.
         */
        fun drawWrappedTextIndented(text: String, paint: Paint, indent: Float, maxWidth: Float) {
            val lineHeight = paint.textSize * LINE_HEIGHT_MULTIPLIER
            val x = MARGIN_HORIZONTAL + indent

            // Split into words and wrap greedily
            val words = text.split(" ")
            var currentLine = StringBuilder()

            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = StringBuilder(candidate)
                } else {
                    // Flush current line
                    if (currentLine.isNotEmpty()) {
                        ensureSpace(lineHeight)
                        canvas?.drawText(currentLine.toString(), x, currentY, paint)
                        currentY += lineHeight
                    }
                    // Start new line with the current word
                    // If a single word is wider than maxWidth, draw it anyway (no infinite loop)
                    currentLine = StringBuilder(word)
                }
            }

            // Flush remaining text
            if (currentLine.isNotEmpty()) {
                ensureSpace(lineHeight)
                canvas?.drawText(currentLine.toString(), x, currentY, paint)
                currentY += lineHeight
            }
        }
    }
}
