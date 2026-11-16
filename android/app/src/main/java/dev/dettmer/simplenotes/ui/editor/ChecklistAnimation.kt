package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.first

// Check/Uncheck-Animation der Checkliste: Konstanten, Pre-Anchor, aufgeschobener Uncheck, Debug-Trace.
// Aus NoteEditorScreen.kt ausgelagert, unverändert. Regeln: Skill checklist-animations.

internal const val AUTO_SCROLL_DELAY_MS = 50L
internal const val ITEM_CORNER_RADIUS_DP = 8
internal const val DRAGGING_ITEM_Z_INDEX = 10f

// 🆕 v2.5.0: Z-Index für Items in Check/Uncheck-Animation. Liegt zwischen 0f
// (Standard-Items) und DRAGGING_ITEM_Z_INDEX (10f). Hebt das animierende Item
// während der LazyColumn-Placement-Animation über seine Nachbarn — behebt die
// Asymmetrie, dass aufwärts wandernde Items (Uncheck) sonst hinter Nachbarn
// gezeichnet werden, weil ihre neue (niedrigere) Composition-Position sie früher
// im Draw-Tree platziert. Drag dominiert weiterhin (10f > 5f).
//
internal const val CHECKING_ITEM_Z_INDEX = 5f

// 🆕 v2.5.0: Gesamt-Dauer der Check-Tap-Animation in Millisekunden. Deckt
// Scale-Up (CHECK_ANIM_SCALE_UP_MS = 80ms) + Spring-Back + Glow-Fade-Out
// (CHECK_GLOW_FADE_OUT_MS = 450ms) + LazyColumn-Placement-Spring komfortabel ab.
// Wird vom LaunchedEffect in DraggableChecklistItem genutzt, um isCheckAnimating
// zurückzusetzen (deterministischer Reset statt finishedListener-Race).
internal const val CHECK_ANIMATION_TOTAL_MS = 500L

// 🔧 v2.5.x: Maximale Wartezeit, bis das initiale LazyColumn-Layout als „stabil"
// gilt. Sicherheitsnetz für den Settle-LaunchedEffect in ChecklistEditor: falls externe
// Effekte (Text-Messung, dynamische Höhen, Sync-Updates) die Liste länger als diese
// Schwelle in Bewegung halten, wird das Placement-Animation-Gate trotzdem geöffnet,
// damit Reorder-Animationen nach Check/Uncheck garantiert spielen.
// 250 ms ≈ 15 Frames @ 60Hz — genug Puffer für Cold-Open auch auf langsamen
// Geräten, kurz genug, dass User es nicht wahrnehmen.
internal const val INITIAL_LAYOUT_SETTLE_TIMEOUT_MS = 250L
internal val DRAGGING_ELEVATION_DP = 8.dp

// 🔧 v2.13.0: Dauer des Eigen-Collapse beim Uncheck mit Ziel außerhalb des Viewports.
// Kurz genug, dass der Eintrag nicht „nachhängt", lang genug, dass das Nachrücken der
// Nachbarn als Bewegung lesbar bleibt. Siehe [DraggableChecklistItem].
internal const val UNCHECK_COLLAPSE_MS = 250

// Nachlauf zwischen Collapse-Ende und Commit: so lange braucht die animateItem-Spring der
// Nachbarn, um die letzten Pixel aufzuholen. Ohne den Nachlauf schneidet der Commit sie ab.
internal const val UNCHECK_COMMIT_SETTLE_MS = 200L

// Frames, die nach dem Snap eines Commit (Scroll-to-Top-Pfad, kein Expand) ohne animateItem
// durchlaufen müssen: einer für den Commit-Layout-Pass selbst, einer als Puffer.
internal const val PLACEMENT_SUPPRESS_FRAMES = 2

// Landet ein aufgehakter Eintrag auf einem Index bis hier, scrollt die Liste ganz an den
// Anfang statt nur zum Eintrag: die paar Zeilen darüber blieben sonst knapp verdeckt.
internal const val SCROLL_TO_START_MAX_INDEX = 3

