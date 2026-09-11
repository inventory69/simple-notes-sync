package dev.dettmer.simplenotes.markdown

/**
 * 🆕 v1.9.0 (F07): Lightweight Markdown parser — no external dependencies.
 *
 * Parses Markdown text into a list of [MarkdownBlock]s for rendering.
 * Supports: headings, paragraphs, unordered lists, code blocks, horizontal rules.
 * Inline formatting (bold, italic, strikethrough, code, links) is handled
 * per-block by [MarkdownRenderer].
 */
object MarkdownEngine {
    /**
     * Sealed class representing block-level Markdown elements.
     */
    sealed class MarkdownBlock {
        /** Heading (H1–H3). [level] is 1, 2, or 3. */
        data class Heading(val level: Int, val text: String) : MarkdownBlock()

        /**
         * Normal paragraph text (may contain inline formatting).
         * [startOrdinal] ist der `IMAGE_REGEX.findAll(gesamter Text)`-Index des ERSTEN
         * `|inline`-Bildes in [text] (falls vorhanden) — nachfolgende Inline-Bilder im selben
         * Paragraph zählen von dort hoch. Ermöglicht [computeImageRewrite] für Inline-Bilder
         * genau wie für Block-[Image]s.
         */
        data class Paragraph(val text: String, val startOrdinal: Int = 0) : MarkdownBlock()

        /** Unordered list. Each entry is one list item's raw text. */
        data class UnorderedList(val items: List<String>) : MarkdownBlock()

        /** Fenced code block (``` ... ```). */
        data class CodeBlock(val code: String, val language: String) : MarkdownBlock()

        /** Horizontal rule (---, ***, ___). */
        data object HorizontalRule : MarkdownBlock()

        /**
         * 🆕 v2.16.0 (Issue #140): Bewusst gesetzte Leerzeilen.
         *
         * Markdown fasst aufeinanderfolgende Leerzeilen laut Spezifikation zu einem einzigen
         * Absatztrenner zusammen. In einer Notiz-App ist das falsch: Wer dreimal Enter drückt,
         * will eine Lücke sehen und keine Absatzgrenze. Die **erste** Leerzeile bleibt der
         * Trenner (der Renderer setzt dafür seinen Blockabstand), [count] zählt nur die
         * darüber hinaus gehenden.
         */
        data class BlankLines(val count: Int) : MarkdownBlock()

        /** 🆕 v1.9.0: Task list (GitHub-style checkboxes: - [ ] / - [x]). */
        data class TaskList(val items: List<TaskItem>) : MarkdownBlock()

        /**
         * 🆕 Bild-Attachments. Text vor/nach dem Bild-Link auf derselben Zeile wird als
         * eigener Paragraph abgetrennt, das Bild selbst immer als eigener Block gerendert.
         * [altText] ist bereits CLEAN (Größe/Ausrichtung-Tokens gestrippt, siehe [parseImageAlt]).
         * [ordinal] ist der Index dieses Links in `IMAGE_REGEX.findAll(gesamter Text)` — identifiziert
         * den Link eindeutig für Rewrites via [computeImageRewrite], auch bei mehreren identischen Links.
         */
        data class Image(
            val altText: String,
            val assetName: String,
            val sizePercent: Int = 50,
            val align: ImageAlign = ImageAlign.CENTER,
            val ordinal: Int = 0,
            /** true = beginnt eine neue Bildreihe (Leerzeile oder Nicht-Bild-Block davor). */
            val startsNewRow: Boolean = false
        ) : MarkdownBlock()
    }

    /** Einzelnes Task-List-Item mit Checked-Status und Text. */
    data class TaskItem(val text: String, val isChecked: Boolean)

    /**
     * Parse raw Markdown [text] into a list of [MarkdownBlock]s.
     */
    // Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun parse(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.lines().toMutableList()
        var i = 0
        // Index dieses Links in IMAGE_REGEX.findAll(gesamter Text) — jeder Branch, der Zeilen
        // konsumiert, zählt seine eigenen Bild-Matches dazu, damit Image.ordinal exakt dem
        // findAll-Index entspricht (auch für Links in Headings/Codeblöcken/Inline-Text).
        var nextOrdinal = 0
        // Zeile des zuletzt geparsten Bildes — trennt "zweites Bild auf DERSELBEN Zeile"
        // (gleiche Reihe) von "Bild eine Zeile tiefer" (Reihe nur bei Leerzeile dazwischen).
        var lineOfLastImage = -1

