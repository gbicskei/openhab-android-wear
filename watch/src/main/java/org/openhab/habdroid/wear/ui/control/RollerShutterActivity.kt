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
import androidx.compose.foundation.layout.Row
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import coil.compose.rememberAsyncImagePainter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Roller shutter control activity.
 * Shows UP/STOP/DOWN buttons vertically, current position percentage in the center,
 * and a position arc on the edge. Bezel rotation adjusts position directly.
 */
@AndroidEntryPoint
class RollerShutterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RollerShutterScreen()
        }
    }
}

@Composable
fun RollerShutterScreen(
    viewModel: RollerShutterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                viewModel.onRotatePosition(event.verticalScrollPixels)
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
                // Edge position arc
                val themeComposeColor = Color(state.themeColor)
                PositionArc(position = state.position / 100f, progressColor = themeComposeColor)

                // openHAB logo at top
                val context = LocalContext.current
                val logoPainter = rememberAsyncImagePainter(
                    model = coil.request.ImageRequest.Builder(context)
                        .data("file:///android_asset/ic_wearoh_logo.svg")
                        .decoderFactory(coil.decode.SvgDecoder.Factory())
                        .build()
                )
                Image(
                    painter = logoPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                        .size(20.dp)
                )

                // Item label below logo
                Text(
                    text = state.label,
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                )

                // Position value at bottom
                Text(
                    text = state.positionDisplay,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeComposeColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                )

                // Center: compact UP / STOP / DOWN
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 50.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendUp() },
                        modifier = Modifier.size(width = 72.dp, height = 34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor)
                    ) {
                        Text("UP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.sendStop() },
                        modifier = Modifier.size(width = 72.dp, height = 34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor.copy(alpha = 0.5f))
                    ) {
                        Text("STOP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.sendDown() },
                        modifier = Modifier.size(width = 72.dp, height = 34.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor.copy(alpha = 0.3f))
                    ) {
                        Text("DOWN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Edge arc showing the shutter position.
 * 0% = open (no arc), 100% = closed (full arc).
 */
@Composable
private fun PositionArc(position: Float, progressColor: Color = Color(0xFFFFB300)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 8.dp.toPx()
        val padding = 4.dp.toPx()
        val arcSize = Size(
            size.width - padding * 2 - strokeWidth,
            size.height - padding * 2 - strokeWidth
        )
        val topLeft = Offset(padding + strokeWidth / 2, padding + strokeWidth / 2)

        // Track
        drawArc(
            color = Color(0xFF333333),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Position arc (starts at top = -90°, sweeps clockwise)
        if (position > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * position,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
