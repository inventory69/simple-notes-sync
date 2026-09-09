package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * Wo die Statistik-Pille überhaupt erscheint — Einstellung, siehe
 * [dev.dettmer.simplenotes.utils.Constants.KEY_WORD_COUNTER_VISIBILITY].
 */
enum class WordCounterVisibility {
    ALWAYS,
    PREVIEW_ONLY,
    OFF;

    fun visibleIn(previewMode: Boolean): Boolean = when (this) {
        ALWAYS -> true
        PREVIEW_ONLY -> previewMode
        OFF -> false
    }
}

/**
 * Anzeigemodus der Statistik-Pille. Tippen läuft im Kreis: Wörter → Zeichen → versteckt.
 * Persistiert wird der Enum-*Name*, siehe [dev.dettmer.simplenotes.utils.Constants.KEY_NOTE_STATS_MODE].
 */
enum class NoteStatsMode {
    WORDS,
    CHARACTERS,
    HIDDEN;

    fun next(): NoteStatsMode = entries[(ordinal + 1) % entries.size]
}

/**
 * 🆕 v2.16.0 (Issue #126): Wort- bzw. Zeichenzahl der Notiz. Tippen wechselt — wie in
 * Notesnook, das der Melder als Vorbild genannt hat.
 *
 * 🎨 v2.16.0: als Material-3-Pille statt als nackter Text. Vorher lag ein unsichtbares
 * `clickable` über die volle Zeilenbreite: keine sichtbare Fläche, ein Ripple über die ganze
 * Breite, kein 48-dp-Ziel, und für einen Screenreader nur Text ohne Rolle und ohne Hinweis,
 * dass er etwas umschaltet. `minimumInteractiveComponentSize` hält das Ziel bei 48 dp, ohne
 * die Pille selbst aufzublasen — dasselbe, was M3 für seine eigenen Chips tut.
 *
 * 🔧 (#126-Nachgang): Die Pille liegt jetzt als Overlay über dem Inhalt statt in einer eigenen
 * Zeile — die 48 dp gehen an den Text zurück. Der Zustand kommt von außen (ViewModel), damit
 * [NoteStatsMode.HIDDEN] notizübergreifend hält.
 *
 * Die Bewegung sitzt bewusst am *Moduswechsel* (`targetState = mode`), nicht an der Zahl:
 * die Zahl ändert sich bei jedem Tastendruck, eine Animation darauf wäre Dauerzappeln.
 */
@Composable
fun NoteStatsPill(
    text: String,
    mode: NoteStatsMode,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val words = remember(text) { countWords(text) }
    val currentLabel = statsLabel(mode, text, words)

    Surface(
        onClick = onCycle,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = currentLabel },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                // Vorwärts im Zyklus schiebt nach oben, der Rundlauf zurück auf WORDS nach unten.
                val up = targetState.ordinal > initialState.ordinal
                val enterOffset = { height: Int -> if (up) height else -height }
                (slideInVertically(initialOffsetY = enterOffset) + fadeIn())
                    .togetherWith(slideOutVertically(targetOffsetY = { if (up) -it else it }) + fadeOut())
            },
            label = "noteStatsUnit"
        ) { frameMode ->
            // Der Inhalt MUSS aus dem Lambda-Parameter kommen, nicht aus `mode`: sonst
            // rendert AnimatedContent den abgehenden Frame schon mit dem neuen Text — beide
            // Hälften der Animation zeigen dasselbe.
            if (frameMode == NoteStatsMode.HIDDEN) {
                Icon(
                    // AutoMirrored ist Pflicht, nicht Kosmetik: die App liefert 16+ Locales aus.
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.note_stats_show),
                    // Schmaler als das Label: mit dem gleichen Padding rundum wird die Pille
                    // hier zum 32-dp-Kreis, das Touch-Ziel hält minimumInteractiveComponentSize.
                    modifier = Modifier
                        .padding(Dimensions.SpacingMedium)
                        .size(Dimensions.IconSizeSmall)
                )
            } else {
                Text(
                    text = statsLabel(frameMode, text, words),
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

/** Beschriftung für einen Modus — einmal für die Semantik, einmal je Animationsframe. */
@Composable
private fun statsLabel(mode: NoteStatsMode, text: String, words: Int): String = when (mode) {
    NoteStatsMode.WORDS -> pluralStringResource(R.plurals.note_stats_words, words, words)
    NoteStatsMode.CHARACTERS ->
        pluralStringResource(R.plurals.note_stats_characters, text.length, text.length)
    NoteStatsMode.HIDDEN -> stringResource(R.string.note_stats_show)
}
