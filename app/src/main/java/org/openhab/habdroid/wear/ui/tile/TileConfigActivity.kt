package org.openhab.habdroid.wear.ui.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Text
import androidx.wear.tiles.TileService
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.data.model.TileItem
import org.openhab.habdroid.wear.tile.OpenHabTileService

/**
 * Tile configuration activity launched by the system pencil icon
 * when the user long-presses the tile in the carousel.
 *
 * Mirrors the tile layout but with "−" badges on items for removal,
 * a "+" slot to add items, and a "✓" button at the bottom to confirm.
 */
@AndroidEntryPoint
class TileConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TileConfigScreen(
                onDone = {
                    TileService.getUpdater(this)
                        .requestUpdate(OpenHabTileService::class.java)
                    finish()
                }
            )
        }
    }
}

@Composable
fun TileConfigScreen(
    viewModel: TileConfigViewModel = hiltViewModel(),
    onDone: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSelector by remember { mutableStateOf(false) }

    if (showSelector) {
        ItemSelectorScreen(
            viewModel = viewModel,
            onItemSelected = { itemName ->
                viewModel.addItem(itemName)
                showSelector = false
            },
            onBack = { showSelector = false }
        )
    } else {
        TileEditorContent(
            uiState = uiState,
            onRemoveItem = { viewModel.removeItem(it) },
            onAddItem = { showSelector = true },
            onDone = onDone
        )
    }
}

@Composable
private fun TileEditorContent(
    uiState: TileConfigUiState,
    onRemoveItem: (String) -> Unit,
    onAddItem: () -> Unit,
    onDone: () -> Unit
) {
    when (uiState) {
        is TileConfigUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...")
            }
        }

        is TileConfigUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.message}", textAlign = TextAlign.Center)
            }
        }

        is TileConfigUiState.Success -> {
            // Mirror the tile layout: title top, grid center, button bottom
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title — same position as "openHAB" on the tile
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "openHAB",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Push grid to center
                Spacer(modifier = Modifier.weight(1f))

                // Item grid with remove badges + add slot
                TileItemGrid(
                    selectedItems = uiState.selectedItems,
                    isFull = uiState.isFull,
                    onRemoveItem = onRemoveItem,
                    onAddItem = onAddItem
                )

                // Push done button to bottom
                Spacer(modifier = Modifier.weight(1f))

                // Done button — same position as mic on the tile
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF4CAF50))
                        .clickable { onDone() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TileItemGrid(
    selectedItems: List<TileItem>,
    isFull: Boolean,
    onRemoveItem: (String) -> Unit,
    onAddItem: () -> Unit
) {
    val columns = if (selectedItems.size + (if (isFull) 0 else 1) <= 4) 2 else 3
    val slots = selectedItems.size + if (isFull) 0 else 1
    val rows = (slots + columns - 1) / columns

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var index = 0
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(columns) {
                    if (index < selectedItems.size) {
                        val item = selectedItems[index]
                        ItemSlot(
                            tileItem = item,
                            onRemove = { onRemoveItem(item.item.name) }
                        )
                    } else if (index == selectedItems.size && !isFull) {
                        AddSlot(onClick = onAddItem)
                    } else {
                        // Empty spacer for alignment
                        Spacer(modifier = Modifier.size(60.dp))
                    }
                    index++
                }
            }
        }
    }
}

@Composable
private fun ItemSlot(
    tileItem: TileItem,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Item circle — same as tile appearance
        Column(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2C)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tileItem.item.displayLabel.take(8),
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tileItem.item.state.take(4),
                fontSize = 9.sp,
                color = if (tileItem.item.isOn) Color(0xFFFF9800) else Color(0xFF757575)
            )
        }

        // Remove badge (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "−", fontSize = 14.sp, color = Color.White)
        }
    }
}

@Composable
private fun AddSlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+", fontSize = 22.sp, color = Color(0xFF4CAF50))
    }
}
