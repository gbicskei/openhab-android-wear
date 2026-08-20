package org.openhab.habdroid.wear.phone.ui.voice

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openhab.habdroid.wear.phone.data.ConnectionTester
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.shared.model.ServerCredentials

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var credentialStore: PhoneCredentialStore
    private lateinit var connectionTester: ConnectionTester

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        credentialStore = mockk(relaxed = true)
        connectionTester = mockk(relaxed = true)

        every { credentialStore.hasGoogleTtsApiKey } returns false
        every { credentialStore.getGoogleTtsApiKey() } returns ""
        every { credentialStore.isVoiceCommandsEnabled } returns true
        every { credentialStore.isReadAloudEnabled } returns false
        every { credentialStore.isUseServerTts } returns false
        every { credentialStore.serverTtsVoice } returns ""
        every { credentialStore.ttsSpeechRate } returns 1.0f
        every { credentialStore.ttsPitch } returns 1.0f
        every { credentialStore.credentials } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = VoiceSettingsViewModel(credentialStore, connectionTester)

    @Test
    fun `initial state loads from credential store`() = runTest(testDispatcher) {
        every { credentialStore.isVoiceCommandsEnabled } returns true
        every { credentialStore.isReadAloudEnabled } returns true

        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.voiceCommandsEnabled)
        assertTrue(state.readAloudEnabled)
    }

    @Test
    fun `onVoiceCommandsEnabledChanged updates state and marks unsaved`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onVoiceCommandsEnabledChanged(false)

        assertFalse(vm.uiState.value.voiceCommandsEnabled)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `onReadAloudChanged updates state and marks unsaved`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onReadAloudChanged(true)

        assertTrue(vm.uiState.value.readAloudEnabled)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `onSpeechRateChanged updates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSpeechRateChanged(1.5f)

        assertEquals(1.5f, vm.uiState.value.speechRate)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `onPitchChanged updates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onPitchChanged(0.8f)

        assertEquals(0.8f, vm.uiState.value.pitch)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `onUseServerTtsChanged updates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onUseServerTtsChanged(true)

        assertTrue(vm.uiState.value.useServerTts)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `onVoiceSelected updates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onVoiceSelected("en-GB-Wavenet-A")

        assertEquals("en-GB-Wavenet-A", vm.uiState.value.selectedVoice)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `save persists to credential store and sets needsSync`() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onVoiceCommandsEnabledChanged(false)
        vm.onReadAloudChanged(true)
        vm.save()
        advanceUntilIdle()

        coVerify {
            credentialStore.saveVoiceSettings(
                voiceCommandsEnabled = false,
                readAloudEnabled = true,
                useServerTts = any(),
                serverTtsVoice = any(),
                speechRate = any(),
                pitch = any()
            )
        }
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertTrue(vm.uiState.value.needsSync)
    }

    @Test
    fun `ohVersionSupported returns false for 5_1_0`() = runTest(testDispatcher) {
        every { credentialStore.credentials } returns flowOf(
            ServerCredentials(serverUrl = "http://test", username = "u", password = "p")
        )
        coEvery { connectionTester.fetchOpenHabVersion(any(), any(), any()) } returns "5.1.0"

        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.ohVersionSupported)
        assertEquals("5.1.0", vm.uiState.value.ohVersion)
    }

    @Test
    fun `ohVersionSupported returns true for 5_2_1`() = runTest(testDispatcher) {
        every { credentialStore.credentials } returns flowOf(
            ServerCredentials(serverUrl = "http://test", username = "u", password = "p")
        )
        coEvery { connectionTester.fetchOpenHabVersion(any(), any(), any()) } returns "5.2.1"

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.ohVersionSupported)
    }

    @Test
    fun `hasGoogleTtsKey reflects credential store`() = runTest(testDispatcher) {
        every { credentialStore.hasGoogleTtsApiKey } returns true
        every { credentialStore.getGoogleTtsApiKey() } returns "key123"
        coEvery { connectionTester.fetchGoogleVoices("key123", any()) } returns listOf(
            VoiceOption(id = "en-US-Wavenet-A", label = "Wavenet A", locale = "en-US")
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasGoogleTtsKey)
    }
}
