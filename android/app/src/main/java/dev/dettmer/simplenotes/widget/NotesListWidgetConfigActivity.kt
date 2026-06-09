package dev.dettmer.simplenotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.launch

class NotesListWidgetConfigActivity : ComponentActivity() {
    companion object {
        private const val TAG = "NotesListWidgetConfig"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch {
            var initialSortOption = SortOption.UPDATED_AT
            var initialSortDir = SortDirection.DESCENDING
            var initialFilter = NoteFilter.ALL
            var initialOpacity = 1.0f
            var initialApplyOpacityToCards = false

            try {
                val glanceId = GlanceAppWidgetManager(this@NotesListWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                val prefs = getAppWidgetState(
                    this@NotesListWidgetConfigActivity,
                    PreferencesGlanceStateDefinition,
                    glanceId
                )
                initialSortOption = SortOption.fromPrefsValue(
                    prefs[NotesListWidgetState.KEY_SORT_OPTION] ?: SortOption.UPDATED_AT.prefsValue
                )
                initialSortDir = SortDirection.fromPrefsValue(
                    prefs[NotesListWidgetState.KEY_SORT_DIRECTION] ?: SortDirection.DESCENDING.prefsValue
                )
                initialFilter = NoteFilter.fromPrefsValue(
                    prefs[NotesListWidgetState.KEY_NOTE_FILTER] ?: NoteFilter.ALL.prefsValue
                )
                initialOpacity = prefs[NotesListWidgetState.KEY_BACKGROUND_OPACITY] ?: 1.0f
                initialApplyOpacityToCards = prefs[NotesListWidgetState.KEY_APPLY_OPACITY_TO_CARDS] ?: false
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to load existing widget config, using defaults: ${e.message}")
            }

            setContent {
                val widgetPrefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                SimpleNotesTheme(
                    themeMode = ThemePreferences.getThemeMode(widgetPrefs),
                    colorTheme = ThemePreferences.getColorTheme(widgetPrefs)
                ) {
                    NotesListWidgetConfigScreen(
                        initialSortOption = initialSortOption,
                        initialSortDirection = initialSortDir,
                        initialFilter = initialFilter,
                        initialOpacity = initialOpacity,
                        initialApplyOpacityToCards = initialApplyOpacityToCards,
                        onSave = { sortOption, sortDir, filter, opacity, applyOpacityToCards ->
                            onSave(sortOption, sortDir, filter, opacity, applyOpacityToCards)
                        }
                    )
                }
            }
        }
    }

    private fun onSave(
        sortOption: SortOption,
        sortDir: SortDirection,
        filter: NoteFilter,
        opacity: Float,
        applyOpacityToCards: Boolean
    ) {
        lifecycleScope.launch {
            val glanceAppWidgetManager = GlanceAppWidgetManager(this@NotesListWidgetConfigActivity)
            try {
                val glanceId = glanceAppWidgetManager.getGlanceIdBy(appWidgetId)

                updateAppWidgetState(this@NotesListWidgetConfigActivity, glanceId) { prefs ->
                    prefs[NotesListWidgetState.KEY_SORT_OPTION] = sortOption.prefsValue
                    prefs[NotesListWidgetState.KEY_SORT_DIRECTION] = sortDir.prefsValue
                    prefs[NotesListWidgetState.KEY_NOTE_FILTER] = filter.prefsValue
                    prefs[NotesListWidgetState.KEY_BACKGROUND_OPACITY] = opacity
                    prefs[NotesListWidgetState.KEY_APPLY_OPACITY_TO_CARDS] = applyOpacityToCards
                    prefs[NotesListWidgetState.KEY_LAST_UPDATED] = System.currentTimeMillis()
                }

                NotesListWidget().update(this@NotesListWidgetConfigActivity, glanceId)
            } catch (e: IllegalArgumentException) {
                Logger.d(TAG, "Glance ID invalid (likely removed widget): ${e.message}")
            } catch (e: Exception) {
                Logger.w(TAG, "Widget update failed, closing anyway: ${e.message}")
            } finally {
                val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
