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
import android.util.LruCache
import com.caverock.androidsvg.SVG
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composites icon bitmaps for the tile: renders the icon graphic (SVG or PNG),
 * applies tinting based on state, and draws a ring around it.
 *
 * The final output is PNG-compressed bytes for ProtoLayout inline image resources.
 * PNG keeps the payload small enough to avoid Binder TransactionTooLargeException.
 *
 * Results are cached in an LRU keyed on the full input tuple (icon content hash,
 * format, state, theme color, label, state text). Cache hits skip all rendering
 * and compression work entirely.
 */
@Singleton
class IconCompositor @Inject constructor() {

    companion object {
        /** Output bitmap size in pixels (renders at 2x for sharp display on high-density watches) */
        const val SIZE = 96

        /** Ring stroke width in pixels */
        private const val RING_STROKE_WIDTH = 4f

        /** Padding between ring and icon content */
        private const val ICON_PADDING = 12f

        /** ON state opacity */
        private const val ALPHA_ON = 255

        /** Neutral state opacity for ring */
        private const val RING_ALPHA_NEUTRAL = 153 // ~0.6

        /** Neutral state opacity for icon */
        private const val ICON_ALPHA_NEUTRAL = 204 // ~0.8

        /** OFF state opacity for ring */
        private const val RING_ALPHA_OFF = 77 // ~0.3

        /** OFF state opacity for icon */
        private const val ICON_ALPHA_OFF = 153 // ~0.6

        /** OFF state tint color */
        private const val COLOR_OFF = 0xFF757575.toInt()

        /** Label text size in pixels */
        private const val LABEL_TEXT_SIZE = 16f

        /** Height reserved for label text */
        private const val LABEL_HEIGHT = 20f

        /** Height reserved for state text */
        private const val STATE_HEIGHT = 20f

        /** State text size in pixels */
        private const val STATE_TEXT_SIZE = 14f

        /** Fallback "?" text size in pixels */
        private const val FALLBACK_TEXT_SIZE = 32f

        /** Max cached composited icons (each ~3-5KB compressed, so ~500KB total max) */
        private const val CACHE_MAX_ENTRIES = 128
    }

    /**
     * LRU cache: composite input key → compressed image bytes.
     * Sized by entry count (not bytes) since entries are uniformly small.
     */
    private val cache = LruCache<CompositeKey, ByteArray>(CACHE_MAX_ENTRIES)

    /**
     * Cache key representing the full set of inputs that determine the output image.
     */
    private data class CompositeKey(
        val iconContentHash: Int,
        val format: IconFormat,
        val state: IconState,
        val themeColor: Int,
        val label: String?,
        val stateText: String?
    )

    /**
     * Composites a final tile icon bitmap from raw icon bytes.
     * Includes label text at the top and icon graphic below, all inside the ring.
     *
     * Returns a cached result if the same inputs were seen before.
     *
     * @param bytes Raw icon bytes (SVG or PNG)
     * @param format Detected format from IconResolver
     * @param state Icon display state (ACTIVE, NEUTRAL, or INACTIVE)
     * @param themeColor The user's chosen accent color (e.g., 0xFFFF9800)
     * @param label Optional label text to render at the top inside the ring
     * @param stateText Optional state text to render below the icon (for valueDisplay=value)
     * @return Compressed pixel bytes for ProtoLayout, or null on failure
     */
    fun composite(bytes: ByteArray, format: IconFormat, state: IconState, themeColor: Int, label: String? = null, stateText: String? = null): ByteArray? {
        val key = CompositeKey(
            iconContentHash = bytes.contentHashCode(),
            format = format,
            state = state,
            themeColor = themeColor,
            label = label,
            stateText = stateText
        )

        cache.get(key)?.let { return it }

        val result = render(bytes, format, state, themeColor, label, stateText) ?: return null
        cache.put(key, result)
        return result
    }

