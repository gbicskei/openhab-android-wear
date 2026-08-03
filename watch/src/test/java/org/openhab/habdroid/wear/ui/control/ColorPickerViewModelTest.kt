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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ColorPickerViewModelTest {

    private lateinit var repository: OpenHabRepository
    private lateinit var viewModel: ColorPickerViewModel
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

    private fun createViewModel(itemName: String = "TestColor"): ColorPickerViewModel {
        val savedState = SavedStateHandle(mapOf("item_name" to itemName))
        return ColorPickerViewModel(repository, savedState)
    }

    // --- HSB State Parsing ---

    @Test
    fun `parses HSB state correctly`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", label = "RGB Light", type = "Color", state = "120,80,50")
        )

        viewModel = createViewModel("Light")

        val state = viewModel.state.value
        assertEquals(120f, state.hue, 0.1f)
        assertEquals(80f, state.saturation, 0.1f)
        assertEquals(50f, state.brightness, 0.1f)
        assertFalse(state.isLoading)
    }

    @Test
    fun `parses ON state as full brightness`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "ON")
        )

        viewModel = createViewModel("Light")

        assertEquals(0f, viewModel.state.value.hue, 0.1f)
        assertEquals(0f, viewModel.state.value.saturation, 0.1f)
        assertEquals(100f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `parses OFF state as zero brightness`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "OFF")
        )

        viewModel = createViewModel("Light")

        assertEquals(0f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `parses NULL state as zero`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "NULL")
        )

        viewModel = createViewModel("Light")

        assertEquals(0f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `parses percentage as brightness`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "75")
        )

        viewModel = createViewModel("Light")

        assertEquals(75f, viewModel.state.value.brightness, 0.1f)
    }

    // --- Preset Selection ---

    @Test
    fun `selectPreset updates hue and saturation`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,80")
        )

        viewModel = createViewModel("Light")
        viewModel.selectPreset(PresetColor("Green", 120f, 100f))

        assertEquals(120f, viewModel.state.value.hue, 0.1f)
        assertEquals(100f, viewModel.state.value.saturation, 0.1f)
        assertEquals(80f, viewModel.state.value.brightness, 0.1f) // preserved
    }

    // --- Brightness Rotation ---

    @Test
    fun `onRotateBrightness increases brightness`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,50")
        )

        viewModel = createViewModel("Light")
        viewModel.onRotateBrightness(60f) // delta / 30 = 2

        assertTrue(viewModel.state.value.brightness > 50f)
    }

    @Test
    fun `onRotateBrightness clamps at 100`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,99")
        )

        viewModel = createViewModel("Light")
        viewModel.onRotateBrightness(300f)

        assertEquals(100f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `onRotateBrightness clamps at 0`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,1")
        )

        viewModel = createViewModel("Light")
        viewModel.onRotateBrightness(-300f)

        assertEquals(0f, viewModel.state.value.brightness, 0.1f)
    }

    // --- Toggle ---

    @Test
    fun `toggleOnOff sets brightness to 0 when on`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,80")
        )

        viewModel = createViewModel("Light")
        viewModel.toggleOnOff()

        assertEquals(0f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `toggleOnOff sets brightness to 100 when off`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "0,100,0")
        )

        viewModel = createViewModel("Light")
        viewModel.toggleOnOff()

        assertEquals(100f, viewModel.state.value.brightness, 0.1f)
    }

    @Test
    fun `toggleOnOff sends command immediately`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.success(
            Item(name = "Light", type = "Color", state = "120,80,50")
        )

        viewModel = createViewModel("Light")
        viewModel.toggleOnOff()

        coVerify { repository.sendCommand("Light", "120,80,0") }
    }

    // --- HSB Command Format ---

    @Test
    fun `hsbCommand formats correctly`() {
        val state = ColorPickerState(hue = 120f, saturation = 80f, brightness = 50f)
        assertEquals("120,80,50", state.hsbCommand)
    }

    @Test
    fun `hsbCommand rounds floats to int`() {
        val state = ColorPickerState(hue = 120.7f, saturation = 80.3f, brightness = 50.9f)
        assertEquals("120,80,50", state.hsbCommand)
    }

    // --- Error Handling ---

    @Test
    fun `shows error when item fetch fails`() = runTest {
        coEvery { repository.getItem("Light") } returns Result.failure(RuntimeException("Network error"))

        viewModel = createViewModel("Light")

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Network error", viewModel.state.value.error)
    }

    // --- isOn ---

    @Test
    fun `isOn true when brightness above 0`() {
        assertTrue(ColorPickerState(brightness = 1f).isOn)
        assertTrue(ColorPickerState(brightness = 100f).isOn)
    }

    @Test
    fun `isOn false when brightness is 0`() {
        assertFalse(ColorPickerState(brightness = 0f).isOn)
    }
}
