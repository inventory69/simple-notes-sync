package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dettmer.simplenotes.models.NoteType

/**
 * FAB with dropdown menu for note type selection
 * v1.5.0: PERFORMANCE FIX - No Box wrapper for proper elevation
 * 
 * Uses consistent icons with NoteCard:
 * - TEXT: Description (document icon)
 * - CHECKLIST: List (bullet list icon)
 */
@Composable
fun NoteTypeFAB(
    modifier: Modifier = Modifier,
    onCreateNote: (NoteType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // FAB directly without Box wrapper - elevation works correctly
    FloatingActionButton(
        onClick = { expanded = true },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Neue Notiz"
        )
        
        // Dropdown inside FAB - renders as popup overlay
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Text-Notiz") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    expanded = false
                    onCreateNote(NoteType.TEXT)
                }
            )
            DropdownMenuItem(
                text = { Text("Checkliste") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    expanded = false
                    onCreateNote(NoteType.CHECKLIST)
                }
            )
        }
    }
}
