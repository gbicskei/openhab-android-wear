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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PreviewIconState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PhoneItem
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.StateDisplay
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TilePageState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TileSlotState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.resolvePreviewIconState
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.resolvePreviewStateText
import kotlin.math.roundToInt

/**
 * Circular watch preview showing the tile layout — pixel-perfect match to the watch.
 * Positions, sizes, ring stroke, glow, state text, title, mic all match OpenHabTileService.
 */
@Composable
fun WatchPreview(
    layout: Int,
    slots: List<TileSlotState>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    watchSize: Dp = 280.dp,
    watchScreenWidthDp: Float = 226f,
    iconBaseUrl: String? = null,
    iconAuthHeader: String? = null,
    themeColor: Color = Color(0xFFFFB950),
    itemStates: Map<String, String> = emptyMap(),
    allPages: List<TilePageState> = emptyList(),
    allItems: List<PhoneItem> = emptyList(),
    pageName: String = "main",
    pageLabel: String = "openHAB",
    voiceEnabled: Boolean = true
) {
    val backgroundColor = Color(0xFF000000)
    val emptySlotColor = Color(0xFF2A2A2A)

    // Scale factor: watch dp → phone render dp
    val scale = watchSize.value / watchScreenWidthDp

    Box(
        modifier = modifier
            .size(watchSize)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Watch bezel ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF444444),
                radius = size.minDimension / 2f,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Compute positions matching the watch tile service
        val (positions, btnDp) = computeWatchPositions(layout, watchScreenWidthDp)
        // Watch renders composited bitmap at iconSize = btnDp - 4dp (2dp padding each side).
        // The ring is at the edge of the bitmap, so visual button size = (btnDp - 4) * scale.
        val visualBtnDp = (btnDp - 4f) * scale

        positions.forEachIndexed { index, (cx, cy) ->
            val position = index + 1
            val slot = slots.find { it.position == position }

            val centerCoord = watchScreenWidthDp / 2f
            val offsetX = ((cx - centerCoord) * scale).roundToInt()
            val offsetY = ((cy - centerCoord) * scale).roundToInt()

            // Angle from watch center to button center (for badge placement on outer ring edge)
            val dx = cx - centerCoord
            val dy = cy - centerCoord
            val badgeAngle = if (dx == 0f && dy == 0f) {
                // Center button: place badge at bottom-right (5π/4 would overlap, π/2 = bottom)
                Math.PI.toFloat() / 2f
            } else {
                kotlin.math.atan2(dy, dx)
            }

            val iconState = if (slot != null && !slot.isEmpty) {
                resolvePreviewIconState(slot, itemStates, allPages)
            } else PreviewIconState.NEUTRAL

            val stateText = if (slot != null) resolvePreviewStateText(slot, itemStates, allItems) else null

            TileSlotButton(
                slot = slot,
                position = position,
                slotSize = visualBtnDp.dp,
                themeColor = themeColor,
                emptyColor = emptySlotColor,
                iconBaseUrl = iconBaseUrl,
                iconAuthHeader = iconAuthHeader,
                iconState = iconState,
                stateText = stateText,
                badgeAngle = badgeAngle,
                onTap = { onSlotTap(position) },
                modifier = Modifier.offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
            )
        }

        // Title overlay at top (matches watch: titleCenterY = 22dp from top)
        val titleY = (22f - watchScreenWidthDp / 2f) * scale
        Box(
            modifier = Modifier
                .offset { IntOffset(0, titleY.roundToInt().dp.roundToPx()) }
                .size(width = (120f * scale).dp, height = (36f * scale).dp),
            contentAlignment = Alignment.Center
        ) {
            // openHAB logo on all pages
            val logoHeight = (36f * scale).dp
            val logoWidth = (36f * 37.945313f / 31.791088f * scale).dp
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/app_logo_gray.svg")
                    .decoderFactory(SvgDecoder.Factory())
                    .build(),
                contentDescription = "openHAB",
                modifier = Modifier.size(width = logoWidth, height = logoHeight)
            )
        }

        // Mic button at bottom on main page, back arrow on sub-pages
        // Watch: positioned at bottom with 10dp padding from edge
        val bottomY = (watchScreenWidthDp / 2f - 10f - 14f) * scale // 14dp = half of 28dp pill
        val showBottom = pageName == "main" && voiceEnabled || pageName != "main"
        if (showBottom) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, bottomY.roundToInt().dp.roundToPx()) }
                    .size(width = (40f * scale).dp, height = (24f * scale).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                if (pageName == "main") {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("file:///android_asset/ic_mic.svg")
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = "Voice",
                        modifier = Modifier.size((13f * scale).dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                } else {
                    Text(
                        text = "\u2190",
                        color = Color.White,
                        fontSize = (14f * scale).sp
                    )
                }
            }
        }
    }
}

/**
 * Compute button center positions matching OpenHabTileService.computePositions() exactly.
 */
