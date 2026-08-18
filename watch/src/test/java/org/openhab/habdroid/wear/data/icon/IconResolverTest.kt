package org.openhab.habdroid.wear.data.icon

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconResolverTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var plainClient: OkHttpClient
    private lateinit var iconResolver: IconResolver

    @Before
    fun setup() {
        okHttpClient = mockk()
        plainClient = mockk()
        iconResolver = IconResolver(okHttpClient, plainClient)
    }

    // --- Source Parsing Tests ---

    @Test
    fun `detectFormat identifies SVG from xml declaration`() {
        val svgBytes = "<?xml version=\"1.0\"?><svg></svg>".toByteArray()
        assertEquals(IconFormat.SVG, iconResolver.detectFormat(svgBytes))
    }

    @Test
    fun `detectFormat identifies SVG from svg tag`() {
        val svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray()
        assertEquals(IconFormat.SVG, iconResolver.detectFormat(svgBytes))
    }

    @Test
    fun `detectFormat identifies SVG with leading whitespace`() {
        val svgBytes = "   \n  <svg></svg>".toByteArray()
        assertEquals(IconFormat.SVG, iconResolver.detectFormat(svgBytes))
    }

    @Test
    fun `detectFormat identifies PNG from magic bytes`() {
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertEquals(IconFormat.PNG, iconResolver.detectFormat(pngMagic))
    }

    @Test
    fun `detectFormat returns UNKNOWN for random bytes`() {
        val randomBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals(IconFormat.UNKNOWN, iconResolver.detectFormat(randomBytes))
    }

    @Test
    fun `detectFormat returns UNKNOWN for empty bytes`() {
        assertEquals(IconFormat.UNKNOWN, iconResolver.detectFormat(byteArrayOf()))
    }

    @Test
    fun `detectFormat returns UNKNOWN for too short bytes`() {
        assertEquals(IconFormat.UNKNOWN, iconResolver.detectFormat(byteArrayOf(0x01, 0x02)))
    }

    // --- Resolve Tests ---

    @Test
    fun `resolve fetches openHAB icon using placeholder URL through main client`() = runTest {
        val svgBytes = "<svg></svg>".toByteArray()
        // IconResolver builds: https://placeholder.openhab.org/icon/light?format=svg&state=ON
        // AuthInterceptor (not present in test) would rewrite the URL, but here we mock the raw placeholder URL
        mockHttpResponse(okHttpClient, "https://placeholder.openhab.org/icon/light?format=svg&state=ON", svgBytes)

        val result = iconResolver.resolve("light", "ON")
        assertNotNull(result)
        assertEquals(String(svgBytes), String(result!!))
    }

    @Test
    fun `resolve fetches oh-prefixed icon using placeholder URL`() = runTest {
        val svgBytes = "<svg></svg>".toByteArray()
        mockHttpResponse(okHttpClient, "https://placeholder.openhab.org/icon/heating?format=svg&state=OFF", svgBytes)

        val result = iconResolver.resolve("oh:heating", "OFF")
        assertNotNull(result)
    }

    @Test
    fun `resolve fetches iconify icon using plain client`() = runTest {
        val svgBytes = "<svg>iconify</svg>".toByteArray()
        mockHttpResponse(plainClient, "https://api.iconify.design/mdi/lightbulb.svg", svgBytes)

        val result = iconResolver.resolve("iconify:mdi:lightbulb", "ON")
        assertNotNull(result)
        assertEquals(String(svgBytes), String(result!!))
    }

    @Test
    fun `resolve fetches material icon using plain client`() = runTest {
        val svgBytes = "<svg>material</svg>".toByteArray()
        mockHttpResponse(
            plainClient,
            "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/lock/default/48px.svg",
            svgBytes
        )

        val result = iconResolver.resolve("material:lock", "ON")
        assertNotNull(result)
    }

    @Test
    fun `resolve returns null on HTTP error`() = runTest {
        mockHttpError(okHttpClient, "https://placeholder.openhab.org/icon/missing?format=svg&state=ON", 404)

        val result = iconResolver.resolve("missing", "ON")
        assertNull(result)
    }

    @Test
    fun `resolve uses cache on second call`() = runTest {
        val svgBytes = "<svg>cached</svg>".toByteArray()
        mockHttpResponse(plainClient, "https://api.iconify.design/mdi/home.svg", svgBytes)

        // First call — fetches from network
        val first = iconResolver.resolve("iconify:mdi:home", "ON")
        assertNotNull(first)

        // Second call — should use cache (mock won't be called again)
        val second = iconResolver.resolve("iconify:mdi:home", "ON")
        assertNotNull(second)
        assertEquals(String(first!!), String(second!!))
    }

    @Test
    fun `resolve caches openHAB icons with state suffix`() = runTest {
        val onBytes = "<svg>on</svg>".toByteArray()
        val offBytes = "<svg>off</svg>".toByteArray()

        mockHttpResponse(okHttpClient, "https://placeholder.openhab.org/icon/light?format=svg&state=ON", onBytes)

        val onResult = iconResolver.resolve("light", "ON")
        assertNotNull(onResult)
        assertEquals("<svg>on</svg>", String(onResult!!))

        // Different state should fetch again (different cache key)
        mockHttpResponse(okHttpClient, "https://placeholder.openhab.org/icon/light?format=svg&state=OFF", offBytes)

        val offResult = iconResolver.resolve("light", "OFF")
        assertNotNull(offResult)
        assertEquals("<svg>off</svg>", String(offResult!!))
    }

    // --- Helpers ---

    private fun mockHttpResponse(client: OkHttpClient, url: String, responseBytes: ByteArray) {
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBytes.toResponseBody(null))
            .build()
        every { call.execute() } returns response
        every { client.newCall(match { it.url.toString() == url }) } returns call
    }

    private fun mockHttpError(client: OkHttpClient, url: String, code: Int) {
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body("".toResponseBody(null))
            .build()
        every { call.execute() } returns response
        every { client.newCall(match { it.url.toString() == url }) } returns call
    }
}
