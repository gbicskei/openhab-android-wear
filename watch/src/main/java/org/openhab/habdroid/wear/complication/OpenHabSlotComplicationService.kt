package org.openhab.habdroid.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.openhab.habdroid.wear.util.AppLog
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.WearComplicationConfig
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Base class for fixed complication slot services (1–10).
 * Each subclass represents one slot and reads its config from the wear:complication-list document
 * on the openHAB server, identified by the `slotNumber` field in each entry's config.
 *
 * Slots that are not configured on the server are disabled via PackageManager so they
 * don't appear in the watch face complication picker.
 */
abstract class OpenHabSlotComplicationService : SuspendingComplicationDataSourceService() {

    /** The fixed slot number this service represents (1–10). */
    abstract val slotNumber: Int

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    @Inject
    lateinit var iconResolver: org.openhab.habdroid.wear.data.icon.IconResolver

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        AppLog.d(TAG, "onComplicationRequest() slot=$slotNumber, type=${request.complicationType}")

        // Fetch complication config from REST API
        val configs = repository.getComplicationConfigs().getOrNull() ?: emptyList()
        val config = configs.find { it.slotNumber == slotNumber }

        if (config == null || config.item.isBlank()) {
            AppLog.d(TAG, "Slot $slotNumber not configured — returning null")
            return null
        }

        // Fetch item state
        val itemResult = repository.getItem(config.item)
        val item = itemResult.getOrNull()
        if (item == null) {
            val error = itemResult.exceptionOrNull()
            AppLog.w(TAG, "Failed to fetch item '${config.item}': ${error?.message}", error)
            return buildErrorData(request.complicationType, config.item, error?.message ?: "Connection failed")
        }

