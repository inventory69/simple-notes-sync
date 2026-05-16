package dev.dettmer.simplenotes.noteimport.keep

/**
 * v2.5.0 — Fortschritts-Snapshot, vom Use-Case via `onProgress`-Callback emittiert.
 */
data class KeepImportProgress(
    val processed: Int,
    val total: Int,
    val currentName: String,
)