        while (i < lines.size) {
            val line = lines[i]
            val headingMatch = HEADING_REGEX.matchEntire(line)
            // Nur der erste NICHT-inline Bild-Link auf der Zeile wird block-level gerendert;
            // Zeilen, deren Bilder alle |inline sind, fallen zu Paragraph/List durch.
            val imageMatch = IMAGE_REGEX.findAll(line).firstOrNull { parseImageAlt(it.groupValues[1]).align != ImageAlign.INLINE }

            when {
                // ── Fenced code block ──
                line.trimStart().startsWith("```") -> {
                    val startIdx = i
                    val language = line.trimStart().removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
                    i++ // skip closing ```
                    nextOrdinal += IMAGE_REGEX.findAll(lines.subList(startIdx, i.coerceAtMost(lines.size)).joinToString("\n")).count()
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
                    nextOrdinal += IMAGE_REGEX.findAll(line).count()
                    i++
                }

                // ── Bild (muss VOR UnorderedList geprüft werden) ──
                // Text vor/nach dem Bild auf derselben Zeile (z.B. Autokorrektur-Tippfehler
                // ohne eigene Zeile) wird abgetrennt statt das Bild zu verschlucken.
                imageMatch != null -> {
                    val prefix = line.substring(0, imageMatch.range.first).trimEnd()
                    val suffix = line.substring(imageMatch.range.last + 1).trimStart()
                    if (prefix.isNotBlank()) {
                        blocks.add(MarkdownBlock.Paragraph(prefix, startOrdinal = nextOrdinal))
                    }
                    nextOrdinal += IMAGE_REGEX.findAll(line.substring(0, imageMatch.range.first)).count()
                    val altInfo = parseImageAlt(imageMatch.groupValues[1])
                    // NACH dem prefix-Paragraph berechnet: ein abgetrennter Text-Prefix zaehlt als Trenner.
                    val startsNewRow = blocks.lastOrNull() !is MarkdownBlock.Image ||
                        (lineOfLastImage != i && i > 0 && lines[i - 1].isBlank())
                    lineOfLastImage = i
                    blocks.add(
                        MarkdownBlock.Image(
                            altText = altInfo.cleanAlt,
                            assetName = imageMatch.groupValues[2],
                            sizePercent = altInfo.sizePercent,
                            align = altInfo.align,
                            ordinal = nextOrdinal,
                            startsNewRow = startsNewRow
                        )
                    )
                    nextOrdinal++
                    if (suffix.isNotBlank()) {
                        lines[i] = suffix
                    } else {
                        i++
                    }
                }

                // ── Task list (muss VOR UnorderedList geprüft werden) ──
                TASK_LIST_REGEX.matches(line) -> {
                    val taskItems = mutableListOf<TaskItem>()
                    while (i < lines.size && TASK_LIST_REGEX.matches(lines[i])) {
                        val m = TASK_LIST_REGEX.find(lines[i]) ?: break
                        taskItems.add(
                            TaskItem(
                                text = m.groupValues[2].trim(),
                                isChecked = m.groupValues[1].lowercase() == "x"
                            )
                        )
                        nextOrdinal += IMAGE_REGEX.findAll(lines[i]).count()
                        i++
                    }
                    blocks.add(MarkdownBlock.TaskList(taskItems))
                }

                // ── Unordered list ──
                LIST_ITEM_REGEX.matches(line) -> {
                    val items = mutableListOf<String>()
                    // !TASK_LIST_REGEX ist zwingend: LIST_ITEM_REGEX matcht "- [x] foo" ebenfalls,
                    // sonst frisst ein Bullet direkt vor Task-Zeilen die ganze Checkliste auf.
                    while (i < lines.size && LIST_ITEM_REGEX.matches(lines[i]) && !TASK_LIST_REGEX.matches(lines[i])) {
                        val itemText = LIST_ITEM_REGEX.find(lines[i])?.groupValues?.get(1)?.trim().orEmpty()
                        items.add(itemText)
                        nextOrdinal += IMAGE_REGEX.findAll(lines[i]).count()
                        i++
                    }
                    blocks.add(MarkdownBlock.UnorderedList(items))
                }

                // ── Blank lines ──
                // 🆕 v2.16.0 (Issue #140): Die erste Leerzeile trennt zwei Blöcke und steckt
                // schon im Blockabstand des Renderers; jede weitere ist eine gewollte Lücke.
                line.isBlank() -> {
                    var blankCount = 0
                    while (i < lines.size && lines[i].isBlank()) {
                        blankCount++
                        i++
                    }
                    if (blankCount > 1) blocks.add(MarkdownBlock.BlankLines(blankCount - 1))
                }

                // ── Paragraph (collect consecutive non-blank, non-special lines) ──
                else -> {
                    val paraLines = mutableListOf<String>()
                    while (i < lines.size && isParagraphLine(lines[i])) {
                        paraLines.add(lines[i])
                        i++
                    }
                    if (paraLines.isNotEmpty()) {
                        val paraText = paraLines.joinToString("\n")
                        blocks.add(MarkdownBlock.Paragraph(paraText, startOrdinal = nextOrdinal))
                        nextOrdinal += IMAGE_REGEX.findAll(paraText).count()
                    }
                }
            }
        }

