package org.openhab.habdroid.wear.data.icon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.caverock.androidsvg.SVG
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composites icon bitmaps for the tile: renders the icon graphic (SVG or PNG),
 * applies tinting based on state, and draws a ring around it.
 *
 * The final output is raw ARGB_8888 bytes ready for ProtoLayout inline image resources.
 */
@Singleton
class IconCompositor @Inject constructor() {

    companion object {
        /** Output bitmap size in pixels */
        const val SIZE = 48

        /** Ring stroke width in pixels */
        private const val RING_STROKE_WIDTH = 2f

        /** Padding between ring and icon content */
        private const val ICON_PADDING = 6f

        /** ON state opacity */
        private const val ALPHA_ON = 255

        /** OFF state opacity for ring */
        private const val RING_ALPHA_OFF = 77 // ~0.3

        /** OFF state opacity for icon */
        private const val ICON_ALPHA_OFF = 102 // ~0.4

        /** OFF state tint color */
        private const val COLOR_OFF = 0xFF757575.toInt()

        /** Label text size in pixels */
        private const val LABEL_TEXT_SIZE = 8f

        /** Height reserved for label text */
        private const val LABEL_HEIGHT = 10f

        /** Height reserved for state text */
        private const val STATE_HEIGHT = 10f

        /** State text size in pixels */
        private const val STATE_TEXT_SIZE = 7f
    }

    /**
     * Composites a final tile icon bitmap from raw icon bytes.
     * Includes label text at the top and icon graphic below, all inside the ring.
     *
     * @param bytes Raw icon bytes (SVG or PNG)
     * @param format Detected format from IconResolver
     * @param isOn Whether the item is in ON state
     * @param themeColor The user's chosen accent color (e.g., 0xFFFF9800)
     * @param label Optional label text to render at the top inside the ring
     * @param stateText Optional state text to render below the icon (for valueDisplay=value)
     * @return Raw ARGB_8888 pixel bytes for ProtoLayout, or null on failure
     */
    fun composite(bytes: ByteArray, format: IconFormat, isOn: Boolean, themeColor: Int, label: String? = null, stateText: String? = null): ByteArray? {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw glow (radial gradient behind ring, ON state only)
        if (isOn) {
            drawGlow(canvas, themeColor)
        }

        // 2. Draw ring
        drawRing(canvas, isOn, themeColor)

        // 2. Draw label at top (inside ring)
        val hasLabel = !label.isNullOrBlank()
        val hasState = !stateText.isNullOrBlank()
        if (hasLabel) {
            drawLabel(canvas, label!!)
        }

        // 3. Render and draw icon (shifted based on label/state presence)
        val iconBitmap = when (format) {
            IconFormat.SVG -> renderSvg(bytes, hasLabel = hasLabel, hasState = hasState)
            IconFormat.PNG -> renderPng(bytes, hasLabel = hasLabel, hasState = hasState)
            IconFormat.UNKNOWN -> null
        } ?: run {
            bitmap.recycle()
            return null
        }

        // 3. Apply tint/alpha and draw centered
        drawIcon(canvas, iconBitmap, isOn, themeColor, hasLabel, hasState)
        iconBitmap.recycle()

        // 4. Draw state text below icon
        if (hasState) {
            drawStateText(canvas, stateText!!, isOn, themeColor)
        }

        // 5. Convert to raw ARGB_8888 bytes
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        bitmap.recycle()

        return buffer.array()
    }

