package org.openhab.habdroid.wear.phone.ui.tiledesign.model

import kotlinx.serialization.Serializable

/**
 * A tile page document as stored in /rest/ui/components/wear:tile.
 * Maps directly to the REST API response structure.
 */
@Serializable
data class WearTilePageDto(
    val uid: String,
    val tags: List<String> = emptyList(),
    val props: Props = Props(),
    val timestamp: String? = null,
    val component: String = COMPONENT_TILE_PAGE,
    val config: PageConfig = PageConfig(),
    val slots: Slots = Slots()
) {
    companion object {
        const val COMPONENT_TILE_PAGE = "wear:tile-page"
        const val COMPONENT_COMPLICATION_LIST = "wear:complication-list"
    }

    val isTilePage: Boolean get() = component == COMPONENT_TILE_PAGE
    val isComplicationList: Boolean get() = component == COMPONENT_COMPLICATION_LIST
}

@Serializable
data class Props(
    val parameters: List<String> = emptyList(),
    val parameterGroups: List<String> = emptyList()
)

@Serializable
data class PageConfig(
    val label: String = "",
    val layout: Double = 6.0,
    val configVersion: Double = 0.0,
    val theme: String = ""
) {
    val layoutInt: Int get() = layout.toInt().coerceIn(1, 7)
    val configVersionInt: Int get() = configVersion.toInt()
}

@Serializable
data class Slots(
    val default: List<SlotDto> = emptyList()
)

@Serializable
data class SlotDto(
    val component: String = "wear:tile-slot",
    val config: SlotConfig = SlotConfig()
)

@Serializable
data class SlotConfig(
    val position: Double = 1.0, // openHAB stores as float
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
    val aggregateState: Boolean = false,
    val doubleTapItem: String? = null,
    val doubleTapAction: String? = null,
    val doubleTapCommand: String? = null,
    val doubleTapConfirmation: Boolean = false,
    val doubleTapStateDisplay: String? = null
) {
    val positionInt: Int get() = position.toInt().coerceIn(1, 7)
}

// ─── Editor State Models ───

/**
 * Editor state for a single tile slot.
 */
