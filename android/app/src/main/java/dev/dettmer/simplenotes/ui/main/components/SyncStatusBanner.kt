package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.SyncStateManager

/**
 * Sync status banner shown below the toolbar during sync
 * v1.5.0: Jetpack Compose MainActivity Redesign
 * v1.5.0: SYNCING_SILENT ignorieren - Banner nur bei manuellen Syncs oder Fehlern anzeigen
 * v1.8.0: Nur noch COMPLETED/ERROR States - SYNCING wird von SyncProgressBanner übernommen
 */
@Composable
fun SyncStatusBanner(
    syncState: SyncStateManager.SyncState,
    message: String?,
    modifier: Modifier = Modifier
) {
    // v1.8.0: Nur COMPLETED/ERROR anzeigen (SYNCING wird von SyncProgressBanner übernommen)
    // IDLE und SYNCING_SILENT werden ignoriert
    val isVisible = syncState == SyncStateManager.SyncState.COMPLETED 
                    || syncState == SyncStateManager.SyncState.ERROR
    
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // v1.8.0: Kein Loading-Icon mehr - wird von SyncProgressBanner übernommen
            
            Text(
                text = when (syncState) {
                    SyncStateManager.SyncState.COMPLETED -> message ?: stringResource(R.string.sync_status_completed)
                    SyncStateManager.SyncState.ERROR -> message ?: stringResource(R.string.sync_status_error)
                    else -> "" // SYNCING/IDLE/SYNCING_SILENT nicht mehr relevant
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
