package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Ein Keep-Label (= zukünftiger App-Tag).
 *
 * v2.5.0: wird in [dev.dettmer.simplenotes.models.Note.labels] als String-Liste
 * persistiert und zusätzlich im LabelStore (Commit #9) aggregiert.
 */
data class KeepLabel(val name: String)
