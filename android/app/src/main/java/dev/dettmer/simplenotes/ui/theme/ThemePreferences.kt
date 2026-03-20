package dev.dettmer.simplenotes.ui.theme

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import dev.dettmer.simplenotes.R

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
 * ThemePreferences — reads and writes theme settings to SharedPreferences.
 * Matches the same [Constants.PREFS_NAME] prefs file used by all other settings.
 * v2.0.0: Multi-theme system
 */
object ThemePreferences {
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_THEME = "color_theme"

    fun getThemeMode(prefs: SharedPreferences): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(stored!!)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(prefs: SharedPreferences, mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun getColorTheme(prefs: SharedPreferences): ColorTheme {
        val stored = prefs.getString(KEY_COLOR_THEME, ColorTheme.DYNAMIC.name)
        return try {
            ColorTheme.valueOf(stored!!)
        } catch (_: Exception) {
            ColorTheme.DYNAMIC
        }
    }

    fun setColorTheme(prefs: SharedPreferences, theme: ColorTheme) {
        prefs.edit { putString(KEY_COLOR_THEME, theme.name) }
    }
}
