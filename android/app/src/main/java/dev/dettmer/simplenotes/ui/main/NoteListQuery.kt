package dev.dettmer.simplenotes.ui.main

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sortierung, Filter und Suche der Hauptliste. Sortierung gilt je Ordner ([currentFolder]), Typ-
 * und Farbfilter global, Archiv-Ansicht und Suchbegriff nur für die Sitzung.
 *
 * Die eigentliche Pipeline (filtern, suchen, sortieren) sind die freien Funktionen unten; der
 * ViewModel kombiniert sie mit der Notizliste.
 */
class NoteListQuery(
    private val prefs: SharedPreferences,
    private val currentFolder: () -> String?
) {
    private val _sortOption = MutableStateFlow(sortSettingsFor(null).first)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(sortSettingsFor(null).second)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    // 🆕 v1.9.0 (F06)
    private val _noteFilter = MutableStateFlow(
        NoteFilter.fromPrefsValue(
            prefs.getString(Constants.KEY_NOTE_FILTER, Constants.DEFAULT_NOTE_FILTER) ?: Constants.DEFAULT_NOTE_FILTER
        )
    )
    val noteFilter: StateFlow<NoteFilter> = _noteFilter.asStateFlow()

    // 🆕 v2.5.0: null = kein Filter, "#RRGGBB" = aktiver Farbfilter
    private val _colorFilter = MutableStateFlow(
        prefs.getString(Constants.KEY_COLOR_FILTER, Constants.DEFAULT_COLOR_FILTER)?.takeIf { it.isNotEmpty() }
    )
    val colorFilter: StateFlow<String?> = _colorFilter.asStateFlow()

    // 🆕 v2.11.0 (Archive): Archiv-Chip, nicht persistiert
    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    // 🆕 v1.9.0 (F10): session-only
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Synchroner Read für MainScreen: die *abgehende* Pane sortiert beim Ordnerwechsel nach
    // IHREM Ordner, unabhängig von sortOption/sortDirection des aktiven Ordners.
    fun sortSettingsFor(folder: String?): Pair<SortOption, SortDirection> =
        SortOption.fromPrefsValue(
            prefs.getString(sortOptionKey(folder), Constants.DEFAULT_SORT_OPTION) ?: Constants.DEFAULT_SORT_OPTION
        ) to SortDirection.fromPrefsValue(
            prefs.getString(sortDirectionKey(folder), Constants.DEFAULT_SORT_DIRECTION)
                ?: Constants.DEFAULT_SORT_DIRECTION
        )

    /** Lädt die Sortierung des jetzt angezeigten Ordners. */
    fun loadSortFor(folder: String?) {
        val (option, direction) = sortSettingsFor(folder)
        _sortOption.value = option
        _sortDirection.value = direction
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        prefs.edit { putString(sortOptionKey(currentFolder()), option.prefsValue) }
        Logger.d(TAG, "🔀 Sort option changed to: ${option.prefsValue} (folder=${currentFolder()})")
    }

    fun setSortDirection(direction: SortDirection) {
        _sortDirection.value = direction
        prefs.edit { putString(sortDirectionKey(currentFolder()), direction.prefsValue) }
        Logger.d(TAG, "🔀 Sort direction changed to: ${direction.prefsValue} (folder=${currentFolder()})")
    }

    fun toggleSortDirection() = setSortDirection(_sortDirection.value.toggle())

    /** Setzt die Sortierung des AKTUELLEN Ordners auf die App-Defaults zurück. */
    fun resetSortToDefault() {
        setSortOption(SortOption.fromPrefsValue(Constants.DEFAULT_SORT_OPTION))
        setSortDirection(SortDirection.fromPrefsValue(Constants.DEFAULT_SORT_DIRECTION))
    }

    fun setNoteFilter(filter: NoteFilter) {
        _noteFilter.value = filter
        prefs.edit { putString(Constants.KEY_NOTE_FILTER, filter.prefsValue) }
        Logger.d(TAG, "🔍 Note filter changed to: ${filter.prefsValue}")
    }

    /** @param hex Hex-String der Farbe (z.B. "#F28B82") oder null zum Aufheben. */
    fun setColorFilter(hex: String?) {
        _colorFilter.value = hex
        prefs.edit { putString(Constants.KEY_COLOR_FILTER, hex ?: "") }
        Logger.d(TAG, "🎨 Color filter changed to: ${hex ?: "none"}")
    }

    fun setShowArchived(show: Boolean) {
        _showArchived.value = show
        Logger.d(TAG, "🗃️ Archive view: $show")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Logger.d(TAG, "🔎 Search query changed to: \"$query\"")
    }

    // 🆕 Per-Ordner-Sortierung: Root behält die alten globalen Keys (Rückwärtskompatibilität),
    // andere Ordner bekommen einen eigenen Key-Namespace.
    private fun sortOptionKey(folder: String?): String =
        if (folder == null) Constants.KEY_SORT_OPTION else "${Constants.KEY_SORT_OPTION}::$folder"

    private fun sortDirectionKey(folder: String?): String =
        if (folder == null) Constants.KEY_SORT_DIRECTION else "${Constants.KEY_SORT_DIRECTION}::$folder"

    private companion object {
        const val TAG = "NoteListQuery"
    }
}

