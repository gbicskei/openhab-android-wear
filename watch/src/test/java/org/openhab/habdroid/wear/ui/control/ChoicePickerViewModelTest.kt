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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.model.CommandDescription
import org.openhab.habdroid.wear.data.model.CommandOption
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.StateDescription
import org.openhab.habdroid.wear.data.model.StateOption
import org.openhab.habdroid.wear.data.repository.OpenHabRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ChoicePickerViewModelTest {

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

    private fun createViewModel(itemName: String = "Scene"): ChoicePickerViewModel {
        val savedState = SavedStateHandle(mapOf("item_name" to itemName))
        return ChoicePickerViewModel(repository, savedState)
    }

    // --- Loading commandOptions ---

    @Test
    fun `loads commandOptions into option list`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.success(
            Item(
                name = "Scene", label = "Living Scene", type = "String", state = "Evening",
                commandDescription = CommandDescription(
                    commandOptions = listOf(
                        CommandOption(command = "Morning", label = "Morning Light"),
                        CommandOption(command = "Evening", label = "Evening Mood"),
                        CommandOption(command = "Night", label = "Night Mode")
                    )
                )
            )
        )

        val vm = createViewModel()

        assertEquals(3, vm.state.value.options.size)
        assertEquals("Morning", vm.state.value.options[0].command)
        assertEquals("Morning Light", vm.state.value.options[0].label)
        assertEquals("Evening", vm.state.value.currentValue)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    // --- Fallback to stateOptions ---

    @Test
    fun `falls back to stateOptions when no commandOptions`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.success(
            Item(
                name = "Scene", label = "Mode", type = "String", state = "auto",
                commandDescription = CommandDescription(commandOptions = emptyList()),
                stateDescription = StateDescription(
                    options = listOf(
                        StateOption(value = "auto", label = "Automatic"),
                        StateOption(value = "manual", label = "Manual"),
                        StateOption(value = "off", label = "Off")
                    )
                )
            )
        )

        val vm = createViewModel()

        assertEquals(3, vm.state.value.options.size)
        assertEquals("auto", vm.state.value.options[0].command)
        assertEquals("Automatic", vm.state.value.options[0].label)
    }

    // --- Label fallback ---

    @Test
    fun `uses command as label when label is null`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.success(
            Item(
                name = "Scene", type = "String", state = "X",
                commandDescription = CommandDescription(
                    commandOptions = listOf(
                        CommandOption(command = "REFRESH", label = null)
                    )
                )
            )
        )

        val vm = createViewModel()

        assertEquals("REFRESH", vm.state.value.options[0].label)
    }

    // --- Empty options ---

    @Test
    fun `shows error when no options available`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.success(
            Item(name = "Scene", type = "String", state = "X")
        )

        val vm = createViewModel()

        assertEquals(0, vm.state.value.options.size)
        assertEquals("No options available", vm.state.value.error)
    }

    // --- Selection ---

    @Test
    fun `selectOption sends command`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.success(
            Item(
                name = "Scene", type = "String", state = "Morning",
                commandDescription = CommandDescription(
                    commandOptions = listOf(
                        CommandOption(command = "Evening", label = "Evening")
                    )
                )
            )
        )

        val vm = createViewModel()
        vm.selectOption(ChoiceOption(command = "Evening", label = "Evening"))

        coVerify { repository.sendCommand("Scene", "Evening") }
        assertEquals("Evening", vm.state.value.currentValue)
    }

    // --- Error Handling ---

    @Test
    fun `shows error when item fetch fails`() = runTest {
        coEvery { repository.getItem("Scene") } returns Result.failure(RuntimeException("Not found"))

        val vm = createViewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals("Not found", vm.state.value.error)
    }
}