private fun computeWatchPositions(count: Int, screenW: Float = 226f): Pair<List<Pair<Float, Float>>, Float> {
    val centerX = screenW / 2f
    val centerY = screenW / 2f
    val btnLarge = 74f
    val btnSmall = 64f

    return when (count) {
        1 -> listOf(centerX to centerY) to btnLarge
        2 -> {
            val spacing = btnLarge + 4f
            val half = spacing / 2f
            listOf(centerX - half to centerY, centerX + half to centerY) to btnLarge
        }
        3 -> {
            val vShift = btnLarge * 0.42f
            val hPos = computeHorizontal2(screenW, btnLarge, 1.5f)
            listOf(hPos[0] to centerY + vShift, centerX to centerY - vShift, hPos[1] to centerY + vShift) to btnLarge
        }
        4 -> {
            val spacing = btnLarge + 4f
            val half = spacing / 2f
            val gridCenterY = centerY - 4f
            listOf(
                centerX - half to gridCenterY - half, centerX + half to gridCenterY - half,
                centerX - half to gridCenterY + half, centerX + half to gridCenterY + half
            ) to btnLarge
        }
        5 -> {
            val hPos = computeHorizontal2(screenW, btnSmall, 1.0f)
            val gap = computeGap2(screenW, btnSmall, 1.0f)
            val vOffset = (btnSmall + gap) / 2f
            listOf(
                hPos[0] to centerY - vOffset, centerX to centerY, hPos[1] to centerY - vOffset,
                hPos[0] to centerY + vOffset, hPos[1] to centerY + vOffset
            ) to btnSmall
        }
        6 -> {
            val mid = computeHorizontal3(screenW, btnSmall, 0.6f)
            val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
            val yOffset = btnSmall * 0.85f
            listOf(
                topBottomX[0] to centerY - yOffset, topBottomX[1] to centerY - yOffset,
                mid[0] to centerY, mid[2] to centerY,
                topBottomX[0] to centerY + yOffset, topBottomX[1] to centerY + yOffset
            ) to btnSmall
        }
        7 -> {
            val mid = computeHorizontal3(screenW, btnSmall, 0.6f)
            val topBottomX = floatArrayOf((mid[0] + mid[1]) / 2f, (mid[1] + mid[2]) / 2f)
            val yOffset = btnSmall * 0.85f
            listOf(
                topBottomX[0] to centerY - yOffset, topBottomX[1] to centerY - yOffset,
                mid[0] to centerY, mid[1] to centerY, mid[2] to centerY,
                topBottomX[0] to centerY + yOffset, topBottomX[1] to centerY + yOffset
            ) to btnSmall
        }
        else -> emptyList<Pair<Float, Float>>() to btnSmall
    }
}

private fun computeHorizontal3(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 3 * btn
    val g = remaining / (2 * edgeRatio + 2)
    val edge = g * edgeRatio
    return floatArrayOf(edge + btn / 2f, screenW / 2f, screenW - edge - btn / 2f)
}

