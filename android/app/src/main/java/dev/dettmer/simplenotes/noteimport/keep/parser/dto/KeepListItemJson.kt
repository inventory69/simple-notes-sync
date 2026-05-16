package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

internal data class KeepListItemJson(
    @SerializedName("text") val text: String? = null,
    @SerializedName("isChecked") val isChecked: Boolean? = null,
)
