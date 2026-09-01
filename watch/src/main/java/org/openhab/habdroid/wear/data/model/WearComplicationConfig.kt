package org.openhab.habdroid.wear.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed complication configuration from the wear:complication-list document.
 * Contains per-type config blocks matching the Google ComplicationData naming.
 */
data class WearComplicationConfig(
    val item: String,
    val label: String = "",
    val icon: String = "",
    val slotNumber: Int = 0,
    /**
     * When true, tapping the complication only shows the item's fresh value (detail view)
     * instead of routing to an edit control, even for otherwise editable item types.
     */
    val readOnly: Boolean = false,
    val supportedTypes: Set<String> = emptySet(),
    val shortText: ShortTextTypeConfig = ShortTextTypeConfig(),
    val longText: LongTextTypeConfig = LongTextTypeConfig(),
    val rangedValue: RangedValueTypeConfig = RangedValueTypeConfig(),
    val monochromaticImage: MonochromaticImageTypeConfig = MonochromaticImageTypeConfig()
) {
    companion object {
        fun fromJson(config: JsonObject): WearComplicationConfig {
            val supportedTypes = config["supportedTypes"]?.let { element ->
                try {
                    kotlinx.serialization.json.Json.parseToJsonElement(element.toString())
                        .let { it as? kotlinx.serialization.json.JsonArray }
                        ?.mapNotNull { it.jsonPrimitive.content }
                        ?.toSet()
                } catch (_: Exception) { null }
            } ?: emptySet()

            return WearComplicationConfig(
                item = config.stringOrEmpty("item"),
                label = config.stringOrEmpty("label"),
                icon = config.stringOrEmpty("icon"),
                slotNumber = config["slotNumber"]?.jsonPrimitive?.doubleOrNull?.toInt()
                    ?: config["slotNumber"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: 0,
                readOnly = config["readOnly"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                supportedTypes = supportedTypes,
                shortText = config["shortText"]?.jsonObject?.let { obj ->
                    ShortTextTypeConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title")
                    )
                } ?: ShortTextTypeConfig(),
                longText = config["longText"]?.jsonObject?.let { obj ->
                    LongTextTypeConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title")
                    )
                } ?: LongTextTypeConfig(),
                rangedValue = config["rangedValue"]?.jsonObject?.let { obj ->
                    RangedValueTypeConfig(
                        text = obj.stringOrEmpty("text"),
                        title = obj.stringOrEmpty("title"),
                        min = obj["min"]?.jsonPrimitive?.doubleOrNull,
                        max = obj["max"]?.jsonPrimitive?.doubleOrNull
                    )
                } ?: RangedValueTypeConfig(),
                monochromaticImage = config["monochromaticImage"]?.jsonObject?.let { obj ->
                    MonochromaticImageTypeConfig(
                        monochromaticImage = obj.stringOrEmpty("monochromaticImage"),
                        activeIcon = obj.stringOrEmpty("activeIcon"),
                        inactiveIcon = obj.stringOrEmpty("inactiveIcon")
                    )
                } ?: MonochromaticImageTypeConfig()
            )
        }

        private fun JsonObject.stringOrEmpty(key: String): String =
            this[key]?.jsonPrimitive?.content ?: ""
    }
}

/** SHORT_TEXT type config. text = formatted value pattern, title = label. Both max 7 chars. */
data class ShortTextTypeConfig(
    val text: String = "",
    val title: String = ""
)

/** LONG_TEXT type config. text = formatted value pattern, title = label. No strict char limit. */
data class LongTextTypeConfig(
    val text: String = "",
    val title: String = ""
)

/** RANGED_VALUE type config. value comes from item state. min/max override stateDescription. */
data class RangedValueTypeConfig(
    val text: String = "",
    val title: String = "",
    val min: Double? = null,
    val max: Double? = null
)

/** MONOCHROMATIC_IMAGE type config. State-based icon switching. */
data class MonochromaticImageTypeConfig(
    val monochromaticImage: String = "",
    val activeIcon: String = "",
    val inactiveIcon: String = ""
) {
    fun iconForState(isActive: Boolean): String {
        return when {
            isActive && activeIcon.isNotBlank() -> activeIcon
            !isActive && inactiveIcon.isNotBlank() -> inactiveIcon
            monochromaticImage.isNotBlank() -> monochromaticImage
            else -> ""
        }
    }
}
