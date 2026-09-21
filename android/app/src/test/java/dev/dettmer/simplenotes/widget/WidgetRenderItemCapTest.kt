package dev.dettmer.simplenotes.widget

import dev.dettmer.simplenotes.markdown.MarkdownEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #154: Die Zeilenzahl im Widget ist ein Stabilitätslimit, kein Schönheitslimit — jedes Item
 * kostet ~3,2 KB in der RemoteViews-Transaktion, und ein zu großer Parcel tötet den AppWidget-Host
 * des Launchers. Wie viele Items erlaubt sind, rechnet [WidgetPayloadBudget] aus; dieser Test hält
 * fest, dass `flattenToRenderItems` das übergebene `maxItems` **nie** überschreitet, auch nicht um
 * das eine Item, das der Block-Spacer vor der Prüfung einschiebt.
 */
class WidgetRenderItemCapTest {
    private fun renderItems(content: String, maxItems: Int) =
        flattenToRenderItems(MarkdownEngine.parse(content), maxItems)

    @Test
    fun `the reported note stays within the cap`() {
        // Genau die Notiz aus Issue #154: 13 Absätze, jeder gefolgt von einer Ein-Punkt-Liste.
        val content = List(13) { "Head\n- item" }.joinToString("\n\n")
        assertEquals(20, renderItems(content, maxItems = 20).size)
    }

    @Test
    fun `long notes never exceed the cap`() {
        val content = List(200) { "Head\n- item" }.joinToString("\n\n")
        for (cap in listOf(1, 2, 3, 7, 20, 51)) {
            assertTrue(
                "cap=$cap überschritten",
                renderItems(content, maxItems = cap).size <= cap
            )
        }
    }

    @Test
    fun `short notes are not padded to the cap`() {
        assertTrue(renderItems("Head\n- item", maxItems = 20).size < 20)
    }
}
