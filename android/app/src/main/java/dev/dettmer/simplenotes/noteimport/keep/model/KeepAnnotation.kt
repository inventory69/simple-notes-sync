package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Eine Annotation aus dem Keep-JSON (i.d.R. Web-Clip).
 *
 * v2.5.0: nur `WEBLINK`-Annotationen werden ausgewertet — der Mapper hängt
 * die URL als zusätzliche Zeile an `Note.content` (Analyseplan §2.3).
 *
 * @property source Quell-Typ aus Keep, z.B. `"WEBLINK"`.
 * @property url Optional. Bei Web-Clips meist gesetzt.
 * @property title Optional. Anzeigetitel des Web-Clips.
 */
data class KeepAnnotation(
    val source: String,
    val url: String?,
    val title: String?
)