private fun computeHorizontal2(screenW: Float, btn: Float, edgeRatio: Float): FloatArray {
    val remaining = screenW - 2 * btn
    val g = remaining / (2 * edgeRatio + 1)
    val edge = g * edgeRatio
    return floatArrayOf(edge + btn / 2f, screenW - edge - btn / 2f)
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
    emptyColor: Color,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    iconState: PreviewIconState = PreviewIconState.NEUTRAL,
    stateText: String? = null,
    badgeAngle: Float = 0f,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = slot == null || slot.isEmpty

    Box(
        modifier = modifier
            .size(slotSize)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (isEmpty) {
            // Empty slot placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(emptyColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add item to slot $position",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(slotSize * 0.4f)
                )
            }
        } else {
            // Glow behind for ACTIVE (radial gradient filling the button area)
            if (iconState == PreviewIconState.ACTIVE) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                themeColor.copy(alpha = 0.55f),
                                themeColor.copy(alpha = 0f)
                            )
                        ),
                        radius = size.minDimension / 2f
                    )
                }
            }

            // Ring + content
            FilledSlotContent(
                slot = slot!!,
                slotSize = slotSize,
                themeColor = themeColor,
                iconBaseUrl = iconBaseUrl,
                iconAuthHeader = iconAuthHeader,
                iconState = iconState,
                stateText = stateText
            )
        }

        // Position badge — centered on the ring at the outward angle
        // The ring radius is slotSize/2 (since the ring is at the edge of the composable).
        // We offset from the Box center by (radius * cos(angle), radius * sin(angle)).
        val ringRadius = slotSize / 2
        val badgeOffsetX = (kotlin.math.cos(badgeAngle) * ringRadius.value).dp
        val badgeOffsetY = (kotlin.math.sin(badgeAngle) * ringRadius.value).dp
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = badgeOffsetX, y = badgeOffsetY)
                .background(
                    color = Color(0xFF555555),
                    shape = CircleShape
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun FilledSlotContent(
    slot: TileSlotState,
    slotSize: Dp,
    themeColor: Color,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    iconState: PreviewIconState = PreviewIconState.NEUTRAL,
    stateText: String? = null
) {
    // Ring alpha: ACTIVE=1.0, NEUTRAL=0.6, INACTIVE=0.3
    val ringAlpha = when (iconState) {
        PreviewIconState.ACTIVE -> 1f
        PreviewIconState.NEUTRAL -> 0.6f
        PreviewIconState.INACTIVE -> 0.3f
    }
    // Icon alpha: ACTIVE=1.0, NEUTRAL=0.8, INACTIVE=0.6
    val iconAlpha = when (iconState) {
        PreviewIconState.ACTIVE -> 1f
        PreviewIconState.NEUTRAL -> 0.8f
        PreviewIconState.INACTIVE -> 0.6f
    }
    // Icon tint: always theme color (watch uses PorterDuff themeColor for all states)
    // Only alpha changes per state
    val iconTintColor = themeColor

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Ring — watch: 4px stroke in 96px = 4.17%
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringStroke = size.minDimension * 0.0417f
            drawCircle(
                color = themeColor.copy(alpha = ringAlpha),
                radius = size.minDimension / 2f - ringStroke / 2f,
                style = Stroke(width = ringStroke)
            )
        }

        // Positioning matches IconCompositor exactly:
        // Label baseline at y=32/96=0.333 from top
        // Icon center at y=58/96=0.604 from top (no state) or 48/96=0.5 (with state)
        // State text baseline at y=81/96=0.844 from top
        //
        // Icon size: (96 - 32 - verticalReduction) / 96
        //   no state: (96 - 32 - 20) / 96 = 44/96 = 0.458
        //   with state: (96 - 32 - 40) / 96 = 24/96 = 0.25

        val hasState = stateText != null

        // Label — positioned at 0.333 from top (baseline), approximate with top padding
        // Text draws from baseline, so we use ~0.18 top padding (accounts for text ascent)
        val displayLabel = if (slot.effectiveLabel.length > 8)
            slot.effectiveLabel.take(7) + "\u2026"
        else slot.effectiveLabel

        // Label — watch draws baseline at 32/96 = 0.333 from top.
        // Visual center of label ≈ 24/96 = 0.25 from top.
        // Compose padding positions the TOP of the text box.
        // With fontSize = 0.167 * slotSize, the text box height ≈ 0.167 * slotSize.
        // To center text at 0.25: topPadding = 0.25 - textHeight/2 ≈ 0.25 - 0.083 = 0.167
        // But Compose adds internal line spacing, so use slightly less: 0.13
        Text(
            text = displayLabel,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = (slotSize.value * 0.167f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = slotSize * 0.13f)
        )

        // Icon — center at 0.604 from top (no state) or 0.5 (with state)
        // Icon size: 0.458 of slotSize (no state) or 0.25 (with state)
        val iconSize = slotSize * (if (hasState) 0.30f else 0.50f)
        // Icon — on watch, centered vertically then shifted by label/state.
        // With label only: icon center ≈ 58% from top (center of space below label to above ring bottom)
        // Offset from geometric center: (0.58 - 0.5) = 0.08 down
        // With state: centered (label pushes down, state pushes up — cancel out)
        val iconOffsetFromCenter = if (hasState) 0.dp else slotSize * 0.10f

        val iconName = slot.effectiveIcon
        val iconUrl = resolveIconUrl(iconName, iconBaseUrl)
        val needsAuth = iconUrl != null && !iconName.startsWith("iconify:") && !iconName.startsWith("material:")

        Box(
            modifier = Modifier
                .size(iconSize)
                .offset(y = iconOffsetFromCenter),
            contentAlignment = Alignment.Center
        ) {
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
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = ColorFilter.tint(iconTintColor.copy(alpha = iconAlpha))
                )
            } else {
                Text(
                    text = iconName.firstOrNull()?.uppercase() ?: "?",
                    color = iconTintColor.copy(alpha = iconAlpha),
                    fontSize = (slotSize.value * 0.3f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // State text — baseline at 0.844 from top → bottom padding = (1 - 0.844) * slotSize = 0.156
        // But text baseline offset means we need slightly less: ~0.10 from bottom
        if (hasState) {
            val displayState = if (stateText!!.length > 6) stateText.take(6) + "\u2026" else stateText
            Text(
                text = displayState,
                color = if (iconState == PreviewIconState.INACTIVE) Color(0xFF757575) else themeColor,
                fontSize = (slotSize.value * 0.146f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = slotSize * 0.06f)
            )
        }
    }
}

/** Resolve icon URL from icon name. */
private fun resolveIconUrl(iconName: String, iconBaseUrl: String?): String? {
    return when {
        iconName == "none" || iconName.isBlank() -> null
        iconName.startsWith("iconify:") -> {
            val parts = iconName.removePrefix("iconify:").split(":", limit = 2)
            if (parts.size == 2) "https://api.iconify.design/${parts[0]}/${parts[1]}.svg" else null
        }
        iconName.startsWith("material:") -> {
            val name = iconName.removePrefix("material:").replace("-", "_")
            "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/$name/default/48px.svg"
        }
        iconBaseUrl != null -> "${iconBaseUrl.trimEnd('/')}/icon/$iconName?format=svg"
        else -> null
    }
}
