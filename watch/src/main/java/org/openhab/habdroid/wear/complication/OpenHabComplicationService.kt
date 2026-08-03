package org.openhab.habdroid.wear.complication

import android.app.PendingIntent
import android.content.Intent
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
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_ID
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.WearComplicationConfig
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Complication data source that exposes openHAB item states on the watch face.
 *
 * Reads complication configuration from the wear:complication-list document.
 * Each complication slot maps to a single openHAB item with per-type display config.
 *
 * Supports: SHORT_TEXT, LONG_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE.
 */
@AndroidEntryPoint
class OpenHabComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        AppLog.d(TAG, "onComplicationRequest() id: ${request.complicationInstanceId}, type: ${request.complicationType}")

        // 1. Read which item is configured for this complication slot
        val itemName = complicationPreferenceStore.getItemForSlot(request.complicationInstanceId)

        // 2. If no item configured, show "Tap to configure" prompt
        if (itemName == null) {
            AppLog.d(TAG, "No item configured for complication ${request.complicationInstanceId}")
            return buildConfigPrompt(request.complicationType, request.complicationInstanceId)
        }

        // 3. Fetch item state from server
        val item = repository.getItem(itemName).getOrNull()
        if (item == null) {
            AppLog.w(TAG, "Failed to fetch item $itemName")
            return null
        }

        // 4. Get per-type config from wear:complication-list document
        val config = getConfigForItem(itemName)

        // 5. Build ComplicationData based on requested type + config
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> buildShortText(item, config, request.complicationInstanceId)
            ComplicationType.LONG_TEXT -> buildLongText(item, config, request.complicationInstanceId)
            ComplicationType.RANGED_VALUE -> buildRangedValue(item, config, request.complicationInstanceId)
            ComplicationType.MONOCHROMATIC_IMAGE -> buildMonochromaticImage(item, config, request.complicationInstanceId)
            else -> {
                AppLog.w(TAG, "Unsupported complication type: ${request.complicationType}")
                null
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("22°C").build(),
                    contentDescription = PlainComplicationText.Builder("openHAB item value").build()
                )
                    .setTitle(PlainComplicationText.Builder("Temp").build())
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Temperature: 22.5°C").build(),
                    contentDescription = PlainComplicationText.Builder("openHAB item value").build()
                ).build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = 22.5f,
                    min = 15f,
                    max = 30f,
                    contentDescription = PlainComplicationText.Builder("openHAB range value").build()
                )
                    .setText(PlainComplicationText.Builder("22°C").build())
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(
                        createPlaceholderIcon()
                    ).build(),
                    contentDescription = PlainComplicationText.Builder("openHAB status").build()
                ).build()

            else -> null
        }
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        AppLog.d(TAG, "onComplicationDeactivated() id: $complicationInstanceId")
        kotlinx.coroutines.runBlocking {
            complicationPreferenceStore.removeSlot(complicationInstanceId)
        }
    }

    // ─── Config Resolution ───

    private suspend fun getConfigForItem(itemName: String): WearComplicationConfig? {
        return repository.getComplicationConfigs().getOrNull()
            ?.find { it.item == itemName }
    }

    // ─── ComplicationData Builders ───

    private fun buildShortText(item: Item, config: WearComplicationConfig?, complicationId: Int): ComplicationData {
        val typeConfig = config?.shortText
        val title = typeConfig?.title?.takeIf { it.isNotBlank() }
            ?: config?.label?.take(7)?.takeIf { it.isNotBlank() }
            ?: item.displayLabel.take(7)
        val text = formatValue(item, typeConfig?.text)

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $text").build()
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildLongText(item: Item, config: WearComplicationConfig?, complicationId: Int): ComplicationData {
        val typeConfig = config?.longText
        val title = typeConfig?.title?.takeIf { it.isNotBlank() }
            ?: config?.label?.takeIf { it.isNotBlank() }
            ?: item.displayLabel
        val text = formatValue(item, typeConfig?.text)

        val builder = LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("$title: $text").build()
        )
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))

        return builder.build()
    }

    private fun buildRangedValue(item: Item, config: WearComplicationConfig?, complicationId: Int): ComplicationData? {
        val typeConfig = config?.rangedValue
        val value = item.numericState?.toFloat() ?: return buildShortText(item, config, complicationId)
        val min = typeConfig?.min?.toFloat()
            ?: item.stateDescription?.minimum?.toFloat()
            ?: 0f
        val max = typeConfig?.max?.toFloat()
            ?: item.stateDescription?.maximum?.toFloat()
            ?: if (item.type.startsWith("Dimmer")) 100f
            else (value * 2f).coerceAtLeast(100f)

        val title = typeConfig?.title?.takeIf { it.isNotBlank() }
            ?: config?.label?.take(7)?.takeIf { it.isNotBlank() }
            ?: item.displayLabel.take(7)
        val text = formatValue(item, typeConfig?.text)

        return RangedValueComplicationData.Builder(
            value = value.coerceIn(min, max),
            min = min,
            max = max,
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $text").build()
        )
            .setText(PlainComplicationText.Builder(text).build())
            .setTitle(PlainComplicationText.Builder(title).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildMonochromaticImage(item: Item, config: WearComplicationConfig?, complicationId: Int): ComplicationData? {
        val typeConfig = config?.monochromaticImage
        val iconRef = typeConfig?.iconForState(item.isActive)?.takeIf { it.isNotBlank() }
            ?: config?.icon?.takeIf { it.isNotBlank() }
            ?: item.iconName

        // For now, use a simple placeholder bitmap — full icon loading would require
        // async bitmap resolution which ComplicationData doesn't support directly.
        // TODO: Pre-cache icons and use Icon.createWithBitmap()
        val icon = createPlaceholderIcon()

        return MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(icon).build(),
            contentDescription = PlainComplicationText.Builder(
                "${config?.label ?: item.displayLabel}: ${if (item.isActive) "Active" else "Inactive"}"
            ).build()
        )
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    // ─── Helpers ───

    /**
     * Format item state value using a pattern or smart defaults.
     * Pattern uses Java String.format syntax (e.g. "%.0f°C", "%.1f kWh").
     * If pattern is blank, uses transformedState or auto-formatting.
     */
    private fun formatValue(item: Item, pattern: String?): String {
        // If a pattern is provided, try to format the numeric value with it
        if (!pattern.isNullOrBlank()) {
            val numericValue = item.numericState
            if (numericValue != null) {
                return try {
                    String.format(pattern, numericValue)
                } catch (_: Exception) {
                    pattern // If format fails, return pattern as literal
                }
            }
            // For non-numeric items, try formatting with the state string
            return try {
                String.format(pattern, item.state)
            } catch (_: Exception) {
                item.state.take(7)
            }
        }

        // No pattern — use smart defaults
        val transformed = item.transformedState
        if (transformed != null && transformed !in listOf("NULL", "UNDEF")) {
            return transformed.take(12)
        }

        val numericValue = item.numericState
        return when {
            item.state in listOf("NULL", "UNDEF") -> "\u2014"
            numericValue != null -> {
                val formatted = if (numericValue == numericValue.toLong().toDouble())
                    numericValue.toLong().toString()
                else
                    String.format("%.1f", numericValue)
                val unit = if (item.type.contains(":")) getUnitSymbol(item.type) else null
                if (unit != null) "$formatted $unit" else formatted
            }
            else -> item.state.take(12)
        }
    }

    private fun getUnitSymbol(type: String): String? {
        return when {
            type.contains("Temperature") -> "°C"
            type.contains("Pressure") -> "hPa"
            type.contains("Speed") -> "km/h"
            type.contains("Length") -> "m"
            type.contains("Power") -> "W"
            type.contains("Energy") -> "kWh"
            type.contains("Dimensionless") -> "%"
            else -> null
        }
    }

    private fun buildConfigPrompt(type: ComplicationType, complicationId: Int): ComplicationData? {
        val configIntent = Intent(this, ComplicationConfigActivity::class.java).apply {
            putExtra(EXTRA_CONFIG_COMPLICATION_ID, complicationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapAction = PendingIntent.getActivity(
            this,
            complicationId + 1000,
            configIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Setup").build(),
                    contentDescription = PlainComplicationText.Builder("Tap to configure openHAB complication").build()
                )
                    .setTitle(PlainComplicationText.Builder("openHAB").build())
                    .setTapAction(tapAction)
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Tap to select item").build(),
                    contentDescription = PlainComplicationText.Builder("Tap to configure openHAB complication").build()
                )
                    .setTapAction(tapAction)
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = 0f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Tap to configure openHAB complication").build()
                )
                    .setText(PlainComplicationText.Builder("Setup").build())
                    .setTapAction(tapAction)
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(
                        createPlaceholderIcon()
                    ).build(),
                    contentDescription = PlainComplicationText.Builder("Tap to configure openHAB complication").build()
                )
                    .setTapAction(tapAction)
                    .build()

            else -> null
        }
    }

    private fun createDetailTapAction(complicationId: Int): PendingIntent {
        val intent = Intent(this, ComplicationDetailActivity::class.java).apply {
            putExtra(ComplicationDetailActivity.EXTRA_COMPLICATION_ID, complicationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            complicationId + 2000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createPlaceholderIcon(): android.graphics.drawable.Icon {
        // Simple circle placeholder icon
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
        return android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }

    companion object {
        private const val TAG = "ComplicationService"
    }
}
