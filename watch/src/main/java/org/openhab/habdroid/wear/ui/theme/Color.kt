package org.openhab.habdroid.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * openHAB Wear OS color palette following Material 3 Expressive color roles.
 *
 * Based on the openHAB brand orange (#FF9800) as primary, a complementary blue
 * (#1976D2) as secondary, and a warm green (#66BB6A) as tertiary.
 *
 * Dark-only (Wear OS always uses dark theme).
 */

// ─── Primary (openHAB Orange) ───
val PrimaryOrange = Color(0xFFFFB74D)       // Primary: warm amber-orange
val PrimaryOrangeDim = Color(0xFFE69500)    // Primary-Dim: muted orange
val OnPrimary = Color(0xFF3E2700)           // On-Primary: dark brown for contrast
val PrimaryContainer = Color(0xFF5C3A00)    // Primary-Container: deep orange-brown
val OnPrimaryContainer = Color(0xFFFFDDB3)  // On-Primary-Container: light amber

// ─── Secondary (Blue – tone 80 for dark theme) ───
val SecondaryBlue = Color(0xFFA8C8FF)       // Secondary: tone 80 blue
val SecondaryBlueDim = Color(0xFF5B8FCC)    // Secondary-Dim: tone 60
val OnSecondary = Color(0xFF003060)         // On-Secondary: tone 20
val SecondaryContainer = Color(0xFF1A4A80)  // Secondary-Container: tone 30
val OnSecondaryContainer = Color(0xFFD6E3FF) // On-Secondary-Container: tone 90

// ─── Tertiary (Green – tone 80 for dark theme) ───
val TertiaryGreen = Color(0xFF8AD88E)       // Tertiary: tone 80 green
val TertiaryGreenDim = Color(0xFF4DA651)    // Tertiary-Dim: tone 60
val OnTertiary = Color(0xFF003910)          // On-Tertiary: tone 20
val TertiaryContainer = Color(0xFF1A5C24)   // Tertiary-Container: tone 30
val OnTertiaryContainer = Color(0xFFB8F0BA) // On-Tertiary-Container: tone 90

// ─── Error (tone 80/60/30 for dark theme) ───
val ErrorRed = Color(0xFFFFB4AB)            // Error: tone 80 (remove/close actions)
val ErrorRedDim = Color(0xFFCC5449)         // Error-Dim: tone 60 (high priority)
val OnError = Color(0xFF680012)             // On-Error: tone 20
val ErrorContainer = Color(0xFF8C1D18)      // Error-Container: tone 30
val OnErrorContainer = Color(0xFFFFDAD6)    // On-Error-Container: tone 90

// ─── Outline ───
val Outline = Color(0xFF8C8780)             // Primary outline for accessibility contrast
val OutlineVariant = Color(0xFF4A4540)      // Subtle outline variant

// ─── Surfaces ───
val SurfaceBlack = Color(0xFF000000)        // Background: pure black (OLED)
val SurfaceContainerLow = Color(0xFF1A1A1A) // Surface-Container-Low
val SurfaceContainer = Color(0xFF2C2C2C)    // Surface-Container (default)
val SurfaceContainerHigh = Color(0xFF3D3D3D) // Surface-Container-High
val OnSurface = Color(0xFFE6E1DC)           // On-Surface: warm white
val OnSurfaceVariant = Color(0xFFC4BFB8)    // On-Surface-Variant: dimmed warm white
val OnBackground = Color(0xFFE6E1DC)        // On-Background: same as OnSurface

// ─── Utility / Semantic ───
val StatusOnline = Color(0xFF4CAF50)        // Connection indicator: online
val StatusOffline = Color(0xFFF44336)       // Connection indicator: offline
val StatusChecking = Color(0xFF9E9E9E)      // Connection indicator: unknown

val ItemOn = Color(0xFFFF9800)              // Item state: ON (brand orange)
val ItemOff = Color(0xFF757575)             // Item state: OFF (gray)

val ArcTrack = Color(0xFF333333)            // Arc progress indicator track
