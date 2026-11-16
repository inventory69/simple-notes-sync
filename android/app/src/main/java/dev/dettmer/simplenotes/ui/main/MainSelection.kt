package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.models.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Mehrfachauswahl der Hauptliste (v1.5.0, Ordner seit v2.7.0). [visibleNotes] und
 * [selectableFolders] liefern, was „alles auswählen" in der aktuellen Ansicht trifft.
 */
class MainSelection(
    scope: CoroutineScope,
    private val visibleNotes: () -> List<Note>,
    private val selectableFolders: () -> Set<String>
) {
    private val _selectedNotes = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> =
        combine(_selectedNotes, _selectedFolders) { n, f -> n.isNotEmpty() || f.isNotEmpty() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleNoteSelection(noteId: String) {
        _selectedNotes.update { if (noteId in it) it - noteId else it + noteId }
    }

    fun startSelectionMode(noteId: String) {
        _selectedNotes.value = setOf(noteId)
    }

    /** Alles auswählen: Notizen der aktuellen Ansicht, im Root auch die Ordner. */
    fun selectAll() {
        _selectedNotes.value = visibleNotes().map { it.id }.toSet()
        _selectedFolders.value = selectableFolders()
    }

    fun toggleFolderSelection(name: String) {
        _selectedFolders.update { if (name in it) it - name else it + name }
    }

    /** Long-Press auf einen Ordner. */
    fun startSelectionWithFolder(name: String) {
        _selectedNotes.value = emptySet()
        _selectedFolders.value = setOf(name)
    }

    fun clearSelection() {
        _selectedNotes.value = emptySet()
        _selectedFolders.value = emptySet()
    }
}
