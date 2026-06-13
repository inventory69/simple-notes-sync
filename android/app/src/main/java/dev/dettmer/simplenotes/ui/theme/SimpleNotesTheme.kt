package dev.dettmer.simplenotes.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
private fun scaleTypography(multiplier: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * multiplier),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * multiplier),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * multiplier),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * multiplier),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * multiplier),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * multiplier),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * multiplier),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * multiplier),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * multiplier),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * multiplier),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * multiplier),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * multiplier),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * multiplier),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * multiplier),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * multiplier),
    )
}

@Composable
fun SimpleNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    fontSizeScale: FontSizeScale = FontSizeScale.SYSTEM,
    content: @Composable () -> Unit
) {
    val fontMultiplier = fontSizeScale.multiplier
    val typography = remember(fontSizeScale) {
        if (fontMultiplier == null) Typography() else scaleTypography(fontMultiplier)
    }
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
            // Bugfix (API 26–28): On API < Q, WindowInsetsController does not reliably
            // update the navigation bar background. enableEdgeToEdge() sets a transparent
            // scrim but only enforces auto-contrast from API 29 onwards, and only for the
            // initial call — not for runtime theme switches. We therefore also set
            // window.navigationBarColor explicitly and toggle
            // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR directly on the decorView when running
            // below Android Q. SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR requires API 26 (O),
            // so the lower bound is O; API ≤ 25 behavior is undefined and not covered.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            ) {
                @Suppress("DEPRECATION")
                window.navigationBarColor =
                    if (isDark) AndroidColor.BLACK else AndroidColor.WHITE
                @Suppress("DEPRECATION")
                val flags = window.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (isDark) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        }
    }

    // v2.1.0: Crossfade cross-dissolves the old UI into the new themed UI.
    // One graphicsLayer.alpha animates per frame (GPU compositing, no recomposition
    // per color) — smooth even in debug builds.
    // IMPORTANT: NavController and other persistent state must be created ABOVE
    // this composable (i.e. before SimpleNotesTheme is called) so they survive
    // the composition recreation that Crossfade triggers on key change.
    CompositionLocalProvider(LocalFontSizeMultiplier provides (fontMultiplier ?: 1.0f)) {
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
                typography = typography,
                content = content
            )
        }
    }
}
