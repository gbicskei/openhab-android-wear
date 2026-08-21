package org.openhab.habdroid.wear.ui.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.ThemeStore
import org.openhab.habdroid.wear.data.repository.TileTheme
import org.openhab.habdroid.wear.tile.OpenHabTileService
import org.openhab.habdroid.wear.ui.theme.WearOHTheme
import javax.inject.Inject
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Theme picker: shows actual tile layout (read-only) with live theme preview.
 * Rotate bezel/crown to cycle themes. Swipe back to confirm.
 * Top edge shows dot indicators for theme position.
 */
@AndroidEntryPoint
class ThemePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearOHTheme {
                ThemePickerScreen(
                    onDone = {
                        TileService.getUpdater(this)
                            .requestUpdate(OpenHabTileService::class.java)
                        finish()
                    }
                )
            }
        }
    }
}

@HiltViewModel
class ThemePickerViewModel @Inject constructor(
    private val themeStore: ThemeStore
) : ViewModel() {

    private val themes = TileTheme.entries.toList()

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    init {
        viewModelScope.launch {
            val current = themeStore.getTheme()
            _selectedIndex.value = themes.indexOf(current).coerceAtLeast(0)
        }
    }

    fun next() {
        _selectedIndex.value = (_selectedIndex.value + 1) % themes.size
        save()
    }

    fun previous() {
        _selectedIndex.value = (_selectedIndex.value - 1 + themes.size) % themes.size
        save()
    }

    fun currentTheme(): TileTheme = themes[_selectedIndex.value]

    private fun save() {
        viewModelScope.launch {
            themeStore.setTheme(themes[_selectedIndex.value])
        }
    }
}

@Composable
fun ThemePickerScreen(
    viewModel: ThemePickerViewModel = hiltViewModel(),
    onDone: () -> Unit
) {
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val themes = TileTheme.entries.toList()
    val current = themes[selectedIndex]

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                if (event.verticalScrollPixels > 0) {
                    viewModel.next()
                } else if (event.verticalScrollPixels < 0) {
                    viewModel.previous()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val themeColor = Color(current.color)

            // === LAYOUT: same math as tile service ===
            val btn = 64f.dp.toPx()
            val centerX = w / 2f
            val centerY = h / 2f
            val edgeRatio = 15f

            // Middle row horizontal positions
            val remaining = w - 3 * btn
            val g = remaining / (2 * edgeRatio + 2)
            val edge = g * edgeRatio
            val midX = floatArrayOf(
                edge + btn / 2f,
                centerX,
                w - edge - btn / 2f
            )
            val spacing = midX[1] - midX[0]

            // Top/bottom row positions (equilateral from middle row)
            val halfDx = spacing / 2f
            val yOffset = kotlin.math.sqrt(spacing * spacing - halfDx * halfDx)
            val topBottomX = floatArrayOf(
                (midX[0] + midX[1]) / 2f,
                (midX[1] + midX[2]) / 2f
            )
            val topY = centerY - yOffset
            val bottomY = centerY + yOffset

            // All 7 button centers
            val positions = arrayOf(
                Offset(topBottomX[0], topY),      // btn1
                Offset(topBottomX[1], topY),      // btn2
                Offset(midX[0], centerY),          // btn3
                Offset(midX[1], centerY),          // btn4
                Offset(midX[2], centerY),          // btn5
                Offset(topBottomX[0], bottomY),   // btn6
                Offset(topBottomX[1], bottomY)    // btn7
            )

            // Draw buttons as themed circles (read-only preview)
            val ringRadius = btn / 2f - 2.dp.toPx()
            val ringStroke = 2.dp.toPx()

            for (pos in positions) {
                // Glow (radial feel — just a larger semi-transparent circle)
                drawCircle(
                    color = themeColor.copy(alpha = 0.15f),
                    radius = ringRadius + 4.dp.toPx(),
                    center = pos
                )
                // Ring
                drawCircle(
                    color = themeColor,
                    radius = ringRadius,
                    center = pos,
                    style = Stroke(width = ringStroke)
                )
                // Inner dot (simulates icon)
                drawCircle(
                    color = themeColor.copy(alpha = 0.6f),
                    radius = 8.dp.toPx(),
                    center = pos
                )
            }

            // === DOT INDICATORS at top edge ===
            val dotRadius = 3.dp.toPx()
            val dotSpacing = 14.dp.toPx()
            val totalDotsWidth = (themes.size - 1) * dotSpacing
            val dotStartX = centerX - totalDotsWidth / 2f
            val dotY = 18.dp.toPx()

            themes.forEachIndexed { index, theme ->
                val isActive = index == selectedIndex
                val dotColor = if (isActive) themeColor else Color(0xFF555555)
                val radius = if (isActive) dotRadius * 1.4f else dotRadius

                drawCircle(
                    color = dotColor,
                    radius = radius,
                    center = Offset(dotStartX + index * dotSpacing, dotY)
                )
            }
        }
    }
}