        return blocks
    }

    /**
     * Laenge der Bildreihe, die bei [start] beginnt: aufeinanderfolgende [MarkdownBlock.Image]s,
     * abgebrochen bei [MarkdownBlock.Image.startsNewRow] oder sobald die Prozentsumme 100
     * ueberschreiten wuerde. 0, wenn bei [start] kein Bild steht.
     *
     * Einzige Stelle, an der die Packregel lebt — Renderer und PDF-Export teilen sie sich.
     */
    fun imageRowLength(blocks: List<MarkdownBlock>, start: Int): Int {
        var count = 0
        var sum = 0
        while (start + count < blocks.size) {
            val image = blocks[start + count] as? MarkdownBlock.Image
            if (image == null || count > 0 && image.startsNewRow || sum + image.sizePercent > MAX_ROW_PERCENT) break
            sum += image.sizePercent
            count++
        }
        return count
    }

    /**
     * Trenner vor jedem Vorschau-Block: "" vor dem ersten, " " zwischen Bildern derselben Reihe
     * (gleiche Packregel wie im Editor, s. [MarkdownEngine.imageRowLength]), sonst "\n".
     */
    fun imageRowSeparators(blocks: List<MarkdownBlock>): List<String> {
        val separators = MutableList(blocks.size) { if (it == 0) "" else "\n" }
        var i = 0
        while (i < blocks.size) {
            val rowLength = if (blocks[i] is MarkdownBlock.Image) imageRowLength(blocks, i) else 0
            for (j in i + 1 until i + rowLength) separators[j] = " "
            i += maxOf(rowLength, 1)
        }
        return separators
    }

    /**
     * Quelltext-Variante derselben Reihen-Regel für Oberflächen, die Markdown als **String**
     * weiterreichen statt als Blöcke (Listen-Widget): direkt aufeinanderfolgende Bildzeilen
     * werden zu einer Zeile verbunden, damit ihre Platzhalter nebeneinander stehen.
     * Eine Leerzeile oder ein Textpräfix trennt weiterhin — wie beim Parsen.
     *
     * ponytail: ohne Prozent-Packung (anders als [imageRowLength]) — dort landen nur
     * Text-Platzhalter, die ohnehin umbrechen. Bei echten Bildern die Blöcke nutzen.
     */
    fun joinImageRows(text: String): String = text.replace(IMAGE_ROW_JOIN_REGEX, "$1 ")

    /** Returns true if [line] is a plain paragraph line (non-blank, non-structural). */
    private fun isParagraphLine(line: String): Boolean {
        if (line.isBlank()) return false
        if (line.trimStart().startsWith("```")) return false
        if (isHorizontalRule(line)) return false
        if (HEADING_REGEX.matchEntire(line) != null) return false
        if (TASK_LIST_REGEX.matches(line)) return false
        if (LIST_ITEM_REGEX.matches(line)) return false
        if (IMAGE_REGEX.findAll(line).any { parseImageAlt(it.groupValues[1]).align != ImageAlign.INLINE }) return false
        return true
    }

    private val HEADING_REGEX = Regex("""^(#{1,3})\s+(.+)$""")
    private val LIST_ITEM_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")

    /**
     * GitHub-Style Task-Item. Single Source of Truth — [MarkdownOutputTransformation] und der
     * Tap-to-Toggle im Editor matchen dagegen, damit Preview, Live-Styling und Tap dieselben
     * Zeilen als Checkbox behandeln.
     *
     * Toleranzen (decken sich mit `NotesImportWizard`/`Note.parseChecklist`):
     * - Marker `-`, `*` und `+`.
     * - Leere Klammern `- []` zählen als unchecked.
     * - Text ist optional (`- [ ]` frisch per Toolbar eingefügt = leere Checkbox).
     *
     * Das `\s+` VOR dem Text bleibt zwingend: sonst würde `- [x](https://…)` — ein Bullet mit
     * Link-Anzeigetext `x` — als abgehakte Checkbox durchgehen.
     */
    internal val TASK_LIST_REGEX = Regex("""^\s*[-*+]\s+\[([ xX]?)\](?:\s+(.*))?$""")
    internal val IMAGE_REGEX = Regex("""!\[([^\]]*)]\(\.assets/([A-Za-z0-9][A-Za-z0-9._-]*)\)""")
    private val IMAGE_ROW_JOIN_REGEX =
        Regex("""(!\[[^\]]*]\(\.assets/[A-Za-z0-9][A-Za-z0-9._-]*\))\n(?=!\[)""")
    private const val HORIZONTAL_RULE_MIN_CHARS = 3
    private const val MAX_ROW_PERCENT = 100

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= HORIZONTAL_RULE_MIN_CHARS &&
            (
                trimmed.all { it == '-' || it == ' ' } &&
                    trimmed.count { it == '-' } >= HORIZONTAL_RULE_MIN_CHARS ||
                    trimmed.all { it == '*' || it == ' ' } &&
                    trimmed.count { it == '*' } >= HORIZONTAL_RULE_MIN_CHARS ||
                    trimmed.all { it == '_' || it == ' ' } &&
                    trimmed.count { it == '_' } >= HORIZONTAL_RULE_MIN_CHARS
                )
    }
}
