package dev.dettmer.simplenotes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.toReadableTime

// Höhe pro Block: hoch genug für ein paar Zeilen, niedrig genug, dass beide Fassungen
// gleichzeitig auf den Schirm passen — genau darum geht es beim Vergleichen.
private val BlockMaxHeight = 160.dp

/**
 * 🆕 v2.16.0: Die beiden Fassungen eines Konflikts nebeneinander (genauer: untereinander).
 *
 * Ohne sie fällt die Entscheidung im Banner blind — "Server-Fassung nehmen" ist keine
 * Vorschau, sondern schreibt sofort in den Storage. Der Zeitstempel steht bewusst an jedem
 * Block: meist ist er der Grund, warum man sich für eine Seite entscheidet.
 *
 * Die Server-Fassung wird beim Öffnen geholt und beim Schließen verworfen — sie liegt nur
 * im UI-State ([ConflictVersions]), nie im Storage.
 */
@Composable
fun ConflictCompareDialog(
    versions: ConflictVersions,
    onKeepLocal: () -> Unit,
    onUseServer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conflict_compare_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
                VersionBlock(stringResource(R.string.conflict_compare_local), versions.local)
                VersionBlock(stringResource(R.string.conflict_compare_server), versions.server)
            }
        },
        confirmButton = {
            TextButton(onClick = onUseServer) {
                Text(stringResource(R.string.conflict_action_use_server))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepLocal) {
                Text(stringResource(R.string.conflict_action_keep_local))
            }
        }
    )
}

@Composable
private fun VersionBlock(label: String, note: Note) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = note.updatedAt.toReadableTime(context),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SpacingSmall),
            shape = RoundedCornerShape(Dimensions.SpacingMedium),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = BlockMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimensions.SpacingMediumLarge)
            ) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = note.content.ifBlank { stringResource(R.string.conflict_compare_empty) },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimensions.SpacingSmall)
                )
            }
        }
    }
}
