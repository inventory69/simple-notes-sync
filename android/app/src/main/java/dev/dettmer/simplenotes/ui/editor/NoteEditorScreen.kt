package dev.dettmer.simplenotes.ui.editor

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.HtmlToMarkdown
import dev.dettmer.simplenotes.markdown.ImageAlign
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownOutputTransformation
import dev.dettmer.simplenotes.markdown.MarkdownPreview
import dev.dettmer.simplenotes.markdown.computeImageRewrite
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.components.CheckedItemsSeparator
import dev.dettmer.simplenotes.ui.editor.components.ChecklistItemRow
import dev.dettmer.simplenotes.ui.editor.components.ChecklistSortDialog
import dev.dettmer.simplenotes.ui.editor.components.ChecklistTargetPickerDialog
import dev.dettmer.simplenotes.ui.editor.components.MarkdownToolbar
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.theme.LocalFontSizeMultiplier
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🆕 v2.12.0: Obergrenze Multi-Bild-Picker (Beta-Feedback: mehrere Bilder auf einmal)
private const val MAX_PICKER_IMAGES = 10

private const val LAYOUT_DELAY_MS = 100L
private const val AUTO_SCROLL_DELAY_MS = 50L
private const val ITEM_CORNER_RADIUS_DP = 8
private const val DRAGGING_ITEM_Z_INDEX = 10f

// 🆕 v2.5.0: Z-Index für Items in Check/Uncheck-Animation. Liegt zwischen 0f
// (Standard-Items) und DRAGGING_ITEM_Z_INDEX (10f). Hebt das animierende Item
// während der LazyColumn-Placement-Animation über seine Nachbarn — behebt die
// Asymmetrie, dass aufwärts wandernde Items (Uncheck) sonst hinter Nachbarn
// gezeichnet werden, weil ihre neue (niedrigere) Composition-Position sie früher
// im Draw-Tree platziert. Drag dominiert weiterhin (10f > 5f).
//
private const val CHECKING_ITEM_Z_INDEX = 5f

// 🆕 v2.5.0: Gesamt-Dauer der Check-Tap-Animation in Millisekunden. Deckt
// Scale-Up (CHECK_ANIM_SCALE_UP_MS = 80ms) + Spring-Back + Glow-Fade-Out
// (CHECK_GLOW_FADE_OUT_MS = 450ms) + LazyColumn-Placement-Spring komfortabel ab.
// Wird vom LaunchedEffect in DraggableChecklistItem genutzt, um isCheckAnimating
// zurückzusetzen (deterministischer Reset statt finishedListener-Race).
private const val CHECK_ANIMATION_TOTAL_MS = 500L

// 🔧 v2.5.x: Maximale Wartezeit, bis das initiale LazyColumn-Layout als „stabil"
// gilt. Sicherheitsnetz für den Settle-LaunchedEffect in ChecklistEditor: falls externe
// Effekte (Text-Messung, dynamische Höhen, Sync-Updates) die Liste länger als diese
// Schwelle in Bewegung halten, wird das Placement-Animation-Gate trotzdem geöffnet,
// damit Reorder-Animationen nach Check/Uncheck garantiert spielen.
// 250 ms ≈ 15 Frames @ 60Hz — genug Puffer für Cold-Open auch auf langsamen
// Geräten, kurz genug, dass User es nicht wahrnehmen.
private const val INITIAL_LAYOUT_SETTLE_TIMEOUT_MS = 250L
private val DRAGGING_ELEVATION_DP = 8.dp
private const val CHECKBOX_TAP_PREFIX_COLS = 5

// 🔧 v2.13.0: Dauer des Eigen-Collapse beim Uncheck mit Ziel außerhalb des Viewports.
// Kurz genug, dass der Eintrag nicht „nachhängt", lang genug, dass das Nachrücken der
// Nachbarn als Bewegung lesbar bleibt. Siehe [DraggableChecklistItem].
private const val UNCHECK_COLLAPSE_MS = 250

// Nachlauf zwischen Collapse-Ende und Commit: so lange braucht die animateItem-Spring der
// Nachbarn, um die letzten Pixel aufzuholen. Ohne den Nachlauf schneidet der Commit sie ab.
private const val UNCHECK_COMMIT_SETTLE_MS = 200L

// Frames, die nach dem Snap eines Commit (Scroll-to-Top-Pfad, kein Expand) ohne animateItem
// durchlaufen müssen: einer für den Commit-Layout-Pass selbst, einer als Puffer.
private const val PLACEMENT_SUPPRESS_FRAMES = 2

// 🔧 Issue #112: ScrollToTop = Cut + pixelgenauer Glide statt animateScrollToItem(0).
// animateScrollToItem schätzt lange Distanzen über Durchschnittshöhen und korrigiert
// mehrphasig nach (Retargeting) — bei mehrzeiligen Einträgen las sich das als
// „zweistufiges" Hochscrollen. Stattdessen: liegt der Viewport weiter als dieser Index
// vom Anfang entfernt, instant auf exakt eine Viewport-Höhe vor den Anfang schneiden
// (der übersprungene Inhalt ist off-screen) und die bekannte Rest-Distanz in einem
// einzigen animateScrollBy gleiten — nichts zu schätzen, eine Bewegung, konstante Dauer.
private const val SCROLL_TOP_CUT_THRESHOLD_INDEX = 3

// Dauer des Glide über die letzte Viewport-Höhe. FastOutSlowIn startet schnell und
// maskiert damit den vorausgehenden Cut.
private const val SCROLL_TOP_GLIDE_MS = 350

/** Key des Separator-Slots in der LazyColumn — nie ein Item-Key. */
private const val SEPARATOR_ITEM_KEY = "separator"

// 🔍 v2.13.0 (nur DEBUG): Frame-Trace der Check/Uncheck-Placement-Animation.
// Zweck: nachvollziehen, warum ein nach oben wandernder Eintrag am Viewport-Rand
// „hängen bleibt", statt durchzulaufen. Filter: `adb logcat -s ChecklistAnim:D`.
private const val CHECK_TRACE_TAG = "ChecklistAnim"

// Feste Trace-Länge. Muss über CHECK_ANIMATION_TOTAL_MS (500 ms, zIndex-Reset) hinausgehen,
// sonst endet der Trace vor dem Moment, in dem das Item „verschwindet".
private const val CHECK_TRACE_DURATION_MS = 700L

// Item-IDs sind UUIDs — die ersten Zeichen reichen zum Zuordnen im Log.
private const val CHECK_TRACE_ID_LEN = 8
private const val NANOS_PER_MS = 1_000_000L

/** 🔍 DEBUG: Trace-Trigger. `tapNanos` macht jeden Tap eindeutig → LaunchedEffect startet neu. */
private data class CheckTrace(val tapNanos: Long, val itemId: String, val checked: Boolean)

