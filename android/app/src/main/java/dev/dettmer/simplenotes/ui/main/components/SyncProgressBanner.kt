package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Info
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
    val isInfo = progress.phase == SyncPhase.INFO
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isInfo -> MaterialTheme.colorScheme.secondaryContainer  // 🆕 v1.8.1 (IMPL_12)
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "bannerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            isInfo -> MaterialTheme.colorScheme.onSecondaryContainer  // 🆕 v1.8.1 (IMPL_12)
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        label = "bannerContentColor"
    )
    
    AnimatedVisibility(
        visible = progress.isVisible,
        // 🆕 v1.10.0: Nur fadeIn beim Einblenden – kein expandVertically, das von oben reinschieben würde
        enter = fadeIn(
            animationSpec = tween(durationMillis = 350, easing = EaseOutCubic)
        ),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 280, easing = EaseInCubic)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 220, easing = EaseInCubic)
        ),
        modifier = modifier
    ) {
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🆕 v1.10.0: AnimatedContent crossfadet Inhalte beim Phasenwechsel, damit
            // kurze Phasen (z.B. IMPORTING_MARKDOWN) leserlich übergeblendet werden
            AnimatedContent(
                targetState = progress,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 220, easing = EaseOutCubic)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 160, easing = EaseInCubic))
                },
                contentKey = { it.phase },
                label = "bannerContent"
            ) { p ->
                val pIsError = p.phase == SyncPhase.ERROR
                val pIsCompleted = p.phase == SyncPhase.COMPLETED
                val pIsInfo = p.phase == SyncPhase.INFO
                val pIsResult = pIsError || pIsCompleted || pIsInfo

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
                        // Icon: Spinner (aktiv), Checkmark (completed), Error (error), Info (info)
                        when {
                            pIsCompleted -> {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = contentColor
                                )
                            }
                            pIsInfo -> {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = contentColor
                                )
                            }
                            pIsError -> {
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
                                pIsResult && !p.resultMessage.isNullOrBlank() -> p.resultMessage
                                else -> phaseToString(p.phase)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Counter: x/y bei Uploads (Total bekannt), nur Zähler bei Downloads
                        if (!pIsResult && p.current > 0) {
                            Text(
                                text = if (p.total > 0) {
                                    "${p.current}/${p.total}"
                                } else {
                                    "${p.current}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Zeile 2: Progress Bar (nur bei Upload mit bekanntem Total)
                    if (!pIsResult && p.total > 0 && p.current > 0 &&
                        p.phase == SyncPhase.UPLOADING) {
                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { p.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.2f)
                        )
                    }

                    // Zeile 3: Aktueller Notiz-Titel (optional, nur bei aktivem Sync)
                    if (!pIsResult && !p.currentFileName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = p.currentFileName,
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
        SyncPhase.INFO -> ""  // 🆕 v1.8.1 (IMPL_12): INFO nutzt immer resultMessage
    }
}
