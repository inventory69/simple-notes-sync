package dev.dettmer.simplenotes.utils

import dev.dettmer.simplenotes.models.Note

/**
 * Extrahiert `.assets/<name>`-Referenzen aus Notiz-Content (Bild-Attachments).
 * Pure Funktionen — kein I/O, dient als Basis für GC (Mark-and-Sweep) und Sync-Diffs.
 */
object AssetReferences {
    private val ASSET_LINK_REGEX = Regex("""!\[[^\]]*]\(\.assets/([A-Za-z0-9][A-Za-z0-9._-]*)\)""")

    fun extractAssetNames(content: String): Set<String> =
        ASSET_LINK_REGEX.findAll(content).map { it.groupValues[1] }.toSet()

    /** Über alle Notizen inkl. Trash + Archiv — GC darf referenzierte Assets nie fälschlich löschen. */
    fun extractAllReferenced(notes: List<Note>): Set<String> =
        notes.flatMapTo(mutableSetOf()) { extractAssetNames(it.content) }
}
