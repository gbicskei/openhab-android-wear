package org.openhab.habdroid.wear.ui.voice

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import org.openhab.habdroid.wear.util.ServerTtsPlayer
import org.openhab.habdroid.wear.util.TtsManager

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCommandViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: OpenHabRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var serverTtsPlayer: ServerTtsPlayer
    private lateinit var voicePreferenceStore: VoicePreferenceStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        ttsManager = mockk(relaxed = true)
        serverTtsPlayer = mockk(relaxed = true)
        voicePreferenceStore = mockk(relaxed = true)

        // Default: TTS disabled
        every { voicePreferenceStore.voiceResponseSpoken } returns flowOf(false)
        every { voicePreferenceStore.serverTtsEnabled } returns flowOf(false)
        every { voicePreferenceStore.serverTtsApiKey } returns flowOf("")
        every { voicePreferenceStore.serverTtsVoice } returns flowOf("en-US-Wavenet-D")
        every { voicePreferenceStore.ttsSpeechRate } returns flowOf(1.0f)
        every { voicePreferenceStore.ttsPitch } returns flowOf(1.0f)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = VoiceCommandViewModel(
        repository, ttsManager, serverTtsPlayer, voicePreferenceStore
    )

    @Test
    fun `initial state is Idle`() {
        val vm = createViewModel()
        assertEquals(VoiceUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `sendVoiceCommand transitions to Sending then Success`() = runTest {
        coEvery { repository.sendVoiceCommand("turn on lights") } returns Result.success("OK")

        val vm = createViewModel()
        vm.sendVoiceCommand("turn on lights")

        val state = vm.uiState.value
        assertTrue(state is VoiceUiState.Success)
        assertEquals("OK", (state as VoiceUiState.Success).responseText)
        assertFalse(state.ttsUsed)
    }

    @Test
    fun `sendVoiceCommand with blank response shows Done`() = runTest {
        coEvery { repository.sendVoiceCommand("test") } returns Result.success("")

        val vm = createViewModel()
        vm.sendVoiceCommand("test")

        val state = vm.uiState.value as VoiceUiState.Success
        assertEquals("Done", state.responseText)
    }

    @Test
    fun `sendVoiceCommand failure shows Error state`() = runTest {
        coEvery { repository.sendVoiceCommand("bad") } returns Result.failure(Exception("Network error"))

        val vm = createViewModel()
        vm.sendVoiceCommand("bad")

        val state = vm.uiState.value
        assertTrue(state is VoiceUiState.Error)
        assertEquals("Network error", (state as VoiceUiState.Error).message)
    }

    @Test
    fun `sendVoiceCommand with server TTS enabled uses serverTtsPlayer`() = runTest {
        every { voicePreferenceStore.voiceResponseSpoken } returns flowOf(true)
        every { voicePreferenceStore.serverTtsEnabled } returns flowOf(true)
        every { voicePreferenceStore.serverTtsApiKey } returns flowOf("test-api-key")
        every { voicePreferenceStore.serverTtsVoice } returns flowOf("en-US-Wavenet-D")
        coEvery { repository.sendVoiceCommand("hello") } returns Result.success("Hi there")
        coEvery { serverTtsPlayer.speakFromServer(any(), voice = any()) } returns true

        val vm = createViewModel()
        vm.sendVoiceCommand("hello")

        coVerify { serverTtsPlayer.setApiKey("test-api-key") }
        coVerify { serverTtsPlayer.speakFromServer("Hi there", voice = "en-US-Wavenet-D") }

        val state = vm.uiState.value as VoiceUiState.Success
        assertTrue(state.ttsUsed)
        assertFalse(state.isSpeaking) // speaking finished (suspend returned)
    }

    @Test
    fun `sendVoiceCommand with server TTS but no API key skips TTS`() = runTest {
        every { voicePreferenceStore.voiceResponseSpoken } returns flowOf(true)
        every { voicePreferenceStore.serverTtsEnabled } returns flowOf(true)
        every { voicePreferenceStore.serverTtsApiKey } returns flowOf("")
        coEvery { repository.sendVoiceCommand("test") } returns Result.success("Response")

        val vm = createViewModel()
        vm.sendVoiceCommand("test")

        coVerify(exactly = 0) { serverTtsPlayer.speakFromServer(any(), voice = any()) }
        val state = vm.uiState.value as VoiceUiState.Success
        assertFalse(state.ttsUsed)
    }

    @Test
    fun `sendVoiceCommand with local TTS uses ttsManager`() = runTest {
        every { voicePreferenceStore.voiceResponseSpoken } returns flowOf(true)
        every { voicePreferenceStore.serverTtsEnabled } returns flowOf(false)
        every { voicePreferenceStore.ttsSpeechRate } returns flowOf(1.2f)
        every { voicePreferenceStore.ttsPitch } returns flowOf(0.9f)
        coEvery { repository.sendVoiceCommand("test") } returns Result.success("Done it")
        coEvery { ttsManager.speak(any(), any(), any()) } returns true

        val vm = createViewModel()
        vm.sendVoiceCommand("test")
        advanceUntilIdle()

        coVerify { ttsManager.speak("Done it", 1.2f, 0.9f) }
        val state = vm.uiState.value as VoiceUiState.Success
        assertTrue(state.ttsUsed)
        assertFalse(state.isSpeaking)
    }

    @Test
    fun `setError updates state to Error`() {
        val vm = createViewModel()
        vm.setError("Something went wrong")

        val state = vm.uiState.value
        assertTrue(state is VoiceUiState.Error)
        assertEquals("Something went wrong", (state as VoiceUiState.Error).message)
    }

    @Test
    fun `reset returns state to Idle`() {
        val vm = createViewModel()
        vm.setError("error")
        vm.reset()

        assertEquals(VoiceUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `setListening transitions to Listening state`() {
        val vm = createViewModel()
        vm.setListening()

        val state = vm.uiState.value
        assertTrue(state is VoiceUiState.Listening)
        assertEquals(0f, (state as VoiceUiState.Listening).rmsLevel)
        assertEquals("", state.partialText)
    }

    @Test
    fun `updateRmsLevel updates Listening state`() {
        val vm = createViewModel()
        vm.setListening()
        vm.updateRmsLevel(5.5f)

        val state = vm.uiState.value as VoiceUiState.Listening
        assertEquals(5.5f, state.rmsLevel)
    }

    @Test
    fun `updateRmsLevel does nothing when not in Listening state`() {
        val vm = createViewModel()
        vm.updateRmsLevel(5.5f)

        assertEquals(VoiceUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `setPartialResult updates Listening state`() {
        val vm = createViewModel()
        vm.setListening()
        vm.setPartialResult("turn on the")

        val state = vm.uiState.value as VoiceUiState.Listening
        assertEquals("turn on the", state.partialText)
    }

    @Test
    fun `setPartialResult does nothing when not in Listening state`() {
        val vm = createViewModel()
        vm.setPartialResult("something")

        assertEquals(VoiceUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `setProcessing transitions to Processing state`() {
        val vm = createViewModel()
        vm.setListening()
        vm.setProcessing()

        assertEquals(VoiceUiState.Processing, vm.uiState.value)
    }
}
