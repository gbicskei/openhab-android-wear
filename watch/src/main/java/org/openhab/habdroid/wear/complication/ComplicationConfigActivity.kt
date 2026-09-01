package org.openhab.habdroid.wear.complication

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.ui.theme.WearOHTheme

/**
 * Configuration activity for the complication data source.
 * Launched by the system when the user selects "openHAB Item" in the complication picker.
 * Shows a list of items flagged for complications via wearTile metadata.
 *
 * Must return RESULT_OK to confirm selection or RESULT_CANCELED to abort.
 */
@AndroidEntryPoint
class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val complicationId = intent.getIntExtra(
            ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, -1
        )

        // If no valid complication ID, cancel immediately
        if (complicationId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setResult(Activity.RESULT_CANCELED) // Default to canceled until selection is made

        setContent {
            WearOHTheme {
                ComplicationConfigScreen(
                    complicationId = complicationId,
                    onItemSelected = {
                        // Request complication data refresh
                        ComplicationDataSourceUpdateRequester.create(
                            this, ComponentName(this, OpenHabComplicationService::class.java)
                        ).requestUpdateAll()
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun ComplicationConfigScreen(
    complicationId: Int,
    onItemSelected: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ComplicationConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    when (val state = uiState) {
        is ComplicationConfigUiState.Loading -> LoadingContent()
        is ComplicationConfigUiState.Success -> ItemPickerContent(
            items = state.items,
            iconResolver = viewModel.iconResolver,
            onItemSelected = { item ->
                scope.launch {
                    viewModel.selectItem(complicationId, item.name)
                    onItemSelected()
                }
            }
        )
        is ComplicationConfigUiState.Empty -> EmptyContent()
        is ComplicationConfigUiState.Error -> ErrorContent(
            message = state.message,
            onRetry = { viewModel.retry() }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ItemPickerContent(
    items: List<Item>,
    iconResolver: org.openhab.habdroid.wear.data.icon.IconResolver,
    onItemSelected: (Item) -> Unit
) {
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
                    Text(
                        text = stringResource(R.string.complication_config_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            items(items, key = { it.name }) { item ->
                ComplicationItemCard(
                    item = item,
                    iconResolver = iconResolver,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    }
}

@Composable
private fun ComplicationItemCard(
    item: Item,
    iconResolver: org.openhab.habdroid.wear.data.icon.IconResolver,
    onClick: () -> Unit
) {
    // Resolve icon asynchronously
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, item.iconName) {
        val iconRef = item.iconName
        if (iconRef.isNotBlank()) {
            val bytes = iconResolver.resolve(iconRef, item.state)
            if (bytes != null) {
                val format = iconResolver.detectFormat(bytes)
                val bitmap = when (format) {
                    org.openhab.habdroid.wear.data.icon.IconFormat.SVG -> {
                        val bmp = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        val svg = com.caverock.androidsvg.SVG.getFromString(String(bytes, Charsets.UTF_8))
                        svg.documentWidth = 32f
                        svg.documentHeight = 32f
                        svg.renderToCanvas(canvas)
                        bmp
                    }
                    org.openhab.habdroid.wear.data.icon.IconFormat.PNG -> {
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    else -> null
                }
                value = bitmap?.asImageBitmap()
            }
        }
    }

    TitleCard(
        onClick = onClick,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                iconBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.displayLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = formatStateForPicker(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.complication_no_items),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * Format item state for display in the picker list.
 * Shows type and current state as a brief summary line.
 */
private fun formatStateForPicker(item: Item): String {
    val pattern = item.stateDescription?.pattern
    val state = when {
        item.state in listOf("NULL", "UNDEF") -> "\u2014"
        item.transformedState != null && item.transformedState !in listOf("NULL", "UNDEF") ->
            item.transformedState.take(20)
        // QuantityType: convert to the pattern's target unit client-side (as MainUI does).
        !pattern.isNullOrBlank() && QuantityFormatter.format(item.state, pattern) != null ->
            QuantityFormatter.format(item.state, pattern)!!.take(20)
        item.numericState != null -> {
            val v = item.numericState!!
            val formatted = if (v == v.toLong().toDouble()) v.toLong().toString()
                else String.format("%.1f", v)
            formatted
        }
        else -> item.state.take(20)
    }
    val typeLabel = item.type.removePrefix("Number:").ifEmpty { item.type }
    return "$typeLabel \u2022 $state"
}
