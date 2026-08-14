package org.openhab.habdroid.wear.ui.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * Roller shutter control activity.
 * Shows the wearOH logo, item label, horizontal UP/STOP/DOWN icon buttons,
 * and the current position percentage. Bezel rotation adjusts position directly.
 * Edge arc indicates position (0% open → 100% closed).
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

                // Vertical layout: icon, label, buttons, value
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
                ) {
                    ControlLogo()

                    ControlLabel(
                        text = state.label,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Horizontal UP / STOP / DOWN — sized proportionally to screen
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.sendUp() },
                            modifier = Modifier.size(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Up",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Button(
                            onClick = { viewModel.sendStop() },
                            modifier = Modifier.size(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Button(
                            onClick = { viewModel.sendDown() },
                            modifier = Modifier.size(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeComposeColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Down",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    ControlValue(
                        text = state.positionDisplay,
                        highlighted = true,
                        highlightColor = themeComposeColor,
                        modifier = Modifier.padding(top = 12.dp)
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
 * Edge arc showing the shutter position.
 * 0% = open (no arc), 100% = closed (full arc).
 */
@Composable
private fun PositionArc(position: Float, progressColor: Color = Color(ControlStyle.DEFAULT_THEME_COLOR)) {
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
            color = Color(ControlStyle.ARC_TRACK_COLOR),
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
