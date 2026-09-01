package org.openhab.habdroid.wear.complication

import org.openhab.habdroid.wear.data.model.Item

/**
 * Formats an openHAB item's state into a short string for complication display.
 *
 * Resolution order (first match wins):
 * 1. [Item.transformedState] — transform-service output (already display-ready).
 * 2. Option/command label from the state or command description, or a built-in label.
 * 3. Quantity formatting: apply the state-description pattern with UoM conversion via
 *    [QuantityFormatter] (e.g. state "3200 W" + pattern "%.1f kW" -> "3.2 kW"). openHAB's
 *    REST API returns the raw state in the item's own unit; the pattern may target a
 *    different compatible unit, so the conversion must happen client-side (as MainUI does).
 * 4. Plain pattern formatting for non-quantity numeric states.
 * 5. Numeric auto-formatting with a type-derived unit symbol.
 *
 * Extracted from the complication services so the logic is unit-testable and not duplicated.
 */
object ComplicationValueFormatter {

    /** Max characters a complication short value should occupy. */
    private const val MAX_LEN = 12

    private val NULL_STATES = setOf("NULL", "UNDEF")

    /** Built-in display labels for common raw state values without stateDescription options. */
    private val BUILT_IN_STATE_LABELS = mapOf(
        "ON" to "On",
        "OFF" to "Off",
        "OPEN" to "Open",
        "CLOSED" to "Closed"
    )

    fun format(item: Item, pattern: String?): String {
        // 1. transformedState (transform-service output) — already display-ready.
        val transformed = item.transformedState
        if (transformed != null && transformed !in NULL_STATES) return transformed.take(MAX_LEN)

        // 2. Option/command label lookup
        val optionLabel = item.stateDescription?.options
            ?.find { it.value == item.state }
            ?.label
            ?: item.commandDescription?.commandOptions
                ?.find { it.command == item.state }
                ?.label
            ?: BUILT_IN_STATE_LABELS[item.state]
        if (optionLabel != null) return optionLabel.take(MAX_LEN)

        // 3. Quantity formatting with UoM conversion (the correct path for Number:X items).
        //    Handles "3200 W" + "%.1f kW" -> "3.2 kW", "11.406 kWh" + "%.0f %unit%" -> "11 kWh", etc.
        if (!pattern.isNullOrBlank()) {
            QuantityFormatter.format(item.state, pattern)?.let { return it.take(MAX_LEN) }

            // 4. Fallback: plain pattern formatting for non-quantity numeric states.
            val numericValue = item.numericState
            if (numericValue != null) {
                return try {
                    if (pattern.contains("%d")) String.format(pattern, numericValue.toLong())
                    else String.format(pattern, numericValue)
                } catch (_: Exception) {
                    if (numericValue == numericValue.toLong().toDouble()) numericValue.toLong().toString()
                    else String.format("%.1f", numericValue)
                }
            }
            return try { String.format(pattern, item.state) } catch (_: Exception) { item.state.take(7) }
        }

        // 5. Numeric auto-formatting with type-derived unit
        val numericValue = item.numericState
        return when {
            item.state in NULL_STATES -> "\u2014"
            numericValue != null -> {
                val formatted = if (numericValue == numericValue.toLong().toDouble())
                    numericValue.toLong().toString() else String.format("%.1f", numericValue)
                val unit = if (item.type.contains(":")) getUnitSymbol(item.type) else null
                if (unit != null) "$formatted $unit" else formatted
            }
            else -> item.state.take(MAX_LEN)
        }
    }

    /** Crude type→unit fallback used only when no server-formatted value or pattern is available. */
    fun getUnitSymbol(type: String): String? = when {
        type.contains("Temperature") -> "°C"
        type.contains("Pressure") -> "hPa"
        type.contains("Speed") -> "km/h"
        type.contains("Length") -> "m"
        type.contains("Power") -> "W"
        type.contains("Energy") -> "kWh"
        type.contains("Dimensionless") -> "%"
        else -> null
    }
}
