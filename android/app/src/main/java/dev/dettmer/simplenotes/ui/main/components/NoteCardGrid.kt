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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.noteCardMarkdownPreview
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteSize
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.getSize
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength
import dev.dettmer.simplenotes.utils.toReadableTime

/** Titel darf bis zu diesem Anteil seiner Basisgröße schrumpfen, bevor Ellipsis greift. */
private const val TITLE_AUTOSIZE_MIN_FRACTION = 0.8f

/** Größe des Sync-Icons, wenn es (ohne Zeitstempel) inline ans Ende des Vorschautexts rutscht. */
private val CORNER_SYNC_ICON_SIZE = 14.dp

/**
 * 🎨 v1.7.0: Unified Note Card for Grid Layout
 *
 * Einheitliche Card für ALLE Notizen im Grid:
 * - Dynamische maxLines basierend auf NoteSize
 * - LARGE notes: 6 Zeilen Preview
 * - SMALL notes: 3 Zeilen Preview
 * - Kein externes Padding - Grid steuert Abstände
 * - Optimiert für Pinterest-style dynamisches Layout
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress(
    "LongParameterList",
    "CyclomaticComplexMethod",
    "LongMethod"
) // 🆕 Issue #100: zwei weitere Display-Toggles neben bestehendem State
@Composable
fun NoteCardGrid(
    note: Note,
    showSyncStatus: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    timestampTicker: Long = 0L,
    previewLength: NotePreviewLength = NotePreviewLength.STANDARD,
    showTimestamp: Boolean = true,
    showTypeIcon: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    // 🚀 Performance: Cache noteSize - nur bei note-Änderung neu berechnen
    val noteSize = remember(note.id, note.content, note.checklistItems) { note.getSize() }

    // ⏱️ Reading timestampTicker triggers recomposition only for visible cards
    @Suppress("UNUSED_VARIABLE")
    val ticker = timestampTicker

    // Dynamische maxLines basierend auf Größe
    val previewMaxLines = if (noteSize == NoteSize.LARGE) previewLength.gridLargeLines else previewLength.gridSmallLines

    // 🆕 Issue #100: Ohne Zeitstempel entfällt die Footer-Row — das Sync-Icon rutscht stattdessen
    // inline ans Ende des Vorschautexts (siehe NoteCardGridPreviewContent/-IconLeadingPreview).
    val showCornerSyncIcon = !showTimestamp && showSyncStatus

    // v2.5.0: Resolve note colour, fall back to theme default
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val noteContainerColor = NoteColorPalette.resolveContainer(note.color, isDark)
        .takeOrElse { MaterialTheme.colorScheme.surfaceContainerHigh }
    val hasNoteColor = !note.color.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Kein externes Padding - Grid steuert alles
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
            containerColor = if (isSelected && !hasNoteColor) {
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
                    .padding(12.dp) // Einheitliches internes Padding
            ) {
                // Title — 🆕 v2.11.0: bei leerem Titel weglassen (kein "Untitled"-Platzhalter);
                // die erste Preview-Zeile rückt dann in den Titel-Slot nach, der Rest
                // erscheint darunter über die volle Kartenbreite (wie beim Titel-Fall)
                if (note.title.isNotBlank()) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showTypeIcon) {
                            NoteCardGridTypeIcon(note = note, isPinned = note.isPinned == true)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        val titleFontSize = MaterialTheme.typography.titleSmall.fontSize
                        val titleAutoSize = remember(titleFontSize) {
                            TextAutoSize.StepBased(
                                minFontSize = titleFontSize * TITLE_AUTOSIZE_MIN_FRACTION,
                                maxFontSize = titleFontSize
                            )
                        }
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                hyphens = Hyphens.Auto,
                                lineBreak = LineBreak.Paragraph
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = titleAutoSize,
                            modifier = Modifier.weight(1f)
                        )
                        // 🆕 Discussion #110: ohne Vorschauzeilen hat das Sync-Icon keinen Text
                        // mehr zum Anhängen — es rückt neben den Titel statt zu verschwinden.
                        if (showCornerSyncIcon && previewMaxLines == 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            NoteCardGridSyncIcon(note = note, modifier = Modifier.size(CORNER_SYNC_ICON_SIZE))
                        }
                    }

                    // ponytail: 0 Zeilen = NotePreviewLength.TITLE_ONLY
                    if (previewMaxLines > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        NoteCardGridPreviewContent(
                            note = note,
                            maxLines = previewMaxLines,
                            itemMaxLines = previewLength.itemMaxLines,
                            showSyncIcon = showCornerSyncIcon
                        )
                    }
                } else {
                    NoteCardGridIconLeadingPreview(
                        note = note,
                        isPinned = note.isPinned == true,
                        maxLines = previewMaxLines,
                        showTypeIcon = showTypeIcon,
                        showSyncIcon = showCornerSyncIcon
                    )
                }

                // 🆕 Issue #100: Ohne Zeitstempel entfällt die Footer-Row komplett — das Sync-Icon
                // hängt stattdessen inline am Ende der letzten Vorschauzeile (siehe oben).
                if (showTimestamp) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = note.updatedAt.toReadableTime(context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )

                        if (showSyncStatus) {
                            Spacer(modifier = Modifier.width(4.dp))
                            NoteCardGridSyncIcon(note = note, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Selection overlay on colored notes — keeps note color visible while
            // signaling selection via a subtle semi-transparent tint
            if (isSelected && hasNoteColor) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                )
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

/** Sync-Status-Icon, gemeinsam genutzt von der Footer-Row und dem TrailingIconText ohne Zeitstempel. */
@Composable
private fun NoteCardGridSyncIcon(note: Note, modifier: Modifier = Modifier) {
    Icon(
        imageVector = syncStatusIcon(note.syncStatus),
        contentDescription = null,
        tint = syncStatusTint(note.syncStatus),
        modifier = modifier
    )
}

