package dev.dettmer.simplenotes.noteimport.keep

import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictStrategy

/**
 * v2.5.0 — User-Auswahl aus dem Configuring-Dialog (§4.2 [3]).
 */
data class KeepImportOptions(
    val includeArchived: Boolean = false,
    val includeTrashed: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.ALWAYS_CREATE,
)
