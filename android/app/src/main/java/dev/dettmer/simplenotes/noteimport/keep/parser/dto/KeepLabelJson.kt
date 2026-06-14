package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

internal data class KeepLabelJson(
    @SerializedName("name") val name: String? = null
)
