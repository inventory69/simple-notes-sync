package dev.dettmer.simplenotes.markdown

/**
 * Bild-Attachments v2: Größe/Ausrichtung leben als Pipe-Tokens im Alt-Text
 * (Obsidian-Style), z.B. `![Sonnenuntergang|50%|right](.assets/x.webp)`.
 * Damit bleibt der Alt-Text `[^\]]*` in allen Asset-Regexes — null Regex-Änderung
 * für GC/Sync/Share/Extraktion, jeder CommonMark-Renderer rendert das Bild weiter.
 */
enum class ImageAlign { LEFT, CENTER, RIGHT, INLINE }

data class ImageAltInfo(val cleanAlt: String, val sizePercent: Int, val align: ImageAlign)

private const val DEFAULT_SIZE_PERCENT = 50
private val DEFAULT_ALIGN = ImageAlign.CENTER
private const val MIN_SIZE_PERCENT = 1
private const val MAX_SIZE_PERCENT = 100
private val SIZE_TOKEN_REGEX = Regex("""^\d{1,3}%$""")

/** Alt an `|` splitten; Größe/Ausrichtung-Segmente rausziehen (last wins), Rest bleibt Clean-Alt. */
fun parseImageAlt(rawAlt: String): ImageAltInfo {
    if (rawAlt.isEmpty()) return ImageAltInfo("", DEFAULT_SIZE_PERCENT, DEFAULT_ALIGN)

    var sizePercent = DEFAULT_SIZE_PERCENT
    var align = DEFAULT_ALIGN
    val cleanSegments = mutableListOf<String>()

    for (segment in rawAlt.split("|")) {
        val alignValue = ImageAlign.entries.firstOrNull { it.name.equals(segment, ignoreCase = true) }
        when {
            SIZE_TOKEN_REGEX.matches(segment) ->
                sizePercent = segment.dropLast(1).toInt().coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT)
            alignValue != null -> align = alignValue
            else -> cleanSegments.add(segment)
        }
    }

    return ImageAltInfo(cleanSegments.joinToString("|"), sizePercent, align)
}

/** Inverse von [parseImageAlt]. Lässt Default-Tokens (100%, center) weg. */
fun buildImageAlt(cleanAlt: String, sizePercent: Int, align: ImageAlign): String {
    val tokens = buildList {
        if (sizePercent != DEFAULT_SIZE_PERCENT) add("$sizePercent%")
        if (align != DEFAULT_ALIGN) add(align.name.lowercase())
    }
    return if (tokens.isEmpty()) cleanAlt else (listOf(cleanAlt) + tokens).joinToString("|")
}

/**
 * Berechnet den Text-Replace für den [ordinal]-ten Bild-Link (Index in
 * `IMAGE_REGEX.findAll(text)` über den Gesamttext) auf neue Größe/Ausrichtung.
 * Asset-Name-Mismatch oder Ordinal außerhalb des Bereichs → `null` (stiller No-op
 * statt eines falschen Links, z.B. wenn der Text sich zwischen Öffnen des
 * Long-Press-Menüs und Auswahl geändert hat).
 */
fun computeImageRewrite(
    text: String,
    ordinal: Int,
    assetName: String,
    sizePercent: Int,
    align: ImageAlign,
    cleanAlt: String? = null
): Pair<IntRange, String>? {
    val match = MarkdownEngine.IMAGE_REGEX.findAll(text).elementAtOrNull(ordinal) ?: return null
    if (match.groupValues[2] != assetName) return null

    val resolvedCleanAlt = cleanAlt ?: parseImageAlt(match.groupValues[1]).cleanAlt
    val newAlt = buildImageAlt(resolvedCleanAlt, sizePercent, align)
    return match.range to "![$newAlt](.assets/$assetName)"
}