// Dauer des Glide über die letzte Viewport-Höhe. FastOutSlowIn startet schnell und
// maskiert damit den vorausgehenden Cut.
private const val SCROLL_TOP_GLIDE_MS = 350

/** Key des Separator-Slots in der LazyColumn — nie ein Item-Key. */
internal const val SEPARATOR_ITEM_KEY = "separator"

// 🔍 v2.13.0 (nur DEBUG): Frame-Trace der Check/Uncheck-Placement-Animation.
// Zweck: nachvollziehen, warum ein nach oben wandernder Eintrag am Viewport-Rand
// „hängen bleibt", statt durchzulaufen. Filter: `adb logcat -s ChecklistAnim:D`.
internal const val CHECK_TRACE_TAG = "ChecklistAnim"

// Feste Trace-Länge. Muss über CHECK_ANIMATION_TOTAL_MS (500 ms, zIndex-Reset) hinausgehen,
// sonst endet der Trace vor dem Moment, in dem das Item „verschwindet".
private const val CHECK_TRACE_DURATION_MS = 700L

// Item-IDs sind UUIDs — die ersten Zeichen reichen zum Zuordnen im Log.
internal const val CHECK_TRACE_ID_LEN = 8
private const val NANOS_PER_MS = 1_000_000L

/** 🔍 DEBUG: Trace-Trigger. `tapNanos` macht jeden Tap eindeutig → LaunchedEffect startet neu. */
internal data class CheckTrace(val tapNanos: Long, val itemId: String, val checked: Boolean)

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
    checked: Boolean,
    // Unterpunkte: alle Zeilen, die mitwandern (Oberpunkt samt Gruppe). Keine davon taugt als Anker.
    movingIds: Set<String> = setOf(itemKey)
) {
    if (listState.firstVisibleItemIndex == 0) {
        val preservedOffset = listState.firstVisibleItemScrollOffset
        if (BuildConfig.DEBUG) logCheckTap(listState, itemKey, checked, "A(0,$preservedOffset)")
        listState.requestScrollToItem(0, preservedOffset)
        return
    }
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.firstOrNull()?.key !in movingIds) {
        if (BuildConfig.DEBUG) logCheckTap(listState, itemKey, checked, "B2(kein Pre-Anchor)")
        return
    }
    val anchor = visible.firstOrNull { info -> info.key !in movingIds }
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
internal fun commitDeferredUncheck(
    listState: LazyListState,
    toggledId: String,
    commitChecked: (String, Boolean) -> Unit,
    reanchor: Boolean = true,
    // Unterpunkte: die ganze wandernde Gruppe. Ist eine ihrer Zeilen der Anker der Liste, ankert die erste Zeile außerhalb.
    movingIds: Set<String> = setOf(toggledId),
    // Key am visuellen Index (Trenner eingerechnet). Der Anker der Liste ist firstVisibleItemIndex; eine auf
    // 0 px kollabierte Zeile bleibt das, fehlt aber in visibleItemsInfo (gemessen: first=29@0, Oberpunkt kollabiert).
    keyAt: (Int) -> Any? = { index -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key }
) {
    val visible = listState.layoutInfo.visibleItemsInfo
    val first = listState.firstVisibleItemIndex
    val anchor = if (reanchor && keyAt(first) in movingIds) {
        // Ab dem Anker suchen: visibleItemsInfo führt auch eine Zeile, die direkt über dem Bild endet
        visible.firstOrNull { it.index >= first && it.key !in movingIds && it.key != SEPARATOR_ITEM_KEY }
    } else {
        null
    }
    if (BuildConfig.DEBUG) {
        Logger.d(
            CHECK_TRACE_TAG,
            "[COMMIT] id=${toggledId.take(CHECK_TRACE_ID_LEN)} anchor=${anchor?.let { "${it.index}@${it.offset}" } ?: "keiner (B2)"} " +
                "first=${listState.firstVisibleItemIndex}@${listState.firstVisibleItemScrollOffset}"
        )
    }
    if (anchor != null) {
        // Der Anker rückt an die Stelle der wandernden Zeile(n): deren Oberkante liegt bei
        // -firstVisibleItemScrollOffset. Seine eigene Lage zählte die Zeilenabstände der kollabierten
        // Zeilen mit (gemessen: 15 px bei einer Gruppe aus drei Zeilen).
        listState.requestScrollToItem(anchor.index, listState.firstVisibleItemScrollOffset)
    } else if (reanchor && first == 0 && listState.firstVisibleItemScrollOffset == 0) {
        // Ganz oben (wie Fall A in preAnchorForToggle): Index 0 festhalten. Landet das Ziel an Index 0,
        // steht es so oben im Bild und wächst dort auf. Ohne Anker folgte LazyList dem Key der bisher
        // ersten Zeile, und die Zeile oder Gruppe läge unsichtbar über dem Bild.
        listState.requestScrollToItem(0, 0)
    }
    commitChecked(toggledId, false)
}

