package dev.dettmer.simplenotes.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Static color-scheme instances — created once at class-load time.
// getColorScheme() always returns an existing reference → zero allocation on
// each recomposition. AMOLED variants are pre-computed via toAmoled().
// ─────────────────────────────────────────────────────────────────────────────

// ── Default (M3 baseline, seed #6750A4) ──────────────────────────────────────

private val DefaultLightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Color(0xFF6750A4)
)

private val DefaultDarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    inversePrimary = Color(0xFF6750A4),
    surfaceTint = Color(0xFFD0BCFF)
)

private val DefaultDarkAmoledColors = DefaultDarkColors.toAmoled()

// ── Blue (seed #1565C0) ───────────────────────────────────────────────────────

private val BlueLightColors = lightColorScheme(
    primary = Color(0xFF0062A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF261431),
    inversePrimary = Color(0xFF9ECAFF),
    surfaceTint = Color(0xFF0062A1),
    // Tonal surface palette (neutral hue ~210°, low chroma, M3-compliant tones)
    surface = Color(0xFFF8F9FF),
    background = Color(0xFFF8F9FF),
    surfaceDim = Color(0xFFD9DAE0),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEDF3),
    surfaceContainerHigh = Color(0xFFE6E8ED),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFFDCE4ED),
    onSurfaceVariant = Color(0xFF41484F)
)

private val BlueDarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004A86),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFD7BEE6),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF52405F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    inversePrimary = Color(0xFF0062A1),
    surfaceTint = Color(0xFF9ECAFF),
    // Tonal surface palette (neutral hue ~210°, low chroma, M3-compliant tones)
    surface = Color(0xFF111318),
    background = Color(0xFF111318),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393F),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF1D2128),
    surfaceContainerHigh = Color(0xFF282C32),
    surfaceContainerHighest = Color(0xFF32363D),
    surfaceVariant = Color(0xFF42474F),
    onSurfaceVariant = Color(0xFFC3C9D1)
)

private val BlueDarkAmoledColors = BlueDarkColors.toAmoled()

// ── Green (seed #2E7D32) ──────────────────────────────────────────────────────

private val GreenLightColors = lightColorScheme(
    primary = Color(0xFF276A25),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFABF6A4),
    onPrimaryContainer = Color(0xFF002203),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFF39656B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEBF2),
    onTertiaryContainer = Color(0xFF001F23),
    inversePrimary = Color(0xFF90DA8D),
    surfaceTint = Color(0xFF276A25),
    // Tonal surface palette (neutral hue ~125°, low chroma, M3-compliant tones)
    surface = Color(0xFFF7FAF3),
    background = Color(0xFFF7FAF3),
    surfaceDim = Color(0xFFD8DBD4),
    surfaceBright = Color(0xFFF7FAF3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4ED),
    surfaceContainer = Color(0xFFEBEEE8),
    surfaceContainerHigh = Color(0xFFE5E9E2),
    surfaceContainerHighest = Color(0xFFE0E3DC),
    surfaceVariant = Color(0xFFDAE5D7),
    onSurfaceVariant = Color(0xFF424B40)
)

private val GreenDarkColors = darkColorScheme(
    primary = Color(0xFF90DA8D),
    onPrimary = Color(0xFF003A07),
    primaryContainer = Color(0xFF0D5213),
    onPrimaryContainer = Color(0xFFABF6A4),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243422),
    secondaryContainer = Color(0xFF3A4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA1CFD6),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBDEBF2),
    inversePrimary = Color(0xFF276A25),
    surfaceTint = Color(0xFF90DA8D),
    // Tonal surface palette (neutral hue ~125°, low chroma, M3-compliant tones)
    surface = Color(0xFF0F1410),
    background = Color(0xFF0F1410),
    surfaceDim = Color(0xFF0F1410),
    surfaceBright = Color(0xFF343B34),
    surfaceContainerLowest = Color(0xFF0A0F0B),
    surfaceContainerLow = Color(0xFF171D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262C27),
    surfaceContainerHighest = Color(0xFF313632),
    surfaceVariant = Color(0xFF3D4939),
    onSurfaceVariant = Color(0xFFBEC9BB)
)

private val GreenDarkAmoledColors = GreenDarkColors.toAmoled()

// ── Red (seed #C62828) ────────────────────────────────────────────────────────

private val RedLightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775651),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF715C2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDDFA6),
    onTertiaryContainer = Color(0xFF261900),
    inversePrimary = Color(0xFFFFB4AB),
    surfaceTint = Color(0xFFB3261E),
    // Tonal surface palette (neutral hue ~5°, low chroma, M3-compliant tones)
    surface = Color(0xFFFFF8F7),
    background = Color(0xFFFFF8F7),
    surfaceDim = Color(0xFFE0D7D6),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EF),
    surfaceContainer = Color(0xFFFDE9E7),
    surfaceContainerHigh = Color(0xFFF7E3E1),
    surfaceContainerHighest = Color(0xFFF2DDD9),
    surfaceVariant = Color(0xFFEDE0DF),
    onSurfaceVariant = Color(0xFF4E4443)
)

