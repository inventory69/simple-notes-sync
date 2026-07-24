package dev.dettmer.simplenotes.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dettmer.simplenotes.models.ChecklistSortOption
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 🔧 v2.13.0: Frame-genaue Regressionstests für die Uncheck-Animation der Checkliste.
 *
 * Hintergrund: `LazyLayoutItemAnimator` ist für die Aufwärts-Richtung nicht robust. Ein Ziel
 * **außerhalb** des Viewports parkt das Item für die Animationsdauer unbewegt am Viewport-Rand
 * („Geist"); bei sichtbarem Ziel und parallel zum ScrollToTop entstanden Einschiebe-Artefakte.
 * Der Fix lässt die Row die Exit-Animation bei **jedem** Uncheck mit Separator-Reorder selbst
 * besitzen: Collapse an Ort und Stelle, Commit danach, an sichtbarem Ziel wächst sie wieder auf.
 *
 * Die Liste enthält bewusst alle fünf Einträge einen mehrzeiligen Text — die Collapse-Animation
 * misst die reale Row-Höhe pro Frame und muss auch mit variablen Zeilenhöhen pixelstabil bleiben.
 *
 * Die Tests hosten [ChecklistEditor] direkt und takten die Compose-Uhr manuell
 * (`mainClock.autoAdvance = false`), damit Zwischenframes vermessbar sind. Ein Blackbox-Test
 * über die echte Activity könnte den Unterschied zwischen „wandert" und „parkt" nicht sehen.
 *
 * Run via adb:
 *   adb shell am instrument -w -r \
 *     -e class dev.dettmer.simplenotes.ui.editor.ChecklistUncheckAnimationInstrumentedTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class ChecklistUncheckAnimationInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    /** Von außen lesbar, damit die Tests den Endzustand prüfen können. */
    private val itemsState = mutableStateOf<List<ChecklistItemState>>(emptyList())

    // extraBufferCapacity wie im ViewModel — tryEmit aus dem (nicht-suspendierenden)
    // onCheckedChange-Callback muss den Collector im ChecklistEditor erreichen.
    private val scrollActions = MutableSharedFlow<NoteEditorViewModel.ChecklistScrollAction>(extraBufferCapacity = 1)

    private companion object {
        // Lang genug, dass der Separator bei bildschirmfüllendem Viewport aus dem Bild
        // gescrollt werden kann und die Messposition trotzdem weit vom Listenende entfernt
        // bleibt: am Ende füllt die LazyColumn die durch den Collapse entstehende Lücke auf,
        // indem sie den Inhalt nachrutschen lässt — das würde die Messung überlagern.
        const val ITEM_COUNT = 60
        const val UNCHECKED_COUNT = 20

        /**
         * Jeder fünfte Eintrag ist mehrzeilig. Die von den Tests direkt vermessenen Items
         * ([TARGET], [NEIGHBOUR], [TARGET_VISIBLE]) bleiben einzeilig — die langen Nachbarn
         * machen Layout und Anker-Mathematik trotzdem höhen-variabel.
         */
        const val LONG_ITEM_EVERY = 5

        /**
         * Off-Screen-Konstellation: visueller Index 26 (= "Item 25") nach oben. Der Separator
         * (visueller Index 20) liegt dann oberhalb des Viewports, [TARGET] und [NEIGHBOUR]
         * stehen mitten im Bild — genau die Konstellation, die den Geist erzeugte.
         */
        const val SCROLL_DEST_OFFSCREEN = 26
        const val TARGET = "Item 28"
        const val NEIGHBOUR = "Item 29"

        /**
         * Sichtbares Ziel: so scrollen, dass noch unchecked Items **über** dem Separator stehen
         * (LazyList verankert dann auf dem ersten sichtbaren unchecked Item, nicht auf dem
         * Separator) und [TARGET_VISIBLE] samt Zielposition im Bild bleibt.
         */
        const val SCROLL_DEST_VISIBLE = UNCHECKED_COUNT - 4
        const val TARGET_VISIBLE = "Item 21"

        /** Frameschritt für die Sampling-Schleifen (≈ 2 Frames @ 60 Hz). */
        const val SAMPLE_STEP_MS = 32L

        /** Kein Nachbar darf pro [SAMPLE_STEP_MS] weiter springen als das — sonst Teleport. */
        const val MAX_JUMP_PX = 40f
    }

    /**
     * Baut die Liste und hostet den Editor. `onCheckedChange` bildet
     * [NoteEditorViewModel.updateChecklistItemChecked] für MANUAL nach: Flip **und** Sort im
     * selben State-Snapshot (die v2.5.0-Invariante).
     */
    private fun setContent(scrollTopOnUncheck: Boolean = false) {
        itemsState.value = (0 until ITEM_COUNT).map { i ->
            ChecklistItemState(
                id = "id-$i",
                text = if (i % LONG_ITEM_EVERY == 0) {
                    "Item $i — dieser Eintrag ist absichtlich deutlich länger und bricht " +
                        "über mehrere Zeilen um, damit die Messungen variable Zeilenhöhen abdecken"
                } else {
                    "Item $i"
                },
                isChecked = i >= UNCHECKED_COUNT,
                order = i,
                originalOrder = i
            )
        }
        rule.setContent {
            var items by itemsState
            MaterialTheme {
                // createComposeRule hostet in einer nackten ComponentActivity: der Content läuft
                // edge-to-edge unter Status- und Navigationsleiste. Ohne safeDrawingPadding läge
                // der obere Teil der Liste unter der Statusbar (auf dem Gerät sichtbar
                // abgeschnitten) und die gemessenen Bounds hätten einen konstanten Versatz.
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        ChecklistEditor(
                            items = items,
                            scope = rememberCoroutineScope(),
                            focusNewItemId = null,
                            currentSortOption = ChecklistSortOption.MANUAL,
                            scrollTopOnUncheck = scrollTopOnUncheck,
                            checklistScrollAction = scrollActions,
                            onTextChange = { _, _ -> },
                            onCheckedChange = { id, checked ->
                                val flipped = items.map {
                                    if (it.id == id) it.copy(isChecked = checked) else it
                                }
                                items = (
                                    flipped.filter { !it.isChecked }.sortedBy { it.originalOrder } +
                                        flipped.filter { it.isChecked }.sortedBy { it.originalOrder }
                                    ).mapIndexed { index, item -> item.copy(order = index) }
                                // Wie NoteEditorViewModel.updateChecklistItemChecked: ScrollToTop
                                // feuert beim Commit — beim aufgeschobenen Uncheck also erst
                                // nach dem Collapse.
                                if (!checked && scrollTopOnUncheck) {
                                    scrollActions.tryEmit(
                                        NoteEditorViewModel.ChecklistScrollAction.ScrollToTop
                                    )
                                }
                            },
                            onDelete = {},
                            onAddNewItemAfter = {},
                            onCopyText = {},
                            onDuplicate = {},
                            onCopyToChecklist = {},
                            onAddToCalendar = {},
                            onAddItemAtEnd = {},
                            onMove = { _, _ -> },
                            onFocusHandled = {},
                            onSortClick = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        // Placement-Animations-Gate öffnet erst nach dem Settle des Initial-Layouts.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(INITIAL_SETTLE_MS)
    }

    /** Scrollt die LazyColumn auf den angegebenen visuellen Index (Item landet oben). */
    private fun scrollToIndex(index: Int) {
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        rule.waitForIdle()
    }

    /**
     * Bounds eines Item-Textfelds, oder null wenn das Item nicht **sichtbar** ist.
     *
     * `LazyLayoutCacheWindow(0.55f)` hält Items außerhalb des Viewports composiert — sie stehen
     * also weiter im Semantics-Baum. `boundsInRoot` ist aber am Viewport geclippt, weggeclippte
     * Knoten liefern ein Rect ohne Höhe. Genau das ist hier das Sichtbarkeitskriterium.
     */
    private fun boundsOf(itemText: String): androidx.compose.ui.geometry.Rect? {
        val list = listBounds()
        return rule.onAllNodes(hasText(itemText)).fetchSemanticsNodes().firstOrNull()
            ?.boundsInRoot
            // Höhe 0 = weggeclippt. Ein Rect oberhalb des Listenanfangs stammt von einem kurz
            // abgelösten LayoutNode (boundsInRoot fällt dann auf den Ursprung zurück) und ist
            // keine echte Position — ein geparkter Geist läge am Listenanfang, nicht darüber.
            ?.takeIf { it.height > 0f && it.top >= list.top }
    }

    /** Bounds der LazyColumn selbst — Bezugsrahmen für „sichtbar" und für den Viewport-Anfang. */
    private fun listBounds(): androidx.compose.ui.geometry.Rect =
        rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot

    /**
     * Die Checkbox derselben Zeile: das einzige toggleable Element, dessen vertikale Mitte
     * innerhalb der Bounds des Textfelds liegt. Robuster als ein Ancestor-Pfad — der Row
     * selbst hat keinen Semantics-Knoten.
     */
    private fun tapCheckbox(itemText: String) {
        val row = requireNotNull(boundsOf(itemText)) { "$itemText ist nicht sichtbar" }
        val sameRow = SemanticsMatcher("in row of $itemText") { node: SemanticsNode ->
            val center = node.boundsInRoot.center.y
            center > row.top && center < row.bottom
        }
        rule.onNode(isToggleable() and sameRow).performClick()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Geist-Regression
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Uncheck weit unten, Ziel (Separator-Slot) oberhalb des Viewports.
     *
     * Zwei Aussagen in einem Test, weil sie dieselbe Messreihe teilen:
     * - **Parking-Signatur:** das Item darf zu keinem Zeitpunkt am oberen Viewport-Rand kleben —
     *   genau so verhielt sich der geparkte Geist.
     * - **Collapse an Ort und Stelle:** bis zum Verschwinden bleibt der obere Rand des Items
     *   stehen, nur die Höhe geht auf 0.
     */
    @Test
    fun uncheckMitZielOffscreenParktNichtAmOberenRand() {
        setContent()
        scrollToIndex(SCROLL_DEST_OFFSCREEN)
        // Erst jetzt die Uhr anhalten — sonst bliebe der Scroll unfertig stehen
        // und liefe in den ersten Messframe hinein.
        rule.mainClock.autoAdvance = false

        val target = TARGET
        val start = requireNotNull(boundsOf(target)) { "Testaufbau: $target muss sichtbar sein" }
        val listTop = listBounds().top
        // Vorbedingung: das Item steht deutlich unterhalb des Viewport-Anfangs.
        assertTrue("Testaufbau: $target darf nicht schon oben stehen", start.top > listTop + ROW_PITCH_PX)

        tapCheckbox(target)

        var sawShrink = false
        var disappeared = false
        repeat(COLLAPSE_FRAMES) {
            rule.mainClock.advanceTimeBy(SAMPLE_STEP_MS)
            val bounds = boundsOf(target)
            if (bounds == null) {
                disappeared = true
                return@repeat
            }
            assertTrue(
                "Geist: $target klebt am oberen Viewport-Rand (top=${bounds.top}, Listenanfang=$listTop)",
                bounds.top > listTop + ROW_PITCH_PX
            )
            if (!disappeared) {
                assertEquals(
                    "$target ist beim Collapse verrutscht statt an Ort und Stelle zu schrumpfen",
                    start.top,
                    bounds.top,
                    COLLAPSE_DRIFT_TOLERANCE_PX
                )
                if (bounds.height < start.height - 1f) sawShrink = true
            }
        }
        assertTrue("Item ist nicht an Ort und Stelle kollabiert", sawShrink)
        assertTrue("Item ist nach dem Collapse nicht verschwunden", disappeared)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Teleport-Detektor
    // ═══════════════════════════════════════════════════════════════════════

    /** Der Nachbar unter dem kollabierenden Item rückt kontinuierlich nach — kein Sprung. */
    @Test
    fun nachbarRuecktOhneTeleportNach() {
        setContent()
        scrollToIndex(SCROLL_DEST_OFFSCREEN)
        // Erst jetzt die Uhr anhalten — sonst bliebe der Scroll unfertig stehen
        // und liefe in den ersten Messframe hinein.
        rule.mainClock.autoAdvance = false

        val neighbour = NEIGHBOUR
        val before = requireNotNull(boundsOf(neighbour)).top
        tapCheckbox(TARGET)

        var previous = before
        repeat(COLLAPSE_FRAMES) {
            rule.mainClock.advanceTimeBy(SAMPLE_STEP_MS)
            val top = boundsOf(neighbour)?.top ?: return@repeat
            assertTrue(
                "Teleport: $neighbour sprang von $previous auf $top in ${SAMPLE_STEP_MS}ms",
                kotlin.math.abs(top - previous) <= MAX_JUMP_PX
            )
            previous = top
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Endzustand
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Nach dem Commit: Reihenfolge korrekt (Item zurück an seiner originalOrder-Position),
     * das Item selbst off-screen, und der Viewport pixelstabil. Letzteres wird daran gemessen,
     * dass der Nachbar **exakt** den Platz des entfernten Items einnimmt: verschöbe der Commit
     * den Viewport um eine Zeile — der klassische Fehler bei falscher Anker-Kompensation —,
     * läge er eine Zeilenhöhe daneben.
     */
    @Test
    fun endzustandNachCommitIstKorrektUndPixelstabil() {
        setContent()
        scrollToIndex(SCROLL_DEST_OFFSCREEN)
        // Erst jetzt die Uhr anhalten — sonst bliebe der Scroll unfertig stehen
        // und liefe in den ersten Messframe hinein.
        rule.mainClock.autoAdvance = false

        val neighbour = NEIGHBOUR
        val targetSlotTop = requireNotNull(boundsOf(TARGET)).top
        tapCheckbox(TARGET)
        rule.mainClock.advanceTimeBy(UNCHECK_COLLAPSE_TOTAL_MS + COMMIT_SETTLE_MS)

        // MANUAL sortiert unchecked nach originalOrder → das Item landet ans Ende der
        // unchecked-Sektion, also direkt vor dem Separator.
        val toggled = 28
        assertEquals(
            "$TARGET muss ans Ende der unchecked-Sektion einsortiert werden",
            (0 until UNCHECKED_COUNT).map { "id-$it" } + "id-$toggled" +
                (UNCHECKED_COUNT until ITEM_COUNT).filter { it != toggled }.map { "id-$it" },
            itemsState.value.map { it.id }
        )
        assertTrue("$TARGET darf nach dem Commit nicht mehr sichtbar sein", boundsOf(TARGET) == null)
        assertEquals(
            "Viewport verschoben — $neighbour muss exakt den Platz des entfernten Items einnehmen",
            targetSlotTop,
            requireNotNull(boundsOf(neighbour)).top,
            ANCHOR_TOLERANCE_PX
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Sichtbares Ziel — Collapse an Ort und Stelle + Aufwachsen am Ziel
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Auch mit sichtbarem Ziel gibt es **kein** Aufwärts-Placement mehr (genau das erzeugte die
     * „Item wandert nach oben"-Artefakte): das Item kollabiert an Ort und Stelle, committet und
     * wächst danach an seiner Zielposition am Ende der unchecked-Sektion wieder auf.
     */
    @Test
    fun uncheckMitSichtbaremZielKollabiertUndWaechstAmZielWiederAuf() {
        setContent()
        scrollToIndex(SCROLL_DEST_VISIBLE)
        // Erst jetzt die Uhr anhalten — sonst bliebe der Scroll unfertig stehen
        // und liefe in den ersten Messframe hinein.
        rule.mainClock.autoAdvance = false

        val target = TARGET_VISIBLE
        val start = requireNotNull(boundsOf(target)) { "Testaufbau: $target muss sichtbar sein" }
        tapCheckbox(target)

        // Phase 1 (nur bis zum Ende des Collapse — danach steht das Item schon am Ziel):
        // Oberkante bleibt stehen, Höhe geht auf 0. Kein Wandern nach oben.
        var sawShrink = false
        repeat(PHASE1_FRAMES) {
            rule.mainClock.advanceTimeBy(SAMPLE_STEP_MS)
            val bounds = boundsOf(target) ?: return@repeat
            assertEquals(
                "$target ist beim Collapse verrutscht statt an Ort und Stelle zu schrumpfen",
                start.top,
                bounds.top,
                COLLAPSE_DRIFT_TOLERANCE_PX
            )
            if (bounds.height < start.height - 1f) sawShrink = true
        }
        assertTrue("Item ist nicht an Ort und Stelle kollabiert", sawShrink)

        // Phase 2: Commit + Aufwachsen. Das Ziel (Ende der unchecked-Sektion, direkt über dem
        // Separator) liegt im Bild — dort muss das Item mit voller Höhe wieder stehen.
        rule.mainClock.advanceTimeBy(UNCHECK_COLLAPSE_TOTAL_MS + COMMIT_SETTLE_MS)
        val end = requireNotNull(boundsOf(target)) { "$target muss am sichtbaren Ziel wieder erscheinen" }
        assertTrue(
            "$target muss oberhalb seiner Ausgangsposition landen (end=${end.top}, start=${start.top})",
            end.top < start.top - 1f
        )
        assertEquals("$target muss am Ziel wieder volle Höhe haben", start.height, end.height, 2f)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Scroll-to-Top an — Commit direkt nach dem Collapse, dann Scroll
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Bei aktivem Scroll-to-Top wird der Commit **nicht** um den Spring-Nachlauf verzögert
     * (der direkt folgende Scroll übernimmt und maskiert die Bewegung) und die Row wächst am
     * Ziel nicht auf (kein Layout-Pumpen unter dem laufenden `animateScrollToItem`). Gemessen:
     * Reorder im Model direkt nach dem Collapse, danach steht die Liste oben.
     */
    @Test
    fun scrollTopOnUncheckCommittetDirektNachCollapseUndScrolltNachOben() {
        setContent(scrollTopOnUncheck = true)
        scrollToIndex(SCROLL_DEST_OFFSCREEN)
        // Erst jetzt die Uhr anhalten — sonst bliebe der Scroll unfertig stehen
        // und liefe in den ersten Messframe hinein.
        rule.mainClock.autoAdvance = false

        val toggledId = "id-28"
        tapCheckbox(TARGET)

        // Ohne Settle-Delay muss der Commit direkt nach dem Collapse (250 ms) im Model stehen —
        // der Scroll-off-Pfad committet erst bei 450 ms (vgl. Test 3).
        rule.mainClock.advanceTimeBy(EARLY_COMMIT_MS)
        assertEquals(
            "Commit muss ohne Settle-Delay direkt nach dem Collapse erfolgen",
            UNCHECKED_COUNT,
            itemsState.value.indexOfFirst { it.id == toggledId }
        )

        // Der beim Commit gefeuerte ScrollToTop muss die Liste nach oben bringen.
        rule.mainClock.advanceTimeBy(SCROLL_TO_TOP_SETTLE_MS)
        requireNotNull(boundsOf("Item 1")) { "Nach ScrollToTop muss der Listenanfang sichtbar sein" }
    }
}

/** Wartezeit, bis das Placement-Animations-Gate in ChecklistEditor offen ist. */
private const val INITIAL_SETTLE_MS = 400L

/** Frames, über die Collapse (250 ms) + Placement-Spring vermessen werden. */
private const val COLLAPSE_FRAMES = 24

/**
 * Frames für die reine Collapse-Phase (8 × 32 ms ≈ 256 ms ≥ 250 ms Collapse). Test 4 misst nur
 * bis hierhin die Ortsfestigkeit — ab dem Commit (450 ms) steht das Item bereits am Ziel.
 */
private const val PHASE1_FRAMES = 8

/**
 * Grobe Zeilenhöhe in px auf Testgeräten. Nur als Sicherheitsabstand zum Viewport-Anfang benutzt
 * — der geparkte Geist saß direkt am Rand, echte Positionen liegen weit darunter.
 */
private const val ROW_PITCH_PX = 140f

/**
 * Der gemessene Textknoten sitzt vertikal zentriert in der Row; beim Clippen wandert seine
 * Oberkante ein paar Pixel mit. Toleranz gegen genau diesen Effekt — ein echter Sprung wäre
 * eine ganze Zeilenhöhe.
 */
private const val COLLAPSE_DRIFT_TOLERANCE_PX = 24f

/** Reicht für den Commit + Reorder-Pass. */
private const val COMMIT_SETTLE_MS = 400L

/**
 * Messpunkt für den Scroll-to-Top-Pfad: nach dem Collapse (250 ms) plus etwas Frame-Puffer,
 * aber deutlich vor dem 450-ms-Commit des Scroll-off-Pfads.
 */
private const val EARLY_COMMIT_MS = 350L

/** `animateScrollToItem` über ~26 teils mehrzeilige Items braucht Zeit zum Auslaufen. */
private const val SCROLL_TO_TOP_SETTLE_MS = 2_000L

/** Collapse (250 ms) + Spring-Nachlauf (200 ms) — danach committet die Row. */
private const val UNCHECK_COLLAPSE_TOTAL_MS = 450L

/**
 * Die Nachbar-Spring läuft asymptotisch aus und legt in den letzten Frames noch ein bis zwei
 * Pixel zurück. Der Commit darf darüber hinaus nichts verschieben.
 */
private const val ANCHOR_TOLERANCE_PX = 8f
