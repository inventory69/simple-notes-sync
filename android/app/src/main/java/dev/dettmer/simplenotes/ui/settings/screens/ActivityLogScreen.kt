package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.ComposeNoteEditorActivity
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.ActivityLog
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * 🆕 Issue #128 Teil 3: Aktivitätsprotokoll-Screen. Tagesweise gruppiert, neueste zuerst.
 * Zeigt ausschließlich, was auf DIESEM Gerät passiert ist (siehe [ActivityLogViewModel]).
 */
@Composable
fun ActivityLogScreen(
    onBack: () -> Unit,
    viewModel: ActivityLogViewModel = viewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val existingNoteIds by viewModel.existingNoteIds.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.activity_log_title),
        onBack = onBack,
        actions = {
            ActivityLogActions(
                hasEntries = entries.isNotEmpty(),
                viewModel = viewModel,
                onRequestClear = { showClearConfirm = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (entries.isEmpty()) {
                EmptyActivityState(Modifier.weight(1f))
            } else {
                ActivityLogList(
                    entries = entries,
                    hasMore = hasMore,
                    existingNoteIds = existingNoteIds,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showClearConfirm) {
        ClearConfirmDialog(
            onConfirm = {
                viewModel.clearLocal()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun ActivityLogActions(
    hasEntries: Boolean,
    viewModel: ActivityLogViewModel,
    onRequestClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        scope.launch {
            val file = viewModel.prepareShareFile() ?: return@launch
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }) {
        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.activity_log_share_cd))
    }
    if (hasEntries) {
        IconButton(onClick = onRequestClear) {
            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.activity_log_clear_cd))
        }
    }
}

@Composable
private fun ActivityLogList(
    entries: List<ActivityUiEntry>,
    hasMore: Boolean,
    existingNoteIds: Set<String>,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Dimensions.SpacingMedium)
    ) {
        var lastDay = -1L
        // Key enthält den Listenindex: Zeitstempel + Op + ID sind NICHT eindeutig (zwei Ordner in
        // derselben Millisekunde gelöscht ⇒ zweimal derselbe Key ⇒ LazyColumn wirft). Der Index ist
        // gleichzeitig der Sekundärschlüssel für stabile Reihenfolge bei Uhren-Drift (Edge Case 3).
        entries.forEachIndexed { index, uiEntry ->
            if (uiEntry.dayStartMs != lastDay) {
                lastDay = uiEntry.dayStartMs
                item(key = "header_${uiEntry.dayStartMs}", contentType = "DayHeader") {
                    DayHeader(uiEntry.dayStartMs)
                }
            }
            item(key = "entry_${index}_${uiEntry.entry.ts}", contentType = "Entry") {
                ActivityRow(
                    uiEntry = uiEntry,
                    noteExists = uiEntry.entry.id != null && uiEntry.entry.id in existingNoteIds,
                    onClick = {
                        val id = uiEntry.entry.id
                        if (id != null && id in existingNoteIds) {
                            context.startActivity(
                                Intent(context, ComposeNoteEditorActivity::class.java)
                                    .putExtra(ComposeNoteEditorActivity.EXTRA_NOTE_ID, id)
                            )
                        }
                    }
                )
            }
        }
    }

    // Einfaches "Load more" statt Cursor-State: verdoppelt die Seitengröße am Listenende
    // (siehe ActivityLogViewModel.loadMore).
    LaunchedEffect(listState, hasMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1
        }.collect { isEnd -> if (isEnd && hasMore) onLoadMore() }
    }
}

