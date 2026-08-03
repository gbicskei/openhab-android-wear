package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.StateDisplay
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TileSlotState
import kotlin.math.roundToInt

/**
 * Circular watch preview showing the tile layout.
 * Button positions match the actual watch tile layouts exactly.
 *
 * Watch screen = 226dp (logical). We scale proportionally to [watchSize].
 * Layouts match OpenHabTileService.computePositions():
 * - 1: center, 74dp button
 * - 2: horizontal pair, 74dp
 * - 3: V-shape (2 sides down, 1 center up), 74dp
 * - 4: 2x2 grid, 74dp
 * - 5: 2 top + center + 2 bottom, 64dp
 * - 6: 2 top + 2 mid + 2 bottom (hex without center), 64dp
 * - 7: 2 top + 3 mid + 2 bottom, 64dp
 */
@Composable
fun WatchPreview(
    layout: Int,
    slots: List<TileSlotState>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    watchSize: Dp = 280.dp,
    iconBaseUrl: String? = null,
    iconAuthHeader: String? = null,
    themeColor: Color = Color(0xFFFFB300)
) {
    val bezelColor = MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = Color(0xFF1A1A1A)
    val slotInactiveColor = Color(0xFF424242)
    val emptySlotColor = Color(0xFF2A2A2A)

    // Scale factor: watch is 226dp logical, we render at watchSize
    val scale = watchSize.value / 226f

    Box(
        modifier = modifier
            .size(watchSize)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Bezel ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = bezelColor,
                radius = size.minDimension / 2f,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Compute positions matching the watch tile service
        val (positions, btnDp) = computeWatchPositions(layout)
        val scaledBtn = (btnDp * scale).dp

        positions.forEachIndexed { index, (cx, cy) ->
            val position = index + 1
            val slot = slots.find { it.position == position }

            // Offset from center (positions are relative to 226dp screen center at 113,113)
            val offsetX = ((cx - 113f) * scale).roundToInt()
            val offsetY = ((cy - 113f) * scale).roundToInt()

            TileSlotButton(
                slot = slot,
                position = position,
                slotSize = scaledBtn,
                themeColor = themeColor,
                inactiveColor = slotInactiveColor,
                emptyColor = emptySlotColor,
                iconBaseUrl = iconBaseUrl,
                iconAuthHeader = iconAuthHeader,
                onTap = { onSlotTap(position) },
                modifier = Modifier.offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
            )
        }
    }
}

/**
 * Compute button center positions matching OpenHabTileService.computePositions().
 * Returns (positions as center-x,center-y in 226dp screen coords, button size in dp).
 */
private fun computeWatchPositions(count: Int): Pair<List<Pair<Float, Float>>, Float> {
    val screenW = 226f
    val centerX = screenW / 2f
    val centerY = screenW / 2f

    return when (count) {
        1 -> listOf(centerX to centerY) to 74f

        2 -> {
            val btn = 74f
            val spacing = btn + 4f
            val half = spacing / 2f
            listOf(
                centerX - half to centerY,
                centerX + half to centerY
            ) to btn
        }

        3 -> {
            // V-shape: 2 sides lower, 1 center upper
            val btn = 74f
            val vShift = btn * 0.42f
            val sideY = centerY + vShift
            val centerBtnY = centerY - vShift
            val hPos = computeHorizontal2(screenW, btn, edgeRatio = 1.5f)
            listOf(
                hPos[0] to sideY,
                centerX to centerBtnY,
                hPos[1] to sideY
            ) to btn
        }

        4 -> {
            // 2x2 grid
            val btn = 74f
            val spacing = btn + 4f
            val half = spacing / 2f
            val gridCenterY = centerY - 4f
            listOf(
                centerX - half to gridCenterY - half,
                centerX + half to gridCenterY - half,
                centerX - half to gridCenterY + half,
                centerX + half to gridCenterY + half
            ) to btn
        }

        5 -> {
            // 2 top + center + 2 bottom
            val btn = 64f
            val hPos = computeHorizontal2(screenW, btn, edgeRatio = 1.0f)
            val gap = computeGap2(screenW, btn, 1.0f)
            val vOffset = (btn + gap) / 2f
            listOf(
                hPos[0] to centerY - vOffset,
                centerX to centerY,
                hPos[1] to centerY - vOffset,
                hPos[0] to centerY + vOffset,
                hPos[1] to centerY + vOffset
            ) to btn
        }

        6 -> {
            // 2 top + 2 mid (sides) + 2 bottom
            val btn = 64f
            val mid = computeHorizontal3(screenW, btn, edgeRatio = 0.6f)
            val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
            val yOffset = btn * 0.85f
            listOf(
                topBottomX[0] to centerY - yOffset,
                topBottomX[1] to centerY - yOffset,
                mid[0] to centerY,
                mid[2] to centerY,
                topBottomX[0] to centerY + yOffset,
                topBottomX[1] to centerY + yOffset
            ) to btn
        }

        7 -> {
            // 2 top + 3 middle + 2 bottom
            val btn = 64f
            val mid = computeHorizontal3(screenW, btn, edgeRatio = 0.6f)
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
            ) to btn
        }

        else -> emptyList<Pair<Float, Float>>() to 64f
    }
}

