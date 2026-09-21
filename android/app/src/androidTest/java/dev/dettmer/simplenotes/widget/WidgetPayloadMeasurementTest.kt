package dev.dettmer.simplenotes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.Build
import android.os.Parcel
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.NotesStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Misst, wie groß die RemoteViews-Transaktion eines Widgets wirklich wird (Issue #154).
 *
 * `GlanceAppWidget.compose()` baut exakt dieselbe [android.widget.RemoteViews], die `update()`
 * durch den Binder schickt — inklusive der Größenvarianten aus den Widget-Optionen. Die Größe
 * kommt aus `Parcel.dataSize()`; das ist die Zahl, an der `TransactionTooLargeException` hängt.
 *
 * Warum nicht über logcat: eine Payload-Größe loggt Glance nur, wenn sie **zu groß** war. Der
 * interessante Bereich ist genau der darunter. Gegengeprüft ist das Verfahren trotzdem an drei
 * echten Fehlschlägen (Caps 600/400/400 auf dem Pixel-Emulator, API 37): Glance meldete
 * 2 764 440 / 2 074 300 / 3 682 784 Bytes, dieser Test misst dieselben Konfigurationen mit
 * unter 2 % Abweichung.
 *
 * Kein Assert auf absolute Bytes — die Zahlen sind gerätespezifisch. Das Gate ist der
 * Stresstest in `.claude/skills/verify-android/widget-stress.sh`; das hier ist das Messgerät.
 *
 *   adb shell am instrument -w \
 *     -e class dev.dettmer.simplenotes.widget.WidgetPayloadMeasurementTest \
 *     dev.dettmer.simplenotes.debug.test/androidx.test.runner.AndroidJUnitRunner
 *   adb logcat -d -s WidgetBytes
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetPayloadMeasurementTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = NotesStorage(context)

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

    /**
     * Optionen eines tatsächlich platzierten Widgets. Darin steht `OPTION_APPWIDGET_SIZES` — die
     * Hoch- **und** Querformat-Größe. Glance komponiert für jede davon, und genau dieser Faktor
     * fehlt, wenn man mit einer einzelnen Größe misst.
     */
    private fun optionsOf(receiver: Class<*>): Bundle {
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, receiver))
        check(ids.isNotEmpty()) {
            "Kein platziertes Widget für ${receiver.simpleName} — erst widget-place.sh laufen lassen"
        }
        return awm.getAppWidgetOptions(ids.first())
    }

    private fun bytes(widget: GlanceAppWidget, options: Bundle, state: Preferences): Int = runBlocking {
        val rv = widget.compose(context = context, options = options, state = state)
        val parcel = Parcel.obtain()
        try {
            rv.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun report(label: String, counts: List<Int>, measure: (Int) -> Int) {
        val points = counts.map { it to measure(it) }
        points.forEach { (n, b) -> Log.i(TAG, "%-26s n=%-4d %8d B (%6.1f KB)".format(label, n, b, b / 1024f)) }
        val (n0, b0) = points.first()
        val (n1, b1) = points.last()
        if (n1 > n0) {
            // Zwei-Punkt-Steigung über die Spannweite; die Kurve ist in der Praxis linear.
            val perRow = (b1 - b0).toFloat() / (n1 - n0)
            Log.i(TAG, "%-26s => Grundlast %.1f KB, je Zeile %.2f KB".format(label, (b0 - perRow * n0) / 1024f, perRow / 1024f))
        }
    }

    private fun seedChecklist(items: Int, locked: Boolean = false): Preferences {
        runBlocking {
            storage.saveNote(
                Note(
                    id = MEASURE_NOTE_ID,
                    title = "Messnotiz",
                    content = "",
                    deviceId = "measure",
                    noteType = NoteType.CHECKLIST,
                    checklistItems = List(items) {
                        ChecklistItem(id = "m-$it", text = "Eintrag $it mit etwas Text", order = it)
                    }
                )
            )
        }
        return preferencesOf(
            NoteWidgetState.KEY_NOTE_ID to MEASURE_NOTE_ID,
            NoteWidgetState.KEY_IS_LOCKED to locked
        )
    }

    private fun seedMarkdown(paragraphs: Int): Preferences {
        runBlocking {
            storage.saveNote(
                Note(
                    id = MEASURE_NOTE_ID,
                    title = "Messnotiz",
                    content = (1..paragraphs).joinToString("\n\n") {
                        "Absatz $it mit **fett**, *kursiv* und ~~durchgestrichen~~ für den Worst Case."
                    },
                    deviceId = "measure"
                )
            )
        }
        return preferencesOf(NoteWidgetState.KEY_NOTE_ID to MEASURE_NOTE_ID)
    }

    @Test
    fun measureNoteWidget() {
        val options = optionsOf(NoteWidgetReceiver::class.java)
        report("Checkliste entsperrt", listOf(0, 25, 50, 100)) { n ->
            bytes(NoteWidget(), options, seedChecklist(n))
        }
        report("Checkliste gesperrt", listOf(0, 25, 50, 100)) { n ->
            bytes(NoteWidget(), options, seedChecklist(n, locked = true))
        }
        report("Markdown", listOf(0, 10, 25, 50)) { n ->
            bytes(NoteWidget(), options, seedMarkdown(n))
        }
    }

    @Test
    fun measureNotesListWidget() {
        val options = optionsOf(NotesListWidgetReceiver::class.java)
        report("Liste (Notizen)", listOf(0, 12, 25, 50)) { n ->
            runBlocking {
                storage.deleteAllNotes()
                repeat(n) { i ->
                    storage.saveNote(
                        Note(
                            id = "measure-list-$i",
                            title = "Listennotiz $i",
                            content = "Zeile eins\nZeile zwei\nZeile drei\nZeile vier",
                            deviceId = "measure"
                        )
                    )
                }
            }
            bytes(NotesListWidget(), options, preferencesOf())
        }
    }

    private companion object {
        const val TAG = "WidgetBytes"
        const val MEASURE_NOTE_ID = "measure-note"
    }
}
