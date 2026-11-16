package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.FontSizeScale
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Anzeige-Teil des Display-Screens: Theme, Listen-/Rasteransicht, App-Titel, Vorschau und die
 * Elemente der Notizkarten. Die Hauptliste liest das per `MainDisplaySettings.refresh()` nach.
 */
class AppearanceSettings(private val ctx: SettingsContext) {
    private val prefs = ctx.prefs

    // v2.0.0
    private val _themeMode = MutableStateFlow(ThemePreferences.getThemeMode(prefs))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(ThemePreferences.getColorTheme(prefs))
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(ThemePreferences.getFontSizeScale(prefs))
    val fontSizeScale: StateFlow<FontSizeScale> = _fontSizeScale.asStateFlow()

    // 🆕 v2.11.0
    private val _notePreviewLength = MutableStateFlow(ThemePreferences.getNotePreviewLength(prefs))
    val notePreviewLength: StateFlow<NotePreviewLength> = _notePreviewLength.asStateFlow()

    // UI-Beta: neues Design (Editor und Übersicht)
    private val _newDesign = MutableStateFlow(ThemePreferences.isNewDesign(prefs))
    val newDesign: StateFlow<Boolean> = _newDesign.asStateFlow()

    // 🎨 v1.7.0
    private val _displayMode = MutableStateFlow(readDisplayMode())
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    // 🆕 v2.1.0 (F46)
    private val gridAdaptive = BoolPref(prefs, Constants.KEY_GRID_ADAPTIVE_SCALING, Constants.DEFAULT_GRID_ADAPTIVE_SCALING)
    val gridAdaptiveScaling: StateFlow<Boolean> = gridAdaptive.flow

    private val _gridManualColumns = MutableStateFlow(readGridManualColumns())
    val gridManualColumns: StateFlow<Int> = _gridManualColumns.asStateFlow()

    // 🆕 v1.9.0 (F05)
    private val _customAppTitle = MutableStateFlow(readCustomAppTitle())
    val customAppTitle: StateFlow<String> = _customAppTitle.asStateFlow()

    // 🆕 Issue #100 / #118
    private val noteTimestamp = BoolPref(prefs, Constants.KEY_SHOW_NOTE_TIMESTAMP, Constants.DEFAULT_SHOW_NOTE_TIMESTAMP)
    val showNoteTimestamp: StateFlow<Boolean> = noteTimestamp.flow

    private val noteTypeIcon = BoolPref(prefs, Constants.KEY_SHOW_NOTE_TYPE_ICON, Constants.DEFAULT_SHOW_NOTE_TYPE_ICON)
    val showNoteTypeIcon: StateFlow<Boolean> = noteTypeIcon.flow

    private val hideChecked = BoolPref(prefs, Constants.KEY_HIDE_CHECKED_IN_PREVIEW, Constants.DEFAULT_HIDE_CHECKED_IN_PREVIEW)
    val hideCheckedInPreview: StateFlow<Boolean> = hideChecked.flow

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        ThemePreferences.setThemeMode(prefs, mode)
    }

    fun setColorTheme(theme: ColorTheme) {
        _colorTheme.value = theme
        ThemePreferences.setColorTheme(prefs, theme)
    }

    fun setFontSizeScale(scale: FontSizeScale) {
        _fontSizeScale.value = scale
        ThemePreferences.setFontSizeScale(prefs, scale)
    }

    fun setNotePreviewLength(length: NotePreviewLength) {
        _notePreviewLength.value = length
        ThemePreferences.setNotePreviewLength(prefs, length)
    }

    /** UI-Beta: Die übrigen Activities folgen über den Prefs-Listener im Theme. */
    fun setNewDesign(enabled: Boolean) {
        ThemePreferences.setNewDesign(prefs, enabled)
        _newDesign.value = enabled
    }

    fun setDisplayMode(mode: String) {
        prefs.edit { putString(Constants.KEY_DISPLAY_MODE, mode) }
        _displayMode.value = mode
        Logger.d(TAG, "Display mode changed to: $mode")
    }

    fun setGridAdaptiveScaling(enabled: Boolean) = gridAdaptive.set(enabled)

    fun setGridManualColumns(columns: Int) {
        val clamped = columns.coerceIn(Constants.GRID_MIN_COLUMNS, Constants.GRID_MAX_COLUMNS)
        prefs.edit { putInt(Constants.KEY_GRID_MANUAL_COLUMNS, clamped) }
        _gridManualColumns.value = clamped
    }

    fun setCustomAppTitle(title: String) {
        val sanitized = title.take(Constants.MAX_CUSTOM_APP_TITLE_LENGTH)
        prefs.edit { putString(Constants.KEY_CUSTOM_APP_TITLE, sanitized) }
        _customAppTitle.value = sanitized
    }

    fun setShowNoteTimestamp(enabled: Boolean) = noteTimestamp.set(enabled)

    fun setShowNoteTypeIcon(enabled: Boolean) {
        noteTypeIcon.set(enabled)
        // 🆕 Issue #120: ComposeSettingsActivity.onStop refresht Widgets nicht: ohne diesen Call
        // bliebe das Listen-Widget bis zum nächsten Sync beim alten Icon-Zustand.
        ctx.scope.launch { WidgetUpdateHelper.refreshAllNotesListWidgets(ctx.app) }
    }

    /** 🆕 Issue #118: erledigte Einträge in den Vorschauen ausblenden (Karten, beide Widget-Typen). */
    fun setHideCheckedInPreview(enabled: Boolean) {
        hideChecked.set(enabled)
        ctx.scope.launch { WidgetUpdateHelper.refreshAllWidgets(ctx.app) }
    }

    fun reloadFromPrefs() {
        _themeMode.value = ThemePreferences.getThemeMode(prefs)
        _colorTheme.value = ThemePreferences.getColorTheme(prefs)
        _fontSizeScale.value = ThemePreferences.getFontSizeScale(prefs)
        _notePreviewLength.value = ThemePreferences.getNotePreviewLength(prefs)
        _newDesign.value = ThemePreferences.isNewDesign(prefs)
        _displayMode.value = readDisplayMode()
        _gridManualColumns.value = readGridManualColumns()
        _customAppTitle.value = readCustomAppTitle()
        listOf(gridAdaptive, noteTimestamp, noteTypeIcon, hideChecked).forEach { it.reload() }
    }

    private fun readDisplayMode() =
        prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE) ?: Constants.DEFAULT_DISPLAY_MODE

    private fun readGridManualColumns() = prefs.getInt(Constants.KEY_GRID_MANUAL_COLUMNS, Constants.DEFAULT_GRID_MANUAL_COLUMNS)

    private fun readCustomAppTitle() =
        prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE) ?: Constants.DEFAULT_CUSTOM_APP_TITLE

    private companion object {
        const val TAG = "AppearanceSettings"
    }
}
