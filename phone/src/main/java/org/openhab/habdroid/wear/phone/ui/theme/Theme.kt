package org.openhab.habdroid.wear.phone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val OpenHabOrange = Color(0xFFE65100)
private val OpenHabOrangeLight = Color(0xFFFF833A)
private val OpenHabOrangeDark = Color(0xFFAC1900)

private val LightColorScheme = lightColorScheme(
    primary = OpenHabOrange,
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

private val DarkColorScheme = darkColorScheme(
    primary = OpenHabOrangeLight,
    onPrimary = Color(0xFF5F1500),
    primaryContainer = OpenHabOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCF),
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

private val AppTypography = Typography()

@Composable
fun OpenHabWearPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