/** Typ-Icon (+ optionaler Pin), gemeinsam genutzt vom Titel- und vom Icon-Leading-Preview-Fall. */
@Composable
private fun NoteCardGridTypeIcon(note: Note, isPinned: Boolean) {
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

    if (isPinned) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(12.dp)
        )
    }
}

@Composable
private fun NoteCardGridPreviewContent(
    note: Note,
    maxLines: Int,
    itemMaxLines: Int,
    showSyncIcon: Boolean,
    modifier: Modifier = Modifier
) {
    // 🔧 Gemeinsamer, hyphenierter Body-Style für TEXT- und Checklist-Preview — vermeidet, dass
    // eine der beiden Note-Typen die Hyphens.Auto/LineBreak.Paragraph-Behandlung verpasst.
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
    val cornerSyncIcon: (@Composable () -> Unit)? = if (showSyncIcon) {
        { NoteCardGridSyncIcon(note = note, modifier = Modifier.size(CORNER_SYNC_ICON_SIZE)) }
    } else {
        null
    }
    if (note.noteType == NoteType.TEXT) {
        TrailingIconText(
            text = noteCardMarkdownPreview(note.content),
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            iconSize = CORNER_SYNC_ICON_SIZE,
            icon = cornerSyncIcon,
            modifier = modifier
        )
    } else {
        ChecklistItemsPreview(
            items = note.checklistItems.orEmpty(),
            sortOptionName = note.checklistSortOption,
            maxItems = maxLines - 1,
            style = bodyStyle,
            itemMaxLines = itemMaxLines,
            modifier = modifier,
            trailingIcon = cornerSyncIcon,
            trailingIconSize = CORNER_SYNC_ICON_SIZE
        )
    }
}

/**
 * Preview-Text als AnnotatedString, unabhängig vom NoteType — Grundlage, um die erste Zeile
 * in den Titel-Slot zu heben und den Rest separat darunter anzuzeigen. AnnotatedString statt
 * String, damit die Markdown-Formatierung aus noteCardMarkdownPreview beim Splitten erhalten
 * bleibt (subSequence bewahrt Spans, ein reiner String-Split würde sie verlieren).
 */
@Composable
private fun notePreviewFullText(note: Note): AnnotatedString {
    return if (note.noteType == NoteType.TEXT) {
        noteCardMarkdownPreview(note.content)
    } else {
        val items = note.checklistItems.orEmpty()
        remember(items, note.checklistSortOption) {
            AnnotatedString(generateChecklistPreview(items, note.checklistSortOption))
        }
    }
}

/** Trennt an der ersten Zeile, Formatierung bleibt dank subSequence erhalten. */
private fun AnnotatedString.splitFirstLine(): Pair<AnnotatedString, AnnotatedString> {
    val newlineIndex = text.indexOf('\n')
    return if (newlineIndex == -1) {
        this to AnnotatedString("")
    } else {
        subSequence(0, newlineIndex) to subSequence(newlineIndex + 1, length)
    }
}

/**
 * Bei leerem Titel: erste Preview-Zeile im Titel-Slot neben dem Icon (einzeilig, wie zuvor
 * der Titel), der Rest der Preview folgt unverändert als eigener Block über die volle
 * Kartenbreite — dieselbe Row/Spacer-Mechanik wie im Titel-Fall, keine Sonderlogik nötig.
 */
@Composable
private fun NoteCardGridIconLeadingPreview(
    note: Note,
    isPinned: Boolean,
    maxLines: Int,
    showTypeIcon: Boolean,
    showSyncIcon: Boolean
) {
    val (firstLine, remainingLines) = notePreviewFullText(note).splitFirstLine()
    // ponytail: maxLines <= 1 = TITLE_ONLY (bzw. kein Platz mehr) — nur die Titel-Slot-Zeile bleibt
    val hasRemainingLines = remainingLines.isNotBlank() && maxLines > 1
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
    val cornerSyncIcon: (@Composable () -> Unit)? = if (showSyncIcon) {
        { NoteCardGridSyncIcon(note = note, modifier = Modifier.size(CORNER_SYNC_ICON_SIZE)) }
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showTypeIcon) {
            NoteCardGridTypeIcon(note = note, isPinned = isPinned)
            Spacer(modifier = Modifier.width(8.dp))
        }
        // Icon hängt an dieser Zeile nur, wenn sie auch die letzte sichtbare ist
        TrailingIconText(
            text = firstLine,
            style = bodyStyle,
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
            text = remainingLines,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines - 1,
            iconSize = CORNER_SYNC_ICON_SIZE,
            icon = cornerSyncIcon
        )
    }
}
