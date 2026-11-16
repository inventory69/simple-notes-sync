package dev.dettmer.simplenotes.ui.main

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.ui.main.components.SECTION_FOLDERS
import dev.dettmer.simplenotes.ui.main.components.SECTION_NOTES
import dev.dettmer.simplenotes.ui.main.components.SECTION_PINNED
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Anzeige-Einstellungen der Hauptliste, gespiegelt aus den SharedPreferences: Darstellung, Raster,
 * Vorschaulänge, Kartenelemente, App-Titel, Abschnitts-Reihenfolge und eingeklappte Abschnitte.
 *
 * Die Einstellungs-Screens schreiben die Prefs, [refresh] holt sie beim Zurückkehren ab
 * (`ComposeMainActivity.onResume`). Reihenfolge und Einklappzustand ändert die Liste selbst.
 */
class MainDisplaySettings(
    private val prefs: SharedPreferences,
    private val currentFolder: () -> String?
) {
    private val _displayMode = MutableStateFlow(readDisplayMode())
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    // 🆕 v2.1.0 (F46)
    private val _gridAdaptiveScaling = MutableStateFlow(readGridAdaptiveScaling())
    val gridAdaptiveScaling: StateFlow<Boolean> = _gridAdaptiveScaling.asStateFlow()

    private val _gridManualColumns = MutableStateFlow(readGridManualColumns())
    val gridManualColumns: StateFlow<Int> = _gridManualColumns.asStateFlow()

    // 🆕 v2.11.0
    private val _notePreviewLength = MutableStateFlow(ThemePreferences.getNotePreviewLength(prefs))
    val notePreviewLength: StateFlow<NotePreviewLength> = _notePreviewLength.asStateFlow()

    // 🆕 Issue #100: Zeitstempel/Icon auf Notizkarten ausblendbar
    private val _showNoteTimestamp = MutableStateFlow(readShowNoteTimestamp())
    val showNoteTimestamp: StateFlow<Boolean> = _showNoteTimestamp.asStateFlow()

    private val _showNoteTypeIcon = MutableStateFlow(readShowNoteTypeIcon())
    val showNoteTypeIcon: StateFlow<Boolean> = _showNoteTypeIcon.asStateFlow()

    private val _hideCheckedInPreview = MutableStateFlow(readHideCheckedInPreview())
    val hideCheckedInPreview: StateFlow<Boolean> = _hideCheckedInPreview.asStateFlow()

    // 🆕 v1.9.0 (F05)
    private val _customAppTitle = MutableStateFlow(readCustomAppTitle())
    val customAppTitle: StateFlow<String> = _customAppTitle.asStateFlow()

    private val _sectionOrder = MutableStateFlow(readSectionOrder())
    val sectionOrder: StateFlow<List<String>> = _sectionOrder.asStateFlow()

    private val _collapsedSections = MutableStateFlow(collapsedSectionsFor(null))
    val collapsedSections: StateFlow<Set<String>> = _collapsedSections.asStateFlow()

    /** Liest alle von den Einstellungs-Screens geschriebenen Werte neu. */
    fun refresh() {
        _displayMode.value = readDisplayMode()
        _gridAdaptiveScaling.value = readGridAdaptiveScaling()
        _gridManualColumns.value = readGridManualColumns()
        _notePreviewLength.value = ThemePreferences.getNotePreviewLength(prefs)
        _showNoteTimestamp.value = readShowNoteTimestamp()
        _showNoteTypeIcon.value = readShowNoteTypeIcon()
        _hideCheckedInPreview.value = readHideCheckedInPreview()
        _customAppTitle.value = readCustomAppTitle()
    }

    /** Tauscht die Positionen zweier Abschnitte. No-op bei unbekanntem oder fehlendem Schlüssel. */
    fun swapSections(sectionA: String, sectionB: String?) {
        if (sectionB == null || sectionA == sectionB) return
        val current = _sectionOrder.value
        val idxA = current.indexOf(sectionA)
        val idxB = current.indexOf(sectionB)
        if (idxA == -1 || idxB == -1) return
        val updated = current.toMutableList().also {
            it[idxA] = sectionB
            it[idxB] = sectionA
        }
        _sectionOrder.value = updated
        prefs.edit { putString(Constants.KEY_SECTION_ORDER, updated.joinToString(",")) }
    }

    // Synchroner Read der pro Ordner gespeicherten Einklapp-Sections: analog sortSettingsFor,
    // von MainScreen genutzt, um die verschwindende Pane nach IHREM eigenen Ordner zu rendern.
    fun collapsedSectionsFor(folder: String?): Set<String> =
        prefs.getStringSet(collapsedSectionsKey(folder), emptySet()) ?: emptySet()

    /** Lädt den Einklappzustand des jetzt angezeigten Ordners. */
    fun loadCollapsedFor(folder: String?) {
        _collapsedSections.value = collapsedSectionsFor(folder)
    }

    fun toggleSectionCollapsed(section: String) {
        val updated = _collapsedSections.value.let { if (section in it) it - section else it + section }
        _collapsedSections.value = updated
        prefs.edit { putStringSet(collapsedSectionsKey(currentFolder()), updated) }
    }

    // 🆕 Per-Ordner-Einklappzustand: Root behält den alten globalen Key (Rückwärtskompatibilität),
    // andere Ordner bekommen einen eigenen Key-Namespace: analog zu sortOptionKey.
    private fun collapsedSectionsKey(folder: String?): String =
        if (folder == null) Constants.KEY_COLLAPSED_SECTIONS else "${Constants.KEY_COLLAPSED_SECTIONS}::$folder"

    private fun readSectionOrder(): List<String> {
        val raw = prefs.getString(Constants.KEY_SECTION_ORDER, null) ?: return DEFAULT_SECTION_ORDER
        val parsed = raw.split(",").filter { it in DEFAULT_SECTION_ORDER }.distinct()
        // Kaputter/unvollständiger Wert → sicherer Default statt Flickwerk.
        return if (parsed.size == DEFAULT_SECTION_ORDER.size) parsed else DEFAULT_SECTION_ORDER
    }

    private fun readDisplayMode(): String =
        prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE) ?: Constants.DEFAULT_DISPLAY_MODE

    private fun readGridAdaptiveScaling(): Boolean =
        prefs.getBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, Constants.DEFAULT_GRID_ADAPTIVE_SCALING)

    private fun readGridManualColumns(): Int =
        prefs.getInt(Constants.KEY_GRID_MANUAL_COLUMNS, Constants.DEFAULT_GRID_MANUAL_COLUMNS)

    private fun readShowNoteTimestamp(): Boolean =
        prefs.getBoolean(Constants.KEY_SHOW_NOTE_TIMESTAMP, Constants.DEFAULT_SHOW_NOTE_TIMESTAMP)

    private fun readShowNoteTypeIcon(): Boolean =
        prefs.getBoolean(Constants.KEY_SHOW_NOTE_TYPE_ICON, Constants.DEFAULT_SHOW_NOTE_TYPE_ICON)

    private fun readHideCheckedInPreview(): Boolean =
        prefs.getBoolean(Constants.KEY_HIDE_CHECKED_IN_PREVIEW, Constants.DEFAULT_HIDE_CHECKED_IN_PREVIEW)

    private fun readCustomAppTitle(): String =
        prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE)
            ?: Constants.DEFAULT_CUSTOM_APP_TITLE

    private companion object {
        val DEFAULT_SECTION_ORDER = listOf(SECTION_PINNED, SECTION_FOLDERS, SECTION_NOTES)
    }
}
