package dev.dettmer.simplenotes.ui.settings.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.ImageCompressionMode
import dev.dettmer.simplenotes.ui.editor.components.WordCounterVisibility
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SegmentLabel
import dev.dettmer.simplenotes.ui.settings.components.SettingsColorDots
import dev.dettmer.simplenotes.ui.settings.components.SettingsHint
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSegmentedRow
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import dev.dettmer.simplenotes.ui.settings.components.splitParenthetical
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
    val newDesign by viewModel.newDesign.collectAsState()

    SettingsSectionCard(title = stringResource(R.string.theme_mode_title)) {
        // UI-Beta: Schalter bleibt nur bis zur Entscheidung, danach entfällt er wieder.
        SettingsSwitch(
            title = stringResource(R.string.settings_new_design_toggle),
            subtitle = stringResource(R.string.settings_new_design_description),
            checked = newDesign,
            onCheckedChange = { viewModel.setNewDesign(it) },
            icon = Icons.Default.AutoAwesome
        )

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
    val hideCheckedInPreview by viewModel.hideCheckedInPreview.collectAsState()

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

        SettingsSwitch(
            title = stringResource(R.string.note_card_hide_checked_toggle),
            subtitle = stringResource(R.string.note_card_hide_checked_description),
            checked = hideCheckedInPreview,
            onCheckedChange = { viewModel.setHideCheckedInPreview(it) },
            icon = Icons.Default.Checklist
        )
    }
}

/** UI-Beta: nur das neue Blatt kann ohne Titelfeld, im alten Design bleibt der Schalter verborgen. */
@Composable
private fun TitleFieldSwitch(viewModel: SettingsViewModel) {
    val newDesign by viewModel.newDesign.collectAsState()
    val titleFieldEnabled by viewModel.titleFieldEnabled.collectAsState()
    if (!newDesign) return
    SettingsSwitch(
        title = stringResource(R.string.editor_title_field_toggle),
        subtitle = stringResource(R.string.editor_title_field_description),
        checked = titleFieldEnabled,
        onCheckedChange = { viewModel.setTitleFieldEnabled(it) },
        icon = Icons.Default.Title
    )
}

/** Karte 3: Editor-Verhalten + Standard-Notizfarbe. */
@Composable
private fun EditorSection(viewModel: SettingsViewModel, defaultNoteColor: String?, onDefaultColorClick: () -> Unit) {
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsState()
    val defaultStartInPreviewMode by viewModel.defaultStartInPreviewMode.collectAsState()
    val checklistStartInReadMode by viewModel.checklistStartInReadMode.collectAsState()
    val newNoteFocusContent by viewModel.newNoteFocusContent.collectAsState()
    val checklistScrollTopOnUncheck by viewModel.checklistScrollTopOnUncheck.collectAsState()
    val wordCounterVisibility by viewModel.wordCounterVisibility.collectAsState()
    val formattingBarEnabled by viewModel.formattingBarEnabled.collectAsState()

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

        // #131 (E11)
        SettingsSwitch(
            title = stringResource(R.string.editor_formatting_bar_toggle),
            subtitle = stringResource(R.string.editor_formatting_bar_description),
            checked = formattingBarEnabled,
            onCheckedChange = { viewModel.setFormattingBarEnabled(it) },
            icon = Icons.Default.TextFormat
        )

        TitleFieldSwitch(viewModel)

        // 🆕 Issue #156
        SettingsSwitch(
            title = stringResource(R.string.editor_checklist_read_mode_toggle),
            subtitle = stringResource(R.string.editor_checklist_read_mode_description),
            checked = checklistStartInReadMode,
            onCheckedChange = { viewModel.setChecklistStartInReadMode(it) },
            icon = Icons.Default.TouchApp
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

        // 🆕 (#126-Nachgang): Wortzähler immer, nur im Lesemodus oder gar nicht
        SettingsSectionHeader(text = stringResource(R.string.word_counter_toggle))
        SettingsRadioGroup(
            options = listOf(
                RadioOption(WordCounterVisibility.ALWAYS, stringResource(R.string.word_counter_always)),
                RadioOption(
                    WordCounterVisibility.PREVIEW_ONLY,
                    stringResource(R.string.word_counter_preview_only)
                ),
                RadioOption(WordCounterVisibility.OFF, stringResource(R.string.word_counter_off))
            ),
            selectedValue = wordCounterVisibility,
            onValueSelected = { viewModel.setWordCounterVisibility(it) }
        )
        SettingsHint(text = stringResource(R.string.word_counter_description))

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

        val defaultImageSizePercent by viewModel.defaultImageSizePercent.collectAsState()
        SettingsSectionHeader(text = stringResource(R.string.settings_default_image_size_title))
        DefaultImageSizeSelector(
            currentPercent = defaultImageSizePercent,
            onPercentSelected = { viewModel.setDefaultImageSizePercent(it) }
        )
        SettingsHint(text = stringResource(R.string.settings_default_image_size_info))
    }
}

