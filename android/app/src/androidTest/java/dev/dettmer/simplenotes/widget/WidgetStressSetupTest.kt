package dev.dettmer.simplenotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.NotesStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Richtet den Worst Case für den Widget-Stresstest ein (Issue #154) — **kein Test im engeren
 * Sinn**, sondern das Setup, das `.claude/skills/verify-android/widget-stress.sh` voraussetzt.
 *
 * Notizen und Widget-State lassen sich von hier aus direkt schreiben, weil der Instrumentierungs-
 * Prozess dieselbe Anwendung ist. Über `adb` wäre dafür der Glance-DataStore von Hand zu
 * fälschen; das Konfigurieren über die UI dauert bei jedem Lauf neu.
 *
 *   ./gradlew connectedFdroidDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=dev.dettmer.simplenotes.widget.WidgetStressSetupTest
 */
@RunWith(AndroidJUnit4::class)
class WidgetStressSetupTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Platziert die drei Widgets auf dem Startbildschirm. `requestPinAppWidget` braucht die App
     * im Vordergrund — der Aufrufer startet sie vorher (siehe widget-stress.sh). Der Bestaetigen-
     * Knopf des Launchers wird danach per `input tap` gedrueckt.
     */
    @Test
    fun pinWidgets() {
        val awm = AppWidgetManager.getInstance(context)
        check(awm.isRequestPinAppWidgetSupported) { "Launcher unterstuetzt kein Widget-Pinning" }
        val provider = ComponentName(context, PIN_PROVIDER)
        Log.i(TAG, "Pin-Anfrage fuer $provider: ${awm.requestPinAppWidget(provider, null, null)}")
    }

    /**
     * Loest ein Update aller Widgets aus — der Auslöser des Stresstests.
     *
     * Nicht per `am broadcast APPWIDGET_UPDATE`: das ist ein geschuetzter Broadcast, den die
     * adb-Shell nicht senden darf ("Permission Denial: ... from unknown caller"), sodass der
     * Test stumm nichts misst. [WidgetUpdateHelper] ist ohnehin der Weg, den die App selbst
     * geht (Skill `widget-glance`), also misst das hier den echten Pfad.
     */
    /**
     * Wie [seedWorstCase], laesst die Notiz-Widgets aber leer. Damit traegt praktisch nur das
     * Listen-Widget zur Transaktion bei — noetig, um zu pruefen, ob das Budget je Widget oder
     * fuer alle Widgets zusammen gilt.
     */
    @Test
    fun seedListOnly() = runBlocking {
        seedNotes()
        pointNoteWidgetsAt(listOf("gibt-es-nicht" to true))
        WidgetUpdateHelper.refreshAllWidgets(context)
        delay(COMPOSE_GRACE_MS)
    }

    @Test
    fun refreshAll() {
        runBlocking {
            WidgetUpdateHelper.refreshAllWidgets(context)
            // Glance komponiert asynchron in einem SessionWorker. `am instrument` schiesst den
            // App-Prozess ab, sobald der Test zurueckkehrt ("Killing ... due to finished inst") —
            // ohne dieses Warten stirbt die Komposition vor dem `updateAppWidget`, und der
            // Stresstest misst eine Transaktion, die es nie gab.
            delay(COMPOSE_GRACE_MS)
        }
        Log.i(TAG, "Refresh ausgeloest")
    }

    /**
     * Schreibt Notizen in den App-Speicher und zeigt Widgets um. Auf einem echten Geraet waere
     * das Datenverlust, deshalb laeuft es nur auf dem Emulator — dieselbe Regel wie fuer die
     * Skripte in `.claude/skills/verify-android/`.
     */
    @Before
    fun onlyOnEmulator() {
        assumeTrue(
            "Nur auf dem Emulator: schreibt in den App-Speicher (Build.FINGERPRINT=${Build.FINGERPRINT})",
            Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.startsWith("google/sdk_gphone")
        )
    }

    private suspend fun pointNoteWidgetsAt(targets: List<Pair<String, Boolean>>) {
        // Nicht ueber getGlanceIds(NoteWidget::class): dessen Provider-Mapping fuellt Glance erst
        // beim ersten onUpdate des Receivers. Frisch platzierte Widgets fehlen dort noch.
        val appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, NoteWidgetReceiver::class.java)
        )
        val manager = GlanceAppWidgetManager(context)
        appWidgetIds.forEachIndexed { index, appWidgetId ->
            val (noteId, locked) = targets[index % targets.size]
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, manager.getGlanceIdBy(appWidgetId)) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[NoteWidgetState.KEY_NOTE_ID] = noteId
                    this[NoteWidgetState.KEY_IS_LOCKED] = locked
                }
            }
        }
        Log.i(TAG, "Setup fertig: ${appWidgetIds.size} Notiz-Widget(s), $LIST_NOTES Listennotizen")
    }

    @Test
    fun seedWorstCase() = runBlocking {
        seedNotes()
        pointNoteWidgetsAt(listOf(STRESS_CHECKLIST_ID to false, STRESS_MARKDOWN_ID to true))
        WidgetUpdateHelper.refreshAllWidgets(context)
        delay(COMPOSE_GRACE_MS)
    }

    private suspend fun seedNotes() {
        val storage = NotesStorage(context)

        // Checkliste am Cap: die teuerste Zeilensorte, weil jede Zeile eine eigene Aktion trägt.
        val checklist = Note(
            id = STRESS_CHECKLIST_ID,
            title = "Stress Checkliste",
            content = "",
            deviceId = "stress",
            noteType = NoteType.CHECKLIST,
            checklistItems = List(CHECKLIST_ITEMS) {
                ChecklistItem(id = "stress-c-$it", text = "Eintrag $it mit etwas Text", order = it)
            }
        )
        storage.saveNote(checklist)

        // Markdown am Cap, mit Inline-Formatierung — die geht als Spanned durch den Parcel.
        storage.saveNote(
            Note(
                id = STRESS_MARKDOWN_ID,
                title = "Stress Markdown",
                content = (1..MARKDOWN_PARAGRAPHS).joinToString("\n\n") {
                    "Absatz $it mit **fett**, *kursiv* und ~~durchgestrichen~~ für den Worst Case."
                },
                deviceId = "stress"
            )
        )

        // Volle Liste für das Listen-Widget.
        repeat(LIST_NOTES) { i ->
            storage.saveNote(
                Note(
                    id = "stress-list-$i",
                    title = "Listennotiz $i",
                    content = "Zeile eins\nZeile zwei\nZeile drei\nZeile vier",
                    deviceId = "stress"
                )
            )
        }

    }

    private companion object {
        const val TAG = "WidgetStress"
        const val COMPOSE_GRACE_MS = 15_000L
        /** Ueber `-e pinProvider <FQCN>` von aussen gesetzt; Default ist das Notiz-Widget. */
        val PIN_PROVIDER: String = InstrumentationRegistry.getArguments()
            .getString("pinProvider", "dev.dettmer.simplenotes.widget.NoteWidgetReceiver")
        const val STRESS_CHECKLIST_ID = "stress-checklist"
        const val STRESS_MARKDOWN_ID = "stress-markdown"
        const val CHECKLIST_ITEMS = 150
        const val MARKDOWN_PARAGRAPHS = 120
        const val LIST_NOTES = 100
    }
}
