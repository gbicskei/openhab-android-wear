package org.openhab.habdroid.wear.data.model

/**
 * Represents an item configured for display on the watch tile.
 * Combines the item data with its wearTile metadata config.
 *
 * Item roles:
 * - The primary [item] is the one carrying the wearTile metadata.
 * - [valueItem] (optional): a separate item whose state is displayed on the button.
 *   When set, the primary item's state is ignored for display purposes.
 * - [commandItemName] (optional): a separate item that receives commands on tap.
 *   When set, commands go to this item instead of the primary item.
 *
 * [valueItem] and [commandItemName] are mutually exclusive:
 * - If valueItem is set: primary = command target, valueItem = display source
 * - If commandItemName is set: primary = display source, commandItemName = command target
 */
data class TileItem(
    val item: Item,
    val page: String = PAGE_MAIN,
    val slot: Int = 1,
    val pageLayout: Int = 6,
    val icon: String? = null,
    val label: String? = null,
    val needsConfirmation: Boolean = false,
    val valueDisplay: ValueDisplay = ValueDisplay.VALUE,
    /** Navigation or command action — "page:{name}" for navigation, "command" for fixed command, null for auto-toggle */
    val action: String? = null,
    /** Item name to read display state from (fetched separately, not from metadata query) */
    val valueItemName: String? = null,
    /** Resolved value item — populated after fetch. State comes from here when set. */
    val valueItem: Item? = null,
    /** If true, invert the active/inactive display interpretation */
    val invertValue: Boolean = false,
    /** Item name to send commands to instead of the primary item */
    val commandItemName: String? = null,
    /** Fixed command string to send on tap (used with action="command") */
    val commandValue: String? = null,
    /** If true, the nav button shows active when any item on the target page is active */
    val aggregateState: Boolean = false,
    /** Item name for double-tap action. If set, enables double-tap detection. */
    val doubleTapItem: String? = null,
    /** Double-tap action: null = auto-detect, "toggle" = force toggle, "command" = fixed command */
    val doubleTapAction: String? = null,
    /** Command to send on double-tap when doubleTapAction = "command" */
    val doubleTapCommand: String? = null,
    /** Show confirmation dialog before executing double-tap action */
    val doubleTapConfirmation: Boolean = false,
    /** State display mode for double-tap item: "color", "value", or "none" */
    val doubleTapStateDisplay: ValueDisplay = ValueDisplay.NONE
) : Comparable<TileItem> {
    override fun compareTo(other: TileItem): Int = slot.compareTo(other.slot)

    /** Effective icon name — metadata override or item's category */
    val effectiveIcon: String get() = icon ?: item.iconName

    /** Effective label — metadata override or item's displayLabel */
    val effectiveLabel: String get() = label ?: item.displayLabel

    /** The item whose state is used for display (valueItem if set, primary item otherwise) */
    val displayItem: Item get() = valueItem ?: item

    /** The item name that should receive commands on tap */
    val commandTargetName: String get() = commandItemName ?: item.name

    /**
     * Whether the display state is currently "active" (respects invertValue).
     * Used for color display mode to determine accent vs grey.
     */
    val isDisplayActive: Boolean
        get() {
            val rawActive = displayItem.isActive
            return if (invertValue) !rawActive else rawActive
        }

    /** The item name whose state should be tracked for display updates (SSE watch target) */
    val displayItemName: String get() = valueItemName ?: item.name

    /** Whether this tile item should show a toggle action (switch-like) */
    val isToggle: Boolean get() = when {
        action == "toggle" -> item.isToggleable // forced toggle (even for range items)
        action == null -> item.isToggleable && !item.isRange // auto: only if not range
        else -> false
    }

    /** Whether this is a fixed command button (sends commandValue, no toggle) */
    val isCommand: Boolean get() = action == "command"

    /** Whether this tile item should open a rotary control screen (only when auto-detect, not forced toggle) */
    val isRangeControl: Boolean get() = action == null && item.isRange

    /** Whether action is explicitly set to toggle (overrides auto-detect for range items) */
    val isForcedToggle: Boolean get() = action == "toggle"

    /** Whether this tile item has a double-tap secondary action configured */
    val hasDoubleTap: Boolean get() = doubleTapItem != null

    /** Raw action config string for passing to QuickActionActivity */
    val actionConfig: String? get() = action

    /** Whether this is a navigation button to another page */
    val isPageNavigation: Boolean get() = action?.startsWith("page:") == true

    /** Target page name for navigation buttons */
    val targetPage: String? get() = if (isPageNavigation) action?.removePrefix("page:") else null

    /** Legacy position (for backward compat with local selection store) */
    val position: Int get() = slot

    companion object {
        const val PAGE_MAIN = "main"

        /**
         * Parses a position string into (page, slot).
         * Supports formats:
         * - "1" or "3.0" → main page, slot 1 or 3
         * - "main:2" → main page, slot 2
         * - "security:1" → security page, slot 1
         */
        fun parsePosition(raw: String?): Pair<String, Int> {
            if (raw == null) return PAGE_MAIN to 1
            val parts = raw.split(":", limit = 2)
            return when (parts.size) {
                1 -> PAGE_MAIN to (parts[0].toDoubleOrNull()?.toInt() ?: 1)
                2 -> parts[0] to (parts[1].toDoubleOrNull()?.toInt() ?: 1)
                else -> PAGE_MAIN to 1
            }
        }
    }
}

/**
 * How the item's state is rendered on the tile.
 */
enum class ValueDisplay {
    /** Show state as text (ON/OFF, 60, 22.5°C) */
    VALUE,
    /** Show as color-highlighted circle (accent=active, grey=inactive, no text) */
    COLOR,
    /** No state indicator — neutral icon with no state text, useful for command-only buttons */
    NONE;

    companion object {
        fun fromString(value: String?): ValueDisplay = when (value?.lowercase()) {
            "color" -> COLOR
            "none" -> NONE
            else -> VALUE
        }
    }
}
