package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * 🆕 v2.16.0 (Issue #126): Zählt die Wörter eines Textes.
 *
 * Bewusst eine Schleife statt `split(Regex("\\s+"))`: Der Zähler läuft bei jedem Tastendruck,
 * und der Split legte dafür jedes Mal eine Liste aller Wörter an. Diese Variante ist ein
 * einzelner Durchlauf ohne Allokation — bei einer 50-000-Zeichen-Notiz Mikrosekunden.
 *
 * Wort = jede Folge von Nicht-Whitespace. Für Sprachen ohne Wortabstände (Chinesisch,
 * Japanisch) ist das Ergebnis dadurch die Zahl der Textblöcke, nicht der Wörter — dafür
 * bräuchte es einen BreakIterator, und die Zeichenzahl daneben beantwortet dieselbe Frage.
 */
internal fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}

/**
 * 🆕 v2.16.0 (Issue #126): Wort- bzw. Zeichenzahl unter der Notiz. Tippen wechselt zwischen
 * beiden — wie in Notesnook, das der Melder als Vorbild genannt hat.
 */
@Composable
fun NoteStatsRow(text: String, modifier: Modifier = Modifier) {
    var showWords by rememberSaveable { mutableStateOf(true) }
    val words = remember(text) { countWords(text) }

    Text(
        text = if (showWords) {
            pluralStringResource(R.plurals.note_stats_words, words, words)
        } else {
            pluralStringResource(R.plurals.note_stats_characters, text.length, text.length)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showWords = !showWords }
            .padding(top = Dimensions.SpacingSmall, bottom = Dimensions.SpacingSmall)
    )
}
