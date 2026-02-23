package dev.dettmer.simplenotes.markdown

/**
 * 🆕 v1.9.0 (F07): Lightweight Markdown parser — no external dependencies.
 *
 * Parses Markdown text into a list of [MarkdownBlock]s for rendering.
 * Supports: headings, paragraphs, unordered lists, code blocks, horizontal rules.
 * Inline formatting (bold, italic, strikethrough, code, links) is handled
 * per-block by [MarkdownRenderer].
 *
 * 🔮 v1.9.0 (F08): Will be extended with image block support.
 */
object MarkdownEngine {

    /**
     * Sealed class representing block-level Markdown elements.
     */
    sealed class MarkdownBlock {
        /** Heading (H1–H3). [level] is 1, 2, or 3. */
        data class Heading(val level: Int, val text: String) : MarkdownBlock()

        /** Normal paragraph text (may contain inline formatting). */
        data class Paragraph(val text: String) : MarkdownBlock()

        /** Unordered list. Each entry is one list item's raw text. */
        data class UnorderedList(val items: List<String>) : MarkdownBlock()

        /** Fenced code block (``` ... ```). */
        data class CodeBlock(val code: String, val language: String) : MarkdownBlock()

        /** Horizontal rule (---, ***, ___). */
        data object HorizontalRule : MarkdownBlock()
    }

    /**
     * Parse raw Markdown [text] into a list of [MarkdownBlock]s.
     */
    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val headingMatch = HEADING_REGEX.matchEntire(line)

            when {
                // ── Fenced code block ──
                line.trimStart().startsWith("```") -> {
                    val language = line.trimStart().removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
                    i++ // skip closing ```
                }

                // ── Horizontal rule ──
                isHorizontalRule(line) -> {
                    blocks.add(MarkdownBlock.HorizontalRule)
                    i++
                }

                // ── Heading ──
                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length.coerceAtMost(3)
                    val headingText = headingMatch.groupValues[2].trim()
                    blocks.add(MarkdownBlock.Heading(level, headingText))
                    i++
                }

                // ── Unordered list ──
                LIST_ITEM_REGEX.matches(line) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && LIST_ITEM_REGEX.matches(lines[i])) {
                        val itemText = LIST_ITEM_REGEX.find(lines[i])?.groupValues?.get(1)?.trim() ?: ""
                        items.add(itemText)
                        i++
                    }
                    blocks.add(MarkdownBlock.UnorderedList(items))
                }

                // ── Blank line (skip) ──
                line.isBlank() -> i++

                // ── Paragraph (collect consecutive non-blank, non-special lines) ──
                else -> {
                    val paraLines = mutableListOf<String>()
                    while (i < lines.size && isParagraphLine(lines[i])) {
                        paraLines.add(lines[i])
                        i++
                    }
                    if (paraLines.isNotEmpty()) {
                        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
                    }
                }
            }
        }

        return blocks
    }

    /** Returns true if [line] is a plain paragraph line (non-blank, non-structural). */
    private fun isParagraphLine(line: String): Boolean {
        if (line.isBlank()) return false
        if (line.trimStart().startsWith("```")) return false
        if (isHorizontalRule(line)) return false
        if (HEADING_REGEX.matchEntire(line) != null) return false
        if (LIST_ITEM_REGEX.matches(line)) return false
        return true
    }

    private val HEADING_REGEX = Regex("""^(#{1,3})\s+(.+)$""")
    private val LIST_ITEM_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
    private const val HORIZONTAL_RULE_MIN_CHARS = 3

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= HORIZONTAL_RULE_MIN_CHARS && (
            trimmed.all { it == '-' || it == ' ' } && trimmed.count { it == '-' } >= HORIZONTAL_RULE_MIN_CHARS ||
            trimmed.all { it == '*' || it == ' ' } && trimmed.count { it == '*' } >= HORIZONTAL_RULE_MIN_CHARS ||
            trimmed.all { it == '_' || it == ' ' } && trimmed.count { it == '_' } >= HORIZONTAL_RULE_MIN_CHARS
        )
    }
}
