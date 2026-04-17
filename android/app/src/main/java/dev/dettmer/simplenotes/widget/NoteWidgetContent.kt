package dev.dettmer.simplenotes.widget

import android.content.ComponentName
import android.os.Build
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
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.ui.main.components.sortChecklistItemsForPreview
import dev.dettmer.simplenotes.utils.Logger

private const val TAG = "NoteWidgetContent"

// Maximum number of lines/items to render in the widget to prevent
// TransactionTooLargeException (1MB Binder limit for RemoteViews).
private const val WIDGET_MAX_TEXT_LINES = 100
private const val WIDGET_MAX_CHECKLIST_ITEMS = 100

/**
 * 🆕 v1.8.0: Glance Composable Content für das Notiz-Widget
 *
 * Unterstützt fünf responsive Größenklassen (breit + schmal),
 * NoteType-Icons, permanenten Options-Button, und einstellbare Opacity.
 */

// ── Size Classification ──

private val WIDGET_HEIGHT_SMALL_THRESHOLD = 110.dp
private val WIDGET_HEIGHT_SCROLL_THRESHOLD = 150.dp // 🆕 v1.8.1: Scrollbare Ansicht
private val WIDGET_SIZE_MEDIUM_THRESHOLD = 250.dp

private fun DpSize.toSizeClass(): WidgetSizeClass = when {
    height < WIDGET_HEIGHT_SMALL_THRESHOLD -> WidgetSizeClass.SMALL

    // 🆕 v1.8.1: Neue ScrollView-Schwelle bei 150dp Höhe
    width < WIDGET_SIZE_MEDIUM_THRESHOLD && height < WIDGET_HEIGHT_SCROLL_THRESHOLD -> WidgetSizeClass.NARROW_MED
    width < WIDGET_SIZE_MEDIUM_THRESHOLD && height < WIDGET_SIZE_MEDIUM_THRESHOLD -> WidgetSizeClass.NARROW_SCROLL
    width < WIDGET_SIZE_MEDIUM_THRESHOLD -> WidgetSizeClass.NARROW_TALL

    height < WIDGET_HEIGHT_SCROLL_THRESHOLD -> WidgetSizeClass.WIDE_MED
    height < WIDGET_SIZE_MEDIUM_THRESHOLD -> WidgetSizeClass.WIDE_SCROLL
    else -> WidgetSizeClass.WIDE_TALL
}

/**
 * 🆕 v1.8.1 (IMPL_04): Separator zwischen erledigten und unerledigten Items im Widget.
 * Glance-kompatible Version von CheckedItemsSeparator.
 */
@Composable
private fun WidgetCheckedItemsSeparator(checkedCount: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "── $checkedCount ✔ ──",
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = 11.sp
            )
        )
    }
}

// ── Background Color Helpers ──

// 🆕 v1.9.0 (F01): Fallback-Farben für Geräte ohne Dynamic Color (vor Android 12)
private const val BG_FALLBACK_DAY_COLOR = 0xFFF5F5F5L // Helles Material-Surface
private const val BG_FALLBACK_NIGHT_COLOR = 0xFF1C1B1FL // Dunkles Material-Surface

/**
 * 🆕 v1.9.0 (F01): Löst die Monet/Dynamic-Color widgetBackground auf und wendet
 * die konfigurierte Opacity an. Dadurch bleibt der Material-You-Tint bei jeder
 * Transparenzstufe erhalten — anstatt auf ein hartkodiertes Neutral zurückzufallen.
 *
 * Strategie: Da GlanceTheme.colors.widgetBackground keinen raw Color-Wert exponiert,
 * lesen wir die System-Dynamic-Color-Tokens direkt via Context.
 */
