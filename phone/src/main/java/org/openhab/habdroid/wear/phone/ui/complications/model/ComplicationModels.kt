package org.openhab.habdroid.wear.phone.ui.complications.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// ─── REST API DTO ───

/**
 * The wear:complication-list document as stored in /rest/ui/components/wear:tile.
 */
@Serializable
data class ComplicationListDto(
    val uid: String = UID,
    val tags: List<String> = emptyList(),
    val props: ComplicationProps = ComplicationProps(),
    val timestamp: String? = null,
    val component: String = COMPONENT,
    val config: JsonObject = JsonObject(emptyMap()),
    val slots: ComplicationSlots = ComplicationSlots()
) {
    companion object {
        const val UID = "complications"
        const val COMPONENT = "wear:complication-list"
    }
}

@Serializable
data class ComplicationProps(
    val parameters: List<String> = emptyList(),
    val parameterGroups: List<String> = emptyList()
)

@Serializable
data class ComplicationSlots(
    val default: List<ComplicationSlotDto> = emptyList()
)

@Serializable
data class ComplicationSlotDto(
    val component: String = "wear:complication-slot",
    val config: JsonObject = JsonObject(emptyMap())
)

// ─── Editor State Models ───

/**
 * Editor state for a single complication slot.
 * Contains per-type configuration blocks.
 */
data class ComplicationState(
    val item: String,
    val label: String = "",
    val icon: String = "",
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
            return ComplicationState(
                item = config.stringOrEmpty("item"),
                label = config.stringOrEmpty("label"),
                icon = config.stringOrEmpty("icon"),
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
 * Overall complication editor state.
 */
data class ComplicationEditorState(
    val complications: List<ComplicationState> = emptyList(),
    val allItems: List<ComplicationItem> = emptyList()
) {
    fun toDto(): ComplicationListDto = ComplicationListDto(
        slots = ComplicationSlots(
            default = complications.map { it.toDto() }
        )
    )

    companion object {
        fun fromDto(dto: ComplicationListDto, items: List<ComplicationItem> = emptyList()): ComplicationEditorState {
            return ComplicationEditorState(
                complications = dto.slots.default.map { ComplicationState.fromDto(it) },
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
