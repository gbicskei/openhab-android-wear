package org.openhab.habdroid.wear.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem

class ItemCacheTest {

    private lateinit var cache: ItemCache

    private fun item(name: String, state: String = "OFF") =
        Item(name = name, type = "Switch", state = state)

    private fun tileItem(
        item: Item,
        valueItemName: String? = null,
        valueItem: Item? = null,
        page: String = TileItem.PAGE_MAIN,
        slot: Int = 1
    ) = TileItem(
        item = item, page = page, slot = slot,
        valueItemName = valueItemName, valueItem = valueItem
    )

    @Before
    fun setup() {
        cache = ItemCache()
    }

    // --- Basic operations ---

    @Test
    fun `get returns null when cache is empty`() {
        assertNull(cache.get())
    }

    @Test
    fun `get returns items after put`() {
        val items = listOf(tileItem(item("Light1")))
        cache.put(items)
        assertEquals(items, cache.get())
    }

    @Test
    fun `isLoaded false initially`() {
        assertFalse(cache.isLoaded)
    }

    @Test
    fun `isLoaded true after put`() {
        cache.put(listOf(tileItem(item("Light1"))))
        assertTrue(cache.isLoaded)
    }

    @Test
    fun `clear resets cache to null`() {
        cache.put(listOf(tileItem(item("Light1"))))
        cache.clear()
        assertNull(cache.get())
        assertFalse(cache.isLoaded)
    }

    @Test
    fun `statesLoaded false initially`() {
        assertFalse(cache.statesLoaded)
    }

    @Test
    fun `clear resets statesLoaded`() {
        cache.statesLoaded = true
        cache.clear()
        assertFalse(cache.statesLoaded)
    }

    // --- invalidateStates ---

    @Test
    fun `invalidateStates sets statesLoaded to false`() {
        cache.statesLoaded = true
        cache.invalidateStates()
        assertFalse(cache.statesLoaded)
    }

    @Test
    fun `invalidateStates does not clear cached items`() {
        val items = listOf(tileItem(item("Light1")))
        cache.put(items)
        cache.statesLoaded = true
        cache.invalidateStates()
        assertEquals(items, cache.get())
    }

    // --- updateItemState (primary item) ---

    @Test
    fun `updateItemState updates primary item state`() {
        cache.put(listOf(tileItem(item("Light1", "OFF"))))
        cache.updateItemState("Light1", "ON")
        assertEquals("ON", cache.get()!![0].item.state)
    }

    @Test
    fun `updateItemState does not affect other items`() {
        cache.put(listOf(
            tileItem(item("Light1", "OFF"), slot = 1),
            tileItem(item("Light2", "OFF"), slot = 2)
        ))
        cache.updateItemState("Light1", "ON")
        assertEquals("ON", cache.get()!![0].item.state)
        assertEquals("OFF", cache.get()!![1].item.state)
    }

    @Test
    fun `updateItemState no-op when item not found`() {
        cache.put(listOf(tileItem(item("Light1", "OFF"))))
        cache.updateItemState("NonExistent", "ON")
        assertEquals("OFF", cache.get()!![0].item.state)
    }

    @Test
    fun `updateItemState no-op when cache is empty`() {
        // Should not throw
        cache.updateItemState("Light1", "ON")
        assertNull(cache.get())
    }

    // --- updateItemState (valueItem) ---

    @Test
    fun `updateItemState updates valueItem state`() {
        val valueItem = item("Sensor1", "CLOSED")
        cache.put(listOf(
            tileItem(item("Gate1", "OFF"), valueItemName = "Sensor1", valueItem = valueItem)
        ))
        cache.updateItemState("Sensor1", "OPEN")
        assertEquals("OPEN", cache.get()!![0].valueItem?.state)
    }

    @Test
    fun `updateItemState on valueItem does not change primary`() {
        val valueItem = item("Sensor1", "CLOSED")
        cache.put(listOf(
            tileItem(item("Gate1", "OFF"), valueItemName = "Sensor1", valueItem = valueItem)
        ))
        cache.updateItemState("Sensor1", "OPEN")
        assertEquals("OFF", cache.get()!![0].item.state)
    }

