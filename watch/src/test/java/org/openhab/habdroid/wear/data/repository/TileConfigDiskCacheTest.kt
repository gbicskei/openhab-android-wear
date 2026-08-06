package org.openhab.habdroid.wear.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TileConfigDiskCacheTest {

    private lateinit var diskCache: TileConfigDiskCache
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        diskCache = TileConfigDiskCache(context, json)
        diskCache.clear()
    }

    private fun makeItem(name: String = "TestItem", state: String = "ON") = Item(
        name = name, label = "Test", type = "Switch", state = state, category = "light"
    )

    private fun makeTileItem(
        name: String = "TestItem",
        state: String = "ON",
        doubleTapItem: String? = null,
        doubleTapAction: String? = null,
        doubleTapCommand: String? = null,
        doubleTapConfirmation: Boolean = false,
        doubleTapStateDisplay: ValueDisplay = ValueDisplay.NONE
    ) = TileItem(
        item = makeItem(name, state),
        page = "main",
        slot = 1,
        doubleTapItem = doubleTapItem,
        doubleTapAction = doubleTapAction,
        doubleTapCommand = doubleTapCommand,
        doubleTapConfirmation = doubleTapConfirmation,
        doubleTapStateDisplay = doubleTapStateDisplay
    )

    // --- Basic save/load ---

    @Test
    fun `save and load round-trips tile items`() {
        val items = listOf(
            makeTileItem("Item1", "ON"),
            makeTileItem("Item2", "OFF")
        )
        diskCache.save(items, 5)

        val loaded = diskCache.load()
        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals("Item1", loaded[0].item.name)
        assertEquals("ON", loaded[0].item.state)
        assertEquals("Item2", loaded[1].item.name)
        assertEquals("OFF", loaded[1].item.state)
    }

    @Test
    fun `load returns null when no cache exists`() {
        assertNull(diskCache.load())
    }

    @Test
    fun `clear removes cache`() {
        diskCache.save(listOf(makeTileItem()), 1)
        assertNotNull(diskCache.load())

        diskCache.clear()
        assertNull(diskCache.load())
    }

    // --- configVersion tracking ---

    @Test
    fun `getStoredConfigVersion returns saved version`() {
        diskCache.save(listOf(makeTileItem()), 42)
        assertEquals(42, diskCache.getStoredConfigVersion())
    }

    @Test
    fun `getStoredConfigVersion returns -1 when no version stored`() {
        assertEquals(-1, diskCache.getStoredConfigVersion())
    }

    @Test
    fun `clear removes configVersion`() {
        diskCache.save(listOf(makeTileItem()), 10)
        assertEquals(10, diskCache.getStoredConfigVersion())

        diskCache.clear()
        assertEquals(-1, diskCache.getStoredConfigVersion())
    }

    @Test
    fun `save without configVersion does not update version file`() {
        diskCache.save(listOf(makeTileItem()), 7)
        assertEquals(7, diskCache.getStoredConfigVersion())

        // Save without version (old overload)
        diskCache.save(listOf(makeTileItem("Other")))
        // Version file should remain from previous save
        assertEquals(7, diskCache.getStoredConfigVersion())
    }

    // --- doubleTap fields persistence ---

    @Test
    fun `doubleTapItem is persisted and loaded`() {
        val item = makeTileItem(doubleTapItem = "AC_Setpoint")
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertEquals("AC_Setpoint", loaded[0].doubleTapItem)
    }

    @Test
    fun `doubleTapAction is persisted and loaded`() {
        val item = makeTileItem(doubleTapItem = "X", doubleTapAction = "toggle")
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertEquals("toggle", loaded[0].doubleTapAction)
    }

    @Test
    fun `doubleTapCommand is persisted and loaded`() {
        val item = makeTileItem(doubleTapItem = "X", doubleTapAction = "command", doubleTapCommand = "BOOST")
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertEquals("BOOST", loaded[0].doubleTapCommand)
    }

    @Test
    fun `doubleTapConfirmation is persisted and loaded`() {
        val item = makeTileItem(doubleTapItem = "X", doubleTapConfirmation = true)
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertEquals(true, loaded[0].doubleTapConfirmation)
    }

    @Test
    fun `doubleTapStateDisplay is persisted and loaded`() {
        val item = makeTileItem(doubleTapItem = "X", doubleTapStateDisplay = ValueDisplay.VALUE)
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertEquals(ValueDisplay.VALUE, loaded[0].doubleTapStateDisplay)
    }

    @Test
    fun `null doubleTapItem round-trips as null`() {
        val item = makeTileItem(doubleTapItem = null)
        diskCache.save(listOf(item), 1)

        val loaded = diskCache.load()!!
        assertNull(loaded[0].doubleTapItem)
    }
}
