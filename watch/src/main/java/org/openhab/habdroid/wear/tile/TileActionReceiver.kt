package org.openhab.habdroid.wear.tile

import android.os.Bundle
import org.openhab.habdroid.wear.util.AppLog
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
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Transparent activity that handles tile button clicks.
 * If needs_confirmation is true, shows a confirmation dialog first.
 * Otherwise sends the command immediately and finishes.
 */
@AndroidEntryPoint
class TileActionReceiver : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var itemCache: org.openhab.habdroid.wear.data.repository.ItemCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemName = intent.getStringExtra("item_name")
        val needsConfirmation = intent.getBooleanExtra("needs_confirmation", false)
        val label = intent.getStringExtra("label") ?: itemName ?: ""

        if (itemName == null) {
            AppLog.w(TAG, "Tile action received with missing item_name extra")
            finish()
            return
        }

        if (needsConfirmation) {
            // Show confirmation UI
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { finish() },
                            label = { Text("No") },
                            icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        )
                        Button(
                            onClick = {
                                executeCommand(itemName)
                                finish()
                            },
                            label = { Text("Yes") },
                            icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        )
                    }
                }
            }
        } else {
            // No confirmation needed — execute immediately
            executeCommand(itemName)
            finish()
        }
    }

    private fun executeCommand(itemName: String) {
        AppLog.d(TAG, "Tile action: sending command to '$itemName'")
        CoroutineScope(Dispatchers.IO).launch {
            val command = intent.getStringExtra("command")
            if (command != null) {
                // Fixed command from tile builder
                AppLog.d(TAG, "Sending fixed command: $command")
                repository.sendCommand(itemName, command)
                    .onSuccess { itemCache.updateItemState(itemName, command) }
            } else {
                // Toggle: check local cache first (fast), fall back to API
                val cachedState = repository.getCachedItemState(itemName)
                if (cachedState != null) {
                    val toggleCommand = if (cachedState == "ON" || cachedState.toDoubleOrNull()?.let { it > 0 } == true) "OFF" else "ON"
                    AppLog.d(TAG, "Cached state: $cachedState, sending: $toggleCommand")
                    repository.sendCommand(itemName, toggleCommand)
                        .onSuccess { itemCache.updateItemState(itemName, toggleCommand) }
                } else {
                    // No cache — fetch from server
                    repository.getItem(itemName)
                        .onSuccess { item ->
                            val toggleCommand = if (item.isOn) "OFF" else "ON"
                            AppLog.d(TAG, "Fetched state: ${item.state}, sending: $toggleCommand")
                            repository.sendCommand(itemName, toggleCommand)
                                .onSuccess { itemCache.updateItemState(itemName, toggleCommand) }
                        }
                        .onFailure { error ->
                            val fallbackCommand = "ON"
                            AppLog.w(TAG, "Failed to fetch state, using fallback: $fallbackCommand", error)
                            repository.sendCommand(itemName, fallbackCommand)
                        }
                }
            }

            // Request tile refresh
            TileService.getUpdater(this@TileActionReceiver)
                .requestUpdate(OpenHabTileService::class.java)
        }
    }

    companion object {
        private const val TAG = "TileActionReceiver"
    }
}
