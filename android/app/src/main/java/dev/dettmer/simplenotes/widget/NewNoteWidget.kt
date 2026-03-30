package dev.dettmer.simplenotes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity

/**
 * v2.2.0: Shortcut-Widget zum schnellen Erstellen neuer Notizen und Checklisten.
 *
 * Uses SizeMode.Exact so Glance renders at the precise size reported by the
 * launcher — avoids breakpoint mismatches that occur with SizeMode.Responsive
 * across different launchers and screen densities.
 *
 * Layout decisions based on actual reported size:
 * - width < 110dp → single note button (1×1 cell)
 * - width >= 110dp → two buttons side-by-side (2+ cells)
 * - height >= 72dp → show text labels below icons
 *
 * Labels erscheinen automatisch wenn genug Platz vorhanden ist.
 * Keine Konfiguration noetig — das Layout passt sich rein an die Groesse an.
 *
 * Feature Request: GitHub Discussion #49 von @Stowaway2979
 */
class NewNoteWidget : GlanceAppWidget() {

    // Exact mode: render at the precise size the launcher reports.
    // Avoids SizeMode.Responsive breakpoint selection failures that caused
    // the two-button layout to never appear on certain launchers.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                NewNoteWidgetContent(context)
            }
        }
    }
}

// ── Content Composable ──

@Composable
private fun NewNoteWidgetContent(context: Context) {
    val size = LocalSize.current
    // 110dp = midpoint between 1-cell (~80dp) and 2-cell (~180dp) widths
    val showBothButtons = size.width >= 110.dp
    val showLabels = showBothButtons && size.height >= 72.dp

    if (!showBothButtons) {
        SingleButtonWidget(context)
    } else {
        TwoButtonWidget(context, showLabels)
    }
}

/**
 * 1x1 Layout: Einzelner gerundeter Button, der die gesamte Widget-Flaeche fuellt.
 * Nutzt primaryContainer als einzigen Hintergrund — ein Layer, null Double-Rounding.
 */
@Composable
private fun SingleButtonWidget(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .clickable(actionStartActivity(buildEditorIntent(context, NoteType.TEXT)))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_new_note_widget),
            contentDescription = context.getString(R.string.new_note_widget_content_description),
            modifier = GlanceModifier.size(28.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
        )
    }
}

/**
 * 2x1+ Layout: Zwei farbige Buttons innerhalb eines widgetBackground-Rahmens.
 * Aeusserer Container nutzt GlanceTheme.colors.widgetBackground — der gleiche Token,
 * den NoteWidgetContent.kt fuer seinen Root-Container nutzt. Dadurch stimmt die
 * Rahmenfarbe exakt mit dem System-Widget-Hintergrund ueberein, und an den
 * abgerundeten Ecken entsteht kein sichtbarer Farbstreifen.
 */
@Composable
private fun TwoButtonWidget(context: Context, showLabels: Boolean) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoteButton(
                context = context,
                label = if (showLabels) context.getString(R.string.new_note_widget_btn_note) else null,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(4.dp))
            ChecklistButton(
                context = context,
                label = if (showLabels) context.getString(R.string.new_note_widget_btn_checklist) else null,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
        }
    }
}

// ── Button Composables ──

@Composable
private fun NoteButton(context: Context, label: String?, modifier: GlanceModifier) {
    ButtonContent(
        icon = R.drawable.ic_new_note_widget,
        contentDesc = context.getString(R.string.new_note_widget_content_description),
        label = label,
        action = actionStartActivity(buildEditorIntent(context, NoteType.TEXT)),
        iconTint = GlanceTheme.colors.onPrimaryContainer,
        labelColor = GlanceTheme.colors.onPrimaryContainer,
        modifier = modifier
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.primaryContainer)
    )
}

@Composable
private fun ChecklistButton(context: Context, label: String?, modifier: GlanceModifier) {
    ButtonContent(
        icon = R.drawable.ic_widget_checklist,
        contentDesc = context.getString(R.string.new_note_widget_checklist_description),
        label = label,
        action = actionStartActivity(buildEditorIntent(context, NoteType.CHECKLIST)),
        iconTint = GlanceTheme.colors.onTertiaryContainer,
        labelColor = GlanceTheme.colors.onTertiaryContainer,
        modifier = modifier
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.tertiaryContainer)
    )
}

@Composable
private fun ButtonContent(
    icon: Int,
    contentDesc: String,
    label: String?,
    action: Action,
    iconTint: ColorProvider,
    labelColor: ColorProvider,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier.clickable(action).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDesc,
            modifier = GlanceModifier.size(24.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(iconTint)
        )
        if (label != null) {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ── Intent Builder ──

private fun buildEditorIntent(context: Context, noteType: NoteType): Intent =
    Intent(context, ComposeNoteEditorActivity::class.java).apply {
        putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_TYPE, noteType.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
