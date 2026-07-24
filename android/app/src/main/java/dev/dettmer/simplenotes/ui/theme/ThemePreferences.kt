package dev.dettmer.simplenotes.ui.theme

import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.toEnumOrDefault

/**
 * FontSizeScale — app-level text size override applied on top of the M3 typography.
 * [multiplier] null means "use system default" (no override); non-null values scale
 * all M3 TextStyle font sizes by the given factor.
 */
enum class FontSizeScale(val displayNameResId: Int, val multiplier: Float?) {
    SYSTEM(R.string.font_size_system, null),
    SMALL(R.string.font_size_small, 0.85f),
    NORMAL(R.string.font_size_normal, 1.0f),
    LARGE(R.string.font_size_large, 1.15f),
    XLARGE(R.string.font_size_xlarge, 1.3f)
}

/** Provides the effective font size multiplier (1.0f when SYSTEM is selected). */
val LocalFontSizeMultiplier = staticCompositionLocalOf { 1.0f }

/**
 * ThemeMode — controls dark/light behaviour of the app.
 * v2.0.0: Multi-theme system
 *
 * [displayNameResId] is used in the Settings UI chip label.
 * [previewColor] is shown as the surface-color swatch in the chip.
 */
@Suppress("MagicNumber") // Color hex values for preview swatches
enum class ThemeMode(val displayNameResId: Int, val previewColor: Color) {
    SYSTEM(R.string.theme_mode_system, Color(0xFF808080)),
    LIGHT(R.string.theme_mode_light, Color(0xFFFFFBFE)),
    DARK(R.string.theme_mode_dark, Color(0xFF1C1B1F)),
    AMOLED(R.string.theme_mode_amoled, Color.Black)
}

/**
 * ColorTheme — selects the active color palette.
 *
 * [displayNameResId] is used in the Settings UI chip label.
 * [previewColor] is shown as the color swatch in the chip.
 * v2.0.0: Multi-theme system
 */
enum class ColorTheme(val displayNameResId: Int, val previewColor: Color) {
    DEFAULT(R.string.theme_color_default, Color(0xFF6750A4)),
    BLUE(R.string.theme_color_blue, Color(0xFF0062A1)),
    GREEN(R.string.theme_color_green, Color(0xFF276A25)),
    RED(R.string.theme_color_red, Color(0xFFB3261E)),
    PURPLE(R.string.theme_color_purple, Color(0xFF7B1FA2)),
    ORANGE(R.string.theme_color_orange, Color(0xFF8B4500)),
    DYNAMIC(R.string.theme_color_dynamic, Color(0xFF6750A4))
}

/**
 * NotePreviewLength — v2.11.0: presets controlling how many preview lines/items a note card
 * shows in List and Grid view. [gridSmallLines]/[gridLargeLines] map to the SMALL/LARGE tiers
 * of [dev.dettmer.simplenotes.models.NoteSize]. STANDARD matches the previous hardcoded
 * behaviour (List = 3, Grid SMALL = 3 / LARGE = 6) so existing users see no change by default.
 *
 * 🆕 Discussion #110: TITLE_ONLY = 0 Vorschauzeilen — die Karten zeigen nur noch den Titel.
 * Bei titellosen Notizen bleibt die erste Inhaltszeile im Titel-Slot stehen (sonst wäre die
 * Karte leer), siehe NoteCard/NoteCardGrid.
 */
enum class NotePreviewLength(
    val displayNameResId: Int,
    val listLines: Int,
    val gridSmallLines: Int,
    val gridLargeLines: Int,
    val itemMaxLines: Int
) {
    TITLE_ONLY(R.string.note_preview_length_title_only, 0, 0, 0, 1),
    COMPACT(R.string.note_preview_length_compact, 2, 2, 4, 1),
    STANDARD(R.string.note_preview_length_standard, 3, 3, 6, 1),
    LONG(R.string.note_preview_length_long, 5, 4, 8, 2),
    EXTRA_LONG(R.string.note_preview_length_extra_long, 8, 6, 10, 2)
}

/**
 * ThemePreferences — reads and writes theme settings to SharedPreferences.
 * Matches the same [Constants.PREFS_NAME] prefs file used by all other settings.
 * v2.0.0: Multi-theme system
 */
object ThemePreferences {
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val KEY_FONT_SIZE_SCALE = "font_size_scale"
    private const val KEY_NOTE_PREVIEW_LENGTH = "note_preview_length"

    fun getThemeMode(prefs: SharedPreferences): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return stored.toEnumOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(prefs: SharedPreferences, mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun getColorTheme(prefs: SharedPreferences): ColorTheme {
        val stored = prefs.getString(KEY_COLOR_THEME, ColorTheme.DYNAMIC.name)
        return stored.toEnumOrDefault(ColorTheme.DYNAMIC)
    }

    fun setColorTheme(prefs: SharedPreferences, theme: ColorTheme) {
        prefs.edit { putString(KEY_COLOR_THEME, theme.name) }
    }

    fun getFontSizeScale(prefs: SharedPreferences): FontSizeScale {
        val stored = prefs.getString(KEY_FONT_SIZE_SCALE, FontSizeScale.SYSTEM.name)
        return stored.toEnumOrDefault(FontSizeScale.SYSTEM)
    }

    fun setFontSizeScale(prefs: SharedPreferences, scale: FontSizeScale) {
        prefs.edit { putString(KEY_FONT_SIZE_SCALE, scale.name) }
    }

    fun getNotePreviewLength(prefs: SharedPreferences): NotePreviewLength {
        val stored = prefs.getString(KEY_NOTE_PREVIEW_LENGTH, NotePreviewLength.STANDARD.name)
        return stored.toEnumOrDefault(NotePreviewLength.STANDARD)
    }

    fun setNotePreviewLength(prefs: SharedPreferences, length: NotePreviewLength) {
        prefs.edit { putString(KEY_NOTE_PREVIEW_LENGTH, length.name) }
    }
}
