package org.openhab.habdroid.wear.phone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OpenHabOrange = Color(0xFFE65100)
private val OpenHabOrangeLight = Color(0xFFFF833A)
private val OpenHabOrangeDark = Color(0xFFAC1900)

/**
 * Accent color definitions matching the watch's TileTheme enum (M3 tone-80 values).
 * Used as the primary color when dynamic color is unavailable.
 */
enum class PhoneAccent(val light: Color, val dark: Color, val container: Color, val onContainer: Color) {
    AMBER(
        light = Color(0xFFE65100),
        dark = Color(0xFFFFB950),
        container = Color(0xFF5C3A00),
        onContainer = Color(0xFFFFDDB3)
    ),
    BLUE(
        light = Color(0xFF1565C0),
        dark = Color(0xFFA8C8FF),
        container = Color(0xFF1A4A80),
        onContainer = Color(0xFFD6E3FF)
    ),
    GREEN(
        light = Color(0xFF2E7D32),
        dark = Color(0xFF8AD88E),
        container = Color(0xFF1A5C24),
        onContainer = Color(0xFFB8F0BA)
    ),
    PURPLE(
        light = Color(0xFF6A1B9A),
        dark = Color(0xFFD4BBFF),
        container = Color(0xFF4A2080),
        onContainer = Color(0xFFEDDDFF)
    ),
    RED(
        light = Color(0xFFC62828),
        dark = Color(0xFFFFB4AB),
        container = Color(0xFF8C1D18),
        onContainer = Color(0xFFFFDAD6)
    );

    companion object {
        fun fromName(name: String): PhoneAccent =
            entries.find { it.name == name } ?: AMBER
    }
}

private fun lightSchemeForAccent(accent: PhoneAccent) = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D0),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFF77574D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C150F),
    tertiary = Color(0xFF6C5D2F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E1A6),
    onTertiaryContainer = Color(0xFF231B00),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A18),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736D)
)

private fun darkSchemeForAccent(accent: PhoneAccent) = darkColorScheme(
    primary = accent.dark,
    onPrimary = Color(0xFF3E2700),
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    secondary = Color(0xFFE7BDB1),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD9C58C),
    onTertiary = Color(0xFF3B2F04),
    tertiaryContainer = Color(0xFF534519),
    onTertiaryContainer = Color(0xFFF6E1A6),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BB),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFDA),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D86)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OpenHabWearPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentName: String = "AMBER",
    content: @Composable () -> Unit
) {
    val accent = PhoneAccent.fromName(accentName)

    // Use the branded accent scheme. Dynamic color is disabled so the app
    // matches the watch's theme selection consistently.
    val colorScheme = when {
        darkTheme -> darkSchemeForAccent(accent)
        else -> lightSchemeForAccent(accent)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
