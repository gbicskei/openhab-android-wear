package org.openhab.habdroid.wear.phone.ui.complications.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ─── REST API DTO ───

/**
 * The wear:complication-list document as stored in /rest/ui/components/wear:tile.
 *
 * Uses fixed numbered slots (slot1–slot10) instead of a dynamic list.
 * Each slot is either a configured ComplicationSlotDto or null (empty).
 * The watch registers 10 ComplicationDataSourceServices, one per slot,
 * and enables/disables them based on which slots are configured here.
 */
@Serializable
data class ComplicationListDto(
    val uid: String = UID,
    val tags: List<String> = emptyList(),
    val props: ComplicationProps = ComplicationProps(),
    val timestamp: String? = null,
    val component: String = COMPONENT,
    val config: JsonObject = JsonObject(emptyMap()),
    val slots: ComplicationSlotsDto = ComplicationSlotsDto()
) {
    companion object {
        const val UID = "complications"
        const val COMPONENT = "wear:complication-list"
        const val MAX_SLOTS = 10
    }
}

@Serializable
data class ComplicationProps(
    val parameters: List<String> = emptyList(),
    val parameterGroups: List<String> = emptyList()
)

/**
 * Fixed slot container using the server's required format: slots.default is an array.
 * Each entry in the array has a `slotNumber` field in its config to identify which
 * of the 10 fixed slots it belongs to. Slots not in the array are empty/disabled.
 */
@Serializable
data class ComplicationSlotsDto(
    val default: List<ComplicationSlotDto> = emptyList()
) {
    /** Get slot config by 1-based slot number, or null if not configured. */
    operator fun get(slotNumber: Int): ComplicationSlotDto? =
        default.find { it.slotNumber == slotNumber }

    /** Returns all configured slots as a map of slotNumber → dto. */
    fun toMap(): Map<Int, ComplicationSlotDto> =
        default.filter { it.slotNumber in 1..ComplicationListDto.MAX_SLOTS }
            .associateBy { it.slotNumber }

    /** Number of configured slots. */
    val configuredCount: Int get() = default.size

    companion object {
        /** Build from a map of slotNumber → dto (null entries are omitted). */
        fun fromMap(map: Map<Int, ComplicationSlotDto?>): ComplicationSlotsDto {
            val entries = map.entries
                .filter { it.value != null && it.key in 1..ComplicationListDto.MAX_SLOTS }
                .sortedBy { it.key }
                .map { (slotNum, dto) -> dto!!.withSlotNumber(slotNum) }
            return ComplicationSlotsDto(default = entries)
        }
    }
}

