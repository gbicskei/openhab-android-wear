package org.openhab.habdroid.wear.data.repository

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.api.OpenHabApiService
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.MetadataEntry
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.ValueDisplay

class OpenHabRepositoryTest {

    private lateinit var apiService: OpenHabApiService
    private lateinit var credentialStore: CredentialStore
    private lateinit var tilePreferenceStore: TilePreferenceStore
    private lateinit var itemCache: ItemCache
    private lateinit var repository: OpenHabRepository

    private fun itemWithMeta(
        name: String,
        type: String = "Switch",
        state: String = "OFF",
        category: String? = "light",
        config: Map<String, String>
    ) = Item(
        name = name, type = type, state = state, category = category,
        metadata = mapOf("wearTile" to MetadataEntry(value = "tile", config = config))
    )

    @Before
    fun setup() {
        apiService = mockk()
        credentialStore = mockk()
        tilePreferenceStore = mockk()
        itemCache = ItemCache()

        every { tilePreferenceStore.selectedItemNames } returns flowOf(emptyList())

        repository = OpenHabRepository(apiService, credentialStore, tilePreferenceStore, itemCache)
    }

    // --- Metadata parsing ---

    @Test
    fun `parses basic tile item from metadata`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1", "icon" to "light", "valueDisplay" to "color"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals(1, result.size)
        val ti = result[0]
        assertEquals("Light1", ti.item.name)
        assertEquals(TileItem.PAGE_MAIN, ti.page)
        assertEquals(1, ti.slot)
        assertEquals("light", ti.icon)
        assertEquals(ValueDisplay.COLOR, ti.valueDisplay)
    }

    @Test
    fun `parses position with page prefix`() = runTest {
        val items = listOf(
            itemWithMeta("Gate1", config = mapOf("position" to "security:2"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("security", result[0].page)
        assertEquals(2, result[0].slot)
    }

    @Test
    fun `parses float position as int`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "3.0"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals(TileItem.PAGE_MAIN, result[0].page)
        assertEquals(3, result[0].slot)
    }

    @Test
    fun `parses needsConfirmation flag`() = runTest {
        val items = listOf(
            itemWithMeta("Gate1", config = mapOf("position" to "1", "needsConfirmation" to "true"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertTrue(result[0].needsConfirmation)
    }

    @Test
    fun `needsConfirmation defaults to false`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertFalse(result[0].needsConfirmation)
    }

    @Test
    fun `parses invertValue flag`() = runTest {
        val items = listOf(
            itemWithMeta("Gate1", config = mapOf("position" to "1", "invertValue" to "true"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertTrue(result[0].invertValue)
    }

    @Test
    fun `invertValue defaults to false`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertFalse(result[0].invertValue)
    }

    @Test
    fun `parses aggregateState flag`() = runTest {
        val items = listOf(
            itemWithMeta("NavBtn", config = mapOf("position" to "1", "action" to "page:security", "aggregateState" to "true"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertTrue(result[0].aggregateState)
    }

    @Test
    fun `aggregateState defaults to false`() = runTest {
        val items = listOf(
            itemWithMeta("NavBtn", config = mapOf("position" to "1", "action" to "page:security"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertFalse(result[0].aggregateState)
    }

    @Test
    fun `parses action field`() = runTest {
        val items = listOf(
            itemWithMeta("NavBtn", config = mapOf("position" to "1", "action" to "page:security"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("page:security", result[0].action)
        assertTrue(result[0].isPageNavigation)
        assertEquals("security", result[0].targetPage)
    }

    @Test
    fun `parses command action with commandValue`() = runTest {
        val items = listOf(
            itemWithMeta("Gate1", config = mapOf("position" to "1", "action" to "command", "commandValue" to "ON"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertTrue(result[0].isCommand)
        assertEquals("ON", result[0].commandValue)
    }

    @Test
    fun `parses commandItem`() = runTest {
        val items = listOf(
            itemWithMeta("Temp", type = "Number", config = mapOf("position" to "1", "action" to "command", "commandItem" to "Trigger", "commandValue" to "ON"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("Trigger", result[0].commandItemName)
        assertEquals("Trigger", result[0].commandTargetName)
    }

    @Test
    fun `parses label override`() = runTest {
        val items = listOf(
            itemWithMeta("BDR_Light", config = mapOf("position" to "1", "label" to "Bedroom"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("Bedroom", result[0].label)
        assertEquals("Bedroom", result[0].effectiveLabel)
    }

    @Test
    fun `parses icon override`() = runTest {
        val items = listOf(
            itemWithMeta("Gate1", config = mapOf("position" to "1", "icon" to "iconify:mdi:gate"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("iconify:mdi:gate", result[0].icon)
        assertEquals("iconify:mdi:gate", result[0].effectiveIcon)
    }

    @Test
    fun `valueDisplay defaults to VALUE`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals(ValueDisplay.VALUE, result[0].valueDisplay)
    }

    // --- valueItem resolution ---

    @Test
    fun `resolves valueItem from server`() = runTest {
        val gateItem = itemWithMeta("Gate1", config = mapOf("position" to "1", "valueItem" to "GateSensor"))
        val sensorItem = Item(name = "GateSensor", type = "Contact", state = "OPEN")

        coEvery { apiService.getItems(any(), any(), any()) } returns listOf(gateItem)
        coEvery { apiService.getItem("GateSensor") } returns sensorItem

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("GateSensor", result[0].valueItemName)
        assertEquals(sensorItem, result[0].valueItem)
        assertEquals("OPEN", result[0].displayItem.state)
    }

    @Test
    fun `valueItem resolution failure falls back to primary`() = runTest {
        val gateItem = itemWithMeta("Gate1", config = mapOf("position" to "1", "valueItem" to "NonExistent"))

        coEvery { apiService.getItems(any(), any(), any()) } returns listOf(gateItem)
        coEvery { apiService.getItem("NonExistent") } throws RuntimeException("Not found")

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals("NonExistent", result[0].valueItemName)
        assertEquals(null, result[0].valueItem)
        // displayItem falls back to primary
        assertEquals("Gate1", result[0].displayItem.name)
    }

    @Test
    fun `shared valueItem resolved once for multiple items`() = runTest {
        val homeScene = itemWithMeta("HomeScene", config = mapOf("position" to "scenes:1", "valueItem" to "Presence"))
        val awayScene = itemWithMeta("AwayScene", config = mapOf("position" to "scenes:2", "valueItem" to "Presence", "invertValue" to "true"))
        val presence = Item(name = "Presence", type = "Switch", state = "ON")

        coEvery { apiService.getItems(any(), any(), any()) } returns listOf(homeScene, awayScene)
        coEvery { apiService.getItem("Presence") } returns presence

        val result = repository.getAvailableTileItems().getOrThrow()

        // Both items have the same valueItem resolved
        assertEquals("ON", result[0].valueItem?.state)
        assertEquals("ON", result[1].valueItem?.state)
        // But display active differs due to invertValue
        assertTrue(result[0].isDisplayActive)   // HomeScene: ON, no invert = active
        assertFalse(result[1].isDisplayActive)  // AwayScene: ON, invert = inactive
    }

    // --- Sorting ---

    @Test
    fun `items sorted by slot`() = runTest {
        val items = listOf(
            itemWithMeta("C", config = mapOf("position" to "3")),
            itemWithMeta("A", config = mapOf("position" to "1")),
            itemWithMeta("B", config = mapOf("position" to "2"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals(listOf("A", "B", "C"), result.map { it.item.name })
    }

    // --- Caching ---

    @Test
    fun `returns cached items on second call`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val first = repository.getAvailableTileItems().getOrThrow()
        val second = repository.getAvailableTileItems().getOrThrow()

        assertEquals(first, second)
    }

    @Test
    fun `clearAndReload fetches fresh items`() = runTest {
        val items1 = listOf(
            itemWithMeta("Light1", state = "OFF", config = mapOf("position" to "1"))
        )
        val items2 = listOf(
            itemWithMeta("Light1", state = "ON", config = mapOf("position" to "1"))
        )
        coEvery { apiService.getItems(any(), any(), any()) } returnsMany listOf(items1, items2)

        repository.getAvailableTileItems() // caches items1
        repository.clearAndReload()

        val result = repository.getAvailableTileItems().getOrThrow()
        assertEquals("ON", result[0].item.state)
    }

    // --- Items filtered ---

    @Test
    fun `filters out items without wearTile metadata`() = runTest {
        val items = listOf(
            itemWithMeta("Light1", config = mapOf("position" to "1")),
            Item(name = "NoMeta", type = "Switch", state = "ON") // no metadata
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns items

        val result = repository.getAvailableTileItems().getOrThrow()

        assertEquals(1, result.size)
        assertEquals("Light1", result[0].item.name)
    }
}