/**
 * Verankert die LazyColumn **vor** dem Model-Reorder, damit LazyListItemAnimator seine
 * from/to-Koordinaten gegen einen stabilen Anker misst. `requestScrollToItem` läuft
 * synchron im nächsten Measure-Pass und löscht `LazyListState.lastKnownFirstItemKey` —
 * ohne das würde die key-basierte Re-Anchor-Logik dem getoggelten Item an seine neue
 * Position folgen und den Viewport mitziehen (→ Animation über ~0 sichtbares Delta).
 *
 * Zwei Viewport-Regime:
 *
 * **A) Viewport ganz oben** (`firstVisibleItemIndex == 0`): Index 0 an seiner exakten
 * Scroll-Position pinnen. Bewährter Pfad aus 7156c12. `requestScrollToItem(0, 0)` wäre
 * falsch: der Sprung um `firstVisibleItemScrollOffset` (92–175 px) landet im selben
 * Measure-Pass wie der Reorder, LazyListItemAnimator deutet das als Scroll statt Reorder
 * und überspringt die Placement-Animation komplett.
 *
 * **B1) Viewport weiter unten, getoggeltes Item ist das erste sichtbare**: Auf den Nachbarn
 * darunter pinnen, sonst folgt LazyLists Re-Anchoring dem Item.
 *
 * **B2) Viewport weiter unten, getoggeltes Item ist NICHT das erste sichtbare**: gar nicht
 * ankern. LazyList pinnt von sich aus das erste sichtbare Item an seinem Key fest — das ist
 * exakt das gewünschte Verhalten, und jeder `requestScrollToItem`-Aufruf mit einem Index
 * abseits von `firstVisibleItemIndex` lässt die Layout-Änderung für LazyListItemAnimator
 * wie einen Scroll aussehen → Placement-Animation entfällt komplett.
 *
 * Messung v2.13.0: Anker auf `first visible` (Index 8) → Animation lief, aber der Pin traf
 * nach dem Reorder ein anderes Item → Inhalt sprang um eine Item-Höhe. Anker auf ein Item
 * unterhalb (Index 13, pixelgenau korrekt) → Layout stimmte, dafür verschwand das Item ohne
 * jede Animation. Ohne Pre-Anchor entfällt beides.
 *
 * 🔧 v2.13.0: Nur noch für den **Check**-Pfad (Item wandert nach unten) und den seltenen
 * Uncheck ohne Separator-Reorder — der Uncheck mit Reorder läuft über den Collapse-Pfad
 * (siehe [onChecklistCheckedChange]) und braucht keinen Pre-Anchor mehr.
 */
private fun preAnchorForToggle(
    listState: LazyListState,
    itemKey: String,
    checked: Boolean
) {
    if (listState.firstVisibleItemIndex == 0) {
        val preservedOffset = listState.firstVisibleItemScrollOffset
        if (BuildConfig.DEBUG) logCheckTap(listState, itemKey, checked, "A(0,$preservedOffset)")
        listState.requestScrollToItem(0, preservedOffset)
        return
    }
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.firstOrNull()?.key != itemKey) {
        if (BuildConfig.DEBUG) logCheckTap(listState, itemKey, checked, "B2(kein Pre-Anchor)")
        return
    }
    val anchor = visible.firstOrNull { info -> info.key != itemKey }
    if (BuildConfig.DEBUG) {
        logCheckTap(listState, itemKey, checked, "B1(${anchor?.index},${anchor?.offset})")
    }
    if (anchor != null) {
        listState.requestScrollToItem(anchor.index, -anchor.offset)
    }
}

/**
 * 🔧 v2.13.0: Commit des aufgeschobenen Uncheck, nachdem die Row selbst auf Höhe 0 kollabiert ist.
 *
 * Vorher wird der Viewport neu verankert: Das Item verlässt seinen (jetzt 0 px hohen) Slot und
 * wird an seiner neuen Position in der unchecked-Sektion eingefügt — meist oberhalb des
 * Viewports. Ohne Anker würde LazyLists key-basiertes Re-Anchoring dem getoggelten Item folgen
 * und den Inhalt um eine Item-Höhe verschieben.
 *
 * Der Index wird **nicht** um die Reorder-Verschiebung korrigiert: `requestScrollToItem` greift
 * im Measure-Pass gegen den Indexraum von **vor** dem Reorder — gleiche Konvention wie B1 in
 * [preAnchorForToggle]. Ein `+1` verschob den Inhalt messbar um genau eine Zeile zu weit
 * (ChecklistUncheckAnimationInstrumentedTest, Teleport-Detektor).
 *
 * Dass `requestScrollToItem` Placement-Animationen für diesen Measure-Pass unterdrückt, ist hier
 * **erwünscht**: die Quelle ist 0 px hoch, und an einem sichtbaren Ziel wächst die Row selbst
 * wieder auf (Collapse-Gegenstück in [DraggableChecklistItem]) — der Animator hätte sonst wieder
 * einen Geist am Rand geparkt.
 *
 * 🔧 Issue #112: `reanchor = false` auf dem Scroll-to-Top-Pfad. Der Anker dient nur der
 * Viewport-Stabilität nach dem Commit — die Position wird hier aber im selben Atemzug per
 * Cut+Glide verlassen. Ein pending `requestScrollToItem` konnte dem bereits laufenden
 * `animateScrollToItem` dazwischenfunken (Viewport-Ruck zurück nach unten = sichtbare Stufe).
 */
private fun commitDeferredUncheck(
    listState: LazyListState,
    toggledId: String,
    commitChecked: (String, Boolean) -> Unit,
    reanchor: Boolean = true
) {
    val visible = listState.layoutInfo.visibleItemsInfo
    val anchor = if (reanchor && visible.firstOrNull()?.key == toggledId) {
        visible.firstOrNull { it.key != toggledId && it.key != SEPARATOR_ITEM_KEY }
    } else {
        null
    }
    if (BuildConfig.DEBUG) {
        Logger.d(
            CHECK_TRACE_TAG,
            "[COMMIT] id=${toggledId.take(CHECK_TRACE_ID_LEN)} anchor=${anchor?.let { "${it.index}@${it.offset}" } ?: "keiner (B2)"}"
        )
    }
    if (anchor != null) {
        listState.requestScrollToItem(anchor.index, -anchor.offset)
    }
    commitChecked(toggledId, false)
}

/**
 * 🔧 Issue #112: ScrollToTop als Cut auf exakt eine Viewport-Höhe vor den Anfang + ein
 * einziger pixelgenauer Glide über genau diese Distanz (siehe
 * [SCROLL_TOP_CUT_THRESHOLD_INDEX]). Nahe am Anfang (Cut würde sichtbar rückwärts
 * schneiden) stattdessen direkt animateScrollToItem — über wenige Items bleibt das
 * einphasig.
 *
 * @return true, wenn [itemKey] nach der Ankunft sichtbar ist — nur dann darf der
 * Highlight-Pop feuern; ein liegengebliebener Trigger feuerte sonst später beim
 * manuellen Scrollen.
 */
private suspend fun cutAndGlideToTop(listState: LazyListState, itemKey: String?): Boolean {
    if (listState.firstVisibleItemIndex > SCROLL_TOP_CUT_THRESHOLD_INDEX) {
        val glidePx = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        listState.scrollToItem(index = 0, scrollOffset = glidePx)
        listState.animateScrollBy(
            value = -glidePx.toFloat(),
            animationSpec = tween(SCROLL_TOP_GLIDE_MS, easing = FastOutSlowInEasing)
        )
    } else {
        listState.animateScrollToItem(index = 0, scrollOffset = 0)
    }
    return itemKey != null && listState.layoutInfo.visibleItemsInfo.any { it.key == itemKey }
}