    /**
     * Renders the composited icon (no cache involvement).
     */
    private fun render(bytes: ByteArray, format: IconFormat, state: IconState, themeColor: Int, label: String?, stateText: String?): ByteArray? {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw glow (radial gradient behind ring, ACTIVE state only)
        if (state == IconState.ACTIVE) {
            drawGlow(canvas, themeColor)
        }

        // 2. Draw ring
        drawRing(canvas, state, themeColor)

        // 3. Draw label at top (inside ring)
        val hasLabel = !label.isNullOrBlank()
        val hasState = !stateText.isNullOrBlank()
        if (hasLabel) {
            drawLabel(canvas, label!!)
        }

        // 4. Render and draw icon (shifted based on label/state presence)
        val iconBitmap = when (format) {
            IconFormat.SVG -> renderSvg(bytes, hasLabel = hasLabel, hasState = hasState)
            IconFormat.PNG -> renderPng(bytes, hasLabel = hasLabel, hasState = hasState)
            IconFormat.UNKNOWN -> null
        } ?: run {
            bitmap.recycle()
            return null
        }

        // 5. Apply tint/alpha and draw centered
        drawIcon(canvas, iconBitmap, state, themeColor, hasLabel, hasState)
        iconBitmap.recycle()

        // 6. Draw state text below icon
        if (hasState) {
            drawStateText(canvas, stateText!!, state, themeColor)
        }

        // 7. Compress to WebP lossless (fast encode with alpha, avoids TransactionTooLargeException)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
        bitmap.recycle()

        return stream.toByteArray()
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
    private fun drawRing(canvas: Canvas, state: IconState, themeColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_WIDTH
            color = themeColor
            alpha = when (state) {
                IconState.ACTIVE -> ALPHA_ON
                IconState.NEUTRAL -> RING_ALPHA_NEUTRAL
                IconState.INACTIVE -> RING_ALPHA_OFF
            }
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
            AppLog.w("IconCompositor", "SVG render failed: ${e.message}", e)
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
            AppLog.w("IconCompositor", "PNG render failed: ${e.message}", e)
            null
        }
    }

    /**
     * Draws the icon bitmap centered (or shifted down if label present).
     */
    private fun drawIcon(canvas: Canvas, iconBitmap: Bitmap, state: IconState, themeColor: Int, hasLabel: Boolean = false, hasState: Boolean = false) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.colorFilter = PorterDuffColorFilter(themeColor, PorterDuff.Mode.SRC_IN)
        paint.alpha = when (state) {
            IconState.ACTIVE -> ALPHA_ON
            IconState.NEUTRAL -> ICON_ALPHA_NEUTRAL
            IconState.INACTIVE -> ICON_ALPHA_OFF
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
    private fun drawStateText(canvas: Canvas, text: String, state: IconState, themeColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (state == IconState.INACTIVE) COLOR_OFF else themeColor
            textSize = STATE_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val displayText = if (text.length > 8) text.take(7) + "\u2026" else text
        canvas.drawText(displayText, SIZE / 2f, SIZE - RING_STROKE_WIDTH - ICON_PADDING + 1f, paint)
    }

    /**
     * Generates a fallback icon when the real icon cannot be fetched or parsed.
     * Renders ring + label + "?" placeholder + optional state text, keeping the
     * button visible and tappable until the real icon loads on retry.
     *
     * @param state Icon display state (ACTIVE, NEUTRAL, or INACTIVE)
     * @param themeColor The user's chosen accent color
     * @param label Optional label text to render at the top inside the ring
     * @param stateText Optional state text to render below the placeholder
     * @return Compressed pixel bytes for ProtoLayout, or null on failure
     */
    fun fallback(state: IconState, themeColor: Int, label: String? = null, stateText: String? = null): ByteArray? {
        val key = CompositeKey(
            iconContentHash = "fallback".hashCode(),
            format = IconFormat.UNKNOWN,
            state = state,
            themeColor = themeColor,
            label = label,
            stateText = stateText
        )

        cache.get(key)?.let { return it }

        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw glow (ACTIVE state only)
        if (state == IconState.ACTIVE) {
            drawGlow(canvas, themeColor)
        }

        // 2. Draw ring
        drawRing(canvas, state, themeColor)

        // 3. Draw label at top
        val hasLabel = !label.isNullOrBlank()
        val hasState = !stateText.isNullOrBlank()
        if (hasLabel) {
            drawLabel(canvas, label!!)
        }

        // 4. Draw "?" placeholder where the icon would be
        val labelShift = if (hasLabel) LABEL_HEIGHT / 2f else 0f
        val stateShift = if (hasState) -(STATE_HEIGHT / 2f) else 0f
        val placeholderY = SIZE / 2f + labelShift + stateShift + FALLBACK_TEXT_SIZE / 3f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeColor
            alpha = when (state) {
                IconState.ACTIVE -> ALPHA_ON
                IconState.NEUTRAL -> ICON_ALPHA_NEUTRAL
                IconState.INACTIVE -> ICON_ALPHA_OFF
            }
            textSize = FALLBACK_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("?", SIZE / 2f, placeholderY, paint)

        // 5. Draw state text below
        if (hasState) {
            drawStateText(canvas, stateText!!, state, themeColor)
        }

        // 6. Compress
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
        bitmap.recycle()

        val result = stream.toByteArray()
        cache.put(key, result)
        return result
    }
}
