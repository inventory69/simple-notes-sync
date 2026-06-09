package dev.dettmer.simplenotes.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListWidgetConfigScreen(
    initialSortOption: SortOption,
    initialSortDirection: SortDirection,
    initialFilter: NoteFilter,
    initialOpacity: Float = 1.0f,
    initialApplyOpacityToCards: Boolean = false,
    onSave: (SortOption, SortDirection, NoteFilter, Float, Boolean) -> Unit
) {
    var sortOption by remember { mutableStateOf(initialSortOption) }
    var sortDirection by remember { mutableStateOf(initialSortDirection) }
    var noteFilter by remember { mutableStateOf(initialFilter) }
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    var applyOpacityToCards by remember { mutableStateOf(initialApplyOpacityToCards) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.notes_list_widget_config_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onSave(sortOption, sortDirection, noteFilter, opacity, applyOpacityToCards) }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.notes_list_widget_config_save)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Sort by ──
            Text(
                text = stringResource(R.string.notes_list_widget_config_sort_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            val sortOptions = listOf(
                SortOption.UPDATED_AT to stringResource(R.string.sort_by_updated),
                SortOption.CREATED_AT to stringResource(R.string.sort_by_created),
                SortOption.TITLE to stringResource(R.string.sort_by_name),
                SortOption.NOTE_TYPE to stringResource(R.string.sort_by_type)
            )
            sortOptions.forEach { (option, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { sortOption = option }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sortOption == option,
                        onClick = { sortOption = option }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ── Direction (hidden for NOTE_TYPE — secondary sort handles order) ──
            if (sortOption != SortOption.NOTE_TYPE) {
                Spacer(Modifier.width(0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortDirection == SortDirection.DESCENDING,
                        onClick = { sortDirection = SortDirection.DESCENDING },
                        label = { Text(stringResource(R.string.sort_descending)) }
                    )
                    FilterChip(
                        selected = sortDirection == SortDirection.ASCENDING,
                        onClick = { sortDirection = SortDirection.ASCENDING },
                        label = { Text(stringResource(R.string.sort_ascending)) }
                    )
                }
            }

            // ── Show (filter by type) ──
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.notes_list_widget_config_filter_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    NoteFilter.ALL to stringResource(R.string.filter_all),
                    NoteFilter.TEXT_ONLY to stringResource(R.string.filter_text_only),
                    NoteFilter.CHECKLIST_ONLY to stringResource(R.string.filter_checklist_only)
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = noteFilter == filter,
                        onClick = { noteFilter = filter },
                        label = { Text(label) }
                    )
                }
            }

            // ── Background opacity ──
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_opacity_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${(opacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.widget_opacity_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f,
                    steps = 9
                )
            }

            if (opacity < 1.0f) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.widget_cards_opacity_label),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.widget_cards_opacity_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = applyOpacityToCards,
                        onCheckedChange = { applyOpacityToCards = it }
                    )
                }
            }

            // Spacer so FAB doesn't cover the last item
            Spacer(Modifier.padding(bottom = 80.dp))
        }
    }
}