/**
 * 🔧 v2.13.0: Pfad-Weiche beim Checkbox-Tap. Drei Fälle:
 *
 * 1. **Re-Tap auf das gerade kollabierende Item** → Abbruch. Das Model wurde nie geändert,
 *    es genügt, den Pending-Slot zu leeren; die Row klappt wieder auf.
 * 2. **Uncheck mit Separator-Reorder** (`separatorVisualIndex >= 0`) → **immer** aufschieben:
 *    die Row besitzt die Exit-Animation selbst (Collapse an Ort und Stelle), Commit erst
 *    danach. Ein Aufwärts-Placement durch LazyLayoutItemAnimator findet nie statt — der ist
 *    für diese Richtung nachweislich nicht robust: Ziel off-screen parkt einen „Geist" am
 *    Viewport-Rand, und parallel zum ScrollToTop entstehen Einschiebe-Artefakte bei den
 *    Nachbarn. Das frühere Kriterium „nur wenn Ziel off-screen" ließ genau diese Fälle durch.
 *    Ist Scroll-to-Top aktiv, feuert das ViewModel die Aktion erst beim Commit — der Scroll
 *    läuft also nach dem Collapse und kollidiert mit keiner Placement-Animation mehr.
 * 3. **Check — und Uncheck ohne Separator** (Sortierungen ohne Auto-Sort: das Item bewegt
 *    sich gar nicht; sowie die komplett abgehakte Liste) → unveränderter Pfad: Pre-Anchor +
 *    sofortiger Commit ins ViewModel.
 *
 * Der Pending-Slot hält genau ein Item. Ein zweiter Uncheck während eines laufenden Collapse
 * committet den ersten sofort — kein Tap darf verloren gehen.
 */
@Suppress("LongParameterList") // Compose-State + Callbacks; ein Wrapper-Objekt brächte nichts
private fun onChecklistCheckedChange(
    listState: LazyListState,
    itemId: String,
    checked: Boolean,
    separatorVisualIndex: Int,
    pendingUncheckId: String?,
    setPendingUncheckId: (String?) -> Unit,
    commitChecked: (String, Boolean) -> Unit
) {
    if (checked && itemId == pendingUncheckId) {
        setPendingUncheckId(null)
        return
    }
    if (pendingUncheckId != null && pendingUncheckId != itemId) {
        commitDeferredUncheck(listState, pendingUncheckId, commitChecked)
    }
    if (!checked && separatorVisualIndex >= 0) {
        setPendingUncheckId(itemId)
        return
    }
    setPendingUncheckId(null)
    preAnchorForToggle(listState, itemId, checked)
    commitChecked(itemId, checked)
}

/**
 * 🔍 DEBUG: Zustand **vor** dem Reorder (Layout ist zu diesem Zeitpunkt noch der alte —
 * `requestScrollToItem` greift erst im nächsten Measure-Pass).
 */
private fun logCheckTap(
    listState: LazyListState,
    itemId: String,
    checked: Boolean,
    anchor: String
) {
    val info = listState.layoutInfo
    val self = info.visibleItemsInfo.firstOrNull { it.key == itemId }
    Logger.d(
        CHECK_TRACE_TAG,
        "[TAP] id=${itemId.take(CHECK_TRACE_ID_LEN)} checked=$checked anchor=$anchor " +
            (self?.let { "idx=${it.index} top=${it.offset} h=${it.size}" } ?: "item=offscreen") +
            " vp=[${info.viewportStartOffset},${info.viewportEndOffset}]" +
            " first=${listState.firstVisibleItemIndex}/${listState.firstVisibleItemScrollOffset}" +
            " visible=${info.visibleItemsInfo.size} total=${info.totalItemsCount}"
    )
    Logger.d(CHECK_TRACE_TAG, "[WIN-BEFORE] ${windowDump(info)}")
}

/** 🔍 DEBUG: `index:key@offset+höhe` aller platzierten Items — zeigt Index-Verschiebung und Überlappung. */
private fun windowDump(info: LazyListLayoutInfo): String =
    info.visibleItemsInfo.joinToString(" ") {
        "${it.index}:${it.key.toString().take(CHECK_TRACE_ID_LEN)}@${it.offset}+${it.size}"
    }

/**
 * 🔍 DEBUG: Loggt [CHECK_TRACE_DURATION_MS] lang pro Frame die Position des getoggelten Items
 * relativ zum Viewport. Feste Länge statt Abbruch bei stabiler Position: der interessante
 * Moment (zIndex-Reset nach CHECK_ANIMATION_TOTAL_MS) liegt hinter der Ruhephase.
 *
 * `GONE` heißt: Item ist nicht mehr in `visibleItemsInfo` — sichtbar sein kann es trotzdem
 * noch, wenn der Item-Animator es über den Rand hinaus zeichnet.
 */
private suspend fun traceCheckPlacement(listState: LazyListState, trace: CheckTrace) {
    var lastOffset: Int? = null
    var frame = 0
    while (System.nanoTime() - trace.tapNanos < CHECK_TRACE_DURATION_MS * NANOS_PER_MS) {
        withFrameNanos { it }
        val info = listState.layoutInfo
        val self = info.visibleItemsInfo.firstOrNull { it.key == trace.itemId }
        lastOffset = self?.offset
        Logger.d(
            CHECK_TRACE_TAG,
            "[F$frame] +${(System.nanoTime() - trace.tapNanos) / NANOS_PER_MS}ms " +
                (self?.let { "idx=${it.index} top=${it.offset} bottom=${it.offset + it.size}" } ?: "GONE") +
                " vp=[${info.viewportStartOffset},${info.viewportEndOffset}]" +
                " win=[${info.visibleItemsInfo.firstOrNull()?.index}..${info.visibleItemsInfo.lastOrNull()?.index}]" +
                " sep=${info.visibleItemsInfo.firstOrNull { it.key == "separator" }?.offset ?: "-"}" +
                " first=${listState.firstVisibleItemIndex}/${listState.firstVisibleItemScrollOffset}" +
                " scrolling=${listState.isScrollInProgress}"
        )
        // Fenster-Dump nur im ersten Frame — danach reicht die Kompaktzeile.
        if (frame == 0) Logger.d(CHECK_TRACE_TAG, "[WIN-AFTER] ${windowDump(info)}")
        frame++
    }
    Logger.d(
        CHECK_TRACE_TAG,
        "[END] id=${trace.itemId.take(CHECK_TRACE_ID_LEN)} checked=${trace.checked} frames=$frame finalTop=$lastOffset"
    )
    Logger.d(CHECK_TRACE_TAG, "[WIN-END] ${windowDump(listState.layoutInfo)}")
}

