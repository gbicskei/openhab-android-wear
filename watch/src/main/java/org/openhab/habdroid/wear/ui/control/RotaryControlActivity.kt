package org.openhab.habdroid.wear.ui.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import dagger.hilt.android.AndroidEntryPoint

/**
 * Dedicated control screen for range/dimmer items.
 * Shows the current value centered with an arc progress indicator on the edge.
 * Bezel rotation adjusts the value with debounced command sending.
 */
@AndroidEntryPoint
class RotaryControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotaryControlScreen()
        }
    }
}

@Composable
fun RotaryControlScreen(
    viewModel: RotaryControlViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                viewModel.onRotate(event.verticalScrollPixels)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> {
                Text("Loading...", color = Color.White)
            }

            state.error != null -> {
                Text("Error: ${state.error}", color = Color.Red, textAlign = TextAlign.Center)
            }

            else -> {
                // Edge progress arc
                val progress = ((state.currentValue - state.min) / (state.max - state.min))
                    .coerceIn(0.0, 1.0).toFloat()

                EdgeProgressIndicator(progress = progress, progressColor = Color(state.themeColor))

                // Center content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    // openHAB logo
                    val context = LocalContext.current
                    val logoPainter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/ic_wearoh_logo.svg")
                            .decoderFactory(SvgDecoder.Factory())
                            .build()
                    )
                    Image(
                        painter = logoPainter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )

                    // Item label
                    Text(
                        text = state.label,
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA)
                    )

                    // Current value — large and centered
                    Text(
                        text = state.displayValue,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Draws an arc progress indicator along the edge of the round screen.
 * Starts at the bottom and fills clockwise.
 */
@Composable
private fun EdgeProgressIndicator(
    progress: Float,
    trackColor: Color = Color(0xFF333333),
    progressColor: Color = Color(0xFFFFB300)
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 8.dp.toPx()
        val padding = 4.dp.toPx()
        val arcSize = Size(
            size.width - padding * 2 - strokeWidth,
            size.height - padding * 2 - strokeWidth
        )
        val topLeft = Offset(padding + strokeWidth / 2, padding + strokeWidth / 2)

        // Track (full circle)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Progress arc (starts at bottom = 90°, sweeps clockwise)
        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = 90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
