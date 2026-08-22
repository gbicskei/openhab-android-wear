package org.openhab.habdroid.wear.phone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.sync.WatchSettingsDataItemClient
import org.openhab.habdroid.wear.phone.util.AppLog
import javax.inject.Inject

data class NotificationSettingsUiState(
    val readAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val hasUnsavedChanges: Boolean = false
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val credentialStore: PhoneCredentialStore,
    private val watchSettingsClient: WatchSettingsDataItemClient
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
                chimeEnabled = credentialStore.isNotificationChime
            )
        }
    }

    fun onReadAloudChanged(enabled: Boolean) {
        _uiState.update { it.copy(readAloudEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun onChimeChanged(enabled: Boolean) {
        _uiState.update { it.copy(chimeEnabled = enabled, hasUnsavedChanges = true) }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            credentialStore.saveNotificationSettings(
                state.readAloudEnabled,
                state.chimeEnabled
            )
            syncToWatch()
            _uiState.update { it.copy(hasUnsavedChanges = false) }
            AppLog.d(TAG, "Notification settings saved (readAloud=${state.readAloudEnabled}, chime=${state.chimeEnabled})")
        }
    }

    private suspend fun syncToWatch() {
        val state = _uiState.value
        val current = watchSettingsClient.read() ?: return
        val updated = current.copy(
            notificationReadAloudEnabled = state.readAloudEnabled,
            chimeEnabled = state.chimeEnabled
        )
        watchSettingsClient.writeSettings(updated)
            .onSuccess { AppLog.d(TAG, "Notification settings synced to watch via DataItem") }
            .onFailure { AppLog.w(TAG, "Failed to sync notification settings", it) }
    }

    companion object {
        private const val TAG = "NotifSettingsVM"
    }
}
