@file:Suppress("MatchingDeclarationName")

package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * Data class for radio option
 */
data class RadioOption<T>(val value: T, val title: String, val subtitle: String? = null)

/**
 * Settings radio group for selecting one option
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun <T> SettingsRadioGroup(
    options: List<RadioOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    enabled: Boolean = true
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = titleColor,
                modifier = Modifier.padding(horizontal = Dimensions.SpacingLarge, vertical = Dimensions.SpacingMedium)
            )
        }

        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.value == selectedValue,
                        enabled = enabled,
                        onClick = { onValueSelected(option.value) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = Dimensions.SpacingLarge, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option.value == selectedValue,
                    onClick = null,
                    enabled = enabled
                )

                Column(
                    modifier = Modifier
                        .padding(start = Dimensions.SpacingLarge)
                        .weight(1f)
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = labelColor
                    )
                    if (option.subtitle != null) {
                        Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            }
        }
    }
}