        // Build ComplicationData based on requested type
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> buildShortText(item, config, request.complicationInstanceId)
            ComplicationType.LONG_TEXT -> buildLongText(item, config, request.complicationInstanceId)
            ComplicationType.RANGED_VALUE -> buildRangedValue(item, config, request.complicationInstanceId)
            ComplicationType.MONOCHROMATIC_IMAGE -> buildMonochromaticImage(item, config, request.complicationInstanceId)
            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        // Read cached label from SharedPreferences (set during sync)
        val prefs = getSharedPreferences("complication_labels", Context.MODE_PRIVATE)
        val label = prefs.getString("slot_${slotNumber}_label", null) ?: "Slot $slotNumber"

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(label).build()
                )
                    .setTitle(PlainComplicationText.Builder(label.take(7)).build())
                    .setMonochromaticImage(MonochromaticImage.Builder(createPlaceholderIcon()).build())
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(label).build(),
                    contentDescription = PlainComplicationText.Builder(label).build()
                )
                    .setMonochromaticImage(MonochromaticImage.Builder(createPlaceholderIcon()).build())
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = 50f, min = 0f, max = 100f,
                    contentDescription = PlainComplicationText.Builder(label).build()
                )
                    .setText(PlainComplicationText.Builder("--").build())
                    .setTitle(PlainComplicationText.Builder(label.take(7)).build())
                    .setMonochromaticImage(MonochromaticImage.Builder(createPlaceholderIcon()).build())
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(createPlaceholderIcon()).build(),
                    contentDescription = PlainComplicationText.Builder(label).build()
                ).build()

            else -> null
        }
    }

    // ─── Data Builders ───

    private fun buildShortText(item: Item, config: WearComplicationConfig, complicationId: Int): ComplicationData {
        val typeConfig = config.shortText
        val title = typeConfig.title.takeIf { it.isNotBlank() }
            ?: config.label.take(7).takeIf { it.isNotBlank() }
            ?: item.displayLabel.take(7)
        val text = formatValue(item, typeConfig.text)

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $text").build()
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildLongText(item: Item, config: WearComplicationConfig, complicationId: Int): ComplicationData {
        val typeConfig = config.longText
        val title = typeConfig.title.takeIf { it.isNotBlank() }
            ?: config.label.takeIf { it.isNotBlank() }
            ?: item.displayLabel
        val text = formatValue(item, typeConfig.text)

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("$title: $text").build()
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildRangedValue(item: Item, config: WearComplicationConfig, complicationId: Int): ComplicationData? {
        val typeConfig = config.rangedValue
        val value = item.numericState?.toFloat() ?: return buildShortText(item, config, complicationId)
        val min = typeConfig.min?.toFloat()
            ?: item.stateDescription?.minimum?.toFloat()
            ?: 0f
        val max = typeConfig.max?.toFloat()
            ?: item.stateDescription?.maximum?.toFloat()
            ?: if (item.type.startsWith("Dimmer")) 100f
            else (value * 2f).coerceAtLeast(100f)

        val title = typeConfig.title.takeIf { it.isNotBlank() }
            ?: config.label.take(7).takeIf { it.isNotBlank() }
            ?: item.displayLabel.take(7)
        val text = formatValue(item, typeConfig.text)

        return RangedValueComplicationData.Builder(
            value = value.coerceIn(min, max), min = min, max = max,
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $text").build()
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private suspend fun buildMonochromaticImage(item: Item, config: WearComplicationConfig, complicationId: Int): ComplicationData? {
        val typeConfig = config.monochromaticImage
        val iconRef = typeConfig.iconForState(item.isActive).takeIf { it.isNotBlank() }
            ?: config.icon.takeIf { it.isNotBlank() }
            ?: item.iconName

        val icon = resolveIconToBitmap(iconRef, item.state) ?: createPlaceholderIcon()

        return MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(icon).build(),
            contentDescription = PlainComplicationText.Builder(
                "${config.label.ifBlank { item.displayLabel }}: ${if (item.isActive) "Active" else "Inactive"}"
            ).build()
        )
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    // ─── Value Formatting ───

    private fun formatValue(item: Item, pattern: String?): String {
        // First: always try transformedState (server-formatted)
        val transformed = item.transformedState
        if (transformed != null && transformed !in listOf("NULL", "UNDEF")) return transformed.take(12)

        // Second: look up display label from stateDescription options or commandDescription
        val optionLabel = item.stateDescription?.options
            ?.find { it.value == item.state }
            ?.label
            ?: item.commandDescription?.commandOptions
                ?.find { it.command == item.state }
                ?.label
            ?: BUILT_IN_STATE_LABELS[item.state]
        if (optionLabel != null) return optionLabel.take(12)

        // Third: if a pattern is provided, format with it
        if (!pattern.isNullOrBlank()) {
            val numericValue = item.numericState
            if (numericValue != null) {
                return try {
                    if (pattern.contains("%d")) String.format(pattern, numericValue.toLong())
                    else String.format(pattern, numericValue)
                } catch (_: Exception) {
                    if (numericValue == numericValue.toLong().toDouble()) numericValue.toLong().toString()
                    else String.format("%.1f", numericValue)
                }
            }
            return try { String.format(pattern, item.state) } catch (_: Exception) { item.state.take(7) }
        }

        // Fourth: numeric auto-formatting
        val numericValue = item.numericState
        return when {
            item.state in listOf("NULL", "UNDEF") -> "\u2014"
            numericValue != null -> {
                val formatted = if (numericValue == numericValue.toLong().toDouble())
                    numericValue.toLong().toString() else String.format("%.1f", numericValue)
                val unit = if (item.type.contains(":")) getUnitSymbol(item.type) else null
                if (unit != null) "$formatted $unit" else formatted
            }
            else -> item.state.take(12)
        }
    }

    private fun getUnitSymbol(type: String): String? = when {
        type.contains("Temperature") -> "°C"
        type.contains("Pressure") -> "hPa"
        type.contains("Speed") -> "km/h"
        type.contains("Length") -> "m"
        type.contains("Power") -> "W"
        type.contains("Energy") -> "kWh"
        type.contains("Dimensionless") -> "%"
        else -> null
    }

    // ─── Error Handling ───

    private fun buildErrorData(type: ComplicationType, itemName: String, errorMessage: String): ComplicationData? {
        val shortError = when {
            errorMessage.contains("401") || errorMessage.contains("Unauthorized") -> "Auth err"
            errorMessage.contains("404") -> "Not found"
            errorMessage.contains("timeout", ignoreCase = true) -> "Timeout"
            errorMessage.contains("Unable to resolve host", ignoreCase = true) -> "No conn"
            errorMessage.contains("connect", ignoreCase = true) -> "No conn"
            else -> "Error"
        }
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(shortError).build(),
                    contentDescription = PlainComplicationText.Builder("$itemName: $errorMessage").build()
                ).setTitle(PlainComplicationText.Builder("\u26A0").build()).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$itemName: $shortError").build(),
                    contentDescription = PlainComplicationText.Builder(errorMessage).build()
                ).build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = 0f, min = 0f, max = 100f,
                    contentDescription = PlainComplicationText.Builder("$itemName: $errorMessage").build()
                ).setText(PlainComplicationText.Builder(shortError).build()).build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(createPlaceholderIcon()).build(),
                    contentDescription = PlainComplicationText.Builder("$itemName: $errorMessage").build()
                ).build()
            else -> null
        }
    }

    // ─── Icon Rendering ───

    private suspend fun resolveIconToBitmap(iconRef: String, state: String): android.graphics.drawable.Icon? {
        val rawBytes = iconResolver.resolve(iconRef, state) ?: return null
        val format = iconResolver.detectFormat(rawBytes)
        val size = 48
        return try {
            val bitmap = when (format) {
                org.openhab.habdroid.wear.data.icon.IconFormat.SVG -> {
                    val svgBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(svgBitmap)
                    val svg = com.caverock.androidsvg.SVG.getFromString(String(rawBytes, Charsets.UTF_8))
                    svg.documentWidth = size.toFloat()
                    svg.documentHeight = size.toFloat()
                    svg.renderToCanvas(canvas)
                    toMonochrome(svgBitmap)
                }
                org.openhab.habdroid.wear.data.icon.IconFormat.PNG -> {
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
                    val scaled = Bitmap.createScaledBitmap(decoded, size, size, true)
                    if (scaled !== decoded) decoded.recycle()
                    toMonochrome(scaled)
                }
                else -> return null
            }
            android.graphics.drawable.Icon.createWithBitmap(bitmap)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to render icon '$iconRef': ${e.message}")
            null
        }
    }

    private fun toMonochrome(source: Bitmap): Bitmap {
        val size = source.width
        val mono = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        source.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            pixels[i] = if (alpha > 30) Color.argb(alpha, 255, 255, 255) else Color.TRANSPARENT
        }
        mono.setPixels(pixels, 0, size, 0, 0, size, size)
        source.recycle()
        return mono
    }

    private fun createPlaceholderIcon(): android.graphics.drawable.Icon {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        return android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }

    private fun createDetailTapAction(complicationId: Int): PendingIntent {
        val intent = Intent(this, ComplicationTapActivity::class.java).apply {
            putExtra(ComplicationTapActivity.EXTRA_COMPLICATION_ID, complicationId)
            putExtra(ComplicationTapActivity.EXTRA_SLOT_NUMBER, slotNumber)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this, slotNumber * 100 + complicationId,
            intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val TAG = "SlotComplication"

        /** Built-in display labels for common raw state values without stateDescription options. */
        private val BUILT_IN_STATE_LABELS = mapOf(
            "ON" to "On",
            "OFF" to "Off",
            "OPEN" to "Open",
            "CLOSED" to "Closed"
        )
        const val MAX_SLOTS = 10

        /** All 10 slot service ComponentNames. */
        fun allSlotComponents(context: Context): List<ComponentName> =
            (1..MAX_SLOTS).map { slotComponentName(context, it) }

        /** ComponentName for a specific slot. */
        fun slotComponentName(context: Context, slotNumber: Int): ComponentName {
            val className = "${context.packageName}.complication.OpenHabComplicationSlot${slotNumber}Service"
            return ComponentName(context, className)
        }

        /**
         * Enable/disable slot services based on which slots have configs.
         * Also caches slot labels for the complication picker preview.
         * Called from: ComplicationUpdateWorker, reload handler, app launch.
         */
        suspend fun syncSlotEnabledState(context: Context, configs: List<WearComplicationConfig>) {
            val pm = context.packageManager
            val configuredSlots = configs.associateBy { it.slotNumber }

            // Cache labels for getPreviewData()
            val prefs = context.getSharedPreferences("complication_labels", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.clear()
            for ((slot, config) in configuredSlots) {
                editor.putString("slot_${slot}_label", config.label.ifBlank { config.item })
            }
            editor.apply()

            for (slot in 1..MAX_SLOTS) {
                val component = slotComponentName(context, slot)
                val shouldEnable = slot in configuredSlots
                val currentState = pm.getComponentEnabledSetting(component)
                val isCurrentlyEnabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

                if (shouldEnable) {
                    if (isCurrentlyEnabled) {
                        // Toggle off/on to force system to re-fetch getPreviewData()
                        pm.setComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                    AppLog.d(TAG, "Enabling slot $slot (${configuredSlots[slot]?.label})")
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else if (isCurrentlyEnabled) {
                    AppLog.d(TAG, "Disabling slot $slot")
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }

        /** Request complication data update for all enabled slots. */
        fun requestUpdateAll(context: Context) {
            val pm = context.packageManager
            for (slot in 1..MAX_SLOTS) {
                val component = slotComponentName(context, slot)
                val state = pm.getComponentEnabledSetting(component)
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    ComplicationDataSourceUpdateRequester.create(context, component).requestUpdateAll()
                }
            }
        }
    }
}
