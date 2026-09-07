package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 *
 * 🎨 v2.16.0: als Material-3-Pille statt als nackter Text. Vorher lag ein unsichtbares
 * `clickable` über die volle Zeilenbreite: keine sichtbare Fläche, ein Ripple über die ganze
 * Breite, kein 48-dp-Ziel, und für einen Screenreader nur Text ohne Rolle und ohne Hinweis,
 * dass er etwas umschaltet. `minimumInteractiveComponentSize` hält das Ziel bei 48 dp, ohne
 * die Pille selbst aufzublasen — dasselbe, was M3 für seine eigenen Chips tut.
 *
 * Die Bewegung sitzt bewusst am *Moduswechsel* (`targetState = showWords`), nicht an der Zahl:
 * die Zahl ändert sich bei jedem Tastendruck, eine Animation darauf wäre Dauerzappeln.
 */
@Composable
fun NoteStatsRow(text: String, modifier: Modifier = Modifier) {
    var showWords by rememberSaveable { mutableStateOf(true) }
    val words = remember(text) { countWords(text) }

    val currentLabel = statsLabel(showWords, text, words)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = { showWords = !showWords },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics { stateDescription = currentLabel },
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            AnimatedContent(
                targetState = showWords,
                transitionSpec = {
                    // Wörter → Zeichen schiebt nach oben, zurück nach unten.
                    val up = targetState
                    val enterOffset = { height: Int -> if (up) height else -height }
                    (slideInVertically(initialOffsetY = enterOffset) + fadeIn())
                        .togetherWith(slideOutVertically(targetOffsetY = { if (up) -it else it }) + fadeOut())
                },
                label = "noteStatsUnit"
            ) { wordsMode ->
                // Das Label MUSS aus dem Lambda-Parameter kommen, nicht aus `showWords`: sonst
                // rendert AnimatedContent den abgehenden Frame schon mit dem neuen Text — beide
                // Hälften der Animation zeigen dasselbe.
                Text(
                    text = statsLabel(wordsMode, text, words),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(
                        horizontal = Dimensions.SpacingMediumLarge,
                        vertical = Dimensions.SpacingMedium
                    )
                )
            }
        }
    }
}

/** Beschriftung für einen der beiden Modi — einmal für die Semantik, einmal je Animationsframe. */
@Composable
private fun statsLabel(showWords: Boolean, text: String, words: Int): String = if (showWords) {
    pluralStringResource(R.plurals.note_stats_words, words, words)
} else {
    pluralStringResource(R.plurals.note_stats_characters, text.length, text.length)
}
