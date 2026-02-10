package dev.dettmer.simplenotes.widget

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity

/**
 * 🆕 v1.8.0: Glance Composable Content für das Notiz-Widget
 *
 * Unterstützt fünf responsive Größenklassen (breit + schmal),
 * NoteType-Icons, permanenten Options-Button, und einstellbare Opacity.
 */

// ── Size Classification ──

enum class WidgetSizeClass {
    SMALL,        // Nur Titel
    NARROW_MED,   // Schmal, Vorschau
    NARROW_TALL,  // Schmal, voller Inhalt
    WIDE_MED,     // Breit, Vorschau
    WIDE_TALL     // Breit, voller Inhalt
}

private fun DpSize.toSizeClass(): WidgetSizeClass = when {
    height < 110.dp                    -> WidgetSizeClass.SMALL
    width < 250.dp && height < 250.dp  -> WidgetSizeClass.NARROW_MED
    width < 250.dp                     -> WidgetSizeClass.NARROW_TALL
    height < 250.dp                    -> WidgetSizeClass.WIDE_MED
    else                               -> WidgetSizeClass.WIDE_TALL
}

@Composable
fun NoteWidgetContent(
    note: Note?,
    isLocked: Boolean,
    showOptions: Boolean,
    bgOpacity: Float,
    glanceId: GlanceId
) {
    val size = LocalSize.current
    val context = LocalContext.current
    val sizeClass = size.toSizeClass()

    if (note == null) {
        EmptyWidgetContent(bgOpacity)
        return
    }

    // Background mit Opacity
    val bgModifier = if (bgOpacity < 1.0f) {
        GlanceModifier.background(
            ColorProvider(
                day = Color.White.copy(alpha = bgOpacity),
                night = Color(0xFF1C1B1F).copy(alpha = bgOpacity)
            )
        )
    } else {
        GlanceModifier.background(GlanceTheme.colors.widgetBackground)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .then(bgModifier)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 🆕 v1.8.0 (IMPL_025): Offizielle TitleBar mit CircleIconButton (48dp Hit Area)
            TitleBar(
                startIcon = ImageProvider(
                    when {
                        isLocked -> R.drawable.ic_lock
                        note.noteType == NoteType.CHECKLIST -> R.drawable.ic_widget_checklist
                        else -> R.drawable.ic_note
                    }
                ),
                title = note.title.ifEmpty { "Untitled" },
                iconColor = GlanceTheme.colors.onSurface,
                textColor = GlanceTheme.colors.onSurface,
                actions = {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.ic_more_vert),
                        contentDescription = "Options",
                        backgroundColor = null, // Transparent → nur Icon + 48x48dp Hit Area
                        contentColor = GlanceTheme.colors.onSurface,
                        onClick = actionRunCallback<ShowOptionsAction>(
                            actionParametersOf(
                                NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                            )
                        )
                    )
                }
            )

            // Optionsleiste (ein-/ausblendbar)
            if (showOptions) {
                OptionsBar(
                    isLocked = isLocked,
                    noteId = note.id,
                    glanceId = glanceId
                )
            }

            // Content-Bereich — Click öffnet Editor (unlocked) oder Options (locked)
            val contentClickModifier = GlanceModifier
                .fillMaxSize()
                .clickable(
                    onClick = if (!isLocked) {
                        actionStartActivity(
                            ComponentName(context, ComposeNoteEditorActivity::class.java),
                            actionParametersOf(
                                androidx.glance.action.ActionParameters.Key<String>("extra_note_id") to note.id
                            )
                        )
                    } else {
                        actionRunCallback<ShowOptionsAction>(
                            actionParametersOf(
                                NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                            )
                        )
                    }
                )

            // Content — abhängig von SizeClass
            when (sizeClass) {
                WidgetSizeClass.SMALL -> {
                    // Nur TitleBar, leerer Body als Click-Target
                    Box(modifier = contentClickModifier) {}
                }

                WidgetSizeClass.NARROW_MED -> Box(modifier = contentClickModifier) {
                    when (note.noteType) {
                        NoteType.TEXT -> TextNotePreview(note, compact = true)
                        NoteType.CHECKLIST -> ChecklistCompactView(
                            note = note,
                            maxItems = 2,
                            isLocked = isLocked,
                            glanceId = glanceId
                        )
                    }
                }

                WidgetSizeClass.NARROW_TALL -> Box(modifier = contentClickModifier) {
                    when (note.noteType) {
                        NoteType.TEXT -> TextNoteFullView(note)
                        NoteType.CHECKLIST -> ChecklistFullView(
                            note = note,
                            isLocked = isLocked,
                            glanceId = glanceId
                        )
                    }
                }

                WidgetSizeClass.WIDE_MED -> Box(modifier = contentClickModifier) {
                    when (note.noteType) {
                        NoteType.TEXT -> TextNotePreview(note, compact = false)
                        NoteType.CHECKLIST -> ChecklistCompactView(
                            note = note,
                            maxItems = 3,
                            isLocked = isLocked,
                            glanceId = glanceId
                        )
                    }
                }

                WidgetSizeClass.WIDE_TALL -> Box(modifier = contentClickModifier) {
                    when (note.noteType) {
                        NoteType.TEXT -> TextNoteFullView(note)
                        NoteType.CHECKLIST -> ChecklistFullView(
                            note = note,
                            isLocked = isLocked,
                            glanceId = glanceId
                        )
                    }
                }
            }
        }
    }
}

