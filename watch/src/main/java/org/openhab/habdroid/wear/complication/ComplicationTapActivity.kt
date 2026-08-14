package org.openhab.habdroid.wear.complication

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.CircularProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Activity launched when the user taps a complication on the watch face.
 * Routes to the appropriate control based on item type:
 * - Switch/toggleable → toggle immediately, show checkmark, close
 * - Dimmer/Number with range → RotaryControlActivity
 * - Color → ColorPickerActivity
 * - Rollershutter → RollerShutterActivity
 * - Has commandOptions → ChoicePickerActivity
 * - Others → show value (ComplicationDetailActivity)
 */
@AndroidEntryPoint
class ComplicationTapActivity : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val complicationId = intent.getIntExtra(EXTRA_COMPLICATION_ID, -1)
        val slotNumber = intent.getIntExtra(EXTRA_SLOT_NUMBER, -1)

        setContent {
            var state by remember { mutableStateOf<TapState>(TapState.Loading) }

            LaunchedEffect(complicationId) {
                // Resolve item name: try preference store first (generic service),
                // then server config by slot number (slot service)
                val itemName = complicationPreferenceStore.getItemForSlot(complicationId)
                    ?: resolveItemFromSlot(slotNumber)
                if (itemName == null) {
                    state = TapState.Done
                    finish()
                    return@LaunchedEffect
                }

                val itemResult = repository.getItem(itemName)
                val item = itemResult.getOrNull()
                if (item == null) {
                    state = TapState.Done
                    finish()
                    return@LaunchedEffect
                }

                // Get complication config for the label
                val configs = repository.getComplicationConfigs().getOrDefault(emptyList())
                val config = configs.find { it.item == itemName }
                // item.label is the human-readable label from openHAB (e.g. "Bedroom Light")
                // config.label is a user override from the complication editor
                // item.name is the technical identifier (e.g. "BDR_Light")
                val label = item.label?.takeIf { it.isNotBlank() }
                    ?: config?.label?.takeIf { it.isNotBlank() && it != item.type }
                    ?: item.name

                // Route based on item type
                when {
                    item.type == "Color" -> {
                        launchControl(org.openhab.habdroid.wear.ui.control.ColorPickerActivity::class.java, itemName, label)
                    }
                    item.type == "Rollershutter" -> {
                        launchControl(org.openhab.habdroid.wear.ui.control.RollerShutterActivity::class.java, itemName, label)
                    }
                    item.commandDescription?.commandOptions?.isNotEmpty() == true -> {
                        launchControl(org.openhab.habdroid.wear.ui.control.ChoicePickerActivity::class.java, itemName, label)
                    }
                    item.isRange || item.type == "Dimmer" -> {
                        launchControl(org.openhab.habdroid.wear.ui.control.RotaryControlActivity::class.java, itemName, label)
                    }
                    isToggleable(item) -> {
                        launchControl(org.openhab.habdroid.wear.ui.control.ToggleControlActivity::class.java, itemName, label)
                    }
                    else -> {
                        launchDetail(complicationId)
                    }
                }
            }

            when (state) {
                is TapState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is TapState.Done -> {}
            }
        }
    }

    private fun launchControl(activityClass: Class<*>, itemName: String, label: String) {
        startActivity(Intent(this, activityClass).apply {
            putExtra("item_name", itemName)
            putExtra("label", label)
        })
        finish()
    }

    private fun launchDetail(complicationId: Int) {
        startActivity(Intent(this, ComplicationDetailActivity::class.java).apply {
            putExtra(ComplicationDetailActivity.EXTRA_COMPLICATION_ID, complicationId)
        })
        finish()
    }

    private fun isToggleable(item: Item): Boolean {
        return item.type in listOf("Switch", "Group") ||
            (item.type == "Dimmer" && !item.isRange) // Dimmer without range = simple on/off
    }

    /**
     * Resolve the item name for a server-configured complication slot.
     * Fetches the wear:complication-list document and finds the entry for this slot number.
     */
    private suspend fun resolveItemFromSlot(slotNumber: Int): String? {
        if (slotNumber < 1) return null
        val configs = repository.getComplicationConfigs().getOrNull() ?: return null
        return configs.find { it.slotNumber == slotNumber }?.item?.takeIf { it.isNotBlank() }
    }

    private sealed interface TapState {
        data object Loading : TapState
        data object Done : TapState
    }

    companion object {
        const val EXTRA_COMPLICATION_ID = "complication_id"
        const val EXTRA_SLOT_NUMBER = "slot_number"
        private const val TAG = "ComplicationTap"
    }
}
