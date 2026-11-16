package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistNesting
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.ui.editor.components.CheckedItemsSeparator
import dev.dettmer.simplenotes.ui.editor.components.ChecklistItemRow
import dev.dettmer.simplenotes.ui.theme.LocalNewDesign
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

// Checklisten-Editor (LazyColumn mit DnD und Placement-Animation), aus NoteEditorScreen.kt ausgelagert,
// unverändert. DnD-Regeln: Skill dnd-protection, Animationen: Skill checklist-animations.

/**
 * 🆕 v1.8.1 IMPL_14: Extrahiertes Composable für ein einzelnes draggbares Checklist-Item.
 * Entkoppelt von der Separator-Logik — wiederverwendbar für unchecked und checked Items.
 */
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod") // Compose callbacks — cannot be reduced without wrapper class
@Composable
internal fun LazyItemScope.DraggableChecklistItem(
    item: ChecklistItemState,
    dragDropState: DragDropListState,
    focusNewItemId: String?,
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onCopyText: (String) -> Unit, // 🆕 v2.2.0: Aktion 1
    onDuplicate: (String) -> Unit, // 🆕 v2.2.0: Aktion 2
    onCopyToChecklist: (String) -> Unit, // 🆕 v2.2.0: Aktion 3
    onAddToCalendar: (String) -> Unit,
    onFocusHandled: () -> Unit,
    onHeightChanged: () -> Unit, // 🆕 v1.8.1 (IMPL_05)
    placementAnimationsEnabled: Boolean, // 🔧 v2.5.x: Gate gegen Open-Burst
    isPendingUncheck: Boolean, // 🔧 v2.13.0: Uncheck aufgeschoben — Collapse statt Reorder-Animation
    followsPendingUncheck: Boolean, // Unterpunkte: gehört zur Gruppe des aufgeschobenen Uncheck, kollabiert mit
    liftedWithGroup: Boolean, // Unterpunkte: wandert beim Abhaken mit dem Oberpunkt, wird wie er über die Nachbarn gezeichnet
    groupExpanding: Boolean, // Unterpunkte: eine Gruppe wächst gerade am Ziel auf → Nachbarn ohne Placement-Feder
    onFollowerExpanding: (Int) -> Unit, // Unterpunkte: +1 / -1 um das Aufwachsen eines Mitläufers
    groupCommitScrolled: () -> Boolean, // Unterpunkte: hat der Gruppen-Commit gescrollt? Dann kein Aufwachsen am Ziel
    canIndent: Boolean,
    onIndent: (String) -> Unit,
    onOutdent: (String) -> Unit,
    scrollsAfterUncheck: (String) -> Boolean, // 🆕 Issue #112: true → Commit ohne Settle + ohne Expand (Scroll übernimmt)
    onUncheckCommit: (id: String, scroll: Boolean) -> Unit, // 🔧 v2.13.0: Commit nach abgeschlossenem Collapse
    topHighlightId: String?, // 🔧 Issue #112: Highlight-Pop nach Scroll-Ankunft
    onTopHighlightShown: () -> Unit, // 🔧 Issue #112: konsumiert topHighlightId
    readOnly: Boolean // 🆕 Issue #156
) {
    // 🆕 v2.0.0 (IMPL_29b): Key-basiertes isDragging statt Index-basiert.
    // Index-basiert hat Timing-Lücke: draggingItemIndex (aus visibleItemsInfo, OLD) vs.
    // visualIndex (aus Composition, NEW) für 1-2 Frames nach jedem Swap → sichtbarer Sprung.
    // Key-basiert ist immun gegen Layout/Composition-Desync.
    val isDragging = dragDropState.isDraggingItem(item.id) && dragDropState.isDragConfirmed
    val elevation by animateDpAsState(
        targetValue = if (isDragging) DRAGGING_ELEVATION_DP else 0.dp,
        label = "elevation"
    )

    val shouldFocus = item.id == focusNewItemId

    LaunchedEffect(shouldFocus) {
        if (shouldFocus) {
            onFocusHandled()
        }
    }

    // 🆕 v2.5.0: Trigger für Check-Tap-Animation. Wird beim Checkbox-Tap auf true
    // gesetzt (über onCheckboxTap-Callback an die Row), und nach CHECK_ANIMATION_TOTAL_MS
    // automatisch zurückgesetzt. Solange true → erhöhter zIndex (siehe unten) →
    // Row wird über Nachbarn gezeichnet, unabhängig von der Draw-Reihenfolge nach
    // dem Reorder. Key item.id: Reset-Timer überlebt Reorder, weil Composition
    // erhalten bleibt.
    var isCheckAnimating by remember(item.id) { mutableStateOf(false) }
    // Detect undo/redo-driven isChecked changes (onCheckboxTap never fires in those cases)
    val prevChecked = remember(item.id) { mutableStateOf(item.isChecked) }
    LaunchedEffect(item.isChecked) {
        if (item.isChecked != prevChecked.value) {
            isCheckAnimating = true
            prevChecked.value = item.isChecked
        }
    }
    LaunchedEffect(isCheckAnimating, item.id) {
        if (isCheckAnimating) {
            // 🔍 DEBUG: markiert das zIndex-/Glow-Fenster in derselben Timeline wie der Frame-Trace
            if (BuildConfig.DEBUG) Logger.d(CHECK_TRACE_TAG, "[ZIDX] on id=${item.id.take(CHECK_TRACE_ID_LEN)}")
            delay(CHECK_ANIMATION_TOTAL_MS)
            isCheckAnimating = false
            if (BuildConfig.DEBUG) Logger.d(CHECK_TRACE_TAG, "[ZIDX] off id=${item.id.take(CHECK_TRACE_ID_LEN)}")
        }
    }

    // 🔧 Issue #112: Frischer Highlight-Pop, nachdem der Scroll das Item oben ins Bild
    // gebracht hat. Die eigene Tap-Animation kann das nicht leisten: sie wird beim Collapse
    // bewusst beendet, und beim Commit wird die Composition disposed (Item landet oberhalb
    // des Viewports) — nach dem Scroll startet hier alles frisch, inkl. Flip-Detektor.
    LaunchedEffect(topHighlightId) {
        if (topHighlightId == item.id) {
            onTopHighlightShown()
            isCheckAnimating = true
        }
    }

    // 🔧 v2.13.0: Eigene Exit-Animation für jeden Uncheck mit Separator-Reorder.
    // LazyLayoutItemAnimator ist für die Aufwärts-Richtung nicht robust (Geist am Viewport-Rand
    // bei Ziel off-screen, Einschiebe-Artefakte parallel zum Scroll). Deshalb besitzt die
    // Row die Animation selbst: Höhe + Alpha → 0 an Ort und Stelle, Commit des Reorders erst
    // danach (onUncheckCommit). Der Animator hat dann nichts mehr zu animieren; ist die
    // Zielposition sichtbar, wächst die Row dort anschließend wieder auf (Gegenstück zum
    // Collapse). Das Model bleibt bis zum Commit unverändert — die Checkbox zeigt nur
    // optimistisch ungecheckt (checkedOverride). Damit bleibt die v2.5.0-Invariante
    // „isChecked-Flip und Sort im selben State-Snapshot" unangetastet.
    val collapse = remember(item.id) { Animatable(1f) }

    // Solange dieses Item seine Exit-Animation selbst fährt, darf LazyLayoutItemAnimator nicht
    // mitmischen — weder beim Schrumpfen noch im Commit-Pass danach. Ohne dieses Gate animiert
    // er den Reorder, sobald die Row wieder auf volle Höhe schnappt, und schiebt sie quer durchs
    // Bild nach oben: derselbe Geist, nur 250 ms später (gemessen in
    // ChecklistUncheckAnimationInstrumentedTest). Anders als die verworfene Variante ohne
    // Collapse kostet das keine Nachbar-Animation: die Nachbarn sind während des Collapse
    // bereits kontinuierlich nachgerückt.
    var collapseOwnsPlacement by remember(item.id) { mutableStateOf(false) }

    // Am Collapse-Ende entschieden: Scroll zum Ziel (Ziel liegt oberhalb des Viewports) oder
    // Aufwachsen am Ort wie ohne Scroll-to-Top. Bleibt false bei Abbruch per Re-Tap.
    var scrollAfterCommit by remember(item.id) { mutableStateOf(false) }
    val currentScrollsAfterUncheck by rememberUpdatedState(scrollsAfterUncheck)

    // Unterpunkte: Mitläufer einer wandernden Gruppe kollabieren mit, committen aber nicht (das tut der
    // getippte Eintrag für die ganze Gruppe). Danach folgen sie seinem Weg: am Ziel synchron mit ihm
    // aufwachsen, beim Commit mit Scroll still auf volle Höhe. Sprängen sie ohne Scroll sofort auf volle
    // Höhe, ragte der noch wachsende Eintrag über ihnen für einige Frames in sie hinein (gemessen im
    // ChecklistUncheckAnimationInstrumentedTest).
    var collapsedAsFollower by remember(item.id) { mutableStateOf(false) }
    val currentGroupCommitScrolled by rememberUpdatedState(groupCommitScrolled)
    val collapsing = isPendingUncheck || followsPendingUncheck

    LaunchedEffect(collapsing) {
        if (collapsing) {
            collapsedAsFollower = !isPendingUncheck
            // Glow/Scale-Pop sofort beenden: auf einer kollabierenden Row wirkt das Highlight
            // falsch — und es klebte sonst nach dem Scroll sichtbar am wieder
            // auftauchenden Item (die 500-ms-Animation lief den Scroll schlicht ab).
            // Nach Scroll-Ankunft triggert topHighlightId stattdessen einen frischen Pop.
            isCheckAnimating = false
            collapseOwnsPlacement = true
            scrollAfterCommit = false
            collapse.animateTo(0f, tween(UNCHECK_COLLAPSE_MS))
            if (collapsedAsFollower) return@LaunchedEffect
            scrollAfterCommit = currentScrollsAfterUncheck(item.id)
            if (!scrollAfterCommit) {
                // Die Nachbarn folgen der schrumpfenden Row über ihre animateItem-Spring und
                // hinken dabei hinterher. Der Commit killt via requestScrollToItem alle
                // Placement-Animationen — käme er sofort, schnappte der Rest sichtbar nach
                // (gemessen ~1/3 Zeilenhöhe in einem Frame). Deshalb erst die Spring auslaufen
                // lassen; die Row ist bereits unsichtbar, die Wartezeit kostet optisch nichts.
                // Mit Scroll zum Ziel entfällt das: der direkt folgende Scroll übernimmt die
                // gesamte Bewegung und maskiert den Spring-Schnitt — dafür startet der Scroll
                // ~200 ms früher, was den Tap spürbar direkter macht.
                delay(UNCHECK_COMMIT_SETTLE_MS)
            }
            onUncheckCommit(item.id, scrollAfterCommit)
            return@LaunchedEffect
        }
        if (!collapseOwnsPlacement) return@LaunchedEffect
        val commitScrolled = if (collapsedAsFollower) currentGroupCommitScrolled() else scrollAfterCommit && !item.isChecked
        if (commitScrolled) {
            // Commit mit folgendem Scroll: NICHT am Ziel aufwachsen. Ein Expand während
            // des Scrolls drückt die Nachbarn mitten in der Bewegung auseinander
            // (sichtbares „Einschieben"). Höhe still zurücksetzen; das Item ist beim Commit
            // off-screen, der Scroll bringt es fertig aufgebaut ins Bild.
            collapse.snapTo(1f)
            repeat(PLACEMENT_SUPPRESS_FRAMES) { withFrameNanos { } }
        } else {
            // Beide Wege enden mit voller Höhe:
            // - Abbruch per Re-Tap: das Model wurde nie geändert → Row klappt an Ort und
            //   Stelle wieder auf.
            // - Nach dem Commit ohne Scroll (Scroll-to-Top aus, oder Ziel im Bild): das Item sitzt an seiner neuen Position in der
            //   unchecked-Sektion. Ist die sichtbar, wächst die Row dort als Gegenstück zum
            //   Collapse auf; off-screen läuft die Animation unsichtbar und kostet nichts.
            // Solange sie läuft, bleibt animateItem über collapseOwnsPlacement aus — danach
            // startet der Modifier ohne Baseline, also ohne nachgeholte Placement-Animation.
            // Unterpunkte: Wächst eine ganze Gruppe, schieben mehrere Zeilen zugleich die Nachbarn weg;
            // deren Feder käme nicht hinterher (gemessen: Überlappung bei drei Zeilen). Die Mitläufer
            // melden ihr Aufwachsen, solange schalten alle Zeilen die Placement-Animation ab.
            if (collapsedAsFollower) onFollowerExpanding(1)
            try {
                collapse.animateTo(1f, tween(UNCHECK_COLLAPSE_MS))
            } finally {
                if (collapsedAsFollower) onFollowerExpanding(-1)
            }
        }
        collapseOwnsPlacement = false
    }
    // Screen/Item wird disposed, während der Collapse läuft → Commit sofort nachholen.
    // Ohne das ginge der Tap verloren (das Model wurde ja noch nicht angefasst).
    val pendingAtDispose = rememberUpdatedState(isPendingUncheck)
    val commitAtDispose = rememberUpdatedState(onUncheckCommit)
    DisposableEffect(item.id) {
        onDispose {
            if (pendingAtDispose.value) commitAtDispose.value(item.id, false)
        }
    }

    // Über den Nachbarn gezeichnet und deckend: getippte Zeile (Abhak-Animation) und ihre mitwandernde Gruppe
    val raised = isCheckAnimating || liftedWithGroup

    ChecklistItemRow(
        item = item,
        onTextChange = { onTextChange(item.id, it) },
        onCheckedChange = { onCheckedChange(item.id, it) },
        onDelete = { onDelete(item.id) },
        onAddNewItem = { onAddNewItemAfter(item.id) },
        onCopyText = { onCopyText(item.id) }, // 🆕 v2.2.0
        onDuplicate = { onDuplicate(item.id) }, // 🆕 v2.2.0
        onCopyToChecklist = { onCopyToChecklist(item.id) }, // 🆕 v2.2.0
        onAddToCalendar = { onAddToCalendar(item.id) },
        canIndent = canIndent,
        onIndent = { onIndent(item.id) },
        onOutdent = { onOutdent(item.id) },
        isCheckAnimating = isCheckAnimating, // 🆕 v2.5.0
        onCheckboxTap = { isCheckAnimating = true }, // 🆕 v2.5.0
        // 🔧 v2.13.0: Solange der Uncheck aufgeschoben ist, zeigt die Checkbox optimistisch
        // ungecheckt — das Model folgt erst beim Commit.
        checkedOverride = if (isPendingUncheck) false else null,
        readOnly = readOnly, // 🆕 Issue #156
        requestFocus = shouldFocus,
        isDragging = isDragging,
        isAnyItemDragging = dragDropState.isAnyItemDragging,
        dragModifier = Modifier.dragContainer(
            dragDropState = dragDropState,
            itemKey = item.id
        ),
        onHeightChanged = onHeightChanged, // 🆕 v1.8.1 (IMPL_05)
        modifier = Modifier
            // 🔧 v2.13.0: Collapse an Ort und Stelle (siehe oben). Als Layout-Modifier, damit
            // die LazyColumn die schrumpfende Höhe misst und die Nachbarn kontinuierlich
            // nachrücken — AnimatedVisibility würde die Row-Struktur verändern.
            .then(
                if (collapse.value < 1f) {
                    // graphicsLayer VOR layout: so misst der Clip-Node die bereits reduzierte
                    // Höhe und schneidet den überstehenden Row-Inhalt ab. Umgekehrt läge der
                    // Clip innerhalb und die Row würde in den Nachbarn hineinragen.
                    Modifier
                        .graphicsLayer {
                            alpha = collapse.value
                            clip = true
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = (placeable.height * collapse.value).roundToInt()
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                } else {
                    Modifier
                }
            )
            // 🆕 v1.8.2 (IMPL_11): Placement-Animation für nicht-gedraggte Items.
            // Historisch (vor IMPL_11): animateItem() mit fadeInSpec/fadeOutSpec auf ALLEN Items
            // → sichtbares Flickering bei langen Items beim schnellen Scrollen.
            // Lösung damals: fade entfernen, animateItem nur während Drag.
            // 🆕 v2.5.0: Bedingung von „nur während Drag" auf „immer außer beim gedraggten Item"
            // erweitert. Grund: Beim Check/Uncheck-Sort (außerhalb DnD) gab es vorher keine
            // Placement-Animation → Items sprangen abrupt. Da fadeInSpec/fadeOutSpec weiterhin
            // null sind, gilt die ursprüngliche Flicker-Ursache nicht. Der gedraggte Item bleibt
            // ausgenommen, weil seine Position via graphicsLayer.translationY gesteuert wird.
            // 🔧 v2.5.x: Zusätzliches Gate `placementAnimationsEnabled` verhindert, dass
            // Modifier.animateItem zwischen den ersten Layout-Pässen nach Open der Checkliste
            // die Offset-Diffs animiert (Symptom: Items „schieben sich rein"). Sobald das
            // initiale Layout stabil ist, ist das Gate offen — alle Check/Uncheck-Reorder-
            // Animationen aus Commit 7156c12 spielen unverändert.
            .then(
                if (!isDragging && placementAnimationsEnabled && !collapseOwnsPlacement && !groupExpanding) {
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                } else {
                    Modifier
                }
            )
            // 🆕 v2.5.0: zIndex-Tabelle:
            //   isDragging              → DRAGGING_ITEM_Z_INDEX (10f)  – Drag wins
            //   isCheckAnimating        → CHECKING_ITEM_Z_INDEX  (5f)  – Glow/Move on top
            //   liftedWithGroup         → CHECKING_ITEM_Z_INDEX  (5f)  – Unterpunkte wandern mit, ohne Glow
            //   sonst                   → 0f
            .zIndex(
                when {
                    isDragging -> DRAGGING_ITEM_Z_INDEX
                    raised -> CHECKING_ITEM_Z_INDEX
                    else -> 0f
                }
            )
            .graphicsLayer {
                if (isDragging) translationY = dragDropState.draggingItemOffset
                shadowElevation = elevation.toPx()
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
                clip = isDragging || elevation > 0.dp
            }
            .background(
                // UI-Beta (E7): in Ruhe durchsichtig, damit der Farbverlauf durchscheint. Nur Farbe, Modifier
                // und Reihenfolge bleiben (Regel aus dnd-protection/checklist-animations). Beim Ziehen und
                // Abhaken deckend, sonst schöbe sich eine Zeile durchsichtig über die andere. Unterpunkte, die
                // mit ihrem Oberpunkt wandern (liftedWithGroup), ebenso.
                color = if (LocalNewDesign.current && !isDragging && !raised && elevation == 0.dp) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
            )
    )
}

// 🔧 v2.13.0: internal statt private — ChecklistUncheckAnimationInstrumentedTest hostet den
// Editor direkt, um die Collapse-/Placement-Animationen frameweise zu vermessen.
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod") // Compose functions commonly have many callback parameters
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistEditor(
    items: List<ChecklistItemState>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusNewItemId: String?,
    currentSortOption: ChecklistSortOption, // 🔀 v1.8.0: Aktuelle Sortierung
    scrollTopOnUncheck: Boolean, // 🆕 Issue #112: Aufhaken scrollt zum Ziel, sofern es oberhalb des Viewports landet
    checklistScrollAction: SharedFlow<NoteEditorViewModel.ChecklistScrollAction>, // 🆕 v1.9.0 (F14): Scroll action on check/un-check
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onCopyText: (String) -> Unit, // 🆕 v2.2.0: Aktion 1
    onDuplicate: (String) -> Unit, // 🆕 v2.2.0: Aktion 2
    onCopyToChecklist: (String) -> Unit, // 🆕 v2.2.0: Aktion 3
    onAddToCalendar: (String) -> Unit,
    onAddItemAtEnd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onFocusHandled: () -> Unit,
    onSortClick: () -> Unit, // 🔀 v1.8.0
    modifier: Modifier = Modifier,
    readOnly: Boolean = false, // 🆕 Issue #156: Lesemodus, nur Abhaken
    onIndent: (String) -> Unit = {}, // Unterpunkte
    onOutdent: (String) -> Unit = {},
    onDragStarted: (draggedId: String) -> Unit = {},
    onDragFinished: () -> Unit = {}
) {
    // IMPL_29q Q1: LazyLayoutCacheWindow ersetzt beyondBoundsItemCount (entfernt in Compose 1.10+).
    // Hält 0.55 × Viewport auf jeder Seite composiert → 865px Buffer (3 große Items à 271px).
    // Verhindert Composable-Recycling beim Auto-Scroll+Swap → kein toter Pointer-Scope.
    val listState = rememberLazyListState(LazyLayoutCacheWindow(0.55f, 0.55f))
    // Unterpunkte: der erste Tausch eines Ziehens ist der Drag-Start; der gezogene Eintrag steht dabei
    // an `from`, die Liste ist noch unverändert. So kennt das ViewModel die Unterpunkte, bevor ein
    // Tausch sie verschiebt, und legt genau einen Undo-Schritt pro Ziehen an. Das Lambda wird von
    // rememberDragDropListState einmalig übernommen, deshalb nur über die aktuellen States lesen.
    val dragTracker = remember { DragTracker() }
    val currentItemsForDrag by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val dragDropState = rememberDragDropListState(
        lazyListState = listState,
        scope = scope,
        onMove = { from, to ->
            if (!dragTracker.active) {
                dragTracker.active = true
                currentItemsForDrag.getOrNull(from)?.let { currentOnDragStarted(it.id) }
            }
            currentOnMove(from, to)
        }
    )

    // 🔧 v2.5.x: Gate für LazyColumn-Placement-Animationen (Modifier.animateItem).
    // Beim Öffnen einer Checkliste durchläuft die LazyColumn mehrere Layout-Pässe
    // (Text-Messung im OutlinedTextField, dynamische Höhen via onHeightChanged,
    // LazyLayoutCacheWindow-Vorbau). Wenn animateItem in diesem Fenster aktiv ist,
    // animiert LazyListItemAnimator die Offset-Diffs zwischen den Pässen → Items
    // „schieben sich rein". Dieses Gate hält animateItem deaktiviert, bis das
    // Initial-Layout zwei aufeinanderfolgende Frames lang unverändert geblieben
    // ist (oder INITIAL_LAYOUT_SETTLE_TIMEOUT_MS abgelaufen ist). Nach Aktivierung
    // bleibt das Gate für die Lebenszeit des Screens offen — alle späteren Reorder
    // (Check/Uncheck — siehe Commit 7156c12) animieren wie vorgesehen.
    // Userinteraktionen (Tap, Drag) erfolgen lange nach dem Settle → kein Konflikt
    // mit der Check-Tap-Animation aus dem Commit.
    var placementAnimationsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val deadline = withFrameNanos { it } + INITIAL_LAYOUT_SETTLE_TIMEOUT_MS * 1_000_000L
        var previousSnapshot: List<Pair<Any, Int>> = emptyList()
        var stableFrames = 0
        var settled = false
        while (!settled) {
            val now = withFrameNanos { it }
            val current = listState.layoutInfo.visibleItemsInfo.map { it.key to it.offset }
            if (current.isNotEmpty() && current == previousSnapshot) {
                stableFrames++
                if (stableFrames >= 2) settled = true
            } else {
                stableFrames = 0
            }
            previousSnapshot = current
            if (now >= deadline) settled = true
        }
        placementAnimationsEnabled = true
    }

    // 🔧 v2.13.0: Item, dessen Uncheck aufgeschoben ist, während seine Row kollabiert.
    // Genau ein Slot — ein zweiter Uncheck committet den ersten sofort
    // (siehe [onChecklistCheckedChange]).
    var pendingUncheckId by remember { mutableStateOf<String?>(null) }

    // Unterpunkte: Weg des letzten Gruppen-Commits, die Mitläufer folgen ihm (siehe DraggableChecklistItem).
    var groupCommitScrolled by remember { mutableStateOf(false) }

    // Unterpunkte: Beim Abhaken eines Oberpunkts wandert die ganze Gruppe nach unten. Nur der getippte
    // Eintrag bekommt über isCheckAnimating den erhöhten zIndex; ohne ihn kreuzten seine Unterpunkte die
    // nach oben rückenden Nachbarn sichtbar (Text über Text). Für die Dauer der Animation angehoben.
    var liftedGroupIds by remember { mutableStateOf(emptySet<String>()) }

    // Unterpunkte: Zahl der Mitläufer, die gerade am Ziel aufwachsen (siehe DraggableChecklistItem).
    var expandingFollowers by remember { mutableIntStateOf(0) }
    LaunchedEffect(liftedGroupIds) {
        if (liftedGroupIds.isNotEmpty()) {
            delay(CHECK_ANIMATION_TOTAL_MS)
            liftedGroupIds = emptySet()
        }
    }

    // Unterpunkte: beim Aufhaken im erledigten Abschnitt wandert die ganze Gruppe nach oben. Die
    // übrigen Zeilen der Gruppe kollabieren mit, committen aber nicht selbst (siehe DraggableChecklistItem).
    val pendingGroupIds = remember(items, pendingUncheckId) {
        val index = items.indexOfFirst { it.id == pendingUncheckId }
        if (index < 0) emptySet() else ChecklistNesting.groupRange(items, index).map { items[it].id }.toSet()
    }

    // Unterpunkte: Drag-Ende erkennen (draggingItemKey nur lesen, DragDropListState bleibt unberührt).
    // Die Unterpunkte eines gezogenen Oberpunkts bleiben während des Ziehens stehen und rücken beim
    // Loslassen nach. Endet die Composition mitten im Ziehen (Drehen, Zurück), wird trotzdem abgeschlossen.
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    LaunchedEffect(dragDropState) {
        try {
            snapshotFlow { dragDropState.draggingItemKey }.collect { key ->
                if (key == null && dragTracker.active) {
                    dragTracker.active = false
                    currentOnDragFinished()
                }
            }
        } finally {
            if (dragTracker.active) {
                dragTracker.active = false
                currentOnDragFinished()
            }
        }
    }

    // 🔧 Issue #112: Handoff Commit → Scroll-Handler. Die Tap-Animation des Items stirbt
    // auf diesem Pfad doppelt: erst bewusst beim Collapse-Start, dann endgültig, weil das Item
    // beim Commit oberhalb des Viewports landet und seine Composition (inkl. Flip-Detektor)
    // disposed wird. Deshalb triggert der Scroll-Handler nach Ankunft einen frischen
    // Highlight-Pop am Ziel-Item (topHighlightId, konsumiert in DraggableChecklistItem).
    var scrollTopUncheckId by remember { mutableStateOf<String?>(null) }
    var topHighlightId by remember { mutableStateOf<String?>(null) }

    // 🔍 v2.13.0 (nur DEBUG): Trace der Check/Uncheck-Placement-Animation. Wird im
    // onCheckedChange-Callback gesetzt (nach dem Pre-Anchor, vor dem Model-Update),
    // damit der Frame-Trace ab dem ersten Layout-Pass nach dem Reorder läuft.
    var checkTrace by remember { mutableStateOf<CheckTrace?>(null) }
    if (BuildConfig.DEBUG) {
        LaunchedEffect(checkTrace) {
            checkTrace?.let { traceCheckPlacement(listState, it) }
        }
    }

    // 🆕 v1.8.1 (IMPL_05): Auto-Scroll bei Zeilenumbruch
    var scrollToItemIndex by remember { mutableStateOf<Int?>(null) }

    // 🆕 v1.8.2 (IMPL_10): Kontrollierter Scroll zum neuen Item (verhindert Sprung ans Ende)
    var scrollToNewItemIndex by remember { mutableStateOf<Int?>(null) }

    // 🆕 v1.10.0-P2: Minimal scroll — only ensure new item is visible, don't force to top
    LaunchedEffect(scrollToNewItemIndex) {
        scrollToNewItemIndex?.let { index ->
            // Wait one frame for the new item to be laid out
            delay(Constants.CHECKLIST_SCROLL_LAYOUT_DELAY_MS)
            val layoutInfo = listState.layoutInfo
            // Unteres contentPadding liegt unter dem FAB (neues Design): nur bis darüber gilt als sichtbar
            val viewportEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            val viewportStart = layoutInfo.viewportStartOffset
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
            if (itemInfo != null) {
                val itemBottom = itemInfo.offset + itemInfo.size
                val itemTop = itemInfo.offset
                when {
                    itemBottom > viewportEnd ->
                        // Partially below viewport — scroll just enough to show it
                        listState.animateScrollBy((itemBottom - viewportEnd).toFloat())
                    itemTop < viewportStart ->
                        // Partially above viewport — scroll up just enough
                        listState.animateScrollBy((itemTop - viewportStart).toFloat())
                    // else: fully visible → no scroll needed
                }
            } else {
                // Item not yet visible (far away) — fallback: scroll to it
                listState.animateScrollToItem(index)
            }
            scrollToNewItemIndex = null
        }
    }

    // 🆕 v1.9.0 (F14): Scroll action handler for check/un-check
    LaunchedEffect(Unit) {
        checklistScrollAction.collect { action ->
            when (action) {
                is NoteEditorViewModel.ChecklistScrollAction.ScrollToItem -> {
                    // Nur nach einem Collapse, der den Scroll angekündigt hat. Ziel im Bild,
                    // Commit beim Dispose und Uncheck ohne Separator bleiben stehen.
                    val highlightId = scrollTopUncheckId
                    scrollTopUncheckId = null
                    if (BuildConfig.DEBUG) {
                        Logger.d(CHECK_TRACE_TAG, "[SCROLL] ScrollToItem(${action.index}) scroll=${highlightId != null}")
                    }
                    if (highlightId != null) {
                        // Der Collector läuft direkt nach dem Commit, bevor die neue Reihenfolge
                        // composiert ist. scrollToItem misst sofort (forceRemeasure): gegen die alte
                        // Liste wäre der Index ein anderes Item, und das Key-Anchoring nach dem
                        // Commit verschöbe alles um eine Zeile. Deshalb erst den Commit-Pass abwarten.
                        repeat(PLACEMENT_SUPPRESS_FRAMES) { withFrameNanos { } }
                        val land = if (action.index <= SCROLL_TO_START_MAX_INDEX) 0 else action.index
                        if (cutAndGlideTo(listState, land, highlightId, dragDropState.separatorVisualIndex)) {
                            topHighlightId = highlightId
                        }
                    }
                }
                is NoteEditorViewModel.ChecklistScrollAction.NoScroll -> {
                    if (BuildConfig.DEBUG) Logger.d(CHECK_TRACE_TAG, "[SCROLL] NoScroll")
                    // Check → intentionally do nothing.
                    // LazyColumn uses stable keys (item.id), so Compose preserves
                    // the scroll position naturally during recomposition.
                }
            }
        }
    }

    // 🆕 v1.8.2 (IMPL_10): Berechne Visual-Index für neues Item bei focusNewItemId
    LaunchedEffect(focusNewItemId) {
        focusNewItemId?.let { itemId ->
            val dataIndex = items.indexOfFirst { it.id == itemId }
            if (dataIndex >= 0) {
                val hasSeparator = currentSortOption == ChecklistSortOption.MANUAL ||
                    currentSortOption == ChecklistSortOption.UNCHECKED_FIRST ||
                    currentSortOption == ChecklistSortOption.CREATION_DATE ||
                    currentSortOption == ChecklistSortOption.CREATION_DATE_DESC
                val unchecked = ChecklistNesting.openRowCount(items)
                val visualIndex = if (hasSeparator && dataIndex >= unchecked) {
                    dataIndex + 1 // +1 für Separator
                } else {
                    dataIndex
                }
                scrollToNewItemIndex = visualIndex
            }
        }
    }

    // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Separator nur bei MANUAL, UNCHECKED_FIRST und CREATION_DATE anzeigen
    // Unterpunkte: Trenner nach den offenen Gruppen; abgehakte Unterpunkte offener Oberpunkte stehen darüber
    val uncheckedCount = ChecklistNesting.openRowCount(items)
    val checkedCount = items.size - uncheckedCount
    val shouldShowSeparator = currentSortOption == ChecklistSortOption.MANUAL ||
        currentSortOption == ChecklistSortOption.UNCHECKED_FIRST ||
        currentSortOption == ChecklistSortOption.CREATION_DATE ||
        currentSortOption == ChecklistSortOption.CREATION_DATE_DESC
    val showSeparator = shouldShowSeparator &&
        (
            (uncheckedCount > 0 && checkedCount > 0) ||
                // 🆕 v1.8.2 (IMPL_26): Separator während Drag beibehalten wenn er vorher sichtbar war.
                // Wenn das letzte Item einer Seite über den Separator gezogen wird, wird ein Count 0.
                // Ohne diesen Guard verschwindet der Separator → visualItemCount ändert sich →
                // draggingItemIndex zeigt auf falschen Slot → Drag bricht ab.
                // dragDropState.separatorVisualIndex hat noch den Wert der VORHERIGEN Composition
                // (SideEffect läuft erst nach Composition) → >= 0 = Separator war vorher sichtbar.
                (dragDropState.isAnyItemDragging && dragDropState.separatorVisualIndex >= 0)
            )

    Column(modifier = modifier) {
        // 🆕 v1.8.1 IMPL_14: Separator-Position für DragDropState aktualisieren
        // 🆕 v1.8.2 (IMPL_26): SideEffect statt LaunchedEffect — synchron nach Composition,
        // damit separatorVisualIndex sofort aktuell ist für den nächsten onDrag-Event
        val separatorVisualIndex = if (showSeparator) uncheckedCount else -1
        SideEffect {
            dragDropState.separatorVisualIndex = separatorVisualIndex
            if (BuildConfig.DEBUG && dragDropState.isAnyItemDragging) {
                Logger.d("DragDrop", "[SEPARATOR] idx=$separatorVisualIndex")
            }
        }

        // 🆕 v1.8.1 + v1.8.2 (IMPL_10): Viewport-aware Auto-Scroll bei Zeilenwachstum
        // Scrollt pixel-genau um die Differenz, statt zum nächsten Item zu springen
        LaunchedEffect(scrollToItemIndex) {
            scrollToItemIndex?.let { index ->
                delay(AUTO_SCROLL_DELAY_MS) // Warten bis Layout-Pass abgeschlossen
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == index }
                if (itemInfo != null) {
                    // Unteres contentPadding liegt unter dem FAB (neues Design)
                    val viewportEnd = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
                    val itemBottom = itemInfo.offset + itemInfo.size
                    if (itemBottom > viewportEnd) {
                        // Item ragt unter den sichtbaren Bereich — genau um die Differenz scrollen
                        listState.scroll { scrollBy((itemBottom - viewportEnd).toFloat()) }
                    }
                }
                scrollToItemIndex = null
            }
        }

        // UI-Beta: Hinzufügen als FAB über der Liste statt eigener Zeile darunter.
        val addFab = LocalNewDesign.current && !readOnly
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Unten Platz für den FAB (56 dp + 16 dp Rand + Luft), damit die letzte Zeile samt ✕ frei liegt.
                contentPadding = PaddingValues(top = 8.dp, bottom = if (addFab) 88.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // IMPL_29f F2: Während Drag: User-Scroll deaktivieren.
                // Verhindert Gesture-Konkurrenz zwischen LazyColumn-Scroll und Drag-Gesture (RC-2).
                // Scrollen erfolgt ausschließlich über programmatisches Auto-Scroll.
                userScrollEnabled = !dragDropState.isAnyItemDragging
            ) {
                // 🆕 v1.8.2 (IMPL_26): Unified items-Block statt drei getrennte Blöcke.
                // Bei getrennten itemsIndexed-Blöcken für unchecked/checked Items wird die
                // Composition zerstört wenn ein Item den Separator überschreitet (anderer
                // Content-Provider) → PointerInput wird destroyed → Drag abgebrochen.
                // Ein einziger items-Block bewahrt die Composition bei Key-Erhalt → Drag bleibt aktiv.
                val visualItemCount = if (showSeparator) items.size + 1 else items.size

                // Lokale Konvertierung mit aktuellem separatorVisualIndex (nicht vom dragDropState,
                // der hat ggf. noch den alten Wert bis SideEffect läuft)
                val localVisualToDataIndex = { visualIndex: Int ->
                    if (!showSeparator || separatorVisualIndex < 0) {
                        visualIndex
                    } else if (visualIndex > separatorVisualIndex) {
                        visualIndex - 1
                    } else {
                        visualIndex
                    }
                }
                // Unterpunkte: Key am visuellen Index wie im items-Block (für den Anker beim Uncheck-Commit)
                val keyAtVisual = { visualIndex: Int ->
                    if (showSeparator && visualIndex == separatorVisualIndex) {
                        SEPARATOR_ITEM_KEY
                    } else {
                        items.getOrNull(localVisualToDataIndex(visualIndex))?.id
                    }
                }

                items(
                    count = visualItemCount,
                    key = { visualIndex ->
                        if (showSeparator && visualIndex == separatorVisualIndex) {
                            "separator"
                        } else {
                            items[localVisualToDataIndex(visualIndex)].id
                        }
                    },
                    contentType = { visualIndex ->
                        if (showSeparator && visualIndex == separatorVisualIndex) {
                            "separator"
                        } else {
                            "checklist_item"
                        }
                    }
                ) { visualIndex ->
                    if (showSeparator && visualIndex == separatorVisualIndex) {
                        CheckedItemsSeparator(
                            checkedCount = checkedCount,
                            isDragActive = dragDropState.isAnyItemDragging,
                            // Unterpunkte: beim Aufwachsen einer Gruppe direkt dem Layout folgen (siehe DraggableChecklistItem)
                            modifier = if (placementAnimationsEnabled && expandingFollowers == 0) Modifier.animateItem() else Modifier
                        )
                    } else {
                        val dataIndex = localVisualToDataIndex(visualIndex)
                        val item = items[dataIndex]
                        DraggableChecklistItem(
                            item = item,
                            dragDropState = dragDropState,
                            focusNewItemId = focusNewItemId,
                            onTextChange = onTextChange,
                            onCheckedChange = { id, checked ->
                                if (BuildConfig.DEBUG) {
                                    checkTrace = CheckTrace(System.nanoTime(), id, checked)
                                }
                                val index = items.indexOfFirst { it.id == id }
                                // Ein Oberpunkt nimmt beim Abhaken seine Gruppe mit, ein Unterpunkt bewegt nur sich
                                val movingIds = if (index >= 0 && items[index].indentationLevel == 0) {
                                    ChecklistNesting.groupRange(items, index).map { items[it].id }.toSet()
                                } else {
                                    setOf(id)
                                }
                                if (checked && movingIds.size > 1) liftedGroupIds = movingIds
                                onChecklistCheckedChange(
                                    listState = listState,
                                    itemId = id,
                                    checked = checked,
                                    // Unterpunkte: nur Zeilen im erledigten Abschnitt wandern beim Aufhaken
                                    uncheckReorders = separatorVisualIndex >= 0 && ChecklistNesting.inCheckedSection(items, index),
                                    pendingUncheckId = pendingUncheckId,
                                    pendingGroupIds = pendingGroupIds,
                                    keyAt = keyAtVisual,
                                    movingIds = movingIds,
                                    setPendingUncheckId = {
                                        pendingUncheckId = it
                                        if (it != null) groupCommitScrolled = false
                                    },
                                    commitChecked = onCheckedChange
                                )
                            },
                            onDelete = onDelete,
                            onAddNewItemAfter = onAddNewItemAfter,
                            onCopyText = onCopyText, // 🆕 v2.2.0
                            onDuplicate = onDuplicate, // 🆕 v2.2.0
                            onCopyToChecklist = onCopyToChecklist, // 🆕 v2.2.0
                            onAddToCalendar = onAddToCalendar,
                            onFocusHandled = onFocusHandled,
                            onHeightChanged = { scrollToItemIndex = visualIndex },
                            placementAnimationsEnabled = placementAnimationsEnabled,
                            isPendingUncheck = item.id == pendingUncheckId,
                            followsPendingUncheck = item.id != pendingUncheckId && item.id in pendingGroupIds,
                            liftedWithGroup = item.id in liftedGroupIds,
                            groupExpanding = expandingFollowers > 0,
                            onFollowerExpanding = { expandingFollowers += it },
                            groupCommitScrolled = { groupCommitScrolled },
                            canIndent = ChecklistNesting.canIndent(items, dataIndex),
                            onIndent = onIndent,
                            onOutdent = onOutdent,
                            scrollsAfterUncheck = { id ->
                                // Ziel oberhalb des Viewports (oder an der ersten sichtbaren Zeile: ein
                                // Einfügen vor dem ersten sichtbaren Key landet oberhalb) → Scroll.
                                // Sonst wächst die Row am Ziel auf wie ohne Scroll-to-Top.
                                // Unterpunkte: Ziel ist der Oberpunkt der wandernden Gruppe.
                                // Steht die Liste ganz oben, landet ein Einfügen an Index 0 sichtbar oben
                                // (gemessen: first=0@0, glide=0): dann wie ohne Scroll am Ziel aufwachsen,
                                // sonst stünde die Zeile sofort in voller Höhe und die Nachbarn federten nach.
                                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                                scrollTopOnUncheck &&
                                    !atTop &&
                                    sortChecklistStates(
                                        items.nested { ChecklistNesting.toggle(it, id, false) },
                                        currentSortOption
                                    ).let { sorted ->
                                        ChecklistNesting.groupStart(sorted, sorted.indexOfFirst { it.id == id }.coerceAtLeast(0))
                                    } <= listState.firstVisibleItemIndex
                            },
                            onUncheckCommit = { id, scroll ->
                                groupCommitScrolled = scroll
                                if (scroll) scrollTopUncheckId = id
                                commitDeferredUncheck(
                                    listState = listState,
                                    toggledId = id,
                                    commitChecked = onCheckedChange,
                                    reanchor = !scroll,
                                    movingIds = pendingGroupIds,
                                    keyAt = keyAtVisual
                                )
                                pendingUncheckId = null
                            },
                            topHighlightId = topHighlightId,
                            onTopHighlightShown = { topHighlightId = null },
                            readOnly = readOnly
                        )
                    }
                }
            }
            if (addFab) {
                FloatingActionButton(
                    onClick = onAddItemAtEnd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item))
                }
            }
        }

        // 🔀 v1.8.0: Add Item Button + Sort Button
        // 🆕 Issue #156: im Lesemodus ausgeblendet, die Liste nutzt die volle Höhe
        // UI-Beta: FAB und ⋮-Menü ersetzen die Zeile, sie bleibt nur im alten Design.
        if (!readOnly && !LocalNewDesign.current) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = onAddItemAtEnd) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.add_item))
                }

                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(R.string.sort_checklist),
                        modifier = androidx.compose.ui.Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}
