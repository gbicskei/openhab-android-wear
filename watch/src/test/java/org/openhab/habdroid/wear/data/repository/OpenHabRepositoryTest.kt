package org.openhab.habdroid.wear.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.api.OpenHabApiService
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.model.TilePageConfig
import org.openhab.habdroid.wear.data.model.TileSlotComponent
import org.openhab.habdroid.wear.data.model.TileSlotConfig
import org.openhab.habdroid.wear.data.model.TileSlots
import org.openhab.habdroid.wear.data.model.WearTileComponent
import org.openhab.habdroid.wear.shared.model.ServerCredentials

class OpenHabRepositoryTest {

    private lateinit var apiService: OpenHabApiService
    private lateinit var credentialStore: CredentialStore
    private lateinit var tilePreferenceStore: TilePreferenceStore
    private lateinit var itemCache: ItemCache
    private lateinit var diskCache: TileConfigDiskCache
    private lateinit var watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter
    private lateinit var themeStore: ThemeStore
    private lateinit var repository: OpenHabRepository

    private val testCredentials = ServerCredentials(
        serverUrl = "https://myopenhab.org",
        username = "user",
        password = "pass"
    )

    @Before
    fun setup() {
        apiService = mockk(relaxed = true)
        credentialStore = mockk()
        tilePreferenceStore = mockk(relaxed = true)
        diskCache = mockk(relaxed = true)
        watchStatusWriter = mockk(relaxed = true)
        themeStore = mockk(relaxed = true)

        // Use real ItemCache with mocked disk cache
        itemCache = ItemCache(diskCache)

        every { credentialStore.credentials } returns flowOf(testCredentials)
        every { diskCache.getStoredConfigVersion() } returns -1
        every { diskCache.load() } returns null
        coEvery { themeStore.getTheme() } returns TileTheme.AMBER

        repository = OpenHabRepository(
            apiService = apiService,
            credentialStore = credentialStore,
            tilePreferenceStore = tilePreferenceStore,
            itemCache = itemCache,
            diskCache = diskCache,
            watchStatusWriter = watchStatusWriter,
            themeStore = themeStore
        )
    }

    private fun makeComponent(
        uid: String = "main",
        configVersion: Double = 5.0,
        layout: Double = 4.0,
        slots: List<TileSlotComponent> = emptyList()
    ) = WearTileComponent(
        uid = uid,
        component = "wear:tile-page",
        config = TilePageConfig(label = uid.replaceFirstChar { it.uppercase() }, layout = layout, configVersion = configVersion),
        slots = TileSlots(default = slots)
    )

    private fun makeSlot(item: String, position: Double = 1.0) = TileSlotComponent(
        config = TileSlotConfig(position = position, item = item)
    )

    private fun makeItem(name: String, state: String = "ON") = Item(
        name = name, label = name, type = "Switch", state = state, category = "light"
    )

    // --- coldLoad: configVersion gating ---

    @Test
    fun `coldLoad uses disk cache when configVersion matches`() = runTest {
        val cachedItems = listOf(
            TileItem(item = makeItem("CachedItem"), page = "main", slot = 1)
        )

        // Server returns configVersion 5
        coEvery { apiService.getTileComponents(any()) } returns listOf(
            makeComponent(configVersion = 5.0, slots = listOf(makeSlot("CachedItem")))
        )

        // Disk cache has version 5
        every { diskCache.getStoredConfigVersion() } returns 5
        every { diskCache.load() } returns cachedItems

        val result = repository.clearAndReload()

        assertTrue(result.isSuccess)
        // Should NOT have fetched individual items from API
        coVerify(exactly = 0) { apiService.getItem(any()) }
    }

    @Test
    fun `coldLoad fetches items when configVersion differs`() = runTest {
        // Server returns configVersion 6
        coEvery { apiService.getTileComponents(any()) } returns listOf(
            makeComponent(configVersion = 6.0, slots = listOf(makeSlot("Light1"), makeSlot("Light2", 2.0)))
        )

        // Disk cache has version 5 (stale)
        every { diskCache.getStoredConfigVersion() } returns 5
        every { diskCache.load() } returns null

        // API returns items
        coEvery { apiService.getItem("Light1") } returns makeItem("Light1", "ON")
        coEvery { apiService.getItem("Light2") } returns makeItem("Light2", "OFF")

        val result = repository.clearAndReload()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        // Should have fetched both items
        coVerify { apiService.getItem("Light1") }
        coVerify { apiService.getItem("Light2") }
    }

