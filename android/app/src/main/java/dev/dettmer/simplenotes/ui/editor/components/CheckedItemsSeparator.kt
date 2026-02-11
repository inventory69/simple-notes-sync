package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R

/**
 * 🆕 v1.8.0 (IMPL_017): Visueller Separator zwischen unchecked und checked Items
 * 🆕 v1.8.1 (IMPL_14): Drag-Awareness — Primary-Farbe während Drag als visueller Hinweis
 *
 * Zeigt eine dezente Linie mit Anzahl der erledigten Items:
 * ── 3 completed ──
 */
@Composable
fun CheckedItemsSeparator(
    checkedCount: Int,
    modifier: Modifier = Modifier,
    isDragActive: Boolean = false  // 🆕 v1.8.1 IMPL_14
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDragActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = pluralStringResource(
                R.plurals.checked_items_count,
                checkedCount,
                checkedCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isDragActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isDragActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outlineVariant
        )
    }
}
