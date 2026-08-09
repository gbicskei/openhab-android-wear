package org.openhab.habdroid.wear.phone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.SyncNotificationSettingsPayload
import javax.inject.Inject

data class NotificationSettingsUiState(
    val readAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val notificationVolume: Float = 1.0f,
    val hasUnsavedChanges: Boolean = false
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val credentialStore: PhoneCredentialStore,
    private val dataLayerSender: PhoneDataLayerSender,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                readAloudEnabled = credentialStore.isNotificationReadAloud,
                chimeEnabled = credentialStore.isNotificationChime,
                notificationVolume = credentialStore.notificationVolume
            )
        }
    }

    fun onReadAloudChanged(enabled: Boolean) {
        _uiState.update { it.copy(readAloudEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun onChimeChanged(enabled: Boolean) {
        _uiState.update { it.copy(chimeEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun onVolumeChanged(volume: Float) {
        _uiState.update { it.copy(notificationVolume = volume, hasUnsavedChanges = true) }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            credentialStore.saveNotificationSettings(
                state.readAloudEnabled,
                state.chimeEnabled,
                state.notificationVolume
            )
            syncToWatch()
            _uiState.update { it.copy(hasUnsavedChanges = false) }
            AppLog.d(TAG, "Notification settings saved (readAloud=${state.readAloudEnabled}, chime=${state.chimeEnabled}, volume=${state.notificationVolume})")
        }
    }

    private suspend fun syncToWatch() {
        val state = _uiState.value
        val payload = json.encodeToString(
            SyncNotificationSettingsPayload.serializer(),
            SyncNotificationSettingsPayload(
                notificationsEnabled = true,
                readAloudEnabled = state.readAloudEnabled,
                chimeEnabled = state.chimeEnabled,
                chimeSound = "default",
                notificationVolume = state.notificationVolume
            )
        )
        dataLayerSender.sendNotificationSettings(payload)
            .onSuccess { AppLog.d(TAG, "Notification settings synced to watch") }
            .onFailure { AppLog.w(TAG, "Failed to sync notification settings", it) }
    }

    companion object {
        private const val TAG = "NotifSettingsVM"
    }
}