private val RedDarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFE0C38C),
    onTertiary = Color(0xFF3F2E04),
    tertiaryContainer = Color(0xFF584419),
    onTertiaryContainer = Color(0xFFFDDFA6),
    inversePrimary = Color(0xFFB3261E),
    surfaceTint = Color(0xFFFFB4AB),
    // Tonal surface palette (neutral hue ~5°, low chroma, M3-compliant tones)
    surface = Color(0xFF191212),
    background = Color(0xFF191212),
    surfaceDim = Color(0xFF191212),
    surfaceBright = Color(0xFF3D3130),
    surfaceContainerLowest = Color(0xFF130C0C),
    surfaceContainerLow = Color(0xFF211919),
    surfaceContainer = Color(0xFF261E1E),
    surfaceContainerHigh = Color(0xFF302828),
    surfaceContainerHighest = Color(0xFF3C3332),
    surfaceVariant = Color(0xFF4E4443),
    onSurfaceVariant = Color(0xFFD2C4C3)
)

private val RedDarkAmoledColors = RedDarkColors.toAmoled()

// ── Purple (seed #7B1FA2) ─────────────────────────────────────────────────────

private val PurpleLightColors = lightColorScheme(
    primary = Color(0xFF7B1FA2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DBFF),
    onPrimaryContainer = Color(0xFF2B0045),
    secondary = Color(0xFF6B587A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DBFF),
    onSecondaryContainer = Color(0xFF251535),
    tertiary = Color(0xFF81525B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF321019),
    inversePrimary = Color(0xFFDDB9FF),
    surfaceTint = Color(0xFF7B1FA2),
    // Tonal surface palette (neutral hue ~285°, low chroma, M3-compliant tones)
    surface = Color(0xFFFDF7FF),
    background = Color(0xFFFDF7FF),
    surfaceDim = Color(0xFFDDD8E1),
    surfaceBright = Color(0xFFFDF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1FB),
    surfaceContainer = Color(0xFFF1EBF5),
    surfaceContainerHigh = Color(0xFFEBE6EF),
    surfaceContainerHighest = Color(0xFFE5E0EA),
    surfaceVariant = Color(0xFFE7DDEC),
    onSurfaceVariant = Color(0xFF4A4350)
)

private val PurpleDarkColors = darkColorScheme(
    primary = Color(0xFFDDB9FF),
    onPrimary = Color(0xFF460072),
    primaryContainer = Color(0xFF63009D),
    onPrimaryContainer = Color(0xFFF2DBFF),
    secondary = Color(0xFFD7BDE4),
    onSecondary = Color(0xFF3A2A49),
    secondaryContainer = Color(0xFF524061),
    onSecondaryContainer = Color(0xFFF3DBFF),
    tertiary = Color(0xFFF2B7C2),
    onTertiary = Color(0xFF4A232C),
    tertiaryContainer = Color(0xFF653B43),
    onTertiaryContainer = Color(0xFFFFD9DF),
    inversePrimary = Color(0xFF7B1FA2),
    surfaceTint = Color(0xFFDDB9FF),
    // Tonal surface palette (neutral hue ~285°, low chroma, M3-compliant tones)
    surface = Color(0xFF15111A),
    background = Color(0xFF15111A),
    surfaceDim = Color(0xFF15111A),
    surfaceBright = Color(0xFF3A3540),
    surfaceContainerLowest = Color(0xFF100B15),
    surfaceContainerLow = Color(0xFF1E1921),
    surfaceContainer = Color(0xFF221D28),
    surfaceContainerHigh = Color(0xFF2D2833),
    surfaceContainerHighest = Color(0xFF38323E),
    surfaceVariant = Color(0xFF47414E),
    onSurfaceVariant = Color(0xFFCAC4D2)
)

private val PurpleDarkAmoledColors = PurpleDarkColors.toAmoled()

// ── Orange (seed #E65100) ─────────────────────────────────────────────────────

private val OrangeLightColors = lightColorScheme(
    primary = Color(0xFF8B4500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF755948),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC8),
    onSecondaryContainer = Color(0xFF2B180A),
    tertiary = Color(0xFF5D6030),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E5A8),
    onTertiaryContainer = Color(0xFF1B1D00),
    inversePrimary = Color(0xFFFFB68C),
    surfaceTint = Color(0xFF8B4500),
    // Tonal surface palette (neutral hue ~30°, low chroma, M3-compliant tones)
    surface = Color(0xFFFFF8F5),
    background = Color(0xFFFFF8F5),
    surfaceDim = Color(0xFFE1D9D5),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFAECE3),
    surfaceContainerHigh = Color(0xFFF4E6DE),
    surfaceContainerHighest = Color(0xFFEFE0D8),
    surfaceVariant = Color(0xFFEEE2DA),
    onSurfaceVariant = Color(0xFF4F4541)
)

