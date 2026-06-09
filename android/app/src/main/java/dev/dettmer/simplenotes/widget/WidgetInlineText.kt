package dev.dettmer.simplenotes.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.Html
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.markdownInlineToHtml

private const val TEXT_FALLBACK_DAY_COLOR = 0x1C1B1F   // MD3 onSurface light
private const val TEXT_FALLBACK_NIGHT_COLOR = 0xE6E1E5  // MD3 onSurface dark
private const val FULL_ALPHA = 0xFF
private const val DIMMED_ALPHA = 0x99 // ~60 %
private const val COLOR_RGB_MASK = 0x00FFFFFF
private const val ALPHA_SHIFT = 24

/**
 * Renders inline-formatted Markdown text in a Glance widget using [AndroidRemoteViews].
 *
 * WORKAROUND: Glance 1.1.1 [androidx.glance.text.Text] only accepts [String], not [AnnotatedString].
 * This composable bridges the gap by delegating to a system [android.widget.TextView] via [RemoteViews],
 * which supports [Html.fromHtml]-based spans (bold, italic, strikethrough, monospace).
 *
 * TODO: Once Glance natively supports [AnnotatedString], replace this composable with a
 *       plain [Text] call using [dev.dettmer.simplenotes.markdown.parseInlineFormatting],
 *       and delete widget_inline_text.xml.
 */
@Composable
internal fun WidgetInlineText(
    text: String,
    fontSize: Float = 14f,
    maxLines: Int = 5,
    dimmed: Boolean = false,
    addStrikethrough: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    val context = LocalContext.current
    val textColor = context.resolveInlineTextColor(dimmed)
    val html = if (addStrikethrough) "<s>${markdownInlineToHtml(text)}</s>"
               else markdownInlineToHtml(text)
    @Suppress("DEPRECATION")
    val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    AndroidRemoteViews(
        remoteViews = RemoteViews(context.packageName, R.layout.widget_inline_text).apply {
            setTextViewText(R.id.widget_inline_text, spanned)
            setTextColor(R.id.widget_inline_text, textColor)
            setFloat(R.id.widget_inline_text, "setTextSize", fontSize)
            setInt(R.id.widget_inline_text, "setMaxLines", maxLines)
        },
        modifier = modifier
    )
}

private fun Context.resolveInlineTextColor(dimmed: Boolean): Int {
    val isNight = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isNight) getColor(android.R.color.system_neutral1_100)
        else getColor(android.R.color.system_neutral1_900)
    } else {
        if (isNight) TEXT_FALLBACK_NIGHT_COLOR else TEXT_FALLBACK_DAY_COLOR
    }
    val alpha = if (dimmed) DIMMED_ALPHA else FULL_ALPHA
    return (base and COLOR_RGB_MASK) or (alpha shl ALPHA_SHIFT)
}
