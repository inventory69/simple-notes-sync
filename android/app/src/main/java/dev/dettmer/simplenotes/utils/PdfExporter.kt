package dev.dettmer.simplenotes.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import dev.dettmer.simplenotes.markdown.ImageAlign
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.markdown.markdownInlineToHtml
import dev.dettmer.simplenotes.markdown.parseImageAlt
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState
import java.io.File
import java.io.FileOutputStream

/**
 * 🆕 v1.10.0-Papa: Generates PDF documents from notes using Android's native PdfDocument API.
 *
 * Supports both TEXT and CHECKLIST note types.
 * - TEXT: Parses content via [MarkdownEngine] and renders headings, paragraphs, lists,
 *   task lists, code blocks and horizontal rules with real formatting (bold/italic/
 *   strikethrough/inline code), not raw Markdown syntax.
 * - CHECKLIST: Renders title + each item with checkbox symbol (☐ / ☑) and inline formatting.
 *
 * No external dependencies — uses only android.graphics.pdf.PdfDocument, Canvas,
 * and android.text.{Html,StaticLayout} (all native platform APIs).
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

    /** In-body heading font sizes (H1–H3), in points. */
    private const val HEADING_H1_FONT_SIZE = 18f
    private const val HEADING_H2_FONT_SIZE = 16f
    private const val HEADING_H3_FONT_SIZE = 14f

    /** Checklist item font size in points. */
    private const val CHECKLIST_FONT_SIZE = 12f

    /** Checkbox symbol font size (slightly larger for visibility). */
    private const val CHECKBOX_FONT_SIZE = 14f

    /** Line height multiplier (font size × this = line spacing). */
    private const val LINE_HEIGHT_MULTIPLIER = 1.5f

    /** Vertical gap between title and body content. */
    private const val TITLE_BODY_GAP = 20f

    /** Vertical gap after an in-body heading. */
    private const val HEADING_GAP = 10f

    /** Indent for checklist/list items (space for checkbox/bullet + gap). */
    private const val CHECKLIST_INDENT = 25f

    /** Max characters for sanitized filename. */
    private const val FILENAME_MAX_LENGTH = 50

    /** Half line-height multiplier for paragraph/block spacing. */
    private const val PARAGRAPH_BREAK_MULTIPLIER = 0.5f

    /** Vertical space reserved for a horizontal rule. */
    private const val HORIZONTAL_RULE_HEIGHT = 12f

    /** Horizontal padding between a code block's background panel and its text. */
    private const val CODE_BLOCK_PADDING = 6f

    /** Fraction of line height above the baseline used by a line's background panel. */
    private const val BACKGROUND_TOP_FRACTION = 0.75f

    // ═══════════════════════════════════════════════════════════════════════
    // Paint Objects (reused across pages)
    // ═══════════════════════════════════════════════════════════════════════

    private val titlePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = TITLE_FONT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }

    private val bodyPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = BODY_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }

    private val heading1Paint = TextPaint().apply {
        isAntiAlias = true
        textSize = HEADING_H1_FONT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }

    private val heading2Paint = TextPaint().apply {
        isAntiAlias = true
        textSize = HEADING_H2_FONT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }

    private val heading3Paint = TextPaint().apply {
        isAntiAlias = true
        textSize = HEADING_H3_FONT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.BLACK
    }

    private val codePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = BODY_FONT_SIZE
        typeface = Typeface.MONOSPACE
        color = android.graphics.Color.BLACK
    }

    private val checkboxPaint = Paint().apply {
        isAntiAlias = true
        textSize = CHECKBOX_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.DKGRAY
    }

    private val checkedItemPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = CHECKLIST_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.GRAY
    }

    private val uncheckedItemPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = CHECKLIST_FONT_SIZE
        typeface = Typeface.DEFAULT
        color = android.graphics.Color.BLACK
    }

    private val horizontalRulePaint = Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 1f
    }

    /** Background panel behind code blocks — matches MarkdownRenderer.CodeBlockSurface's intent. */
    private val codeBackgroundPaint = Paint().apply {
        color = android.graphics.Color.rgb(230, 230, 230)
        style = Paint.Style.FILL
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
                NoteType.TEXT -> renderTextNote(renderer, textContent, context)
                NoteType.CHECKLIST -> renderChecklistItems(
                    renderer,
                    NoteShareHelper.formatChecklistForPdf(checklistItems)
                )
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

    /**
     * Converts inline Markdown in [text] to a [Spanned] with real bold/italic/strike/code spans.
     * Inline code (rendered by [markdownInlineToHtml] as `<tt>`) gets a gray background overlay,
     * since [Html.fromHtml] renders `<tt>` as monospace text only, with no background — matching
     * the preview's gray inline-code box requires adding it here explicitly.
     */
    private fun toSpanned(text: String, strikethrough: Boolean = false): Spanned {
        var html = markdownInlineToHtml(text)
        if (strikethrough) html = "<s>$html</s>"
        html = html.replace("\n", "<br>")
        val spanned = SpannableString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        for (span in spanned.getSpans(0, spanned.length, TypefaceSpan::class.java)) {
            if (span.family != "monospace") continue
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            spanned.setSpan(BackgroundColorSpan(codeBackgroundPaint.color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spanned.setSpan(ForegroundColorSpan(android.graphics.Color.BLACK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spanned
    }

    private fun renderTextNote(renderer: PageRenderer, content: String, context: Context) {
        if (content.isBlank()) return

        for (block in MarkdownEngine.parse(content)) {
            when (block) {
                is MarkdownBlock.Heading -> {
                    val paint = headingPaint(block.level)
                    renderer.drawWrappedText(toSpanned(block.text), paint, TEXT_WIDTH)
                    renderer.advanceY(HEADING_GAP)
                }

                is MarkdownBlock.Paragraph -> renderParagraph(renderer, context, block.text)

                is MarkdownBlock.TaskList -> {
                    renderChecklistItems(renderer, block.items.map { it.text to it.isChecked })
                }

                is MarkdownBlock.UnorderedList -> renderBulletList(renderer, block.items)

                is MarkdownBlock.CodeBlock -> renderCodeBlock(renderer, block.code)

                MarkdownBlock.HorizontalRule -> renderHorizontalRule(renderer)

                is MarkdownBlock.Image -> renderImageBlock(renderer, context, block)
            }
        }
    }

    /**
     * Zeichnet ein Bild skaliert auf [sizePercent] der Seitenbreite (nie größer als die
     * Original-Auflösung), ausgerichtet per [align]. Passt ein zu hohes Bild zusätzlich auf
     * eine volle Seitenhöhe — keine Bild-Aufteilung über Seitengrenzen hinweg (v1-Vereinfachung,
     * bei ≤1920px-Assets unkritisch). Fehlt die Asset-Datei, wird der Alt-Text als
     * Platzhalterzeile gedruckt (analog Editor-Preview).
     */
    private fun renderImage(
        renderer: PageRenderer,
        context: Context,
        assetName: String,
        altText: String,
        align: ImageAlign,
        sizePercent: Int
    ) {
        val file = AssetStore(context).getAssetFile(assetName)
        val bitmap = file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        if (bitmap == null) {
            val placeholder = altText.ifBlank { "[image]" }
            renderer.drawWrappedText(toSpanned(placeholder), bodyPaint, TEXT_WIDTH)
        } else {
            val maxWidth = TEXT_WIDTH * sizePercent / 100f
            renderer.drawBitmapFit(bitmap, maxWidth, PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM, align)
            bitmap.recycle()
        }
        renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
    }

    private fun renderImageBlock(renderer: PageRenderer, context: Context, image: MarkdownBlock.Image) {
        renderImage(renderer, context, image.assetName, image.altText, image.align, image.sizePercent)
    }

    /**
     * Enthält [text] keinen Bild-Link, identisch zu vorher. Sonst werden Inline-Bilder als
     * eigene, zentrierte Block-Bilder zwischen den umgebrochenen Textsegmenten gedruckt — kein
     * echtes Inline-Fließen im PDF (vorab genehmigte Vereinfachung), aber es gehen keine
     * Bildinhalte verloren. Größen-Token werden bei Inline-Bildern überall ignoriert (Format-Spec).
     */
    private fun renderParagraph(renderer: PageRenderer, context: Context, text: String) {
        if (!MarkdownEngine.IMAGE_REGEX.containsMatchIn(text)) {
            renderer.drawWrappedText(toSpanned(text), bodyPaint, TEXT_WIDTH)
            renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
            return
        }

        var pos = 0
        for (match in MarkdownEngine.IMAGE_REGEX.findAll(text)) {
            val segment = text.substring(pos, match.range.first)
            if (segment.isNotBlank()) {
                renderer.drawWrappedText(toSpanned(segment), bodyPaint, TEXT_WIDTH)
                renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
            }
            val cleanAlt = parseImageAlt(match.groupValues[1]).cleanAlt
            renderImage(renderer, context, match.groupValues[2], cleanAlt, ImageAlign.CENTER, sizePercent = 100)
            pos = match.range.last + 1
        }
        val tail = text.substring(pos)
        if (tail.isNotBlank()) {
            renderer.drawWrappedText(toSpanned(tail), bodyPaint, TEXT_WIDTH)
            renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
        }
    }

    private fun headingPaint(level: Int): TextPaint = when (level) {
        1 -> heading1Paint
        2 -> heading2Paint
        else -> heading3Paint
    }

    private fun renderChecklistItems(renderer: PageRenderer, items: List<Pair<String, Boolean>>) {
        for ((text, isChecked) in items) {
            val symbol = if (isChecked) "☑ " else "☐ "
            val textPaint = if (isChecked) checkedItemPaint else uncheckedItemPaint

            // Ensure one full line of space before drawing
            renderer.ensureSpace(CHECKLIST_FONT_SIZE * LINE_HEIGHT_MULTIPLIER)

            // Draw checkbox symbol at left margin
            renderer.drawTextDirect(symbol, MARGIN_HORIZONTAL, checkboxPaint)

            // Draw item text with wrapping and inline formatting (indented past checkbox)
            val textWidth = TEXT_WIDTH - CHECKLIST_INDENT
            renderer.drawWrappedTextIndented(
                toSpanned(text, strikethrough = isChecked),
                textPaint,
                CHECKLIST_INDENT,
                textWidth
            )
        }
    }

    private fun renderBulletList(renderer: PageRenderer, items: List<String>) {
        for (itemText in items) {
            renderer.ensureSpace(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER)
            renderer.drawTextDirect("•", MARGIN_HORIZONTAL, bodyPaint)
            val textWidth = TEXT_WIDTH - CHECKLIST_INDENT
            renderer.drawWrappedTextIndented(toSpanned(itemText), bodyPaint, CHECKLIST_INDENT, textWidth)
        }
        renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
    }

    private fun renderCodeBlock(renderer: PageRenderer, code: String) {
        // Code is drawn literally — inline Markdown is intentionally not parsed inside code,
        // matching MarkdownRenderer.CodeBlockSurface's behavior in the editor preview.
        // A shaded background panel is painted behind it, same as in the editor preview.
        val textWidth = TEXT_WIDTH - 2 * CODE_BLOCK_PADDING
        for (line in code.split("\n")) {
            renderer.drawWrappedTextIndented(line, codePaint, CODE_BLOCK_PADDING, textWidth, codeBackgroundPaint)
        }
        renderer.advanceY(BODY_FONT_SIZE * LINE_HEIGHT_MULTIPLIER * PARAGRAPH_BREAK_MULTIPLIER)
    }

    private fun renderHorizontalRule(renderer: PageRenderer) {
        renderer.ensureSpace(HORIZONTAL_RULE_HEIGHT)
        renderer.drawHorizontalLine(horizontalRulePaint)
        renderer.advanceY(HORIZONTAL_RULE_HEIGHT)
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
            val page = document.startPage(pageInfo)
            currentPage = page
            canvas = page.canvas
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

        /** Draws a horizontal line across the text width at the current Y position. */
        fun drawHorizontalLine(paint: Paint) {
            canvas?.drawLine(MARGIN_HORIZONTAL, currentY, MARGIN_HORIZONTAL + TEXT_WIDTH, currentY, paint)
        }

        /**
         * Draws [bitmap] scaled down (never up) to fit within [maxWidth] and [maxHeight],
         * preserving aspect ratio, horizontally positioned per [align] within the full text
         * width. Advances the Y cursor by the drawn height.
         */
        fun drawBitmapFit(bitmap: Bitmap, maxWidth: Float, maxHeight: Float, align: ImageAlign = ImageAlign.CENTER) {
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height, 1f)
            val destWidth = bitmap.width * scale
            val destHeight = bitmap.height * scale
            ensureSpace(destHeight)
            val x = when (align) {
                ImageAlign.LEFT, ImageAlign.INLINE -> MARGIN_HORIZONTAL
                ImageAlign.CENTER -> MARGIN_HORIZONTAL + (TEXT_WIDTH - destWidth) / 2f
                ImageAlign.RIGHT -> MARGIN_HORIZONTAL + TEXT_WIDTH - destWidth
            }
            canvas?.drawBitmap(
                bitmap,
                null,
                RectF(x, currentY, x + destWidth, currentY + destHeight),
                null
            )
            currentY += destHeight
        }

        /**
         * Draws [text] with word wrapping starting at [MARGIN_HORIZONTAL].
         * Advances the Y cursor for each rendered line.
         */
        fun drawWrappedText(text: CharSequence, paint: TextPaint, maxWidth: Float) {
            drawWrappedTextIndented(text, paint, 0f, maxWidth)
        }

        /**
         * Draws [text] with word wrapping, indented by [indent] from the left margin.
         * Uses [StaticLayout] to compute line breaks and preserve any style spans
         * (bold/italic/strikethrough/monospace) [text] may carry (e.g. from
         * [android.text.Html.fromHtml]). Advances the Y cursor after each line and
         * automatically creates new pages when space runs out.
         *
         * If [backgroundPaint] is set, a full-width panel is painted behind each line first
         * (e.g. code blocks) — the per-line boxes are sized to abut exactly, so consecutive
         * lines of the same block read as one contiguous panel with no visible seams.
         */
        fun drawWrappedTextIndented(
            text: CharSequence,
            paint: TextPaint,
            indent: Float,
            maxWidth: Float,
            backgroundPaint: Paint? = null
        ) {
            if (text.isEmpty()) {
                // A blank code-block line still needs its background panel drawn and the
                // cursor advanced, otherwise a multi-line block shows a gap at this line.
                if (backgroundPaint == null) return
                val lineHeight = paint.textSize * LINE_HEIGHT_MULTIPLIER
                ensureSpace(lineHeight)
                canvas?.drawRect(
                    MARGIN_HORIZONTAL,
                    currentY - lineHeight * BACKGROUND_TOP_FRACTION,
                    MARGIN_HORIZONTAL + TEXT_WIDTH,
                    currentY + lineHeight * (1f - BACKGROUND_TOP_FRACTION),
                    backgroundPaint
                )
                currentY += lineHeight
                return
            }

            val x = MARGIN_HORIZONTAL + indent
            val width = maxWidth.toInt().coerceAtLeast(1)
            val lineHeight = paint.textSize * LINE_HEIGHT_MULTIPLIER
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()

            for (i in 0 until layout.lineCount) {
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineEnd(i)
                val lineText = text.subSequence(lineStart, lineEnd)

                ensureSpace(lineHeight)

                canvas?.let { c ->
                    if (backgroundPaint != null) {
                        c.drawRect(
                            MARGIN_HORIZONTAL,
                            currentY - lineHeight * BACKGROUND_TOP_FRACTION,
                            MARGIN_HORIZONTAL + TEXT_WIDTH,
                            currentY + lineHeight * (1f - BACKGROUND_TOP_FRACTION),
                            backgroundPaint
                        )
                    }

                    val lineLayout = StaticLayout.Builder.obtain(lineText, 0, lineText.length, paint, width).build()
                    c.save()
                    c.translate(x, currentY - lineLayout.getLineBaseline(0))
                    lineLayout.draw(c)
                    c.restore()
                }
                currentY += lineHeight
            }
        }
    }
}
