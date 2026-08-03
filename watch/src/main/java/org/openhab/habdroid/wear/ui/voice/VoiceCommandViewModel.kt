package org.openhab.habdroid.wear.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Sending(val command: String) : VoiceUiState
    data object Success : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

/** Handles voice command flow — sends recognized speech text to the openHAB voice interpreter endpoint. */
@HiltViewModel
class VoiceCommandViewModel @Inject constructor(
    private val repository: OpenHabRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    fun sendVoiceCommand(text: String) {
        viewModelScope.launch {
            _uiState.value = VoiceUiState.Sending(text)
            repository.sendVoiceCommand(text)
                .onSuccess {
                    _uiState.value = VoiceUiState.Success
                }
                .onFailure { error ->
                    _uiState.value = VoiceUiState.Error(
                        error.localizedMessage ?: "Command failed"
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
}