    @Test
    fun `updateItemState on primary does not change valueItem`() {
        val valueItem = item("Sensor1", "CLOSED")
        cache.put(listOf(
            tileItem(item("Gate1", "OFF"), valueItemName = "Sensor1", valueItem = valueItem)
        ))
        cache.updateItemState("Gate1", "ON")
        assertEquals("ON", cache.get()!![0].item.state)
        assertEquals("CLOSED", cache.get()!![0].valueItem?.state)
    }

    @Test
    fun `updateItemState updates shared valueItem across multiple tileItems`() {
        val sensor = item("Presence", "ON")
        cache.put(listOf(
            tileItem(item("HomeScene", "OFF"), valueItemName = "Presence", valueItem = sensor, slot = 1),
            tileItem(item("AwayScene", "OFF"), valueItemName = "Presence", valueItem = sensor, slot = 2)
        ))
        cache.updateItemState("Presence", "OFF")
        assertEquals("OFF", cache.get()!![0].valueItem?.state)
        assertEquals("OFF", cache.get()!![1].valueItem?.state)
    }

    // --- updateStates (bulk) ---

    @Test
    fun `updateStates updates primary item states from fresh data`() {
        cache.put(listOf(
            tileItem(item("Light1", "OFF"), slot = 1),
            tileItem(item("Light2", "OFF"), slot = 2)
        ))
        val freshItems = listOf(
            tileItem(item("Light1", "ON"), slot = 1),
            tileItem(item("Light2", "ON"), slot = 2)
        )
        cache.updateStates(freshItems)
        assertEquals("ON", cache.get()!![0].item.state)
        assertEquals("ON", cache.get()!![1].item.state)
    }

    @Test
    fun `updateStates updates valueItem states from fresh data`() {
        val sensor = item("Sensor1", "CLOSED")
        cache.put(listOf(
            tileItem(item("Gate1", "OFF"), valueItemName = "Sensor1", valueItem = sensor)
        ))
        val freshSensor = item("Sensor1", "OPEN")
        val freshItems = listOf(
            tileItem(item("Gate1", "OFF"), valueItemName = "Sensor1", valueItem = freshSensor)
        )
        cache.updateStates(freshItems)
        assertEquals("OPEN", cache.get()!![0].valueItem?.state)
    }

    @Test
    fun `updateStates sets statesLoaded to true`() {
        cache.put(listOf(tileItem(item("Light1", "OFF"))))
        assertFalse(cache.statesLoaded)
        cache.updateStates(listOf(tileItem(item("Light1", "ON"))))
        assertTrue(cache.statesLoaded)
    }

    @Test
    fun `updateStates no-op when cache is empty`() {
        cache.updateStates(listOf(tileItem(item("Light1", "ON"))))
        assertNull(cache.get())
    }

    @Test
    fun `updateStates preserves config fields while updating state`() {
        cache.put(listOf(
            TileItem(
                item = item("Light1", "OFF"),
                page = "security", slot = 2,
                icon = "iconify:mdi:gate", label = "My Gate",
                needsConfirmation = true, invertValue = true
            )
        ))
        cache.updateStates(listOf(tileItem(item("Light1", "ON"))))
        val cached = cache.get()!![0]
        assertEquals("ON", cached.item.state)
        // Config preserved
        assertEquals("security", cached.page)
        assertEquals(2, cached.slot)
        assertEquals("iconify:mdi:gate", cached.icon)
        assertEquals("My Gate", cached.label)
        assertTrue(cached.needsConfirmation)
        assertTrue(cached.invertValue)
    }

    @Test
    fun `updateStates handles items not present in fresh data gracefully`() {
        cache.put(listOf(
            tileItem(item("Light1", "OFF"), slot = 1),
            tileItem(item("Light2", "ON"), slot = 2)
        ))
        // Fresh data only has Light1
        cache.updateStates(listOf(tileItem(item("Light1", "ON"))))
        assertEquals("ON", cache.get()!![0].item.state)
        // Light2 keeps its old state
        assertEquals("ON", cache.get()!![1].item.state)
    }
}
