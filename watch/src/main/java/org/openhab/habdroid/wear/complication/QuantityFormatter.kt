package org.openhab.habdroid.wear.complication

import kotlin.math.roundToLong

/**
 * Formats an openHAB QuantityType state for display, replicating what MainUI does.
 *
 * openHAB stores a QuantityType item's state in its own unit (e.g. "4200 W"), while the
 * state description pattern is display-only and may target a *different, compatible* unit
 * (e.g. "%.1f kW"). The REST API does NOT return a pre-converted value — the conversion is
 * a client responsibility. This formatter performs that conversion:
 *
 *  - "4200 W" + pattern "%.1f kW"  -> "4.2 kW"   (metric-prefix scaling)
 *  - "11.406 kWh" + "%.0f %unit%"  -> "11 kWh"   (%unit% = item's own unit, no conversion)
 *  - "18.4 °C" + "%.0f °F"         -> "65 °F"    (temperature formula)
 *
 * Only conversions defined by the openHAB UoM concept are supported: SI metric prefixes on a
 * shared base unit, and temperature (°C/°F/K). When the target unit is unknown or incompatible,
 * the value is formatted with the item's own unit (never a wrong unit).
 *
 * Reference: https://www.openhab.org/docs/concepts/units-of-measurement.html
 */
object QuantityFormatter {

    /** SI metric prefix → factor relative to the base unit. */
    private val METRIC_PREFIXES = mapOf(
        "Y" to 1e24, "Z" to 1e21, "E" to 1e18, "P" to 1e15, "T" to 1e12,
        "G" to 1e9, "M" to 1e6, "k" to 1e3, "h" to 1e2, "da" to 1e1,
        "d" to 1e-1, "c" to 1e-2, "m" to 1e-3, "µ" to 1e-6, "u" to 1e-6,
        "n" to 1e-9, "p" to 1e-12, "f" to 1e-15, "a" to 1e-18, "z" to 1e-21, "y" to 1e-24
    )

    /** Base unit symbols that commonly take metric prefixes in openHAB. */
    private val PREFIXABLE_BASE_UNITS = setOf(
        "W", "Wh", "VA", "VAh", "var", "varh", "J", "Ws",   // power / energy
        "V", "A", "Ah", "Ω", "F", "H", "S",                 // electrical
        "m", "m²", "m³", "g", "Pa", "Hz", "l", "bar",       // length / mass / misc
        "W/m²", "B", "bit"
    )

    private val TEMPERATURE_UNITS = setOf("°C", "°F", "K")

    /**
     * Format [state] (e.g. "4200 W") using [pattern] (e.g. "%.1f kW").
     * Returns null if the state isn't a numeric quantity or the pattern can't be applied,
     * so the caller can fall back to its own handling.
     */
    fun format(state: String, pattern: String): String? {
        val (value, stateUnit) = parseQuantity(state) ?: return null

        val targetUnit = extractPatternUnit(pattern)
        val numericFormat = extractNumericFormat(pattern)

        // Determine the value + unit to render.
        val (renderValue, renderUnit) = when {
            // No explicit target unit (or %unit% placeholder): use the item's own unit as-is.
            targetUnit == null || targetUnit == "%unit%" ->
                value to stateUnit

            // Target unit equals the state unit: no conversion.
            stateUnit != null && targetUnit == stateUnit ->
                value to targetUnit

            // Try to convert from the state unit to the pattern's target unit.
            stateUnit != null -> {
                val converted = convert(value, stateUnit, targetUnit)
                if (converted != null) converted to targetUnit
                else return null // incompatible units — let caller fall back
            }

            // State had no unit but pattern specifies one: just apply the pattern's unit.
            else -> value to targetUnit
        }

        return formatNumber(renderValue, numericFormat).let { num ->
            if (renderUnit.isNullOrBlank()) num else "$num $renderUnit"
        }
    }

