package dev.dettmer.simplenotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Canonical note-colour palette derived from the 11 Google Keep colour slots.
 *
 * Each [NoteColorSlot] bundles the canonical hex (as persisted in
 * [dev.dettmer.simplenotes.models.Note.color]), a light-mode container colour,
 * and a pre-computed dark-mode container colour so the card renderer can choose
 * the correct variant without allocating new Color instances on recomposition.
 *
 * The canonical hex is always the Keep-derived light value.  Dark variants are
 * purpose-designed darker/more-muted tones that remain identifiable under a
 * dark surface.
 *
 * v2.5.0 (Issue #65)
 */
data class NoteColorSlot(
    /** Canonical `#RRGGBB` hex string; matches values in [KeepColor][dev.dettmer.simplenotes.noteimport.keep.model.KeepColor]. */
    val hex: String,
    /** Card container colour for light mode. */
    val containerColor: Color,
    /** Card container colour for dark / AMOLED mode. */
    val containerColorDark: Color
)

@Suppress("MagicNumber") // Keep-derived palette — hex literals are design constants
object NoteColorPalette {
    /** All 11 available colour slots, in display order (matches Keep palette order). */
    val slots: List<NoteColorSlot> = listOf(
        NoteColorSlot("#F28B82", Color(0xFFF28B82), Color(0xFF7A3028)),
        NoteColorSlot("#FBBC04", Color(0xFFFBBC04), Color(0xFF6B4E00)),
        NoteColorSlot("#FFF475", Color(0xFFFFF475), Color(0xFF5C5200)),
        NoteColorSlot("#CCFF90", Color(0xFFCCFF90), Color(0xFF2D5A0F)),
        NoteColorSlot("#A7FFEB", Color(0xFFA7FFEB), Color(0xFF145C45)),
        NoteColorSlot("#CBF0F8", Color(0xFFCBF0F8), Color(0xFF104F5C)),
        NoteColorSlot("#AECBFA", Color(0xFFAECBFA), Color(0xFF0D3360)),
        NoteColorSlot("#D7AEFB", Color(0xFFD7AEFB), Color(0xFF3D1060)),
        NoteColorSlot("#FDCFE8", Color(0xFFFDCFE8), Color(0xFF6B1940)),
        NoteColorSlot("#E6C9A8", Color(0xFFE6C9A8), Color(0xFF4A2D10)),
        NoteColorSlot("#E8EAED", Color(0xFFE8EAED), Color(0xFF2E3135))
    )

    private val byHex: Map<String, NoteColorSlot> = slots.associateBy { it.hex }

    /**
     * Returns the [NoteColorSlot] for the given canonical hex string, or `null`
     * when [hex] is `null` / unknown (= no colour override).
     */
    fun fromHex(hex: String?): NoteColorSlot? = hex?.let { byHex[it] }

    /**
     * Resolves [Note.color][dev.dettmer.simplenotes.models.Note.color] to a card
     * container colour for the current dark-mode state.
     *
     * Returns [Color.Unspecified] when [hex] is `null` so callers can use
     * `takeOrElse { MaterialTheme.colorScheme.surface }` to fall back gracefully.
     */
    fun resolveContainer(hex: String?, isDark: Boolean): Color {
        val slot = fromHex(hex) ?: return Color.Unspecified
        return if (isDark) slot.containerColorDark else slot.containerColor
    }
}
