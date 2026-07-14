package dev.dettmer.simplenotes.markdown

/**
 * Converts an HTML clipboard fragment to the Markdown subset that
 * [MarkdownRenderer]/[MarkdownEngine] actually render: links, bold, italic,
 * strikethrough, inline code, H1–H3 headings, unordered/task lists,
 * horizontal rules and fenced code blocks.
 *
 * Crash-safe: any failure (or an empty/unchanged result) returns [fallback]
 * (the plain-text coercion). ponytail: regex-based, no jsoup/CommonMark dep;
 * covers the common clipboard sources (Telegram/Word/Google Docs/browser).
 * Ordered lists lose their numbers (renderer has no OrderedList block type),
 * blockquotes degrade to "> "-prefixed plain text, checkboxes wrapped in
 * extra markup (e.g. a <label>) degrade to a plain list item — upgrade to a
 * real HTML parser only if these measurably break.
 */
object HtmlToMarkdown {
    /**
     * Sanity-Cap gegen absurde Clips. Seit v2.12.0 läuft convert() off-main
     * (Dispatchers.Default in NoteEditorScreen), das Limit ist kein
     * Main-Thread-Budget mehr — Chrome-Clips mit Inline-Styles sprengen
     * 200k schnell und fielen vorher stumm auf Plaintext zurück. Gemessen
     * wird gegen das style-gestrippte HTML (siehe [stripStyleAttributes]).
     */
    const val MAX_HTML_LENGTH = 8_000_000

    private val STYLE_ATTR_REGEX = Regex("""\sstyle\s*=\s*("[^"]*"|'[^']*')""", RegexOption.IGNORE_CASE)

    /**
     * Strips inline `style="…"` attributes. Chrome/Chromium annotieren beim
     * Kopieren jedes Element mit computed styles, was den Clip um ein
     * Vielfaches aufbläht. Nur zum **Messen** gegen [MAX_HTML_LENGTH] gedacht —
     * [convert] bekommt weiter das Original-HTML, damit Google-Docs/Word-Fett-
     * und Kursiv-Erkennung über style-Attribute (SPAN_*_REGEX) intakt bleibt.
     */
    fun stripStyleAttributes(html: String): String = STYLE_ATTR_REGEX.replace(html, "")

    private const val MAX_HEADING_LEVEL = 3
    private const val INLINE_NEST_PASSES = 6
    private const val LIST_NEST_PASSES = 6
    private const val CODE_PLACEHOLDER_MARK = ''

