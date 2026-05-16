package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.ui.theme.NoteColorSlot

/**
 * v1.9.0: FilterChip-Zeile + Suchfeld für die Notizliste.
 * v2.0.0: Sort-Button als gleichwertiger FilterChip, zentrierte Labels, responsive Layout.
 * v2.5.0: Text/Listen-Chips auf Icon-only umgestellt; Farbfilter-Chip mit Dropdown ergänzt.
 */
@Composable
fun FilterChipRow(
    currentFilter: NoteFilter,
    onFilterSelected: (NoteFilter) -> Unit,
    currentColorFilter: String?,
    onColorFilterSelected: (String?) -> Unit,
    availableColors: Map<String?, Int>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showColorDropdown by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Sort-Chip — Icon-only, immer unausgewählt
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = false,
                onClick = {
                    focusManager.clearFocus()
                    onSortClick()
                },
                label = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.sort_notes),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            // 2. Alle-Chip — Text bleibt (einziger Chip mit Label)
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.ALL,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.ALL)
                },
                label = {
                    Text(
                        text = stringResource(R.string.filter_all),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            // 3. Text-Chip — 🆕 v2.5.0: Icon-only (Notes-Icon, kein leadingIcon mehr)
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.TEXT_ONLY,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.TEXT_ONLY)
                },
                label = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Notes,
                            contentDescription = stringResource(R.string.filter_text_only),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            // 4. Listen-Chip — 🆕 v2.5.0: Icon-only (Checklist-Icon, kein leadingIcon mehr)
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.CHECKLIST_ONLY,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.CHECKLIST_ONLY)
                },
                label = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = stringResource(R.string.filter_checklist_only),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            // 5. 🆕 v2.5.0: Farbfilter-Chip — Palette-Icon, öffnet ColorFilterDropdown
            // Box als Anchor für DropdownMenu (weight auf Box, nicht auf FilterChip)
            Box(modifier = Modifier.weight(1f)) {
                FilterChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = currentColorFilter != null,
                    onClick = {
                        focusManager.clearFocus()
                        showColorDropdown = true
                    },
                    label = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = stringResource(R.string.filter_color),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
                ColorFilterDropdown(
                    expanded = showColorDropdown,
                    onDismiss = { showColorDropdown = false },
                    currentColorFilter = currentColorFilter,
                    onColorSelected = { hex ->
                        onColorFilterSelected(hex)
                        showColorDropdown = false
                    },
                    availableColors = availableColors,
                    isDark = isDark
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Suchfeld — volle Breite (unverändert gegenüber v2.0.0)
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        val chipShape = MaterialTheme.shapes.small
        val borderColor =
            if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Surface(
                    shape = chipShape,
                    border = BorderStroke(width = 1.dp, color = borderColor),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_notes_placeholder),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onSearchQueryChanged("") }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * 🆕 v2.5.0: Dropdown-Menü für den Farbfilter.
 * Zeigt nur Farben aus [NoteColorPalette.slots] an, die in [availableColors] mit count > 0
 * vorhanden sind (nach aktivem Typ-Filter, vor Farbfilter).
 * "Alle Farben" (Filter aufheben) ist immer die erste Option.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorFilterDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    currentColorFilter: String?,
    onColorSelected: (String?) -> Unit,
    availableColors: Map<String?, Int>,
    isDark: Boolean
) {
    val slotsWithNotes = NoteColorPalette.slots.filter { slot ->
        (availableColors[slot.hex] ?: 0) > 0
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        // "Alle Farben" — hebt den Farbfilter auf
        DropdownMenuItem(
            text = { Text(stringResource(R.string.filter_color_all)) },
            onClick = { onColorSelected(null) },
            leadingIcon = if (currentColorFilter == null) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                null
            }
        )

        if (slotsWithNotes.isNotEmpty()) {
            // Dimensions.SpacingSmall (4.dp) für vertikalen Abstand um den Divider (§10.3)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            // Dimensions.SpacingMediumLarge (12.dp) horizontal, SpacingSmall (4.dp) vertikal (§10.3)
            Box(
                modifier = Modifier.padding(
                    horizontal = Dimensions.SpacingMediumLarge,
                    vertical = Dimensions.SpacingSmall
                )
            ) {
                // Dimensions.SpacingMedium (8.dp) für FlowRow-Abstände (§10.3)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
                ) {
                    slotsWithNotes.forEach { slot ->
                        ColorFilterSwatch(
                            slot = slot,
                            count = availableColors[slot.hex] ?: 0,
                            isSelected = currentColorFilter == slot.hex,
                            isDark = isDark,
                            onClick = { onColorSelected(slot.hex) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 🆕 v2.5.0: Einzelne Farb-Swatch im Dropdown.
 * Zeigt Farbkreis (28dp, kontextspezifisch) + Notizanzahl darunter.
 * Ausgewählt: primärer Rahmen (2dp) + Häkchen-Icon.
 */
@Composable
private fun ColorFilterSwatch(
    slot: NoteColorSlot,
    count: Int,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = if (isDark) slot.containerColorDark else slot.containerColor
    // Dimensions.SpacingSmall (4.dp) als Padding in der Column (§10.3)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(Dimensions.SpacingSmall)
    ) {
        Box(
            modifier = Modifier
                // 28.dp: kontextspezifischer Swatch-Durchmesser (kein Dimensions-Token)
                .size(28.dp)
                .clip(CircleShape)
                .background(swatchColor)
                // 2dp aktiv / 1dp inaktiv: kontextspezifische visuelle Abstufung
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    // 14.dp: Icon muss kleiner als 28dp-Kreis bleiben, kontextspezifisch
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        // Dimensions.SpacingXSmall (2.dp) Abstand zwischen Kreis und Zahl (§10.3)
        Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
