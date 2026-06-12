package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette

/** 🆕 v2.7.0 (Folders): Ordnerkarte in voller Breite (List-Ansicht). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCardList(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
    color: String? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isLocalOnly: Boolean = false, // 🆕 v2.8.0 (Local-Only Folders)
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = NoteColorPalette.resolveContainer(color, isDark)
        .takeOrElse { MaterialTheme.colorScheme.surfaceContainerHigh }
    val hasColor = !color.isNullOrBlank()
    val selectionBorder = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(selectionBorder)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && !hasColor) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                container
            }
        )
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = stringResource(R.string.cd_folder_card, name),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isLocalOnly) {
                    Icon(
                        imageVector = Icons.Outlined.SyncDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FolderSelectionOverlay(isSelected = isSelected, hasColor = hasColor, isSelectionMode = isSelectionMode)
        }
    }
}

/** 🆕 v2.7.0 (Folders): kompakte Ordner-Kachel (Grid-Ansicht, Single-Lane). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderCardGrid(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
    color: String? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isLocalOnly: Boolean = false, // 🆕 v2.8.0 (Local-Only Folders)
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = NoteColorPalette.resolveContainer(color, isDark)
        .takeOrElse { MaterialTheme.colorScheme.surfaceContainerHigh }
    val hasColor = !color.isNullOrBlank()
    val selectionBorder = if (isSelected) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(selectionBorder)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && !hasColor) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                container
            }
        )
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = stringResource(R.string.cd_folder_card, name),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (isLocalOnly) {
                        Icon(
                            imageVector = Icons.Outlined.SyncDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FolderSelectionOverlay(isSelected = isSelected, hasColor = hasColor, isSelectionMode = isSelectionMode)
        }
    }
}

/**
 * 🆕 v2.7.0 (Folders): Selektions-Overlay analog NoteCardGrid — dezenter Tint hält die Ordnerfarbe
 * sichtbar, Checkbox oben rechts zeigt den Auswahlzustand im Selection-Mode.
 */
@Composable
private fun BoxScope.FolderSelectionOverlay(isSelected: Boolean, hasColor: Boolean, isSelectionMode: Boolean) {
    if (isSelected && hasColor) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )
    }
    AnimatedVisibility(
        visible = isSelectionMode,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.selection_count, 1),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
