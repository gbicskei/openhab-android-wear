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
import org.openhab.habdroid.wear.R
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
open class OpenHabComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    @Inject
    lateinit var iconResolver: org.openhab.habdroid.wear.data.icon.IconResolver

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
        val itemResult = repository.getItem(itemName)
        val item = itemResult.getOrNull()
        if (item == null) {
            val error = itemResult.exceptionOrNull()
            AppLog.w(TAG, "Failed to fetch item '$itemName': ${error?.message}", error)
            return buildErrorData(request.complicationType, itemName, error?.message ?: "Connection failed")
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

    private suspend fun buildMonochromaticImage(item: Item, config: WearComplicationConfig?, complicationId: Int): ComplicationData? {
        val typeConfig = config?.monochromaticImage
        val iconRef = typeConfig?.iconForState(item.isActive)?.takeIf { it.isNotBlank() }
            ?: config?.icon?.takeIf { it.isNotBlank() }
            ?: item.iconName

        // Resolve icon from server/Iconify/Material and render to monochromatic bitmap
        val icon = resolveIconToBitmap(iconRef, item.state) ?: createPlaceholderIcon()

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
     * Format item state value for complication display.
     * Delegates to [ComplicationValueFormatter] (shared, unit-tested).
     */
    private fun formatValue(item: Item, pattern: String?): String =
        ComplicationValueFormatter.format(item, pattern)

    /**
     * Build error complication data showing what went wrong instead of silently returning null.
     * This prevents the system from showing "SETUP" when the item is configured but unreachable.
     */
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
                )
                    .setTitle(PlainComplicationText.Builder("⚠").build())
                    .build()

            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$itemName: $shortError").build(),
                    contentDescription = PlainComplicationText.Builder(errorMessage).build()
                )
                    .setTitle(PlainComplicationText.Builder("openHAB Error").build())
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = 0f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("$itemName: $errorMessage").build()
                )
                    .setText(PlainComplicationText.Builder(shortError).build())
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(createPlaceholderIcon()).build(),
                    contentDescription = PlainComplicationText.Builder("$itemName: $errorMessage").build()
                ).build()

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
                    .setTitle(PlainComplicationText.Builder(getString(R.string.setup_title)).build())
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
        val intent = Intent(this, ComplicationTapActivity::class.java).apply {
            putExtra(ComplicationTapActivity.EXTRA_COMPLICATION_ID, complicationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            complicationId + 2000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Resolves an icon reference to a monochromatic bitmap suitable for complications.
     * SVG icons are rendered via Android's SVG support; PNG icons are decoded directly.
     * Returns null if resolution fails (falls back to placeholder).
     */
    private suspend fun resolveIconToBitmap(iconRef: String, state: String): android.graphics.drawable.Icon? {
        val rawBytes = iconResolver.resolve(iconRef, state) ?: return null
        val format = iconResolver.detectFormat(rawBytes)
        val size = 48

        return try {
            val bitmap = when (format) {
                org.openhab.habdroid.wear.data.icon.IconFormat.SVG -> {
                    // Render SVG to monochromatic bitmap (white on transparent)
                    val svgBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(svgBitmap)
                    val svg = com.caverock.androidsvg.SVG.getFromString(String(rawBytes, Charsets.UTF_8))
                    svg.documentWidth = size.toFloat()
                    svg.documentHeight = size.toFloat()
                    svg.renderToCanvas(canvas)
                    // Convert to white-on-transparent for monochromatic display
                    toMonochrome(svgBitmap)
                }
                org.openhab.habdroid.wear.data.icon.IconFormat.PNG -> {
                    val decoded = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        ?: return null
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

    /**
     * Converts a color bitmap to monochromatic (white pixels where alpha > 0).
     * Complications expect white-on-transparent for monochromatic images.
     */
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