/**
 * Main Composable for the Note Editor screen.
 *
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * - Supports both TEXT and CHECKLIST notes
 * - Drag & Drop reordering for checklist items
 * - Auto-keyboard focus for new items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()

    // 🔧 v2.3.0: Block ALL rendering until async note load completes.
    // Must be before any remember/LaunchedEffect blocks so they never
    // execute with stale UiState defaults (noteType=TEXT, isNewNote=true).
    if (uiState.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {}
        return
    }

    // 🆕 v2.0.1: Markdown Preview default for existing TEXT notes
    // v2.8.0: savedPreviewMode persists across TEXT↔CHECKLIST type changes so Undo
    // can restore the exact mode the user was in before the conversion — not just the
    // global default. Initialized from the user preference so first open is correct.
    var savedPreviewMode by remember { mutableStateOf(uiState.defaultStartInPreviewMode) }
    // Both isNewNote and noteType are keys so the value is recomputed synchronously
    // (in the same frame) whenever the type changes — avoids a one-frame flash.
    var isPreviewMode by remember(uiState.isNewNote, uiState.noteType) {
        mutableStateOf(
            when {
                uiState.isNewNote -> false
                uiState.noteType == NoteType.CHECKLIST -> false
                else -> savedPreviewMode // TEXT: restore saved value (covers Undo)
            }
        )
    }
    var showChecklistSortDialog by remember { mutableStateOf(false) } // 🔀 v1.8.0
    val lastChecklistSortOption by viewModel.lastChecklistSortOption.collectAsState() // 🔀 v1.8.0
    val autosaveIndicatorVisible by viewModel.autosaveIndicatorVisible.collectAsState() // 🆕 v1.9.0
    val canUndo by viewModel.canUndo.collectAsState() // 🆕 v1.10.0
    val canRedo by viewModel.canRedo.collectAsState() // 🆕 v1.10.0
    var showOverflowMenu by remember { mutableStateOf(false) } // 🆕 v1.10.0-Papa
    var showColorPicker by remember { mutableStateOf(false) } // 🆕 v2.5.0
    var showConvertDialog by remember { mutableStateOf(false) }
    var focusNewItemId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 🆕 v2.2.0: Checklist Item Context Menu — State für Aktion 3
    var copyToChecklistItemId by remember { mutableStateOf<String?>(null) }
    val otherChecklists by viewModel.otherChecklists.collectAsState()
    val clipboard = LocalClipboard.current

    // v2.0.1: Compact toolbar for narrow displays or large font scale (Issue #48)
    // Uses LocalWindowInfo (preferred for foldable/multi-window) over LocalConfiguration.
    // effectiveWidth = window dp / fontScale — if < 360, Undo/Redo go to overflow and title is shortened
    val isCompactToolbar = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp().value / fontScale < 360f
    }

    // 🆕 v2.5.0: Resolve note accent colour for the 3 dp stripe below the TopAppBar.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val noteAccentColor: Color? = NoteColorPalette
        .resolveContainer(uiState.color, isDark)
        .takeIf { it != Color.Unspecified }

    // Strings for toast messages (avoid LocalContextGetResourceValueCall lint)
    val msgNoteIsEmpty = stringResource(R.string.note_is_empty)
    val msgNoteSaved = stringResource(R.string.note_saved)
    val msgNoteDeleted = stringResource(R.string.note_deleted)
    val msgItemCopiedToChecklist = stringResource(R.string.checklist_item_copied_toast) // 🆕 v2.2.0
    val msgNoteCopied = stringResource(R.string.toast_note_copied)

    // v1.5.0: Auto-keyboard support
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    var isContentFocused by remember { mutableStateOf(false) }

    // 🆕 v1.9.0 (F07): Lifted TextFieldState for toolbar access
    val textFieldState = rememberTextFieldState(initialText = uiState.content)

    // 🆕 Bild-Attachments: Photo-Picker → ViewModel verarbeitet + speichert → Markdown einfügen
    val isAttachingImage by viewModel.isAttachingImage.collectAsState()
    val imagePickerLauncher = rememberImagePickerLauncher(viewModel, textFieldState, scope)

    // v2.0.0: Register content provider so saveOnBack() can read the latest
    // TextFieldState content directly — avoids snapshotFlow race condition
    DisposableEffect(textFieldState) {
        viewModel.contentProvider = { textFieldState.text.toString() }
        onDispose { viewModel.contentProvider = null }
    }

    // Cursor ans Ende setzen wenn Content geladen wird (einmalig)
    // 🔧 v2.3.0 (FIX-011): Sync TextFieldState when content arrives from async storage load.
    // rememberTextFieldState only uses initialText on first call; this LaunchedEffect handles
    // the case where uiState.content is updated after first composition.
    LaunchedEffect(uiState.content) {
        if (textFieldState.text.isEmpty() && uiState.content.isNotEmpty()) {
            textFieldState.edit {
                replace(0, length, uiState.content)
                placeCursorAtEnd()
            }
        }
    }

    // 🆕 v1.9.0 (F07): Auto-show keyboard when switching from preview → edit
    // v2.8.0: Also keeps savedPreviewMode in sync with isPreviewMode while in TEXT mode.
    // Guard !isNewNote prevents overwriting savedPreviewMode during the initial composition
    // (when isPreviewMode is false because the note hasn't loaded yet).
    LaunchedEffect(isPreviewMode) {
        if (uiState.noteType == NoteType.TEXT && !uiState.isNewNote) {
            savedPreviewMode = isPreviewMode
        }
        if (!isPreviewMode && uiState.noteType == NoteType.TEXT && !uiState.isNewNote) {
            delay(LAYOUT_DELAY_MS)
            contentFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // v1.5.0: Auto-focus and show keyboard
    // v2.0.1: Skip auto-focus for existing TEXT notes (they start in preview mode)
    // v2.8.0: Show keyboard when existing TEXT note loads directly in edit mode (user preference)
    // 🆕 v2.11.0: New-note focus target configurable (title vs. content / first checklist item)
    LaunchedEffect(uiState.isNewNote) {
        delay(LAYOUT_DELAY_MS) // Wait for layout
        when {
            uiState.isNewNote && !uiState.newNoteFocusContent -> {
                // New note (default): focus title
                titleFocusRequester.requestFocus()
                keyboardController?.show()
            }
            uiState.isNewNote && uiState.noteType == NoteType.TEXT -> {
                // 🆕 v2.11.0: user preference — start in content field
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            }
            uiState.isNewNote -> {
                // 🆕 v2.11.0: CHECKLIST — focus the first (empty) item via the existing
                // focusNewItemId mechanism (same path as the title onNext handler below)
                focusNewItemId = checklistItems.firstOrNull()?.id
                keyboardController?.show()
            }
            !isPreviewMode && uiState.noteType == NoteType.TEXT -> {
                // Existing note opened in edit mode via user preference — isPreviewMode stays
                // false (no state change), so LaunchedEffect(isPreviewMode) never fires here.
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteEditorEvent.ShowToast -> {
                    val message = when (event.message) {
                        ToastMessage.NOTE_IS_EMPTY -> msgNoteIsEmpty
                        ToastMessage.NOTE_SAVED -> msgNoteSaved
                        ToastMessage.NOTE_DELETED -> msgNoteDeleted
                        ToastMessage.ITEM_COPIED_TO_CHECKLIST -> msgItemCopiedToChecklist // 🆕 v2.2.0
                    }
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                is NoteEditorEvent.NavigateBack -> onNavigateBack()
                is NoteEditorEvent.RestoreContent -> { // 🆕 v1.10.0: Undo/Redo
                    textFieldState.edit {
                        replace(0, length, event.content)
                        placeCursorAtEnd()
                    }
                }
                // 🆕 v1.10.0-P2: handled by Activity (deletion forwarded to MainViewModel)
                is NoteEditorEvent.NoteDeleteRequested -> Unit
                is NoteEditorEvent.NoteArchiveToggleRequested -> Unit // 🆕 v2.11.0: handled by Activity
                // 🆕 v1.10.0-Papa: handled by Activity
                is NoteEditorEvent.OpenCalendar -> Unit
                is NoteEditorEvent.ShareAsText -> Unit
                is NoteEditorEvent.ShareAsPdf -> Unit
                is NoteEditorEvent.CopyToClipboard -> {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", event.text)))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbarHostState.showSnackbar(msgNoteCopied)
                        }
                    }
                }
                is NoteEditorEvent.ActivatePreviewMode -> {
                    isPreviewMode = uiState.defaultStartInPreviewMode
                }
                is NoteEditorEvent.RequestContentFocus -> {
                    delay(LAYOUT_DELAY_MS)
                    contentFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        }
    }

    // Collect snackbar messages from Activity-originated actions (share, PDF, etc.)
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    NoteEditorToolbarTitle(
                        toolbarTitle = uiState.toolbarTitle,
                        autosaveIndicatorVisible = autosaveIndicatorVisible,
                        compact = isCompactToolbar
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // 🆕 v1.9.0 (F07): Markdown Preview Toggle (only for TEXT notes)
                    if (uiState.noteType == NoteType.TEXT) {
                        IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                            Icon(
                                imageVector = if (isPreviewMode) {
                                    Icons.Outlined.Edit
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(R.string.editor_toggle_preview)
                            )
                        }
                    }

                    // v2.0.1: Undo/Redo in toolbar for wide displays, overflow for narrow (Issue #48)
                    if (!isCompactToolbar) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.editor_undo)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = canRedo
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.editor_redo)
                            )
                        }
                    }

                    // Save button
                    IconButton(onClick = { viewModel.saveNote() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }

                    // 🆕 v1.10.0-Papa: Overflow menu (Calendar, Share, PDF, Delete)
                    // 🆕 v1.10.0-P2: Box ensures menu anchors to the ⋮ button,
                    // not to the full actions block (fixes position inconsistency
                    // between text and checklist note types)
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.share_overflow_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = MaterialTheme.shapes.large, // 🆕 v1.10.0-P2: Rounded corners
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // 🆕 v1.10.0-P2
                            shadowElevation = 6.dp, // 🆕 v1.10.0-P2
                            tonalElevation = 2.dp // 🆕 v1.10.0-P2
                        ) {
                            // v2.0.1: Undo/Redo in overflow only for compact displays (Issue #48)
                            if (isCompactToolbar) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_undo)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null,
                                            tint = if (canUndo) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            }
                                        )
                                    },
                                    enabled = canUndo,
                                    onClick = {
                                        viewModel.undo()
                                        // Don't dismiss — user may want to undo multiple times
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_redo)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = null,
                                            tint = if (canRedo) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            }
                                        )
                                    },
                                    enabled = canRedo,
                                    onClick = {
                                        viewModel.redo()
                                        // Don't dismiss — user may want to redo multiple times
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_set_note_color)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showColorPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.noteType == NoteType.CHECKLIST) {
                                                R.string.action_convert_to_note
                                            } else {
                                                R.string.action_convert_to_checklist
                                            }
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.noteType == NoteType.CHECKLIST) {
                                            Icons.AutoMirrored.Outlined.Notes
                                        } else {
                                            Icons.Outlined.Checklist
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showConvertDialog = true
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_to_calendar)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.openInCalendar()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy_note_text)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.copyNoteText()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_as_text)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.shareAsText()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_as_pdf)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.shareAsPdf()
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.isArchived) R.string.action_unarchive else R.string.action_archive
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    // 🆕 v2.11.0 (Archive): speichert + schließt den Editor (via Event/Activity)
                                    viewModel.toggleArchive()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    // 🆕 v2.9.0 (Trash): direkt in den Papierkorb, kein Bestätigungs-Dialog.
                                    viewModel.deleteNote()
                                }
                            )
                        }
                    } // Box
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface // 🆕 v2.5.0: neutral bar; accent shown as stripe below
                )
            )
        },
        modifier = Modifier.imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🆕 v2.5.0: 3 dp accent stripe — full width, outside content padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(noteAccentColor ?: Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(align = Alignment.CenterHorizontally) // 🆕 v1.10.0-P2: Center on tablets
                    .widthIn(max = 720.dp) // 🆕 v1.10.0-P2: Constrain width for readability
                    .fillMaxWidth() // 🆕 v1.10.0-P2: Fill up to constrained width
                    .padding(16.dp)
            ) {
                // Title Input (for both types)
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .onFocusChanged { isTitleFocused = it.isFocused },
                    label = { Text(stringResource(R.string.title)) },
                    singleLine = true, // 🆕 v1.8.2 (IMPL_09): Enter navigiert statt Newline
                    // 🆕 v1.8.2: Auto-Großschreibung für Wortanfänge im Titel
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next // 🆕 v1.8.2 (IMPL_09): Weiter-Taste
                    ),
                    // 🆕 v1.8.2 (IMPL_09): Nach Enter/Next → ins passende Feld springen
                    keyboardActions = KeyboardActions(
                        onNext = {
                            when (uiState.noteType) {
                                NoteType.TEXT -> {
                                    // Text-Notiz: Fokus direkt ins Content-Feld
                                    contentFocusRequester.requestFocus()
                                }
                                NoteType.CHECKLIST -> {
                                    // Checkliste: Fokus auf erstes Item
                                    val firstItemId = checklistItems.firstOrNull()?.id
                                    if (firstItemId != null) {
                                        focusNewItemId = firstItemId
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (uiState.noteType) {
                    NoteType.TEXT -> {
                        val linkColor = MaterialTheme.colorScheme.primary
                        val codeBackground = MaterialTheme.colorScheme.surfaceVariant
                        val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        val fontSizeMultiplier = LocalFontSizeMultiplier.current
                        val markdownTransformation = remember(linkColor, codeBackground, codeColor, markerColor, fontSizeMultiplier) {
                            MarkdownOutputTransformation(linkColor, codeBackground, codeColor, markerColor, fontSizeMultiplier)
                        }
                        if (isPreviewMode) {
                            // 🆕 v1.9.0 (F07): Markdown rendered preview
                            val blocks = remember(uiState.content) {
                                MarkdownEngine.parse(uiState.content)
                            }
                            MarkdownPreview(
                                blocks = blocks,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                // 🆕 Bild-Attachments v2: Long-Press-Menü schreibt Größe/Ausrichtung
                                // zurück in die Markdown-Source. Explizites updateContent ist
                                // zwingend: die snapshotFlow-Bridge lebt in TextNoteContent (im
                                // Preview-Mode nicht komponiert), die Preview rendert aus uiState.content.
                                onImageTokensChange = { image, size, align, altText ->
                                    applyImageTokenRewrite(textFieldState, viewModel, image, size, align, altText)
                                }
                            )
                        } else {
                            // Content Input for TEXT notes
                            TextNoteContent(
                                textFieldState = textFieldState,
                                onContentChange = { viewModel.updateContent(it) },
                                focusRequester = contentFocusRequester,
                                outputTransformation = markdownTransformation,
                                // 🆕 v2.12.0: Bilder aus der Zwischenablage
                                onPasteImages = { uris ->
                                    scope.launch { attachAndInsertImages(viewModel, textFieldState, uris) }
                                },
                                onFocusChanged = { isContentFocused = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            if (isContentFocused) {
                                MarkdownToolbar(
                                    textFieldState = textFieldState,
                                    onImageClick = {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    isAttachingImage = isAttachingImage
                                )
                            }
                        }
                    }

                    NoteType.CHECKLIST -> {
                        // Checklist Editor
                        ChecklistEditor(
                            items = checklistItems,
                            scope = scope,
                            focusNewItemId = focusNewItemId,
                            currentSortOption = lastChecklistSortOption, // 🔀 v1.8.0
                            scrollTopOnUncheck = viewModel.scrollTopOnUncheck, // 🆕 Issue #112
                            checklistScrollAction = viewModel.checklistScrollAction, // 🆕 v1.9.0 (F14)
                            onTextChange = { id, text -> viewModel.updateChecklistItemText(id, text) },
                            onCheckedChange = { id, checked ->
                                viewModel.updateChecklistItemChecked(id, checked)
                            },
                            onDelete = { id -> viewModel.deleteChecklistItem(id) },
                            onAddNewItemAfter = { id ->
                                val newId = viewModel.addChecklistItemAfter(id)
                                focusNewItemId = newId
                            },
                            onCopyText = { itemId ->
                                // 🆕 v2.2.0
                                val text = checklistItems.find { it.id == itemId }?.text
                                if (!text.isNullOrBlank()) {
                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                                }
                            },
                            onDuplicate = { itemId ->
                                // 🆕 v2.2.0
                                val newId = viewModel.duplicateChecklistItem(itemId)
                                if (newId != null) {
                                    focusNewItemId = newId
                                }
                            },
                            onCopyToChecklist = { itemId ->
                                // 🆕 v2.2.0
                                copyToChecklistItemId = itemId
                                viewModel.loadOtherChecklists()
                            },
                            onAddToCalendar = { itemId ->
                                viewModel.openChecklistItemInCalendar(itemId)
                            },
                            onAddItemAtEnd = {
                                val newId = viewModel.addChecklistItemAtEnd()
                                focusNewItemId = newId
                            },
                            onMove = { from, to -> viewModel.moveChecklistItem(from, to) },
                            onFocusHandled = { focusNewItemId = null },
                            onSortClick = { showChecklistSortDialog = true }, // 🔀 v1.8.0
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }

    // 🔀 v1.8.0: Checklist Sort Dialog
    if (showChecklistSortDialog) {
        ChecklistSortDialog(
            currentOption = lastChecklistSortOption,
            onOptionSelected = { option ->
                viewModel.sortChecklistItems(option)
                showChecklistSortDialog = false
            },
            onDismiss = { showChecklistSortDialog = false }
        )
    }

    // 🆕 v2.5.0: Note color picker sheet
    if (showColorPicker) {
        NoteColorPickerSheet(
            currentColor = uiState.color,
            onColorSelected = { hex ->
                viewModel.setColor(hex)
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST) {
                            R.string.action_convert_to_note
                        } else {
                            R.string.action_convert_to_checklist
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST) {
                            R.string.confirm_convert_to_note
                        } else {
                            R.string.confirm_convert_to_checklist
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConvertDialog = false
                    viewModel.convertNoteType()
                }) {
                    Text(stringResource(R.string.action_convert))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 🆕 v2.2.0: Checklist Target Picker Dialog (Aktion 3)
    otherChecklists?.let { checklists ->
        ChecklistTargetPickerDialog(
            checklists = checklists,
            onSelect = { targetNoteId ->
                copyToChecklistItemId?.let { itemId ->
                    viewModel.copyItemToChecklist(itemId, targetNoteId)
                }
                copyToChecklistItemId = null
            },
            onDismiss = {
                viewModel.dismissChecklistPicker()
                copyToChecklistItemId = null
            }
        )
    }
}

/**
 * 🆕 Bild-Attachments: Photo-Picker-Launcher — ausgelagert, damit die Verzweigung
 * (uri null-check, attachImage-Ergebnis) nicht in NoteEditorScreens Cyclomatic Complexity zählt.
 */
