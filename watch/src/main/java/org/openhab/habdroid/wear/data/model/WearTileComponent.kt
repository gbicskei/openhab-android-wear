package org.openhab.habdroid.wear.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a UI component document from /rest/ui/components/wear:tile.
 * Can be a tile page (wear:tile-page) or complication list (wear:complication-list).
 */
@Serializable
data class WearTileComponent(
    val uid: String,
    val tags: List<String> = emptyList(),
    val timestamp: String? = null,
    val component: String = "",
    val config: TilePageConfig = TilePageConfig(),
    val slots: TileSlots = TileSlots()
) {
    val isTilePage: Boolean get() = component == COMPONENT_TILE_PAGE
    val isComplicationList: Boolean get() = component == COMPONENT_COMPLICATION_LIST

    companion object {
        const val COMPONENT_TILE_PAGE = "wear:tile-page"
        const val COMPONENT_COMPLICATION_LIST = "wear:complication-list"
    }
}

@Serializable
data class TilePageConfig(
    val label: String = "",
    val layout: Double = 6.0,
    val configVersion: Double = 0.0
) {
    val layoutInt: Int get() = layout.toInt().coerceIn(1, 7)
    val configVersionInt: Int get() = configVersion.toInt()
}

@Serializable
data class TileSlots(
    val default: List<TileSlotComponent> = emptyList()
)

@Serializable
data class TileSlotComponent(
    val component: String = "wear:tile-slot",
    val config: TileSlotConfig = TileSlotConfig()
)

@Serializable
data class TileSlotConfig(
    val position: Double = 1.0,
    val item: String? = null,
    val icon: String? = null,
    val label: String? = null,
    val stateDisplay: String? = null,
    val action: String? = null,
    val actionCommand: String? = null,
    val actionItem: String? = null,
    val stateItem: String? = null,
    val invertState: Boolean = false,
    val actionConfirmation: Boolean = false,
    val aggregateState: Boolean = false
) {
    val positionInt: Int get() = position.toInt().coerceIn(1, 7)
}
