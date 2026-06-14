package dev.dettmer.simplenotes.widget

import android.content.ComponentName
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.stripInlineFormatting
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.ui.main.ComposeMainActivity
import dev.dettmer.simplenotes.ui.main.components.sortChecklistItemsForPreview
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette

private const val NOTE_CARD_CHECKLIST_MAX_ITEMS = 4

private const val BG_FALLBACK_DAY_COLOR = 0xFFF5F5F5L
private const val BG_FALLBACK_NIGHT_COLOR = 0xFF1C1B1FL

private const val CARD_FALLBACK_DAY_COLOR = 0xFFE1E2ECL
private const val CARD_FALLBACK_NIGHT_COLOR = 0xFF45464FL

@Composable
private fun resolveWidgetBackgroundModifier(bgOpacity: Float): GlanceModifier {
    if (bgOpacity >= 1.0f) {
        return GlanceModifier.background(GlanceTheme.colors.widgetBackground)
    }

    val context = LocalContext.current
    val dayColor: Color
    val nightColor: Color

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dayColor = Color(context.getColor(android.R.color.system_accent1_100)).copy(alpha = bgOpacity)
        nightColor = Color(context.getColor(android.R.color.system_neutral1_800)).copy(alpha = bgOpacity)
    } else {
        dayColor = Color(BG_FALLBACK_DAY_COLOR.toInt()).copy(alpha = bgOpacity)
        nightColor = Color(BG_FALLBACK_NIGHT_COLOR.toInt()).copy(alpha = bgOpacity)
    }

    return GlanceModifier.background(ColorProvider(day = dayColor, night = nightColor))
}

@Composable
fun NotesListWidgetContent(
    notes: List<Note>,
    folders: List<Folder> = emptyList(),
    folderNoteCounts: Map<String, Int> = emptyMap(),
    bgOpacity: Float = 1.0f,
    cardBgOpacity: Float = 1.0f,
    fabExpanded: Boolean = false,
    hasPinnedNotes: Boolean = false,
    hideHeader: Boolean = false,
    fontSizeScale: Float = 1.0f
) {
    val context = LocalContext.current
    val bgModifier = resolveWidgetBackgroundModifier(bgOpacity)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .then(bgModifier)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (!hideHeader) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Spacer(GlanceModifier.width(48.dp))
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.notes_list_widget_name),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = (16 * fontSizeScale).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.ic_settings),
                        contentDescription = context.getString(R.string.notes_list_widget_config_title),
                        backgroundColor = null,
                        contentColor = GlanceTheme.colors.onSurface,
                        onClick = actionRunCallback<OpenNotesListConfigAction>()
                    )
                }
            }

            if (notes.isEmpty() && folders.isEmpty()) {
                EmptyNotesState(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), fontSizeScale = fontSizeScale)
            } else {
                val pinned = notes.filter { it.isPinned == true }
                val others = notes.filter { it.isPinned != true }
                val showNotesHeader = others.isNotEmpty() && (pinned.isNotEmpty() || hasPinnedNotes)

                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = 8.dp)
                ) {
                    if (pinned.isNotEmpty()) {
                        item { SectionHeader(context.getString(R.string.notes_list_widget_section_pinned), fontSizeScale) }
                    }
                    items(pinned.size) { i -> NoteCard(note = pinned[i], bgOpacity = cardBgOpacity, fontSizeScale = fontSizeScale) }

                    if (folders.isNotEmpty()) {
                        item { SectionHeader(context.getString(R.string.notes_list_widget_section_folders), fontSizeScale) }
                    }
                    items(folders.size) { i ->
                        FolderCard(
                            folder = folders[i],
                            noteCount = folderNoteCounts[folders[i].name] ?: 0,
                            bgOpacity = cardBgOpacity,
                            fontSizeScale = fontSizeScale
                        )
                    }

                    if (showNotesHeader) {
                        item { SectionHeader(context.getString(R.string.notes_list_widget_section_others), fontSizeScale) }
                    }
                    items(others.size) { i -> NoteCard(note = others[i], bgOpacity = cardBgOpacity, fontSizeScale = fontSizeScale) }

                    item { Spacer(GlanceModifier.height(72.dp)) }
                }
            }
        }

        if (fabExpanded) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionRunCallback<ToggleFabExpandedAction>())
            ) {}
        }

        if (hideHeader) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_settings),
                    contentDescription = context.getString(R.string.notes_list_widget_config_title),
                    backgroundColor = null,
                    contentColor = GlanceTheme.colors.onSurface,
                    onClick = actionRunCallback<OpenNotesListConfigAction>()
                )
            }
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            CreateFab(expanded = fabExpanded)
        }
    }
}

