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
import org.openhab.habdroid.wear.complication.ComplicationRefresher
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.ThemeStore
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
    val unit: String = "",
    val themeColor: Long = ControlStyle.DEFAULT_THEME_COLOR,
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
                        pattern.replace("%unit%", unit),
                        if (isWholeNumber) intValue else currentValue
                    )
                } catch (e: Exception) {
                    if (isWholeNumber) "$intValue$unit" else "${String.format("%.1f", currentValue)}$unit"
                }
                unit.isNotEmpty() -> {
                    if (isWholeNumber) "$intValue$unit" else "${String.format("%.1f", currentValue)}$unit"
                }
                isWholeNumber -> intValue.toString()
                else -> String.format("%.1f", currentValue)
            }
        }

    /** Progress as 0..1 for the edge indicator */
    val progress: Float
        get() = ((currentValue - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Controls bezel-driven adjustment of range items (Dimmer, Number with min/max).
 * Loads item metadata for range bounds, debounces command sending on rotation.
 */
@HiltViewModel
class RotaryControlViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    private val themeStore: ThemeStore,
    private val complicationRefresher: ComplicationRefresher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemName: String = savedStateHandle["item_name"] ?: ""
    private val passedLabel: String = savedStateHandle["label"] ?: ""

    private val _state = MutableStateFlow(RotaryControlState(itemName = itemName, label = passedLabel))
    val state: StateFlow<RotaryControlState> = _state.asStateFlow()

    private var sendJob: Job? = null
    private var sseJob: Job? = null

    init {
        loadCachedMetadata()
        refreshLiveState()
        loadTheme()
    }

    private fun loadTheme() {
        viewModelScope.launch {
            val theme = themeStore.getTheme()
            _state.value = _state.value.copy(themeColor = theme.color.toLong() and 0xFFFFFFFFL)
        }
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
                        _state.value = _state.value.copy(
                            itemName = item.name,
                            label = tileItem.effectiveLabel.takeIf { it.isNotBlank() && it != item.type }
                                ?: passedLabel.takeIf { it.isNotBlank() && it != item.type }
                                ?: item.label?.takeIf { it.isNotBlank() }
                                ?: item.name,
                            icon = tileItem.effectiveIcon,
                            currentValue = item.numericState ?: 0.0,
                            min = stateDesc?.minimum ?: 0.0,
                            max = stateDesc?.maximum ?: 100.0,
                            step = stateDesc?.step ?: 1.0,
                            pattern = stateDesc?.pattern,
                            unit = resolveUnit(item.type, stateDesc?.pattern),
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
                        label = current.label.takeIf { it.isNotBlank() && it != item.type }
                            ?: item.label?.takeIf { it.isNotBlank() }
                            ?: passedLabel.takeIf { it.isNotBlank() && it != item.type }
                            ?: item.name,
                        currentValue = item.numericState ?: current.currentValue,
                        min = stateDesc?.minimum ?: current.min,
                        max = stateDesc?.maximum ?: current.max,
                        step = stateDesc?.step ?: current.step,
                        pattern = stateDesc?.pattern ?: current.pattern,
                        unit = resolveUnit(item.type, stateDesc?.pattern).ifEmpty { current.unit },
                        isLoading = false
                    )
                    // Start SSE for live updates
                    startSseUpdates()
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

        val range = current.max - current.min
        // Scale so a full bezel rotation (~1800px) covers the entire range
        val scaledDelta = (delta / 1800f) * range
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
        complicationRefresher.requestUpdate()
    }

    /**
     * Subscribe to SSE events for this specific item.
     * Updates the current value in real-time while the activity is visible.
     */
    private fun startSseUpdates() {
        if (sseJob?.isActive == true) return
        sseJob = viewModelScope.launch {
            repository.observeItemState(itemName).collect { newState ->
                val numericValue = newState.replace(" .*".toRegex(), "").toDoubleOrNull()
                if (numericValue != null) {
                    val current = _state.value
                    _state.value = current.copy(
                        currentValue = numericValue.coerceIn(current.min, current.max)
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }

    /**
     * Resolve display unit from item type and pattern.
     */
    private fun resolveUnit(itemType: String, pattern: String?): String {
        // Extract unit from pattern if it contains a literal suffix after the format specifier
        if (pattern != null && pattern.contains("%unit%")) {
            // %unit% is a placeholder — resolve from item type
            return when {
                itemType.startsWith("Number:Temperature") -> "°C"
                itemType == "Dimmer" || itemType == "Rollershutter" -> "%"
                itemType.startsWith("Number:Angle") -> "°"
                itemType.startsWith("Number:Power") -> "W"
                itemType.startsWith("Number:Energy") -> "kWh"
                itemType.startsWith("Number:Pressure") -> "hPa"
                itemType.startsWith("Number:Speed") -> "km/h"
                itemType.startsWith("Number:Length") -> "m"
                itemType.startsWith("Number:Dimensionless") -> "%"
                else -> ""
            }
        }
        // No %unit% in pattern — infer from type if no pattern at all
        if (pattern == null) {
            return when {
                itemType == "Dimmer" || itemType == "Rollershutter" -> "%"
                itemType.startsWith("Number:Temperature") -> "°C"
                else -> ""
            }
        }
        return ""
    }
}
