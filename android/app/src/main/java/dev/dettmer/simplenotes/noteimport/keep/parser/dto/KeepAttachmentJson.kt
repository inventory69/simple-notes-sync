package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

internal data class KeepAttachmentJson(
    @SerializedName("filePath") val filePath: String? = null,
    @SerializedName("mimetype") val mimetype: String? = null
)