/**
 * 🔧 Issue #112: Scroll zu [index] als Cut + ein einziger pixelgenauer Glide.
 * `animateScrollToItem` schätzt lange Distanzen über Durchschnittshöhen und korrigiert
 * mehrphasig nach (Retargeting). Bei mehrzeiligen Einträgen las sich das als
 * „zweistufiges" Scrollen. Stattdessen instant auf `glidePx` unterhalb der Oberkante des
 * Ziels schneiden (der übersprungene Inhalt ist off-screen) und genau diese Strecke in einem
 * `animateScrollBy` gleiten.
 *
 * `glidePx` ist höchstens eine Viewport-Höhe und höchstens die Untergrenze der echten Strecke:
 * jeder Eintrag zwischen Ziel und Viewport ist mindestens so hoch wie der kleinste sichtbare,
 * der flachere Separator zählt mit 0. `scrollToItem` ist absolut: eine zu große Glide-Strecke
 * schnitte bei nahem Ziel erst sichtbar **nach unten**. Mit der Untergrenze geht der Schnitt nur
 * in Laufrichtung oder bleibt aus (bei einzeiligen Einträgen ist sie exakt: reiner Glide).
 *
 * @return true, wenn [itemKey] nach der Ankunft sichtbar ist — nur dann darf der
 * Highlight-Pop feuern; ein liegengebliebener Trigger feuerte sonst später beim
 * manuellen Scrollen.
 */
internal suspend fun cutAndGlideTo(
    listState: LazyListState,
    index: Int,
    itemKey: String,
    separatorIndex: Int
): Boolean {
    val info = listState.layoutInfo
    val first = listState.firstVisibleItemIndex
    val minRowPx = info.visibleItemsInfo
        .filter { it.size > 0 && it.key != SEPARATOR_ITEM_KEY }
        .minOfOrNull { it.size } ?: 0
    val rowsBetween = first - index - if (separatorIndex in index until first) 1 else 0
    val lowerBoundPx = rowsBetween * (minRowPx + info.mainAxisItemSpacing) + listState.firstVisibleItemScrollOffset
    val glidePx = minOf(info.viewportEndOffset - info.viewportStartOffset, lowerBoundPx).coerceAtLeast(0)
    if (BuildConfig.DEBUG) {
        Logger.d(
            CHECK_TRACE_TAG,
            "[SCROLL] index=$index first=$first@${listState.firstVisibleItemScrollOffset} glide=$glidePx lowerBound=$lowerBoundPx"
        )
    }
    listState.scrollToItem(index = index, scrollOffset = glidePx)
    listState.animateScrollBy(
        value = -glidePx.toFloat(),
        animationSpec = tween(SCROLL_TOP_GLIDE_MS, easing = FastOutSlowInEasing)
    )
    return listState.layoutInfo.visibleItemsInfo.any { it.key == itemKey }
}

