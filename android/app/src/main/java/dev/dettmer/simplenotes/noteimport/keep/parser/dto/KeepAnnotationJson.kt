package dev.dettmer.simplenotes.noteimport.keep.parser.dto

import com.google.gson.annotations.SerializedName

internal data class KeepAnnotationJson(
    @SerializedName("source") val source: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("title") val title: String? = null
)
