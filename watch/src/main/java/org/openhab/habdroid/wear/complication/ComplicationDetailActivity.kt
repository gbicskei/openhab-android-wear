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
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.ui.theme.WearOHTheme
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
        val slotNumber = intent.getIntExtra(EXTRA_SLOT_NUMBER, -1)

        setContent {
            WearOHTheme {
                ComplicationDetailScreen(
                    complicationId = complicationId,
                    slotNumber = slotNumber,
                    repository = repository,
                    preferenceStore = complicationPreferenceStore
                )
            }
        }
    }

    companion object {
        const val EXTRA_COMPLICATION_ID = "complication_id"
        const val EXTRA_SLOT_NUMBER = "slot_number"
    }
}

@Composable
private fun ComplicationDetailScreen(
    complicationId: Int,
    slotNumber: Int,
    repository: OpenHabRepository,
    preferenceStore: ComplicationPreferenceStore
) {
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }

    LaunchedEffect(complicationId) {
        // Resolve item: preference store (generic service) first, then server config
        // by slot number (slot service).
        val itemName = preferenceStore.getItemForSlot(complicationId)
            ?: if (slotNumber >= 1) {
                repository.getComplicationConfigs().getOrNull()
                    ?.find { it.slotNumber == slotNumber }
                    ?.item
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
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
                text = ComplicationValueFormatter.format(item, item.stateDescription?.pattern),
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // openHAB icon at top center (gray)
        val logoPainter = coil.compose.rememberAsyncImagePainter(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data("file:///android_asset/app_logo_gray.svg")
                .decoderFactory(coil.decode.SvgDecoder.Factory())
                .build()
        )
        Image(
            painter = logoPainter,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopCenter)
                .offset(y = 12.dp)
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

