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

data class RotaryControlState(
    val itemName: String = "",
    val label: String = "",
    val icon: String? = null,
    val currentValue: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val pattern: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Formatted display value */
    val displayValue: String
        get() {
            val intValue = currentValue.toLong()
            val isWholeNumber = currentValue == intValue.toDouble()
            return when {
                pattern != null -> try {
                    String.format(
                        pattern.replace("%unit%", ""),
                        if (isWholeNumber) intValue else currentValue
                    )
                } catch (e: Exception) {
                    if (isWholeNumber) intValue.toString() else String.format("%.1f", currentValue)
                }
                isWholeNumber -> intValue.toString()
                else -> String.format("%.1f", currentValue)
            }
        }

    /** Progress as 0..1 for the edge indicator */
    val progress: Float
        get() = ((currentValue - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
}

@HiltViewModel
class RotaryControlViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemName: String = savedStateHandle["item_name"] ?: ""

    private val _state = MutableStateFlow(RotaryControlState(itemName = itemName))
    val state: StateFlow<RotaryControlState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        loadCachedMetadata()
        refreshLiveState()
    }

    /**
     * Load metadata (label, icon) from the cached tile items immediately.
     * This gives instant display without waiting for a network call.
     */
    private fun loadCachedMetadata() {
        viewModelScope.launch {
            repository.getAvailableTileItems()
                .onSuccess { tileItems ->
                    val tileItem = tileItems.find { it.item.name == itemName }
                    if (tileItem != null) {
                        val item = tileItem.item
                        val stateDesc = item.stateDescription
                        _state.value = RotaryControlState(
                            itemName = item.name,
                            label = tileItem.effectiveLabel,
                            icon = tileItem.effectiveIcon,
                            currentValue = item.numericState ?: 0.0,
                            min = stateDesc?.minimum ?: 0.0,
                            max = stateDesc?.maximum ?: 100.0,
                            step = stateDesc?.step ?: 1.0,
                            pattern = stateDesc?.pattern,
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * Refresh live state from the server in the background.
     * Updates the current value if it changed externally.
     */
    private fun refreshLiveState() {
        viewModelScope.launch {
            repository.getItem(itemName)
                .onSuccess { item ->
                    val stateDesc = item.stateDescription
                    val current = _state.value
                    _state.value = current.copy(
                        currentValue = item.numericState ?: current.currentValue,
                        min = stateDesc?.minimum ?: current.min,
                        max = stateDesc?.maximum ?: current.max,
                        step = stateDesc?.step ?: current.step,
                        pattern = stateDesc?.pattern ?: current.pattern,
                        isLoading = false
                    )
                }
                .onFailure { error ->
                    // Only show error if we have nothing cached
                    if (_state.value.isLoading) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.localizedMessage ?: "Failed to load item"
                        )
                    }
                }
        }
    }

    /**
     * Called on each bezel rotation event.
     * Scales the rotation delta proportionally to the item's range.
     */
    fun onRotate(delta: Float) {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        val scaledDelta = (delta / 50f) * current.step
        val newValue = (current.currentValue + scaledDelta)
            .coerceIn(current.min, current.max)

        // Snap to step
        val snapped = (Math.round((newValue - current.min) / current.step) * current.step + current.min)
            .coerceIn(current.min, current.max)

        if (snapped == current.currentValue) return

        _state.value = current.copy(currentValue = snapped)

        // Debounce: cancel previous send, schedule new one after 500ms
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            delay(500)
            sendCommand(snapped)
        }
    }

    private suspend fun sendCommand(value: Double) {
        val command = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
        repository.sendCommand(itemName, command)
    }
}