    /**
     * Draws a radial glow behind the ring (ON state only).
     * Mimics the CSS .glow-center / .glow-edge gradient.
     */
    private fun drawGlow(canvas: Canvas, themeColor: Int) {
        val centerX = SIZE / 2f
        val centerY = SIZE / 2f
        val radius = SIZE / 2f

        // Extract RGB from theme color, apply glow opacity (0.55-0.6)
        val r = Color.red(themeColor)
        val g = Color.green(themeColor)
        val b = Color.blue(themeColor)
        val glowCenter = Color.argb(140, r, g, b)  // ~0.55 opacity at center
        val glowEdge = Color.argb(0, r, g, b)      // fully transparent at edge

        val gradient = RadialGradient(
            centerX, centerY, radius,
            glowCenter, glowEdge,
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    /**
     * Draws the ring circle on the canvas.
     */
    private fun drawRing(canvas: Canvas, isOn: Boolean, themeColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_WIDTH
            color = if (isOn) themeColor else themeColor
            alpha = if (isOn) ALPHA_ON else RING_ALPHA_OFF
        }
        val inset = RING_STROKE_WIDTH / 2f
        canvas.drawOval(
            RectF(inset, inset, SIZE - inset, SIZE - inset),
            paint
        )
    }

    /**
     * Renders SVG bytes to a bitmap sized to fit within the ring.
     */
    private fun renderSvg(bytes: ByteArray, hasLabel: Boolean = false, hasState: Boolean = false): Bitmap? {
        return try {
            val svgString = String(bytes, Charsets.UTF_8)
            val svg = SVG.getFromString(svgString)
            val verticalReduction = (if (hasLabel) LABEL_HEIGHT else 0f) + (if (hasState) STATE_HEIGHT else 0f)
            val iconSize = (SIZE - (RING_STROKE_WIDTH + ICON_PADDING) * 2 - verticalReduction).toInt().coerceAtLeast(8)
            val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            svg.documentWidth = iconSize.toFloat()
            svg.documentHeight = iconSize.toFloat()
            svg.renderToCanvas(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes PNG bytes to a bitmap, scaled to fit within the ring.
     */
    private fun renderPng(bytes: ByteArray, hasLabel: Boolean = false, hasState: Boolean = false): Bitmap? {
        return try {
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val verticalReduction = (if (hasLabel) LABEL_HEIGHT else 0f) + (if (hasState) STATE_HEIGHT else 0f)
            val iconSize = (SIZE - (RING_STROKE_WIDTH + ICON_PADDING) * 2 - verticalReduction).toInt().coerceAtLeast(8)
            val scaled = Bitmap.createScaledBitmap(original, iconSize, iconSize, true)
            if (scaled !== original) original.recycle()
            scaled
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Draws the icon bitmap centered (or shifted down if label present).
     */
    private fun drawIcon(canvas: Canvas, iconBitmap: Bitmap, isOn: Boolean, themeColor: Int, hasLabel: Boolean = false, hasState: Boolean = false) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isOn) {
            paint.colorFilter = PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        } else {
            // Use dimmed theme color (matching ring appearance)
            paint.colorFilter = PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
            paint.alpha = 153 // ~0.6 opacity — visible but clearly dimmed
        }

        val offsetX = (SIZE - iconBitmap.width) / 2f
        // Center vertically, then shift based on label/state presence
        val labelShift = if (hasLabel) LABEL_HEIGHT / 2f else 0f
        val stateShift = if (hasState) -(STATE_HEIGHT / 2f) else 0f
        val offsetY = (SIZE - iconBitmap.height) / 2f + labelShift + stateShift
        canvas.drawBitmap(iconBitmap, offsetX, offsetY, paint)
    }

    /**
     * Draws label text at the top inside the ring.
     */
    private fun drawLabel(canvas: Canvas, label: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = LABEL_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT
        }
        val displayLabel = if (label.length > 8) label.take(7) + "\u2026" else label
        canvas.drawText(displayLabel, SIZE / 2f, RING_STROKE_WIDTH + ICON_PADDING + LABEL_TEXT_SIZE, paint)
    }

    /**
     * Draws state text at the bottom inside the ring.
     */
    private fun drawStateText(canvas: Canvas, text: String, isOn: Boolean, themeColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOn) themeColor else COLOR_OFF
            textSize = STATE_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val displayText = if (text.length > 8) text.take(7) + "\u2026" else text
        canvas.drawText(displayText, SIZE / 2f, SIZE - RING_STROKE_WIDTH - ICON_PADDING + 1f, paint)
    }
}
