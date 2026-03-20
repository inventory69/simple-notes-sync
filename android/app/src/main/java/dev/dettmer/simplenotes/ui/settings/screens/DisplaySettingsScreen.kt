package dev.dettmer.simplenotes.ui.settings.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import dev.dettmer.simplenotes.ui.theme.ColorTheme
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
    val displayMode by viewModel.displayMode.collectAsState()
    val gridAdaptiveScaling by viewModel.gridAdaptiveScaling.collectAsState()
    val gridManualColumns by viewModel.gridManualColumns.collectAsState()
    val customAppTitle by viewModel.customAppTitle.collectAsState()
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()

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

            // ── v2.0.0: Appearance (ThemeMode) Section ──
            SettingsSectionHeader(text = stringResource(R.string.theme_mode_title))

            ThemeModeSelector(
                currentMode = themeMode,
                onModeSelected = { viewModel.setThemeMode(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── v2.0.0: Color Scheme (ColorTheme) Section ──
            SettingsSectionHeader(text = stringResource(R.string.theme_color_title))

            ColorThemeSelector(
                currentTheme = colorTheme,
                onThemeSelected = { viewModel.setColorTheme(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Display Mode Section ──
            SettingsSectionHeader(text = stringResource(R.string.display_mode_title))

            DisplayModeSelector(
                currentMode = displayMode,
                onModeSelected = { viewModel.setDisplayMode(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsInfoCard(
                text = stringResource(R.string.display_mode_info)
            )

            // 🆕 v2.1.0 (F46): Grid column control — only visible when grid mode is active
            if (displayMode == "grid") {
                Spacer(modifier = Modifier.height(24.dp))

                SettingsSectionHeader(text = stringResource(R.string.grid_scaling_title))

                SettingsSwitch(
                    title = stringResource(R.string.grid_adaptive_scaling_title),
                    subtitle = stringResource(R.string.grid_adaptive_scaling_subtitle),
                    checked = gridAdaptiveScaling,
                    onCheckedChange = { viewModel.setGridAdaptiveScaling(it) }
                )

                AnimatedVisibility(visible = !gridAdaptiveScaling) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsSectionHeader(text = stringResource(R.string.grid_manual_columns_title))

                        GridColumnSelector(
                            currentColumns = gridManualColumns,
                            onColumnsSelected = { viewModel.setGridManualColumns(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SettingsInfoCard(
                    text = stringResource(R.string.grid_scaling_info)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 🆕 v1.9.0 (F05): Custom App Title Section ──
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

            Spacer(modifier = Modifier.height(8.dp))

            SettingsInfoCard(
                text = stringResource(R.string.custom_app_title_info)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 🆕 v1.9.0: Autosave Section ──
            SettingsSectionHeader(text = stringResource(R.string.autosave_section))

            SettingsSwitch(
                title = stringResource(R.string.autosave_toggle),
                subtitle = stringResource(R.string.autosave_description),
                checked = autosaveEnabled,
                onCheckedChange = { viewModel.setAutosaveEnabled(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsInfoCard(
                text = stringResource(R.string.autosave_info)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DisplayModeSelector — FlowRow grid matching ThemeModeSelector / ColorThemeSelector
// Two items with preview icons (list lines vs grid squares).
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayModeSelector(currentMode: String, onModeSelected: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DisplayModeChip(
            label = stringResource(R.string.display_mode_list),
            icon = Icons.AutoMirrored.Outlined.List,
            selected = currentMode == "list",
            onClick = { onModeSelected("list") }
        )
        DisplayModeChip(
            label = stringResource(R.string.display_mode_grid),
            icon = Icons.Outlined.GridView,
            selected = currentMode == "grid",
            onClick = { onModeSelected("grid") }
        )
    }
}

@Composable
private fun DisplayModeChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GridColumnSelector + GridColumnChip — Schritt 7 (F46)
// Chip-Row for manual grid column count (1–5). Only shown when adaptive scaling
// is disabled. Visually consistent with DisplayModeChip.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridColumnSelector(currentColumns: Int, onColumnsSelected: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (count in Constants.GRID_MIN_COLUMNS..Constants.GRID_MAX_COLUMNS) {
            GridColumnChip(
                columns = count,
                selected = currentColumns == count,
                onClick = { onColumnsSelected(count) }
            )
        }
    }
}

@Composable
private fun GridColumnChip(columns: Int, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Mini-grid preview: N small squares side by side
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            Text(
                text = "$columns",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThemeModeSelector — FlowRow grid with selectable chips (matches ColorThemeSelector)
// 4 static items — FlowRow renders all at once without recycling overhead.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeModeSelector(currentMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeChip(
                mode = mode,
                selected = currentMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThemeModeChip — individual selectable theme mode chip
// Mirrors ColorThemeChip layout: color swatch + label, rounded surface.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeModeChip(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            Text(
                text = stringResource(mode.displayNameResId),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ColorThemeSelector — FlowRow with color preview chips
// FlowRow (not LazyRow/LazyGrid): 7 static items — no recycling overhead,
// all chips visible at once without horizontal scrolling.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorThemeSelector(currentTheme: ColorTheme, onThemeSelected: (ColorTheme) -> Unit) {
    val dynamicUnavailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorTheme.entries.forEach { theme ->
            val isDisabled = theme == ColorTheme.DYNAMIC && dynamicUnavailable
            ColorThemeChip(
                theme = theme,
                selected = currentTheme == theme,
                enabled = !isDisabled,
                onClick = { if (!isDisabled) onThemeSelected(theme) }
            )
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
// ColorThemeChip — individual selectable color palette chip
// Stable parameters (enum + Boolean) → Compose skips recomposition when
// neither the selection nor the enabled state has changed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColorThemeChip(theme: ColorTheme, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val chipAlpha = if (enabled) 1f else 0.38f

    Surface(
        onClick = onClick,
        modifier = Modifier
            .alpha(chipAlpha)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(theme.previewColor)
            )
            Text(
                text = stringResource(theme.displayNameResId),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