@Composable
private fun rememberImagePickerLauncher(
    viewModel: NoteEditorViewModel,
    textFieldState: TextFieldState,
    scope: CoroutineScope
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICKER_IMAGES)
) { uris ->
    if (uris.isNotEmpty()) {
        scope.launch { attachAndInsertImages(viewModel, textFieldState, uris) }
    }
}

/**
 * 🆕 v2.12.0: Hängt [uris] sequenziell als Assets an (Kompression + Store via
 * attachImage) und fügt je ein `![](.assets/…)` auf eigener Zeile ein.
 * Gemeinsamer Pfad für Multi-Picker und Clipboard-Bild-Paste.
 */
private suspend fun attachAndInsertImages(
    viewModel: NoteEditorViewModel,
    textFieldState: TextFieldState,
    uris: List<Uri>
) {
    uris.forEach { uri ->
        viewModel.attachImage(uri)?.let { assetName ->
            insertImageMarkdownOnOwnLine(textFieldState, assetName)
        }
    }
}

/**
 * Wie insertImageMarkdown (MarkdownToolbar.kt), aber mit führendem Umbruch,
 * wenn der Cursor nicht am Zeilenanfang steht — bei mehreren Bildern landet
 * so jedes `![](.assets/…)` auf einer eigenen Zeile (Renderer erwartet
 * Bild-Links als eigene Zeile).
 */
