package org.openhab.habdroid.wear.ui.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

data class RollerShutterState(
    val itemName: String = "",
    val label: String = "",
    val position: Float = 0f, // 0 = open, 100 = closed (openHAB convention)
    val isMoving: Boolean = false,
    val themeColor: Long = ControlStyle.DEFAULT_THEME_COLOR,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Display text for position */
    val positionDisplay: String
        get() = "${position.toInt()}%"
}

/**
 * Controls a Rollershutter item — sends UP/DOWN/STOP commands and adjusts
 * position via bezel rotation with debounced percentage commands.
 */
@HiltViewModel
class RollerShutterViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    private val themeStore: org.openhab.habdroid.wear.data.repository.ThemeStore,
    private val complicationRefresher: org.openhab.habdroid.wear.complication.ComplicationRefresher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemName: String = savedStateHandle["item_name"] ?: ""

    private val _state = MutableStateFlow(RollerShutterState(itemName = itemName))
    val state: StateFlow<RollerShutterState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        loadCurrentState()
        loadTheme()
    }

    private fun loadTheme() {
        viewModelScope.launch {
            val theme = themeStore.getTheme()
            _state.value = _state.value.copy(themeColor = theme.color.toLong() and 0xFFFFFFFFL)
        }
    }

    private fun loadCurrentState() {
        viewModelScope.launch {
            repository.getItem(itemName)
                .onSuccess { item ->
                    val position = item.numericState?.toFloat()?.coerceIn(0f, 100f) ?: 0f
                    _state.value = _state.value.copy(
                        itemName = itemName,
                        label = item.displayLabel,
                        position = position,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.localizedMessage ?: "Failed to load item"
                    )
                }
        }
    }

    /**
     * Send UP command — opens the shutter (moves toward 0%).
     */
    fun sendUp() {
        val current = _state.value
        if (current.isLoading || current.error != null) return
        _state.value = current.copy(isMoving = true, position = 0f)
        viewModelScope.launch {
            repository.sendCommand(itemName, "UP")
            complicationRefresher.requestUpdate()
            _state.value = _state.value.copy(isMoving = false)
        }
    }

    /**
     * Send DOWN command — closes the shutter (moves toward 100%).
     */
    fun sendDown() {
        val current = _state.value
        if (current.isLoading || current.error != null) return
        _state.value = current.copy(isMoving = true, position = 100f)
        viewModelScope.launch {
            repository.sendCommand(itemName, "DOWN")
            complicationRefresher.requestUpdate()
            _state.value = _state.value.copy(isMoving = false)
        }
    }

    /**
     * Send STOP command — halts shutter movement.
     */
    fun sendStop() {
        val current = _state.value
        if (current.isLoading || current.error != null) return
        viewModelScope.launch {
            repository.sendCommand(itemName, "STOP")
            complicationRefresher.requestUpdate()
            _state.value = _state.value.copy(isMoving = false)
            // Refresh position after stop
            delay(500)
            refreshPosition()
        }
    }

    /**
     * Adjust position via bezel rotation.
     * Scroll down = close (increase %), scroll up = open (decrease %).
     */
    fun onRotatePosition(delta: Float) {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        val newPosition = (current.position + (delta / 1800f) * 100f).coerceIn(0f, 100f)
        if (newPosition.toInt() == current.position.toInt()) return

        _state.value = current.copy(position = newPosition)

        // Debounce: send position command after 500ms
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            delay(500)
            sendPosition(newPosition.toInt())
        }
    }

    private suspend fun sendPosition(position: Int) {
        repository.sendCommand(itemName, position.toString())
        complicationRefresher.requestUpdate()
    }

    private suspend fun refreshPosition() {
        repository.getItem(itemName)
            .onSuccess { item ->
                val position = item.numericState?.toFloat()?.coerceIn(0f, 100f)
                    ?: _state.value.position
                _state.value = _state.value.copy(position = position)
            }
    }
}