@Serializable
data class ComplicationSlotDto(
    val component: String = "wear:complication-slot",
    val config: JsonObject = JsonObject(emptyMap())
) {
    /** Extract the slot number from config, defaulting to 0 if not present. */
    val slotNumber: Int get() =
        config["slotNumber"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
            ?: config["slotNumber"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: 0

    /** Return a copy with slotNumber set in config. */
    fun withSlotNumber(slot: Int): ComplicationSlotDto {
        val updatedConfig = JsonObject(config.toMutableMap().apply {
            put("slotNumber", JsonPrimitive(slot))
        })
        return copy(config = updatedConfig)
    }
}

// ─── Editor State Models ───

/**
 * Supported complication display types.
 * Maps to Wear OS ComplicationType constants.
 */
enum class ComplicationType(val displayName: String) {
    SHORT_TEXT("Short Text"),
    LONG_TEXT("Long Text"),
    RANGED_VALUE("Ranged Value"),
    MONOCHROMATIC_IMAGE("Icon");

    companion object {
        /** All types suitable for numeric items (Number, Dimmer, etc.) */
        fun forNumericItem(): Set<ComplicationType> = entries.toSet()

        /** Types suitable for non-numeric items (String, Switch, Contact, etc.) */
        fun forNonNumericItem(): Set<ComplicationType> =
            setOf(SHORT_TEXT, LONG_TEXT, MONOCHROMATIC_IMAGE)

        /** Determine default supported types based on item type string. */
        fun defaultsForItemType(itemType: String): Set<ComplicationType> {
            val isNumeric = itemType.startsWith("Number") ||
                itemType == "Dimmer" ||
                itemType == "Color" ||
                (itemType.startsWith("Group") && itemType.contains(":"))
            return if (isNumeric) forNumericItem() else forNonNumericItem()
        }
    }
}

/**
 * Editor state for a single complication slot.
 * Contains per-type configuration blocks and explicit supported types.
 */
data class ComplicationState(
    val item: String,
    val label: String = "",
    val icon: String = "",
    /** Which complication types this slot advertises. The watch only responds to these types. */
    val supportedTypes: Set<ComplicationType> = setOf(
        ComplicationType.SHORT_TEXT,
        ComplicationType.LONG_TEXT,
        ComplicationType.MONOCHROMATIC_IMAGE
    ),
    val shortText: ShortTextConfig = ShortTextConfig(),
    val longText: LongTextConfig = LongTextConfig(),
    val rangedValue: RangedValueConfig = RangedValueConfig(),
    val monochromaticImage: MonochromaticImageConfig = MonochromaticImageConfig()
) {
    /**
     * Convert editor state to DTO for persistence.
     */
    fun toDto(): ComplicationSlotDto {
        val configMap = buildJsonObject {
            put("item", item)
            if (label.isNotBlank()) put("label", label)
            if (icon.isNotBlank()) put("icon", icon)

            // Supported types — always persisted so the watch knows what to advertise
            put("supportedTypes", JsonArray(supportedTypes.map { JsonPrimitive(it.name) }))

            // Short text config
            if (shortText.isConfigured) {
                put("shortText", buildJsonObject {
                    if (shortText.text.isNotBlank()) put("text", shortText.text)
                    if (shortText.title.isNotBlank()) put("title", shortText.title)
                })
            }

            // Long text config
            if (longText.isConfigured) {
                put("longText", buildJsonObject {
                    if (longText.text.isNotBlank()) put("text", longText.text)
                    if (longText.title.isNotBlank()) put("title", longText.title)
                })
            }

            // Ranged value config
            if (rangedValue.isConfigured) {
                put("rangedValue", buildJsonObject {
                    if (rangedValue.text.isNotBlank()) put("text", rangedValue.text)
                    if (rangedValue.title.isNotBlank()) put("title", rangedValue.title)
                    if (rangedValue.min != null) put("min", rangedValue.min)
                    if (rangedValue.max != null) put("max", rangedValue.max)
                })
            }

            // Monochromatic image config
            if (monochromaticImage.isConfigured) {
                put("monochromaticImage", buildJsonObject {
                    if (monochromaticImage.monochromaticImage.isNotBlank()) put("monochromaticImage", monochromaticImage.monochromaticImage)
                    if (monochromaticImage.activeIcon.isNotBlank()) put("activeIcon", monochromaticImage.activeIcon)
                    if (monochromaticImage.inactiveIcon.isNotBlank()) put("inactiveIcon", monochromaticImage.inactiveIcon)
                })
            }
        }

        return ComplicationSlotDto(
            component = "wear:complication-slot",
            config = configMap
        )
    }

    companion object {
        fun fromDto(dto: ComplicationSlotDto): ComplicationState {
            val config = dto.config

            // Parse supportedTypes array; default to SHORT_TEXT + LONG_TEXT + MONOCHROMATIC_IMAGE
            val typesArray = config["supportedTypes"]?.jsonArray
            val supportedTypes = if (typesArray != null) {
                typesArray.mapNotNull { element ->
                    try { ComplicationType.valueOf(element.jsonPrimitive.content) } catch (_: Exception) { null }
                }.toSet()
            } else {
                setOf(ComplicationType.SHORT_TEXT, ComplicationType.LONG_TEXT, ComplicationType.MONOCHROMATIC_IMAGE)
            }

            return ComplicationState(
                item = config.stringOrEmpty("item"),
                label = config.stringOrEmpty("label"),
                icon = config.stringOrEmpty("icon"),
                supportedTypes = supportedTypes,
                shortText = config["shortText"]?.jsonObject?.let { obj ->
                    ShortTextConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title")
                    )
                } ?: ShortTextConfig(),
                longText = config["longText"]?.jsonObject?.let { obj ->
                    LongTextConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title")
                    )
                } ?: LongTextConfig(),
                rangedValue = config["rangedValue"]?.jsonObject?.let { obj ->
                    RangedValueConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title"),
                        min = obj["min"]?.jsonPrimitive?.doubleOrNull,
                        max = obj["max"]?.jsonPrimitive?.doubleOrNull
                    )
                } ?: RangedValueConfig(),
                monochromaticImage = config["monochromaticImage"]?.jsonObject?.let { obj ->
                    MonochromaticImageConfig(
                        monochromaticImage = obj.stringOrEmpty("monochromaticImage"),
                        activeIcon = obj.stringOrEmpty("activeIcon"),
                        inactiveIcon = obj.stringOrEmpty("inactiveIcon")
                    )
                } ?: MonochromaticImageConfig()
            )
        }

        private fun JsonObject.stringOrEmpty(key: String): String =
            this[key]?.jsonPrimitive?.content ?: ""
    }
}

/**
 * SHORT_TEXT config.
 * Maps to ShortTextComplicationData fields.
 * - text: the main value (max 7 chars). Use pattern format e.g. "%.0f°C"
 * - title: optional label (max 7 chars)
 */