    /** Parse "4200 W" -> (4200.0, "W"); "11.406 kWh" -> (11.406, "kWh"); "42" -> (42.0, null). */
    private fun parseQuantity(state: String): Pair<Double, String?>? {
        val trimmed = state.trim()
        if (trimmed.isEmpty() || trimmed in setOf("NULL", "UNDEF")) return null
        val spaceIdx = trimmed.indexOf(' ')
        return if (spaceIdx < 0) {
            val v = trimmed.toDoubleOrNull() ?: return null
            v to null
        } else {
            val v = trimmed.substring(0, spaceIdx).toDoubleOrNull() ?: return null
            val u = trimmed.substring(spaceIdx + 1).trim().ifBlank { null }
            v to u
        }
    }

    /**
     * Extract the target unit from a pattern like "%.1f kW" -> "kW", "%.0f %unit%" -> "%unit%",
     * "%d" -> null. The unit is whatever non-format text follows the format specifier.
     */
    private fun extractPatternUnit(pattern: String): String? {
        // Match a printf-style specifier, then take the remainder as the unit.
        val specifier = Regex("%[-+ 0,(#]*\\d*(?:\\.\\d+)?[a-zA-Z%]").find(pattern) ?: return null
        // %% would be a literal percent, not a specifier followed by unit — handled by remainder.
        val remainder = pattern.substring(specifier.range.last + 1).trim()
        return remainder.ifBlank { null }
    }

    /** Extract just the numeric format specifier, e.g. "%.1f kW" -> "%.1f", default "%s". */
    private fun extractNumericFormat(pattern: String): String {
        val specifier = Regex("%[-+ 0,(#]*\\d*(?:\\.\\d+)?[dfeg]").find(pattern)
        return specifier?.value ?: "%s"
    }

    /** Convert [value] from [fromUnit] to [toUnit]. Returns null if incompatible. */
    private fun convert(value: Double, fromUnit: String, toUnit: String): Double? {
        if (fromUnit == toUnit) return value

        // Temperature: non-linear, handle explicitly.
        if (fromUnit in TEMPERATURE_UNITS && toUnit in TEMPERATURE_UNITS) {
            return convertTemperature(value, fromUnit, toUnit)
        }

        // Metric-prefix scaling on a shared base unit.
        val (fromPrefix, fromBase) = splitPrefix(fromUnit) ?: return null
        val (toPrefix, toBase) = splitPrefix(toUnit) ?: return null
        if (fromBase != toBase) return null // different base units — not convertible here

        val fromFactor = METRIC_PREFIXES[fromPrefix] ?: 1.0
        val toFactor = METRIC_PREFIXES[toPrefix] ?: 1.0
        return value * fromFactor / toFactor
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        // Normalize to Celsius first.
        val celsius = when (from) {
            "°C" -> value
            "°F" -> (value - 32.0) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> value
        }
        return when (to) {
            "°C" -> celsius
            "°F" -> celsius * 9.0 / 5.0 + 32.0
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    /**
     * Split a prefixed unit into (prefix, baseUnit). "kW" -> ("k","W"), "W" -> ("","W"),
     * "kWh" -> ("k","Wh"), "mm" -> ("m","m"). Returns null if the base isn't a known prefixable unit.
     */
    private fun splitPrefix(unit: String): Pair<String, String>? {
        if (unit in PREFIXABLE_BASE_UNITS) return "" to unit
        // Try longest prefixes first (e.g. "da") then single-char.
        for (prefix in listOf("da") + METRIC_PREFIXES.keys.filter { it.length == 1 }) {
            if (unit.length > prefix.length && unit.startsWith(prefix)) {
                val base = unit.substring(prefix.length)
                if (base in PREFIXABLE_BASE_UNITS) return prefix to base
            }
        }
        return null
    }

    /** Apply a numeric format specifier; falls back to a compact representation on error. */
    private fun formatNumber(value: Double, numericFormat: String): String {
        return try {
            if (numericFormat.contains("%d")) {
                String.format(numericFormat, value.roundToLong())
            } else if (numericFormat == "%s") {
                if (value == value.toLong().toDouble()) value.toLong().toString()
                else value.toString()
            } else {
                String.format(numericFormat, value)
            }
        } catch (_: Exception) {
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else String.format("%.1f", value)
        }
    }
}