@Composable
private fun resolveWidgetBackgroundModifier(bgOpacity: Float): GlanceModifier {
    if (bgOpacity >= 1.0f) {
        // Volle Deckkraft → Standard-GlanceTheme-Hintergrund (keine Berechnung nötig)
        return GlanceModifier.background(GlanceTheme.colors.widgetBackground)
    }

    val context = LocalContext.current
    val dayColor: Color
    val nightColor: Color

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+: echte Dynamic Color Tokens (gleiche die Glance intern nutzt)
        dayColor = Color(context.getColor(android.R.color.system_accent1_100)).copy(alpha = bgOpacity)
        nightColor = Color(context.getColor(android.R.color.system_neutral1_800)).copy(alpha = bgOpacity)
    } else {
        // Vor Android 12: Fallback auf Material-Standardwerte mit Alpha
        dayColor = Color(BG_FALLBACK_DAY_COLOR.toInt()).copy(alpha = bgOpacity)
        nightColor = Color(BG_FALLBACK_NIGHT_COLOR.toInt()).copy(alpha = bgOpacity)
    }

    return GlanceModifier.background(ColorProvider(day = dayColor, night = nightColor))
}

@Composable
fun NoteWidgetContent(note: Note?, isLocked: Boolean, showOptions: Boolean, bgOpacity: Float, glanceId: GlanceId) {
    val size = LocalSize.current
    val context = LocalContext.current
    val sizeClass = size.toSizeClass()

    if (note == null) {
        EmptyWidgetContent(bgOpacity)
        return
    }

    // 🆕 v1.9.0 (F01): Translucenter Hintergrund mit Monet-Tint bei beliebiger Opacity
    val bgModifier = resolveWidgetBackgroundModifier(bgOpacity)

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
            // 🆕 v1.9.0 (F02): bgOpacity weitergeben für farblich passende Leiste
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

                // 🆕 v1.8.2: Text-Notizen scrollbar auch in NARROW_MED (2x1 Widgets)
                WidgetSizeClass.NARROW_MED -> {
                    when (note.noteType) {
                        NoteType.TEXT -> Box(modifier = contentClickModifier) {
                            TextNoteFullView(note)
                        }
                        NoteType.CHECKLIST -> Box(modifier = contentClickModifier) {
                            ChecklistCompactView(
                                note = note,
                                maxItems = 2,
                                isLocked = isLocked,
                                glanceId = glanceId
                            )
                        }
                    }
                }

                // 🆕 v1.8.1 (IMPL_09): Scrollbare Größe (150dp+ Höhe)
                WidgetSizeClass.NARROW_SCROLL,
                WidgetSizeClass.NARROW_TALL -> {
                    when (note.noteType) {
                        NoteType.TEXT -> Box(modifier = contentClickModifier) {
                            TextNoteFullView(note)
                        }
                        NoteType.CHECKLIST -> {
                            // 🆕 v1.8.1: Locked: Click -> Options | Unlocked: kein Click -> Scroll frei
                            val checklistBoxModifier = if (isLocked) {
                                contentClickModifier
                            } else {
                                GlanceModifier.fillMaxSize()
                            }
                            Box(modifier = checklistBoxModifier) {
                                ChecklistFullView(
                                    note = note,
                                    isLocked = isLocked,
                                    glanceId = glanceId
                                )
                            }
                        }
                    }
                }

                // 🆕 v1.8.2: Text-Notizen scrollbar auch in WIDE_MED
                WidgetSizeClass.WIDE_MED -> {
                    when (note.noteType) {
                        NoteType.TEXT -> Box(modifier = contentClickModifier) {
                            TextNoteFullView(note)
                        }
                        NoteType.CHECKLIST -> Box(modifier = contentClickModifier) {
                            ChecklistCompactView(
                                note = note,
                                maxItems = 3,
                                isLocked = isLocked,
                                glanceId = glanceId
                            )
                        }
                    }
                }

                // 🆕 v1.8.1 (IMPL_09): Scrollbare Größe (150dp+ Höhe)
                WidgetSizeClass.WIDE_SCROLL,
                WidgetSizeClass.WIDE_TALL -> {
                    when (note.noteType) {
                        NoteType.TEXT -> Box(modifier = contentClickModifier) {
                            TextNoteFullView(note)
                        }
                        NoteType.CHECKLIST -> {
                            // 🆕 v1.8.1: Locked: Click -> Options | Unlocked: kein Click -> Scroll frei
                            val checklistBoxModifier = if (isLocked) {
                                contentClickModifier
                            } else {
                                GlanceModifier.fillMaxSize()
                            }
                            Box(modifier = checklistBoxModifier) {
                                ChecklistFullView(
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
    }
}

/**
 * Optionsleiste — Lock/Unlock + Refresh + Open in App
 * 🆕 v1.9.0 (F02): Kein eigener Hintergrund, nahtlos in Widget-Surface integriert.
 */
@Composable
private fun OptionsBar(isLocked: Boolean, noteId: String, glanceId: GlanceId) {
    val context = LocalContext.current

    Row(
        // 🆕 v1.9.0 (F02): Kein eigener Hintergrund — nahtlos im Widget integriert
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun TextNoteFullView(note: Note) {
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
    ) {
        // 🆕 v1.8.0 Fix: Split text into individual lines instead of paragraphs.
        // This ensures each line is a separate LazyColumn item that can scroll properly.
        // Empty lines are preserved as small spacers for visual paragraph separation.
        val lines = note.content.split("\n").let { allLines ->
            if (allLines.size > WIDGET_MAX_TEXT_LINES) {
                Logger.d(TAG, "Truncating text note from ${allLines.size} to $WIDGET_MAX_TEXT_LINES lines")
                allLines.take(WIDGET_MAX_TEXT_LINES)
            } else {
                allLines
            }
        }
        items(lines.size) { index ->
            val line = lines[index]
            if (line.isBlank()) {
                // Preserve empty lines as spacing (paragraph separator)
                Spacer(modifier = GlanceModifier.height(8.dp))
            } else {
                Text(
                    text = line,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp
                    ),
                    maxLines = 5, // Allow wrapping but prevent single-item overflow
                    modifier = GlanceModifier.padding(bottom = 4.dp) // 🆕 v1.8.2 (IMPL_12): 2dp → 4dp
                )
            }
        }
    }
}

// ── Checklist Views ──

/**
 * Kompakte Checklist-Ansicht für MEDIUM-Größen.
 * Zeigt maxItems interaktive Checkboxen + Zusammenfassung.
 */
@Composable
private fun ChecklistCompactView(note: Note, maxItems: Int, isLocked: Boolean, glanceId: GlanceId) {
    // 🆕 v1.8.1 (IMPL_04): Sortierung aus Editor übernehmen
    val items = note.checklistItems?.let { rawItems ->
        sortChecklistItemsForPreview(rawItems, note.checklistSortOption)
    } ?: return

    // 🆕 v1.8.1 (IMPL_04): Separator-Logik
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
    val sortOption = try {
        note.checklistSortOption?.let { ChecklistSortOption.valueOf(it) }
    } catch (e: IllegalArgumentException) {
        Logger.d(TAG, "Unknown checklistSortOption '${note.checklistSortOption}': ${e.message}")
        null
    }
        ?: ChecklistSortOption.MANUAL

    val showSeparator = (
        sortOption == ChecklistSortOption.MANUAL ||
            sortOption == ChecklistSortOption.UNCHECKED_FIRST
        ) &&
        uncheckedCount > 0 &&
        checkedCount > 0

    val visibleItems = items.take(maxItems)
    val remainingCount = items.size - visibleItems.size

    // 🆕 v1.8.2 (IMPL_08): Konsistente Randabstände
    Column(modifier = GlanceModifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
        var separatorShown = false
        visibleItems.forEach { item ->
            // 🆕 v1.8.1: Separator vor dem ersten checked Item anzeigen
            if (showSeparator && !separatorShown && item.isChecked) {
                WidgetCheckedItemsSeparator(checkedCount = checkedCount)
                separatorShown = true
            }

            if (isLocked) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), // 🆕 v1.8.2 (IMPL_08): 2dp → 4dp
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "☑️" else "☐", // 🆕 v1.8.1 (IMPL_06)
                        style = TextStyle(fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    // 🆕 v1.9.0 (F03): Strikethrough for completed items
                    Text(
                        text = item.text,
                        style = TextStyle(
                            color = if (item.isChecked) {
                                GlanceTheme.colors.outline
                            } else {
                                GlanceTheme.colors.onSurface
                            },
                            fontSize = 13.sp,
                            textDecoration = if (item.isChecked) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
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
                    // 🆕 v1.9.0 (F03): Strikethrough + dimmed color for completed items
                    style = TextStyle(
                        color = if (item.isChecked) {
                            GlanceTheme.colors.outline
                        } else {
                            GlanceTheme.colors.onSurface
                        },
                        fontSize = 13.sp,
                        textDecoration = if (item.isChecked) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp) // 🆕 v1.8.2 (IMPL_08): 1dp → 3dp
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
private fun ChecklistFullView(note: Note, isLocked: Boolean, glanceId: GlanceId) {
    // 🆕 v1.8.1 (IMPL_04): Sortierung aus Editor übernehmen
    val items = note.checklistItems?.let { rawItems ->
        val sorted = sortChecklistItemsForPreview(rawItems, note.checklistSortOption)
        if (sorted.size > WIDGET_MAX_CHECKLIST_ITEMS) {
            Logger.d(TAG, "Truncating checklist from ${sorted.size} to $WIDGET_MAX_CHECKLIST_ITEMS items")
            sorted.take(WIDGET_MAX_CHECKLIST_ITEMS)
        } else {
            sorted
        }
    } ?: return

    // 🆕 v1.8.1 (IMPL_04): Separator-Logik
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
    val sortOption = try {
        note.checklistSortOption?.let { ChecklistSortOption.valueOf(it) }
    } catch (e: IllegalArgumentException) {
        Logger.d(TAG, "Unknown checklistSortOption '${note.checklistSortOption}': ${e.message}")
        null
    }
        ?: ChecklistSortOption.MANUAL

    val showSeparator = (
        sortOption == ChecklistSortOption.MANUAL ||
            sortOption == ChecklistSortOption.UNCHECKED_FIRST
        ) &&
        uncheckedCount > 0 &&
        checkedCount > 0

    // 🆕 v1.8.1: Berechne die Gesamtanzahl der Elemente inklusive Separator
    val totalItems = items.size + if (showSeparator) 1 else 0

    // 🆕 v1.8.2 (IMPL_08): Konsistente Randabstände
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
    ) {
        items(totalItems) { index ->
            // 🆕 v1.8.1: Separator an Position uncheckedCount einfügen
            if (showSeparator && index == uncheckedCount) {
                WidgetCheckedItemsSeparator(checkedCount = checkedCount)
                return@items
            }

            // Tatsächlichen Item-Index berechnen (nach Separator um 1 verschoben)
            val itemIndex = if (showSeparator && index > uncheckedCount) index - 1 else index
            val item = items.getOrNull(itemIndex) ?: return@items

            if (isLocked) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), // 🆕 v1.8.2 (IMPL_12): 2dp → 4dp
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isChecked) "☑️" else "☐", // 🆕 v1.8.1 (IMPL_06)
                        style = TextStyle(fontSize = 16.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    // 🆕 v1.9.0 (F03): Strikethrough + dimmed color for completed items
                    Text(
                        text = item.text,
                        style = TextStyle(
                            color = if (item.isChecked) {
                                GlanceTheme.colors.outline
                            } else {
                                GlanceTheme.colors.onSurface
                            },
                            fontSize = 14.sp,
                            textDecoration = if (item.isChecked) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
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
                    // 🆕 v1.9.0 (F03): Strikethrough + dimmed color for completed items
                    style = TextStyle(
                        color = if (item.isChecked) {
                            GlanceTheme.colors.outline
                        } else {
                            GlanceTheme.colors.onSurface
                        },
                        fontSize = 14.sp,
                        textDecoration = if (item.isChecked) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        }
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp) // 🆕 v1.8.2 (IMPL_12): 1dp → 3dp
                )
            }
        }
    }
}

// ── Empty State ──

@Composable
private fun EmptyWidgetContent(bgOpacity: Float) {
    // 🆕 v1.9.0 (F01): Translucenter Hintergrund mit Monet-Tint bei beliebiger Opacity
    val bgModifier = resolveWidgetBackgroundModifier(bgOpacity)
    val context = LocalContext.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .then(bgModifier)
            .padding(16.dp)
            .clickable(
                onClick = actionRunCallback<OpenConfigAction>()
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_note_not_found),
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.widget_tap_to_reconfigure),
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = 12.sp
                )
            )
        }
    }
}
