package org.openhab.habdroid.wear.ui.control

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.model.CommandOption
import org.openhab.habdroid.wear.data.model.StateOption
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

data class ChoicePickerState(
    val itemName: String = "",
    val label: String = "",
    val options: List<ChoiceOption> = emptyList(),
    val currentValue: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * A selectable option with command value and display label.
 */
data class ChoiceOption(
    val command: String,
    val label: String
)

@HiltViewModel
class ChoicePickerViewModel @Inject constructor(
    private val repository: OpenHabRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemName: String = savedStateHandle["item_name"] ?: ""

    private val _state = MutableStateFlow(ChoicePickerState(itemName = itemName))
    val state: StateFlow<ChoicePickerState> = _state.asStateFlow()

    init {
        loadOptions()
    }

    /**
     * Load the item's command options (or state options as fallback) from the server.
     * Priority: commandDescription.commandOptions > stateDescription.options
     */
    private fun loadOptions() {
        viewModelScope.launch {
            repository.getItem(itemName)
                .onSuccess { item ->
                    val options = buildOptionList(item.commandDescription?.commandOptions, item.stateDescription?.options)
                    _state.value = ChoicePickerState(
                        itemName = itemName,
                        label = item.displayLabel,
                        options = options,
                        currentValue = item.state,
                        isLoading = false,
                        error = if (options.isEmpty()) "No options available" else null
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
     * Send the selected option's command value.
     */
    fun selectOption(option: ChoiceOption) {
        val current = _state.value
        if (current.isSending) return

        _state.value = current.copy(isSending = true, currentValue = option.command)
        viewModelScope.launch {
            repository.sendCommand(itemName, option.command)
            _state.value = _state.value.copy(isSending = false)
        }
    }

    /**
     * Build a unified option list from commandOptions (preferred) or stateOptions (fallback).
     */
    private fun buildOptionList(
        commandOptions: List<CommandOption>?,
        stateOptions: List<StateOption>?
    ): List<ChoiceOption> {
        // Prefer commandOptions — these are what can be sent as commands
        if (!commandOptions.isNullOrEmpty()) {
            return commandOptions.map { opt ->
                ChoiceOption(
                    command = opt.command,
                    label = opt.label ?: opt.command
                )
            }
        }
        // Fall back to stateOptions (read-only items may have these)
        if (!stateOptions.isNullOrEmpty()) {
            return stateOptions.map { opt ->
                ChoiceOption(
                    command = opt.value,
                    label = opt.label ?: opt.value
                )
            }
        }
        return emptyList()
    }
}
