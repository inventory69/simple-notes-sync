package dev.dettmer.simplenotes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * 🆕 v2.16.0: Auflösung eines Sync-Konflikts, direkt über der Notiz.
 *
 * Der Editor ist die einzige Stelle, an der das Banner richtig sitzt: Das Warn-Icon in der
 * Liste bringt den Nutzer genau hierher, und ohne die Entscheidung führt jede weitere
 * Bearbeitung nur zurück in denselben Konflikt — der Upload läuft mit `If-Match` erneut
 * auf `412`.
 *
 * "Vergleichen" holt die Server-Fassung und stellt sie der lokalen gegenüber
 * ([ConflictCompareDialog]) — ohne sie fiele die Entscheidung blind, denn "Server-Fassung
 * nehmen" ist keine Vorschau, sondern schreibt sofort.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConflictBanner(
    onKeepLocal: () -> Unit,
    onUseServer: () -> Unit,
    onCompare: () -> Unit,
    compareEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.SpacingMediumLarge),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null, // Titel daneben sagt dasselbe
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.conflict_banner_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = stringResource(R.string.conflict_banner_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimensions.SpacingSmall)
            )
            // FlowRow statt Row: "Server-Fassung nehmen" ist in mehreren Sprachen lang genug,
            // dass drei Buttons auf schmalen Geräten sonst abgeschnitten würden.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimensions.SpacingSmall),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCompare, enabled = compareEnabled) {
                    Text(stringResource(R.string.conflict_action_compare))
                }
                TextButton(onClick = onUseServer) {
                    Text(stringResource(R.string.conflict_action_use_server))
                }
                TextButton(onClick = onKeepLocal) {
                    Text(stringResource(R.string.conflict_action_keep_local))
                }
            }
        }
    }
}
