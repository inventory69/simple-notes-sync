package dev.dettmer.simplenotes.ui.theme

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Shared Material 3 theme.
 *
 * v1.5.0: Unified theme for all Compose Activities.
 * v2.0.0: Multi-theme support — [ThemeMode] controls dark/light/AMOLED,
 *         [ColorTheme] selects the color palette (6 static + Dynamic Color).
 * v2.1.0: Smooth theme transitions via [Crossfade] — one graphicsLayer.alpha
 *         per frame instead of 35 per-color State invalidations, keeping
 *         animation smooth even in debug builds.
 *         NavController must be created ABOVE SimpleNotesTheme in the
 *         caller's composition so it survives Crossfade key changes
 *         (see ComposeSettingsActivity).
 *         Status bar icon appearance adapts to dark/light mode at runtime.
 *
 * Used by:
 * - ComposeMainActivity (Notes list)
 * - ComposeSettingsActivity (Settings screens)
 * - ComposeNoteEditorActivity (Note editor)
 * - NoteWidgetConfigActivity (Widget configuration)
 */
@Composable
fun SimpleNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
    }

    // v2.1.0: Keep status bar and navigation bar icon appearance in sync with the
    // runtime theme. enableEdgeToEdge() runs only once in onCreate, so it cannot
    // react to theme changes made by the user at runtime.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    // v2.1.0: Crossfade cross-dissolves the old UI into the new themed UI.
    // One graphicsLayer.alpha animates per frame (GPU compositing, no recomposition
    // per color) — smooth even in debug builds.
    // IMPORTANT: NavController and other persistent state must be created ABOVE
    // this composable (i.e. before SimpleNotesTheme is called) so they survive
    // the composition recreation that Crossfade triggers on key change.
    Crossfade(
        targetState = themeMode to colorTheme,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "ThemeCrossfade"
    ) { (mode, palette) ->
        val context = LocalContext.current
        val dark = when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AMOLED -> true
        }
        val colorScheme = ColorPalettes.getColorScheme(
            colorTheme = palette,
            isDark = dark,
            isAmoled = mode == ThemeMode.AMOLED,
            context = context
        )
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
