package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.dettmer.simplenotes.utils.Logger

/**
 * Wie viele Zeilen ein Widget rendern darf (Issue #154).
 *
 * `AppWidgetService` stellt die RemoteViews **aller** Widgets eines Hosts in einem Schwung zu —
 * gemessen liegen die Aufrufe 3 ms auseinander. Die Zustellung ist *oneway*, und für
 * asynchrone Binder-Transaktionen steht nur die Hälfte des 1-MB-Puffers eines Prozesses zur
 * Verfügung. Ist der Launcher gerade kalt gestartet und leert den Puffer nicht sofort, teilen
 * sich also alle Widgets zusammen rund 500 KB. Wer darüber liegt, bekommt
 * `TransactionTooLargeException`; `AppWidgetService` verwirft dann die Host-Callbacks und
 * **alle** Widgets dieses Launchers bleiben bis zu dessen Neustart tot.
 *
 * Daraus folgt der Zuschnitt hier: ein gemeinsames Byte-Budget, geteilt durch die Zahl der
 * platzierten Widgets. Eine feste Zeilenzahl je Widget könnte das nicht leisten — sie wäre bei
 * einem Widget unnötig streng und bei fünf immer noch zu großzügig.
 *
 * **Messwerte** (Pixel-Emulator, API 37, Pixel-Launcher, Stresstest
 * `.claude/skills/verify-android/widget-stress.sh`, Launcher vor jedem Lauf kalt):
 *
 * | Konfiguration                        | Transaktion | Ergebnis      |
 * |--------------------------------------|-------------|---------------|
 * | Liste allein, 50 Zeilen              | 460 KB      | 5/5 sauber    |
 * | Liste allein, 60 Zeilen              | 550 KB      | 5/5 Ausfall   |
 * | Liste 24 + Checkliste 25 + Markdown 20 | 428 KB    | 8/8 sauber    |
 * | Liste 30 + Checkliste 25 + Markdown 20 | 481 KB    | 3/5 Ausfall   |
 *
 * Kosten je Zeile, gemessen mit [dev.dettmer.simplenotes.widget.WidgetPayloadMeasurementTest]
 * über `GlanceAppWidget.compose()` und `Parcel.dataSize()` (gegengeprüft an drei echten
 * Fehlschlägen, Abweichung unter 1 %).
 *
 * [SHARED_BUDGET_BYTES] liegt bewusst unter den gemessenen ~500 KB: die Messung stammt von
 * einem Launcher, und wie viel Puffer ein fremder beim Kaltstart selbst belegt, ist
 * geräteabhängig.
 */
internal object WidgetPayloadBudget {
    /** Gemeinsames Budget aller platzierten Widgets. ~20 % Abstand zur gemessenen Grenze. */
    private const val SHARED_BUDGET_BYTES = 400 * 1024

    /** Rahmen eines Widgets ohne Listeninhalt: Hintergrund, TitleBar, FAB-Overlay. */
    private const val WIDGET_BASE_BYTES = 14 * 1024

    private const val LIST_ROW_BYTES = 9_200
    private const val CHECKLIST_ITEM_BYTES = 4_600
    private const val MARKDOWN_ITEM_BYTES = 3_200

    /**
     * Obergrenzen aus der Zeit vor Issue #154. Darüber hinaus zu gehen bringt nichts: so viel
     * zeigt kein Widget, und die Liste würde nur teurer.
     */
    private const val MAX_LIST_ROWS = 50
    private const val MAX_CHECKLIST_ITEMS = 100
    private const val MAX_MARKDOWN_ITEMS = 50

    /** Untergrenze, damit ein Widget bei sehr vielen Instanzen nicht leer wirkt. */
    private const val MIN_ITEMS = 5

    private const val TAG = "WidgetPayloadBudget"

    /**
     * Budget für **ein** Widget. Zählt die platzierten Notiz- und Listen-Widgets; das
     * `NewNoteWidget` bleibt außen vor, weil es eine feste Handvoll Knoten rendert (~10 KB)
     * und mit der Zeilenzahl nichts zu tun hat.
     */
    suspend fun forCurrentWidgets(context: Context): WidgetItemCaps {
        val count = try {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(NoteWidget::class.java).size +
                manager.getGlanceIds(NotesListWidget::class.java).size
        } catch (e: IllegalArgumentException) {
            Logger.d(TAG, "Glance ID invalid (likely removed widget): ${e.message}")
            0
        }
        return forWidgetCount(count).also {
            Logger.d(
                TAG,
                "$count Widget(s) platziert → Liste ${it.listRows}, " +
                    "Checkliste ${it.checklistItems}, Markdown ${it.markdownItems}"
            )
        }
    }

    /** Getrennt von [forCurrentWidgets], damit die Rechnung ohne Gerät testbar bleibt. */
    fun forWidgetCount(widgetCount: Int): WidgetItemCaps {
        val perWidget = SHARED_BUDGET_BYTES / widgetCount.coerceAtLeast(1)
        val forRows = (perWidget - WIDGET_BASE_BYTES).coerceAtLeast(0)
        return WidgetItemCaps(
            listRows = cap(forRows, LIST_ROW_BYTES, MAX_LIST_ROWS),
            checklistItems = cap(forRows, CHECKLIST_ITEM_BYTES, MAX_CHECKLIST_ITEMS),
            markdownItems = cap(forRows, MARKDOWN_ITEM_BYTES, MAX_MARKDOWN_ITEMS)
        )
    }

    private fun cap(budgetBytes: Int, bytesPerItem: Int, max: Int): Int =
        (budgetBytes / bytesPerItem).coerceIn(MIN_ITEMS, max)
}

/** Zeilenbudget eines einzelnen Widgets, siehe [WidgetPayloadBudget]. */
internal data class WidgetItemCaps(
    val listRows: Int,
    val checklistItems: Int,
    val markdownItems: Int
)

/**
 * Reicht die Caps an die Zeilen-Composables durch. Als CompositionLocal statt als Parameter,
 * weil sonst jede Zwischenstufe von [NoteWidgetContent] bis zur Checklist-Ansicht sie nur
 * weiterreichen würde.
 *
 * Der Default ist der Wert für drei Widgets — konservativ, falls ein Aufrufer (Preview, Test)
 * keinen Provider setzt.
 */
internal val LocalWidgetItemCaps = staticCompositionLocalOf {
    WidgetPayloadBudget.forWidgetCount(widgetCount = 3)
}
