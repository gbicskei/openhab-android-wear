package org.openhab.habdroid.wear.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileItemTest {

    private fun item(
        name: String = "TestItem",
        label: String? = "Test Label",
        type: String = "Switch",
        state: String = "ON",
        category: String? = "light"
    ) = Item(name = name, label = label, type = type, state = state, category = category)

    private fun tileItem(
        item: Item = item(),
        page: String = TileItem.PAGE_MAIN,
        slot: Int = 1,
        icon: String? = null,
        label: String? = null,
        action: String? = null,
        valueItemName: String? = null,
        valueItem: Item? = null,
        invertValue: Boolean = false,
        commandItemName: String? = null,
        commandValue: String? = null,
        needsConfirmation: Boolean = false,
        aggregateState: Boolean = false
    ) = TileItem(
        item = item, page = page, slot = slot, icon = icon, label = label,
        action = action, valueItemName = valueItemName, valueItem = valueItem,
        invertValue = invertValue, commandItemName = commandItemName,
        commandValue = commandValue, needsConfirmation = needsConfirmation,
        aggregateState = aggregateState
    )

    // --- parsePosition ---

    @Test
    fun `parsePosition with plain number returns main page`() {
        assertEquals(TileItem.PAGE_MAIN to 1, TileItem.parsePosition("1"))
        assertEquals(TileItem.PAGE_MAIN to 3, TileItem.parsePosition("3"))
        assertEquals(TileItem.PAGE_MAIN to 7, TileItem.parsePosition("7"))
    }

    @Test
    fun `parsePosition with float number returns main page with int slot`() {
        assertEquals(TileItem.PAGE_MAIN to 1, TileItem.parsePosition("1.0"))
        assertEquals(TileItem.PAGE_MAIN to 3, TileItem.parsePosition("3.0"))
    }

    @Test
    fun `parsePosition with page colon slot returns correct page and slot`() {
        assertEquals("security" to 1, TileItem.parsePosition("security:1"))
        assertEquals("climate" to 3, TileItem.parsePosition("climate:3"))
        assertEquals(TileItem.PAGE_MAIN to 6, TileItem.parsePosition("main:6"))
    }

    @Test
    fun `parsePosition with page colon float slot returns int slot`() {
        assertEquals("security" to 2, TileItem.parsePosition("security:2.0"))
    }

    @Test
    fun `parsePosition with null returns main page slot 1`() {
        assertEquals(TileItem.PAGE_MAIN to 1, TileItem.parsePosition(null))
    }

    @Test
    fun `parsePosition with invalid string returns main page slot 1`() {
        assertEquals(TileItem.PAGE_MAIN to 1, TileItem.parsePosition("abc"))
    }

    // --- effectiveIcon ---

    @Test
    fun `effectiveIcon returns metadata icon when set`() {
        val ti = tileItem(icon = "iconify:mdi:gate")
        assertEquals("iconify:mdi:gate", ti.effectiveIcon)
    }

    @Test
    fun `effectiveIcon falls back to item category`() {
        val ti = tileItem(item = item(category = "heating"), icon = null)
        assertEquals("heating", ti.effectiveIcon)
    }

    @Test
    fun `effectiveIcon falls back to none when no category`() {
        val ti = tileItem(item = item(category = null), icon = null)
        assertEquals("none", ti.effectiveIcon)
    }

    // --- effectiveLabel ---

    @Test
    fun `effectiveLabel returns metadata label when set`() {
        val ti = tileItem(label = "Custom Label")
        assertEquals("Custom Label", ti.effectiveLabel)
    }

    @Test
    fun `effectiveLabel falls back to item displayLabel`() {
        val ti = tileItem(item = item(label = "Item Label"), label = null)
        assertEquals("Item Label", ti.effectiveLabel)
    }

    @Test
    fun `effectiveLabel falls back to item name when label is null`() {
        val ti = tileItem(item = item(name = "MyItem", label = null), label = null)
        assertEquals("MyItem", ti.effectiveLabel)
    }

    // --- displayItem ---

    @Test
    fun `displayItem returns valueItem when set`() {
        val primary = item(name = "Primary", state = "OFF")
        val value = item(name = "ValueItem", state = "ON")
        val ti = tileItem(item = primary, valueItemName = "ValueItem", valueItem = value)
        assertEquals(value, ti.displayItem)
    }

    @Test
    fun `displayItem returns primary item when no valueItem`() {
        val primary = item(name = "Primary", state = "ON")
        val ti = tileItem(item = primary)
        assertEquals(primary, ti.displayItem)
    }

    // --- commandTargetName ---

    @Test
    fun `commandTargetName returns commandItemName when set`() {
        val ti = tileItem(item = item(name = "Primary"), commandItemName = "CommandTarget")
        assertEquals("CommandTarget", ti.commandTargetName)
    }

    @Test
    fun `commandTargetName returns primary item name when no commandItem`() {
        val ti = tileItem(item = item(name = "Primary"))
        assertEquals("Primary", ti.commandTargetName)
    }

    // --- isDisplayActive ---

    @Test
    fun `isDisplayActive returns true when displayItem is active`() {
        val ti = tileItem(item = item(state = "ON"))
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive returns false when displayItem is inactive`() {
        val ti = tileItem(item = item(state = "OFF"))
        assertFalse(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with invertValue flips ON to inactive`() {
        val ti = tileItem(item = item(state = "ON"), invertValue = true)
        assertFalse(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with invertValue flips OFF to active`() {
        val ti = tileItem(item = item(state = "OFF"), invertValue = true)
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive uses valueItem state when set`() {
        val primary = item(name = "Primary", state = "OFF")
        val value = item(name = "Sensor", state = "ON")
        val ti = tileItem(item = primary, valueItemName = "Sensor", valueItem = value)
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with valueItem and invertValue`() {
        val primary = item(name = "Primary", state = "OFF")
        val value = item(name = "Sensor", state = "ON")
        val ti = tileItem(item = primary, valueItemName = "Sensor", valueItem = value, invertValue = true)
        assertFalse(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with OPEN state is active`() {
        val ti = tileItem(item = item(state = "OPEN", type = "Contact"))
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with CLOSED state is inactive`() {
        val ti = tileItem(item = item(state = "CLOSED", type = "Contact"))
        assertFalse(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with invertValue CLOSED becomes active`() {
        val ti = tileItem(item = item(state = "CLOSED", type = "Contact"), invertValue = true)
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with numeric gt 0 is active`() {
        val ti = tileItem(item = item(state = "75", type = "Dimmer"))
        assertTrue(ti.isDisplayActive)
    }

    @Test
    fun `isDisplayActive with numeric 0 is inactive`() {
        val ti = tileItem(item = item(state = "0", type = "Dimmer"))
        assertFalse(ti.isDisplayActive)
    }

    // --- displayItemName ---

    @Test
    fun `displayItemName returns valueItemName when set`() {
        val ti = tileItem(item = item(name = "Primary"), valueItemName = "Sensor")
        assertEquals("Sensor", ti.displayItemName)
    }

    @Test
    fun `displayItemName returns primary name when no valueItem`() {
        val ti = tileItem(item = item(name = "Primary"))
        assertEquals("Primary", ti.displayItemName)
    }

    // --- isToggle ---

    @Test
    fun `isToggle true for switch with no action`() {
        val ti = tileItem(item = item(type = "Switch"), action = null)
        assertTrue(ti.isToggle)
    }

    @Test
    fun `isToggle false when action is set`() {
        val ti = tileItem(item = item(type = "Switch"), action = "command")
        assertFalse(ti.isToggle)
    }

    @Test
    fun `isToggle false for range item`() {
        val ti = tileItem(
            item = Item(name = "X", type = "Number", stateDescription = StateDescription(minimum = 0.0, maximum = 100.0)),
            action = null
        )
        assertFalse(ti.isToggle)
    }

    // --- isCommand ---

    @Test
    fun `isCommand true when action is command`() {
        val ti = tileItem(action = "command")
        assertTrue(ti.isCommand)
    }

    @Test
    fun `isCommand false for other actions`() {
        val ti = tileItem(action = "page:security")
        assertFalse(ti.isCommand)
    }

    @Test
    fun `isCommand false when action is null`() {
        val ti = tileItem(action = null)
        assertFalse(ti.isCommand)
    }

    // --- isRangeControl ---

    @Test
    fun `isRangeControl true for range item with no action`() {
        val ti = tileItem(
            item = Item(name = "X", type = "Number", stateDescription = StateDescription(minimum = 15.0, maximum = 30.0)),
            action = null
        )
        assertTrue(ti.isRangeControl)
    }

    @Test
    fun `isRangeControl false when action is set`() {
        val ti = tileItem(
            item = Item(name = "X", type = "Number", stateDescription = StateDescription(minimum = 15.0, maximum = 30.0)),
            action = "command"
        )
        assertFalse(ti.isRangeControl)
    }

    // --- isPageNavigation ---

    @Test
    fun `isPageNavigation true for page action`() {
        val ti = tileItem(action = "page:security")
        assertTrue(ti.isPageNavigation)
    }

    @Test
    fun `isPageNavigation false for command action`() {
        val ti = tileItem(action = "command")
        assertFalse(ti.isPageNavigation)
    }

    @Test
    fun `isPageNavigation false for null action`() {
        val ti = tileItem(action = null)
        assertFalse(ti.isPageNavigation)
    }

    // --- targetPage ---

    @Test
    fun `targetPage returns page name for navigation buttons`() {
        val ti = tileItem(action = "page:security")
        assertEquals("security", ti.targetPage)
    }

    @Test
    fun `targetPage returns null for non-navigation`() {
        val ti = tileItem(action = "command")
        assertNull(ti.targetPage)
    }

    // --- aggregateState ---

    @Test
    fun `aggregateState defaults to false`() {
        val ti = tileItem()
        assertFalse(ti.aggregateState)
    }

    @Test
    fun `aggregateState can be set to true`() {
        val ti = tileItem(aggregateState = true)
        assertTrue(ti.aggregateState)
    }

    // --- compareTo (sorting by slot) ---

    @Test
    fun `compareTo sorts by slot ascending`() {
        val a = tileItem(slot = 3)
        val b = tileItem(slot = 1)
        val c = tileItem(slot = 2)
        val sorted = listOf(a, b, c).sorted()
        assertEquals(listOf(1, 2, 3), sorted.map { it.slot })
    }

    // --- doubleTap properties ---

    @Test
    fun `hasDoubleTap is true when doubleTapItem is set`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "AC_Setpoint"
        )
        assertTrue(ti.hasDoubleTap)
    }

    @Test
    fun `hasDoubleTap is false when doubleTapItem is null`() {
        val ti = tileItem()
        assertFalse(ti.hasDoubleTap)
    }

    @Test
    fun `doubleTapStateDisplay defaults to NONE`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X"
        )
        assertEquals(ValueDisplay.NONE, ti.doubleTapStateDisplay)
    }

    @Test
    fun `doubleTapStateDisplay can be VALUE`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X",
            doubleTapStateDisplay = ValueDisplay.VALUE
        )
        assertEquals(ValueDisplay.VALUE, ti.doubleTapStateDisplay)
    }

    @Test
    fun `doubleTapAction stores toggle`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X",
            doubleTapAction = "toggle"
        )
        assertEquals("toggle", ti.doubleTapAction)
    }

    @Test
    fun `doubleTapCommand stores command string`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X",
            doubleTapAction = "command",
            doubleTapCommand = "BOOST"
        )
        assertEquals("BOOST", ti.doubleTapCommand)
    }

    @Test
    fun `doubleTapConfirmation defaults to false`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X"
        )
        assertFalse(ti.doubleTapConfirmation)
    }

    @Test
    fun `doubleTapConfirmation can be true`() {
        val ti = TileItem(
            item = item(), page = "main", slot = 1,
            doubleTapItem = "X",
            doubleTapConfirmation = true
        )
        assertTrue(ti.doubleTapConfirmation)
    }
}