@Composable
private fun SectionHeader(title: String, fontSizeScale: Float = 1.0f) {
    Text(
        text = title,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = (11 * fontSizeScale).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun NoteCard(note: Note, bgOpacity: Float, fontSizeScale: Float = 1.0f) {
    val context = LocalContext.current
    val slot = NoteColorPalette.fromHex(note.color)
    val cardBg = if (slot != null) {
        ColorProvider(
            day = slot.containerColor.copy(alpha = bgOpacity),
            night = slot.containerColorDark.copy(alpha = bgOpacity)
        )
    } else {
        ColorProvider(
            day = Color(CARD_FALLBACK_DAY_COLOR.toInt()).copy(alpha = bgOpacity),
            night = Color(CARD_FALLBACK_NIGHT_COLOR.toInt()).copy(alpha = bgOpacity)
        )
    }

    Box(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(cardBg)
                .clickable(
                    actionStartActivity(
                        ComponentName(context, ComposeNoteEditorActivity::class.java),
                        actionParametersOf(
                            ActionParameters.Key<String>(ComposeNoteEditorActivity.EXTRA_NOTE_ID) to note.id
                        )
                    )
                )
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(10.dp)) {
                NoteCardTitle(note, fontSizeScale)
                NoteCardBody(note, fontSizeScale)
            }
        }
    }
}

@Composable
private fun FolderCard(folder: Folder, noteCount: Int, bgOpacity: Float, fontSizeScale: Float = 1.0f) {
    val context = LocalContext.current
    val slot = NoteColorPalette.fromHex(folder.color)
    val folderBg = if (slot != null) {
        ColorProvider(
            day = slot.containerColor.copy(alpha = bgOpacity),
            night = slot.containerColorDark.copy(alpha = bgOpacity)
        )
    } else {
        ColorProvider(
            day = Color(CARD_FALLBACK_DAY_COLOR.toInt()).copy(alpha = bgOpacity),
            night = Color(CARD_FALLBACK_NIGHT_COLOR.toInt()).copy(alpha = bgOpacity)
        )
    }

    Box(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(folderBg)
                .clickable(
                    actionStartActivity(
                        ComponentName(context, ComposeMainActivity::class.java),
                        actionParametersOf(
                            ActionParameters.Key<String>(ComposeMainActivity.EXTRA_FOLDER) to folder.name
                        )
                    )
                )
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_folder),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface)
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = folder.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = (13 * fontSizeScale).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = "$noteCount",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = (12 * fontSizeScale).sp)
                )
            }
        }
    }
}

@Composable
private fun NoteTypeIcon(noteType: NoteType) {
    Image(
        provider = ImageProvider(
            if (noteType == NoteType.TEXT) R.drawable.ic_type_text else R.drawable.ic_type_checklist
        ),
        contentDescription = null,
        modifier = GlanceModifier.size(16.dp).padding(end = 4.dp),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface)
    )
}

