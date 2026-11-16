package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test

/** #179: zweiter Tipp auf Fett/Kursiv/Durchgestrichen nimmt die Zeichen wieder weg. */
class ToggleMarkerTest {
    private fun toggle(text: String, sel: TextRange, marker: String): Pair<String, TextRange> {
        val state = TextFieldState(text, sel)
        toggleMarker(state, marker)
        return state.text.toString() to state.selection
    }

    @Test fun `wraps and unwraps a selection`() {
        assertEquals("a **word** b" to TextRange(4, 8), toggle("a word b", TextRange(2, 6), "**"))
        assertEquals("a word b" to TextRange(2, 6), toggle("a **word** b", TextRange(4, 8), "**"))
    }

    @Test fun `empty pair at cursor is removed again`() {
        assertEquals("ab" to TextRange(1), toggle("a****b", TextRange(3), "**"))
    }

    @Test fun `italic inside bold adds italic instead of breaking bold`() {
        assertEquals("***word***", toggle("**word**", TextRange(2, 6), "*").first)
        assertEquals("**word**", toggle("***word***", TextRange(3, 7), "*").first)
        assertEquals("*word*", toggle("***word***", TextRange(3, 7), "**").first)
    }

    @Test fun `strikethrough toggles`() {
        assertEquals("x" to TextRange(0, 1), toggle("~~x~~", TextRange(2, 3), "~~"))
    }
}
