package dev.dettmer.simplenotes.ui.editor

import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🆕 v1.9.1: Undo/Redo manager for the note editor.
 *
 * Maintains two stacks (undo and redo) of [EditorSnapshot]s.
 * - [pushUndo]: Captures state before an edit; clears the redo stack.
 * - [undo]: Restores the last snapshot from the undo stack (pushing current state to redo).
 * - [redo]: Re-applies the last undone change (pushing current state back to undo).
 * - [clear]: Resets both stacks (called on note load to avoid cross-note undo).
 *
 * Stack size is bounded by [Constants.UNDO_STACK_MAX_SIZE].
 */
class UndoRedoManager {

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /**
     * Push [snapshot] onto the undo stack.
     * Deduplicates consecutive identical snapshots (e.g. hydration calls).
     * Clears the redo stack on every new user edit.
     */
    fun pushUndo(snapshot: EditorSnapshot) {
        if (undoStack.lastOrNull() == snapshot) return  // Deduplicate
        undoStack.addLast(snapshot)
        if (undoStack.size > Constants.UNDO_STACK_MAX_SIZE) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        updateCanFlags()
    }

    /**
     * Undo the last edit.
     * Saves [currentState] to the redo stack and returns the previous snapshot,
     * or null if nothing to undo.
     */
    fun undo(currentState: EditorSnapshot): EditorSnapshot? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentState)
        val snapshot = undoStack.removeLast()
        updateCanFlags()
        return snapshot
    }

    /**
     * Redo the last undone edit.
     * Saves [currentState] back to the undo stack and returns the re-applied snapshot,
     * or null if nothing to redo.
     */
    fun redo(currentState: EditorSnapshot): EditorSnapshot? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentState)
        val snapshot = redoStack.removeLast()
        updateCanFlags()
        return snapshot
    }

    /** Clear both stacks — call when loading a new note to prevent cross-note undo. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateCanFlags()
    }

    private fun updateCanFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}

/**
 * Immutable snapshot of the note editor state at a point in time.
 * Used as the unit stored in [UndoRedoManager]'s stacks.
 */
data class EditorSnapshot(
    val title: String,
    val content: String,
    val checklistItems: List<ChecklistItemState>
)
