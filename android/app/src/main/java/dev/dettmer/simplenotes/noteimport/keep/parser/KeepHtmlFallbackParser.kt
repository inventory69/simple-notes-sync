package dev.dettmer.simplenotes.noteimport.keep.parser

import dev.dettmer.simplenotes.noteimport.keep.model.KeepChecklistItem
import dev.dettmer.simplenotes.utils.Logger

/**
 * v2.5.0 — Eigenbau-Fallback-Parser für die `.html`-Geschwister von Keep-Takeout-JSONs.
 *
 * Kommt nur zum Einsatz, wenn das JSON weder `textContent` noch `listContent` liefert,
 * aber eine parallele `<basename>.html` existiert (Keep schreibt sie als Render-Backup).
 *
 * Inhalt steht ausschließlich in `<div class="content">`. Alles daneben sind Metadaten,
 * die das JSON schon trägt: Titel, Labels, Weblinks, Anhänge und Kollaboratoren, die
 * beiden letzten als eigene `<ul>` (#162). Ohne Content-Div liefern beide Funktionen nichts.
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
     *     Falls nicht vorhanden: leerer String.
     *  2. `<br>` / `<br/>` / `<br />` → `\n`.
     *  3. `<p>` / `</p>` / `<div>` / `</div>` → `\n`.
     *  4. Restliche Tags strippen.
     *  5. HTML-Entities decodieren (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`,
     *     Latin-1-Namen wie `&auml;`, numerische `&#NNN;` Hex/Dec).
     *  6. Mehrfach-Newlines auf max. 2 reduzieren, end-trim.
     */
    fun extractPlainText(html: String): String {
        if (html.isBlank()) return ""

        return try {
            val contentBlock = extractContentBlock(html) ?: return ""
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
     * Extrahiert eine Checkliste aus `<ul class="list"><li>…</li></ul>`-Strukturen
     * innerhalb von `<div class="content">`.
     *
     * Heuristik für `indentationLevel`:
     *  - Verschachtelte `<ul>`-Tiefe (jedes nested `<ul>` erhöht Level um 1).
     *  - Cap auf [MAX_INDENT_LEVEL] (Schutz gegen pathologische HTML-Tiefe).
     *  - `isChecked` aus `<input type="checkbox" checked>` oder `class="checked"`
     *    am Listen-Item; Default `false`.
     *  - Die Glyphe aus `<span class="bullet">☐</span>` gehört nicht zum Text.
     *
     * Gibt leere Liste zurück, wenn keine `<ul>`-Strukturen gefunden werden.
     */
    fun extractChecklist(html: String): List<KeepChecklistItem> {
        if (html.isBlank()) return emptyList()

        return try {
            val content = extractContentBlock(html) ?: return emptyList()
            val items = mutableListOf<KeepChecklistItem>()
            // Naive Stack-basierte Tiefen-Verfolgung. Wir scannen sequentiell durch
            // <ul> / </ul> / <li …> Tokens. Reicht für Keep, das nur sehr begrenzte
            // Verschachtelung produziert.
            var depth = 0
            val tokens = TOKEN_REGEX.findAll(content).iterator()
            while (tokens.hasNext()) {
                val match = tokens.next()
                val raw = match.value.lowercase()
                when {
                    raw.startsWith("<ul") -> depth = (depth + 1).coerceAtMost(MAX_INDENT_LEVEL + 1)
                    raw.startsWith("</ul") -> depth = (depth - 1).coerceAtLeast(0)
                    raw.startsWith("<li") -> {
                        // Vollständigen <li>…</li>-Block ab Position des Matches greifen.
                        val liStart = match.range.first
                        val liEnd = findMatchingTagEnd(content, liStart, "li")
                        if (liEnd <= liStart) {
                            Logger.w(TAG, "Unclosed <li> at $liStart, skipping")
                            continue
                        }
                        val liInner = content.substring(liStart, liEnd)
                        val isChecked = CHECKBOX_CHECKED_REGEX.containsMatchIn(liInner) ||
                            CLASS_CHECKED_REGEX.containsMatchIn(liInner)
                        // Inner-Text: alles ohne Tags + Entities.
                        val text = decodeEntities(
                            liInner
                                .replace(BULLET_SPAN_REGEX, "")
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

    private fun extractContentBlock(html: String): String? = CONTENT_DIV_REGEX.find(html)?.groupValues?.get(1)

    /** Ein Durchgang, damit `&amp;lt;` als `&lt;` stehen bleibt. Unbekannte Entities bleiben roh. */
    private fun decodeEntities(s: String): String =
        ENTITY_REGEX.replace(s) { m ->
            val name = m.groupValues[1]
            val decoded = when {
                name.startsWith("#x", ignoreCase = true) -> codePoint(name.substring(2).toIntOrNull(HEX_RADIX))
                name.startsWith("#") -> codePoint(name.substring(1).toIntOrNull())
                else -> NAMED_ENTITIES[name]
            }
            decoded ?: m.value
        }

    private fun codePoint(cp: Int?): String? =
        cp?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

    private fun findMatchingTagEnd(html: String, start: Int, tag: String): Int {
        val closing = "</$tag>"
        val idx = html.indexOf(closing, startIndex = start, ignoreCase = true)
        return if (idx < 0) -1 else idx + closing.length
    }

    companion object {
        private const val TAG = "KeepHtmlFallbackParser"
        private const val MAX_INDENT_LEVEL = 3
        private const val HEX_RADIX = 16
        private const val LATIN1_FIRST = 0xA0

        // HTML-4-Namen für U+00A0..U+00FF; Keep schreibt Umlaute in Listen als `&auml;`.
        private const val LATIN1_NAMES =
            "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg " +
                "plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 " +
                "iquest Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml " +
                "Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash " +
                "Ugrave Uacute Ucirc Uuml Yacute THORN szlig agrave aacute acirc atilde auml aring " +
                "aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml eth ntilde ograve " +
                "oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml"
        private val NAMED_ENTITIES: Map<String, String> =
            LATIN1_NAMES.split(' ').withIndex().associate { (i, n) -> n to (LATIN1_FIRST + i).toChar().toString() } +
                mapOf("nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")

        private val BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
        private val BLOCK_BOUNDARY_REGEX =
            Regex("""</?(p|div|li|h[1-6])(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
        private val ANY_TAG_REGEX = Regex("""<[^>]+>""")
        private val MULTIPLE_NEWLINES_REGEX = Regex("""\n{3,}""")
        private val CONTENT_DIV_REGEX =
            Regex("""<div\s+class="content"\s*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        private val ENTITY_REGEX = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);""")
        private val BULLET_SPAN_REGEX =
            Regex("""<span\s+class="bullet"\s*>[\s\S]*?</span>""", RegexOption.IGNORE_CASE)
        private val TOKEN_REGEX = Regex("""<(ul|/ul|li)[^>]*>""", RegexOption.IGNORE_CASE)
        private val CHECKBOX_CHECKED_REGEX =
            Regex("""<input[^>]*type="checkbox"[^>]*checked""", RegexOption.IGNORE_CASE)
        private val CLASS_CHECKED_REGEX =
            Regex("""class="[^"]*\bchecked\b[^"]*"""", RegexOption.IGNORE_CASE)
    }
}