data class TileSlotState(
    val position: Int,
    val item: String? = null,
    val icon: String? = null,
    val label: String? = null,
    val stateDisplay: StateDisplay = StateDisplay.VALUE,
    val action: SlotAction = SlotAction.Toggle,
    val actionCommand: String? = null,
    val actionItem: String? = null,
    val stateItem: String? = null,
    val invertState: Boolean = false,
    val actionConfirmation: Boolean = false,
    val aggregateState: Boolean = false,
    val doubleTapItem: String? = null,
    val doubleTapAction: SlotAction? = null,
    val doubleTapCommand: String? = null,
    val doubleTapConfirmation: Boolean = false,
    val doubleTapStateDisplay: StateDisplay = StateDisplay.NONE
) {
    val isEmpty: Boolean get() = item == null && action !is SlotAction.Navigate
    val effectiveLabel: String get() = label ?: item ?: ""
    val effectiveIcon: String get() = icon ?: "none"
    val hasDoubleTap: Boolean get() = doubleTapItem != null

    fun toSlotDto(): SlotDto = SlotDto(
        component = "wear:tile-slot",
        config = SlotConfig(
            position = position.toDouble(),
            item = item,
            icon = icon,
            label = label,
            stateDisplay = stateDisplay.apiValue,
            action = when (action) {
                is SlotAction.Auto -> null
                is SlotAction.Toggle -> "toggle"
                is SlotAction.Command -> "command"
                is SlotAction.Navigate -> "page:${action.targetPage}"
            },
            actionCommand = actionCommand,
            actionItem = actionItem,
            stateItem = stateItem,
            invertState = invertState,
            actionConfirmation = actionConfirmation,
            aggregateState = aggregateState,
            doubleTapItem = doubleTapItem,
            doubleTapAction = when (doubleTapAction) {
                is SlotAction.Auto -> null
                is SlotAction.Toggle -> "toggle"
                is SlotAction.Command -> "command"
                else -> null
            },
            doubleTapCommand = doubleTapCommand,
            doubleTapConfirmation = doubleTapConfirmation,
            doubleTapStateDisplay = if (doubleTapItem != null) doubleTapStateDisplay.apiValue else null
        )
    )

    companion object {
        fun fromDto(dto: SlotDto): TileSlotState {
            val config = dto.config
            val actionStr = config.action
            val action = when {
                actionStr == null -> SlotAction.Auto
                actionStr == "toggle" -> SlotAction.Toggle
                actionStr == "command" -> SlotAction.Command
                actionStr.startsWith("page:") -> SlotAction.Navigate(actionStr.removePrefix("page:"))
                else -> SlotAction.Auto
            }
            return TileSlotState(
                position = config.positionInt,
                item = config.item,
                icon = config.icon,
                label = config.label,
                stateDisplay = StateDisplay.fromApi(config.stateDisplay),
                action = action,
                actionCommand = config.actionCommand,
                actionItem = config.actionItem,
                stateItem = config.stateItem,
                invertState = config.invertState,
                actionConfirmation = config.actionConfirmation,
                aggregateState = config.aggregateState,
                doubleTapItem = config.doubleTapItem,
                doubleTapAction = when (config.doubleTapAction) {
                    "toggle" -> SlotAction.Toggle
                    "command" -> SlotAction.Command
                    else -> null
                },
                doubleTapCommand = config.doubleTapCommand,
                doubleTapConfirmation = config.doubleTapConfirmation,
                doubleTapStateDisplay = StateDisplay.fromApi(config.doubleTapStateDisplay)
            )
        }
    }
}

/** How an item's state is rendered on the tile. */
enum class StateDisplay(val apiValue: String) {
    VALUE("value"),
    COLOR("color"),
    /** No state indicator — icon only, useful for command-only buttons */
    NONE("none");

    companion object {
        fun fromApi(value: String?): StateDisplay = when (value?.lowercase()) {
            "color" -> COLOR
            "none" -> NONE
            else -> VALUE
        }
    }
}

/** Action type for a tile slot. */
sealed interface SlotAction {
    /** Auto-detect from item type (range → slider, color → picker, switch → toggle) */
    data object Auto : SlotAction
    /** Force toggle ON/OFF regardless of item type */
    data object Toggle : SlotAction
    data object Command : SlotAction
    data class Navigate(val targetPage: String) : SlotAction
}

/**
 * Editor state for one tile page.
 */
data class TilePageState(
    val uid: String,
    val label: String = "",
    val layout: Int = 6,
    val slots: List<TileSlotState> = emptyList(),
    val configVersion: Int = 0
) {
    val isMain: Boolean get() = uid == "main"
    val filledSlotCount: Int get() = slots.count { !it.isEmpty }

    fun toDto(): WearTilePageDto = WearTilePageDto(
        uid = uid,
        component = WearTilePageDto.COMPONENT_TILE_PAGE,
        config = PageConfig(label = label, layout = layout.toDouble(), configVersion = configVersion.toDouble()),
        slots = Slots(default = slots.filter { !it.isEmpty }.map { it.toSlotDto() })
    )

    companion object {
        fun fromDto(dto: WearTilePageDto): TilePageState {
            val slotStates = dto.slots.default.map { TileSlotState.fromDto(it) }
            return TilePageState(
                uid = dto.uid,
                label = dto.config.label,
                layout = dto.config.layoutInt,
                slots = slotStates,
                configVersion = dto.config.configVersionInt
            )
        }
    }
}

/**
 * Overall tile editor state.
 */
