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
import androidx.compose.runtime.rememberUpdatedState
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
 * v1.8.0: IMPL_023 - Drag & Drop Fix (pointerInput key + Handle-only drag)
 * v1.8.0: IMPL_023b - Flicker-Fix (Straddle-Target-Center-Erkennung statt Mittelpunkt)
 * v1.8.1: IMPL_14 - Separator als eigenes Item, Cross-Boundary-Drag mit Auto-Toggle
 */
class DragDropListState(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    // 🆕 v1.8.2 (IMPL_11): Drag gilt erst als bestätigt nach erstem onDrag-Callback.
    // Verhindert visuellen Glitch beim schnellen Scrollen (onDragStart → onDragCancel
    // ohne onDrag dazwischen → kurzzeitiger Drag-State sichtbar).
    var isDragConfirmed by mutableStateOf(false)
        private set

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableFloatStateOf(0f)
    // 🆕 v1.8.1: Item-Größe beim Drag-Start fixieren
    // Verhindert dass Höhenänderungen die Swap-Erkennung destabilisieren
    private var draggingItemSize by mutableStateOf(0)
    private var overscrollJob by mutableStateOf<Job?>(null)

    // 🆕 v1.8.1 IMPL_14: Visual-Index des Separators (-1 = kein Separator)
    var separatorVisualIndex by mutableStateOf(-1)

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    /**
     * 🆕 v1.8.1 IMPL_14: Visual-Index → Data-Index Konvertierung.
     * Wenn ein Separator existiert, sind alle Items nach dem Separator um 1 verschoben.
     */
    fun visualToDataIndex(visualIndex: Int): Int {
        if (separatorVisualIndex < 0) return visualIndex
        return if (visualIndex > separatorVisualIndex) visualIndex - 1 else visualIndex
    }

    /**
     * 🆕 v1.8.1 IMPL_14: Data-Index → Visual-Index Konvertierung.
     */
    fun dataToVisualIndex(dataIndex: Int): Int {
        if (separatorVisualIndex < 0) return dataIndex
        return if (dataIndex >= separatorVisualIndex) dataIndex + 1 else dataIndex
    }

    fun onDragStart(offset: Offset, itemIndex: Int) {
        draggingItemIndex = itemIndex
        isDragConfirmed = false  // 🆕 v1.8.2 (IMPL_11): Noch nicht bestätigt
        val info = draggingItemLayoutInfo
        draggingItemInitialOffset = info?.offset?.toFloat() ?: 0f
        draggingItemSize = info?.size ?: 0
        draggingItemDraggedDelta = 0f
    }

    fun onDragInterrupted() {
        draggingItemDraggedDelta = 0f
        draggingItemIndex = null
        isDragConfirmed = false  // 🆕 v1.8.2 (IMPL_11): Reset
        draggingItemInitialOffset = 0f
        draggingItemSize = 0
        overscrollJob?.cancel()
    }

    fun onDrag(offset: Offset) {
        isDragConfirmed = true  // 🆕 v1.8.2 (IMPL_11): Erster Drag-Callback → bestätigt
        draggingItemDraggedDelta += offset.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        // 🆕 v1.8.1: Fixierte Item-Größe für stabile Swap-Erkennung
        val endOffset = startOffset + draggingItemSize

        // 🆕 v1.8.0: IMPL_023b — Straddle-Target-Center + Adjazenz-Filter
        // Statt den Mittelpunkt des gezogenen Items zu prüfen ("liegt mein Zentrum im Target?"),
        // wird geprüft ob das gezogene Item den MITTELPUNKT des Targets überspannt.
        // Dies verhindert Oszillation bei Items unterschiedlicher Größe.
        // 🆕 v1.8.1 IMPL_14: Separator überspringen, Adjazenz berücksichtigt Separator-Lücke
        val targetItem = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            // Separator überspringen
            item.index != separatorVisualIndex &&
            // Nur adjazente Items (Separator-Lücke wird übersprungen)
            isAdjacentSkippingSeparator(draggingItem.index, item.index) &&
                run {
                    val targetCenter = item.offset + item.size / 2
                    startOffset < targetCenter && endOffset > targetCenter
                }
        }

        if (targetItem != null) {
            // 🆕 v1.8.2 (IMPL_26): Kein Scroll bei Cross-Separator-Swap.
            // Wenn ein Item über den Separator gezogen wird, ändert sich das Layout erheblich
            // (Separator-Position verschiebt sich, Items werden umgeordnet). Der asynchrone
            // scrollToItem-Pfad verzögert onMove/draggingItemIndex-Update und scrollt den
            // Viewport weg vom Drag-Punkt → Drag bricht ab (draggingItemLayoutInfo = null).
            val crossesSeparator = separatorVisualIndex >= 0 &&
                (draggingItem.index < separatorVisualIndex) !=
                (targetItem.index < separatorVisualIndex)

            val scrollToIndex = if (crossesSeparator) {
                null
            } else if (targetItem.index == state.firstVisibleItemIndex) {
                draggingItem.index
            } else if (draggingItem.index == state.firstVisibleItemIndex) {
                targetItem.index
            } else {
                null
            }

            // 🆕 v1.8.1 IMPL_14: Visual-Indizes zu Data-Indizes konvertieren für onMove
            val fromDataIndex = visualToDataIndex(draggingItem.index)
            val toDataIndex = visualToDataIndex(targetItem.index)
            
            if (scrollToIndex != null) {
                scope.launch {
                    state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
                    onMove(fromDataIndex, toDataIndex)
                    // 🆕 v1.8.0: IMPL_023b — Index-Update NACH dem Move (verhindert Race-Condition)
                    draggingItemIndex = targetItem.index
                }
            } else {
                onMove(fromDataIndex, toDataIndex)
                draggingItemIndex = targetItem.index
            }
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

    /**
     * 🆕 v1.8.1 IMPL_14: Prüft ob zwei Visual-Indizes adjazent sind,
     * wobei der Separator übersprungen wird.
     * Beispiel: Items bei Visual 1 und Visual 3 sind adjazent wenn Separator bei Visual 2 liegt.
     */
    private fun isAdjacentSkippingSeparator(indexA: Int, indexB: Int): Boolean {
        val diff = kotlin.math.abs(indexA - indexB)
        if (diff == 1) {
            // Direkt benachbart — aber NICHT wenn der Separator dazwischen liegt
            val between = minOf(indexA, indexB) + 1
            return between != separatorVisualIndex || separatorVisualIndex < 0
        }
        if (diff == 2 && separatorVisualIndex >= 0) {
            // 2 Positionen entfernt — adjazent wenn Separator dazwischen
            val between = minOf(indexA, indexB) + 1
            return between == separatorVisualIndex
        }
        return false
    }
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

@Composable
fun Modifier.dragContainer(
    dragDropState: DragDropListState,
    itemIndex: Int
): Modifier {
    val currentIndex = rememberUpdatedState(itemIndex)  // 🆕 v1.8.0: rememberUpdatedState statt Key
    return this.pointerInput(dragDropState) {  // Nur dragDropState als Key - verhindert Gesture-Restart
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragDropState.onDragStart(offset, currentIndex.value)  // Aktuellen Wert lesen
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
