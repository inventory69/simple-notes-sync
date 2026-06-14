package dev.dettmer.simplenotes.noteimport.keep.persistence

import com.google.gson.Gson
import dev.dettmer.simplenotes.utils.Logger

/**
 * v2.5.0 — Reverse-Index `label → Set<noteId>` für späteres Tag-UI.
 *
 * Persistenz: `context.filesDir/notes_labels.json` (Gson). Mutationen
 * dürfen **nur** über [LabelStore] geschehen (Mutex-geschützt).
 *
 * Daten-Struktur ist bewusst ein `LinkedHashMap`/`LinkedHashSet`-äquivalent
 * (Insertion-Order erhalten), damit das spätere Tag-UI eine stabile
 * Default-Sortierung anzeigen kann.
 */
data class LabelIndex(
    val labels: MutableMap<String, MutableSet<String>> = LinkedHashMap()
) {
    fun add(label: String, noteId: String) {
        labels.getOrPut(label) { LinkedHashSet() }.add(noteId)
    }

    fun toJson(): String = Gson().toJson(this)

    /** Anzahl distinkter Labels (für Summary-Counter). */
    fun size(): Int = labels.size

    companion object {
        private const val TAG = "LabelIndex"

        fun fromJson(s: String): LabelIndex? = try {
            // Gson deserialisiert MutableMap/MutableSet als LinkedHashMap/LinkedHashSet.
            Gson().fromJson(s, LabelIndex::class.java)
        } catch (e: Exception) {
            Logger.w(TAG, "Label index corrupt: ${e.message}")
            null
        }
    }
}
