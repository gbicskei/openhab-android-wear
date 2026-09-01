package org.openhab.habdroid.wear.complication

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openhab.habdroid.wear.data.model.CommandDescription
import org.openhab.habdroid.wear.data.model.CommandOption
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.StateDescription
import org.openhab.habdroid.wear.data.model.StateOption

class ComplicationValueFormatterTest {

    private fun format(item: Item, pattern: String? = null) =
        ComplicationValueFormatter.format(item, pattern)

    // ─── UOM conversion via pattern (the reported bug) ───

    @Test
    fun `power in watts with kW pattern is converted`() {
        // The reported bug: 3200 W with pattern "%.1f kW" must show "3.2 kW", not "3.2 W"
        val item = Item(
            name = "SolarPower",
            type = "Number:Power",
            state = "3200 W",
            stateDescription = StateDescription(pattern = "%.1f kW")
        )
        assertEquals("3.2 kW", format(item, "%.1f kW"))
    }

    @Test
    fun `energy pattern with percent-unit keeps state unit`() {
        // state already in kWh, pattern "%.0f %unit%" → keep kWh, apply rounding
        val item = Item(
            name = "Energy",
            type = "Number:Energy",
            state = "11.406 kWh",
            stateDescription = StateDescription(pattern = "%.0f %unit%")
        )
        assertEquals("11 kWh", format(item, "%.0f %unit%"))
    }

    @Test
    fun `temperature celsius to fahrenheit`() {
        val item = Item(
            name = "T",
            type = "Number:Temperature",
            state = "18.4 °C",
            stateDescription = StateDescription(pattern = "%.0f °F")
        )
        assertEquals("65 °F", format(item, "%.0f °F"))
    }

    @Test
    fun `same unit pattern is not converted`() {
        val item = Item(name = "P", type = "Number:Power", state = "3200 W")
        assertEquals("3200.0 W", format(item, "%.1f W"))
    }

    // ─── transformedState (first tier) ───

    @Test
    fun `transformedState used when no displayState`() {
        val item = Item(name = "X", state = "1", transformedState = "Sunny")
        assertEquals("Sunny", format(item))
    }

    @Test
    fun `transformedState NULL falls through`() {
        val item = Item(name = "X", state = "5", transformedState = "NULL")
        assertEquals("5", format(item))
    }

    // ─── option / command / built-in labels (third tier) ───

    @Test
    fun `stateDescription option label used`() {
        val item = Item(
            name = "X",
            state = "1",
            stateDescription = StateDescription(
                options = listOf(StateOption(value = "1", label = "Active"))
            )
        )
        assertEquals("Active", format(item))
    }

    @Test
    fun `commandDescription option label used`() {
        val item = Item(
            name = "X",
            state = "AUTO",
            commandDescription = CommandDescription(
                commandOptions = listOf(CommandOption(command = "AUTO", label = "Automatic"))
            )
        )
        assertEquals("Automatic", format(item))
    }

    @Test
    fun `built-in labels map ON to On`() {
        assertEquals("On", format(Item(name = "X", state = "ON")))
        assertEquals("Off", format(Item(name = "X", state = "OFF")))
        assertEquals("Open", format(Item(name = "X", state = "OPEN")))
        assertEquals("Closed", format(Item(name = "X", state = "CLOSED")))
    }

    // ─── pattern formatting (fourth tier) ───

    @Test
    fun `pattern formats numeric state`() {
        val item = Item(name = "X", state = "22.456")
        assertEquals("22.5", format(item, "%.1f"))
    }

    @Test
    fun `pattern with integer format rounds`() {
        // openHAB / MainUI round %d values (native app uses roundToInt); 50.9 -> 51
        val item = Item(name = "X", state = "50.9")
        assertEquals("51", format(item, "%d"))
    }

    @Test
    fun `pattern with unit literal no space`() {
        // "%.1f°C" — no space before unit. QuantityFormatter treats "°C" as the target unit;
        // state "21" has no unit, so the value is formatted and the pattern unit appended.
        val item = Item(name = "X", state = "21")
        assertEquals("21.0 °C", format(item, "%.1f°C"))
    }

    @Test
    fun `plain percent-d pattern on integer`() {
        val item = Item(name = "X", state = "42")
        assertEquals("42", format(item, "%d"))
    }

    // ─── numeric auto-formatting (fifth tier) ───

    @Test
    fun `numeric auto-format integer without unit`() {
        val item = Item(name = "X", type = "Number", state = "42")
        assertEquals("42", format(item))
    }

    @Test
    fun `numeric auto-format decimal without unit`() {
        val item = Item(name = "X", type = "Number", state = "42.7")
        assertEquals("42.7", format(item))
    }

    @Test
    fun `numeric auto-format appends type unit for dimensioned type`() {
        val item = Item(name = "X", type = "Number:Temperature", state = "21")
        assertEquals("21 °C", format(item))
    }

    @Test
    fun `plain Number type gets no unit`() {
        val item = Item(name = "X", type = "Number", state = "100")
        assertEquals("100", format(item))
    }

    // ─── NULL / UNDEF / non-numeric ───

    @Test
    fun `NULL state renders em dash`() {
        val item = Item(name = "X", type = "Number", state = "NULL")
        assertEquals("\u2014", format(item))
    }

    @Test
    fun `UNDEF state renders em dash`() {
        val item = Item(name = "X", type = "Number", state = "UNDEF")
        assertEquals("\u2014", format(item))
    }

    @Test
    fun `non-numeric string state returned truncated`() {
        val item = Item(name = "X", type = "String", state = "HelloWorldLongString")
        assertEquals("HelloWorldLo", format(item))
    }

    // ─── getUnitSymbol ───

    @Test
    fun `getUnitSymbol maps known dimensions`() {
        assertEquals("°C", ComplicationValueFormatter.getUnitSymbol("Number:Temperature"))
        assertEquals("hPa", ComplicationValueFormatter.getUnitSymbol("Number:Pressure"))
        assertEquals("km/h", ComplicationValueFormatter.getUnitSymbol("Number:Speed"))
        assertEquals("m", ComplicationValueFormatter.getUnitSymbol("Number:Length"))
        assertEquals("W", ComplicationValueFormatter.getUnitSymbol("Number:Power"))
        assertEquals("kWh", ComplicationValueFormatter.getUnitSymbol("Number:Energy"))
        assertEquals("%", ComplicationValueFormatter.getUnitSymbol("Number:Dimensionless"))
    }

    @Test
    fun `getUnitSymbol returns null for unknown type`() {
        assertEquals(null, ComplicationValueFormatter.getUnitSymbol("Number"))
        assertEquals(null, ComplicationValueFormatter.getUnitSymbol("String"))
    }
}
