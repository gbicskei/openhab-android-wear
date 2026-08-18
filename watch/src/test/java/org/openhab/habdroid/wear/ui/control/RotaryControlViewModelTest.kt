package org.openhab.habdroid.wear.ui.control

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.StateDescription
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.ThemeStore

@OptIn(ExperimentalCoroutinesApi::class)
class RotaryControlViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: OpenHabRepository
    private lateinit var themeStore: ThemeStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        themeStore = mockk(relaxed = true)

        coEvery { themeStore.getTheme() } returns mockk {
            every { color } returns 0xFFFFB300.toInt()
            every { name } returns "AMBER"
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(itemName: String = "Dimmer1"): RotaryControlViewModel {
        val savedState = SavedStateHandle(mapOf("item_name" to itemName))
        val complicationRefresher = mockk<org.openhab.habdroid.wear.complication.ComplicationRefresher>(relaxed = true)
        return RotaryControlViewModel(repository, themeStore, complicationRefresher, savedState)
    }

    private fun makeItem(
        name: String = "Dimmer1",
        state: String = "50",
        min: Double = 0.0,
        max: Double = 100.0,
        step: Double = 1.0,
        pattern: String? = "%d%%"
    ) = Item(
        name = name,
        label = "Test Dimmer",
        type = "Dimmer",
        state = state,
        category = "slider",
        stateDescription = StateDescription(
            minimum = min,
            maximum = max,
            step = step,
            pattern = pattern,
            isReadOnly = false,
            options = null
        )
    )

    @Test
    fun `initial state has item name from saved state`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.failure(Exception("Not found"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        assertEquals("Dimmer1", vm.state.value.itemName)
    }

    @Test
    fun `loads live state from server`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "75"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        assertEquals(75.0, vm.state.value.currentValue, 0.01)
        assertEquals(0.0, vm.state.value.min, 0.01)
        assertEquals(100.0, vm.state.value.max, 0.01)
        assertEquals(1.0, vm.state.value.step, 0.01)
    }

    @Test
    fun `onRotate adjusts value within bounds`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "50"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        // Rotate by 180px = 10% of range (180/1800 * 100 = 10)
        vm.onRotate(180f)

        assertEquals(60.0, vm.state.value.currentValue, 0.01)
    }

    @Test
    fun `onRotate clamps at max`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "95"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        // Large rotation that would exceed max
        vm.onRotate(1800f) // full rotation = 100% of range

        assertEquals(100.0, vm.state.value.currentValue, 0.01)
    }

    @Test
    fun `onRotate clamps at min`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "5"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        // Negative rotation that would go below min
        vm.onRotate(-1800f)

        assertEquals(0.0, vm.state.value.currentValue, 0.01)
    }

    @Test
    fun `onRotate snaps to step`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(
            makeItem(state = "50", step = 5.0)
        )

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        // Rotate by small amount (180/1800 * 100 = 10, snaps to nearest 5)
        vm.onRotate(130f) // ~7.2, rounds to nearest step = 55

        assertEquals(55.0, vm.state.value.currentValue, 0.01)
    }

    @Test
    fun `onRotate debounces command sending`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "50"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        vm.onRotate(180f) // moves to 60
        advanceTimeBy(200) // not yet 500ms

        vm.onRotate(180f) // moves to 70
        advanceTimeBy(200) // not yet 500ms

        // First command should have been cancelled
        coVerify(exactly = 0) { repository.sendCommand("Dimmer1", "60") }

        advanceTimeBy(500) // debounce fires

        coVerify(exactly = 1) { repository.sendCommand("Dimmer1", "70") }
    }

    @Test
    fun `displayValue uses pattern when available`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(
            makeItem(state = "75", pattern = "%d%%")
        )

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        assertEquals("75%", vm.state.value.displayValue)
    }

    @Test
    fun `progress returns normalized 0-1 value`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.success(makeItem(state = "25"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        assertEquals(0.25f, vm.state.value.progress, 0.01f)
    }

    @Test
    fun `error state shown when both cache and server fail`() = runTest(testDispatcher) {
        coEvery { repository.getAvailableTileItems() } returns Result.success(emptyList())
        coEvery { repository.getItem("Dimmer1") } returns Result.failure(Exception("Timeout"))

        val vm = createViewModel("Dimmer1")
        advanceUntilIdle()

        assertEquals("Timeout", vm.state.value.error)
    }
}
