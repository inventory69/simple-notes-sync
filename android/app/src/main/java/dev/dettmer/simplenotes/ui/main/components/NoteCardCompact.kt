package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.NOTE_PREVIEW_CHAR_LIMIT
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.utils.toReadableTime
import dev.dettmer.simplenotes.utils.truncate

/** Größe des Sync-Icons, wenn es (ohne Zeitstempel) inline ans Ende des Vorschautexts rutscht. */
private val CORNER_SYNC_ICON_SIZE = 14.dp

/**
 * 🎨 v1.7.0: Compact Note Card for Grid Layout
 *
 * COMPACT DESIGN für kleine Notizen:
 * - Reduzierter Padding (12dp statt 16dp)
 * - Kleinere Icons (24dp statt 32dp)
 * - Kompakte Typography (titleSmall)
 * - Max 3 Zeilen Preview
 * - Optimiert für Grid-Ansicht
 */
@Composable
fun NoteCardCompact(
    note: Note,
    showSyncStatus: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    showTimestamp: Boolean = true,
    showTypeIcon: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    // 🆕 Issue #100: Ohne Zeitstempel entfällt die Bottom-Row — das Sync-Icon rutscht stattdessen
    // inline ans Ende des Vorschautexts (siehe NoteCardCompactPreviewContent/-IconLeadingPreview).
    val showCornerSyncIcon = !showTimestamp && showSyncStatus

    // v2.5.0: Resolve note colour, fall back to theme default
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val noteContainerColor = NoteColorPalette.resolveContainer(note.color, isDark)
        .takeOrElse { MaterialTheme.colorScheme.surfaceContainerHigh }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .pointerInput(note.id, isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                noteContainerColor
            }
        )
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Title - COMPACT Typography — 🆕 v2.11.0: bei leerem Titel weglassen;
                // die erste Preview-Zeile rückt dann in den Titel-Slot nach, der Rest
                // erscheint darunter über die volle Kartenbreite (wie beim Titel-Fall)
                if (note.title.isNotBlank()) {
                    // Header row - COMPACT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showTypeIcon) {
                            NoteCardCompactTypeIcon(note = note)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    NoteCardCompactPreviewContent(note = note, showSyncIcon = showCornerSyncIcon)
                } else {
                    NoteCardCompactIconLeadingPreview(
                        note = note,
                        showTypeIcon = showTypeIcon,
                        showSyncIcon = showCornerSyncIcon
                    )
                }

                // 🆕 Issue #100: Ohne Zeitstempel entfällt die Bottom-Row komplett — das Sync-Icon
                // hängt stattdessen inline am Ende der letzten Vorschauzeile (siehe oben).
                if (showTimestamp) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom row - KOMPAKT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timestamp - SMALLER
                        Text(
                            text = note.updatedAt.toReadableTime(context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )

                        // Sync Status - KOMPAKT
                        if (showSyncStatus) {
                            Spacer(modifier = Modifier.width(4.dp))
                            NoteCardCompactSyncIcon(note = note, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Selection indicator checkbox (top-right)
            androidx.compose.animation.AnimatedVisibility(
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
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
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
    }
}

/** Sync-Status-Icon, gemeinsam genutzt von der Bottom-Row und dem TrailingIconText ohne Zeitstempel. */
@Composable
private fun NoteCardCompactSyncIcon(note: Note, modifier: Modifier = Modifier) {
    Icon(
        imageVector = syncStatusIcon(note.syncStatus),
        contentDescription = null,
        tint = syncStatusTint(note.syncStatus),
        modifier = modifier
    )
}

/** Typ-Icon, gemeinsam genutzt vom Titel- und vom Icon-Leading-Preview-Fall. */
@Composable
private fun NoteCardCompactTypeIcon(note: Note) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (note.noteType == NoteType.TEXT) {
                Icons.Outlined.Description
            } else {
                Icons.AutoMirrored.Outlined.List
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun NoteCardCompactPreviewContent(note: Note, showSyncIcon: Boolean, modifier: Modifier = Modifier) {
    TrailingIconText(
        text = AnnotatedString(notePreviewFullText(note)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        iconSize = CORNER_SYNC_ICON_SIZE,
        icon = if (showSyncIcon) {
            { NoteCardCompactSyncIcon(note = note, modifier = Modifier.size(CORNER_SYNC_ICON_SIZE)) }
        } else {
            null
        },
        modifier = modifier
    )
}

@Composable
private fun notePreviewFullText(note: Note): String {
    return when (note.noteType) {
        NoteType.TEXT -> note.content.truncate(NOTE_PREVIEW_CHAR_LIMIT)
        NoteType.CHECKLIST -> {
            note.checklistItems?.let { items ->
                remember(items, note.checklistSortOption) {
                    generateChecklistPreview(items, note.checklistSortOption)
                }
            }.orEmpty()
        }
    }
}

/**
 * Bei leerem Titel: erste Preview-Zeile im Titel-Slot neben dem Icon (einzeilig, wie zuvor
 * der Titel), der Rest der Preview folgt unverändert als eigener Block über die volle
 * Kartenbreite — dieselbe Row/Spacer-Mechanik wie im Titel-Fall, keine Sonderlogik nötig.
 */
@Composable
private fun NoteCardCompactIconLeadingPreview(
    note: Note,
    showTypeIcon: Boolean,
    showSyncIcon: Boolean
) {
    val fullPreviewText = notePreviewFullText(note)
    val firstLine = fullPreviewText.substringBefore('\n')
    val remainingLines = fullPreviewText.substringAfter('\n', "")
    val hasRemainingLines = remainingLines.isNotBlank()
    val cornerSyncIcon: (@Composable () -> Unit)? = if (showSyncIcon) {
        { NoteCardCompactSyncIcon(note = note, modifier = Modifier.size(CORNER_SYNC_ICON_SIZE)) }
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showTypeIcon) {
            NoteCardCompactTypeIcon(note = note)
            Spacer(modifier = Modifier.width(8.dp))
        }
        // Icon hängt an dieser Zeile nur, wenn sie auch die letzte sichtbare ist
        TrailingIconText(
            text = AnnotatedString(firstLine),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            iconSize = CORNER_SYNC_ICON_SIZE,
            icon = if (hasRemainingLines) null else cornerSyncIcon,
            modifier = Modifier.weight(1f)
        )
    }

    if (hasRemainingLines) {
        Spacer(modifier = Modifier.height(6.dp))
        TrailingIconText(
            text = AnnotatedString(remainingLines),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            iconSize = CORNER_SYNC_ICON_SIZE,
            icon = cornerSyncIcon
        )
    }
}
