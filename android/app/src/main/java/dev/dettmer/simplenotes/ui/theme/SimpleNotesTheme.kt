package dev.dettmer.simplenotes.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.dettmer.simplenotes.security.AppLock
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.ExternalUriHandler

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
private fun scaleTypography(multiplier: Float, base: Typography = Typography()): Typography {
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
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * multiplier)
    )
}

/**
 * UI-Beta (F2): engere Laufweite kleiner Texte, kräftigere Titel. Roboto bleibt; nur Gewicht und
 * Laufweite ändern sich, die Größen bleiben die von Material 3 (und skalieren wie bisher).
 */
private fun newDesignTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Medium),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.3.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.15.sp),
        bodySmall = base.bodySmall.copy(letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.3.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.3.sp)
    )
}

/**
 * UI-Beta (F1): drei benannte Rundungsstufen statt einzelner Werte im Code.
 * small = Chip/Feld, medium = Karte, extraLarge = Dialog/Sheet. Die übrigen Stufen
 * werden mitgezogen, damit Material-Komponenten mit Default-Shape stimmig bleiben.
 */
private val NewDesignShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Liest den Beta-Schalter und folgt Änderungen live (Einstellungen laufen in einer anderen Activity). */
@Composable
private fun rememberNewDesign(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(ThemePreferences.isNewDesign(prefs)) }
    DisposableEffect(prefs) {
        // Starke Referenz nötig: SharedPreferences hält Listener nur schwach.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == Constants.KEY_NEW_DESIGN) enabled = ThemePreferences.isNewDesign(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("CyclomaticComplexMethod")
@Composable
fun SimpleNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    fontSizeScale: FontSizeScale = FontSizeScale.SYSTEM,
    content: @Composable () -> Unit
) {
    val fontMultiplier = fontSizeScale.multiplier
    val newDesign = rememberNewDesign()
    val typography = remember(fontSizeScale, newDesign) {
        val base = if (newDesign) newDesignTypography() else Typography()
        if (fontMultiplier == null) base else scaleTypography(fontMultiplier, base)
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
    // Skip while app-locked: a recomposition landing after AppLock.applySecureFlag()
    // has set the neutral Recents-placeholder color would otherwise stomp it back to
    // the isDark-derived color before the window is fully backgrounded.
    val locked by AppLock.locked.collectAsState()
    val view = LocalView.current
    if (!view.isInEditMode && !locked) {
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

    // ExternalUriHandler statt Composes Default: Links müssen in einem eigenen Task landen,
    // sonst bleibt die fremde App auf unserem Task-Stack liegen (siehe ExternalUriHandler).
    val uriHandler = remember(view.context) { ExternalUriHandler(view.context) }

    CompositionLocalProvider(
        LocalFontSizeMultiplier provides (fontMultiplier ?: 1.0f),
        LocalNewDesign provides newDesign,
        LocalUriHandler provides uriHandler
    ) {
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
                shapes = if (newDesign) NewDesignShapes else Shapes(),
                content = content
            )
        }
    }
}