/**
 * Filtert nach Archiv-Zustand, Notiztyp und optional Farbe (UND-Verknüpfung). Kein Ordner-Filter:
 * den wendet jede Pane selbst an (sonst Flackern beim Ordnerwechsel, siehe MainScreen).
 */
fun filterNotes(
    notes: List<Note>,
    filter: NoteFilter,
    colorFilter: String? = null,
    showArchived: Boolean = false
): List<Note> {
    val byArchive = notes.filter { it.isArchived == showArchived }
    val byType = when (filter) {
        NoteFilter.ALL -> byArchive
        NoteFilter.TEXT_ONLY -> byArchive.filter { it.noteType == NoteType.TEXT }
        NoteFilter.CHECKLIST_ONLY -> byArchive.filter { it.noteType == NoteType.CHECKLIST }
    }
    return if (colorFilter != null) byType.filter { it.color == colorFilter } else byType
}

/** Volltextsuche über Titel, Inhalt und Checklisten-Einträge. Leerer Begriff → unverändert. */
fun searchNotes(notes: List<Note>, query: String): List<Note> {
    if (query.isBlank()) return notes
    val lowerQuery = query.trim().lowercase()
    return notes.filter { note ->
        note.title.lowercase().contains(lowerQuery) ||
            note.content.lowercase().contains(lowerQuery) ||
            note.checklistItems?.any { it.text.lowercase().contains(lowerQuery) } == true
    }
}

fun sortNotes(notes: List<Note>, option: SortOption, direction: SortDirection): List<Note> {
    val comparator: Comparator<Note> = when (option) {
        SortOption.UPDATED_AT -> compareBy { it.editedAt }
        SortOption.CREATED_AT -> compareBy { it.createdAt }
        SortOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortOption.NOTE_TYPE -> compareBy<Note> { it.noteType.ordinal }
            .thenByDescending { it.editedAt } // Sekundär: Datum innerhalb gleicher Typen
        SortOption.COLOR -> {
            // 🔧 Perf: Index-Map einmal vorab bauen statt pro Notiz die Palette zu durchsuchen
            val hexToIndex = NoteColorPalette.slots.withIndex().associate { (i, slot) -> slot.hex to i }
            compareBy<Note> { note -> hexToIndex[note.color] ?: Int.MAX_VALUE }
                .thenByDescending { it.editedAt } // Sekundär: Datum innerhalb gleicher Farbe
        }
    }

    // Farbe hat keine Richtung: immer Palettenfolge (Rot … Grau), Notizen ohne Farbe am Ende (#131).
    return when {
        option == SortOption.COLOR || direction == SortDirection.ASCENDING -> notes.sortedWith(comparator)
        else -> notes.sortedWith(comparator.reversed())
    }
}