private fun insertImageMarkdownOnOwnLine(state: TextFieldState, assetName: String) {
    state.edit {
        val start = selection.min
        val atLineStart = start == 0 || asCharSequence()[start - 1] == '\n'
        val link = (if (atLineStart) "" else "\n") + "![](.assets/$assetName)"
        insert(start, link)
        selection = TextRange(start + link.length)
    }
}

/**
 * 🆕 Bild-Attachments v2: Schreibt eine Größe/Ausrichtung-Auswahl aus dem Long-Press-Menü
 * zurück in die Markdown-Source. [computeImageRewrite] liefert `null` bei Asset-Mismatch/
 * Out-of-range-Ordinal (Text hat sich geändert) — dann ist es ein stiller No-op.
 */
private fun applyImageTokenRewrite(
    textFieldState: TextFieldState,
    viewModel: NoteEditorViewModel,
    image: MarkdownEngine.MarkdownBlock.Image,
    sizePercent: Int,
    align: ImageAlign,
    altText: String
) {
    val rewrite = computeImageRewrite(
        textFieldState.text.toString(),
        image.ordinal,
        image.assetName,
        sizePercent,
        align,
        cleanAlt = altText
    ) ?: return
    val (range, replacement) = rewrite
    textFieldState.edit { replace(range.first, range.last + 1, replacement) }
    viewModel.updateContent(textFieldState.text.toString())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextNoteContent(
    textFieldState: TextFieldState,
    onContentChange: (String) -> Unit,
    focusRequester: FocusRequester,
    outputTransformation: MarkdownOutputTransformation,
    onPasteImages: (List<Uri>) -> Unit, // 🆕 v2.12.0
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    // 🆕 v1.8.2 (IMPL_07): Migration zu TextFieldState-API für scrollState-Unterstützung
    // v1.9.0 (F07): TextFieldState now provided from parent for toolbar access
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope() // 🆕 v2.12.0: async HTML-Konvertierung

    // Focus-State tracken für Auto-Scroll bei Tastaturöffnung
    var isFocused by remember { mutableStateOf(false) }

    // Layout-Result für Tap-to-Toggle (Task-Checkboxes)
    val textLayoutRef = remember { mutableStateOf<TextLayoutResult?>(null) }

    fun handleCheckboxTap(event: PointerEvent) {
        if (event.type != PointerEventType.Press) return
        val tapPos = event.changes.firstOrNull()?.position ?: return
        val layout = textLayoutRef.value ?: return
        val charOff = layout.getOffsetForPosition(tapPos)
        val rawText = textFieldState.text.toString()
        val lineStartOff = rawText.lastIndexOf('\n', (charOff - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        if (charOff - lineStartOff > CHECKBOX_TAP_PREFIX_COLS) return
        val lineEndOff = rawText.indexOf('\n', lineStartOff).let { if (it < 0) rawText.length else it }
        val rawLine = rawText.substring(lineStartOff, lineEndOff)
        val taskMatch = MarkdownEngine.TASK_LIST_REGEX.matchEntire(rawLine) ?: return
        val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
        // Aus dem Match statt fixem Offset: der Marker darf eingerückt sein, "*"/"+" heißen,
        // und "- []" hat gar kein Zeichen zwischen den Klammern (replace über Länge 0 = Insert).
        val open = lineStartOff + rawLine.indexOf('[')
        val close = lineStartOff + rawLine.indexOf(']')
        textFieldState.edit {
            replace(open + 1, close, if (isChecked) " " else "x")
        }
    }

    // Text-Änderungen an ViewModel propagieren
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1) // 🆕 v1.9.0: skip initial emission — snapshotFlow always emits current
            // value on first collect, but that's hydration, not a user edit
            .collect { newText ->
                onContentChange(newText)
            }
    }

    // 🆕 v1.8.2 (IMPL_07): Auto-Scroll zum Ende wenn Fokus erhalten (Tastatur öffnet sich)
    // Delay gibt dem Layout Zeit, sich nach imePadding-Resize zu stabilisieren
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(LAYOUT_DELAY_MS)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val context = LocalContext.current

    OutlinedTextField(
        state = textFieldState,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            }
            .contentReceiver { transferable ->
                handlePastedContent(transferable, context, textFieldState, scope, onPasteImages)
            }
            .pointerInput(textFieldState) {
                awaitPointerEventScope {
                    while (true) {
                        handleCheckboxTap(awaitPointerEvent(PointerEventPass.Initial))
                    }
                }
            },
        label = { Text(stringResource(R.string.content)) },
        // 🆕 v1.8.2: Auto-Großschreibung für Satzanfänge im Inhalt
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        shape = RoundedCornerShape(16.dp),
        // 🆕 v1.8.2 (IMPL_07): Externer ScrollState für programmatisches Auto-Scroll
        scrollState = scrollState,
        outputTransformation = outputTransformation,
        onTextLayout = { getResult -> textLayoutRef.value = getResult() }
    )
}

