package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.images.ImageCompressionMode
import dev.dettmer.simplenotes.ui.editor.components.WordCounterVisibility
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.toEnumOrDefault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Editor-Teil des Display-Screens. Alles reine Prefs; `NoteEditorViewModel` liest sie beim Öffnen
 * einer Notiz, eine Änderung gilt also ab der nächsten.
 */
class EditorSettings(private val ctx: SettingsContext) {
    private val prefs = ctx.prefs

    // 🆕 v1.9.0
    private val autosave = BoolPref(prefs, Constants.KEY_AUTOSAVE_ENABLED, Constants.DEFAULT_AUTOSAVE_ENABLED)
    val autosaveEnabled: StateFlow<Boolean> = autosave.flow

    // 🆕 v2.8.0: bestehende Textnotizen im Lesemodus öffnen
    private val startInPreview = BoolPref(prefs, Constants.KEY_DEFAULT_START_IN_PREVIEW_MODE, Constants.DEFAULT_START_IN_PREVIEW_MODE)
    val defaultStartInPreviewMode: StateFlow<Boolean> = startInPreview.flow

    // #131 (E11)
    private val formattingBar = BoolPref(prefs, Constants.KEY_FORMATTING_BAR, Constants.DEFAULT_FORMATTING_BAR)
    val formattingBarEnabled: StateFlow<Boolean> = formattingBar.flow

    // UI-Beta: leeres Titelfeld
    private val titleField = BoolPref(prefs, Constants.KEY_TITLE_FIELD, Constants.DEFAULT_TITLE_FIELD)
    val titleFieldEnabled: StateFlow<Boolean> = titleField.flow

    // 🆕 Issue #156
    private val checklistReadMode =
        BoolPref(prefs, Constants.KEY_CHECKLIST_START_IN_READ_MODE, Constants.DEFAULT_CHECKLIST_START_IN_READ_MODE)
    val checklistStartInReadMode: StateFlow<Boolean> = checklistReadMode.flow

    // 🆕 v2.11.0: Cursor in neuen Notizen im Inhalt statt im Titel
    private val focusContent = BoolPref(prefs, Constants.KEY_NEW_NOTE_FOCUS_CONTENT, Constants.DEFAULT_NEW_NOTE_FOCUS_CONTENT)
    val newNoteFocusContent: StateFlow<Boolean> = focusContent.flow

    // 🆕 Issue #112
    private val scrollTopOnUncheck =
        BoolPref(prefs, Constants.KEY_CHECKLIST_SCROLL_TOP_ON_UNCHECK, Constants.DEFAULT_CHECKLIST_SCROLL_TOP_ON_UNCHECK)
    val checklistScrollTopOnUncheck: StateFlow<Boolean> = scrollTopOnUncheck.flow

    // 🆕 v2.11.0: null = keine
    private val _defaultNoteColor = MutableStateFlow(readDefaultNoteColor())
    val defaultNoteColor: StateFlow<String?> = _defaultNoteColor.asStateFlow()

    // 🆕 (#126-Nachgang)
    private val _wordCounterVisibility = MutableStateFlow(readWordCounterVisibility())
    val wordCounterVisibility: StateFlow<WordCounterVisibility> = _wordCounterVisibility.asStateFlow()

    private val _imageCompressionMode = MutableStateFlow(readImageCompressionMode())
    val imageCompressionMode: StateFlow<ImageCompressionMode> = _imageCompressionMode.asStateFlow()

    private val _defaultImageSizePercent = MutableStateFlow(readDefaultImageSizePercent())
    val defaultImageSizePercent: StateFlow<Int> = _defaultImageSizePercent.asStateFlow()

    private val bools = listOf(autosave, startInPreview, formattingBar, titleField, checklistReadMode, focusContent, scrollTopOnUncheck)

    fun setAutosaveEnabled(enabled: Boolean) = autosave.set(enabled)

    fun setDefaultStartInPreviewMode(enabled: Boolean) = startInPreview.set(enabled)

    fun setFormattingBarEnabled(enabled: Boolean) = formattingBar.set(enabled)

    fun setTitleFieldEnabled(enabled: Boolean) = titleField.set(enabled)

    fun setChecklistStartInReadMode(enabled: Boolean) = checklistReadMode.set(enabled)

    fun setNewNoteFocusContent(enabled: Boolean) = focusContent.set(enabled)

    fun setChecklistScrollTopOnUncheck(enabled: Boolean) = scrollTopOnUncheck.set(enabled)

    fun setDefaultNoteColor(hex: String?) {
        prefs.edit { putString(Constants.KEY_DEFAULT_NOTE_COLOR, hex ?: "") }
        _defaultNoteColor.value = hex
    }

    fun setWordCounterVisibility(visibility: WordCounterVisibility) {
        prefs.edit {
            putString(Constants.KEY_WORD_COUNTER_VISIBILITY, visibility.name)
            // Ohne Reset sähe man nach dem Wiedereinschalten nur den Pfeil, falls der Zähler vorher weggetippt war.
            if (visibility != WordCounterVisibility.OFF) {
                putString(Constants.KEY_NOTE_STATS_MODE, Constants.DEFAULT_NOTE_STATS_MODE)
            }
        }
        _wordCounterVisibility.value = visibility
    }

    fun setImageCompressionMode(mode: ImageCompressionMode) {
        prefs.edit { putString(Constants.KEY_IMAGE_COMPRESSION_MODE, mode.name) }
        _imageCompressionMode.value = mode
    }

    fun setDefaultImageSizePercent(percent: Int) {
        prefs.edit { putInt(Constants.KEY_DEFAULT_IMAGE_SIZE_PERCENT, percent) }
        _defaultImageSizePercent.value = percent
    }

    fun reloadFromPrefs() {
        bools.forEach { it.reload() }
        _defaultNoteColor.value = readDefaultNoteColor()
        _wordCounterVisibility.value = readWordCounterVisibility()
        _imageCompressionMode.value = readImageCompressionMode()
        _defaultImageSizePercent.value = readDefaultImageSizePercent()
    }

    private fun readDefaultNoteColor() =
        prefs.getString(Constants.KEY_DEFAULT_NOTE_COLOR, Constants.DEFAULT_NOTE_COLOR)?.takeIf { it.isNotEmpty() }

    // toEnumOrDefault fängt unbekannte Werte ab (z. B. Backup einer neueren App-Version).
    private fun readWordCounterVisibility() =
        prefs.getString(Constants.KEY_WORD_COUNTER_VISIBILITY, Constants.DEFAULT_WORD_COUNTER_VISIBILITY)
            .toEnumOrDefault(WordCounterVisibility.valueOf(Constants.DEFAULT_WORD_COUNTER_VISIBILITY))

    private fun readImageCompressionMode() =
        prefs.getString(Constants.KEY_IMAGE_COMPRESSION_MODE, Constants.DEFAULT_IMAGE_COMPRESSION_MODE)
            .toEnumOrDefault(ImageCompressionMode.valueOf(Constants.DEFAULT_IMAGE_COMPRESSION_MODE))

    private fun readDefaultImageSizePercent() =
        prefs.getInt(Constants.KEY_DEFAULT_IMAGE_SIZE_PERCENT, Constants.DEFAULT_DEFAULT_IMAGE_SIZE_PERCENT)
}
