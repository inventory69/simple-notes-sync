package dev.dettmer.simplenotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import kotlinx.coroutines.launch

/**
 * 🆕 v1.8.0: Konfigurations-Activity beim Platzieren eines Widgets
 *
 * Zeigt eine Liste aller Notizen. User wählt eine aus,
 * die dann im Widget angezeigt wird.
 *
 * Optionen:
 * - Notiz auswählen
 * - Widget initial sperren (optional)
 * - Hintergrund-Transparenz einstellen
 *
 * Unterstützt Reconfiguration (Android 12+): Beim erneuten Öffnen
 * werden die bestehenden Einstellungen als Defaults geladen.
 *
 * 🆕 v1.8.0 (IMPL_025): Auto-Save bei Back-Navigation + Save-FAB
 */
class NoteWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // 🆕 v1.8.0 (IMPL_025): State-Tracking für Auto-Save bei Back-Navigation
    private var currentSelectedNoteId: String? = null
    private var currentLockState: Boolean = false
    private var currentOpacity: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default-Result: Cancelled (falls User zurück-navigiert)
        setResult(RESULT_CANCELED)

        // 🆕 v1.8.0 (IMPL_025): Auto-Save bei Back-Navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Auto-Save nur bei Reconfigure (wenn bereits eine Note konfiguriert war)
                if (currentSelectedNoteId != null) {
                    configureWidget(currentSelectedNoteId!!, currentLockState, currentOpacity)
                } else {
                    finish()
                }
            }
        })

        // Widget-ID aus Intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val storage = NotesStorage(this)

        // Bestehende Konfiguration laden (für Reconfigure)
        lifecycleScope.launch {
            var existingNoteId: String? = null
            var existingLock = false
            var existingOpacity = 1.0f

            try {
                val glanceId = GlanceAppWidgetManager(this@NoteWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                val prefs = getAppWidgetState(
                    this@NoteWidgetConfigActivity,
                    PreferencesGlanceStateDefinition,
                    glanceId
                )
                existingNoteId = prefs[NoteWidgetState.KEY_NOTE_ID]
                existingLock = prefs[NoteWidgetState.KEY_IS_LOCKED] ?: false
                existingOpacity = prefs[NoteWidgetState.KEY_BACKGROUND_OPACITY] ?: 1.0f
            } catch (_: Exception) {
                // Neues Widget — keine bestehende Konfiguration
            }

            // 🆕 v1.8.0 (IMPL_025): Initiale State-Werte für Auto-Save setzen
            currentSelectedNoteId = existingNoteId
            currentLockState = existingLock
            currentOpacity = existingOpacity

            setContent {
                SimpleNotesTheme {
                    NoteWidgetConfigScreen(
                        storage = storage,
                        initialLock = existingLock,
                        initialOpacity = existingOpacity,
                        selectedNoteId = existingNoteId,
                        onNoteSelected = { noteId, isLocked, opacity ->
                            configureWidget(noteId, isLocked, opacity)
                        },
                        // 🆕 v1.8.0 (IMPL_025): Save-FAB Callback
                        onSave = { noteId, isLocked, opacity ->
                            configureWidget(noteId, isLocked, opacity)
                        },
                        // 🆕 v1.8.0 (IMPL_025): Settings-Änderungen tracken für Auto-Save
                        onSettingsChanged = { noteId, isLocked, opacity ->
                            currentSelectedNoteId = noteId
                            currentLockState = isLocked
                            currentOpacity = opacity
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    private fun configureWidget(noteId: String, isLocked: Boolean, opacity: Float) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@NoteWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)

            // Widget-State speichern
            updateAppWidgetState(this@NoteWidgetConfigActivity, glanceId) { prefs ->
                prefs[NoteWidgetState.KEY_NOTE_ID] = noteId
                prefs[NoteWidgetState.KEY_IS_LOCKED] = isLocked
                prefs[NoteWidgetState.KEY_SHOW_OPTIONS] = false
                prefs[NoteWidgetState.KEY_BACKGROUND_OPACITY] = opacity
            }

            // Widget initial rendern
            NoteWidget().update(this@NoteWidgetConfigActivity, glanceId)

            // Erfolg melden
            val resultIntent = Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
            )
            setResult(RESULT_OK, resultIntent)
            
            // 🐛 FIX: Zurück zum Homescreen statt zur MainActivity
            // moveTaskToBack() bringt den Task in den Hintergrund → Homescreen wird sichtbar
            if (!isTaskRoot) {
                finish()
            } else {
                moveTaskToBack(true)
                finish()
            }
        }
    }
}