/**
 * Optionsleiste — Lock/Unlock + Refresh + Open in App
 */
@Composable
private fun OptionsBar(
    isLocked: Boolean,
    noteId: String,
    glanceId: GlanceId
) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(GlanceTheme.colors.secondaryContainer),
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lock/Unlock Toggle
        Image(
            provider = ImageProvider(
                if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock
            ),
            contentDescription = if (isLocked) "Unlock" else "Lock",
            modifier = GlanceModifier
                .size(36.dp)
                .padding(6.dp)
                .clickable(
                    onClick = actionRunCallback<ToggleLockAction>(
                        actionParametersOf(
                            NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                        )
                    )
                )
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        // Refresh
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Refresh",
            modifier = GlanceModifier
                .size(36.dp)
                .padding(6.dp)
                .clickable(
                    onClick = actionRunCallback<RefreshAction>(
                        actionParametersOf(
                            NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                        )
                    )
                )
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        // Settings (Reconfigure)
        Image(
            provider = ImageProvider(R.drawable.ic_settings),
            contentDescription = "Settings",
            modifier = GlanceModifier
                .size(36.dp)
                .padding(6.dp)
                .clickable(
                    onClick = actionRunCallback<OpenConfigAction>(
                        actionParametersOf(
                            NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                        )
                    )
                )
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        // Open in App
        Image(
            provider = ImageProvider(R.drawable.ic_open_in_new),
            contentDescription = "Open",
            modifier = GlanceModifier
                .size(36.dp)
                .padding(6.dp)
                .clickable(
                    onClick = actionStartActivity(
                        ComponentName(context, ComposeNoteEditorActivity::class.java),
                        actionParametersOf(
                            androidx.glance.action.ActionParameters.Key<String>("extra_note_id") to noteId
                        )
                    )
                )
        )
    }
}

// ── Text Note Views ──

@Composable
private fun TextNotePreview(note: Note, compact: Boolean) {
    Text(
        text = note.content.take(if (compact) 100 else 200),
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = if (compact) 13.sp else 14.sp
        ),
        maxLines = if (compact) 2 else 3,
        modifier = GlanceModifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun TextNoteFullView(note: Note) {
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        val paragraphs = note.content.split("\n").filter { it.isNotBlank() }
        items(paragraphs.size) { index ->
            Text(
                text = paragraphs[index],
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )
        }
    }
}

// ── Checklist Views ──

/**
 * Kompakte Checklist-Ansicht für MEDIUM-Größen.
 * Zeigt maxItems interaktive Checkboxen + Zusammenfassung.
 */
@Composable
private fun ChecklistCompactView(
    note: Note,
    maxItems: Int,
    isLocked: Boolean,
    glanceId: GlanceId
) {
    val items = note.checklistItems?.sortedBy { it.order } ?: return
    val visibleItems = items.take(maxItems)
    val remainingCount = items.size - visibleItems.size
    val checkedCount = items.count { it.isChecked }

    Column(modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        visibleItems.forEach { item ->
            if (isLocked) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "✅" else "☐",
                        style = TextStyle(fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = item.text,
                        style = TextStyle(
                            color = if (item.isChecked) GlanceTheme.colors.outline
                            else GlanceTheme.colors.onSurface,
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                }
            } else {
                CheckBox(
                    checked = item.isChecked,
                    onCheckedChange = actionRunCallback<ToggleChecklistItemAction>(
                        actionParametersOf(
                            NoteWidgetActionKeys.KEY_NOTE_ID to note.id,
                            NoteWidgetActionKeys.KEY_ITEM_ID to item.id,
                            NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                        )
                    ),
                    text = item.text,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                )
            }
        }

        if (remainingCount > 0) {
            Text(
                text = "+$remainingCount more · ✔ $checkedCount/${items.size}",
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = 12.sp
                ),
                modifier = GlanceModifier.padding(top = 2.dp, start = 4.dp)
            )
        }
    }
}

/**
 * Vollständige Checklist-Ansicht für LARGE-Größen.
 */
@Composable
private fun ChecklistFullView(
    note: Note,
    isLocked: Boolean,
    glanceId: GlanceId
) {
    val items = note.checklistItems?.sortedBy { it.order } ?: return

    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]

            if (isLocked) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "✅" else "☐",
                        style = TextStyle(fontSize = 16.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = item.text,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp
                        ),
                        maxLines = 2
                    )
                }
            } else {
                CheckBox(
                    checked = item.isChecked,
                    onCheckedChange = actionRunCallback<ToggleChecklistItemAction>(
                        actionParametersOf(
                            NoteWidgetActionKeys.KEY_NOTE_ID to note.id,
                            NoteWidgetActionKeys.KEY_ITEM_ID to item.id,
                            NoteWidgetActionKeys.KEY_GLANCE_ID to glanceId.toString()
                        )
                    ),
                    text = item.text,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                )
            }
        }
    }
}

// ── Empty State ──

@Composable
private fun EmptyWidgetContent(bgOpacity: Float) {
    val bgModifier = if (bgOpacity < 1.0f) {
        GlanceModifier.background(
            ColorProvider(
                day = Color.White.copy(alpha = bgOpacity),
                night = Color(0xFF1C1B1F).copy(alpha = bgOpacity)
            )
        )
    } else {
        GlanceModifier.background(GlanceTheme.colors.widgetBackground)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .then(bgModifier)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Note not found",
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = 14.sp
            )
        )
    }
}
