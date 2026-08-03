package org.openhab.habdroid.wear.complication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import javax.inject.Inject

/**
 * Full-screen activity showing an item's current value in large text.
 * Launched when the user taps a configured complication on the watch face.
 * Fetches a fresh value from the server on every open.
 */
@AndroidEntryPoint
class ComplicationDetailActivity : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationPreferenceStore: ComplicationPreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val complicationId = intent.getIntExtra(EXTRA_COMPLICATION_ID, -1)

        setContent {
            ComplicationDetailScreen(
                complicationId = complicationId,
                repository = repository,
                preferenceStore = complicationPreferenceStore
            )
        }
    }

    companion object {
        const val EXTRA_COMPLICATION_ID = "complication_id"
    }
}

@Composable
private fun ComplicationDetailScreen(
    complicationId: Int,
    repository: OpenHabRepository,
    preferenceStore: ComplicationPreferenceStore
) {
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }

    LaunchedEffect(complicationId) {
        val itemName = preferenceStore.getItemForSlot(complicationId)
        if (itemName == null) {
            state = DetailState.Error("No item configured")
            return@LaunchedEffect
        }

        val result = repository.getItem(itemName)
        state = result.fold(
            onSuccess = { DetailState.Success(it) },
            onFailure = { DetailState.Error(it.message ?: "Failed to load") }
        )
    }

    when (val s = state) {
        is DetailState.Loading -> LoadingView()
        is DetailState.Success -> ItemDetailView(s.item)
        is DetailState.Error -> ErrorView(s.message)
    }
}

private sealed interface DetailState {
    data object Loading : DetailState
    data class Success(val item: Item) : DetailState
    data class Error(val message: String) : DetailState
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ItemDetailView(item: Item) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Value content absolutely centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Item label
            Text(
                text = item.displayLabel,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Value in large text
            Text(
                text = formatValue(item),
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // openHAB icon at top center (drawn last = on top)
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp)
        )
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Format the item value for large display.
 * Prefers transformedState (server-formatted with pattern), falls back to manual formatting.
 */
private fun formatValue(item: Item): String {
    // Server-formatted state (e.g., "28.5 °C") — best source
    val transformed = item.transformedState
    if (transformed != null && transformed !in listOf("NULL", "UNDEF")) {
        return transformed
    }

    val numericValue = item.numericState
    return when {
        item.state in listOf("NULL", "UNDEF") -> "\u2014"

        // Numeric value available — format cleanly
        numericValue != null -> {
            val formatted = if (numericValue == numericValue.toLong().toDouble())
                numericValue.toLong().toString()
            else
                String.format("%.1f", numericValue)
            val unit = if (item.type.contains(":")) getUnitSymbol(item.type) else null
            if (unit != null) "$formatted $unit" else formatted
        }

        // Switch/Contact states
        item.state == "ON" -> "ON"
        item.state == "OFF" -> "OFF"
        item.state == "OPEN" -> "OPEN"
        item.state == "CLOSED" -> "CLOSED"

        else -> item.state.take(20)
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