/**
 * 🔧 v2.13.0: Pfad-Weiche beim Checkbox-Tap. Drei Fälle:
 *
 * 1. **Re-Tap auf das gerade kollabierende Item** → Abbruch. Das Model wurde nie geändert,
 *    es genügt, den Pending-Slot zu leeren; die Row klappt wieder auf.
 * 2. **Uncheck mit Separator-Reorder** (`uncheckReorders`: Trenner sichtbar und Zeile im erledigten
 *    Abschnitt; ein abgehakter Unterpunkt unter offenem Oberpunkt bewegt sich nicht) → **immer** aufschieben:
 *    die Row besitzt die Exit-Animation selbst (Collapse an Ort und Stelle), Commit erst
 *    danach. Ein Aufwärts-Placement durch LazyLayoutItemAnimator findet nie statt — der ist
 *    für diese Richtung nachweislich nicht robust: Ziel off-screen parkt einen „Geist" am
 *    Viewport-Rand, und parallel zum Scroll entstehen Einschiebe-Artefakte bei den
 *    Nachbarn. Das frühere Kriterium „nur wenn Ziel off-screen" ließ genau diese Fälle durch.
 *    Ist Scroll-to-Top aktiv, feuert das ViewModel die Aktion erst beim Commit — der Scroll
 *    läuft also nach dem Collapse und kollidiert mit keiner Placement-Animation mehr.
 * 3. **Check — und Uncheck ohne Separator** (Sortierungen ohne Auto-Sort: das Item bewegt
 *    sich gar nicht; sowie die komplett abgehakte Liste) → unveränderter Pfad: Pre-Anchor +
 *    sofortiger Commit ins ViewModel.
 *
 * Der Pending-Slot hält genau ein Item. Ein zweiter Uncheck während eines laufenden Collapse
 * committet den ersten sofort — kein Tap darf verloren gehen.
 *
 * Unterpunkte: Ein Tap auf einen Mitläufer der kollabierenden Gruppe ([pendingGroupIds]) committet die
 * Gruppe und danach ihn direkt. Nach dem Gruppen-Commit steht er im offenen Abschnitt und bewegt sich
 * nicht mehr; als neuer Pending-Slot würde er nie committen (sein Collapse läuft schon als Mitläufer).
 */

/** Unterpunkte: läuft gerade ein Ziehen mit mindestens einem Tausch (siehe ChecklistEditor). */
internal class DragTracker {
    var active = false
}

@Suppress("LongParameterList") // Compose-State + Callbacks; ein Wrapper-Objekt brächte nichts
internal fun onChecklistCheckedChange(
    listState: LazyListState,
    itemId: String,
    checked: Boolean,
    uncheckReorders: Boolean,
    pendingUncheckId: String?,
    pendingGroupIds: Set<String>,
    movingIds: Set<String>,
    keyAt: (Int) -> Any?,
    setPendingUncheckId: (String?) -> Unit,
    commitChecked: (String, Boolean) -> Unit
) {
    if (checked && itemId == pendingUncheckId) {
        setPendingUncheckId(null)
        return
    }
    val inPendingGroup = itemId in pendingGroupIds
    if (pendingUncheckId != null && pendingUncheckId != itemId) {
        commitDeferredUncheck(listState, pendingUncheckId, commitChecked, movingIds = pendingGroupIds, keyAt = keyAt)
    }
    if (!checked && uncheckReorders && !inPendingGroup) {
        setPendingUncheckId(itemId)
        return
    }
    setPendingUncheckId(null)
    // Der Gruppen-Commit hat gerade selbst verankert; der Mitläufer bewegt sich danach nicht mehr.
    if (!inPendingGroup) preAnchorForToggle(listState, itemId, checked, movingIds)
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
internal suspend fun traceCheckPlacement(listState: LazyListState, trace: CheckTrace) {
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