private val OrangeDarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF4D2200),
    primaryContainer = Color(0xFF6D3300),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE6BEAA),
    onSecondary = Color(0xFF422C1D),
    secondaryContainer = Color(0xFF5B4132),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFFC7C98E),
    onTertiary = Color(0xFF2F3106),
    tertiaryContainer = Color(0xFF45481B),
    onTertiaryContainer = Color(0xFFE3E5A8),
    inversePrimary = Color(0xFF8B4500),
    surfaceTint = Color(0xFFFFB68C),
    // Tonal surface palette (neutral hue ~30°, low chroma, M3-compliant tones)
    surface = Color(0xFF1A110B),
    background = Color(0xFF1A110B),
    surfaceDim = Color(0xFF1A110B),
    surfaceBright = Color(0xFF3F3129),
    surfaceContainerLowest = Color(0xFF140C07),
    surfaceContainerLow = Color(0xFF211912),
    surfaceContainer = Color(0xFF261E17),
    surfaceContainerHigh = Color(0xFF302821),
    surfaceContainerHighest = Color(0xFF3B322B),
    surfaceVariant = Color(0xFF4F4641),
    onSurfaceVariant = Color(0xFFD3C4BB)
)

private val OrangeDarkAmoledColors = OrangeDarkColors.toAmoled()

// ─────────────────────────────────────────────────────────────────────────────
// AMOLED extension — darkens surface tokens towards black while preserving
// the active palette's color tint. Applied on top of any dark ColorScheme.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts any dark [ColorScheme] to AMOLED-compatible surfaces.
 *
 * background, surface, surfaceDim, surfaceContainerLowest → pure black.
 * All other surface containers → blended towards black via [blendToBlack],
 * preserving the original color tint so Dynamic Color and static palettes
 * remain visually cohesive in AMOLED mode.
 */
@Suppress("MagicNumber") // Blend fractions are intentional design values
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = surfaceContainerLow.blendToBlack(0.65f),
    surfaceContainer = surfaceContainer.blendToBlack(0.55f),
    surfaceContainerHigh = surfaceContainerHigh.blendToBlack(0.45f),
    surfaceContainerHighest = surfaceContainerHighest.blendToBlack(0.35f),
    surfaceVariant = surfaceVariant.blendToBlack(0.55f),
    surfaceBright = surfaceBright.blendToBlack(0.35f),
    outlineVariant = outlineVariant.blendToBlack(0.45f),
    inverseSurface = Color(0xFFE0E0E0)
)

/**
 * Linearly interpolates this color towards pure black by [fraction].
 * fraction=0.0 → original color, fraction=1.0 → pure black.
 * Alpha is preserved unchanged.
 */
private fun Color.blendToBlack(fraction: Float): Color = Color(
    red = red * (1f - fraction),
    green = green * (1f - fraction),
    blue = blue * (1f - fraction),
    alpha = alpha
)

// ─────────────────────────────────────────────────────────────────────────────
// Dispatch function — called from SimpleNotesTheme on every recomposition.
// Always returns a pre-allocated reference, never allocates a new ColorScheme.
// Exception: ColorTheme.DYNAMIC on Android 12+ creates a new scheme per call
// (unavoidable — wallpaper colors are dynamic).
// ─────────────────────────────────────────────────────────────────────────────

object ColorPalettes {
    fun getColorScheme(colorTheme: ColorTheme, isDark: Boolean, isAmoled: Boolean, context: Context): ColorScheme {
        return when (colorTheme) {
            ColorTheme.DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val base = if (isDark) {
                        dynamicDarkColorScheme(context)
                    } else {
                        dynamicLightColorScheme(context)
                    }
                    if (isAmoled) base.toAmoled() else base
                } else {
                    getStaticScheme(ColorTheme.DEFAULT, isDark, isAmoled)
                }
            }
            else -> getStaticScheme(colorTheme, isDark, isAmoled)
        }
    }

    private fun getStaticScheme(theme: ColorTheme, isDark: Boolean, isAmoled: Boolean): ColorScheme = when (theme) {
        ColorTheme.DEFAULT -> if (isDark) {
            if (isAmoled) DefaultDarkAmoledColors else DefaultDarkColors
        } else {
            DefaultLightColors
        }

        ColorTheme.BLUE -> if (isDark) {
            if (isAmoled) BlueDarkAmoledColors else BlueDarkColors
        } else {
            BlueLightColors
        }

        ColorTheme.GREEN -> if (isDark) {
            if (isAmoled) GreenDarkAmoledColors else GreenDarkColors
        } else {
            GreenLightColors
        }

        ColorTheme.RED -> if (isDark) {
            if (isAmoled) RedDarkAmoledColors else RedDarkColors
        } else {
            RedLightColors
        }

        ColorTheme.PURPLE -> if (isDark) {
            if (isAmoled) PurpleDarkAmoledColors else PurpleDarkColors
        } else {
            PurpleLightColors
        }

        ColorTheme.ORANGE -> if (isDark) {
            if (isAmoled) OrangeDarkAmoledColors else OrangeDarkColors
        } else {
            OrangeLightColors
        }

        // DYNAMIC is handled in getColorScheme(), should not reach here
        else -> if (isDark) DefaultDarkColors else DefaultLightColors
    }
}
