package dev.dettmer.simplenotes.ui.editor.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🆕 v2.16.0 (Issue #126): Wortzählung.
 *
 * Der Zähler läuft bei jedem Tastendruck — deshalb ein Durchlauf ohne Allokation statt
 * `split(Regex)`. Diese Tests halten fest, dass die Handzählung dasselbe liefert wie die
 * naive Variante, inklusive der Randfälle, an denen ein Split danebenliegt (führender und
 * mehrfacher Whitespace, Zeilenumbrüche, Tabs).
 */
class NoteStatsTest {

    @Test fun `counts plain words`() {
        assertEquals(3, countWords("Milch Brot Eier"))
    }

    @Test fun `leading and trailing whitespace does not create empty words`() {
        assertEquals(2, countWords("   Milch Brot   "))
    }

    @Test fun `runs of whitespace count as one separator`() {
        assertEquals(2, countWords("Milch     Brot"))
        assertEquals(2, countWords("Milch\n\n\nBrot"))
        assertEquals(2, countWords("Milch\t\tBrot"))
    }

    @Test fun `line breaks separate words`() {
        assertEquals(4, countWords("Einkaufen\nMilch\nBrot\nEier"))
    }

    @Test fun `empty and blank text has no words`() {
        assertEquals(0, countWords(""))
        assertEquals(0, countWords("   \n\t  "))
    }

    @Test fun `punctuation stays part of its word`() {
        assertEquals(3, countWords("Hallo, wie geht's?"))
        assertEquals(4, countWords("Eins. Zwei! Drei? Vier"))
    }

    @Test fun `matches a naive split for ordinary prose`() {
        val text = "Der schnelle braune Fuchs springt über den faulen Hund"
        val naive = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        assertEquals(naive, countWords(text))
    }
}
