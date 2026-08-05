package org.openhab.habdroid.wear.data.icon

import android.util.LruCache
import org.openhab.habdroid.wear.util.AppLog
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.repository.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves icon references to raw bytes (SVG or PNG).
 *
 * Supports multiple icon sources via prefix syntax:
 * - No prefix or "oh:" → openHAB classic icons from the server
 * - "iconify:{set}:{name}" → Iconify API (e.g., iconify:mdi:lightbulb)
 * - "material:{name}" → Google Material Symbols
 *
 * Raw bytes are cached in an LRU memory cache keyed by the full icon reference.
 * The cache is not affected by theme or state changes (those are applied during compositing).
 */
@Singleton
class IconResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore
) {
    companion object {
        private const val TAG = "IconResolver"
        private const val CACHE_SIZE = 100 // max cached icon byte arrays
        private const val ICONIFY_BASE = "https://api.iconify.design"
        private const val MATERIAL_BASE = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined"
    }

    /** LRU cache for raw icon bytes, keyed by icon reference string */
    private val cache = LruCache<String, ByteArray>(CACHE_SIZE)

    /**
     * Resolves an icon reference to raw bytes.
     * Returns null if fetch fails or icon is not found.
     *
     * @param iconRef The icon reference (e.g., "light", "oh:light", "iconify:mdi:lightbulb", "material:lock")
     * @param state The item state (used for openHAB dynamic icons, e.g., "ON", "OFF", "50")
     */
    suspend fun resolve(iconRef: String, state: String): ByteArray? {
        // For openHAB icons, cache key includes state (dynamic icon selection)
        val source = parseSource(iconRef)
        val cacheKey = if (source is IconSource.OpenHab) "${iconRef}_${state}" else iconRef

        // Check cache
        cache.get(cacheKey)?.let { return it }

        // Fetch
        val url = buildUrl(source, state) ?: return null
        val bytes = fetchBytes(url)

        if (bytes != null) {
            cache.put(cacheKey, bytes)
            return bytes
        }

        // Fallback for openHAB icons: try any previously cached state
        if (source is IconSource.OpenHab) {
            // Check if we have this icon cached with any state
            val snapshot = cache.snapshot()
            val fallback = snapshot.keys.firstOrNull { it.startsWith("${iconRef}_") }
            if (fallback != null) {
                AppLog.d(TAG, "Icon fallback: using cached '$fallback' for state '$state'")
                return cache.get(fallback)
            }
        }

        return null
    }

    /**
     * Detects if the raw bytes are SVG or PNG.
     */
    fun detectFormat(bytes: ByteArray): IconFormat {
        if (bytes.size < 4) return IconFormat.UNKNOWN
        // PNG magic: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return IconFormat.PNG
        }
        // SVG: starts with '<svg', '<?xml', or namespace-prefixed like '<ns0:svg'
        val start = String(bytes.take(100).toByteArray(), Charsets.UTF_8).trimStart()
        if (start.startsWith("<svg") || start.startsWith("<?xml") ||
            start.matches(Regex("^<[a-zA-Z][a-zA-Z0-9]*:svg[\\s>].*", RegexOption.DOT_MATCHES_ALL))
        ) {
            return IconFormat.SVG
        }
        return IconFormat.UNKNOWN
    }

    private fun parseSource(iconRef: String): IconSource {
        return when {
            iconRef.startsWith("iconify:") -> {
                // Format: iconify:{set}:{name}
                val parts = iconRef.removePrefix("iconify:").split(":", limit = 2)
                if (parts.size == 2) IconSource.Iconify(set = parts[0], name = parts[1])
                else IconSource.OpenHab(name = iconRef)
            }
            iconRef.startsWith("material:") -> {
                val name = iconRef.removePrefix("material:")
                IconSource.Material(name = name)
            }
            iconRef.startsWith("oh:") -> {
                val name = iconRef.removePrefix("oh:")
                IconSource.OpenHab(name = name)
            }
            else -> {
                // No prefix — default to openHAB classic
                IconSource.OpenHab(name = iconRef)
            }
        }
    }

    private suspend fun buildUrl(source: IconSource, state: String): String? {
        return when (source) {
            is IconSource.OpenHab -> {
                val credentials = credentialStore.credentials.first() ?: return null
                "${credentials.serverUrl.trimEnd('/')}/icon/${source.name}?format=svg&state=$state"
            }
            is IconSource.Iconify -> {
                "$ICONIFY_BASE/${source.set}/${source.name}.svg"
            }
            is IconSource.Material -> {
                val gstaticName = source.name.replace("-", "_")
                "$MATERIAL_BASE/$gstaticName/default/48px.svg"
            }
        }
    }

    private fun fetchBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val bytes = if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                AppLog.w(TAG, "Icon fetch failed: $url → ${response.code}")
                null
            }
            response.close()
            bytes
        } catch (e: Exception) {
            AppLog.e(TAG, "Icon fetch error: $url", e)
            null
        }
    }
}

/** Parsed icon source */
sealed interface IconSource {
    data class OpenHab(val name: String) : IconSource
    data class Iconify(val set: String, val name: String) : IconSource
    data class Material(val name: String) : IconSource
}

/** Detected image format */
enum class IconFormat {
    SVG,
    PNG,
    UNKNOWN
}