// Gleiche Presets wie das Long-Press-Bildmenü (ImageActionsMenu.SIZE_PRESETS) — bewusst dupliziert
// statt geteilt, da dort private und UI-Kontext (Popup) ein anderer ist.
private val DEFAULT_IMAGE_SIZE_PRESETS = listOf(
    R.string.image_menu_size_small to 25,
    R.string.image_menu_size_medium to 50,
    R.string.image_menu_size_large to 75,
    R.string.image_menu_size_full to 100
)

@Composable
private fun DefaultImageSizeSelector(currentPercent: Int, onPercentSelected: (Int) -> Unit) {
    SettingsSegmentedRow(
        options = DEFAULT_IMAGE_SIZE_PRESETS.map { it.second },
        selected = currentPercent,
        onSelect = onPercentSelected
    ) { percent ->
        SegmentLabel(stringResource(DEFAULT_IMAGE_SIZE_PRESETS.first { it.second == percent }.first))
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
    SettingsSegmentedRow(options = listOf("list", "grid"), selected = currentMode, onSelect = onModeSelected) { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (mode == "list") Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            SegmentLabel(stringResource(if (mode == "list") R.string.display_mode_list else R.string.display_mode_grid))
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
    SettingsSegmentedRow(
        options = (Constants.GRID_MIN_COLUMNS..Constants.GRID_MAX_COLUMNS).toList(),
        selected = currentColumns,
        onSelect = onColumnsSelected
    ) { count -> SegmentLabel("$count") }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThemeModeSelector — FlowRow grid with selectable chips (matches ColorThemeSelector)
// 4 static items — FlowRow renders all at once without recycling overhead.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeModeSelector(currentMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    // S2: Zusatz in Klammern („System (follow device)“) wandert unter die Zeile, das Segment trägt nur den Namen.
    SettingsSegmentedRow(options = ThemeMode.entries, selected = currentMode, onSelect = onModeSelected) { mode ->
        SegmentLabel(splitParenthetical(stringResource(mode.displayNameResId)).first)
    }
    splitParenthetical(stringResource(currentMode.displayNameResId)).second
        ?.let { SettingsHint(text = it.replaceFirstChar { c -> c.uppercase() }, modifier = Modifier.padding(top = 0.dp)) }
}

// ─────────────────────────────────────────────────────────────────────────────
// ColorThemeSelector — FlowRow with color preview chips
// FlowRow (not LazyRow/LazyGrid): 7 static items — no recycling overhead,
// all chips visible at once without horizontal scrolling.
// ─────────────────────────────────────────────────────────────────────────────

// Regenbogen-Sweep wie im UI-Artifact: die Farben kommen erst vom Hintergrundbild.
private val DynamicSwatch = Brush.sweepGradient(
    listOf(
        ColorTheme.DEFAULT.previewColor,
        Color(0xFF1565C0),
        Color(0xFF2E7D32),
        Color(0xFFB45309),
        Color(0xFFC62828),
        ColorTheme.DEFAULT.previewColor
    )
)

@Composable
private fun ColorThemeSelector(currentTheme: ColorTheme, onThemeSelected: (ColorTheme) -> Unit) {
    val dynamicUnavailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    // S2: sieben Farbpunkte mit Namen statt Kacheln
    SettingsColorDots(
        options = ColorTheme.entries,
        selected = currentTheme,
        onSelect = onThemeSelected,
        color = { it.previewColor },
        name = { stringResource(it.displayNameResId) },
        enabled = { !(it == ColorTheme.DYNAMIC && dynamicUnavailable) },
        brush = { if (it == ColorTheme.DYNAMIC) DynamicSwatch else SolidColor(it.previewColor) }
    )

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
    SettingsSegmentedRow(options = NotePreviewLength.entries, selected = currentLength, onSelect = onLengthSelected) {
        SegmentLabel(stringResource(it.displayNameResId))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FontSizeSelector + FontSizeChip — chip row for text size preference
// 5 static options: System / Small / Normal / Large / Extra large.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FontSizeSelector(currentScale: FontSizeScale, onScaleSelected: (FontSizeScale) -> Unit) {
    // S2: „System“ als Wort, die festen Stufen als „Aa“ in ihrer Größe; der Name der Wahl steht darunter.
    SettingsSegmentedRow(options = FontSizeScale.entries, selected = currentScale, onSelect = onScaleSelected) { scale ->
        val multiplier = scale.multiplier
        if (multiplier == null) {
            SegmentLabel(stringResource(scale.displayNameResId))
        } else {
            val name = stringResource(scale.displayNameResId)
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * multiplier
                ),
                maxLines = 1,
                // TalkBack liest sonst fünfmal „Aa“.
                modifier = Modifier.semantics { contentDescription = name }
            )
        }
    }
    // „System“ steht schon im Segment; die festen Stufen zeigen nur „Aa“, ihr Name steht darunter.
    if (currentScale.multiplier != null) SettingsHint(text = stringResource(currentScale.displayNameResId))
}
