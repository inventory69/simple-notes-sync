package dev.dettmer.simplenotes.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownTest {
    @Test fun `anchor becomes markdown link`() =
        assertEquals(
            "[Example](https://example.com)",
            HtmlToMarkdown.convert("""<a href="https://example.com">Example</a>""", "Example")
        )

    @Test fun `bold and italic and strike and code convert`() {
        assertEquals("**b**", HtmlToMarkdown.convert("<strong>b</strong>", "b"))
        assertEquals("*i*", HtmlToMarkdown.convert("<em>i</em>", "i"))
        assertEquals("~~s~~", HtmlToMarkdown.convert("<del>s</del>", "s"))
        assertEquals("`c`", HtmlToMarkdown.convert("<code>c</code>", "c"))
    }

    @Test fun `nested bold italic become triple stars`() =
        assertEquals("***x***", HtmlToMarkdown.convert("<b><i>x</i></b>", "x"))

    @Test fun `heading is capped at h3`() =
        assertEquals("### Title", HtmlToMarkdown.convert("<h5>Title</h5>", "Title"))

    @Test fun `list items become dashes`() =
        assertEquals(
            "- one\n- two",
            HtmlToMarkdown.convert("<ul><li>one</li><li>two</li></ul>", "one two")
        )

    @Test fun `entities in url and text are decoded`() =
        assertEquals(
            "[A & B](https://x.io/?a=1&b=2)",
            HtmlToMarkdown.convert("""<a href="https://x.io/?a=1&amp;b=2">A &amp; B</a>""", "fb")
        )

    @Test fun `link inside sentence keeps surrounding text`() =
        assertEquals(
            "See [my link](https://x.io) now",
            HtmlToMarkdown.convert("""See <a href="https://x.io">my link</a> now""", "fb")
        )

    @Test fun `plain html without rich tags returns fallback`() =
        assertEquals("plain", HtmlToMarkdown.convert("<span>plain</span>", "plain"))

    @Test fun `hasRichContent detects formatting`() {
        assertTrue(HtmlToMarkdown.hasRichContent("<b>x</b>"))
        assertTrue(HtmlToMarkdown.hasRichContent("""<a href="x">y</a>"""))
        assertFalse(HtmlToMarkdown.hasRichContent("<span>no</span>"))
    }

    @Test fun `hasRichContent detects hr pre and blockquote`() {
        assertTrue(HtmlToMarkdown.hasRichContent("a<hr>b"))
        assertTrue(HtmlToMarkdown.hasRichContent("<pre>code</pre>"))
        assertTrue(HtmlToMarkdown.hasRichContent("<blockquote>q</blockquote>"))
    }

    @Test fun `hasRichContent detects checkbox input without li wrapper`() =
        assertTrue(HtmlToMarkdown.hasRichContent("""plain <input type="checkbox"> text"""))

    @Test fun `hasRichContent detects style based bold italic strike spans`() {
        assertTrue(HtmlToMarkdown.hasRichContent("""<span style="font-weight:700">x</span>"""))
        assertTrue(HtmlToMarkdown.hasRichContent("""<span style="font-style:italic">x</span>"""))
        assertTrue(
            HtmlToMarkdown.hasRichContent("""<span style="text-decoration:line-through">x</span>""")
        )
    }

    @Test fun `plain span without formatting style is not rich`() =
        assertFalse(HtmlToMarkdown.hasRichContent("""<span style="font-family:Arial">plain</span>"""))

    @Test fun `horizontal rule becomes markdown divider`() =
        assertEquals("before\n\n---\n\nafter", HtmlToMarkdown.convert("before<hr>after", "beforeafter"))

    @Test fun `horizontal rule with attributes still converts`() =
        assertEquals("a\n\n---\n\nb", HtmlToMarkdown.convert("""a<hr class="thin">b""", "ab"))

    @Test fun `nested list items become separate flat dashes`() =
        assertEquals(
            "- one\n- two\n- three",
            HtmlToMarkdown.convert(
                "<ul><li>one<ul><li>two</li></ul></li><li>three</li></ul>",
                "one two three"
            )
        )

    @Test fun `checkbox list item becomes task list markdown`() =
        assertEquals(
            "- [ ] Buy milk\n- [x] Call dentist",
            HtmlToMarkdown.convert(
                """<ul><li><input type="checkbox"> Buy milk</li>""" +
                    """<li><input type="checkbox" checked> Call dentist</li></ul>""",
                "Buy milk Call dentist"
            )
        )

    @Test fun `checkbox without checked attribute is unchecked`() =
        assertEquals(
            "- [ ] Task",
            HtmlToMarkdown.convert("""<li><input type="checkbox">Task</li>""", "Task")
        )

    @Test fun `checkbox wrapped in label degrades to plain list item`() =
        assertEquals(
            "- Task",
            HtmlToMarkdown.convert(
                """<li><label><input type="checkbox" checked> Task</label></li>""",
                "Task"
            )
        )

    @Test fun `style attribute span becomes bold italic strike`() {
        assertEquals("**bold**", HtmlToMarkdown.convert("""<span style="font-weight:700">bold</span>""", "bold"))
        assertEquals(
            "*italic*",
            HtmlToMarkdown.convert("""<span style="font-style:italic">italic</span>""", "italic")
        )
        assertEquals(
            "~~struck~~",
            HtmlToMarkdown.convert("""<span style="text-decoration:line-through">struck</span>""", "struck")
        )
    }

    @Test fun `google docs normal weight wrapper is not treated as bold`() =
        assertEquals(
            "**Bold Word** normal word",
            HtmlToMarkdown.convert(
                """<b style="font-weight:normal" id="docs-internal-guid-abc123">""" +
                    """<span style="font-weight:700">Bold Word</span> normal word</b>""",
                "Bold Word normal word"
            )
        )

    @Test fun `pre becomes fenced code block preserving line breaks and indentation`() =
        assertEquals(
            "```\nfun main() {\n    println(\"hi\")\n}\n```",
            HtmlToMarkdown.convert(
                "<pre>fun main() {\n    println(\"hi\")\n}</pre>",
                "fun main() {     println(\"hi\") }"
            )
        )

    @Test fun `pre with br tags preserves line breaks`() =
        assertEquals(
            "```\nline1\nline2\n```",
            HtmlToMarkdown.convert("<pre>line1<br>line2</pre>", "line1 line2")
        )

    @Test fun `pre with nested code and syntax highlighting spans strips markup only`() =
        assertEquals(
            "```\nval x = 1\n```",
            HtmlToMarkdown.convert(
                """<pre><code><span class="hljs-keyword">val</span> x = 1</code></pre>""",
                "val x = 1"
            )
        )

    @Test fun `text around pre block keeps paragraph separation`() =
        assertEquals(
            "before\n\n```\ncode\n```\n\nafter",
            HtmlToMarkdown.convert("<p>before</p><pre>code</pre><p>after</p>", "before code after")
        )

    @Test fun `blockquote lines get quote prefix`() =
        assertEquals(
            "> line one\n> line two",
            HtmlToMarkdown.convert("<blockquote>line one<br>line two</blockquote>", "line one line two")
        )

    @Test fun `underline tag is stripped without corrupting surrounding text`() =
        assertEquals(
            "underlined text",
            HtmlToMarkdown.convert("<u>underlined</u> text", "underlined text")
        )

    @Test fun `ordered list degrades to dashes without numbering`() =
        assertEquals(
            "- first\n- second",
            HtmlToMarkdown.convert("<ol><li>first</li><li>second</li></ol>", "first second")
        )

    @Test fun `style-bloated chrome clip strips small and still converts headings`() {
        // Chrome inlines computed styles on every element → clip balloons.
        val bloat = """ style="${"color:#000;font-weight:400;".repeat(200)}""""
        val body = "<h2$bloat>Heading</h2>" + "<p$bloat>text</p>".repeat(400)
        assertTrue("fixture must exceed old 2MB cap", body.length > 2_000_000)

        val stripped = HtmlToMarkdown.stripStyleAttributes(body)
        assertTrue("stripping styles must shrink the clip a lot", stripped.length < body.length / 2)

        val md = HtmlToMarkdown.convert(body, "fallback")
        assertTrue("heading survives", md.contains("## Heading"))
        assertTrue("must not fall back to plaintext", md != "fallback")
    }
}
