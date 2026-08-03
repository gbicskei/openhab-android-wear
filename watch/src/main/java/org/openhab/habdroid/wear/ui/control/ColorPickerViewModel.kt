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

data class ColorPickerState(
    val itemName: String = "",
    val label: String = "",
    val hue: Float = 0f,        // 0-360
    val saturation: Float = 100f, // 0-100
    val brightness: Float = 100f, // 0-100
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** HSB command string for openHAB (e.g. "120,100,50") */
    val hsbCommand: String
        get() = "${hue.toInt()},${saturation.toInt()},${brightness.toInt()}"

    /** Display text showing brightness percentage */
    val brightnessDisplay: String
        get() = "${brightness.toInt()}%"

    /** Whether the light is currently on (brightness > 0) */
    val isOn: Boolean get() = brightness > 0f
}

/**
 * Preset color with a human-readable name.
 */
data class PresetColor(
    val name: String,
    val hue: Float,
    val saturation: Float
)

/** Common preset colors for quick selection */
val PRESET_COLORS = listOf(
    PresetColor("Red", 0f, 100f),
    PresetColor("Orange", 30f, 100f),
    PresetColor("Yellow", 60f, 100f),
    PresetColor("Green", 120f, 100f),
    PresetColor("Cyan", 180f, 100f),
    PresetColor("Blue", 240f, 100f),
    PresetColor("Purple", 270f, 100f),
    PresetColor("Pink", 320f, 100f),
    PresetColor("Warm", 30f, 60f),
    PresetColor("Cool", 210f, 20f)
)

@HiltViewModel
class ColorPickerViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemName: String = savedStateHandle["item_name"] ?: ""

    private val _state = MutableStateFlow(ColorPickerState(itemName = itemName))
    val state: StateFlow<ColorPickerState> = _state.asStateFlow()

    private var sendJob: Job? = null

    init {
        loadCurrentState()
    }

    /**
     * Load the item's current HSB state from the server.
     */
    private fun loadCurrentState() {
        viewModelScope.launch {
            repository.getItem(itemName)
                .onSuccess { item ->
                    val label = item.displayLabel
                    val hsb = parseHsbState(item.state)
                    _state.value = ColorPickerState(
                        itemName = itemName,
                        label = label,
                        hue = hsb.first,
                        saturation = hsb.second,
                        brightness = hsb.third,
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
     * Select a preset color (updates hue + saturation, keeps brightness).
     */
    fun selectPreset(preset: PresetColor) {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        _state.value = current.copy(
            hue = preset.hue,
            saturation = preset.saturation
        )
        debounceSendCommand()
    }

    /**
     * Select a specific hue from the color wheel.
     */
    fun selectHue(hue: Float) {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        _state.value = current.copy(hue = hue.coerceIn(0f, 360f))
        debounceSendCommand()
    }

    /**
     * Adjust brightness via bezel rotation.
     */
    fun onRotateBrightness(delta: Float) {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        val newBrightness = (current.brightness + delta / 30f)
            .coerceIn(0f, 100f)

        if (newBrightness.toInt() == current.brightness.toInt()) return

        _state.value = current.copy(brightness = newBrightness)
        debounceSendCommand()
    }

    /**
     * Toggle the light on/off. Off = brightness 0, On = restore to 100 or last value.
     */
    fun toggleOnOff() {
        val current = _state.value
        if (current.isLoading || current.error != null) return

        val newBrightness = if (current.isOn) 0f else 100f
        _state.value = current.copy(brightness = newBrightness)
        sendImmediately()
    }

    private fun debounceSendCommand() {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            delay(400)
            sendCurrentColor()
        }
    }

    private fun sendImmediately() {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            sendCurrentColor()
        }
    }

    private suspend fun sendCurrentColor() {
        repository.sendCommand(itemName, _state.value.hsbCommand)
    }

    /**
     * Parse openHAB HSB state string (e.g. "120,100,50") into (hue, saturation, brightness).
     * Falls back to 0,100,100 for ON, 0,0,0 for OFF/NULL.
     */
    private fun parseHsbState(state: String): Triple<Float, Float, Float> {
        val parts = state.split(",")
        if (parts.size == 3) {
            val h = parts[0].trim().toFloatOrNull() ?: 0f
            val s = parts[1].trim().toFloatOrNull() ?: 100f
            val b = parts[2].trim().toFloatOrNull() ?: 100f
            return Triple(h, s, b)
        }
        // Dimmer-like percentage or ON/OFF
        return when {
            state == "ON" -> Triple(0f, 0f, 100f)
            state == "OFF" || state == "NULL" || state == "UNDEF" -> Triple(0f, 0f, 0f)
            state.toFloatOrNull() != null -> Triple(0f, 0f, state.toFloat().coerceIn(0f, 100f))
            else -> Triple(0f, 100f, 100f)
        }
    }
}
