package dev.dettmer.simplenotes.noteimport.keep.parser

import dev.dettmer.simplenotes.noteimport.keep.model.KeepChecklistItem
import dev.dettmer.simplenotes.utils.Logger

/**
 * v2.5.0 — Eigenbau-Fallback-Parser für die `.html`-Geschwister von Keep-Takeout-JSONs.
 *
 * Kommt zum Einsatz, wenn das primäre `textContent` im JSON leer/null ist, aber
 * eine parallele `<basename>.html` existiert (Keep schreibt sie als Render-Backup).
 *
 * **Bewusst keine neue Dependency** (jsoup o.ä.) — Keep-HTML ist sehr regelmäßig
 * (`<div class="content">` für Plaintext, `<ul class="list"><li>` für Listen).
 * Bei zukünftigen Keep-HTML-Änderungen: defensives Logging, leerer Fallback,
 * **kein** Crash (Analyseplan §3.3).
 */
class KeepHtmlFallbackParser {

    /**
     * Extrahiert Plaintext aus dem Render-HTML.
     *
     * Strategie:
     *  1. `<div class="content">…</div>`-Block isolieren (case-insensitive).
     *     Falls nicht vorhanden, gesamten `<body>` nehmen.
     *  2. `<br>` / `<br/>` / `<br />` → `\n`.
     *  3. `<p>` / `</p>` / `<div>` / `</div>` → `\n`.
     *  4. Restliche Tags strippen.
     *  5. HTML-Entities decodieren (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;`,
     *     numerische `&#NNN;` Hex/Dec).
     *  6. Mehrfach-Newlines auf max. 2 reduzieren, end-trim.
     */
    fun extractPlainText(html: String): String {
        if (html.isBlank()) return ""

        return try {
            val contentBlock = extractContentBlock(html)
            val withNewlines = contentBlock
                .replace(BR_REGEX, "\n")
                .replace(BLOCK_BOUNDARY_REGEX, "\n")
            val stripped = withNewlines.replace(ANY_TAG_REGEX, "")
            val decoded = decodeEntities(stripped)
            decoded
                .replace(MULTIPLE_NEWLINES_REGEX, "\n\n")
                .trim()
        } catch (e: Exception) {
            Logger.w(TAG, "extractPlainText failed (returning empty): ${e.message}")
            ""
        }
    }

    /**
     * Extrahiert eine Checkliste aus `<ul class="list"><li>…</li></ul>`-Strukturen.
     *
     * Heuristik für `indentationLevel`:
     *  - Verschachtelte `<ul>`-Tiefe (jedes nested `<ul>` erhöht Level um 1).
     *  - Cap auf [MAX_INDENT_LEVEL] (Schutz gegen pathologische HTML-Tiefe).
     *  - `isChecked` aus `<input type="checkbox" checked>` oder `class="checked"`
     *    am Listen-Item; Default `false`.
     *
     * Gibt leere Liste zurück, wenn keine `<ul>`-Strukturen gefunden werden.
     */
    fun extractChecklist(html: String): List<KeepChecklistItem> {
        if (html.isBlank()) return emptyList()

        return try {
            val items = mutableListOf<KeepChecklistItem>()
            // Naive Stack-basierte Tiefen-Verfolgung. Wir scannen sequentiell durch
            // <ul> / </ul> / <li …> Tokens. Reicht für Keep, das nur sehr begrenzte
            // Verschachtelung produziert.
            var depth = 0
            val tokens = TOKEN_REGEX.findAll(html).iterator()
            while (tokens.hasNext()) {
                val match = tokens.next()
                val raw = match.value.lowercase()
                when {
                    raw.startsWith("<ul") -> depth = (depth + 1).coerceAtMost(MAX_INDENT_LEVEL + 1)
                    raw.startsWith("</ul") -> depth = (depth - 1).coerceAtLeast(0)
                    raw.startsWith("<li") -> {
                        // Vollständigen <li>…</li>-Block ab Position des Matches greifen.
                        val liStart = match.range.first
                        val liEnd = findMatchingTagEnd(html, liStart, "li")
                        if (liEnd <= liStart) {
                            Logger.w(TAG, "Unclosed <li> at $liStart, skipping")
                            continue
                        }
                        val liInner = html.substring(liStart, liEnd)
                        val isChecked = CHECKBOX_CHECKED_REGEX.containsMatchIn(liInner) ||
                            CLASS_CHECKED_REGEX.containsMatchIn(liInner)
                        // Inner-Text: alles ohne Tags + Entities.
                        val text = decodeEntities(
                            liInner
                                .replace(BR_REGEX, " ")
                                .replace(ANY_TAG_REGEX, "")
                        ).trim()
                        if (text.isNotEmpty()) {
                            items.add(
                                KeepChecklistItem(
                                    text = text,
                                    isChecked = isChecked,
                                    indentationLevel = (depth - 1).coerceAtLeast(0)
                                        .coerceAtMost(MAX_INDENT_LEVEL)
                                )
                            )
                        }
                    }
                }
            }
            items
        } catch (e: Exception) {
            Logger.w(TAG, "extractChecklist failed (returning empty): ${e.message}")
            emptyList()
        }
    }

    // ───── Helpers ────────────────────────────────────────────────────

    private fun extractContentBlock(html: String): String {
        val match = CONTENT_DIV_REGEX.find(html)
        if (match != null) return match.groupValues[1]
        val body = BODY_REGEX.find(html)
        return body?.groupValues?.get(1) ?: html
    }

    private fun decodeEntities(s: String): String {
        // Numerische Entities zuerst.
        val numericDecoded = NUMERIC_ENTITY_REGEX.replace(s) { m ->
            val raw = m.groupValues[1]
            try {
                val codePoint = if (raw.startsWith("x") || raw.startsWith("X")) {
                    raw.substring(1).toInt(HEX_RADIX)
                } else {
                    raw.toInt()
                }
                String(Character.toChars(codePoint))
            } catch (e: NumberFormatException) {
                Logger.w(TAG, "Bad numeric entity '${m.value}': ${e.message}")
                m.value
            }
        }
        return numericDecoded
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    }

    private fun findMatchingTagEnd(html: String, start: Int, tag: String): Int {
        val closing = "</$tag>"
        val idx = html.indexOf(closing, startIndex = start, ignoreCase = true)
        return if (idx < 0) -1 else idx + closing.length
    }

    companion object {
        private const val TAG = "KeepHtmlFallbackParser"
        private const val MAX_INDENT_LEVEL = 3
        private const val HEX_RADIX = 16

        private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val BLOCK_BOUNDARY_REGEX =
            Regex("""</?(p|div|li|h[1-6])(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
        private val ANY_TAG_REGEX = Regex("""<[^>]+>""")
        private val MULTIPLE_NEWLINES_REGEX = Regex("""\n{3,}""")
        private val CONTENT_DIV_REGEX =
            Regex("""<div\s+class="content"\s*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        private val BODY_REGEX = Regex("""<body[^>]*>([\s\S]*?)</body>""", RegexOption.IGNORE_CASE)
        private val NUMERIC_ENTITY_REGEX = Regex("""&#([xX]?[0-9a-fA-F]+);""")
        private val TOKEN_REGEX = Regex("""<(ul|/ul|li)[^>]*>""", RegexOption.IGNORE_CASE)
        private val CHECKBOX_CHECKED_REGEX =
            Regex("""<input[^>]*type="checkbox"[^>]*checked""", RegexOption.IGNORE_CASE)
        private val CLASS_CHECKED_REGEX =
            Regex("""class="[^"]*\bchecked\b[^"]*"""", RegexOption.IGNORE_CASE)
    }
}