    private val IGNORE_DOTALL = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val SCRIPT_STYLE_REGEX =
        Regex("""<(script|style)\b[^>]*>.*?</\1>""", IGNORE_DOTALL)
    private val COMMENT_REGEX = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val PRE_REGEX = Regex("""<pre\b[^>]*>(.*?)</pre>""", IGNORE_DOTALL)
    private val PRE_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    private val ANCHOR_REGEX =
        Regex("""<a\b[^>]*\bhref\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a>""", IGNORE_DOTALL)
    private val BOLD_REGEX = Regex("""<(?:b|strong)\b([^>]*)>(.*?)</(?:b|strong)>""", IGNORE_DOTALL)
    private val BOLD_NORMAL_WEIGHT_REGEX =
        Regex("""font-weight\s*:\s*(?:normal|[1-4]00)\b""", RegexOption.IGNORE_CASE)
    private val SPAN_BOLD_REGEX = Regex(
        """<span\b[^>]*style\s*=\s*["'][^"']*font-weight\s*:\s*(?:bold|[6-9]00)[^"']*["'][^>]*>(.*?)</span>""",
        IGNORE_DOTALL
    )
    private val ITALIC_REGEX = Regex("""<(?:i|em)\b[^>]*>(.*?)</(?:i|em)>""", IGNORE_DOTALL)
    private val SPAN_ITALIC_REGEX = Regex(
        """<span\b[^>]*style\s*=\s*["'][^"']*font-style\s*:\s*italic[^"']*["'][^>]*>(.*?)</span>""",
        IGNORE_DOTALL
    )
    private val STRIKE_REGEX =
        Regex("""<(?:s|strike|del)\b[^>]*>(.*?)</(?:s|strike|del)>""", IGNORE_DOTALL)
    private val SPAN_STRIKE_REGEX = Regex(
        """<span\b[^>]*style\s*=\s*["'][^"']*text-decoration\s*:\s*line-through[^"']*["'][^>]*>(.*?)</span>""",
        IGNORE_DOTALL
    )
    private val CODE_REGEX = Regex("""<code\b[^>]*>(.*?)</code>""", IGNORE_DOTALL)
    private val HEADING_REGEX = Regex("""<h([1-6])\b[^>]*>(.*?)</h[1-6]>""", IGNORE_DOTALL)
    private val BLOCKQUOTE_REGEX = Regex("""<blockquote\b[^>]*>(.*?)</blockquote>""", IGNORE_DOTALL)
    private val INNERMOST_LI_REGEX =
        Regex("""<li\b[^>]*>((?:(?!<li\b)(?!</li>).)*)</li>""", IGNORE_DOTALL)
    private val CHECKBOX_INPUT_REGEX =
        Regex("""^\s*<input\b[^>]*\btype\s*=\s*["']checkbox["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val CHECKED_ATTR_REGEX = Regex("""\bchecked\b""", RegexOption.IGNORE_CASE)
    private val HR_REGEX = Regex("""<hr\b[^>]*/?>""", RegexOption.IGNORE_CASE)
    private val BLOCK_BREAK_REGEX =
        Regex("""<br\s*/?>|</p>|</div>|</tr>|</h[1-6]>""", RegexOption.IGNORE_CASE)

    private val ANY_TAG_REGEX = Regex("""<[^>]+>""")
    private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?)([0-9a-fA-F]+);""")
    private val TRIM_AROUND_NEWLINE_REGEX = Regex("""[ \t]*\n[ \t]*""")
    private val MULTI_NEWLINE_REGEX = Regex("""\n{3,}""")
    private val RICH_TAG_REGEX = Regex(
        """<(a|b|strong|i|em|s|strike|del|code|h[1-6]|li|br|p|hr|pre|blockquote)\b""",
        RegexOption.IGNORE_CASE
    )
    private val RICH_STYLE_REGEX = Regex(
        """<span\b[^>]*style\s*=\s*["'][^"']*(?:font-weight\s*:\s*(?:bold|[6-9]00)""" +
            """|font-style\s*:\s*italic|text-decoration\s*:\s*line-through)""",
        RegexOption.IGNORE_CASE
    )
    private val CHECKBOX_TAG_REGEX =
        Regex("""<input\b[^>]*\btype\s*=\s*["']checkbox["']""", RegexOption.IGNORE_CASE)

    /** Cheap pre-check: does [html] contain any tag/style we convert? */
    fun hasRichContent(html: String): Boolean =
        RICH_TAG_REGEX.containsMatchIn(html) ||
            RICH_STYLE_REGEX.containsMatchIn(html) ||
            CHECKBOX_TAG_REGEX.containsMatchIn(html)

    /** Convert [html]; on failure or empty/unchanged output return [fallback]. */
    // Logger unavailable (pure JVM object; used in unit tests) — swallow is intentional by contract
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun convert(html: String, fallback: String): String = try {
        var s = SCRIPT_STYLE_REGEX.replace(html, " ")
        s = COMMENT_REGEX.replace(s, " ")

        // Pull <pre>/<pre><code> out before whitespace collapsing so internal
        // line breaks and indentation survive; the placeholder has no \s chars
        // so it rides through every later pass untouched, and gets swapped for
        // a fenced ``` block only once all cleanup passes are done (otherwise
        // TRIM_AROUND_NEWLINE_REGEX would strip the code's own indentation).
        val codeBlocks = mutableListOf<String>()
        s = PRE_REGEX.replace(s) { m ->
            val withBreaks = PRE_BR_REGEX.replace(m.groupValues[1], "\n")
            codeBlocks.add(decodeEntities(stripTags(withBreaks)).trim('\n', ' '))
            "$CODE_PLACEHOLDER_MARK${codeBlocks.size - 1}$CODE_PLACEHOLDER_MARK"
        }

        s = WHITESPACE_REGEX.replace(s, " ") // normalize: only block tags create newlines

        // Inline (looped so nested tags like <b><i>…</i></b> resolve to ***…***,
        // and style-attribute spans from Google Docs/Word are treated like real tags).
        var pass = 0
        while (pass < INLINE_NEST_PASSES) {
            val before = s
            s = ANCHOR_REGEX.replace(s) { m ->
                val url = decodeEntities(m.groupValues[1]).trim()
                val text = decodeEntities(stripTags(m.groupValues[2])).trim()
                when {
                    url.isEmpty() -> text
                    text.isEmpty() -> url
                    else -> "[$text]($url)"
                }
            }
            s = BOLD_REGEX.replace(s) { m ->
                // Google Docs wraps whole pastes in <b style="font-weight:normal"
                // id="docs-internal-guid-…"> as an internal marker, not real bold.
                if (BOLD_NORMAL_WEIGHT_REGEX.containsMatchIn(m.groupValues[1])) {
                    m.groupValues[2]
                } else {
                    wrap(m.groupValues[2], "**")
                }
            }
            s = SPAN_BOLD_REGEX.replace(s) { wrap(it.groupValues[1], "**") }
            s = ITALIC_REGEX.replace(s) { wrap(it.groupValues[1], "*") }
            s = SPAN_ITALIC_REGEX.replace(s) { wrap(it.groupValues[1], "*") }
            s = STRIKE_REGEX.replace(s) { wrap(it.groupValues[1], "~~") }
            s = SPAN_STRIKE_REGEX.replace(s) { wrap(it.groupValues[1], "~~") }
            s = CODE_REGEX.replace(s) { wrap(it.groupValues[1], "`") }
            if (s == before) break
            pass++
        }

        // Block elements → newlines.
        s = HEADING_REGEX.replace(s) { m ->
            val level = m.groupValues[1].toInt().coerceAtMost(MAX_HEADING_LEVEL)
            "\n${"#".repeat(level)} ${stripTags(m.groupValues[2]).trim()}\n"
        }
        s = BLOCKQUOTE_REGEX.replace(s) { m ->
            val lines = BLOCK_BREAK_REGEX.replace(m.groupValues[1], "\n")
                .let { decodeEntities(stripTags(it)) }
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.isEmpty()) "" else "\n\n${lines.joinToString("\n") { "> $it" }}\n\n"
        }
        s = listItemsToMarkdown(s)
        s = HR_REGEX.replace(s, "\n\n---\n\n")
        s = BLOCK_BREAK_REGEX.replace(s, "\n")

