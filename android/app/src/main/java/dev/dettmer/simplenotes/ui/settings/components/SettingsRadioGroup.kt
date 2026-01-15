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

/**
 * Data class for radio option
 */
data class RadioOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null
)

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
    title: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.value == selectedValue,
                        onClick = { onValueSelected(option.value) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option.value == selectedValue,
                    onClick = null // handled by selectable
                )
                
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (option.subtitle != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
