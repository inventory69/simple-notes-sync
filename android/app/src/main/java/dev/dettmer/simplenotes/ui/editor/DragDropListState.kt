package dev.dettmer.simplenotes.ui.editor

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import dev.dettmer.simplenotes.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

/**
 * Drag & Drop State für LazyList mit Separator-Support.
 *
 * Rewrite für v2.0.0 (IMPL_29h):
 * - Key-basiertes Tracking (stabil über Separator-Crossings)
 * - Continuous Auto-Scroll-Loop ohne cancel/restart-Flapping
 * - Layout-Wait nach jedem Swap (verhindert Race-Conditions)
 * - Mutex-geschützte Swap-Operationen mit Library-Pattern lock/tryLock
 * - derivedStateOf für isAnyItemDragging (minimale Recompositions)
 * - Float-basierter draggingItemOffset (für graphicsLayer Sub-Pixel-Präzision)
 *
 * Adaptiert von: Calvin-LL/Reorderable v3.0.0 (Apache 2.0)
 * Custom: Separator-Logik, Adjazenz-Filter, Visual/Data-Index-Konvertierung
 */
class DragDropListState(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
) {
    // --- Primary Drag State ---
    internal var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    var isDragConfirmed by mutableStateOf(false)
        private set

    // derivedStateOf: invalidiert Composition nur bei null ↔ non-null Wechsel (2× pro Drag)
    val isAnyItemDragging: Boolean by derivedStateOf { draggingItemKey != null }

    val draggingItemIndex: Int?
        get() = draggingItemLayoutInfo?.index

    fun isDraggingItem(key: Any): Boolean = draggingItemKey == key

    // --- Drag Offset State ---
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset = 0
    private var draggingItemSize = 0
    private var lastKnownDraggingOffset = 0f
    // IMPL_29d: Predicted offset — überbrückt Frames zwischen Swap und Layout-Update
    private var predictedItemOffset: Int? = null
    // IMPL_29e: Letzter bekannter Index VOR dem Swap — für Prediction-Gating im Offset-Getter
    private var oldDraggingItemIndex: Int? = null

    // --- Debug Logging ---
    private var lastLogTimeMs = 0L

    // --- Auto-Scroll ---
    private var autoScrollJob: Job? = null
    // IMPL_29h H2: Speed-Variable statt cancel/restart; laufender Job liest diesen Wert direkt
    private var autoScrollSpeed by mutableFloatStateOf(0f)
    private var lastSwapTimeMs = 0L
    // IMPL_29f F3: Serialisiert Swap-Operationen (verhindert Race-Condition RC-3)
    private val swapMutex = Mutex()
    // IMPL_29k K3: Anti-Flapping — verhindert Swap-Oscillation
    private var lastSwapFromIndex = -1
    private var lastSwapToIndex = -1
    // IMPL_29q Q3: Flag für Immunity-Recovery im Auto-Scroll-Loop
    private var immunityFiredNeedsCleanCancel = false

    // --- Layout-Wait (IMPL_29h H3) ---
    // snapshotFlow emittiert bei jedem layoutInfo-Update (nach jedem onMove → Recomposition)
    private val layoutInfoFlow = snapshotFlow { state.layoutInfo }

    // --- Separator (v1.8.1 IMPL_14) ---
    var separatorVisualIndex by mutableIntStateOf(-1)

    // --- Layout Lookup (Key-basiert statt Index-basiert) ---
    // Key bleibt stabil über Separator-Crossings; Index ändert sich bei jedem Crossing
    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    // Float-basierter Offset für graphicsLayer (Sub-Pixel-Präzision, kein ±1px Jitter)
    val draggingItemOffset: Float
        get() {
            if (draggingItemKey == null) return 0f
            val info = draggingItemLayoutInfo
            return if (info != null) {
                // Prediction erst clearen wenn Layout den Move tatsächlich reflektiert
                val layoutOffset = if (info.index != oldDraggingItemIndex || oldDraggingItemIndex == null) {
                    oldDraggingItemIndex = null
                    predictedItemOffset = null
                    info.offset
                } else {
                    predictedItemOffset ?: info.offset
                }
                val offset = draggingItemInitialOffset + draggingItemDraggedDelta - layoutOffset
                lastKnownDraggingOffset = offset
                offset
            } else {
                if (predictedItemOffset != null) {
                    logD("OFFSET_PREDICTED", "key=$draggingItemKey predicted=$predictedItemOffset")
                } else {
                    logD("OFFSET_FALLBACK", "key=$draggingItemKey fallback=$lastKnownDraggingOffset")
                }
                lastKnownDraggingOffset
            }
        }

    // --- Public API ---

    fun onDragStart(key: Any) {
        draggingItemKey = key
        isDragConfirmed = false
        val info = draggingItemLayoutInfo
        draggingItemInitialOffset = info?.offset ?: 0
        draggingItemSize = info?.size ?: 0
        draggingItemDraggedDelta = 0f
        lastKnownDraggingOffset = 0f
        oldDraggingItemIndex = null
        predictedItemOffset = null
        lastSwapFromIndex = -1
        lastSwapToIndex = -1
        immunityFiredNeedsCleanCancel = false
        logD("DRAG_START", "key=$key idx=${info?.index} off=${info?.offset} size=${info?.size}")
    }

    fun onDragInterrupted() {
        if (draggingItemKey == null) return
        val autoScrollActive = autoScrollJob?.isActive == true
        val mutexLocked = swapMutex.isLocked
        val timeSinceLastSwap = System.currentTimeMillis() - lastSwapTimeMs
        logD("DRAG_INTERRUPTION", "key=$draggingItemKey confirmed=$isDragConfirmed " +
            "autoScrollActive=$autoScrollActive swapMutexLocked=$mutexLocked " +
            "lastSwapAgo=${timeSinceLastSwap}ms compOff=$draggingItemOffset " +
            "oldDragIdx=$oldDraggingItemIndex dragIdx=${draggingItemIndex}")
        stopAutoScroll()
        draggingItemDraggedDelta = 0f
        draggingItemKey = null
        isDragConfirmed = false
        draggingItemInitialOffset = 0
        draggingItemSize = 0
        lastKnownDraggingOffset = 0f
        predictedItemOffset = null
        oldDraggingItemIndex = null
        lastSwapFromIndex = -1
        lastSwapToIndex = -1
        immunityFiredNeedsCleanCancel = false
    }

    /**
     * IMPL_29q Q4: Immunity-Check für DRAG_CANCEL vom aktiven Pointer-Scope.
     *
     * Wird von dragContainer.onDragCancel aufgerufen wenn der Cancel-Key == aktiver Drag-Key.
     * Prüft ob der Cancel innerhalb des Post-Swap Immunity-Fensters liegt:
     * - Ja: Speed sofort auf 0, Recovery-Flag setzen → Auto-Scroll-Loop macht Clean Cancel (~32ms)
     * - Nein: Echter Cancel → onDragInterrupted() direkt aufrufen
     */
    fun onDragCancelWithImmunityCheck() {
        val now = System.currentTimeMillis()
        val timeSinceSwap = now - lastSwapTimeMs
        val isAutoScrolling = autoScrollJob?.isActive == true
        val immunityMs = if (isAutoScrolling) SCROLL_AWARE_IMMUNITY_MS else POST_SWAP_IMMUNITY_MS
        if (timeSinceSwap < immunityMs) {
            // IMPL_29q Q4: Immunity-Sicherheitsnetz
            // beyondBoundsItemCount sollte das verhindern, aber falls doch Recycling:
            // Speed sofort auf 0 → kein Runaway
            logD("POINTER_EVENT", "DRAG_CANCEL_IGNORED reason=POST_SWAP_IMMUNITY " +
                "key=$draggingItemKey timeSinceSwap=${timeSinceSwap}ms immunityMs=$immunityMs")
            autoScrollSpeed = 0f
            immunityFiredNeedsCleanCancel = true
        } else {
            // Außerhalb Immunity-Window → echter Cancel
            onDragInterrupted()
        }
    }

    fun onDrag(offset: Offset) {
        if (draggingItemKey == null) return
        isDragConfirmed = true
        draggingItemDraggedDelta += offset.y
        if (!shouldThrottleLog()) {
            logD("DRAG_DELTA", "dy=${offset.y} total=$draggingItemDraggedDelta compOff=$draggingItemOffset")
        }

        // IMPL_29h: Swap nur wenn KEIN Auto-Scroll aktiv (Library-Pattern: tryLock)
        if (!isAutoScrollActive) trySwapOnDrag()

        // Speed aktualisieren — laufender Job liest den neuen Wert direkt
        autoScrollSpeed = calculateAutoScrollSpeed()
        if (autoScrollSpeed != 0f) {
            ensureAutoScrollRunning()
        }
    }

    // --- Swap Logic ---

    private val isAutoScrollActive: Boolean
        get() = autoScrollJob?.isActive == true && autoScrollSpeed != 0f

    /**
     * IMPL_29k K4: Non-blocking Swap-Attempt von onDrag mit Viewport-Safety.
     *
     * Nur aktiv wenn kein Auto-Scroll läuft. Prüft zusätzlich:
     * - K1: Viewport-Safety (predicted offset muss im Viewport liegen)
     * - K3: Flapping-Guard (verhindert Swap-Oscillation)
     */
    private fun trySwapOnDrag() {
        if (!swapMutex.tryLock()) return

        val draggingItem = draggingItemLayoutInfo
        if (draggingItem == null) {
            swapMutex.unlock()
            return
        }

        val currentIndex = draggingItem.index
        val startOffset = (draggingItemInitialOffset + draggingItemDraggedDelta).toInt()
        val endOffset = startOffset + draggingItemSize

        val targetItem = state.layoutInfo.visibleItemsInfo
            .filter { item ->
                item.index != separatorVisualIndex &&
                item.index != currentIndex &&
                isAdjacentSkippingSeparator(currentIndex, item.index) &&
                run {
                    val targetCenter = item.offset + item.size / 2
                    startOffset < targetCenter && endOffset > targetCenter
                }
            }
            .minByOrNull { kotlin.math.abs(it.index - currentIndex) }

        if (targetItem == null) {
            swapMutex.unlock()
            if (!shouldThrottleLog()) {
                logD("SWAP_SKIP", "reason=NO_TARGET dragIdx=$currentIndex " +
                    "start=$startOffset end=$endOffset sep=$separatorVisualIndex")
            }
            return
        }

        // K1: Viewport-Safety Check
        if (!isSwapViewportSafe(currentIndex, targetItem)) {
            swapMutex.unlock()
            return
        }

        // K3: Flapping-Guard
        if (isSwapFlapping(currentIndex, targetItem.index)) {
            swapMutex.unlock()
            return
        }

        // Swap mit Layout-Wait — Mutex wird in performSwapWithLayoutWait freigegeben
        scope.launch {
            performSwapWithLayoutWait(draggingItem, targetItem)
        }
    }

    /**
     * IMPL_29k K2: Blocking Swap in Scroll-Richtung mit Viewport-Safety.
     *
     * Wird VOR jedem scrollBy in der Auto-Scroll-Loop aufgerufen.
     * BLOCKT bis der Swap + Layout-Wait abgeschlossen ist.
     *
     * Änderungen vs. 29j:
     * - Content-Area Filter: Bevorzugt VOLLSTÄNDIG sichtbare Items als Targets
     *   (Library-Pattern: getItemsInContentArea)
     * - Viewport-Safety Check (K1): Swap nur wenn predicted offset im Viewport liegt
     * - Flapping-Guard (K3): Verhindert Swap-Oscillation
     *
     * Library-Pattern (Calvin-LL/Reorderable):
     *   onMoveStateMutex.lock() → isDraggingItemAtEnd? →
     *   findTarget(itemsInContentArea) ?: findLast/findFirst(visibleItems) →
     *   isTargetDirectionCorrect? → moveItems (with viewport-safe positions)
     */
    private suspend fun moveDraggingItemToEnd(scrollDirection: Int) {
        swapMutex.lock()

        val draggingItem = draggingItemLayoutInfo
        if (draggingItem == null) {
            swapMutex.unlock()
            return
        }
        val currentIndex = draggingItem.index

        // isDraggingItemAtEnd-Check (Library-Pattern, unverändert)
        val visibleItems = state.layoutInfo.visibleItemsInfo
        val isDraggingItemAtEnd = if (scrollDirection > 0) {
            currentIndex == visibleItems.lastOrNull()?.index
        } else {
            currentIndex == state.firstVisibleItemIndex
        }
        if (isDraggingItemAtEnd) {
            swapMutex.unlock()
            return
        }

        // K2: Content-Area Filter — nur Items die VOLLSTÄNDIG im sichtbaren Bereich liegen
        val viewportStart = state.layoutInfo.viewportStartOffset
        val viewportEnd = state.layoutInfo.viewportEndOffset
        val itemsInContentArea = visibleItems.filter { item ->
            item.offset >= viewportStart &&
            item.offset + item.size <= viewportEnd
        }

        // Candidates: Items in Scroll-Richtung, Content-Area bevorzugt
        val contentAreaCandidates = itemsInContentArea.filter { item ->
            item.index != currentIndex &&
            item.index != separatorVisualIndex &&
            (if (scrollDirection > 0) item.index > currentIndex else item.index < currentIndex)
        }

        // Fallback auf alle sichtbaren Items wenn Content-Area leer
        val candidates = contentAreaCandidates.ifEmpty {
            visibleItems.filter { item ->
                item.index != currentIndex &&
                item.index != separatorVisualIndex &&
                (if (scrollDirection > 0) item.index > currentIndex else item.index < currentIndex)
            }
        }

        if (candidates.isEmpty()) {
            swapMutex.unlock()
            return
        }

        // Primär: Overlap-Check (Straddle-Center)
        val dragOffset = draggingItemOffset
        val startOffset = (draggingItem.offset + dragOffset).toInt()
        val endOffset = startOffset + draggingItemSize

        var targetItem = candidates.firstOrNull { item ->
            val targetCenter = item.offset + item.size / 2
            startOffset < targetCenter && endOffset > targetCenter
        }

        // Adjacent-Fallback (nur aus Content-Area Candidates wenn möglich)
        if (targetItem == null) {
            targetItem = candidates
                .filter { isAdjacentSkippingSeparator(currentIndex, it.index) }
                .let { adjacent ->
                    if (scrollDirection > 0) adjacent.minByOrNull { it.index }
                    else adjacent.maxByOrNull { it.index }
                }
        }

        if (targetItem == null) {
            if (!shouldThrottleLog()) {
                logD("SCROLL_SWAP_SKIP", "reason=NO_ADJACENT dragIdx=$currentIndex " +
                    "scrollDir=$scrollDirection candidates=${candidates.size}")
            }
            swapMutex.unlock()
            return
        }

        // Direction-Validierung (Library-Pattern)
        val isDirectionCorrect = if (scrollDirection > 0) {
            targetItem.index > currentIndex
        } else {
            targetItem.index < currentIndex
        }
        if (!isDirectionCorrect) {
            swapMutex.unlock()
            return
        }

        // K1: Viewport-Safety Check — predicted offset MUSS im Viewport liegen
        if (!isSwapViewportSafe(currentIndex, targetItem)) {
            swapMutex.unlock()
            return
        }

        // K3: Flapping-Guard — verhindert Swap-Oscillation
        if (isSwapFlapping(currentIndex, targetItem.index)) {
            swapMutex.unlock()
            return
        }

        // Swap ist sicher → ausführen
        performSwapWithLayoutWait(draggingItem, targetItem)
    }

    /**
     * IMPL_29k K1 + IMPL_29l L2: Prüft ob ein Swap das dragged Item im Viewport halten würde.
     *
     * Berechnet die predictedItemOffset Position und prüft ob diese innerhalb
     * [minSafe, maxSafe] liegt. Wenn nicht → Swap wäre unsicher → return false.
     *
     * L2 Boundary-Safety: Am oberen (Index 0) und unteren (letzter Index) Listenrand
     * wird der VIEWPORT_SAFETY_MARGIN_PX auf 0 reduziert, da Items an diesen Positionen
     * physisch nicht weiter geschoben werden können. Ohne diese Ausnahme wäre Index 0
     * permanent blockiert (predicted=0 < margin=16).
     */
    private fun isSwapViewportSafe(
        currentIndex: Int,
        targetItem: LazyListItemInfo,
    ): Boolean {
        val predicted = if (targetItem.index > currentIndex) {
            (targetItem.offset + targetItem.size) - draggingItemSize
        } else {
            targetItem.offset
        }
        val viewportHeight = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset

        // IMPL_29l L2: Boundary-Safe — am Listenrand ist der Safety-Margin nicht nötig
        val isTopBoundary = targetItem.index == 0
        val lastIndex = state.layoutInfo.totalItemsCount - 1
        val isBottomBoundary = targetItem.index == lastIndex
        val minSafe = if (isTopBoundary) 0 else VIEWPORT_SAFETY_MARGIN_PX
        val maxSafe = viewportHeight - draggingItemSize - if (isBottomBoundary) 0 else VIEWPORT_SAFETY_MARGIN_PX

        val isSafe = predicted in minSafe..maxSafe
        if (!isSafe) {
            logD("SWAP_BLOCKED", "reason=VIEWPORT_UNSAFE currentIdx=$currentIndex " +
                "targetIdx=${targetItem.index} predicted=$predicted " +
                "safeRange=$minSafe..$maxSafe viewport=$viewportHeight" +
                " topBound=$isTopBoundary bottomBound=$isBottomBoundary")
        }
        return isSafe
    }

    /**
     * IMPL_29k K3: Anti-Flapping Guard.
     *
     * Verhindert dass ein Swap die exakte Umkehrung des vorherigen Swaps ist
     * UND der letzte Swap weniger als SWAP_COOLDOWN_MS her ist.
     *
     * Beispiel: Swap 10→11 gefolgt von 11→10 innerhalb von 80ms → BLOCKIERT.
     * Aber: Swap 10→11 gefolgt von 11→12 → ERLAUBT (gleiche Richtung).
     * Und: Swap 10→11, 200ms Pause, dann 11→10 → ERLAUBT (Cooldown abgelaufen).
     */
    private fun isSwapFlapping(fromIndex: Int, toIndex: Int): Boolean {
        val timeSinceSwap = System.currentTimeMillis() - lastSwapTimeMs
        val isReversal = fromIndex == lastSwapToIndex && toIndex == lastSwapFromIndex
        if (isReversal && timeSinceSwap < SWAP_COOLDOWN_MS) {
            logD("SWAP_BLOCKED", "reason=FLAPPING from=$fromIndex to=$toIndex " +
                "lastFrom=$lastSwapFromIndex lastTo=$lastSwapToIndex ago=${timeSinceSwap}ms")
            return true
        }
        return false
    }

    /**
     * IMPL_29h H3: Swap ausführen mit Layout-Wait.
     * Caller muss swapMutex.lock()/tryLock() halten.
     * Diese Funktion gibt den Mutex IMMER frei (auch bei Fehler).
     *
     * Library-Pattern (Calvin-LL/Reorderable): wartet mit layoutInfoFlow.take(2).collect()
     * bis das Layout den Move reflektiert hat. Das verhindert:
     * - Race zwischen Swap und nächstem Swap-Check
     * - Item-Verschwinden aus visibleItemsInfo (→ DRAG_CANCEL)
     * - Pointer-Scope-Invalidierung durch Recomposition
     */
    private suspend fun performSwapWithLayoutWait(
        draggingItem: LazyListItemInfo,
        targetItem: LazyListItemInfo,
    ) {
        val currentIndex = draggingItem.index
        try {
            if (!isAnyItemDragging) return

            // ScrollToItem-Stabilisierung (Library-Pattern)
            if (targetItem.index == state.firstVisibleItemIndex ||
                currentIndex == state.firstVisibleItemIndex
            ) {
                state.requestScrollToItem(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset
                )
            }

            // Separator-Crossing Scroll-Adjust
            val isSeparatorCrossing = separatorVisualIndex >= 0 &&
                ((currentIndex < separatorVisualIndex && targetItem.index > separatorVisualIndex) ||
                 (currentIndex > separatorVisualIndex && targetItem.index < separatorVisualIndex))
            if (isSeparatorCrossing && targetItem.index < state.firstVisibleItemIndex) {
                state.requestScrollToItem(targetItem.index, 0)
                logD("SCROLL_ADJUST", "sepCross=UP newIdx=${targetItem.index}")
            }

            // Prediction setzen
            oldDraggingItemIndex = currentIndex
            predictedItemOffset = if (targetItem.index > currentIndex) {
                (targetItem.offset + targetItem.size) - draggingItemSize
            } else {
                targetItem.offset
            }
            lastKnownDraggingOffset =
                draggingItemInitialOffset + draggingItemDraggedDelta - predictedItemOffset!!.toFloat()

            // Swap ausführen
            val fromDataIndex = visualToDataIndex(currentIndex)
            val toDataIndex = visualToDataIndex(targetItem.index)
            logD("SWAP_EXEC", "fromVis=$currentIndex toVis=${targetItem.index} " +
                "fromData=$fromDataIndex toData=$toDataIndex sep=$separatorVisualIndex " +
                "predicted=$predictedItemOffset")
            lastSwapFromIndex = currentIndex
            lastSwapToIndex = targetItem.index
            lastSwapTimeMs = System.currentTimeMillis()
            onMove(fromDataIndex, toDataIndex)

            // ⭐ Layout-Wait: Warten bis Layout den Move reflektiert
            // take(1) = aktueller Snapshot, take(2) = nächster Update nach onMove
            withTimeout(LAYOUT_WAIT_TIMEOUT_MS) {
                layoutInfoFlow.take(2).collect()
            }

            // Layout hat aufgeholt → Prediction clearen
            oldDraggingItemIndex = null
            predictedItemOffset = null
        } catch (_: CancellationException) {
            oldDraggingItemIndex = null
            predictedItemOffset = null
        } finally {
            swapMutex.unlock()
        }
    }

    // --- Auto-Scroll ---

    private fun calculateAutoScrollSpeed(): Float {
        val draggingItem = draggingItemLayoutInfo ?: return 0f
        val handleY = draggingItem.offset + draggingItemOffset + draggingItemSize / 2f

        val viewportStart = state.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = state.layoutInfo.viewportEndOffset.toFloat()
        val viewportHeight = viewportEnd - viewportStart
        val baseSpeed = viewportHeight * BASE_SPEED_FRACTION / (MS_PER_SECOND / AUTO_SCROLL_FRAME_MS)
        val dynamicThreshold = viewportHeight * SCROLL_THRESHOLD_FRACTION

        val distFromTop = (handleY - viewportStart).coerceAtLeast(0f)
        val distFromBottom = (viewportEnd - handleY).coerceAtLeast(0f)

        return when {
            distFromTop < dynamicThreshold && state.canScrollBackward -> {
                val mult = ((1f - distFromTop / dynamicThreshold) * MAX_SPEED_MULTIPLIER)
                    .coerceAtLeast(MIN_SPEED_MULTIPLIER)
                -(baseSpeed * mult)
            }
            distFromBottom < dynamicThreshold && state.canScrollForward -> {
                val mult = ((1f - distFromBottom / dynamicThreshold) * MAX_SPEED_MULTIPLIER)
                    .coerceAtLeast(MIN_SPEED_MULTIPLIER)
                baseSpeed * mult
            }
            else -> 0f
        }
    }

    /**
     * IMPL_29j J3: Berechnet die maximale Scroll-Distanz bis zum Viewport-Rand.
     *
     * MUSS nach moveDraggingItemToEnd() (Blocking Swap) aufgerufen werden,
     * damit die Layout-Daten frisch sind.
     *
     * Returns: Positive Float-Distanz (≥ 0). Wenn 0 → Item ist am Viewport-Rand,
     * kein Scroll in dieser Richtung sicher.
     *
     * Library-Pattern: maxScrollDistanceProvider in Scroller.start()
     * - Forward: item.offset + item.size - 1 (Abstand Item-Ende → Viewport-Top)
     * - Backward: viewportHeight - item.offset - 1 (Abstand Item-Start → Viewport-Bottom)
     */
    private fun calculateMaxScrollDistance(scrollDirection: Int): Float {
        val draggingItem = draggingItemLayoutInfo ?: return 0f
        return if (scrollDirection > 0) {
            (draggingItem.offset + draggingItem.size - 1f).coerceAtLeast(0f)
        } else {
            val viewportHeight = (state.layoutInfo.viewportEndOffset -
                state.layoutInfo.viewportStartOffset).toFloat()
            (viewportHeight - draggingItem.offset - 1f).coerceAtLeast(0f)
        }
    }

    /**
     * IMPL_29j J4: Sequential Auto-Scroll-Loop.
     *
     * Kern-Änderung vs. 29i: Die Loop BLOCKT auf moveDraggingItemToEnd (suspend).
     * Scroll findet erst NACH Swap+Layout-Wait statt. Layout-Daten für
     * calculateMaxScrollDistance sind dadurch garantiert frisch.
     *
     * Flow pro Iteration:
     * 1. moveDraggingItemToEnd() → BLOCKT bis Swap+Layout fertig (oder kein Swap nötig)
     * 2. calculateMaxScrollDistance() → liest FRISCHE Layout-Daten
     * 3. wenn maxDist ≤ 0 → kein Scroll, nächste Iteration (Item am Rand)
     * 4. scrollBy(min(speed, maxDist)) → BEGRENZTER Scroll
     * 5. delay(16ms) → nächste Iteration
     */
    private fun ensureAutoScrollRunning() {
        if (autoScrollJob?.isActive == true) return
        logD("AUTOSCROLL_STATE", "action=START draggingKey=$draggingItemKey")
        autoScrollJob = scope.launch {
            try {
                while (isActive && draggingItemKey != null) {
                    // IMPL_29q Q5: Immunity-Recovery-Cancel
                    // Wenn Immunity gefeuert hat (beyondBoundsItemCount hat nicht gereicht),
                    // Speed ist bereits 0 → Recovery-Scroll + sauberer Cancel (~32ms)
                    if (immunityFiredNeedsCleanCancel) {
                        immunityFiredNeedsCleanCancel = false
                        val targetIdx = maxOf(0, lastSwapToIndex - 1)
                        logD("IMMUNITY_CLEAN_CANCEL", "requestScrollToItem idx=$targetIdx " +
                            "lastSwapTo=$lastSwapToIndex")
                        try {
                            state.requestScrollToItem(targetIdx)
                        } catch (_: Exception) { /* Best effort */ }
                        delay(AUTO_SCROLL_FRAME_MS) // 1 Frame für Layout-Settle
                        onDragInterrupted()
                        break
                    }

                    val speed = autoScrollSpeed
                    if (speed != 0f) {
                        val scrollDirection = if (speed > 0) 1 else -1

                        // J1: BLOCKING Swap — Loop wartet auf Swap+Layout-Completion
                        moveDraggingItemToEnd(scrollDirection)

                        // J3: Frischer maxDist NACH Swap (Layout ist aktuell)
                        val maxDist = calculateMaxScrollDistance(scrollDirection)
                        if (maxDist > 0f) {
                            // Clamp Speed auf maxDist (verhindert Viewport-Exit)
                            val clampedSpeed = if (speed > 0f) {
                                speed.coerceAtMost(maxDist)
                            } else {
                                speed.coerceAtLeast(-maxDist)
                            }
                            try {
                                val scrolled = state.scrollBy(clampedSpeed)
                                if (!shouldThrottleLog()) {
                                    logD("AUTO_SCROLL", "speed=$speed maxDist=$maxDist " +
                                        "clamped=$clampedSpeed scrolled=$scrolled")
                                }
                            } catch (_: CancellationException) {
                                if (!isActive) return@launch
                            }
                        } else {
                            if (!shouldThrottleLog()) {
                                logD("AUTO_SCROLL", "speed=$speed maxDist=$maxDist SKIP (item at edge)")
                            }
                        }
                    }
                    delay(AUTO_SCROLL_FRAME_MS)
                }
            } finally {
                logD("AUTOSCROLL_STATE", "action=LOOP_END draggingKey=$draggingItemKey")
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollSpeed = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    // --- Debug Logging Helpers ---

    private fun shouldThrottleLog(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastLogTimeMs < LOG_THROTTLE_MS) return true
        lastLogTimeMs = now
        return false
    }

    private fun logD(event: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "[$event] $msg")
    }

    // --- Separator Logic (unverändert von v1.8.1 IMPL_14) ---

    fun visualToDataIndex(visualIndex: Int): Int {
        if (separatorVisualIndex < 0) return visualIndex
        return if (visualIndex > separatorVisualIndex) visualIndex - 1 else visualIndex
    }

    fun dataToVisualIndex(dataIndex: Int): Int {
        if (separatorVisualIndex < 0) return dataIndex
        return if (dataIndex >= separatorVisualIndex) dataIndex + 1 else dataIndex
    }

    private fun isAdjacentSkippingSeparator(indexA: Int, indexB: Int): Boolean {
        val diff = kotlin.math.abs(indexA - indexB)
        if (diff == 1) {
            val between = minOf(indexA, indexB) + 1
            return between != separatorVisualIndex || separatorVisualIndex < 0
        }
        if (diff == 2 && separatorVisualIndex >= 0) {
            val between = minOf(indexA, indexB) + 1
            return between == separatorVisualIndex
        }
        return false
    }

    private companion object {
        const val AUTO_SCROLL_FRAME_MS = 16L
        const val BASE_SPEED_FRACTION = 0.04f
        const val MAX_SPEED_MULTIPLIER = 7f
        const val MIN_SPEED_MULTIPLIER = 0.3f
        const val SCROLL_THRESHOLD_FRACTION = 0.25f
        const val LAYOUT_WAIT_TIMEOUT_MS = 1000L    // IMPL_29h H3: Layout-Wait Timeout
        const val MS_PER_SECOND = 1000f
        const val LOG_TAG = "DragDrop"
        const val LOG_THROTTLE_MS = 100L
        // IMPL_29k K3: Anti-Flapping
        const val SWAP_COOLDOWN_MS = 80L          // Minimum ms zwischen zwei Swaps
        // IMPL_29k K1: Viewport-Safety Margin
        const val VIEWPORT_SAFETY_MARGIN_PX = 16  // Min. Pixel Abstand zum Viewport-Rand
        // IMPL_29q Q2: Post-Swap Immunity — Sicherheitsnetz falls beyondBoundsItemCount
        // in einem Extremfall nicht ausreicht und Recycling doch passiert.
        const val POST_SWAP_IMMUNITY_MS = 200L
        const val SCROLL_AWARE_IMMUNITY_MS = 500L
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
            onMove = onMove,
        )
    }
}

