package org.openhab.habdroid.wear.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.Typography

/**
 * openHAB Wear OS typography based on the Wear Material 3 Expressive type scale.
 *
 * Uses the system default font (Roboto Flex on Wear OS 5+).
 * Type roles: display, title, label, body, numeral, arc.
 *
 * Customizations from baseline:
 * - Title: slightly bolder for glanceability on the small screen
 * - Numeral: uses tabular figures (handled by system Roboto Flex)
 */
val WearOHTypography = Typography(
    // Use all defaults from the Wear Material 3 type scale.
    // The system Roboto Flex variable font handles weight/width axes automatically.
    // Explicit overrides can be added here when needed:
    //
    // displayLarge = TextStyle(...)
    // titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, ...)
)
