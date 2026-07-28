package org.openhab.habdroid.wear.data.icon

/**
 * Three-state display mode for tile icons.
 *
 * - ACTIVE: item is on/open/active — full glow, full opacity ring and icon
 * - NEUTRAL: item has no boolean state (page nav, value display, range) — no glow, moderate opacity
 * - INACTIVE: item is off/closed/inactive — no glow, dimmed ring and icon
 */
enum class IconState {
    ACTIVE,
    NEUTRAL,
    INACTIVE
}
