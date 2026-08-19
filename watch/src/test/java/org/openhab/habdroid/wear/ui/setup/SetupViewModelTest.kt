package org.openhab.habdroid.wear.ui.setup

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.openhab.habdroid.wear.data.api.ServerSelector
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.shared.model.ServerCredentials

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: CredentialStore
    private lateinit var serverSelector: ServerSelector
    private lateinit var repository: OpenHabRepository
    private lateinit var credentialsFlow: MutableStateFlow<ServerCredentials?>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        credentialStore = mockk(relaxed = true)
        serverSelector = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        credentialsFlow = MutableStateFlow(null)
        every { credentialStore.credentials } returns credentialsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SetupViewModel(credentialStore, serverSelector, repository)

    // ─── Initial State ───

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
    fun `initial state is ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.ManualEntry)
    }

    // ─── Credential Prefill ───

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
    fun `does not prefill if username is already set`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // User types a username before credentials arrive
        viewModel.updateUsername("manual@user.com")
        advanceUntilIdle()

        // Now credentials arrive from store
        credentialsFlow.value = ServerCredentials(
            serverUrl = "https://other.server.com",
            username = "stored@user.com",
            password = "storedpass"
        )
        advanceUntilIdle()

        // Should NOT overwrite the manual entry
        val state = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("manual@user.com", state.username)
    }

    @Test
    fun `does not overwrite user edits when credentials flow emits again`() = runTest(testDispatcher) {
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
    fun `prefills only once even if credentials flow emits multiple times`() = runTest(testDispatcher) {
        credentialsFlow.value = ServerCredentials(
            serverUrl = "https://first.org",
            username = "first",
            password = "pass1"
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify first prefill worked
        val state1 = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("first", state1.username)

        // Emit different credentials
        credentialsFlow.value = ServerCredentials(
            serverUrl = "https://second.org",
            username = "second",
            password = "pass2"
        )
        advanceUntilIdle()

        // Should still show first prefill
        val state2 = viewModel.uiState.value as SetupUiState.ManualEntry
        assertEquals("first", state2.username)
    }

    // ─── Field Updates ───

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
    fun `updateServerUrl does nothing when state is not ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Transition to Success
        coEvery { repository.getAllItems() } returns Result.success(emptyList())
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Success)

        // Try updating — should be ignored
        viewModel.updateServerUrl("http://other.com")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.Success)
    }

    @Test
    fun `updateUsername does nothing when state is not ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Success)

        viewModel.updateUsername("ignored")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.Success)
    }

    @Test
    fun `updatePassword does nothing when state is not ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Success)

        viewModel.updatePassword("ignored")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.Success)
    }

    // ─── Save Credentials ───

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
    fun `saveManualCredentials does not trim password`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user")
        viewModel.updatePassword("  pass with spaces  ")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        coVerify {
            credentialStore.saveCredentials(
                match { it.password == "  pass with spaces  " }
            )
        }
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
    fun `saveManualCredentials error message includes Connection failed prefix`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.failure(Exception("network unreachable"))

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        val state = viewModel.uiState.value as SetupUiState.Error
        assertTrue(state.message.startsWith("Connection failed:"))
    }

    @Test
    fun `saveManualCredentials handles exception during save`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { credentialStore.saveCredentials(any()) } throws RuntimeException("DataStore corrupt")

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SetupUiState.Error)
        assertTrue((state as SetupUiState.Error).message.contains("DataStore corrupt"))
    }

    @Test
    fun `saveManualCredentials does nothing when state is not ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Transition to Success first
        coEvery { repository.getAllItems() } returns Result.success(emptyList())
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Success)

        // Try saving again — should not change state
        viewModel.saveManualCredentials()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.Success)
    }

    // ─── ServerSelector Reset ───

    @Test
    fun `saveManualCredentials resets serverSelector after saving credentials`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user@test.com")
        viewModel.updatePassword("password")
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        verify(exactly = 1) { serverSelector.reset() }
    }

    @Test
    fun `saveManualCredentials resets serverSelector before verifying connection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateServerUrl("https://myopenhab.org")
        viewModel.updateUsername("user")
        viewModel.updatePassword("pass")
        advanceUntilIdle()

        // Track call order
        val callOrder = mutableListOf<String>()
        coEvery { credentialStore.saveCredentials(any()) } coAnswers {
            callOrder.add("saveCredentials")
        }
        every { serverSelector.reset() } answers {
            callOrder.add("reset")
        }
        coEvery { repository.getAllItems() } coAnswers {
            callOrder.add("getAllItems")
            Result.success(emptyList())
        }

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        assertEquals(listOf("saveCredentials", "reset", "getAllItems"), callOrder)
    }

    @Test
    fun `saveManualCredentials does not reset serverSelector if save throws`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { credentialStore.saveCredentials(any()) } throws RuntimeException("write failed")

        viewModel.saveManualCredentials()
        advanceUntilIdle()

        verify(exactly = 0) { serverSelector.reset() }
    }

    // ─── Reset ───

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

    @Test
    fun `reset from Error state returns to ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.failure(Exception("failed"))
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Error)

        viewModel.reset()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.ManualEntry)
    }

    @Test
    fun `reset from Success state returns to ManualEntry`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { repository.getAllItems() } returns Result.success(emptyList())
        viewModel.saveManualCredentials()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SetupUiState.Success)

        viewModel.reset()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SetupUiState.ManualEntry)
    }
}
