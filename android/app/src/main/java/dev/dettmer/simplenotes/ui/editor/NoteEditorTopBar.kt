package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.utils.Constants

/** Menüeinträge, die nur Zustand des Screens ändern (Dialoge, Picker, Titel). */
internal enum class EditorMenuAction { PICK_IMAGE, SORT, ADD_TITLE, COLOR, CONVERT }

/** Was das Überlaufmenü vom Screen wissen muss, um Einträge zu zeigen oder zu sperren. */
internal data class EditorMenuFlags(
    val compactToolbar: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isPreviewMode: Boolean,
    val isAttachingImage: Boolean,
    val newDesign: Boolean,
    val showSheetTitle: Boolean
)

/**
 * 🆕 v1.10.0-Papa: Überlaufmenü des Editors (Kalender, Teilen, PDF, Archiv, Löschen …).
 * Aus NoteEditorScreen ausgelagert. Jeder Eintrag schließt das Menü, außer Rückgängig und
 * Wiederholen (man will oft mehrfach).
 */
@Composable
internal fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: NoteEditorViewModel,
    uiState: NoteEditorUiState,
    flags: EditorMenuFlags,
    onLocalAction: (EditorMenuAction) -> Unit
) {
    fun close(then: () -> Unit) = {
        onDismiss()
        then()
    }

    fun local(action: EditorMenuAction) = close { onLocalAction(action) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large, // 🆕 v1.10.0-P2: Rounded corners
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // 🆕 v1.10.0-P2
        shadowElevation = 6.dp, // 🆕 v1.10.0-P2
        tonalElevation = 2.dp // 🆕 v1.10.0-P2
    ) {
        EditItems(viewModel, uiState, flags, ::local)
        MenuDivider()
        MenuItem(R.string.share_to_calendar, Icons.Outlined.CalendarMonth, onClick = close { viewModel.openInCalendar() })
        MenuItem(R.string.action_copy_note_text, Icons.Outlined.ContentCopy, onClick = close { viewModel.copyNoteText() })
        MenuItem(R.string.share_as_text, Icons.Outlined.Share, onClick = close { viewModel.shareAsText() })
        MenuItem(R.string.share_as_pdf, Icons.Outlined.PictureAsPdf, onClick = close { viewModel.shareAsPdf() })
        MenuDivider()
        // 🆕 v2.11.0 (Archive): speichert und schließt den Editor (via Event/Activity)
        MenuItem(
            if (uiState.isArchived) R.string.action_unarchive else R.string.action_archive,
            if (uiState.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
            onClick = close { viewModel.toggleArchive() }
        )
        // 🆕 v2.9.0 (Trash): direkt in den Papierkorb, kein Bestätigungs-Dialog.
        MenuItem(
            R.string.delete,
            Icons.Default.Delete,
            color = MaterialTheme.colorScheme.error,
            onClick = close { viewModel.deleteNote() }
        )
    }
}

/** Bearbeiten-Teil des Menüs: Rückgängig/Wiederholen, Bild, Sortieren, Titel, Farbe, Umwandeln. */
@Composable
private fun EditItems(
    viewModel: NoteEditorViewModel,
    uiState: NoteEditorUiState,
    flags: EditorMenuFlags,
    local: (EditorMenuAction) -> () -> Unit
) {
    val isChecklist = uiState.noteType == NoteType.CHECKLIST
    // v2.0.1: Undo/Redo im Menü nur bei schmaler Leiste (Issue #48)
    if (flags.compactToolbar) {
        MenuItem(R.string.editor_undo, Icons.AutoMirrored.Filled.Undo, enabled = flags.canUndo) { viewModel.undo() }
        MenuItem(R.string.editor_redo, Icons.AutoMirrored.Filled.Redo, enabled = flags.canRedo) { viewModel.redo() }
        MenuDivider()
    }
    // Formatierleiste aus (E11): Bild einfügen bleibt hier erreichbar, nur beim Bearbeiten,
    // weil die Brücke Feld → ViewModel im Lesemodus nicht läuft.
    if (uiState.noteType == NoteType.TEXT && !flags.isPreviewMode && !uiState.formattingBarEnabled) {
        MenuItem(
            R.string.md_toolbar_image,
            Icons.Filled.AddPhotoAlternate,
            enabled = !flags.isAttachingImage,
            onClick = local(EditorMenuAction.PICK_IMAGE)
        )
    }
    // UI-Beta: Sortieren wandert aus der Zeile unter der Liste hierher.
    if (flags.newDesign && isChecklist && !flags.isPreviewMode) {
        MenuItem(R.string.sort_checklist, Icons.AutoMirrored.Outlined.Sort, onClick = local(EditorMenuAction.SORT))
    }
    if (flags.newDesign && !flags.showSheetTitle) {
        MenuItem(R.string.editor_add_title, Icons.Filled.Title, onClick = local(EditorMenuAction.ADD_TITLE))
    }
    MenuItem(R.string.action_set_note_color, Icons.Outlined.Palette, onClick = local(EditorMenuAction.COLOR))
    MenuItem(
        if (isChecklist) R.string.action_convert_to_note else R.string.action_convert_to_checklist,
        if (isChecklist) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.Checklist,
        onClick = local(EditorMenuAction.CONVERT)
    )
}

@Composable
private fun MenuItem(
    textRes: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val iconTint = when {
        color != Color.Unspecified -> color
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    DropdownMenuItem(
        text = { Text(stringResource(textRes), color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 🆕 v1.9.0: TopAppBar title with optional autosave confirmation indicator. */
@Composable
internal fun NoteEditorToolbarTitle(toolbarTitle: ToolbarTitle, autosaveIndicatorVisible: Boolean, compact: Boolean = false) {
    Column {
        Text(
            text = if (compact) {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE, ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.toolbar_title_new)
                    ToolbarTitle.EDIT_NOTE, ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.toolbar_title_edit)
                }
            } else {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE -> stringResource(R.string.new_note)
                    ToolbarTitle.EDIT_NOTE -> stringResource(R.string.edit_note)
                    ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.new_checklist)
                    ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.edit_checklist)
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(
            visible = autosaveIndicatorVisible,
            enter = fadeIn(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS)),
            exit = fadeOut(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS))
        ) {
            Text(
                text = stringResource(R.string.autosave_indicator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
