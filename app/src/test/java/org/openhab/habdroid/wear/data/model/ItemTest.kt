package org.openhab.habdroid.wear.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTest {

    // --- isActive ---

    @Test
    fun `isActive true for ON state`() {
        assertTrue(Item(name = "X", state = "ON").isActive)
    }

    @Test
    fun `isActive true for OPEN state`() {
        assertTrue(Item(name = "X", state = "OPEN").isActive)
    }

    @Test
    fun `isActive true for positive numeric state`() {
        assertTrue(Item(name = "X", state = "75").isActive)
        assertTrue(Item(name = "X", state = "1").isActive)
    }

    @Test
    fun `isActive false for OFF state`() {
        assertFalse(Item(name = "X", state = "OFF").isActive)
    }

    @Test
    fun `isActive false for CLOSED state`() {
        assertFalse(Item(name = "X", state = "CLOSED").isActive)
    }

    @Test
    fun `isActive false for 0 state`() {
        assertFalse(Item(name = "X", state = "0").isActive)
    }

    @Test
    fun `isActive false for NULL state`() {
        assertFalse(Item(name = "X", state = "NULL").isActive)
    }

    @Test
    fun `isActive false for UNDEF state`() {
        assertFalse(Item(name = "X", state = "UNDEF").isActive)
    }

    @Test
    fun `isActive false for non-numeric string`() {
        assertFalse(Item(name = "X", state = "some text").isActive)
    }

    // --- isToggleable ---

    @Test
    fun `isToggleable true for Switch`() {
        assertTrue(Item(name = "X", type = "Switch").isToggleable)
    }

    @Test
    fun `isToggleable true for Dimmer`() {
        assertTrue(Item(name = "X", type = "Dimmer").isToggleable)
    }

    @Test
    fun `isToggleable true for Color`() {
        assertTrue(Item(name = "X", type = "Color").isToggleable)
    }

    @Test
    fun `isToggleable false for Number`() {
        assertFalse(Item(name = "X", type = "Number").isToggleable)
    }

    @Test
    fun `isToggleable false for Contact`() {
        assertFalse(Item(name = "X", type = "Contact").isToggleable)
    }

    @Test
    fun `isToggleable false for String`() {
        assertFalse(Item(name = "X", type = "String").isToggleable)
    }

    @Test
    fun `isToggleable true for item with ON OFF command options`() {
        val item = Item(
            name = "X", type = "Number",
            commandDescription = CommandDescription(
                commandOptions = listOf(
                    CommandOption(command = "ON", label = "On"),
                    CommandOption(command = "OFF", label = "Off")
                )
            )
        )
        assertTrue(item.isToggleable)
    }

    @Test
    fun `isToggleable false for item with unrelated command options`() {
        val item = Item(
            name = "X", type = "Number",
            commandDescription = CommandDescription(
                commandOptions = listOf(
                    CommandOption(command = "REFRESH", label = "Refresh")
                )
            )
        )
        assertFalse(item.isToggleable)
    }

    // --- isRange ---

    @Test
    fun `isRange true for item with min max and not read-only`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(minimum = 15.0, maximum = 30.0)
        )
        assertTrue(item.isRange)
    }

    @Test
    fun `isRange false when read-only`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(minimum = 15.0, maximum = 30.0, isReadOnly = true)
        )
        assertFalse(item.isRange)
    }

    @Test
    fun `isRange false when no minimum`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(minimum = null, maximum = 30.0)
        )
        assertFalse(item.isRange)
    }

    @Test
    fun `isRange false when no maximum`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(minimum = 15.0, maximum = null)
        )
        assertFalse(item.isRange)
    }

    @Test
    fun `isRange false when no stateDescription`() {
        assertFalse(Item(name = "X", type = "Number").isRange)
    }

    // --- isContact ---

    @Test
    fun `isContact true for Contact type`() {
        assertTrue(Item(name = "X", type = "Contact").isContact)
    }

    @Test
    fun `isContact false for Switch type`() {
        assertFalse(Item(name = "X", type = "Switch").isContact)
    }

    // --- isReadOnly ---

    @Test
    fun `isReadOnly true for Contact`() {
        assertTrue(Item(name = "X", type = "Contact").isReadOnly)
    }

    @Test
    fun `isReadOnly true when stateDescription says read-only`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(isReadOnly = true)
        )
        assertTrue(item.isReadOnly)
    }

    @Test
    fun `isReadOnly false for regular Switch`() {
        assertFalse(Item(name = "X", type = "Switch").isReadOnly)
    }

    // --- numericState ---

    @Test
    fun `numericState parses integer`() {
        assertEquals(75.0, Item(name = "X", state = "75").numericState)
    }

    @Test
    fun `numericState parses decimal`() {
        assertEquals(22.5, Item(name = "X", state = "22.5").numericState)
    }

    @Test
    fun `numericState strips unit suffix`() {
        assertEquals(22.5, Item(name = "X", state = "22.5 °C").numericState)
    }

    @Test
    fun `numericState returns null for non-numeric`() {
        assertNull(Item(name = "X", state = "ON").numericState)
    }

    @Test
    fun `numericState returns null for NULL state`() {
        assertNull(Item(name = "X", state = "NULL").numericState)
    }

    // --- isSupportedForTile ---

    @Test
    fun `isSupportedForTile true for Switch`() {
        assertTrue(Item(name = "X", type = "Switch").isSupportedForTile)
    }

    @Test
    fun `isSupportedForTile true for range item`() {
        val item = Item(
            name = "X", type = "Number",
            stateDescription = StateDescription(minimum = 0.0, maximum = 100.0)
        )
        assertTrue(item.isSupportedForTile)
    }

    @Test
    fun `isSupportedForTile true for Contact`() {
        assertTrue(Item(name = "X", type = "Contact").isSupportedForTile)
    }

    @Test
    fun `isSupportedForTile false for plain Number without range`() {
        assertFalse(Item(name = "X", type = "Number").isSupportedForTile)
    }

    @Test
    fun `isSupportedForTile false for String`() {
        assertFalse(Item(name = "X", type = "String").isSupportedForTile)
    }

    // --- iconName ---

    @Test
    fun `iconName returns category when set`() {
        assertEquals("light", Item(name = "X", category = "light").iconName)
    }

    @Test
    fun `iconName returns none when no category`() {
        assertEquals("none", Item(name = "X", category = null).iconName)
    }

    // --- displayLabel ---

    @Test
    fun `displayLabel returns label when set`() {
        assertEquals("My Light", Item(name = "X", label = "My Light").displayLabel)
    }

    @Test
    fun `displayLabel returns name when label is null`() {
        assertEquals("X", Item(name = "X", label = null).displayLabel)
    }

    // --- isForTile ---

    @Test
    fun `isForTile true when wearTile metadata has position`() {
        val item = Item(
            name = "X",
            metadata = mapOf("wearTile" to MetadataEntry(value = "tile", config = mapOf("position" to "1")))
        )
        assertTrue(item.isForTile)
    }

    @Test
    fun `isForTile false when no wearTile metadata`() {
        assertFalse(Item(name = "X").isForTile)
    }

    @Test
    fun `isForTile false when wearTile has no position`() {
        val item = Item(
            name = "X",
            metadata = mapOf("wearTile" to MetadataEntry(value = "tile", config = mapOf("icon" to "light")))
        )
        assertFalse(item.isForTile)
    }

    // --- isForComplication ---

    @Test
    fun `isForComplication true when value is complication`() {
        val item = Item(
            name = "X",
            metadata = mapOf("wearTile" to MetadataEntry(value = "complication"))
        )
        assertTrue(item.isForComplication)
    }

    @Test
    fun `isForComplication true when config has complication true`() {
        val item = Item(
            name = "X",
            metadata = mapOf("wearTile" to MetadataEntry(value = "tile", config = mapOf("complication" to "true")))
        )
        assertTrue(item.isForComplication)
    }

    @Test
    fun `isForComplication false when no wearTile metadata`() {
        assertFalse(Item(name = "X").isForComplication)
    }

    @Test
    fun `isForComplication false when complication is not set`() {
        val item = Item(
            name = "X",
            metadata = mapOf("wearTile" to MetadataEntry(value = "tile", config = mapOf("position" to "1")))
        )
        assertFalse(item.isForComplication)
    }
}
