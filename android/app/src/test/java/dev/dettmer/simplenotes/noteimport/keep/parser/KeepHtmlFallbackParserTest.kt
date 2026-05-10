package dev.dettmer.simplenotes.noteimport.keep.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.5.0 — Tests #13, #14, #15, #16 aus Analyseplan §5.1.
 */
class KeepHtmlFallbackParserTest {

    private lateinit var parser: KeepHtmlFallbackParser

    @Before
    fun setUp() {
        parser = KeepHtmlFallbackParser()
    }

    // ───── #13: extractPlainText handles <br> and <div>s ─────
    @Test
    fun `extractPlainText_handlesBrAndDivs`() {
        val html = """
            <html><body>
              <div class="content">Hallo<br>Welt<br/>Zeile 3</div>
            </body></html>
        """.trimIndent()
        val out = parser.extractPlainText(html)
        assertEquals("Hallo\nWelt\nZeile 3", out)
    }

    // ───── #14: extractPlainText decodes entities ─────
    @Test
    fun `extractPlainText_handlesEntities`() {
        val html = """<div class="content">A &amp; B &#39;quoted&#39; &lt;tag&gt; &#x20AC;</div>"""
        val out = parser.extractPlainText(html)
        assertEquals("A & B 'quoted' <tag> €", out)
    }

    // ───── #15: extractChecklist handles <ul><li> ─────
    @Test
    fun `extractChecklist_handlesUlListitem`() {
        val html = """
            <html><body>
              <ul class="list">
                <li>Milch</li>
                <li>Brot</li>
                <li class="checked">Kaffee</li>
              </ul>
            </body></html>
        """.trimIndent()
        val items = parser.extractChecklist(html)
        assertEquals(3, items.size)
        assertEquals("Milch", items[0].text)
        assertEquals(false, items[0].isChecked)
        assertEquals("Kaffee", items[2].text)
        assertEquals(true, items[2].isChecked)
        assertTrue(items.all { it.indentationLevel == 0 })
    }

    // ───── #16: extractChecklist erkennt nested <ul> als Indent ─────
    @Test
    fun `extractChecklist_handlesIndentation`() {
        val html = """
            <ul>
              <li>Obst</li>
              <ul>
                <li>Äpfel</li>
                <li>Birnen</li>
              </ul>
              <li>Brot</li>
            </ul>
        """.trimIndent()
        val items = parser.extractChecklist(html)
        assertEquals(4, items.size)
        assertEquals("Obst", items[0].text)
        assertEquals(0, items[0].indentationLevel)
        assertEquals("Äpfel", items[1].text)
        assertEquals(1, items[1].indentationLevel)
        assertEquals("Birnen", items[2].text)
        assertEquals(1, items[2].indentationLevel)
        assertEquals("Brot", items[3].text)
        assertEquals(0, items[3].indentationLevel)
    }

    // ───── Defensiv: leeres HTML → leerer String / leere Liste ─────
    @Test
    fun `extractPlainText_emptyHtml_returnsEmpty`() {
        assertEquals("", parser.extractPlainText(""))
        assertEquals("", parser.extractPlainText("   "))
    }

    @Test
    fun `extractChecklist_emptyHtml_returnsEmptyList`() {
        assertTrue(parser.extractChecklist("").isEmpty())
    }

    // ───── Defensiv: HTML ohne <div class="content"> → Body-Fallback ─────
    @Test
    fun `extractPlainText_noContentDiv_fallsBackToBody`() {
        val html = "<html><body>Just text</body></html>"
        assertEquals("Just text", parser.extractPlainText(html))
    }

    // ───── Defensiv: Checkbox mit checked-Attribut ─────
    @Test
    fun `extractChecklist_checkboxInputChecked_isDetected`() {
        val html = """
            <ul>
              <li><input type="checkbox" checked> Done thing</li>
              <li><input type="checkbox"> Pending thing</li>
            </ul>
        """.trimIndent()
        val items = parser.extractChecklist(html)
        assertEquals(2, items.size)
        assertEquals(true, items[0].isChecked)
        assertEquals("Done thing", items[0].text)
        assertEquals(false, items[1].isChecked)
    }

    // ───── Defensiv: HTML mit broken <li> ohne </li> → kein Crash ─────
    @Test
    fun `extractChecklist_unclosedLi_doesNotCrash`() {
        val html = "<ul><li>Broken<li>Next</li></ul>"
        val items = parser.extractChecklist(html)
        // First <li> ohne </li> wird per indexOf("</li>") an das nächste </li>
        // gebunden → enthält "Broken<li>Next" als roh, getrimmt.
        // Akzeptanzkriterium: kein Crash, Liste nicht leer.
        assertTrue(items.isNotEmpty())
    }

    // ───── Defensiv: indentation cap auf 3 ─────
    @Test
    fun `extractChecklist_deepNesting_capsAtMaxIndent`() {
        val html = """
            <ul><li>L0
              <ul><li>L1
                <ul><li>L2
                  <ul><li>L3
                    <ul><li>L4-capped</li></ul>
                  </li></ul>
                </li></ul>
              </li></ul>
            </li></ul>
        """.trimIndent()
        val items = parser.extractChecklist(html)
        // alle indentationLevel müssen ≤ 3 sein
        assertTrue("All items capped at 3", items.all { it.indentationLevel <= 3 })
    }
}
