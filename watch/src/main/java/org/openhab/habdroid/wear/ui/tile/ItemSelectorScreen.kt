package org.openhab.habdroid.wear.ui.tile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard

/**
 * Item selector screen shown when the user taps "+" in the tile editor.
 * Displays all wearTile-tagged items that are not yet selected on the tile.
 * Tapping an item adds it and returns to the editor.
 */
@Composable
fun ItemSelectorScreen(
    viewModel: TileConfigViewModel,
    onItemSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val availableItems = (uiState as? TileConfigUiState.Success)?.availableItems ?: emptyList()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader {
                Text("Add Item")
            }
        }

        if (availableItems.isEmpty()) {
            item {
                Text("No more items available.\nAll wearTile items are on the tile.")
            }
        } else {
            items(availableItems) { tileItem ->
                TitleCard(
                    onClick = { onItemSelected(tileItem.item.name) },
                    title = { Text(tileItem.item.displayLabel) },
                ) {
                    Text(tileItem.item.state)
                }
            }
        }
    }
}
