package org.openhab.habdroid.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.Flow
import org.openhab.habdroid.wear.data.repository.ThemeStore
import org.openhab.habdroid.wear.data.repository.TileTheme

/**
 * openHAB Wear OS Material 3 Expressive theme.
 *
 * Wraps the Wear Compose Material 3 [MaterialTheme] with openHAB-branded colors
 * following the M3 Expressive design guidelines.
 *
 * The primary accent color is driven by the user's theme selection in [TileTheme],
 * which also controls the tile's color. This keeps the app and tile visually consistent.
 *
 * All activities should wrap their content in this theme:
 * ```
 * setContent {
 *     WearOHTheme {
 *         MyScreen()
 *     }
 * }
 * ```
 */
@Composable
fun WearOHTheme(
    themeFlow: Flow<TileTheme>? = null,
    accentTheme: TileTheme = ThemeStore.cachedTheme,
    content: @Composable () -> Unit
) {
    val resolvedTheme = if (themeFlow != null) {
        val theme by themeFlow.collectAsState(initial = accentTheme)
        theme
    } else {
        accentTheme
    }
    val colorScheme = buildColorScheme(resolvedTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WearOHTypography,
        content = content
    )
}

/**
 * Build a color scheme with the primary accent derived from the user's [TileTheme] selection.
 * Derives dim/container/on variants using M3 Expressive tonal ratios:
 * - Primary: tone 80 (provided by TileTheme)
 * - Primary-Dim: tone 60 (darker, less prominent)
 * - Primary-Container: tone 30 (deep, for card/modal backgrounds)
 * - On-Primary: tone 20 (dark, for text on primary fills)
 * - On-Primary-Container: tone 90 (light, for text on containers)
 *
 * Secondary (blue) and tertiary (green) remain fixed as complementary roles.
 */
private fun buildColorScheme(theme: TileTheme): ColorScheme {
    val accent = Color(theme.color.toLong() or 0xFF000000L)

    // Approximate tonal shifts: dim = darken to ~60%, container = darken to ~30%
    // These ratios simulate the HCT tone steps without a full HCT library
    val accentDim = darkenToTone(accent, targetTone = 0.60f)
    val accentContainer = darkenToTone(accent, targetTone = 0.30f)
    val onPrimaryColor = darkenToTone(accent, targetTone = 0.20f)
    val onPrimaryContainerColor = lightenToTone(accent, targetTone = 0.90f)

    return ColorScheme(
        primary = accent,
        primaryDim = accentDim,
        primaryContainer = accentContainer,
        onPrimary = onPrimaryColor,
        onPrimaryContainer = onPrimaryContainerColor,
        secondary = SecondaryBlue,
        secondaryDim = SecondaryBlueDim,
        secondaryContainer = SecondaryContainer,
        onSecondary = OnSecondary,
        onSecondaryContainer = OnSecondaryContainer,
        tertiary = TertiaryGreen,
        tertiaryDim = TertiaryGreenDim,
        tertiaryContainer = TertiaryContainer,
        onTertiary = OnTertiary,
        onTertiaryContainer = OnTertiaryContainer,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        onSurface = OnSurface,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        outlineVariant = OutlineVariant,
        background = SurfaceBlack,
        onBackground = OnBackground,
        error = ErrorRed,
        errorDim = ErrorRedDim,
        errorContainer = ErrorContainer,
        onError = OnError,
        onErrorContainer = OnErrorContainer,
    )
}

/**
 * Darken a color to approximate a lower HCT tone.
 * Maps the color's luminance toward the target tone (0.0 = black, 1.0 = white).
 */
private fun darkenToTone(color: Color, targetTone: Float): Color {
    // Scale RGB channels proportionally to reach target luminance
    val scale = targetTone / 0.80f // Source is tone 80
    return Color(
        red = (color.red * scale).coerceIn(0f, 1f),
        green = (color.green * scale).coerceIn(0f, 1f),
        blue = (color.blue * scale).coerceIn(0f, 1f),
        alpha = 1f
    )
}

/**
 * Lighten a color to approximate a higher HCT tone.
 * Blends toward white to reach the target tone.
 */
private fun lightenToTone(color: Color, targetTone: Float): Color {
    val blend = (targetTone - 0.80f) / (1f - 0.80f) // How much to blend toward white
    return Color(
        red = color.red + (1f - color.red) * blend,
        green = color.green + (1f - color.green) * blend,
        blue = color.blue + (1f - color.blue) * blend,
        alpha = 1f
    )
}