data class TileEditorState(
    val pages: List<TilePageState> = listOf(TilePageState(uid = "main", label = "Main")),
    val currentPageIndex: Int = 0,
    val allItems: List<PhoneItem> = emptyList()
) {
    val currentPage: TilePageState get() = pages.getOrElse(currentPageIndex) { pages.first() }
    val pageNames: List<String> get() = pages.map { it.uid }
    val pageLabels: List<String> get() = pages.map { it.label.ifBlank { it.uid.replaceFirstChar { c -> c.uppercase() } } }
}

// ─── Item Model (for picker) ───

/**
 * Represents an openHAB item for the item picker.
 */
@Serializable
data class PhoneItem(
    val name: String,
    val label: String? = null,
    val type: String = "",
    val state: String = "NULL",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val groupNames: List<String> = emptyList()
) {
    val displayLabel: String get() = label ?: name
    val iconName: String get() = category ?: "none"

    val isToggleable: Boolean
        get() = type in listOf("Switch", "Dimmer", "Color", "Group")

    val isContact: Boolean
        get() = type == "Contact"
}


// ─── Migration Model (for importing from item metadata) ───

/**
 * Item model that includes metadata (used only for import/migration from wearTile metadata).
 */
@Serializable
data class PhoneItemWithMetadata(
    val name: String,
    val label: String? = null,
    val type: String = "",
    val state: String = "NULL",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val groupNames: List<String> = emptyList(),
    val metadata: Map<String, PhoneMetadataEntry>? = null
)

@Serializable
data class PhoneMetadataEntry(
    val value: String = "",
    val config: Map<String, String>? = null
)


// ─── Preview Icon State (matches watch IconState) ───

/**
 * Three-state display mode matching the watch's IconState.
 * Used by the phone tile preview to render buttons with correct coloring.
 */
enum class PreviewIconState {
    /** Item is on/open/active — full glow, full opacity ring and icon */
    ACTIVE,
    /** Item has no boolean state (page nav, value display, range, stateDisplay=none) — moderate opacity */
    NEUTRAL,
    /** Item is off/closed/inactive — dimmed ring and icon */
    INACTIVE
}

/**
 * Resolves the icon state for a tile slot, matching the watch's logic exactly.
 *
 * Watch logic (from OpenHabTileService.onTileResourcesRequest):
 * - Navigation buttons: ACTIVE if valueItem/own state/aggregate is active, else NEUTRAL
 * - stateDisplay=color: ACTIVE if display item is active (respecting invertState), else INACTIVE
 * - stateDisplay=none: always NEUTRAL
 * - stateDisplay=value: always NEUTRAL
 *
 * @param slot The tile slot configuration
 * @param itemStates Map of item name → current state string
 * @param pages All pages (needed for aggregate state on nav buttons)
 */
fun resolvePreviewIconState(
    slot: TileSlotState,
    itemStates: Map<String, String>,
    pages: List<TilePageState> = emptyList()
): PreviewIconState {
    if (slot.isEmpty) return PreviewIconState.NEUTRAL

    val isNavigation = slot.action is SlotAction.Navigate

    if (isNavigation) {
        // Navigation buttons: ACTIVE if state source is active, else NEUTRAL
        val isActive = when {
            // Priority 1: explicit stateItem
            slot.stateItem != null -> isItemActive(slot.stateItem, itemStates, slot.invertState)
            // Priority 2: own item state
            slot.item != null && itemStates[slot.item] !in listOf(null, "NULL", "UNDEF") ->
                isItemActive(slot.item, itemStates, slot.invertState)
            // Priority 3: aggregate from target page items
            slot.aggregateState -> {
                val targetPage = (slot.action as SlotAction.Navigate).targetPage
                pages.find { it.uid == targetPage }?.slots
                    ?.filter { it.action !is SlotAction.Navigate && !it.isEmpty }
                    ?.any { subSlot ->
                        val subItem = subSlot.stateItem ?: subSlot.item
                        subItem != null && isItemActive(subItem, itemStates, subSlot.invertState)
                    } ?: false
            }
            else -> false
        }
        return if (isActive) PreviewIconState.ACTIVE else PreviewIconState.NEUTRAL
    }

    // Regular items — determine which source provides the COLOR state
    val colorSource = when {
        slot.stateDisplay == StateDisplay.COLOR -> "primary"
        slot.hasDoubleTap && slot.doubleTapStateDisplay == StateDisplay.COLOR -> "doubleTap"
        else -> null
    }

    return when (colorSource) {
        "primary" -> {
            val displayItem = slot.stateItem ?: slot.item
            if (displayItem != null && isItemActive(displayItem, itemStates, slot.invertState)) {
                PreviewIconState.ACTIVE
            } else {
                PreviewIconState.INACTIVE
            }
        }
        "doubleTap" -> {
            val dblItem = slot.doubleTapItem
            if (dblItem != null && isItemActive(dblItem, itemStates, false)) {
                PreviewIconState.ACTIVE
            } else {
                PreviewIconState.INACTIVE
            }
        }
        else -> PreviewIconState.NEUTRAL
    }
}

