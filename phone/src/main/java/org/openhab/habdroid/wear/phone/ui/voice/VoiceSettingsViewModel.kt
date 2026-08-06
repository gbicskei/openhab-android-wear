package org.openhab.habdroid.wear.phone.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.ConnectionTester
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.util.AppLog
import javax.inject.Inject

data class VoiceSettingsUiState(
    val voiceCommandsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val useServerTts: Boolean = false,
    val hasGoogleTtsKey: Boolean = false,
    val selectedVoice: String = "",
    val availableVoices: List<VoiceOption> = emptyList(),
    val voicesLoading: Boolean = false,
    val volume: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val ohVersion: String? = null,
    val ohVersionSupported: Boolean = true,
    val syncResult: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val needsSync: Boolean = false
)

data class VoiceOption(
    val id: String,
    val label: String,
    val locale: String
)

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val credentialStore: PhoneCredentialStore,
    private val connectionTester: ConnectionTester
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "VoiceSettingsVM"
    }

    init {
        loadSettings()
        checkOpenHabVersion()
    }

    private fun loadSettings() {
        val hasKey = credentialStore.hasGoogleTtsApiKey
        _uiState.update {
            it.copy(
                voiceCommandsEnabled = credentialStore.isVoiceCommandsEnabled,
                readAloudEnabled = credentialStore.isReadAloudEnabled,
                useServerTts = credentialStore.isUseServerTts && hasKey,
                selectedVoice = credentialStore.serverTtsVoice,
                volume = credentialStore.ttsVolume,
                speechRate = credentialStore.ttsSpeechRate,
                pitch = credentialStore.ttsPitch,
                hasGoogleTtsKey = hasKey
            )
        }
    }

    private fun checkOpenHabVersion() {
        viewModelScope.launch {
            try {
                val creds = credentialStore.credentials.first() ?: return@launch
                val version = connectionTester.fetchOpenHabVersion(
                    serverUrl = creds.serverUrl,
                    username = creds.username,
                    password = creds.password
                )
                val supported = isVersionSupported(version)
                _uiState.update { it.copy(ohVersion = version, ohVersionSupported = supported) }
                // Load voices after we know the server is reachable
                loadVoices()
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to check OH version", e)
            }
        }
    }

    private fun isVersionSupported(version: String?): Boolean {
        if (version == null) return true // assume supported if we can't check
        val parts = version.split(".")
        if (parts.size < 3) return false
        val major = parts[0].toIntOrNull() ?: return false
        val minor = parts[1].toIntOrNull() ?: return false
        val patch = parts[2].toIntOrNull() ?: return false
        return major > 5 || (major == 5 && minor > 2) || (major == 5 && minor == 2 && patch >= 1)
    }

    fun loadVoices() {
        val apiKey = credentialStore.getGoogleTtsApiKey()
        if (apiKey.isBlank()) return

        _uiState.update { it.copy(voicesLoading = true) }

        viewModelScope.launch {
            try {
                // Use the phone's locale language as a proxy for the watch language
                val languageTag = java.util.Locale.getDefault().toLanguageTag() // e.g. "en-GB"
                val voices = connectionTester.fetchGoogleVoices(apiKey, languageTag)
                _uiState.update { it.copy(availableVoices = voices, voicesLoading = false) }
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load voices", e)
                _uiState.update { it.copy(voicesLoading = false) }
            }
        }
    }

    fun onVoiceCommandsEnabledChanged(enabled: Boolean) {
        _uiState.update { it.copy(voiceCommandsEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun onReadAloudChanged(enabled: Boolean) {
        _uiState.update { it.copy(readAloudEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun onUseServerTtsChanged(useServer: Boolean) {
        _uiState.update { it.copy(useServerTts = useServer, hasUnsavedChanges = true) }
    }

    fun onVoiceSelected(voiceId: String) {
        _uiState.update { it.copy(selectedVoice = voiceId, hasUnsavedChanges = true) }
    }

    fun testVoice() {
        val state = _uiState.value
        val apiKey = credentialStore.getGoogleTtsApiKey()

        viewModelScope.launch {
            try {
                if (state.useServerTts && apiKey.isNotBlank() && state.selectedVoice.isNotBlank()) {
                    connectionTester.playTestVoice(apiKey, state.selectedVoice)
                } else {
                    // System TTS test — use Android TTS on the phone
                    connectionTester.playSystemTtsTest(state.speechRate, state.pitch)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "Voice test failed", e)
            }
        }
    }

    fun onVolumeChanged(volume: Float) {
        _uiState.update { it.copy(volume = volume, hasUnsavedChanges = true) }
    }

    fun onSpeechRateChanged(rate: Float) {
        _uiState.update { it.copy(speechRate = rate, hasUnsavedChanges = true) }
    }

    fun onPitchChanged(pitch: Float) {
        _uiState.update { it.copy(pitch = pitch, hasUnsavedChanges = true) }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            credentialStore.saveVoiceSettings(
                voiceCommandsEnabled = state.voiceCommandsEnabled,
                readAloudEnabled = state.readAloudEnabled,
                useServerTts = state.useServerTts,
                serverTtsVoice = state.selectedVoice,
                volume = state.volume,
                speechRate = state.speechRate,
                pitch = state.pitch
            )
            _uiState.update { it.copy(hasUnsavedChanges = false, needsSync = true) }
            AppLog.d(TAG, "Voice settings saved — sync needed")
        }
    }

    fun dismissSyncResult() {
        _uiState.update { it.copy(syncResult = null) }
    }
}