@Composable
fun Modifier.dragContainer(
    dragDropState: DragDropListState,
    itemKey: Any
): Modifier {
    val currentKey = rememberUpdatedState(itemKey)
    return this.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { _ ->
                if (BuildConfig.DEBUG) Log.d("DragDrop",
                    "[POINTER_EVENT] type=DRAG_START key=${currentKey.value}")
                dragDropState.onDragStart(currentKey.value)
            },
            onDragEnd = {
                if (BuildConfig.DEBUG) Log.d("DragDrop",
                    "[POINTER_EVENT] type=DRAG_END key=${currentKey.value} " +
                    "draggingKey=${dragDropState.draggingItemKey}")
                dragDropState.onDragInterrupted()
            },
            onDragCancel = {
                val eventKey = currentKey.value
                val activeKey = dragDropState.draggingItemKey
                if (BuildConfig.DEBUG) Log.d("DragDrop",
                    "[POINTER_EVENT] type=DRAG_CANCEL key=$eventKey " +
                    "draggingKey=$activeKey")
                if (activeKey == null) {
                    // Kein aktiver Drag — no-op (harmlos)
                } else if (eventKey != activeKey) {
                    // IMPL_29l L1: Stale Cancel von anderem Item's Pointer-Scope
                    if (BuildConfig.DEBUG) Log.d("DragDrop",
                        "[POINTER_EVENT] DRAG_CANCEL_IGNORED reason=STALE_KEY " +
                        "staleKey=$eventKey activeKey=$activeKey")
                } else {
                    // Same-Key Cancel: Entweder echt oder durch Viewport-Edge Recycling
                    // IMPL_29q Q4: Immunity-Check an DragDropListState delegieren
                    dragDropState.onDragCancelWithImmunityCheck()
                }
            },
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset)
            }
        )
    }
}
