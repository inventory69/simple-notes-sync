package dev.dettmer.simplenotes.ui.main

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import dev.dettmer.simplenotes.R

// Topbars der Hauptliste (Standard, Auswahl, Ordner) und der Ordner-Wechsel, aus MainScreen.kt ausgelagert.

/** 🆕 v2.7.0 (Folders): Dauer/Versatz der Ordner-Navigations-Animation (analog shared_axis_x, 200 ms). */
private const val FOLDER_ANIM_DURATION_MS = 200
private const val FOLDER_SLIDE_FRACTION = 0.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTopBar(
    customTitle: String, // 🆕 v1.9.0 (F05): Custom app title (empty = default)
    syncEnabled: Boolean,
    onSyncLegendClick: (() -> Unit)?, // 🆕 v1.8.0: null blendet den Hilfe-Button aus
    syncLegendBadgeCount: Int, // 🆕 v2.19.0: Export-Probleme des letzten Syncs (0 = keine)
    showFilterRow: Boolean, // 🆕 v1.9.0 (F11): Filter row toggle state
    onFilterToggle: () -> Unit, // 🆕 v1.9.0 (F11): Toggle filter row visibility
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                // 🆕 v1.9.0 (F05): Use custom title if set, otherwise default
                text = customTitle.ifBlank { stringResource(R.string.main_title) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            TopBarActions(
                syncEnabled = syncEnabled,
                onSyncLegendClick = onSyncLegendClick,
                syncLegendBadgeCount = syncLegendBadgeCount,
                showFilterRow = showFilterRow,
                onFilterToggle = onFilterToggle,
                onSyncClick = onSyncClick,
                onSettingsClick = onSettingsClick
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 🆕 v2.7.0 (Folders): Action-Icons, geteilt von MainTopBar und FolderTopBar. Läuft im actions-RowScope. */
@Composable
private fun TopBarActions(
    syncEnabled: Boolean,
    onSyncLegendClick: (() -> Unit)?,
    syncLegendBadgeCount: Int,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    IconButton(onClick = onFilterToggle) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.toggle_filter_row),
            tint = if (showFilterRow) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
    if (onSyncLegendClick != null) {
        IconButton(onClick = onSyncLegendClick) {
            // 🆕 v2.19.0: Zähler-Badge + getöntes Icon, solange der letzte Sync Export-Probleme hatte
            val hasProblems = syncLegendBadgeCount > 0
            BadgedBox(
                badge = {
                    if (hasProblems) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ) { Text(syncLegendBadgeCount.toString()) }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.sync_legend_button),
                    tint = if (hasProblems) MaterialTheme.colorScheme.tertiary else LocalContentColor.current
                )
            }
        }
    }
    IconButton(onClick = onSyncClick, enabled = syncEnabled) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.action_sync))
    }
    IconButton(onClick = onSettingsClick) {
        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
    }
}

/**
 * Selection mode TopBar - shows selected count and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // 🆕 v2.8.0: onToggleLocalOnly erhöht Count weiter
@Composable
internal fun SelectionTopBar(
    selectedNoteCount: Int,
    selectedFolderCount: Int, // 🆕 v2.7.0 (Folders)
    totalCount: Int,
    allSelectedPinned: Boolean,
    isSelectedFolderLocalOnly: Boolean = false, // 🆕 v2.8.0 (Local-Only Folders)
    isArchiveView: Boolean = false, // 🆕 v2.11.0 (Archive)
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onTogglePinSelected: () -> Unit,
    onToggleArchiveSelected: () -> Unit = {}, // 🆕 v2.11.0 (Archive)
    onColorClick: () -> Unit,
    onMoveClick: () -> Unit = {},
    onRename: () -> Unit = {}, // 🆕 v2.7.0 (Folders)
    onToggleLocalOnly: () -> Unit = {}, // 🆕 v2.8.0 (Local-Only Folders)
    onDeleteSelected: () -> Unit
) {
    val selectedCount = selectedNoteCount + selectedFolderCount
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close_selection)
                )
            }
        },
        title = {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1
            )
        },
        actions = {
            SelectionActions(
                selectedNoteCount = selectedNoteCount,
                selectedFolderCount = selectedFolderCount,
                totalCount = totalCount,
                allSelectedPinned = allSelectedPinned,
                isSelectedFolderLocalOnly = isSelectedFolderLocalOnly,
                isArchiveView = isArchiveView, // 🆕 v2.11.0 (Archive)
                onSelectAll = onSelectAll,
                onTogglePin = onTogglePinSelected,
                onToggleArchive = onToggleArchiveSelected, // 🆕 v2.11.0 (Archive)
                onColorClick = onColorClick,
                onMoveClick = onMoveClick,
                onRename = onRename,
                onToggleLocalOnly = onToggleLocalOnly,
                onDeleteSelected = onDeleteSelected
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderTopBar(
    folderName: String,
    onBack: () -> Unit,
    syncEnabled: Boolean,
    onSyncLegendClick: (() -> Unit)?,
    syncLegendBadgeCount: Int,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        title = {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            TopBarActions(
                syncEnabled = syncEnabled,
                onSyncLegendClick = onSyncLegendClick,
                syncLegendBadgeCount = syncLegendBadgeCount,
                showFilterRow = showFilterRow,
                onFilterToggle = onFilterToggle,
                onSyncClick = onSyncClick,
                onSettingsClick = onSettingsClick
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 🆕 v2.7.0 (Folders): Shared-Axis-X Transition zwischen Root und Ordner (wie Notiz-Öffnen-Animation). */
internal fun folderNavTransition(forward: Boolean): ContentTransform {
    val slide: FiniteAnimationSpec<IntOffset> = tween(FOLDER_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
    val fade: FiniteAnimationSpec<Float> = tween(FOLDER_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
    return if (forward) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { w -> (w * FOLDER_SLIDE_FRACTION).toInt() } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { w -> -(w * FOLDER_SLIDE_FRACTION).toInt() } + fadeOut(fade),
            sizeTransform = SizeTransform(clip = false)
        )
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { w -> -(w * FOLDER_SLIDE_FRACTION).toInt() } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { w -> (w * FOLDER_SLIDE_FRACTION).toInt() } + fadeOut(fade),
            sizeTransform = SizeTransform(clip = false)
        )
    }
}