/**
 * Check if an item is in an active state, matching the watch Item.isActive logic.
 * Active = ON, OPEN, numeric > 0, or HSB brightness > 0.
 */
private fun isItemActive(itemName: String, itemStates: Map<String, String>, invertState: Boolean): Boolean {
    val state = itemStates[itemName] ?: return false
    val raw = state == "ON" || state == "OPEN" ||
        (state.toIntOrNull() ?: 0) > 0 ||
        (state.contains(',') && (state.split(',').getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0) > 0)
    return if (invertState) !raw else raw
}

/**
 * Resolves the state text to display below the icon, matching the watch logic.
 * Returns null if no state text should be shown (COLOR, NONE, or navigation).
 * Returns "–" for NULL/UNDEF states (same as watch formatState).
 */
fun resolvePreviewStateText(
    slot: TileSlotState,
    itemStates: Map<String, String>,
    allItems: List<PhoneItem> = emptyList()
): String? {
    if (slot.isEmpty) return null
    if (slot.action is SlotAction.Navigate) return null

    // Determine which source provides the VALUE display
    val valueSource = when {
        slot.stateDisplay == StateDisplay.VALUE -> "primary"
        slot.hasDoubleTap && slot.doubleTapStateDisplay == StateDisplay.VALUE -> "doubleTap"
        else -> null
    }

    val displayItem = when (valueSource) {
        "primary" -> slot.stateItem ?: slot.item ?: return null
        "doubleTap" -> slot.doubleTapItem ?: return null
        else -> return null
    }

    val state = itemStates[displayItem]

    // NULL/UNDEF/missing → en-dash (same as watch)
    if (state == null || state in listOf("NULL", "UNDEF")) return "\u2013"

    // Format with unit based on item type (matching watch formatRangeState)
    val itemType = allItems.find { it.name == displayItem }?.type ?: ""
    return formatStateText(state, itemType)
}

/**
 * Format a state string for display, matching the watch's formatRangeState/formatState logic.
 * Adds unit suffixes based on item type. Truncates to 6 chars with ellipsis.
 */
private fun formatStateText(state: String, itemType: String = ""): String {
    val numeric = state.split(" ").first().toDoubleOrNull()

    val formatted = when {
        // Dimmer/Rollershutter: show as percentage
        numeric != null && (itemType == "Dimmer" || itemType == "Rollershutter") ->
            "${numeric.toInt()}%"
        // Temperature: show with degree symbol
        numeric != null && itemType.startsWith("Number:Temperature") ->
            "${String.format("%.0f", numeric)}\u00B0"
        // Quantity state from server (e.g. "22.5 °C") — compact by removing space
        state.contains(" ") -> state.replace(" ", "")
        // Plain numeric or string
        else -> state
    }

    return if (formatted.length > 6) formatted.take(6) + "\u2026" else formatted
}
