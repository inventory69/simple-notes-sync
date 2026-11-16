package dev.dettmer.simplenotes.markdown

import android.content.ClipData
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/*
 * Lange Wörter ohne Leerraum (Base64, Daten-URLs) bringen Androids Textlayout an seine Grenze:
 * Minikin legt für jede umbrochene Zeile das ganze Wort als Cache-Kontext ab, quadratisch in der
 * Wortlänge. 100 000 Zeichen kosteten 470 MB nativen Speicher, 250 000 über 2 GB, und Android
 * beendete die App beim Einfügen und danach bei jedem Öffnen der Notiz.
 *
 * Den Kontext beendet Minikin an Bidi-Steuerzeichen (LayoutUtils, isWordBreakAfter). Ein
 * unsichtbares PDI alle [LAYOUT_BREAK_EVERY] Zeichen eines Laufs hält das Layout linear. Ohne
 * öffnendes Gegenstück wirkt PDI nicht auf die Textrichtung.
 *
 * Nur für die Anzeige: gespeichert wird der Text ohne Marken, beim Kopieren fallen sie raus.
 */
internal const val LAYOUT_BREAK = '\u2069'
private const val LAYOUT_BREAK_EVERY = 1000

/** Ab hier beginnen Akzente, Surrogate und Emoji-Verbinder. Dazwischen wird nie geschnitten. */
private const val FIRST_JOINING_CHAR = '\u0300'

/** Stellen im Text, vor die eine [LAYOUT_BREAK] kommt: nach je [LAYOUT_BREAK_EVERY] Zeichen ohne Leerraum. */
internal fun layoutBreakPositions(text: CharSequence): List<Int> {
    val cuts = mutableListOf<Int>()
    var run = 0
    for (i in 0 until text.length - 1) {
        if (text[i].isWhitespace()) {
            run = 0
        } else if (++run >= LAYOUT_BREAK_EVERY && text[i] < FIRST_JOINING_CHAR && text[i + 1] < FIRST_JOINING_CHAR) {
            cuts += i + 1
            run = 0
        }
    }
    return cuts
}

internal fun AnnotatedString.withLayoutBreaks(): AnnotatedString {
    val cuts = layoutBreakPositions(text)
    if (cuts.isEmpty()) return this
    val source = this
    return buildAnnotatedString {
        var from = 0
        for (cut in cuts) {
            append(source.subSequence(from, cut))
            append(LAYOUT_BREAK)
            from = cut
        }
        append(source.subSequence(from, source.length))
    }
}

/** Offset im angezeigten Text (mit Marken) → Offset in [source] (ohne). */
internal fun toSourceOffset(source: CharSequence, displayOffset: Int): Int {
    var shift = 0
    for (cut in layoutBreakPositions(source)) {
        if (cut + shift >= displayOffset) break
        shift++
    }
    return displayOffset - shift
}

/** [SelectionContainer], aus dem markierter Text ohne Marken in die Zwischenablage geht. */
@Composable
internal fun SelectionContainerWithoutLayoutBreaks(modifier: Modifier, content: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val stripping = remember(clipboard) {
        object : Clipboard by clipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) = clipboard.setClipEntry(clipEntry?.withoutLayoutBreaks())
        }
    }
    CompositionLocalProvider(LocalClipboard provides stripping) { SelectionContainer(modifier, content) }
}

/** Kopierter Text aus einer Anzeige mit Marken, ohne sie. Formatierung fällt dabei weg. */
internal fun ClipEntry.withoutLayoutBreaks(): ClipEntry {
    val data = clipData
    val text = (0 until data.itemCount).joinToString("") { data.getItemAt(it).text?.toString().orEmpty() }
    if (LAYOUT_BREAK !in text) return this
    return ClipEntry(ClipData.newPlainText(data.description?.label, text.replace(LAYOUT_BREAK.toString(), "")))
}
