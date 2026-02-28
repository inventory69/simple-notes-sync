package dev.dettmer.simplenotes.ui.main.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType

/**
 * Expandable FAB with animated sub-actions (Breezy Weather style).
 * v1.10.0-P2: Replaces DropdownMenu-based FAB with expanding mini-FABs.
 *
 * When collapsed: Standard FAB with + icon.
 * When expanded: + rotates to ×, mini-FABs slide up with staggered animation.
 */
@Composable
fun NoteTypeFAB(
    modifier: Modifier = Modifier,
    onCreateNote: (NoteType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
        // Transparent dismiss overlay — no visual change, but catches taps outside FAB
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                .padding(16.dp),
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
                            expanded = false
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
 * A single sub-action row: label text + small FAB icon.
 */
@Composable
private fun FabSubActionRow(
    label: String,
    icon: ImageVector,
    scale: Float,
    alpha: Float,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    ) {
        // Label pill
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 🆕 v1.10.0-P2: Standard FAB (56dp) — larger than SmallFAB, proportional icon
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp,
                hoveredElevation = 10.dp,
                focusedElevation = 10.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label
            )
        }
    }
}

/**
 * Data class for sub-action configuration.
 */
private data class FabSubAction(
    val label: String,
    val icon: ImageVector,
    val noteType: NoteType
)
