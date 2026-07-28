package org.openhab.habdroid.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_ID
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Complication data source that exposes openHAB item states on the watch face.
 *
 * Each complication slot maps to a single openHAB item (stored in ComplicationPreferenceStore).
 * The system calls onComplicationRequest() when data is needed — we fetch the item state
 * from the server and return the appropriate ComplicationData.
 *
 * Supports SHORT_TEXT, LONG_TEXT, and RANGED_VALUE complication types.
 */
@AndroidEntryPoint
class OpenHabComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Log.d(TAG, "onComplicationRequest() id: ${request.complicationInstanceId}, type: ${request.complicationType}")

        // 1. Read which item is configured for this complication slot
        val itemName = complicationPreferenceStore.getItemForSlot(request.complicationInstanceId)

        // 2. If no item configured, show "Tap to configure" prompt
        if (itemName == null) {
            Log.d(TAG, "No item configured for complication ${request.complicationInstanceId}, showing config prompt")
            return buildConfigPrompt(request.complicationType, request.complicationInstanceId)
        }

        // 3. Fetch item state from server
        val item = repository.getItem(itemName).getOrNull()
        if (item == null) {
            Log.w(TAG, "Failed to fetch item $itemName")
            return null
        }

        // 3. Build ComplicationData based on requested type
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> buildShortText(item, request.complicationInstanceId)
            ComplicationType.LONG_TEXT -> buildLongText(item, request.complicationInstanceId)
            ComplicationType.RANGED_VALUE -> buildRangedValue(item, request.complicationInstanceId)
            else -> {
                Log.w(TAG, "Unsupported complication type: ${request.complicationType}")
                null
            }
        }
    }

    /**
     * Static preview shown in the complication picker UI.
     * MUST be synchronous — no network calls allowed.
     */
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

            else -> null
        }
    }

    /**
     * Called when a complication using this data source is deactivated.
     * Clean up the stored item preference for this slot.
     */
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated() id: $complicationInstanceId")
        kotlinx.coroutines.runBlocking {
            complicationPreferenceStore.removeSlot(complicationInstanceId)
        }
    }

    // --- ComplicationData builders ---

    /**
     * Build a "Tap to configure" complication that launches the config activity.
     */
    private fun buildConfigPrompt(type: ComplicationType, complicationId: Int): ComplicationData? {
        val configIntent = Intent(this, ComplicationConfigActivity::class.java).apply {
            putExtra(EXTRA_CONFIG_COMPLICATION_ID, complicationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapAction = PendingIntent.getActivity(
            this,
            complicationId + 1000, // unique request code per slot
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

            else -> null
        }
    }

    private fun buildShortText(item: Item, complicationId: Int): ComplicationData {
        val stateText = formatItemState(item)
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(stateText).build(),
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $stateText").build()
        )
            .setTitle(PlainComplicationText.Builder(item.displayLabel.take(10)).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildLongText(item: Item, complicationId: Int): ComplicationData {
        val stateText = formatItemState(item)
        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder("${item.displayLabel}: $stateText").build(),
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $stateText").build()
        )
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    private fun buildRangedValue(item: Item, complicationId: Int): ComplicationData? {
        val value = item.numericState?.toFloat() ?: return buildShortText(item, complicationId)
        val min = item.stateDescription?.minimum?.toFloat() ?: 0f
        val max = item.stateDescription?.maximum?.toFloat()
            ?: if (item.type.startsWith("Dimmer")) 100f
            else (value * 2f).coerceAtLeast(100f)

        return RangedValueComplicationData.Builder(
            value = value.coerceIn(min, max),
            min = min,
            max = max,
            contentDescription = PlainComplicationText.Builder("${item.displayLabel}: $value").build()
        )
            .setText(PlainComplicationText.Builder(formatItemState(item)).build())
            .setTapAction(createDetailTapAction(complicationId))
            .build()
    }

    // --- Helpers ---

    /**
     * Format the item state for display on the complication.
     * Prefers transformedState (server-formatted with pattern), falls back to manual formatting.
     */
    private fun formatItemState(item: Item): String {
        // Server-formatted state (e.g., "28.5 °C") — best source
        val transformed = item.transformedState
        if (transformed != null && transformed !in listOf("NULL", "UNDEF")) {
            return transformed.take(12)
        }

        val numericValue = item.numericState
        return when {
            item.state in listOf("NULL", "UNDEF") -> "\u2014" // em dash

            // Numeric value — format cleanly with unit if known
            numericValue != null -> {
                val formatted = if (numericValue == numericValue.toLong().toDouble())
                    numericValue.toLong().toString()
                else
                    String.format("%.1f", numericValue)
                val unit = if (item.type.contains(":")) getUnitSymbol(item.type) else null
                if (unit != null) "$formatted $unit" else formatted
            }

            // Plain state (ON/OFF/OPEN/CLOSED/text)
            else -> item.state.take(12)
        }
    }

    /**
     * Get the unit symbol for a Number:Dimension item type.
     */
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

    /**
     * Creates a PendingIntent that opens the detail activity showing the fresh item value.
     */
    private fun createDetailTapAction(complicationId: Int): PendingIntent {
        val intent = Intent(this, ComplicationDetailActivity::class.java).apply {
            putExtra(ComplicationDetailActivity.EXTRA_COMPLICATION_ID, complicationId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            complicationId + 2000, // unique request code per slot
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val TAG = "ComplicationService"
    }
}
