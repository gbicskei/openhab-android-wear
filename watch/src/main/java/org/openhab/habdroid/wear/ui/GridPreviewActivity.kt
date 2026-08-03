package org.openhab.habdroid.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Debug activity to visualize the 3x3 concentric grid layout.
 * Launch via: adb shell am start -n org.openhab.habdroid.wear/.ui.GridPreviewActivity
 *
 * All positions are expressed as fractions of screen size — fully responsive.
 */
class GridPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReferenceGrid()
        }
    }
}

@Composable
fun ReferenceGrid() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw watch bezel circle
            drawCircle(
                color = Color.DarkGray,
                radius = w / 2f - 4.dp.toPx(),
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 2.dp.toPx())
            )

            // === ZONES (fixed dp for title/mic — they have fixed-size content) ===
            val titleZoneH = 36.dp.toPx()
            val micZoneH = 36.dp.toPx()

            // Zone boundary lines
            drawLine(Color(0xFF666666), Offset(0f, titleZoneH), Offset(w, titleZoneH), strokeWidth = 1f)
            drawLine(Color(0xFF666666), Offset(0f, h - micZoneH), Offset(w, h - micZoneH), strokeWidth = 1f)

            // Title placeholder
            drawCircle(
                color = Color(0xFF666666),
                radius = 4.dp.toPx(),
                center = Offset(w / 2f, titleZoneH / 2f)
            )

            // Mic placeholder
            val micCy = h - micZoneH / 2f
            drawRoundRect(
                color = Color(0xFF333333),
                topLeft = Offset(w / 2f - 24.dp.toPx(), micCy - 11.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(48.dp.toPx(), 22.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(11.dp.toPx())
            )

            // === GRID (fully responsive, fraction-based) ===
            val gridTop = titleZoneH
            val gridBottom = h - micZoneH
            val gridHeight = gridBottom - gridTop
            val gridCenterX = w / 2f
            val gridCenterY = (gridTop + gridBottom) / 2f

            // Row Y positions as fractions of grid height (from grid top)
            val rowYFractions = floatArrayOf(0.17f, 0.50f, 0.83f)
            val rowY = FloatArray(3) { gridTop + gridHeight * rowYFractions[it] }

            // Column X positions as fractions of screen width
            // Row 1 (middle): widest — 21%, 50%, 79%
            // Row 0 & 2 (top/bottom): narrower — 35%, 50%, 65%
            val row0ColFractions = floatArrayOf(0.35f, 0.50f, 0.65f)
            val row1ColFractions = floatArrayOf(0.21f, 0.50f, 0.79f)
            val row2ColFractions = floatArrayOf(0.35f, 0.50f, 0.65f)

            val row0ColX = FloatArray(3) { w * row0ColFractions[it] }
            val row1ColX = FloatArray(3) { w * row1ColFractions[it] }
            val row2ColX = FloatArray(3) { w * row2ColFractions[it] }
            val colsPerRow = arrayOf(row0ColX, row1ColX, row2ColX)

            // Cell size: fraction of grid height (fits 3 rows with spacing)
            val cellHalf = gridHeight / 6f

            // Draw grid cells (skip 0,1 and 2,1 — only 2 cells in rows 0 and 2)
            for (row in 0..2) {
                for (col in 0..2) {
                    if ((row == 0 || row == 2) && col == 1) continue

                    val cx = colsPerRow[row][col]
                    val cy = rowY[row]

                    drawRect(
                        color = Color(0xFF444444),
                        topLeft = Offset(cx - cellHalf, cy - cellHalf),
                        size = androidx.compose.ui.geometry.Size(cellHalf * 2, cellHalf * 2),
                        style = Stroke(width = 1.5f.dp.toPx())
                    )
                }
            }

            // Center crosshair
            drawLine(Color.Red, Offset(gridCenterX - 15, gridCenterY), Offset(gridCenterX + 15, gridCenterY), strokeWidth = 1.dp.toPx())
            drawLine(Color.Red, Offset(gridCenterX, gridCenterY - 15), Offset(gridCenterX, gridCenterY + 15), strokeWidth = 1.dp.toPx())
        }
    }
}
