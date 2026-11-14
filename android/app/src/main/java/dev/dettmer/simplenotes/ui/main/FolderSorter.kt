package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette

/**
 * Sortiert die Ordner-Section der Root-Ansicht nach derselben Einstellung wie die Notizen.
 *
 * Ordner tragen weder Zeitstempel noch Notiz-Typ, deshalb fallen UPDATED_AT/CREATED_AT/NOTE_TYPE
 * auf den Namen zurück (wie Dateimanager es halten). Die Richtung gilt trotzdem, damit der
 * Richtungs-Toggle die Ordner sichtbar mitdreht statt sie als einzigen Block stehen zu lassen.
 * Ausnahme Farbe: dort gilt immer die Palettenfolge.
 */
fun sortFolders(folders: List<Folder>, option: SortOption, direction: SortDirection): List<Folder> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER, Folder::name)
    val comparator = when (option) {
        SortOption.COLOR -> {
            val hexToIndex = NoteColorPalette.slots.withIndex().associate { (i, slot) -> slot.hex to i }
            compareBy<Folder> { hexToIndex[it.color] ?: Int.MAX_VALUE }.then(byName)
        }
        else -> byName
    }
    // Farbe hat keine Richtung: immer Palettenfolge, Ordner ohne Farbe am Ende (wie sortNotes).
    return when {
        option == SortOption.COLOR || direction == SortDirection.ASCENDING -> folders.sortedWith(comparator)
        else -> folders.sortedWith(comparator.reversed())
    }
}
