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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.NotesStorage
import kotlin.math.roundToInt

/**
 * 🆕 v1.8.0: Compose Screen für Widget-Konfiguration
 *
 * Zeigt alle Notizen als auswählbare Liste.
 * Optionen: Widget-Lock, Hintergrund-Transparenz.
 * Unterstützt Reconfiguration mit bestehenden Defaults.
 *
 * 🆕 v1.8.0 (IMPL_025): Save-FAB + onSettingsChanged für Reconfigure-Flow
 */

private const val NOTE_PREVIEW_MAX_LENGTH = 50

private val WIDGET_FONT_SCALE_STEPS = listOf(0.85f, 1.0f, 1.15f, 1.3f)

private fun fontScaleToSlider(scale: Float): Float {
    val idx = WIDGET_FONT_SCALE_STEPS.indexOfFirst { kotlin.math.abs(it - scale) < 0.05f }
    return if (idx < 0) 1f else idx.toFloat()
}

private fun sliderToFontScale(pos: Float): Float =
    WIDGET_FONT_SCALE_STEPS[pos.roundToInt().coerceIn(0, WIDGET_FONT_SCALE_STEPS.lastIndex)]

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun NoteWidgetConfigScreen(
    storage: NotesStorage,
    initialLock: Boolean = false,
    initialOpacity: Float = 1.0f,
    initialFontSizeScale: Float = 1.0f,
    selectedNoteId: String? = null,
    configLoadError: Boolean = false,
    onNoteSelected: (noteId: String, isLocked: Boolean, opacity: Float, fontSizeScale: Float) -> Unit,
    onSave: ((noteId: String, isLocked: Boolean, opacity: Float, fontSizeScale: Float) -> Unit)? = null,
    onSettingsChanged: ((noteId: String?, isLocked: Boolean, opacity: Float, fontSizeScale: Float) -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onCancel: () -> Unit // Reserved for future use
) {
    val allNotes by produceState<List<Note>>(initialValue = emptyList()) {
        // 🆕 v2.9.0 (Trash): getrashte Notizen nicht zur Widget-Auswahl anbieten.
        value = storage.loadActiveNotes().sortedByDescending { it.updatedAt }
    }
    var lockWidget by remember { mutableStateOf(initialLock) }
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    var fontSizeScale by remember { mutableFloatStateOf(initialFontSizeScale) }
    var currentSelectedId by remember { mutableStateOf(selectedNoteId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) }
            )
        },
        floatingActionButton = {
            // 🆕 v1.8.0 (IMPL_025): Save-FAB — sichtbar wenn eine Note ausgewählt ist
            if (currentSelectedId != null) {
                FloatingActionButton(
                    onClick = {
                        currentSelectedId?.let { noteId ->
                            onSave?.invoke(noteId, lockWidget, opacity, fontSizeScale)
                                ?: onNoteSelected(noteId, lockWidget, opacity, fontSizeScale)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.widget_config_save)
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
            if (configLoadError) {
                Text(
                    text = stringResource(R.string.widget_config_load_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.widget_lock_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.widget_lock_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = lockWidget,
                    onCheckedChange = {
                        lockWidget = it
                        // 🆕 v1.8.0 (IMPL_025): Settings-Änderung an Activity melden
                        onSettingsChanged?.invoke(currentSelectedId, lockWidget, opacity, fontSizeScale)
                    }
                )
            }

            // Opacity-Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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
                    onValueChange = {
                        opacity = it
                        // 🆕 v1.8.0 (IMPL_025): Settings-Änderung an Activity melden
                        onSettingsChanged?.invoke(currentSelectedId, lockWidget, opacity, fontSizeScale)
                    },
                    valueRange = 0f..1f,
                    steps = 9
                )
            }

            // Font size slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val fontSizeLabel = when (fontScaleToSlider(fontSizeScale).roundToInt()) {
                    0 -> stringResource(R.string.font_size_small)
                    2 -> stringResource(R.string.font_size_large)
                    3 -> stringResource(R.string.font_size_xlarge)
                    else -> stringResource(R.string.font_size_normal)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.widget_font_size_label),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = fontSizeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.widget_font_size_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = fontScaleToSlider(fontSizeScale),
                    onValueChange = {
                        fontSizeScale = sliderToFontScale(it)
                        onSettingsChanged?.invoke(currentSelectedId, lockWidget, opacity, fontSizeScale)
                    },
                    valueRange = 0f..3f,
                    steps = 2
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Hinweis
            Text(
                text = stringResource(R.string.widget_config_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Notizen-Liste
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allNotes, key = { it.id }) { note ->
                    NoteSelectionCard(
                        note = note,
                        isSelected = note.id == currentSelectedId,
                        onClick = {
                            currentSelectedId = note.id
                            // 🐛 FIX: Nur auswählen + Settings-Tracking, NICHT sofort konfigurieren
                            onSettingsChanged?.invoke(note.id, lockWidget, opacity, fontSizeScale)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteSelectionCard(note: Note, isSelected: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (note.noteType) {
                    NoteType.TEXT -> Icons.Outlined.Description
                    NoteType.CHECKLIST -> Icons.AutoMirrored.Outlined.List
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = when (note.noteType) {
                        NoteType.TEXT -> note.content.take(NOTE_PREVIEW_MAX_LENGTH).replace("\n", " ")
                        NoteType.CHECKLIST -> {
                            val items = note.checklistItems.orEmpty()
                            val checked = items.count { it.isChecked }
                            "✔ $checked/${items.size}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    maxLines = 1
                )
            }
        }
    }
}
