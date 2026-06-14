package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.ui.theme.NoteColorSlot

/**
 * Bottom-sheet colour picker for note background colour.
 *
 * Shows a "None" option (clears the colour) followed by the 11 Keep-derived
 * colour swatches.  Tapping a swatch calls [onColorSelected] immediately so
 * the card preview updates in real time.  The sheet stays open until the user
 * swipes down or the host dismisses it.
 *
 * v2.5.0 (Issue #65)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteColorPickerSheet(
    currentColor: String?,
    onColorSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = Dimensions.SpacingXSmall
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.SpacingLarge)
                .navigationBarsPadding()
        ) {
            // Title
            Text(
                text = stringResource(R.string.action_set_note_color),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

            // Colour swatches in a wrapping row: "None" + 11 colours
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
            ) {
                // ── None ────────────────────────────────────────────────────
                ColorSwatch(
                    swatchColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    isSelected = currentColor == null,
                    isNone = true,
                    a11yLabel = stringResource(R.string.note_color_none),
                    onClick = { onColorSelected(null) }
                )

                // ── 11 Keep colours ─────────────────────────────────────────
                NoteColorPalette.slots.forEach { slot ->
                    ColorSwatch(
                        swatchColor = if (isDark) slot.containerColorDark else slot.containerColor,
                        isSelected = currentColor == slot.hex,
                        isNone = false,
                        a11yLabel = stringResource(slot.labelRes()),
                        onClick = { onColorSelected(slot.hex) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single circular colour swatch.
 *
 * - [isNone] = true  → surface-coloured with a border and × icon
 * - [isSelected]     → checkmark overlay, thicker selection ring
 */
@Composable
private fun ColorSwatch(
    swatchColor: Color,
    isSelected: Boolean,
    isNone: Boolean,
    a11yLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val swatchDesc = stringResource(R.string.cd_note_color_swatch, a11yLabel)

    Box(
        modifier = modifier
            .size(Dimensions.MinTouchTarget)
            .clip(CircleShape)
            .background(swatchColor, CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .semantics {
                contentDescription = swatchDesc
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isSelected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isNone) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Black.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(Dimensions.IconSizeMedium)
            )
            isNone -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.IconSizeMedium)
            )
        }
    }
}

/** Maps each [NoteColorSlot] to its string-resource id for display. */
private fun NoteColorSlot.labelRes(): Int =
    when (hex) {
        "#F28B82" -> R.string.note_color_red
        "#FBBC04" -> R.string.note_color_orange
        "#FFF475" -> R.string.note_color_yellow
        "#CCFF90" -> R.string.note_color_green
        "#A7FFEB" -> R.string.note_color_teal
        "#CBF0F8" -> R.string.note_color_blue
        "#AECBFA" -> R.string.note_color_dark_blue
        "#D7AEFB" -> R.string.note_color_purple
        "#FDCFE8" -> R.string.note_color_pink
        "#E6C9A8" -> R.string.note_color_brown
        else -> R.string.note_color_gray // "#E8EAED"
    }