private fun computeHorizontal3(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 3 * btn
    val g = remaining / (2 * edgeRatio + 2)
    val edge = g * edgeRatio
    return floatArrayOf(
        edge + btn / 2f,
        screenW / 2f,
        screenW - edge - btn / 2f
    )
}

private fun computeHorizontal2(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 2 * btn
    val g = remaining / (2 * edgeRatio + 1)
    val edge = g * edgeRatio
    return floatArrayOf(
        edge + btn / 2f,
        screenW - edge - btn / 2f
    )
}

private fun computeGap2(screenW: Float, btn: Float, edgeRatio: Float): Float {
    val remaining = screenW - 2 * btn
    return remaining / (2 * edgeRatio + 1)
}

@Composable
private fun TileSlotButton(
    slot: TileSlotState?,
    position: Int,
    slotSize: Dp,
    themeColor: Color,
    inactiveColor: Color,
    emptyColor: Color,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = slot == null || slot.isEmpty
    val bgColor = if (isEmpty) emptyColor else inactiveColor

    Box(
        modifier = modifier
            .size(slotSize)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (isEmpty) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add item to slot $position",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(slotSize * 0.4f)
            )
        } else {
            FilledSlotContent(slot = slot!!, slotSize = slotSize, themeColor = themeColor, iconBaseUrl = iconBaseUrl, iconAuthHeader = iconAuthHeader)
        }
    }
}

@Composable
private fun FilledSlotContent(
    slot: TileSlotState,
    slotSize: Dp,
    themeColor: Color,
    iconBaseUrl: String?,
    iconAuthHeader: String?
) {
    // All buttons get the same themed ring at full opacity
    val ringColor = themeColor

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Ring border (mimics watch tile ring)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - 2f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Content: label at top, icon centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Label at top
            Text(
                text = slot.effectiveLabel,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = (slotSize.value * 0.13f).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Icon from server
            val iconName = slot.effectiveIcon
            val iconUrl = when {
                iconName == "none" || iconName.isBlank() -> null
                iconName.startsWith("iconify:") -> {
                    // Format: iconify:{set}:{name} → https://api.iconify.design/{set}/{name}.svg
                    val parts = iconName.removePrefix("iconify:").split(":", limit = 2)
                    if (parts.size == 2) "https://api.iconify.design/${parts[0]}/${parts[1]}.svg"
                    else null
                }
                iconName.startsWith("material:") -> {
                    val name = iconName.removePrefix("material:").replace("-", "_")
                    "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/$name/default/48px.svg"
                }
                iconBaseUrl != null -> {
                    // Standard openHAB icon
                    "${iconBaseUrl.trimEnd('/')}/icon/$iconName?format=svg"
                }
                else -> null
            }

            // Determine if auth is needed (only for openHAB server icons, not iconify/material)
            val needsAuth = iconUrl != null && !iconName.startsWith("iconify:") && !iconName.startsWith("material:")

            if (iconUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .apply {
                            if (needsAuth && iconAuthHeader != null) {
                                addHeader("Authorization", iconAuthHeader)
                            }
                        }
                        .build(),
                    contentDescription = slot.effectiveLabel,
                    modifier = Modifier.size(slotSize * 0.45f),
                    colorFilter = ColorFilter.tint(themeColor.copy(alpha = 0.85f))
                )
            } else {
                // Fallback: icon initial letter
                Text(
                    text = iconName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = (slotSize.value * 0.3f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
