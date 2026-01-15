package dev.dettmer.simplenotes.ui.editor

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FOSS Drag & Drop State für LazyList
 * 
 * Native Compose-Implementierung ohne externe Dependencies
 * v1.5.0: NoteEditor Redesign
 */
class DragDropListState(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableFloatStateOf(0f)
    private var overscrollJob by mutableStateOf<Job?>(null)

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    fun onDragStart(offset: Offset, itemIndex: Int) {
        draggingItemIndex = itemIndex
        draggingItemInitialOffset = draggingItemLayoutInfo?.offset?.toFloat() ?: 0f
        draggingItemDraggedDelta = 0f
    }

    fun onDragInterrupted() {
        draggingItemDraggedDelta = 0f
        draggingItemIndex = null
        draggingItemInitialOffset = 0f
        overscrollJob?.cancel()
    }

    fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size

        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..item.offsetEnd &&
                draggingItem.index != item.index
        }

        if (targetItem != null) {
            val scrollToIndex = if (targetItem.index == state.firstVisibleItemIndex) {
                draggingItem.index
            } else if (draggingItem.index == state.firstVisibleItemIndex) {
                targetItem.index
            } else {
                null
            }
            
            if (scrollToIndex != null) {
                scope.launch {
                    state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
                    onMove(draggingItem.index, targetItem.index)
                }
            } else {
                onMove(draggingItem.index, targetItem.index)
            }
            
            draggingItemIndex = targetItem.index
        } else {
            val overscroll = when {
                draggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }

            if (overscroll != 0f) {
                if (overscrollJob?.isActive != true) {
                    overscrollJob = scope.launch {
                        state.scrollBy(overscroll)
                    }
                }
            } else {
                overscrollJob?.cancel()
            }
        }
    }

    private val LazyListItemInfo.offsetEnd: Int
        get() = this.offset + this.size
}

@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState,
    scope: CoroutineScope,
    onMove: (Int, Int) -> Unit
): DragDropListState {
    return remember(lazyListState, scope) {
        DragDropListState(
            state = lazyListState,
            scope = scope,
            onMove = onMove
        )
    }
}

fun Modifier.dragContainer(
    dragDropState: DragDropListState,
    itemIndex: Int
): Modifier {
    return this.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragDropState.onDragStart(offset, itemIndex)
            },
            onDragEnd = {
                dragDropState.onDragInterrupted()
            },
            onDragCancel = {
                dragDropState.onDragInterrupted()
            },
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset)
            }
        )
    }
}
