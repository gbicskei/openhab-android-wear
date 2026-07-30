package org.openhab.habdroid.wear.ui.setup

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: CredentialStore
    private lateinit var repository: OpenHabRepository
    private lateinit var credentialsFlow: MutableStateFlow<ServerCredentials?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        credentialStore = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        credentialsFlow = MutableStateFlow(null)
        every { credentialStore.credentials } returns credentialsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SetupViewModel(credentialStore, repository)

    @Test
    fun `initial state defaults to myopenhab url`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("https://myopenhab.org", state.serverUrl)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }

    @Test
    fun `prefills from stored credentials on first load`() = runTest(testDispatcher) {
        val stored = ServerCredentials(
            serverUrl = "https://myopenhab.org",
            username = "user@test.com",
            password = "secret"
        )
        credentialsFlow.value = stored

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("https://myopenhab.org", state.serverUrl)
        assertEquals("user@test.com", state.username)
        assertEquals("secret", state.password)
    }

    @Test
    fun `does not overwrite user edits when credentials flow emits again`() = runTest(testDispatcher) {
        // Start with a stored bad URL
        val badCredentials = ServerCredentials(
            serverUrl = "https://myopenhub.org",
            username = "user@test.com",
            password = "secret"
        )
        credentialsFlow.value = badCredentials

        val viewModel = createViewModel()
        advanceUntilIdle()

        // User corrects the URL
        viewModel.updateServerUrl("https://myopenhab.org")
        advanceUntilIdle()

        // Simulate DataStore emitting again (e.g. after save)
        credentialsFlow.value = badCredentials
        advanceUntilIdle()

        // The corrected URL should NOT be overwritten
        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("https://myopenhab.org", state.serverUrl)
    }

    @Test
    fun `does not overwrite after reset and retry`() = runTest(testDispatcher) {
        val badCredentials = ServerCredentials(
            serverUrl = "https://myopenhub.org",
            username = "user@test.com",
            password = "secret"
        )
        credentialsFlow.value = badCredentials

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Simulate failed connection -> error -> reset
        coEvery { repository.getAllItems() } returns Result.failure(Exception("Connection failed"))
        viewModel.saveManualCredentials()
        advanceUntilIdle()

        viewModel.reset()
        advanceUntilIdle()

        // User enters correct URL after reset
        viewModel.updateServerUrl("https://myopenhab.org")
        advanceUntilIdle()

        // Credentials flow emits again — should NOT overwrite
        credentialsFlow.value = badCredentials
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("https://myopenhab.org", state.serverUrl)
    }

    @Test
    fun `updateServerUrl updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("http://192.168.1.100:8080")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("http://192.168.1.100:8080", state.serverUrl)
    }

    @Test
    fun `updateUsername updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateUsername("newuser@email.com")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("newuser@email.com", state.username)
    }

    @Test
    fun `updatePassword updates state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePassword("newpass123")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("newpass123", state.password)
    }

    @Test
    fun `saveManualCredentials transitions to Success on successful connection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user@test.com")
        viewModel.updatePassword("password")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.Success)
    }

    @Test
    fun `saveManualCredentials transitions to Error on failed connection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user@test.com")
        viewModel.updatePassword("password")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.failure(Exception("timeout"))

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SetupUiState.Error)
        assertTrue((state as SetupUiState.Error).message.contains("timeout"))
    }

    @Test
    fun `saveManualCredentials saves credentials to store`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user@test.com")
        viewModel.updatePassword("pass123")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        coVerify {
            credentialStore.saveCredentials(
                ServerCredentials(
                    serverUrl = "https://myopenhab.org",
                    username = "user@test.com",
                    password = "pass123"
                )
            )
        }
    }

    @Test
    fun `saveManualCredentials trims serverUrl and username`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("  https://myopenhab.org  ")
        viewModel.updateUsername("  user@test.com  ")
        viewModel.updatePassword("pass123")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        coVerify {
            credentialStore.saveCredentials(
                ServerCredentials(
                    serverUrl = "https://myopenhab.org",
                    username = "user@test.com",
                    password = "pass123"
                )
            )
        }
    }

    @Test
    fun `reset returns to ManualEntry with defaults`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://other.server.com")
        viewModel.updateUsername("user")
        viewModel.updatePassword("pass")
        advanceUntilIdle()

        viewModel.reset()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("https://myopenhab.org", state.serverUrl)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }
}