/**
 * 🆕 v1.8.1 IMPL_14: Extrahiertes Composable für ein einzelnes draggbares Checklist-Item.
 * Entkoppelt von der Separator-Logik — wiederverwendbar für unchecked und checked Items.
 */
@Suppress("LongParameterList") // Compose callbacks — cannot be reduced without wrapper class
@Composable
private fun LazyItemScope.DraggableChecklistItem(
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
    scrollTopOnUncheck: Boolean, // 🆕 Issue #112: Commit ohne Settle + ohne Expand (Scroll übernimmt)
    onUncheckCommit: (String) -> Unit, // 🔧 v2.13.0: Commit nach abgeschlossenem Collapse
    topHighlightId: String?, // 🔧 Issue #112: Highlight-Pop nach ScrollToTop-Ankunft
    onTopHighlightShown: () -> Unit // 🔧 Issue #112: konsumiert topHighlightId
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

    // 🔧 Issue #112: Frischer Highlight-Pop, nachdem der ScrollToTop das Item oben ins Bild
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
    // bei Ziel off-screen, Einschiebe-Artefakte parallel zum ScrollToTop). Deshalb besitzt die
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

    LaunchedEffect(isPendingUncheck) {
        if (isPendingUncheck) {
            // Glow/Scale-Pop sofort beenden: auf einer kollabierenden Row wirkt das Highlight
            // falsch — und es klebte sonst nach dem ScrollToTop sichtbar am wieder
            // auftauchenden Item (die 500-ms-Animation lief den Scroll schlicht ab).
            // Nach Scroll-Ankunft triggert topHighlightId stattdessen einen frischen Pop.
            isCheckAnimating = false
            collapseOwnsPlacement = true
            collapse.animateTo(0f, tween(UNCHECK_COLLAPSE_MS))
            if (!scrollTopOnUncheck) {
                // Die Nachbarn folgen der schrumpfenden Row über ihre animateItem-Spring und
                // hinken dabei hinterher. Der Commit killt via requestScrollToItem alle
                // Placement-Animationen — käme er sofort, schnappte der Rest sichtbar nach
                // (gemessen ~1/3 Zeilenhöhe in einem Frame). Deshalb erst die Spring auslaufen
                // lassen; die Row ist bereits unsichtbar, die Wartezeit kostet optisch nichts.
                // Mit Scroll-to-Top entfällt das: der direkt folgende Scroll übernimmt die
                // gesamte Bewegung und maskiert den Spring-Schnitt — dafür startet der Scroll
                // ~200 ms früher, was den Tap spürbar direkter macht.
                delay(UNCHECK_COMMIT_SETTLE_MS)
            }
            onUncheckCommit(item.id)
            return@LaunchedEffect
        }
        if (!collapseOwnsPlacement) return@LaunchedEffect
        if (scrollTopOnUncheck && !item.isChecked) {
            // Commit mit folgendem ScrollToTop: NICHT am Ziel aufwachsen — ein Expand während
            // animateScrollToItem drückt die Nachbarn mitten in der Scroll-Bewegung auseinander
            // (sichtbares „Einschieben"). Höhe still zurücksetzen; das Item ist beim Commit
            // off-screen, der Scroll bringt es fertig aufgebaut ins Bild.
            collapse.snapTo(1f)
            repeat(PLACEMENT_SUPPRESS_FRAMES) { withFrameNanos { } }
        } else {
            // Beide Wege enden mit voller Höhe:
            // - Abbruch per Re-Tap: das Model wurde nie geändert → Row klappt an Ort und
            //   Stelle wieder auf.
            // - Nach dem Commit ohne Scroll: das Item sitzt an seiner neuen Position in der
            //   unchecked-Sektion. Ist die sichtbar, wächst die Row dort als Gegenstück zum
            //   Collapse auf; off-screen läuft die Animation unsichtbar und kostet nichts.
            // Solange sie läuft, bleibt animateItem über collapseOwnsPlacement aus — danach
            // startet der Modifier ohne Baseline, also ohne nachgeholte Placement-Animation.
            collapse.animateTo(1f, tween(UNCHECK_COLLAPSE_MS))
        }
        collapseOwnsPlacement = false
    }
    // Screen/Item wird disposed, während der Collapse läuft → Commit sofort nachholen.
    // Ohne das ginge der Tap verloren (das Model wurde ja noch nicht angefasst).
    val pendingAtDispose = rememberUpdatedState(isPendingUncheck)
    val commitAtDispose = rememberUpdatedState(onUncheckCommit)
    DisposableEffect(item.id) {
        onDispose {
            if (pendingAtDispose.value) commitAtDispose.value(item.id)
        }
    }

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
        isCheckAnimating = isCheckAnimating, // 🆕 v2.5.0
        onCheckboxTap = { isCheckAnimating = true }, // 🆕 v2.5.0
        // 🔧 v2.13.0: Solange der Uncheck aufgeschoben ist, zeigt die Checkbox optimistisch
        // ungecheckt — das Model folgt erst beim Commit.
        checkedOverride = if (isPendingUncheck) false else null,
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
                if (!isDragging && placementAnimationsEnabled && !collapseOwnsPlacement) {
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                } else {
                    Modifier
                }
            )
            // 🆕 v2.5.0: zIndex-Tabelle:
            //   isDragging              → DRAGGING_ITEM_Z_INDEX (10f)  – Drag wins
            //   isCheckAnimating        → CHECKING_ITEM_Z_INDEX  (5f)  – Glow/Move on top
            //   sonst                   → 0f
            .zIndex(
                when {
                    isDragging -> DRAGGING_ITEM_Z_INDEX
                    isCheckAnimating -> CHECKING_ITEM_Z_INDEX
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
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
            )
    )
}

