package dev.dettmer.simplenotes.ui.settings.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.ImageCompressionMode
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsChip
import dev.dettmer.simplenotes.ui.settings.components.SettingsChipRow
import dev.dettmer.simplenotes.ui.settings.components.SettingsHint
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.FontSizeScale
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🎨 v1.7.0: Display Settings Screen
 * 🆕 v1.9.0 (F05): Added Custom App Title section
 * v2.0.0: Added Appearance (ThemeMode) + Color scheme (ColorTheme) sections
 *
 * Allows switching between List and Grid view modes,
 * and setting a custom app title for the main screen.
 */
@Composable
fun DisplaySettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    // Nur der Farbwähler-State lebt hier — er wird von Karte 3 *und* vom Sheet gebraucht,
    // das außerhalb des Scroll-Columns gerendert wird. Alles andere sammelt seine Sektion selbst.
    val defaultNoteColor by viewModel.defaultNoteColor.collectAsState()
    var showDefaultColorPicker by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.display_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AppearanceSection(viewModel)

            NoteListSection(viewModel)

            EditorSection(
                viewModel = viewModel,
                defaultNoteColor = defaultNoteColor,
                onDefaultColorClick = { showDefaultColorPicker = true }
            )

            ImageCompressionSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDefaultColorPicker) {
        NoteColorPickerSheet(
            currentColor = defaultNoteColor,
            onColorSelected = { hex -> viewModel.setDefaultNoteColor(hex) },
            onDismiss = { showDefaultColorPicker = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Sektionskarten — jede mit eigenem collectAsState()-Scope, damit ein Toggle
// nicht den kompletten Screen rekomponiert (analog zu SyncSettingsScreen).
// ═══════════════════════════════════════════════════════════════════════════════

/** Karte 1: Theme, Farbschema, Schriftgröße, App-Titel. */
@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val customAppTitle by viewModel.customAppTitle.collectAsState()

    SettingsSectionCard(title = stringResource(R.string.theme_mode_title)) {
        SettingsSectionHeader(text = stringResource(R.string.theme_mode_subsection))

        ThemeModeSelector(
            currentMode = themeMode,
            onModeSelected = { viewModel.setThemeMode(it) }
        )

        SettingsSectionHeader(text = stringResource(R.string.theme_color_title))

        ColorThemeSelector(
            currentTheme = colorTheme,
            onThemeSelected = { viewModel.setColorTheme(it) }
        )

        SettingsSectionHeader(text = stringResource(R.string.font_size_title))

        FontSizeSelector(
            currentScale = fontSizeScale,
            onScaleSelected = { viewModel.setFontSizeScale(it) }
        )

        SettingsSectionHeader(text = stringResource(R.string.custom_app_title_section))

        OutlinedTextField(
            value = customAppTitle,
            onValueChange = { newValue ->
                if (newValue.length <= Constants.MAX_CUSTOM_APP_TITLE_LENGTH) {
                    viewModel.setCustomAppTitle(newValue)
                }
            },
            label = { Text(stringResource(R.string.custom_app_title_label)) },
            placeholder = { Text(stringResource(R.string.custom_app_title_placeholder)) },
            singleLine = true,
            supportingText = {
                Text(
                    text = stringResource(
                        R.string.custom_app_title_char_count,
                        customAppTitle.length,
                        Constants.MAX_CUSTOM_APP_TITLE_LENGTH
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (customAppTitle.length >= Constants.MAX_CUSTOM_APP_TITLE_LENGTH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        SettingsHint(text = stringResource(R.string.custom_app_title_info))
    }
}

/** Karte 2: Ansichtsmodus, Raster-Spalten, Vorschaulänge, Kartenelemente. */
@Composable
private fun NoteListSection(viewModel: SettingsViewModel) {
    val displayMode by viewModel.displayMode.collectAsState()
    val gridAdaptiveScaling by viewModel.gridAdaptiveScaling.collectAsState()
    val gridManualColumns by viewModel.gridManualColumns.collectAsState()
    val notePreviewLength by viewModel.notePreviewLength.collectAsState()
    val showNoteTimestamp by viewModel.showNoteTimestamp.collectAsState()
    val showNoteTypeIcon by viewModel.showNoteTypeIcon.collectAsState()

    SettingsSectionCard(title = stringResource(R.string.settings_section_note_list)) {
        SettingsSectionHeader(text = stringResource(R.string.display_mode_title))

        DisplayModeSelector(
            currentMode = displayMode,
            onModeSelected = { viewModel.setDisplayMode(it) }
        )

        SettingsHint(text = stringResource(R.string.display_mode_info))

        // 🆕 v2.1.0 (F46): Grid column control — only visible when grid mode is active
        if (displayMode == "grid") {
            SettingsSectionHeader(text = stringResource(R.string.grid_scaling_title))

            SettingsSwitch(
                title = stringResource(R.string.grid_adaptive_scaling_title),
                subtitle = stringResource(R.string.grid_adaptive_scaling_subtitle),
                checked = gridAdaptiveScaling,
                onCheckedChange = { viewModel.setGridAdaptiveScaling(it) }
            )

            AnimatedVisibility(visible = !gridAdaptiveScaling) {
                Column {
                    SettingsSectionHeader(text = stringResource(R.string.grid_manual_columns_title))

                    GridColumnSelector(
                        currentColumns = gridManualColumns,
                        onColumnsSelected = { viewModel.setGridManualColumns(it) }
                    )
                }
            }

            SettingsHint(text = stringResource(R.string.grid_scaling_info))
        }

        SettingsSectionHeader(text = stringResource(R.string.note_preview_length_title))

        NotePreviewLengthSelector(
            currentLength = notePreviewLength,
            onLengthSelected = { viewModel.setNotePreviewLength(it) }
        )

        SettingsHint(text = stringResource(R.string.note_preview_length_info))

        // 🆕 Issue #100: Zeitstempel/Icon auf Notizkarten ausblendbar
        SettingsSwitch(
            title = stringResource(R.string.note_card_show_timestamp_toggle),
            subtitle = stringResource(R.string.note_card_show_timestamp_description),
            checked = showNoteTimestamp,
            onCheckedChange = { viewModel.setShowNoteTimestamp(it) },
            icon = Icons.Default.Schedule
        )

        SettingsSwitch(
            title = stringResource(R.string.note_card_show_icon_toggle),
            subtitle = stringResource(R.string.note_card_show_icon_description),
            checked = showNoteTypeIcon,
            onCheckedChange = { viewModel.setShowNoteTypeIcon(it) },
            icon = Icons.AutoMirrored.Filled.Notes
        )
    }
}

/** Karte 3: Editor-Verhalten + Standard-Notizfarbe. */
@Composable
private fun EditorSection(viewModel: SettingsViewModel, defaultNoteColor: String?, onDefaultColorClick: () -> Unit) {
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsState()
    val defaultStartInPreviewMode by viewModel.defaultStartInPreviewMode.collectAsState()
    val newNoteFocusContent by viewModel.newNoteFocusContent.collectAsState()
    val checklistScrollTopOnUncheck by viewModel.checklistScrollTopOnUncheck.collectAsState()

    SettingsSectionCard(title = stringResource(R.string.autosave_section)) {
        SettingsSwitch(
            title = stringResource(R.string.autosave_toggle),
            subtitle = stringResource(R.string.autosave_description),
            checked = autosaveEnabled,
            onCheckedChange = { viewModel.setAutosaveEnabled(it) },
            icon = Icons.Default.Save
        )

        SettingsHint(text = stringResource(R.string.autosave_info))

        SettingsSwitch(
            title = stringResource(R.string.editor_default_preview_mode_toggle),
            subtitle = stringResource(R.string.editor_default_preview_mode_description),
            checked = defaultStartInPreviewMode,
            onCheckedChange = { viewModel.setDefaultStartInPreviewMode(it) },
            icon = Icons.Default.Visibility
        )

        // 🆕 v2.11.0: Cursor-Start für neue Notizen
        SettingsSwitch(
            title = stringResource(R.string.editor_new_note_focus_content_toggle),
            subtitle = stringResource(R.string.editor_new_note_focus_content_description),
            checked = newNoteFocusContent,
            onCheckedChange = { viewModel.setNewNoteFocusContent(it) },
            icon = Icons.Default.EditNote
        )

        // 🆕 Issue #112: Un-Check scrollt optional nicht mehr an den Listenanfang
        SettingsSwitch(
            title = stringResource(R.string.checklist_scroll_top_on_uncheck_toggle),
            subtitle = stringResource(R.string.checklist_scroll_top_on_uncheck_description),
            checked = checklistScrollTopOnUncheck,
            onCheckedChange = { viewModel.setChecklistScrollTopOnUncheck(it) },
            icon = Icons.Default.Checklist
        )

        // 🆕 v2.11.0: Standard-Notizfarbe für neue Notizen
        DefaultNoteColorRow(
            currentColor = defaultNoteColor,
            onClick = onDefaultColorClick
        )
    }
}

/** Karte 4: Kompressionsmodus für Bild-Anhänge. */
@Composable
private fun ImageCompressionSection(viewModel: SettingsViewModel) {
    val imageCompressionMode by viewModel.imageCompressionMode.collectAsState()

    SettingsSectionCard(title = stringResource(R.string.settings_image_compression_title)) {
        SettingsRadioGroup(
            options = listOf(
                RadioOption(
                    ImageCompressionMode.COMPRESSED,
                    stringResource(R.string.image_compression_mode_compressed),
                    stringResource(R.string.image_compression_mode_compressed_subtitle)
                ),
                RadioOption(
                    ImageCompressionMode.LOSSLESS,
                    stringResource(R.string.image_compression_mode_lossless),
                    stringResource(R.string.image_compression_mode_lossless_subtitle)
                ),
                RadioOption(
                    ImageCompressionMode.ORIGINAL,
                    stringResource(R.string.image_compression_mode_original),
                    stringResource(R.string.image_compression_mode_original_subtitle)
                )
            ),
            selectedValue = imageCompressionMode,
            onValueSelected = { viewModel.setImageCompressionMode(it) }
        )

        SettingsHint(text = stringResource(R.string.settings_image_compression_info))
    }
}

// 🆕 v2.11.0: DefaultNoteColorRow — Zeile mit aktueller Standard-Notizfarbe,
// öffnet den NoteColorPickerSheet (wiederverwendet aus ui/main/components).
@Composable
private fun DefaultNoteColorRow(currentColor: String?, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val swatchColor = if (currentColor != null) {
        NoteColorPalette.resolveContainer(currentColor, isDark)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_default_note_color_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (currentColor != null) {
                    stringResource(R.string.settings_default_note_color_subtitle)
                } else {
                    stringResource(R.string.note_color_none)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DisplayModeSelector — FlowRow grid matching ThemeModeSelector / ColorThemeSelector
// Two items with preview icons (list lines vs grid squares).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisplayModeSelector(currentMode: String, onModeSelected: (String) -> Unit) {
    SettingsChipRow {
        SettingsChip(
            label = stringResource(R.string.display_mode_list),
            selected = currentMode == "list",
            onClick = { onModeSelected("list") }
        ) { contentColor ->
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        }
        SettingsChip(
            label = stringResource(R.string.display_mode_grid),
            selected = currentMode == "grid",
            onClick = { onModeSelected("grid") }
        ) { contentColor ->
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GridColumnSelector + GridColumnChip — Schritt 7 (F46)
// Chip-Row for manual grid column count (1–5). Only shown when adaptive scaling
// is disabled. Visually consistent with DisplayModeChip.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GridColumnSelector(currentColumns: Int, onColumnsSelected: (Int) -> Unit) {
    SettingsChipRow {
        for (count in Constants.GRID_MIN_COLUMNS..Constants.GRID_MAX_COLUMNS) {
            SettingsChip(
                label = "$count",
                selected = currentColumns == count,
                onClick = { onColumnsSelected(count) },
                modifier = Modifier.widthIn(min = 56.dp)
            ) { contentColor ->
                // Mini-grid preview: N small squares side by side
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(count) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = contentColor, shape = RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThemeModeSelector — FlowRow grid with selectable chips (matches ColorThemeSelector)
// 4 static items — FlowRow renders all at once without recycling overhead.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeModeSelector(currentMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    SettingsChipRow {
        ThemeMode.entries.forEach { mode ->
            SettingsChip(
                label = stringResource(mode.displayNameResId),
                selected = currentMode == mode,
                onClick = { onModeSelected(mode) }
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(mode.previewColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ColorThemeSelector — FlowRow with color preview chips
// FlowRow (not LazyRow/LazyGrid): 7 static items — no recycling overhead,
// all chips visible at once without horizontal scrolling.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColorThemeSelector(currentTheme: ColorTheme, onThemeSelected: (ColorTheme) -> Unit) {
    val dynamicUnavailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    SettingsChipRow {
        ColorTheme.entries.forEach { theme ->
            val isDisabled = theme == ColorTheme.DYNAMIC && dynamicUnavailable
            SettingsChip(
                label = stringResource(theme.displayNameResId),
                selected = currentTheme == theme,
                enabled = !isDisabled,
                onClick = { onThemeSelected(theme) }
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(theme.previewColor)
                )
            }
        }
    }

    if (dynamicUnavailable) {
        Text(
            text = stringResource(R.string.theme_color_dynamic_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NotePreviewLengthSelector + NotePreviewLengthChip — chip row for the note preview
// length preset (List + Grid). 4 static options: Compact / Standard / Long / Extra long.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotePreviewLengthSelector(currentLength: NotePreviewLength, onLengthSelected: (NotePreviewLength) -> Unit) {
    SettingsChipRow {
        NotePreviewLength.entries.forEach { length ->
            SettingsChip(
                label = stringResource(length.displayNameResId),
                selected = currentLength == length,
                onClick = { onLengthSelected(length) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FontSizeSelector + FontSizeChip — chip row for text size preference
// 5 static options: System / Small / Normal / Large / Extra large.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FontSizeSelector(currentScale: FontSizeScale, onScaleSelected: (FontSizeScale) -> Unit) {
    SettingsChipRow {
        FontSizeScale.entries.forEach { scale ->
            SettingsChip(
                label = stringResource(scale.displayNameResId),
                selected = currentScale == scale,
                onClick = { onScaleSelected(scale) }
            ) { contentColor ->
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * (scale.multiplier ?: 1.0f)
                    ),
                    color = contentColor
                )
            }
        }
    }
}
