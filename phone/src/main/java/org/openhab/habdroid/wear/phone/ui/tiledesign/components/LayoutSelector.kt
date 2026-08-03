package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Layout selector with pictogram buttons showing actual tile layouts (1-7).
 * Each pictogram mirrors the exact button positions from the watch tile.
 */
@Composable
fun LayoutSelector(
    selectedLayout: Int,
    onLayoutSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (count in 1..7) {
            FilterChip(
                selected = selectedLayout == count,
                onClick = { onLayoutSelected(count) },
                enabled = enabled,
                label = {
                    LayoutPictogram(
                        count = count,
                        color = if (selectedLayout == count)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/**
 * Draws dots matching actual watch tile button positions.
 * Uses the same layout logic as the watch tile service, scaled to fit in a small icon.
 */
@Composable
private fun LayoutPictogram(
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Scale: watch is 226dp, we have ~22dp canvas. Scale positions to fit.
        val scale = w / 226f
        val dotRadius = w * 0.07f

        val positions = computeLayoutPositions(count)
        positions.forEach { (px, py) ->
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(px * scale, py * scale)
            )
        }
    }
}

/**
 * Same position logic as OpenHabTileService.computePositions() and WatchPreview.
 * Returns center positions in 226dp coordinate space.
 */
private fun computeLayoutPositions(count: Int): List<Pair<Float, Float>> {
    val screenW = 226f
    val centerX = screenW / 2f
    val centerY = screenW / 2f

    return when (count) {
        1 -> listOf(centerX to centerY)

        2 -> {
            val btn = 74f
            val spacing = btn + 4f
            val half = spacing / 2f
            listOf(
                centerX - half to centerY,
                centerX + half to centerY
            )
        }

        3 -> {
            val btn = 74f
            val vShift = btn * 0.42f
            val sideY = centerY + vShift
            val centerBtnY = centerY - vShift
            val hPos = computeH2(screenW, btn, 1.5f)
            listOf(
                hPos[0] to sideY,
                centerX to centerBtnY,
                hPos[1] to sideY
            )
        }

        4 -> {
            val btn = 74f
            val spacing = btn + 4f
            val half = spacing / 2f
            val gridCenterY = centerY - 4f
            listOf(
                centerX - half to gridCenterY - half,
                centerX + half to gridCenterY - half,
                centerX - half to gridCenterY + half,
                centerX + half to gridCenterY + half
            )
        }

        5 -> {
            val btn = 64f
            val hPos = computeH2(screenW, btn, 1.0f)
            val remaining = screenW - 2 * btn
            val gap = remaining / (2 * 1.0f + 1)
            val vOffset = (btn + gap) / 2f
            listOf(
                hPos[0] to centerY - vOffset,
                centerX to centerY,
                hPos[1] to centerY - vOffset,
                hPos[0] to centerY + vOffset,
                hPos[1] to centerY + vOffset
            )
        }

        6 -> {
            val btn = 64f
            val mid = computeH3(screenW, btn, 0.6f)
            val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
            val yOffset = btn * 0.85f
            listOf(
                topBottomX[0] to centerY - yOffset,
                topBottomX[1] to centerY - yOffset,
                mid[0] to centerY,
                mid[2] to centerY,
                topBottomX[0] to centerY + yOffset,
                topBottomX[1] to centerY + yOffset
            )
        }

        7 -> {
            val btn = 64f
            val mid = computeH3(screenW, btn, 0.6f)
            val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
            val yOffset = btn * 0.85f
            listOf(
                topBottomX[0] to centerY - yOffset,
                topBottomX[1] to centerY - yOffset,
                mid[0] to centerY,
                mid[1] to centerY,
                mid[2] to centerY,
                topBottomX[0] to centerY + yOffset,
                topBottomX[1] to centerY + yOffset
            )
        }

        else -> emptyList()
    }
}

private fun computeH3(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 3 * btn
    val g = remaining / (2 * edgeRatio + 2)
    val edge = g * edgeRatio
    return floatArrayOf(edge + btn / 2f, screenW / 2f, screenW - edge - btn / 2f)
}

private fun computeH2(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 2 * btn
    val g = remaining / (2 * edgeRatio + 1)
    val edge = g * edgeRatio
    return floatArrayOf(edge + btn / 2f, screenW - edge - btn / 2f)
}
