package org.openhab.habdroid.wear.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.VoicePreferenceStore
import org.openhab.habdroid.wear.util.ServerTtsPlayer
import org.openhab.habdroid.wear.util.TtsManager
import javax.inject.Inject

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Sending(val command: String) : VoiceUiState
    data class Success(val responseText: String, val isSpeaking: Boolean = false, val ttsUsed: Boolean = false) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

/** Handles voice command flow — sends recognized speech text to the openHAB voice interpreter endpoint. */
@HiltViewModel
class VoiceCommandViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    private val ttsManager: TtsManager,
    private val serverTtsPlayer: ServerTtsPlayer,
    private val voicePreferenceStore: VoicePreferenceStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    fun sendVoiceCommand(text: String) {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Sending(text)
            repository.sendVoiceCommand(text)
                .onSuccess { responseText ->
                    val displayText = responseText.ifBlank { "Done" }

                    // Speak the response if TTS is enabled
                    val ttsEnabled = voicePreferenceStore.voiceResponseSpoken.first()
                    if (ttsEnabled) {
                        val useServer = voicePreferenceStore.serverTtsEnabled.first()
                        if (useServer) {
                            val apiKey = voicePreferenceStore.serverTtsApiKey.first()
                            val voice = voicePreferenceStore.serverTtsVoice.first()
                            if (apiKey.isNotBlank()) {
                                _uiState.value = VoiceUiState.Success(responseText = displayText, isSpeaking = true, ttsUsed = true)
                                serverTtsPlayer.setApiKey(apiKey)
                                serverTtsPlayer.speakFromServer(responseText, voice = voice)
                                _uiState.value = VoiceUiState.Success(responseText = displayText, isSpeaking = false, ttsUsed = true)
                            } else {
                                _uiState.value = VoiceUiState.Success(responseText = displayText)
                            }
                        } else {
                            // Local TTS — apply volume, speech rate, and pitch from preferences
                            val volume = voicePreferenceStore.ttsVolume.first()
                            val speechRate = voicePreferenceStore.ttsSpeechRate.first()
                            val pitch = voicePreferenceStore.ttsPitch.first()
                            _uiState.value = VoiceUiState.Success(responseText = displayText, isSpeaking = true, ttsUsed = true)
                            ttsManager.speak(responseText, volume, speechRate, pitch)
                            // Wait for TTS to start, then wait for it to finish
                            ttsManager.isSpeaking.first { it } // wait until speaking starts
                            ttsManager.isSpeaking.first { !it } // wait until speaking ends
                            _uiState.value = VoiceUiState.Success(responseText = displayText, isSpeaking = false, ttsUsed = true)
                        }
                    } else {
                        _uiState.value = VoiceUiState.Success(responseText = displayText)
                    }
                }
                .onFailure { error ->
                    _uiState.value = VoiceUiState.Error(
                        error.message ?: "Command failed"
                    )
                }
        }
    }

    fun setError(message: String) {
        _uiState.value = VoiceUiState.Error(message)
    }

    fun reset() {
        _uiState.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
        serverTtsPlayer.stop()
    }
}
