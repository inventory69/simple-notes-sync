package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.SyncPhase
import dev.dettmer.simplenotes.sync.SyncProgress

/**
 * 🆕 v1.8.0: Einziges Sync-Banner für den gesamten Sync-Lebenszyklus
 * 
 * Deckt alle Phasen ab:
 * - PREPARING: Indeterminate Spinner + "Synchronisiere…" (sofort beim Klick, bleibt bis echte Arbeit)
 * - UPLOADING / DOWNLOADING / IMPORTING_MARKDOWN: Nur bei echten Aktionen
 * - COMPLETED: Erfolgsmeldung mit Checkmark-Icon (auto-hide durch ComposeMainActivity)
 * - ERROR: Fehlermeldung mit Error-Icon (auto-hide durch ComposeMainActivity)
 * 
 * Silent Syncs (onResume) zeigen kein Banner (progress.isVisible == false)
 */
@Composable
fun SyncProgressBanner(
    progress: SyncProgress,
    modifier: Modifier = Modifier
) {
    // Farbe animiert wechseln je nach State
    val isError = progress.phase == SyncPhase.ERROR
    val isCompleted = progress.phase == SyncPhase.COMPLETED
    val isResult = isError || isCompleted
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "bannerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "bannerContentColor"
    )
    
    AnimatedVisibility(
        visible = progress.isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Zeile 1: Icon + Phase/Message + Counter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Icon: Spinner (aktiv), Checkmark (completed), Error (error)
                    when {
                        isCompleted -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = contentColor
                            )
                        }
                        isError -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = contentColor
                            )
                        }
                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = contentColor
                            )
                        }
                    }
                    
                    // Text: Ergebnisnachricht oder Phase
                    Text(
                        text = when {
                            isResult && !progress.resultMessage.isNullOrBlank() -> progress.resultMessage
                            else -> phaseToString(progress.phase)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Counter: x/y bei Uploads (Total bekannt), nur Zähler bei Downloads
                    if (!isResult && progress.current > 0) {
                        Text(
                            text = if (progress.total > 0) {
                                "${progress.current}/${progress.total}"
                            } else {
                                "${progress.current}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Zeile 2: Progress Bar (nur bei Upload mit bekanntem Total)
                if (!isResult && progress.total > 0 && progress.current > 0 &&
                    progress.phase == SyncPhase.UPLOADING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.2f)
                    )
                }
                
                // Zeile 3: Aktueller Notiz-Titel (optional, nur bei aktivem Sync)
                if (!isResult && !progress.currentFileName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Konvertiert SyncPhase zu lokalisierten String
 */
@Composable
private fun phaseToString(phase: SyncPhase): String {
    return when (phase) {
        SyncPhase.IDLE -> ""
        SyncPhase.PREPARING -> stringResource(R.string.sync_phase_preparing)
        SyncPhase.UPLOADING -> stringResource(R.string.sync_phase_uploading)
        SyncPhase.DOWNLOADING -> stringResource(R.string.sync_phase_downloading)
        SyncPhase.IMPORTING_MARKDOWN -> stringResource(R.string.sync_phase_importing_markdown)
        SyncPhase.COMPLETED -> stringResource(R.string.sync_phase_completed)
        SyncPhase.ERROR -> stringResource(R.string.sync_phase_error)
    }
}