    @Test
    fun `coldLoad saves to disk with configVersion after fetch`() = runTest {
        coEvery { apiService.getTileComponents(any()) } returns listOf(
            makeComponent(configVersion = 10.0, slots = listOf(makeSlot("Item1")))
        )
        every { diskCache.getStoredConfigVersion() } returns -1
        coEvery { apiService.getItem("Item1") } returns makeItem("Item1")

        repository.clearAndReload()

        verify { diskCache.save(any<List<TileItem>>(), 10) }
    }

    // --- coldLoad: unknown item filtering ---

    @Test
    fun `coldLoad does not fetch item named unknown`() = runTest {
        // A navigation slot has no item → defaults to "unknown" in TileItem
        coEvery { apiService.getTileComponents(any()) } returns listOf(
            makeComponent(configVersion = 1.0, slots = listOf(
                makeSlot("Light1"),
                TileSlotComponent(config = TileSlotConfig(position = 2.0, item = null, action = "page:security"))
            ))
        )
        every { diskCache.getStoredConfigVersion() } returns -1
        coEvery { apiService.getItem("Light1") } returns makeItem("Light1")

        repository.clearAndReload()

        coVerify { apiService.getItem("Light1") }
        coVerify(exactly = 0) { apiService.getItem("unknown") }
    }

    // --- refreshStates: parallel fetch ---

    @Test
    fun `refreshStates fetches only needed item names`() = runTest {
        // Prime the cache with items
        val items = listOf(
            TileItem(item = makeItem("Switch1"), page = "main", slot = 1, valueItemName = "Sensor1"),
            TileItem(item = makeItem("Switch2"), page = "main", slot = 2, doubleTapItem = "Setpoint1")
        )
        itemCache.put(items)

        coEvery { apiService.getItem("Switch1") } returns makeItem("Switch1", "OFF")
        coEvery { apiService.getItem("Switch2") } returns makeItem("Switch2", "ON")
        coEvery { apiService.getItem("Sensor1") } returns makeItem("Sensor1", "OPEN")
        coEvery { apiService.getItem("Setpoint1") } returns makeItem("Setpoint1", "22")

        val result = repository.refreshStates()

        assertTrue(result.isSuccess)
        coVerify { apiService.getItem("Switch1") }
        coVerify { apiService.getItem("Switch2") }
        coVerify { apiService.getItem("Sensor1") }
        coVerify { apiService.getItem("Setpoint1") }
    }

    @Test
    fun `refreshStates filters out unknown from neededNames`() = runTest {
        // A nav-only TileItem has item.name = "unknown"
        val items = listOf(
            TileItem(item = Item(name = "unknown", label = "Nav"), page = "main", slot = 1),
            TileItem(item = makeItem("RealItem"), page = "main", slot = 2)
        )
        itemCache.put(items)

        coEvery { apiService.getItem("RealItem") } returns makeItem("RealItem", "OFF")

        repository.refreshStates()

        coVerify(exactly = 0) { apiService.getItem("unknown") }
        coVerify { apiService.getItem("RealItem") }
    }

    @Test
    fun `refreshStates updates cached item states`() = runTest {
        val items = listOf(
            TileItem(item = makeItem("Light1", "OFF"), page = "main", slot = 1)
        )
        itemCache.put(items)

        coEvery { apiService.getItem("Light1") } returns makeItem("Light1", "ON")

        repository.refreshStates()

        val cached = itemCache.get()!!
        assertEquals("ON", cached[0].item.state)
    }

    @Test
    fun `refreshStates handles partial failures gracefully`() = runTest {
        val items = listOf(
            TileItem(item = makeItem("Good"), page = "main", slot = 1),
            TileItem(item = makeItem("Bad"), page = "main", slot = 2)
        )
        itemCache.put(items)

        coEvery { apiService.getItem("Good") } returns makeItem("Good", "OFF")
        coEvery { apiService.getItem("Bad") } throws RuntimeException("EOF")

        val result = repository.refreshStates()

        // Should succeed despite partial failure
        assertTrue(result.isSuccess)
        val cached = itemCache.get()!!
        assertEquals("OFF", cached[0].item.state)
        // Bad item retains old state
        assertEquals("ON", cached[1].item.state)
    }

    @Test
    fun `refreshStates stores doubleTap item states in extra map`() = runTest {
        val items = listOf(
            TileItem(item = makeItem("AC_Power"), page = "main", slot = 1, doubleTapItem = "AC_Setpoint")
        )
        itemCache.put(items)

        coEvery { apiService.getItem("AC_Power") } returns makeItem("AC_Power", "ON")
        coEvery { apiService.getItem("AC_Setpoint") } returns makeItem("AC_Setpoint", "24")

        repository.refreshStates()

        assertEquals("24", itemCache.getExtraItemState("AC_Setpoint"))
    }
}