        s = decodeEntities(stripTags(s))
        s = TRIM_AROUND_NEWLINE_REGEX.replace(s, "\n")
        s = MULTI_NEWLINE_REGEX.replace(s, "\n\n").trim()
        s = restoreCodeBlocks(s, codeBlocks)
        if (codeBlocks.isNotEmpty()) {
            // fence insertion can reintroduce a run of 3+ blank lines at the seam
            s = MULTI_NEWLINE_REGEX.replace(s, "\n\n").trim()
        }

        if (s.isEmpty() || s == fallback) fallback else s
    } catch (e: Exception) {
        fallback
    }

    /**
     * Converts `<li>` to "- text" / "- [ ] text" / "- [x] text", innermost
     * nesting first (a lazy single-pass regex would let an outer `<li>` eat
     * an inner `</li>` and merge two items' text into one line).
     */
    private fun listItemsToMarkdown(html: String): String {
        var s = html
        var pass = 0
        while (pass < LIST_NEST_PASSES) {
            val before = s
            s = INNERMOST_LI_REGEX.replace(s) { m -> "\n" + liToMarkdown(m.groupValues[1]) }
            if (s == before) break
            pass++
        }
        return s
    }

    private fun liToMarkdown(content: String): String {
        val checkbox = CHECKBOX_INPUT_REGEX.find(content)
        return if (checkbox != null) {
            val checked = CHECKED_ATTR_REGEX.containsMatchIn(checkbox.value)
            val text = stripTags(content.substring(checkbox.range.last + 1)).trim()
            "- [${if (checked) "x" else " "}] $text"
        } else {
            "- ${stripTags(content).trim()}"
        }
    }

    private fun restoreCodeBlocks(s: String, codeBlocks: List<String>): String {
        if (codeBlocks.isEmpty()) return s
        var result = s
        codeBlocks.forEachIndexed { i, code ->
            result = result.replace(
                "$CODE_PLACEHOLDER_MARK$i$CODE_PLACEHOLDER_MARK",
                "\n\n```\n$code\n```\n\n"
            )
        }
        return result
    }

    private fun wrap(inner: String, marker: String): String {
        val content = inner.trim()
        return if (content.isEmpty()) "" else "$marker$content$marker"
    }

    private fun stripTags(s: String): String = ANY_TAG_REGEX.replace(s, "")

    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val named = s
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        val numeric = NUMERIC_ENTITY_REGEX.replace(named) { m ->
            val value = m.groupValues[2]
            val code = if (m.groupValues[1].isNotEmpty()) value.toInt(16) else value.toInt()
            runCatching { String(Character.toChars(code)) }.getOrDefault(m.value)
        }
        // &amp; last so "&amp;lt;" decodes to literal "&lt;", not "<".
        return numeric.replace("&amp;", "&")
    }
}
