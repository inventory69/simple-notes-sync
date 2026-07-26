package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🆕 v2.9.0 (Trash): Papierkorb-Screen. Listet getrashte Notizen (neueste zuerst), erlaubt
 * Wiederherstellen und endgültiges Löschen (mit Bestätigung) sowie „Papierkorb leeren".
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("LongMethod")
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: TrashViewModel = viewModel()
) {
    val notes by viewModel.trashedNotes.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()

    var pendingPurge by remember { mutableStateOf<Note?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    val restoredMessage = stringResource(R.string.snackbar_note_restored)
    val emptiedMessage = stringResource(R.string.snackbar_trash_emptied)

    SettingsScaffold(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showRetentionDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.trash_retention_settings_cd)
                )
            }
            if (notes.isNotEmpty()) {
                IconButton(onClick = { showEmptyConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.trash_empty_action)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isReady) {
                // 🔧 Noch am Laden: Spinner statt des Hint-only-Zwischenzustands. Verhindert den
                // Flash beim Öffnen (leere Liste mit Hinweis → dann Items springen rein) und
                // überbrückt bei vielen tausend Notizen sichtbar den Kaltscan.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (notes.isEmpty()) {
                EmptyState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Dimensions.SpacingMedium)
                ) {
                    item {
                        Text(
                            text = if (retentionDays == 0) {
                                stringResource(R.string.trash_retention_hint_immediate)
                            } else {
                                stringResource(R.string.trash_retention_hint, retentionDays)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Dimensions.SpacingLarge,
                                vertical = Dimensions.SpacingMedium
                            )
                        )
                    }
                    items(notes, key = { it.id }) { note ->
                        TrashItem(
                            note = note,
                            retentionDays = retentionDays,
                            onRestore = {
                                viewModel.restore(note)
                                onShowSnackbar(restoredMessage)
                            },
                            onDeleteForever = { pendingPurge = note }
                        )
                    }
                }
            }
        }
    }

    pendingPurge?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            title = { Text(stringResource(R.string.trash_delete_forever_title)) },
            text = { Text(stringResource(R.string.trash_delete_forever_message, note.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purge(note)
                    pendingPurge = null
                }) {
                    Text(
                        text = stringResource(R.string.trash_delete_forever),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurge = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRetentionDialog) {
        RetentionDialog(
            selectedDays = retentionDays,
            onSelect = { viewModel.setRetentionDays(it) },
            onDismiss = { showRetentionDialog = false }
        )
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
                    Text(pluralStringResource(R.plurals.trash_empty_confirm_message, notes.size, notes.size))
                    Text(
                        text = stringResource(R.string.trash_empty_confirm_server_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyConfirm = false
                    onShowSnackbar(emptiedMessage)
                }) {
                    Text(
                        text = stringResource(R.string.trash_empty_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimensions.SpacingMedium)
            )
            Text(
                text = stringResource(R.string.trash_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrashItem(note: Note, retentionDays: Int, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.SpacingLarge, vertical = Dimensions.SpacingSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Dimensions.SpacingLarge,
                    top = Dimensions.SpacingMedium,
                    bottom = Dimensions.SpacingMedium,
                    end = Dimensions.SpacingSmall
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val days = remember(note.trashedAt, retentionDays) { daysLeft(note, retentionDays) }
                Text(
                    text = pluralStringResource(R.plurals.trash_days_left, days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore_cd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDeleteForever) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.trash_delete_forever_cd),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Verbleibende Tage bis zur automatischen Löschung (mind. 0, aufgerundet). */
private fun daysLeft(note: Note, retentionDays: Int): Int {
    val trashedAt = note.trashedAt ?: return retentionDays
    val retentionMs = retentionDays * Constants.DAY_MS
    val remaining = (retentionMs - (System.currentTimeMillis() - trashedAt)).coerceAtLeast(0L)
    return ((remaining + Constants.DAY_MS - 1) / Constants.DAY_MS).toInt()
}

@Composable
private fun RetentionDialog(selectedDays: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_retention_duration_title)) },
        text = { RetentionSection(selectedDays = selectedDays, onSelect = onSelect) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun RetentionSection(selectedDays: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        RadioOption(
            value = 0,
            title = stringResource(R.string.trash_retention_immediate),
            subtitle = stringResource(R.string.trash_retention_immediate_hint)
        ),
        RadioOption(value = 7, title = pluralStringResource(R.plurals.days, 7, 7)),
        RadioOption(value = 14, title = pluralStringResource(R.plurals.days, 14, 14)),
        RadioOption(value = 30, title = pluralStringResource(R.plurals.days, 30, 30)),
        RadioOption(value = 90, title = pluralStringResource(R.plurals.days, 90, 90))
    )
    SettingsRadioGroup(
        options = options,
        selectedValue = selectedDays,
        onValueSelected = onSelect
    )
}
