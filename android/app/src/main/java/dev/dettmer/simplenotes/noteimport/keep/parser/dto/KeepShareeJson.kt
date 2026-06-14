package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

/**
 * v2.5.0 — Kollaborator einer geteilten Keep-Notiz. Wird in v2.5.0
 * **nicht** importiert; nur als Counter in [KeepImportSummary]
 * (`sharedNotesImported`) verwendet.
 */
internal data class KeepShareeJson(
    @SerializedName("email") val email: String? = null
)
