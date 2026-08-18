package org.openhab.habdroid.wear.ui.control

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RollerShutterViewModelTest {

    private lateinit var repository: OpenHabRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(itemName: String = "Shutter"): RollerShutterViewModel {
        val savedState = SavedStateHandle(mapOf("item_name" to itemName))
        val themeStore = mockk<org.openhab.habdroid.wear.data.repository.ThemeStore>(relaxed = true)
        val complicationRefresher = mockk<org.openhab.habdroid.wear.complication.ComplicationRefresher>(relaxed = true)
        return RollerShutterViewModel(repository, themeStore, complicationRefresher, savedState)
    }

    // --- State Loading ---

    @Test
    fun `loads current position from item state`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", label = "Living Room Blinds", type = "Rollershutter", state = "75")
        )

        val vm = createViewModel()

        assertEquals(75f, vm.state.value.position, 0.1f)
        assertEquals("Living Room Blinds", vm.state.value.label)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `position 0 shows OPEN`() {
        val state = RollerShutterState(position = 0f)
        assertEquals("OPEN", state.positionDisplay)
    }

    @Test
    fun `position 100 shows CLOSED`() {
        val state = RollerShutterState(position = 100f)
        assertEquals("CLOSED", state.positionDisplay)
    }

    @Test
    fun `position 42 shows percentage`() {
        val state = RollerShutterState(position = 42f)
        assertEquals("42%", state.positionDisplay)
    }

    // --- Commands ---

    @Test
    fun `sendUp sends UP command`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "50")
        )

        val vm = createViewModel()
        vm.sendUp()

        coVerify { repository.sendCommand("Shutter", "UP") }
    }

    @Test
    fun `sendDown sends DOWN command`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "50")
        )

        val vm = createViewModel()
        vm.sendDown()

        coVerify { repository.sendCommand("Shutter", "DOWN") }
    }

    @Test
    fun `sendStop sends STOP command`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "50")
        )

        val vm = createViewModel()
        vm.sendStop()

        coVerify { repository.sendCommand("Shutter", "STOP") }
    }

    // --- Bezel Rotation ---

    @Test
    fun `onRotatePosition increases position`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "50")
        )

        val vm = createViewModel()
        vm.onRotatePosition(60f) // (60/1800)*100 = 3.33 added → 53.33

        assertEquals(53.3f, vm.state.value.position, 1f)
    }

    @Test
    fun `onRotatePosition clamps at 100`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "99")
        )

        val vm = createViewModel()
        vm.onRotatePosition(300f)

        assertEquals(100f, vm.state.value.position, 0.1f)
    }

    @Test
    fun `onRotatePosition clamps at 0`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.success(
            Item(name = "Shutter", type = "Rollershutter", state = "1")
        )

        val vm = createViewModel()
        vm.onRotatePosition(-300f)

        assertEquals(0f, vm.state.value.position, 0.1f)
    }

    // --- Error Handling ---

    @Test
    fun `shows error when item fetch fails`() = runTest {
        coEvery { repository.getItem("Shutter") } returns Result.failure(RuntimeException("Timeout"))

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals("Timeout", vm.state.value.error)
    }
}
