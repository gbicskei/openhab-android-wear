package org.openhab.habdroid.wear.ui

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import androidx.wear.tiles.TileService

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: CredentialStore
    private lateinit var repository: OpenHabRepository
    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        credentialStore = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { credentialStore.isConfigured } returns flowOf(true)
        every { credentialStore.credentials } returns flowOf(
            ServerCredentials(serverUrl = "http://test", username = "u", password = "p", userKey = "mykey")
        )
        every { repository.lastConfigVersion } returns 0

        // Mock TileService.getUpdater static call
        mockkStatic(TileService::class)
        val updater = mockk<androidx.wear.tiles.TileUpdateRequester>(relaxed = true)
        every { TileService.getUpdater(any()) } returns updater
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(TileService::class)
    }

    private fun createViewModel() = MainViewModel(credentialStore, repository, context)

    @Test
    fun `initial reloadState is Idle`() {
        val vm = createViewModel()
        assertEquals(ReloadState.Idle, vm.reloadState.value)
    }

    @Test
    fun `reloadItems transitions to Loading then Success`() = runTest(testDispatcher) {
        coEvery { repository.clearAndReload() } returns Result.success(5)
        every { repository.lastConfigVersion } returns 42

        val vm = createViewModel()
        vm.reloadItems()
        advanceUntilIdle()

        val state = vm.reloadState.value
        assertTrue(state is ReloadState.Success)
        assertEquals(5, (state as ReloadState.Success).count)
        assertEquals(42, vm.configVersion.value)
    }

    @Test
    fun `reloadItems failure shows Error`() = runTest(testDispatcher) {
        coEvery { repository.clearAndReload() } returns Result.failure(Exception("Server unreachable"))

        val vm = createViewModel()
        vm.reloadItems()
        advanceUntilIdle()

        val state = vm.reloadState.value
        assertTrue(state is ReloadState.Error)
        assertEquals("Server unreachable", (state as ReloadState.Error).message)
    }

    @Test
    fun `clearReloadState resets to Idle`() = runTest(testDispatcher) {
        coEvery { repository.clearAndReload() } returns Result.success(3)

        val vm = createViewModel()
        vm.reloadItems()
        advanceUntilIdle()

        vm.clearReloadState()

        assertEquals(ReloadState.Idle, vm.reloadState.value)
    }
}
