package org.openhab.habdroid.wear.tile

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tiles.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.ItemCache
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Transparent activity for detecting single tap vs double tap on tile buttons.
 *
 * Only used for buttons with a configured doubleTapItem.
 * - Single tap (no second tap within DOUBLE_TAP_WINDOW_MS): executes primary action
 * - Double tap (Activity launched again within window): executes secondary action on doubleTapItem
 *
 * Both primary and secondary actions use auto-detection based on item type:
 * - Range item (Number with min/max, Dimmer) → RotaryControlActivity
 * - Color item → ColorPickerActivity
 * - Rollershutter → RollerShutterActivity
 * - Has commandOptions → ChoicePickerActivity
 * - Switch/toggleable → Toggle ON/OFF
 * - action="toggle" override → force toggle regardless of type
 * - action="command" override → send fixed command
 */
@AndroidEntryPoint
class QuickActionActivity : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var itemCache: ItemCache

    private var waitJob: Job? = null
    private var doubleTapDetected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME)
        Log.d(TAG, "onCreate: itemName=$itemName")

        if (itemName == null) {
            Log.w(TAG, "Missing item_name extra")
            finish()
            return
        }

        Log.d(TAG, "First tap for '$itemName' — waiting ${DOUBLE_TAP_WINDOW_MS}ms")

        // Start countdown: if no second tap arrives, execute primary action
        waitJob = CoroutineScope(Dispatchers.Main).launch {
            delay(DOUBLE_TAP_WINDOW_MS)
            if (!doubleTapDetected) {
                Log.d(TAG, "Single tap confirmed for '$itemName'")
                executePrimaryAction(itemName)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)

        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: return
        doubleTapDetected = true
        waitJob?.cancel()

        Log.d(TAG, "Double tap detected for '$itemName'")

        // Haptic feedback
        @Suppress("DEPRECATION")
        window?.decorView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        executeDoubleTapAction(itemName, intent)
    }

    /**
     * Execute the primary action (single tap) based on the item's configured action.
     */
    private fun executePrimaryAction(itemName: String) {
        val primaryAction = intent.getStringExtra(EXTRA_PRIMARY_ACTION) // "toggle", "command", or null (auto)
        val fixedCommand = intent.getStringExtra(EXTRA_SHORT_COMMAND)
        val needsConfirmation = intent.getBooleanExtra("needs_confirmation", false)
        val label = intent.getStringExtra("label") ?: itemName

        Log.d(TAG, "Primary: action=$primaryAction, command=$fixedCommand, confirm=$needsConfirmation")

        when {
            needsConfirmation -> showConfirmation(label) {
                executeAction(itemName, primaryAction, fixedCommand)
                requestTileUpdate()
                finish()
            }
            primaryAction == "toggle" || (primaryAction == null && isToggleableItem(itemName)) -> {
                executeToggle(itemName)
                requestTileUpdate()
                finish()
            }
            primaryAction == "command" && fixedCommand != null -> {
                sendCommand(itemName, fixedCommand)
                requestTileUpdate()
                finish()
            }
            primaryAction == null -> {
                // Auto-detect: open appropriate activity
                openControlActivity(itemName)
            }
            else -> {
                executeToggle(itemName)
                requestTileUpdate()
                finish()
            }
        }
    }

    /**
     * Execute the double-tap action on the doubleTapItem.
     */
    private fun executeDoubleTapAction(itemName: String, secondIntent: android.content.Intent) {
        val doubleTapItemName = secondIntent.getStringExtra(EXTRA_DOUBLE_PRESS_ITEM)
            ?: intent.getStringExtra(EXTRA_DOUBLE_PRESS_ITEM) ?: itemName
        val doubleTapAction = secondIntent.getStringExtra(EXTRA_DOUBLE_PRESS_ACTION)
            ?: intent.getStringExtra(EXTRA_DOUBLE_PRESS_ACTION) // "toggle", "command", "auto", or null
        val doubleTapCommand = secondIntent.getStringExtra(EXTRA_DOUBLE_PRESS_COMMAND)
            ?: intent.getStringExtra(EXTRA_DOUBLE_PRESS_COMMAND)
        val doubleTapConfirmation = secondIntent.getBooleanExtra(EXTRA_DOUBLE_PRESS_CONFIRMATION, false)
            || intent.getBooleanExtra(EXTRA_DOUBLE_PRESS_CONFIRMATION, false)

        Log.d(TAG, "DoubleTap: item=$doubleTapItemName, action=$doubleTapAction, command=$doubleTapCommand, confirm=$doubleTapConfirmation")

        val effectiveAction = if (doubleTapAction == "auto") null else doubleTapAction

        when {
            doubleTapConfirmation -> showConfirmation(doubleTapItemName) {
                if (effectiveAction == "toggle" || effectiveAction == "command") {
                    executeAction(doubleTapItemName, effectiveAction, doubleTapCommand)
                    requestTileUpdate()
                    finish()
                } else {
                    // Auto-detect: may open a control activity
                    openControlActivity(doubleTapItemName)
                }
            }
            effectiveAction == "toggle" -> {
                executeToggle(doubleTapItemName)
                requestTileUpdate()
                finish()
            }
            effectiveAction == "command" && doubleTapCommand != null -> {
                sendCommand(doubleTapItemName, doubleTapCommand)
                requestTileUpdate()
                finish()
            }
            effectiveAction == null -> {
                // Auto-detect from item type — openControlActivity handles fetch if needed
                openControlActivity(doubleTapItemName)
            }
            else -> {
                executeToggle(doubleTapItemName)
                requestTileUpdate()
                finish()
            }
        }
    }

    private fun executeAction(itemName: String, action: String?, command: String?) {
        when {
            action == "toggle" -> executeToggle(itemName)
            action == "command" && command != null -> sendCommand(itemName, command)
            action == null && isToggleableItem(itemName) && !isRangeItem(itemName) -> executeToggle(itemName)
            action == null -> openControlActivity(itemName)
            else -> executeToggle(itemName)
        }
    }

    private fun executeToggle(itemName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val cachedItem = itemCache.get()?.find { it.item.name == itemName }
            val item = cachedItem?.item
            val state = item?.state ?: repository.getCachedItemState(itemName)

            val command = when {
                // Rollershutter: UP if >= 50, DOWN if < 50
                item?.type == "Rollershutter" -> {
                    val pos = state?.toDoubleOrNull() ?: 0.0
                    if (pos >= 50.0) "UP" else "DOWN"
                }
                // Switch/Dimmer/Color: ON/OFF toggle
                state == "ON" || (state?.toDoubleOrNull()?.let { it > 0 } == true) -> "OFF"
                else -> "ON"
            }
            Log.d(TAG, "Toggle '$itemName': state=$state → $command")
            repository.sendCommand(itemName, command)
        }
    }

    private fun sendCommand(itemName: String, command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Command '$itemName': $command")
            repository.sendCommand(itemName, command)
        }
    }

    private fun openControlActivity(itemName: String) {
        val cachedItem = itemCache.get()?.find { it.item.name == itemName }
        val item = cachedItem?.item ?: itemCache.getExtraItem(itemName)

        if (item == null) {
            // Item not in any cache — fetch from server to determine type
            Log.d(TAG, "Item '$itemName' not in any cache, fetching from server")
            fetchAndOpenControl(itemName)
            return
        }

        val activityClass = when {
            item.type == "Color" -> org.openhab.habdroid.wear.ui.control.ColorPickerActivity::class.java
            item.type == "Rollershutter" -> org.openhab.habdroid.wear.ui.control.RollerShutterActivity::class.java
            item.commandDescription?.commandOptions?.isNotEmpty() == true ->
                org.openhab.habdroid.wear.ui.control.ChoicePickerActivity::class.java
            item.isRange || item.type == "Dimmer" ->
                org.openhab.habdroid.wear.ui.control.RotaryControlActivity::class.java
            else -> {
                // Fallback: toggle
                executeToggle(itemName)
                requestTileUpdate()
                return
            }
        }

        Log.d(TAG, "Opening ${activityClass.simpleName} for '$itemName'")
        startActivity(android.content.Intent(this, activityClass).apply {
            putExtra("item_name", itemName)
        })
        finish()
    }

    private fun isToggleableItem(itemName: String): Boolean {
        val cachedItem = itemCache.get()?.find { it.item.name == itemName }
        val type = cachedItem?.item?.type ?: fetchedItemTypes[itemName] ?: return false
        return type in listOf("Switch", "Dimmer", "Color", "Rollershutter", "Group")
    }

    private fun isRangeItem(itemName: String): Boolean {
        val cachedItem = itemCache.get()?.find { it.item.name == itemName }
        if (cachedItem != null) return cachedItem.item.isRange
        // Check fetched item metadata
        return fetchedItemTypes[itemName]?.let { type ->
            type == "Dimmer" || type.startsWith("Number")
        } ?: false
    }

    /** Cache of item types fetched from server for items not in tile cache */
    private val fetchedItemTypes = mutableMapOf<String, String>()

    /**
     * Fetch item metadata from server and determine the correct action to open.
     * Called when the item isn't in the tile cache (doubleTap items).
     */
    private fun fetchAndOpenControl(itemName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.getItem(itemName)
                .onSuccess { item ->
                    fetchedItemTypes[itemName] = item.type
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        val activityClass = when {
                            item.type == "Color" -> org.openhab.habdroid.wear.ui.control.ColorPickerActivity::class.java
                            item.type == "Rollershutter" -> org.openhab.habdroid.wear.ui.control.RollerShutterActivity::class.java
                            item.commandDescription?.commandOptions?.isNotEmpty() == true ->
                                org.openhab.habdroid.wear.ui.control.ChoicePickerActivity::class.java
                            item.isRange || item.type == "Dimmer" ->
                                org.openhab.habdroid.wear.ui.control.RotaryControlActivity::class.java
                            item.isToggleable -> {
                                executeToggle(itemName)
                                requestTileUpdate()
                                finish()
                                return@withContext
                            }
                            else -> {
                                Log.w(TAG, "Unknown item type '${item.type}' for '$itemName', defaulting to RotaryControl")
                                org.openhab.habdroid.wear.ui.control.RotaryControlActivity::class.java
                            }
                        }
                        Log.d(TAG, "Fetched type=${item.type} for '$itemName', opening ${activityClass.simpleName}")
                        startActivity(android.content.Intent(this@QuickActionActivity, activityClass).apply {
                            putExtra("item_name", itemName)
                        })
                        finish()
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to fetch item '$itemName': ${e.message}, defaulting to RotaryControl")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        startActivity(android.content.Intent(this@QuickActionActivity, org.openhab.habdroid.wear.ui.control.RotaryControlActivity::class.java).apply {
                            putExtra("item_name", itemName)
                        })
                        finish()
                    }
                }
        }
    }

    private fun showConfirmation(label: String, onConfirm: () -> Unit) {
        // Set opaque background for the confirmation dialog (activity theme is translucent)
        window?.decorView?.setBackgroundColor(android.graphics.Color.BLACK)
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Are you sure?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { finish() },
                        label = { Text("No") },
                        icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                    Button(
                        onClick = { onConfirm() },
                        label = { Text("Yes") },
                        icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        }
    }

    private fun requestTileUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            TileService.getUpdater(this@QuickActionActivity)
                .requestUpdate(OpenHabTileService::class.java)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        waitJob?.cancel()
    }

    companion object {
        private const val TAG = "QuickAction"
        const val EXTRA_ITEM_NAME = "item_name"
        const val EXTRA_SHORT_COMMAND = "short_command"
        const val EXTRA_PRIMARY_ACTION = "primary_action"
        const val EXTRA_DOUBLE_PRESS_ACTION = "double_press_action"
        const val EXTRA_DOUBLE_PRESS_ITEM = "double_press_item"
        const val EXTRA_DOUBLE_PRESS_COMMAND = "double_press_command"
        const val EXTRA_DOUBLE_PRESS_CONFIRMATION = "double_press_confirmation"

        /** Time window in ms to detect a double tap */
        const val DOUBLE_TAP_WINDOW_MS = 350L
    }
}
