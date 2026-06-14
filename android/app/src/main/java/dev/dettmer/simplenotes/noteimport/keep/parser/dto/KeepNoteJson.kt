package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

/**
 * v2.5.0 — Top-Level-DTO für eine Keep-Takeout-JSON-Datei.
 *
 * Alle Felder sind nullable; Default-Werte werden im Parser angewandt.
 * Reflection-basiert deserialisiert (Gson) — siehe `proguard-rules.pro`
 * Block "Keep-Import DTOs".
 */
internal data class KeepNoteJson(
    @SerializedName("title") val title: String? = null,
    @SerializedName("textContent") val textContent: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName("isArchived") val isArchived: Boolean? = null,
    @SerializedName("isTrashed") val isTrashed: Boolean? = null,
    @SerializedName("createdTimestampUsec") val createdTimestampUsec: Long? = null,
    @SerializedName("userEditedTimestampUsec") val userEditedTimestampUsec: Long? = null,
    @SerializedName("labels") val labels: List<KeepLabelJson>? = null,
    @SerializedName("listContent") val listContent: List<KeepListItemJson>? = null,
    @SerializedName("attachments") val attachments: List<KeepAttachmentJson>? = null,
    @SerializedName("annotations") val annotations: List<KeepAnnotationJson>? = null,
    @SerializedName("sharees") val sharees: List<KeepShareeJson>? = null
)
