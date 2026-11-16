package dev.dettmer.simplenotes.markdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ein Wort ohne Leerraum über ~100 000 Zeichen ließ Androids Textlayout auf über 2 GB wachsen. */
class LayoutBreaksTest {
    @Test fun `normal text gets no marks`() {
        val prose = "Ein ganz normaler Satz. ".repeat(10_000)
        assertEquals(emptyList<Int>(), layoutBreakPositions(prose))
    }

    @Test fun `a long word gets a mark every thousand chars`() {
        val text = "a " + "x".repeat(2_500) + " b"
        assertEquals(listOf(1_002, 2_002), layoutBreakPositions(text))
    }

    @Test fun `surrogate pairs are never split`() {
        val text = "😀".repeat(2_000) + "x".repeat(2_000)
        layoutBreakPositions(text).forEach { cut ->
            assertTrue(!text[cut - 1].isHighSurrogate() && !text[cut].isLowSurrogate())
        }
    }

    @Test fun `display offsets map back to the stored text`() {
        val text = "x".repeat(3_000) + "\n- [ ] Aufgabe"
        val cuts = layoutBreakPositions(text)
        val shown = StringBuilder(text).apply { cuts.asReversed().forEach { insert(it, LAYOUT_BREAK) } }.toString()
        val target = text.indexOf('[')
        assertEquals(target, toSourceOffset(text, shown.indexOf('[')))
        assertEquals(0, toSourceOffset(text, 0))
    }

    @Test fun `styles keep covering their own text`() {
        val styled = buildAnnotatedString {
            append("x".repeat(1_500))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("fett") }
        }.withLayoutBreaks()

        assertEquals(1, styled.text.count { it == LAYOUT_BREAK })
        val bold = styled.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("fett", styled.text.substring(bold.start, bold.end))
    }
}