@Composable
private fun NoteCardTitle(note: Note, fontSizeScale: Float = 1.0f) {
    val context = LocalContext.current
    val hasTitle = note.title.isNotBlank()
    val isBlankNote = note.title.isBlank() &&
        when (note.noteType) {
            NoteType.TEXT -> note.content.isBlank()
            NoteType.CHECKLIST -> note.checklistItems.isNullOrEmpty()
        }

    when {
        hasTitle -> Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            NoteTypeIcon(note.noteType)
            if (note.isPinned == true) {
                Image(
                    provider = ImageProvider(R.drawable.ic_pin),
                    contentDescription = null,
                    modifier = GlanceModifier.size(12.dp).padding(end = 4.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface)
                )
            }
            Text(
                text = note.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = (13 * fontSizeScale).sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
        }
        isBlankNote -> Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            NoteTypeIcon(note.noteType)
            Text(
                text = context.getString(R.string.notes_list_widget_untitled),
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = (13 * fontSizeScale).sp
                ),
                maxLines = 1
            )
        }
        else -> NoteTypeIcon(note.noteType)
    }
}

@Composable
private fun NoteCardBody(note: Note, fontSizeScale: Float = 1.0f) {
    when (note.noteType) {
        NoteType.TEXT -> {
            if (note.content.isNotBlank()) {
                WidgetInlineText(
                    text = note.content,
                    fontSize = 12f * fontSizeScale,
                    maxLines = 4
                )
            }
        }
        NoteType.CHECKLIST -> ChecklistCardPreview(note, fontSizeScale)
    }
}

@Composable
private fun ChecklistCardPreview(note: Note, fontSizeScale: Float = 1.0f) {
    val sorted = sortChecklistItemsForPreview(note.checklistItems.orEmpty(), note.checklistSortOption)
    val visibleItems = sorted.take(NOTE_CARD_CHECKLIST_MAX_ITEMS)
    val remaining = (sorted.size - visibleItems.size).coerceAtLeast(0)

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        visibleItems.forEach { item ->
            val prefix = if (item.isChecked) "☑️" else "☐"
            Text(
                text = "$prefix ${stripInlineFormatting(item.text)}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = (12 * fontSizeScale).sp
                ),
                maxLines = 1
            )
        }
        if (remaining > 0) {
            Text(
                text = "+$remaining more",
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = (12 * fontSizeScale).sp
                )
            )
        }
    }
}

@Composable
private fun CreateFab(expanded: Boolean) {
    val context = LocalContext.current
    if (expanded) {
        Column(
            horizontalAlignment = Alignment.Horizontal.End
        ) {
            FabSubAction(
                iconRes = R.drawable.ic_type_text,
                label = context.getString(R.string.fab_text_note),
                noteType = NoteType.TEXT
            )
            Spacer(GlanceModifier.height(8.dp))
            FabSubAction(
                iconRes = R.drawable.ic_type_checklist,
                label = context.getString(R.string.fab_checklist),
                noteType = NoteType.CHECKLIST
            )
            Spacer(GlanceModifier.height(8.dp))
            FabMainButton(
                iconRes = R.drawable.ic_fab_close,
                contentDescription = context.getString(R.string.fab_close)
            )
        }
    } else {
        FabMainButton(
            iconRes = R.drawable.ic_fab_add,
            contentDescription = context.getString(R.string.fab_new_note)
        )
    }
}

@Composable
private fun FabMainButton(iconRes: Int, contentDescription: String) {
    Box(
        modifier = GlanceModifier
            .size(48.dp)
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(4.dp)
            .clickable(actionRunCallback<ToggleFabExpandedAction>()),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
        )
    }
}

@Composable
private fun FabSubAction(iconRes: Int, label: String, noteType: NoteType) {
    Row(
        modifier = GlanceModifier
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(
                actionRunCallback<CreateNoteAndCollapseAction>(
                    actionParametersOf(
                        CreateNoteAndCollapseAction.KEY_NOTE_TYPE to noteType.name
                    )
                )
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun EmptyNotesState(modifier: GlanceModifier = GlanceModifier, fontSizeScale: Float = 1.0f) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_note),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.outline)
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = context.getString(R.string.notes_list_widget_empty),
            style = TextStyle(
                color = GlanceTheme.colors.outline,
                fontSize = (13 * fontSizeScale).sp
            )
        )
    }
}