@Composable
private fun ClearConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.activity_log_clear_confirm_title)) },
        text = { Text(stringResource(R.string.activity_log_clear_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.activity_log_clear_cd), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EmptyActivityState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimensions.SpacingMedium)
            )
            Text(
                text = stringResource(R.string.activity_log_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayHeader(dayStartMs: Long) {
    val today = remember { startOfToday() }
    val label = when (dayStartMs) {
        today -> stringResource(R.string.activity_log_today)
        today - MS_PER_DAY -> stringResource(R.string.activity_log_yesterday)
        else -> formatDayHeader(dayStartMs)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            horizontal = Dimensions.SpacingLarge,
            vertical = Dimensions.SpacingSmall
        )
    )
}

private const val MS_PER_DAY = 24L * 60 * 60 * 1000

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun ActivityRow(
    uiEntry: ActivityUiEntry,
    noteExists: Boolean,
    onClick: () -> Unit
) {
    val entry = uiEntry.entry
    val clickable = entry.id != null && noteExists
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (clickable) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Dimensions.SpacingLarge, vertical = Dimensions.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
    ) {
        Icon(
            imageVector = if (entry.src == ActivityLog.Src.REMOTE) Icons.Default.Sync else Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.activity_untitled_sync_entry),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (entry.id != null && !noteExists) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            val folderSuffix = entry.folder?.let { stringResource(R.string.activity_folder_suffix, it) }.orEmpty()
            // ponytail: activity_folder_suffix ist generisch " · %1$s" — kein zweiter Separator-String.
            val triggerSuffix = triggerLabel(entry.trigger)
                ?.let { stringResource(R.string.activity_folder_suffix, it) }.orEmpty()
            Text(
                text = opLabel(entry) + folderSuffix + triggerSuffix,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (entry.id != null && !noteExists) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = stringResource(R.string.activity_log_note_gone_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = uiEntry.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun opLabel(entry: ActivityLog.Entry): String = when (entry.op) {
    ActivityLog.Op.CREATE -> stringResource(R.string.activity_op_create)
    ActivityLog.Op.EDIT -> stringResource(R.string.activity_op_edit)
    ActivityLog.Op.TRASH -> stringResource(R.string.activity_op_trash)
    ActivityLog.Op.RESTORE -> if (entry.why == "self_heal_present_on_server") {
        stringResource(R.string.activity_op_restore_self_heal)
    } else {
        stringResource(R.string.activity_op_restore)
    }
    ActivityLog.Op.PURGE -> if (entry.why == "auto_purge_expired") {
        stringResource(R.string.activity_op_purge_auto)
    } else {
        stringResource(R.string.activity_op_purge)
    }
    ActivityLog.Op.UPLOAD -> stringResource(R.string.activity_op_upload)
    ActivityLog.Op.DOWNLOAD -> stringResource(R.string.activity_op_download)
    ActivityLog.Op.CONFLICT -> stringResource(R.string.activity_op_conflict)
    ActivityLog.Op.FOLDER_DELETE -> stringResource(R.string.activity_op_folder_delete, entry.folder ?: "")
    ActivityLog.Op.SYNC_OK -> stringResource(R.string.activity_op_sync_ok)
    ActivityLog.Op.SYNC_FAIL -> stringResource(R.string.activity_op_sync_fail, entry.err ?: "?")
    ActivityLog.Op.DELETION_SKIPPED -> stringResource(R.string.activity_op_deletion_skipped)
}

@Composable
private fun triggerLabel(t: ActivityLog.Trigger?): String? = when (t) {
    null -> null
    // Vier Wege, denselben Knopf zu drücken — im UI eine Zeile, im File weiter unterscheidbar.
    ActivityLog.Trigger.TOOLBAR, ActivityLog.Trigger.PULL_REFRESH,
    ActivityLog.Trigger.SETTINGS, ActivityLog.Trigger.FOLDER_INCLUDE ->
        stringResource(R.string.activity_trigger_manual)
    ActivityLog.Trigger.RESUME -> stringResource(R.string.activity_trigger_resume)
    ActivityLog.Trigger.ONSAVE -> stringResource(R.string.activity_trigger_onsave)
    ActivityLog.Trigger.WIFI_CONNECT, ActivityLog.Trigger.WIFI_FALLBACK ->
        stringResource(R.string.activity_trigger_wifi)
    ActivityLog.Trigger.PERIODIC -> stringResource(R.string.activity_trigger_periodic)
}
