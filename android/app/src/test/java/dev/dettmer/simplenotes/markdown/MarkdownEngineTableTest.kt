package dev.dettmer.simplenotes.markdown

import dev.dettmer.simplenotes.markdown.MarkdownEngine.ColumnAlign
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 GFM-Pipe-Tabellen: der Desktop rendert sie über `marked`, Android muss dasselbe zeigen.
 *
 * Kern der Regeln: eine Tabelle beginnt nur, wenn die FOLGEZEILE eine Trennzeile mit gleich
 * vielen Zellen ist — sonst bleibt `a | b` im Fließtext ein Absatz.
 */
class MarkdownEngineTableTest {
    private fun firstTable(text: String) =
        MarkdownEngine.parse(text).filterIsInstance<MarkdownBlock.Table>().first()

    @Test fun `header delimiter and body become a table`() {
        val table = firstTable("| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |")

        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.rows)
    }

    @Test fun `alignment markers drive the column alignment`() {
        val table = firstTable("| l | c | r | d |\n| :--- | :---: | ---: | --- |\n| 1 | 2 | 3 | 4 |")

        assertEquals(
            listOf(ColumnAlign.LEFT, ColumnAlign.CENTER, ColumnAlign.RIGHT, ColumnAlign.LEFT),
            table.alignments
        )
    }

    @Test fun `a pipe in running text stays a paragraph`() {
        val blocks = MarkdownEngine.parse("Kosten a | b sind hoch\nund die naechste Zeile auch")

        assertTrue("ohne Trennzeile keine Tabelle", blocks.none { it is MarkdownBlock.Table })
        assertEquals(1, blocks.filterIsInstance<MarkdownBlock.Paragraph>().size)
    }

    @Test fun `outer pipes are optional and escaped pipes stay literal`() {
        val table = firstTable("a | b\n--- | ---\n1 \\| 2 | 3")

        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("1 | 2", "3")), table.rows)
    }

    @Test fun `short rows are padded and an extra cell widens the table`() {
        val table = firstTable("| a | b |\n| --- | --- |\n| 1 |\n| 1 | 2 | 3 |")

        // Die zusätzliche Zelle erweitert die Tabelle, statt verschluckt zu werden — genau so
        // fügt man im Editor eine Spalte hinzu. Kopf und kurze Zeilen werden aufgefüllt.
        assertEquals(listOf("a", "b", ""), table.header)
        assertEquals(3, table.alignments.size)
        assertEquals(listOf(listOf("1", "", ""), listOf("1", "2", "3")), table.rows)
    }

    @Test fun `a stray delimiter row in the body is dropped`() {
        val table = firstTable("| a | b |\n| --- | --- |\n| 1 | 2 |\n| --- | --- |\n| 3 | 4 |")

        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.rows)
    }

    @Test fun `a row that only looks like a delimiter keeps its content`() {
        val table = firstTable("| a | b |\n| --- | --- |\n| --- | offen |")

        assertEquals(listOf(listOf("---", "offen")), table.rows)
    }

    @Test fun `tableAt reports the end and the widest row of the table`() {
        val text = "davor\n| a | b |\n| --- | --- |\n| 1 | 2 | 3 |\ndanach"

        val span = MarkdownEngine.tableAt(text, lineIndex = 2)

        assertEquals(3, span?.lastLine)
        assertEquals(3, span?.columns)
    }

    @Test fun `tableAt returns null outside a table`() {
        val text = "nur Text\n| a | b |"

        assertEquals(null, MarkdownEngine.tableAt(text, lineIndex = 0))
        assertEquals(null, MarkdownEngine.tableAt(text, lineIndex = 1))
    }

    @Test fun `a table directly under a paragraph keeps both blocks`() {
        val blocks = MarkdownEngine.parse("Davor ohne Leerzeile\n| a | b |\n| --- | --- |\n| 1 | 2 |")

        assertEquals("Davor ohne Leerzeile", (blocks[0] as MarkdownBlock.Paragraph).text)
        assertEquals(listOf("a", "b"), (blocks[1] as MarkdownBlock.Table).header)
    }

    @Test fun `a table cut off mid row still parses`() {
        val table = firstTable("| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | unvollstaen")

        assertEquals(listOf(listOf("1", "2"), listOf("3", "unvollstaen")), table.rows)
    }

    @Test fun `an image after a table keeps its ordinal`() {
        val text = "| a | b |\n| --- | --- |\n| ![x](.assets/one.png) | c |\n\n![y](.assets/two.png)"
        val image = MarkdownEngine.parse(text).filterIsInstance<MarkdownBlock.Image>().first()

        assertEquals("two.png", image.assetName)
        assertEquals("das Bild in der Tabellenzelle zaehlt mit", 1, image.ordinal)
    }

    @Test fun `a half typed delimiter cell keeps the table alive`() {
        // Genau der Fall aus der Praxis: in die Trennzeile getippt statt in die Body-Zeile.
        val table = firstTable("| lelel | lelelelel |\n| -lblblblb-- | --- |\n| a | b |")

        assertEquals(listOf("lelel", "lelelelel"), table.header)
        assertEquals(listOf(ColumnAlign.LEFT, ColumnAlign.LEFT), table.alignments)
        assertEquals(listOf(listOf("a", "b")), table.rows)
    }

    @Test fun `a delimiter row with a different cell count still starts a table`() {
        val table = firstTable("| a | b | c |\n| --- |\n| 1 | 2 | 3 |")

        assertEquals(3, table.header.size)
        assertEquals(3, table.alignments.size)
        assertEquals(listOf(listOf("1", "2", "3")), table.rows)
    }

    @Test fun `a second line without any delimiter cell stays a paragraph`() {
        val blocks = MarkdownEngine.parse("Kosten a | b sind hoch\nMiete c | d ist fix")

        assertTrue("ohne echte ---Zelle keine Tabelle", blocks.none { it is MarkdownBlock.Table })
    }

    @Test fun `flattenTableRows turns a table into plain text`() {
        val flat = MarkdownEngine.flattenTableRows("Davor\n| a | b |\n| --- | --- |\n| 1 | 2 |\nDanach")

        assertEquals("Davor\na · b\n1 · 2\nDanach", flat)
    }

    @Test fun `flattenTableRows leaves running text alone`() {
        val text = "Kosten a | b sind hoch"

        assertEquals(text, MarkdownEngine.flattenTableRows(text))
    }
}
