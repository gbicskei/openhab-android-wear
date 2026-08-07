package org.openhab.habdroid.wear.phone.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.openhab.habdroid.wear.phone.sync.PhoneDataLayerSender
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import kotlin.coroutines.resume

data class AssistantSetupState(
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val statusMessage: String? = null
)

data class TestResult(
    val hasPermission: Boolean,
    val isRegistered: Boolean
)

@HiltViewModel
class AssistantSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataLayerSender: PhoneDataLayerSender
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantSetupState())
    val state: StateFlow<AssistantSetupState> = _state.asStateFlow()

    private val messageClient by lazy { Wearable.getMessageClient(context) }

    companion object {
        private const val TAG = "AssistantSetupVM"
    }

    /**
     * Query the watch via Data Layer to check if the assistant is properly configured.
     * Works over Bluetooth — no WiFi needed.
     */
    fun test() {
        _state.update { it.copy(isTesting = true, testResult = null, statusMessage = null) }

        viewModelScope.launch {
            try {
                val node = dataLayerSender.getConnectedWatch()
                if (node == null) {
                    _state.update { it.copy(isTesting = false, statusMessage = "Watch not connected") }
                    return@launch
                }

                val response = withTimeoutOrNull(5000L) {
                    suspendCancellableCoroutine { cont ->
                        val listener = MessageClient.OnMessageReceivedListener { event: MessageEvent ->
                            if (event.path == SyncConstants.PATH_ASSISTANT_STATUS_RESPONSE) {
                                val data = String(event.data, Charsets.UTF_8)
                                val parts = data.split("|")
                                if (parts.size == 2) {
                                    cont.resume(TestResult(
                                        hasPermission = parts[0].toBoolean(),
                                        isRegistered = parts[1].toBoolean()
                                    ))
                                }
                            }
                        }
                        messageClient.addListener(listener)
                        cont.invokeOnCancellation { messageClient.removeListener(listener) }

                        viewModelScope.launch {
                            messageClient.sendMessage(
                                node.id,
                                SyncConstants.PATH_ASSISTANT_STATUS_REQUEST,
                                ByteArray(0)
                            ).await()
                        }
                    }
                }

                if (response != null) {
                    _state.update { it.copy(isTesting = false, testResult = response) }
                } else {
                    _state.update { it.copy(isTesting = false, statusMessage = "Watch did not respond. Make sure the openHAB watch app is installed.") }
                }
            } catch (e: Exception) {
                AppLog.d(TAG, "Test failed: ${e.message}")
                _state.update { it.copy(isTesting = false, statusMessage = "Test failed: ${e.message}") }
            }
        }
    }
}
