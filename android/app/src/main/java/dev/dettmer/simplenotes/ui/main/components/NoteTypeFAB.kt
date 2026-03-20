package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType

/**
 * Expandable FAB with animated sub-actions (Breezy Weather style).
 * v1.10.0-P2: Replaces DropdownMenu-based FAB with expanding mini-FABs.
 * v1.11.0-P3: Semi-transparent animated scrim, improved colors.
 * v1.11.0-P4: Entire sub-action row is clickable (label + icon as one unit, Aegis style).
 *
 * When collapsed: Standard FAB with + icon.
 * When expanded: + rotates to ×, sub-action rows slide up with staggered animation.
 *                Semi-transparent scrim covers entire screen (incl. status bar).
 */
@Composable
fun NoteTypeFAB(modifier: Modifier = Modifier, onCreateNote: (NoteType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var navigating by remember { mutableStateOf(false) }

    // Main FAB icon rotation: 0° → 45° (+ becomes ×)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_rotation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 🆕 v1.11.0: Semi-transparent scrim overlay — smooth animated, fullscreen inkl. Statusbar
        // Orientiert an Aegis Authenticator: dunkler Scrim hinter dem geöffneten FAB-Menü
        // v2.0.0: Animatable statt animateFloatAsState — snapTo(0f) beim Schließen verhindert
        // Scrim-Artefakte im Activity-Transition-Snapshot.
        val scrimAlpha = remember { Animatable(0f) }
        LaunchedEffect(expanded) {
            if (expanded) {
                navigating = false
                scrimAlpha.animateTo(1f, animationSpec = tween(durationMillis = 250))
            } else if (navigating) {
                // ON_STOP → Activity abgedeckt → sofort unsichtbar
                scrimAlpha.snapTo(0f)
            } else {
                // Scrim-Tap oder FAB-Toggle → sanft ausblenden
                scrimAlpha.animateTo(0f, animationSpec = tween(durationMillis = 200))
            }
        }
        // Scrim aufräumen wenn Activity stoppt (Editor-Window deckt Screen bereits ab)
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            if (expanded) {
                navigating = true
                expanded = false
            }
        }
        if (expanded || scrimAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(color = Color.Black.copy(alpha = 0.5f * scrimAlpha.value))
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        // FAB column: sub-actions above main FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sub-action items (only when expanded)
            val items = listOf(
                FabSubAction(
                    label = stringResource(R.string.fab_text_note),
                    icon = Icons.Outlined.Description,
                    noteType = NoteType.TEXT
                ),
                FabSubAction(
                    label = stringResource(R.string.fab_checklist),
                    icon = Icons.AutoMirrored.Outlined.List,
                    noteType = NoteType.CHECKLIST
                )
            )

            items.forEachIndexed { index, action ->
                // Staggered animation: each item appears slightly after the previous
                val delayMs = index * 50
                val animatedScale by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = delayMs
                    ),
                    label = "sub_fab_scale_$index"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (expanded) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 200,
                        delayMillis = delayMs
                    ),
                    label = "sub_fab_alpha_$index"
                )

                if (expanded || animatedScale > 0f) {
                    FabSubActionRow(
                        label = action.label,
                        icon = action.icon,
                        scale = animatedScale,
                        alpha = animatedAlpha,
                        onClick = {
                            onCreateNote(action.noteType)
                        }
                    )
                }
            }

            // Main FAB
            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // 🆕 v1.10.0-P2: Stronger shadow so FAB floats clearly above note cards
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp,
                    hoveredElevation = 10.dp,
                    focusedElevation = 10.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (expanded) {
                        stringResource(R.string.fab_close)
                    } else {
                        stringResource(R.string.fab_new_note)
                    },
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}

/**
 * Eine breite klickbare Pill — Icon + Text in einer Surface (Aegis Authenticator style).
 * 🆕 v1.11.0-P4: Kein separates FAB-Icon, alles in einer einzigen Pill.
 */
@Composable
private fun FabSubActionRow(label: String, icon: ImageVector, scale: Float, alpha: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Data class for sub-action configuration.
 */
private data class FabSubAction(val label: String, val icon: ImageVector, val noteType: NoteType)
