package org.openhab.habdroid.wear.data.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay

class ItemCacheTest {

    private lateinit var diskCache: TileConfigDiskCache
    private lateinit var itemCache: ItemCache

    @Before
    fun setup() {
        diskCache = mockk(relaxed = true)
        every { diskCache.load() } returns null
        itemCache = ItemCache(diskCache)
    }

    private fun makeItem(name: String, state: String = "ON", isGroup: Boolean = false, members: List<Item>? = null) = Item(
        name = name,
        label = name,
        type = if (isGroup) "Group" else "Switch",
        state = state,
        members = members
    )

    private fun makeTileItem(
        name: String,
        state: String = "ON",
        valueItemName: String? = null,
        valueItem: Item? = null,
        doubleTapItem: String? = null,
        isGroup: Boolean = false,
        members: List<Item>? = null
    ) = TileItem(
        item = makeItem(name, state, isGroup, members),
        page = "main",
        slot = 1,
        valueItemName = valueItemName,
        valueItem = valueItem,
        doubleTapItem = doubleTapItem
    )

    // --- updateItemState: primary item ---

    @Test
    fun `updateItemState updates primary item state`() {
        val items = listOf(makeTileItem("Light1", "OFF"))
        itemCache.put(items)

        itemCache.updateItemState("Light1", "ON")

        val updated = itemCache.get()!!
        assertEquals("ON", updated[0].item.state)
    }

    // --- updateItemState: valueItem ---

    @Test
    fun `updateItemState updates valueItem state`() {
        val valueItem = makeItem("Sensor1", "CLOSED")
        val items = listOf(makeTileItem("Gate1", valueItemName = "Sensor1", valueItem = valueItem))
        itemCache.put(items)

        itemCache.updateItemState("Sensor1", "OPEN")

        val updated = itemCache.get()!!
        assertEquals("OPEN", updated[0].valueItem?.state)
    }

    // --- updateItemState: Group member ---

    @Test
    fun `updateItemState updates Group member state`() {
        val members = listOf(makeItem("Member1", "OFF"), makeItem("Member2", "OFF"))
        val items = listOf(makeTileItem("GroupItem", state = "NULL", isGroup = true, members = members))
        itemCache.put(items)

        itemCache.updateItemState("Member1", "ON")

        val updated = itemCache.get()!!
        val updatedMembers = updated[0].item.members!!
        assertEquals("ON", updatedMembers.find { it.name == "Member1" }?.state)
        assertEquals("OFF", updatedMembers.find { it.name == "Member2" }?.state)
    }

    // --- updateItemState: extra states (doubleTap items) ---

    @Test
    fun `updateItemState stores in extraItemStates for doubleTap lookup`() {
        val items = listOf(makeTileItem("AC_Power", doubleTapItem = "AC_Setpoint"))
        itemCache.put(items)

        itemCache.updateItemState("AC_Setpoint", "22.5")

        assertEquals("22.5", itemCache.getExtraItemState("AC_Setpoint"))
    }

    @Test
    fun `updateItemState stores all items in extra map regardless of match`() {
        val items = listOf(makeTileItem("Light1"))
        itemCache.put(items)

        itemCache.updateItemState("UnrelatedItem", "50")

        assertEquals("50", itemCache.getExtraItemState("UnrelatedItem"))
    }

    // --- putExtraItemStates / putExtraItems ---

    @Test
    fun `putExtraItemStates stores batch of states`() {
        itemCache.putExtraItemStates(mapOf("A" to "1", "B" to "2"))
        assertEquals("1", itemCache.getExtraItemState("A"))
        assertEquals("2", itemCache.getExtraItemState("B"))
    }

    @Test
    fun `putExtraItems stores full item objects`() {
        val item = makeItem("DoubleTapTarget", "25")
        itemCache.putExtraItems(mapOf("DoubleTapTarget" to item))
        assertEquals(item, itemCache.getExtraItem("DoubleTapTarget"))
    }

    @Test
    fun `getExtraItem returns null for unknown item`() {
        assertNull(itemCache.getExtraItem("NonExistent"))
    }

    // --- statesLoaded flag ---

    @Test
    fun `statesLoaded is false initially`() {
        assertFalse(itemCache.statesLoaded)
    }

    @Test
    fun `putStates sets statesLoaded to true`() {
        itemCache.putStates(listOf(makeTileItem("X")))
        assertTrue(itemCache.statesLoaded)
    }

    @Test
    fun `invalidateStates resets statesLoaded`() {
        itemCache.putStates(listOf(makeTileItem("X")))
        assertTrue(itemCache.statesLoaded)

        itemCache.invalidateStates()
        assertFalse(itemCache.statesLoaded)
    }

    // --- clear ---

    @Test
    fun `clear removes memory cache and calls diskCache clear`() {
        itemCache.put(listOf(makeTileItem("X")))
        assertNotNull(itemCache.get())

        itemCache.clear()
        // Memory is cleared — disk returns null
        assertNull(itemCache.get())
        verify { diskCache.clear() }
    }

    // --- warm start from disk ---

    @Test
    fun `get loads from disk when memory is empty`() {
        val diskItems = listOf(makeTileItem("FromDisk"))
        every { diskCache.load() } returns diskItems

        val result = itemCache.get()
        assertNotNull(result)
        assertEquals("FromDisk", result!![0].item.name)
    }

    @Test
    fun `get returns memory cache without hitting disk`() {
        val memItems = listOf(makeTileItem("FromMemory"))
        itemCache.put(memItems)

        // Even if disk has different data, memory wins
        every { diskCache.load() } returns listOf(makeTileItem("FromDisk"))

        val result = itemCache.get()
        assertEquals("FromMemory", result!![0].item.name)
    }
}
