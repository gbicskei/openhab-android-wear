package org.openhab.habdroid.wear.complication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityFormatterTest {

    // ─── Metric-prefix conversion (the reported bug) ───

    @Test
    fun `watts to kilowatts`() {
        assertEquals("3.2 kW", QuantityFormatter.format("3200 W", "%.1f kW"))
    }

    @Test
    fun `watts to kilowatts no space in pattern`() {
        // The tile complication config uses "%.1fkW" (no space)
        assertEquals("3.2 kW", QuantityFormatter.format("3200 W", "%.1fkW"))
    }

    @Test
    fun `watts to kilowatts rounded`() {
        assertEquals("4.2 kW", QuantityFormatter.format("4200 W", "%.1f kW"))
    }

    @Test
    fun `watts to megawatts`() {
        assertEquals("1.5 MW", QuantityFormatter.format("1500000 W", "%.1f MW"))
    }

    @Test
    fun `watt-hours to kilowatt-hours`() {
        assertEquals("2.5 kWh", QuantityFormatter.format("2500 Wh", "%.1f kWh"))
    }

    @Test
    fun `kilowatts to watts`() {
        assertEquals("3200 W", QuantityFormatter.format("3.2 kW", "%.0f W"))
    }

    @Test
    fun `meters to millimeters`() {
        assertEquals("1500 mm", QuantityFormatter.format("1.5 m", "%.0f mm"))
    }

    // ─── %unit% placeholder keeps the state's own unit (no conversion) ───

    @Test
    fun `percent-unit keeps state unit`() {
        assertEquals("11 kWh", QuantityFormatter.format("11.406 kWh", "%.0f %unit%"))
    }

    @Test
    fun `percent-unit with watts`() {
        assertEquals("4200 W", QuantityFormatter.format("4200 W", "%.0f %unit%"))
    }

    // ─── Same unit: format only, no conversion ───

    @Test
    fun `same unit applies numeric format`() {
        assertEquals("3200.0 W", QuantityFormatter.format("3200 W", "%.1f W"))
    }

    // ─── Temperature ───

    @Test
    fun `celsius to fahrenheit`() {
        assertEquals("65 °F", QuantityFormatter.format("18.4 °C", "%.0f °F"))
    }

    @Test
    fun `fahrenheit to celsius`() {
        assertEquals("20.0 °C", QuantityFormatter.format("68 °F", "%.1f °C"))
    }

    @Test
    fun `celsius to kelvin`() {
        assertEquals("293 K", QuantityFormatter.format("20 °C", "%.0f K"))
    }

    @Test
    fun `celsius stays celsius`() {
        assertEquals("21.5 °C", QuantityFormatter.format("21.5 °C", "%.1f °C"))
    }

    // ─── No unit in state ───

    @Test
    fun `plain number with numeric pattern`() {
        assertEquals("22.5", QuantityFormatter.format("22.456", "%.1f"))
    }

    @Test
    fun `plain number with integer pattern rounds`() {
        assertEquals("51", QuantityFormatter.format("50.9", "%d"))
    }

    @Test
    fun `plain number with pattern unit appends unit`() {
        // state has no unit, pattern specifies one → append it (no conversion possible)
        assertEquals("50.0 kW", QuantityFormatter.format("50", "%.1f kW"))
    }

    // ─── Non-convertible / fallback cases return null ───

    @Test
    fun `incompatible units return null`() {
        // W cannot convert to °C
        assertNull(QuantityFormatter.format("3200 W", "%.1f °C"))
    }

    @Test
    fun `non-numeric state returns null`() {
        assertNull(QuantityFormatter.format("ON", "%.1f kW"))
    }

    @Test
    fun `NULL state returns null`() {
        assertNull(QuantityFormatter.format("NULL", "%.0f %unit%"))
    }

    @Test
    fun `empty state returns null`() {
        assertNull(QuantityFormatter.format("", "%.0f"))
    }

    // ─── Unknown base unit falls back gracefully ───

    @Test
    fun `unknown unit with percent-unit keeps it`() {
        // A unit we don't model, with %unit% → keep the state's unit verbatim
        assertEquals("42 foo", QuantityFormatter.format("42 foo", "%.0f %unit%"))
    }
}
