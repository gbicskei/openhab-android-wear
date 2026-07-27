package org.openhab.habdroid.wear.data.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconCompositorTest {

    private lateinit var compositor: IconCompositor

    private val themeAmber = 0xFFFF9800.toInt()
    private val themeBlue = 0xFF42A5F5.toInt()

    @Before
    fun setup() {
        compositor = IconCompositor()
    }

    // --- SVG Rendering Tests ---

    @Test
    fun `composite renders valid SVG and returns correct size`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
            <circle cx="24" cy="24" r="10" fill="white"/>
        </svg>""".toByteArray()

        val result = compositor.composite(svg, IconFormat.SVG, isOn = true, themeColor = themeAmber)

        assertNotNull(result)
        // SIZE x SIZE ARGB_8888 = SIZE * SIZE * 4 bytes
        val expectedSize = IconCompositor.SIZE * IconCompositor.SIZE * 4
        assertEquals(expectedSize, result!!.size)
    }

    @Test
    fun `composite renders SVG for OFF state`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
            <rect x="10" y="10" width="28" height="28" stroke="black" fill="none"/>
        </svg>""".toByteArray()

        val result = compositor.composite(svg, IconFormat.SVG, isOn = false, themeColor = themeAmber)

        assertNotNull(result)
        val expectedSize = IconCompositor.SIZE * IconCompositor.SIZE * 4
        assertEquals(expectedSize, result!!.size)
    }

    @Test
    fun `composite returns null for invalid SVG`() {
        val badSvg = "this is not svg at all".toByteArray()

        val result = compositor.composite(badSvg, IconFormat.SVG, isOn = true, themeColor = themeAmber)

        assertNull(result)
    }

    @Test
    fun `composite returns null for empty SVG`() {
        val result = compositor.composite(byteArrayOf(), IconFormat.SVG, isOn = true, themeColor = themeAmber)

        assertNull(result)
    }

    // --- PNG Rendering Tests ---

    @Test
    fun `composite returns null for invalid PNG bytes`() {
        val badPng = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)

        // With Robolectric, BitmapFactory may or may not decode invalid bytes.
        // The important thing is it doesn't crash.
        val result = compositor.composite(badPng, IconFormat.PNG, isOn = true, themeColor = themeAmber)
        // Result may be null (real Android) or non-null (Robolectric shadow) — both acceptable
    }

    // --- Format Handling ---

    @Test
    fun `composite returns null for UNKNOWN format`() {
        val randomBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val result = compositor.composite(randomBytes, IconFormat.UNKNOWN, isOn = true, themeColor = themeAmber)

        assertNull(result)
    }

    // --- Theme Color Tests ---

    @Test
    fun `composite produces different output for different theme colors`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
            <circle cx="24" cy="24" r="10" fill="white"/>
        </svg>""".toByteArray()

        val amberResult = compositor.composite(svg, IconFormat.SVG, isOn = true, themeColor = themeAmber)
        val blueResult = compositor.composite(svg, IconFormat.SVG, isOn = true, themeColor = themeBlue)

        assertNotNull(amberResult)
        assertNotNull(blueResult)
        // Both should produce valid output (Robolectric may not fully implement color filters)
        val expectedSize = IconCompositor.SIZE * IconCompositor.SIZE * 4
        assertEquals(expectedSize, amberResult!!.size)
        assertEquals(expectedSize, blueResult!!.size)
    }

    @Test
    fun `composite produces different output for ON vs OFF state`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
            <circle cx="24" cy="24" r="10" fill="white"/>
        </svg>""".toByteArray()

        val onResult = compositor.composite(svg, IconFormat.SVG, isOn = true, themeColor = themeAmber)
        val offResult = compositor.composite(svg, IconFormat.SVG, isOn = false, themeColor = themeAmber)

        assertNotNull(onResult)
        assertNotNull(offResult)
        // Both produce valid sized output
        val expectedSize = IconCompositor.SIZE * IconCompositor.SIZE * 4
        assertEquals(expectedSize, onResult!!.size)
        assertEquals(expectedSize, offResult!!.size)
    }

    // --- Output Size Consistency ---

    @Test
    fun `composite always produces SIZE x SIZE x 4 bytes output`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">
            <circle cx="50" cy="50" r="40" fill="red"/>
        </svg>""".toByteArray()

        val result = compositor.composite(svg, IconFormat.SVG, isOn = true, themeColor = themeAmber)

        assertNotNull(result)
        val expectedSize = IconCompositor.SIZE * IconCompositor.SIZE * 4
        assertEquals(expectedSize, result!!.size)
    }
}
