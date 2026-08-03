package org.openhab.habdroid.wear.ui.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * Color picker activity for Color items on the watch tile.
 * Shows preset color chips arranged in a ring, with bezel for brightness control.
 * The outer arc shows brightness level. Tapping a chip selects that color.
 */
@AndroidEntryPoint
class ColorPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorPickerScreen()
        }
    }
}

@Composable
fun ColorPickerScreen(
    viewModel: ColorPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                viewModel.onRotateBrightness(event.verticalScrollPixels)
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
                // Edge brightness arc
                BrightnessArc(brightness = state.brightness / 100f, hue = state.hue)

                // Center content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    // openHAB logo
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val logoPainter = coil.compose.rememberAsyncImagePainter(
                        model = coil.request.ImageRequest.Builder(context)
                            .data("file:///android_asset/ic_openhab_logo.svg")
                            .decoderFactory(coil.decode.SvgDecoder.Factory())
                            .build()
                    )
                    androidx.compose.foundation.Image(
                        painter = logoPainter,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    // Item label
                    Text(
                        text = state.label,
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA),
                        maxLines = 1
                    )

                    // Preset color grid (2 rows of 5)
                    val presets = PRESET_COLORS
                    ColorPresetGrid(
                        presets = presets,
                        selectedHue = state.hue,
                        selectedSaturation = state.saturation,
                        onSelect = { viewModel.selectPreset(it) }
                    )

                    // Brightness display
                    Text(
                        text = state.brightnessDisplay,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // On/Off label
                    Text(
                        text = if (state.isOn) "ON" else "OFF",
                        fontSize = 11.sp,
                        color = if (state.isOn) hsbToColor(state.hue, state.saturation, state.brightness)
                            else Color(0xFF666666),
                        modifier = Modifier.clickable { viewModel.toggleOnOff() }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun ColorPresetGrid(
    presets: List<PresetColor>,
    selectedHue: Float,
    selectedSaturation: Float,
    onSelect: (PresetColor) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: first 5 presets
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.take(5).forEach { preset ->
                ColorChip(
                    preset = preset,
                    isSelected = (preset.hue.toInt() == selectedHue.toInt() &&
                        preset.saturation.toInt() == selectedSaturation.toInt()),
                    onClick = { onSelect(preset) }
                )
            }
        }
        // Row 2: next 5 presets
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            presets.drop(5).take(5).forEach { preset ->
                ColorChip(
                    preset = preset,
                    isSelected = (preset.hue.toInt() == selectedHue.toInt() &&
                        preset.saturation.toInt() == selectedSaturation.toInt()),
                    onClick = { onSelect(preset) }
                )
            }
        }
    }
}

@Composable
private fun ColorChip(
    preset: PresetColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipColor = hsbToColor(preset.hue, preset.saturation, 100f)
    val chipSize = if (isSelected) 28.dp else 24.dp

    Box(
        modifier = Modifier
            .size(chipSize)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Filled circle
            drawCircle(color = chipColor)
            // Selection ring
            if (isSelected) {
                drawCircle(
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Edge arc showing brightness level (0-100%).
 * Colored with the current hue.
 */
@Composable
private fun BrightnessArc(brightness: Float, hue: Float) {
    val arcColor = hsbToColor(hue, 100f, 100f)

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

        // Brightness arc
        if (brightness > 0f) {
            drawArc(
                color = arcColor,
                startAngle = 90f,
                sweepAngle = 360f * brightness,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Convert HSB (hue 0-360, saturation 0-100, brightness 0-100) to Compose Color.
 */
private fun hsbToColor(hue: Float, saturation: Float, brightness: Float): Color {
    val s = saturation / 100f
    val b = brightness / 100f
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, b))
    return Color(color)
}
