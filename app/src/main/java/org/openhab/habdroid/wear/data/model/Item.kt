package org.openhab.habdroid.wear.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an openHAB Item from the REST API.
 */
@Serializable
data class Item(
    val name: String,
    val label: String? = null,
    val type: String = "",
    val state: String = "NULL",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val groupNames: List<String> = emptyList(),
    val link: String? = null,
    val stateDescription: StateDescription? = null,
    val commandDescription: CommandDescription? = null,
    val metadata: Map<String, MetadataEntry>? = null
) {
    /** Display label — falls back to item name if label is null */
    val displayLabel: String get() = label ?: name

    /** Whether this item is a switch-like type that can be toggled */
    val isToggleable: Boolean
        get() = type in listOf("Switch", "Dimmer", "Color") ||
            commandDescription?.commandOptions?.any { it.command in listOf("ON", "OFF") } == true

    /** Whether this item is a range/slider type (has min/max and is not read-only) */
    val isRange: Boolean
        get() = stateDescription?.let {
            it.minimum != null && it.maximum != null && !it.isReadOnly
        } ?: false

    /** Whether this item is a Contact type (display-only, OPEN/CLOSED) */
    val isContact: Boolean
        get() = type == "Contact"

    /** Whether this item is read-only (display only, no action on tap) */
    val isReadOnly: Boolean
        get() = stateDescription?.isReadOnly == true || isContact

    /**
     * Whether this item is supported on the watch tile.
     * Supported: toggles (Switch/Dimmer/Color), range (Number/Dimmer with min/max), Contact.
     */
    val isSupportedForTile: Boolean
        get() = isToggleable || isRange || isContact

    /** Icon name for use with openHAB icon API */
    val iconName: String get() = category ?: "none"

    /** Whether the item is currently in an active state (ON, OPEN, or numeric > 0) */
    val isActive: Boolean
        get() = state == "ON" || state == "OPEN" || (state.toIntOrNull() ?: 0) > 0

    /** Whether the item is currently in ON state */
    val isOn: Boolean get() = isActive

    /** Numeric state value (for range items) */
    val numericState: Double?
        get() = state.replace(" .*".toRegex(), "").toDoubleOrNull()
}

@Serializable
data class StateDescription(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val step: Double? = null,
    val pattern: String? = null,
    @SerialName("readOnly")
    val isReadOnly: Boolean = false,
    val options: List<StateOption>? = null
)

@Serializable
data class StateOption(
    val value: String,
    val label: String? = null
)

@Serializable
data class CommandDescription(
    val commandOptions: List<CommandOption> = emptyList()
)

@Serializable
data class CommandOption(
    val command: String,
    val label: String? = null
)

@Serializable
data class MetadataEntry(
    val value: String = "",
    val config: Map<String, String>? = null
)