// 🔧 v2.13.0: internal statt private — ChecklistUncheckAnimationInstrumentedTest hostet den
// Editor direkt, um die Collapse-/Placement-Animationen frameweise zu vermessen.
@Suppress("LongParameterList") // Compose functions commonly have many callback parameters
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistEditor(
    items: List<ChecklistItemState>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusNewItemId: String?,
    currentSortOption: ChecklistSortOption, // 🔀 v1.8.0: Aktuelle Sortierung
    scrollTopOnUncheck: Boolean, // 🆕 Issue #112: Collapse committet dann ohne Settle + ohne Expand
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
    modifier: Modifier = Modifier
) {
    // IMPL_29q Q1: LazyLayoutCacheWindow ersetzt beyondBoundsItemCount (entfernt in Compose 1.10+).
    // Hält 0.55 × Viewport auf jeder Seite composiert → 865px Buffer (3 große Items à 271px).
    // Verhindert Composable-Recycling beim Auto-Scroll+Swap → kein toter Pointer-Scope.
    val listState = rememberLazyListState(LazyLayoutCacheWindow(0.55f, 0.55f))
    val dragDropState = rememberDragDropListState(
        lazyListState = listState,
        scope = scope,
        onMove = onMove
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

    // 🔧 Issue #112: Handoff Commit → ScrollToTop-Handler. Die Tap-Animation des Items stirbt
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
            val viewportEnd = layoutInfo.viewportEndOffset
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
                is NoteEditorViewModel.ChecklistScrollAction.ScrollToTop -> {
                    if (BuildConfig.DEBUG) Logger.d(CHECK_TRACE_TAG, "[SCROLL] ScrollToTop")
                    val highlightId = scrollTopUncheckId
                    scrollTopUncheckId = null
                    if (cutAndGlideToTop(listState, highlightId)) {
                        topHighlightId = highlightId
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
                val unchecked = items.count { !it.isChecked }
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
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
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
                    val viewportEnd = listState.layoutInfo.viewportEndOffset
                    val itemBottom = itemInfo.offset + itemInfo.size
                    if (itemBottom > viewportEnd) {
                        // Item ragt unter den sichtbaren Bereich — genau um die Differenz scrollen
                        listState.scroll { scrollBy((itemBottom - viewportEnd).toFloat()) }
                    }
                }
                scrollToItemIndex = null
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
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
                        modifier = if (placementAnimationsEnabled) Modifier.animateItem() else Modifier
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
                            onChecklistCheckedChange(
                                listState = listState,
                                itemId = id,
                                checked = checked,
                                separatorVisualIndex = separatorVisualIndex,
                                pendingUncheckId = pendingUncheckId,
                                setPendingUncheckId = { pendingUncheckId = it },
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
                        scrollTopOnUncheck = scrollTopOnUncheck,
                        onUncheckCommit = { id ->
                            if (scrollTopOnUncheck) scrollTopUncheckId = id
                            commitDeferredUncheck(
                                listState = listState,
                                toggledId = id,
                                commitChecked = onCheckedChange,
                                reanchor = !scrollTopOnUncheck
                            )
                            pendingUncheckId = null
                        },
                        topHighlightId = topHighlightId,
                        onTopHighlightShown = { topHighlightId = null }
                    )
                }
            }
        }

        // 🔀 v1.8.0: Add Item Button + Sort Button
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

// v1.5.0: Local DeleteConfirmationDialog removed - now using shared component from ui/main/components/

/** 🆕 v1.9.0: TopAppBar title with optional autosave confirmation indicator. */
@Composable
private fun NoteEditorToolbarTitle(toolbarTitle: ToolbarTitle, autosaveIndicatorVisible: Boolean, compact: Boolean = false) {
    Column {
        Text(
            text = if (compact) {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE, ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.toolbar_title_new)
                    ToolbarTitle.EDIT_NOTE, ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.toolbar_title_edit)
                }
            } else {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE -> stringResource(R.string.new_note)
                    ToolbarTitle.EDIT_NOTE -> stringResource(R.string.edit_note)
                    ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.new_checklist)
                    ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.edit_checklist)
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(
            visible = autosaveIndicatorVisible,
            enter = fadeIn(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS)),
            exit = fadeOut(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS))
        ) {
            Text(
                text = stringResource(R.string.autosave_indicator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 🆕 v2.12.0: Paste-Handler für das Content-Feld. Bilder zuerst („Bild kopieren"
 * in Chrome legt Uri **und** ein <img>-HTML-Fragment in die ClipData — das Bild ist
 * das, was der Nutzer will), dann HTML→Markdown. Rückgabe `null` = konsumiert,
 * sonst geht [transferable] den normalen Paste-Weg.
 *
 * HTML-Konvertierung läuft zweistufig: Rohtext synchron (eigener Undo-Eintrag),
 * Markdown-Ersetzung async off-main (zweiter Undo-Eintrag → direktes Undo stellt
 * den Rohtext wieder her).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun handlePastedContent(
    transferable: TransferableContent,
    context: Context,
    textFieldState: TextFieldState,
    scope: CoroutineScope,
    onPasteImages: (List<Uri>) -> Unit
): TransferableContent? {
    val clipData = transferable.clipEntry.clipData
    val imageUris = (0 until clipData.itemCount)
        .mapNotNull { clipData.getItemAt(it).uri }
        .filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
    if (imageUris.isNotEmpty()) {
        onPasteImages(imageUris)
        return null
    }

    val html = transferable.clipEntry.firstHtmlText()
    if (html == null || !HtmlToMarkdown.hasRichContent(html)) {
        return transferable // nicht zuständig → normaler Paste
    }
    // ponytail: gegen die style-gestrippte Länge messen — Chrome inlined computed
    // styles, die den Clip weit über den Cap blähen; convert() bekommt trotzdem das
    // Original-HTML (off-main, Größe egal dort), damit style-Attribut-Fett/Kursiv bleibt.
    val measured = HtmlToMarkdown.stripStyleAttributes(html).length
    if (measured > HtmlToMarkdown.MAX_HTML_LENGTH) {
        Logger.d("HtmlPaste", "over cap: raw=${html.length} stripped=$measured → plaintext fallback")
        return transferable // nicht zuständig → normaler Paste
    }

    val plain = clipData.getItemAt(0).coerceToText(context).toString()
    val sel = textFieldState.selection
    val start = sel.min
    // Stufe 1 (synchron): Rohtext einfügen
    textFieldState.edit {
        if (!sel.collapsed) delete(sel.min, sel.max)
        insert(start, plain)
        selection = TextRange(start + plain.length)
    }
    // Stufe 2 (async, off-main): zu Markdown ersetzen
    scope.launch {
        val markdown = withContext(Dispatchers.Default) { HtmlToMarkdown.convert(html, plain) }
        if (markdown == plain) Logger.d("HtmlPaste", "convert() returned fallback: raw=${html.length}")
        val end = start + plain.length
        // Guard: nur ersetzen, wenn der Rohtext dort noch unverändert steht
        // (Nutzer könnte während der Konvertierung getippt haben)
        if (markdown != plain &&
            textFieldState.text.length >= end &&
            textFieldState.text.subSequence(start, end).toString() == plain
        ) {
            textFieldState.edit {
                replace(start, end, markdown)
                selection = TextRange(start + markdown.length)
            }
        }
    }
    return null // konsumiert
}

/** First non-blank `text/html` payload across all clip items, or null. */
private fun ClipEntry.firstHtmlText(): String? {
    val data = clipData
    for (i in 0 until data.itemCount) {
        val html = data.getItemAt(i).htmlText
        if (!html.isNullOrBlank()) return html
    }
    return null
}
