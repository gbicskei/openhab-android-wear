package org.openhab.habdroid.wear.data.api

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Dns
import org.openhab.habdroid.wear.util.AppLog
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom OkHttp DNS resolver that caches resolved addresses.
 *
 * When system DNS resolution fails (common on Wear OS due to flaky network),
 * falls back to the last-known cached IPs. Cache is persisted to SharedPreferences
 * so it survives app restarts.
 *
 * Behavior:
 * - On DNS success → update in-memory + disk cache, return fresh IPs
 * - On DNS failure + cache hit → return cached IPs (silent fallback)
 * - On DNS failure + no cache → throw UnknownHostException (first-run scenario)
 */
@Singleton
class CachingDns @Inject constructor(
    @ApplicationContext private val context: Context
) : Dns {

    companion object {
        private const val TAG = "CachingDns"
        private const val PREFS_NAME = "dns_cache"
    }

    /** In-memory cache: hostname → list of resolved addresses */
    private val memoryCache = ConcurrentHashMap<String, List<InetAddress>>()

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        // Load persisted cache into memory on creation
        loadFromDisk()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            // Attempt system DNS resolution
            val addresses = Dns.SYSTEM.lookup(hostname)
            // Success — update cache
            memoryCache[hostname] = addresses
            persistToDisk(hostname, addresses)
            addresses
        } catch (e: UnknownHostException) {
            // DNS failed — try cached addresses
            val cached = memoryCache[hostname]
            if (cached != null && cached.isNotEmpty()) {
                AppLog.d(TAG, "DNS failed for $hostname, using ${cached.size} cached address(es)")
                cached
            } else {
                AppLog.w(TAG, "DNS failed for $hostname, no cache available")
                throw e
            }
        }
    }

    /**
     * Seed the cache with externally resolved IPs (e.g., from phone sync).
     * Only writes if there's no existing cache entry (avoids overwriting fresher data).
     */
    fun seedCache(hostname: String, ips: List<String>) {
        if (memoryCache.containsKey(hostname)) {
            AppLog.d(TAG, "Cache already has entry for $hostname, skipping seed")
            return
        }
        val addresses = ips.mapNotNull { ip ->
            try {
                InetAddress.getByName(ip)
            } catch (e: Exception) {
                null
            }
        }
        if (addresses.isNotEmpty()) {
            memoryCache[hostname] = addresses
            persistToDisk(hostname, addresses)
            AppLog.d(TAG, "Seeded cache for $hostname with ${addresses.size} address(es)")
        }
    }

    private fun persistToDisk(hostname: String, addresses: List<InetAddress>) {
        val serialized = addresses.joinToString(",") { it.hostAddress ?: "" }
        prefs.edit().putString(hostname, serialized).apply()
    }

    private fun loadFromDisk() {
        prefs.all.forEach { (hostname, value) ->
            if (value is String && value.isNotBlank()) {
                val addresses = value.split(",").mapNotNull { ip ->
                    try {
                        InetAddress.getByName(ip)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (addresses.isNotEmpty()) {
                    memoryCache[hostname] = addresses
                }
            }
        }
        if (memoryCache.isNotEmpty()) {
            AppLog.d(TAG, "Loaded ${memoryCache.size} cached hostname(s) from disk")
        }
    }
}