data class ShortTextConfig(
    val text: String = "",
    val title: String = ""
) {
    val isConfigured: Boolean get() = text.isNotBlank() || title.isNotBlank()

    companion object {
        const val MAX_TEXT_LENGTH = 7
        const val MAX_TITLE_LENGTH = 7
    }
}

/**
 * LONG_TEXT config.
 * Maps to LongTextComplicationData fields.
 * - text: the main value (no strict limit). Use pattern format e.g. "Temperature: %.1f °C"
 * - title: optional label
 */
data class LongTextConfig(
    val text: String = "",
    val title: String = ""
) {
    val isConfigured: Boolean get() = text.isNotBlank() || title.isNotBlank()
}

/**
 * RANGED_VALUE config.
 * Maps to RangedValueComplicationData fields.
 * - value is always the item's numeric state (not configurable)
 * - min/max: range bounds (falls back to item's stateDescription)
 * - text: optional formatted value text (max 7 chars)
 * - title: optional label (max 7 chars)
 */
data class RangedValueConfig(
    val text: String = "",
    val title: String = "",
    val min: Double? = null,
    val max: Double? = null
) {
    val isConfigured: Boolean get() =
        text.isNotBlank() || title.isNotBlank() || min != null || max != null

    companion object {
        const val MAX_TEXT_LENGTH = 7
        const val MAX_TITLE_LENGTH = 7
    }
}

/**
 * MONOCHROMATIC_IMAGE config.
 * Maps to MonochromaticImageComplicationData.
 * - monochromaticImage: the icon reference
 * - activeIcon/inactiveIcon: optional state-dependent overrides
 */
data class MonochromaticImageConfig(
    val monochromaticImage: String = "",
    val activeIcon: String = "",
    val inactiveIcon: String = ""
) {
    val isConfigured: Boolean get() =
        monochromaticImage.isNotBlank() || activeIcon.isNotBlank() || inactiveIcon.isNotBlank()

    /** Effective icon for a given active state. Falls back to main icon. */
    fun iconForState(isActive: Boolean): String {
        return when {
            isActive && activeIcon.isNotBlank() -> activeIcon
            !isActive && inactiveIcon.isNotBlank() -> inactiveIcon
            monochromaticImage.isNotBlank() -> monochromaticImage
            else -> ""
        }
    }
}

/**
 * Overall complication editor state with fixed 10 slots.
 * Slots are indexed 1–10. A null value means the slot is empty.
 */
data class ComplicationEditorState(
    val slots: Map<Int, ComplicationState?> = (1..ComplicationListDto.MAX_SLOTS).associateWith { null },
    val allItems: List<ComplicationItem> = emptyList()
) {
    /** Number of configured slots. */
    val configuredCount: Int get() = slots.count { it.value != null }

    fun toDto(): ComplicationListDto {
        val slotDtos = slots.mapValues { (_, state) -> state?.toDto() }
        return ComplicationListDto(
            slots = ComplicationSlotsDto.fromMap(slotDtos)
        )
    }

    companion object {
        fun fromDto(dto: ComplicationListDto, items: List<ComplicationItem> = emptyList()): ComplicationEditorState {
            val configuredSlots = dto.slots.toMap()

            val slotMap: Map<Int, ComplicationState?> = if (configuredSlots.isNotEmpty()) {
                // Entries have slotNumber — use it
                val map = (1..ComplicationListDto.MAX_SLOTS).associateWith { slotNum ->
                    configuredSlots[slotNum]?.let { ComplicationState.fromDto(it) }
                }
                map
            } else if (dto.slots.default.isNotEmpty()) {
                // Legacy format: entries without slotNumber — assign sequentially
                val map = mutableMapOf<Int, ComplicationState?>()
                dto.slots.default.forEachIndexed { index, slotDto ->
                    val slotNum = index + 1
                    if (slotNum <= ComplicationListDto.MAX_SLOTS) {
                        map[slotNum] = ComplicationState.fromDto(slotDto)
                    }
                }
                (1..ComplicationListDto.MAX_SLOTS).associateWith { map[it] }
            } else {
                (1..ComplicationListDto.MAX_SLOTS).associateWith { null }
            }

            return ComplicationEditorState(
                slots = slotMap,
                allItems = items
            )
        }
    }
}

/**
 * Simplified item model for the complication item picker.
 */
@Serializable
data class ComplicationItem(
    val name: String,
    val label: String? = null,
    val type: String = "",
    val state: String = "NULL",
    val category: String? = null
) {
    val displayLabel: String get() = label ?: name
    val isNumeric: Boolean get() = type.startsWith("Number") || type == "Dimmer"
}
