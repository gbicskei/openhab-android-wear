package org.openhab.habdroid.wear.ui.tile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

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
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 48.dp
            )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(tileItem.item.state)
                    }
                }
            }
        }
    }
}
