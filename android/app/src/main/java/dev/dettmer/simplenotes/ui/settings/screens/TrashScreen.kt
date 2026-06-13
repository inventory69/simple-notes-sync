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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🆕 v2.9.0 (Trash): Papierkorb-Screen. Listet getrashte Notizen (neueste zuerst), erlaubt
 * Wiederherstellen und endgültiges Löschen (mit Bestätigung) sowie „Papierkorb leeren".
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    viewModel: TrashViewModel = viewModel()
) {
    val notes by viewModel.trashedNotes.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    var pendingPurge by remember { mutableStateOf<Note?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    val restoredMessage = stringResource(R.string.snackbar_note_restored)
    val emptiedMessage = stringResource(R.string.snackbar_trash_emptied)

    SettingsScaffold(
        title = stringResource(R.string.trash_title),
        onBack = onBack,
        actions = {
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
        if (isReady && notes.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = Dimensions.SpacingMedium)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.trash_retention_hint, Constants.TRASH_RETENTION_DAYS),
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
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
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
private fun TrashItem(note: Note, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
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
                Text(
                    text = pluralStringResource(R.plurals.trash_days_left, daysLeft(note), daysLeft(note)),
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
private fun daysLeft(note: Note): Int {
    val trashedAt = note.trashedAt ?: return Constants.TRASH_RETENTION_DAYS
    val dayMs = 24L * 60L * 60L * 1000L
    val remaining = (Constants.TRASH_RETENTION_MS - (System.currentTimeMillis() - trashedAt)).coerceAtLeast(0L)
    return ((remaining + dayMs - 1) / dayMs).toInt()
}
