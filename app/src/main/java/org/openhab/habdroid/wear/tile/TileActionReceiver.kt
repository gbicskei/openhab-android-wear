package org.openhab.habdroid.wear.tile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.wear.tiles.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Transparent activity that handles tile button clicks.
 * Fetches the item's current state, sends the opposite command (toggle),
 * requests a tile refresh, then finishes immediately.
 */
@AndroidEntryPoint
class TileActionReceiver : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemName = intent.getStringExtra("item_name")

        if (itemName != null) {
            Log.d(TAG, "Tile action: toggling '$itemName'")
            CoroutineScope(Dispatchers.IO).launch {
                // Fetch current state and send the opposite
                repository.getItem(itemName)
                    .onSuccess { item ->
                        val command = if (item.isOn) "OFF" else "ON"
                        Log.d(TAG, "Current state: ${item.state}, sending: $command")
                        repository.sendCommand(itemName, command)
                    }
                    .onFailure { error ->
                        // Fallback: use the command from the intent if state fetch fails
                        val fallbackCommand = intent.getStringExtra("command") ?: "ON"
                        Log.w(TAG, "Failed to fetch state, using fallback: $fallbackCommand", error)
                        repository.sendCommand(itemName, fallbackCommand)
                    }

                // Request tile refresh
                TileService.getUpdater(this@TileActionReceiver)
                    .requestUpdate(OpenHabTileService::class.java)
            }
        } else {
            Log.w(TAG, "Tile action received with missing item_name extra")
        }

        finish()
    }

    companion object {
        private const val TAG = "TileActionReceiver"
    }
}
