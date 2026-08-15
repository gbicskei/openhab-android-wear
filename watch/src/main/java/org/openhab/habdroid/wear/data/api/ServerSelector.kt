package org.openhab.habdroid.wear.data.api

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Happy Eyeballs server selector: races the local (LAN/direct) and cloud server URLs
 * in parallel on the first request of the process lifetime, then caches the winner.
 *
 * Design:
 * - First call to [resolveUrl]: fires HEAD requests to both local and cloud concurrently.
 *   First successful response wins and is cached as [activeUrl].
 * - Subsequent calls: return the cached winner immediately.
 * - "Session" = process lifetime (until app is killed).
 * - Local is always preferred when reachable (lower latency, no cloud hop).
 * - If only cloud responds, use cloud for the rest of the process.
 * - If neither responds, falls back to cloud URL (let the actual request fail naturally).
 */
@Singleton
class ServerSelector @Inject constructor(
    private val credentialStore: CredentialStore,
    @Named("probeClient") private val probeClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ServerSelector"

        /** Timeout for each probe request during racing. */
        private const val PROBE_TIMEOUT_MS = 5_000L

        /** Overall timeout for the entire race (both probes). */
        private const val RACE_TIMEOUT_MS = 6_000L
    }

    /** Cached winner URL. Null until first resolution. */
    @Volatile
    private var activeUrl: String? = null

    /** Whether resolution has been attempted this process lifetime. */
    @Volatile
    private var resolved = false

    /**
     * Returns the active server URL, resolving via parallel racing if not yet cached.
     * This is a suspend function — call from a coroutine context.
     */
    suspend fun resolveUrl(): String {
        // Fast path: already resolved
        activeUrl?.let { return it }

        val credentials = credentialStore.credentials.first()
        val cloudUrl = credentials?.serverUrl?.trimEnd('/') ?: ""
        val localUrl = credentialStore.localServerUrl.first().trimEnd('/')

        // If no local URL configured, just use cloud directly
        if (localUrl.isBlank()) {
            activeUrl = cloudUrl
            resolved = true
            AppLog.d(TAG, "No local URL configured, using cloud: $cloudUrl")
            return cloudUrl
        }

        // Race both URLs
        val winner = raceUrls(localUrl, cloudUrl)
        activeUrl = winner
        resolved = true
        AppLog.d(TAG, "Race winner: $winner (local=$localUrl, cloud=$cloudUrl)")
        return winner
    }

    /**
     * Returns the cached active URL, or the cloud URL as default if not yet resolved.
     * Non-suspending — safe to call from interceptors via runBlocking context.
     */
    fun getActiveUrlOrDefault(): String? {
        return activeUrl
    }

    /**
     * Resets the cached selection. Called when credentials change (e.g., new sync from phone).
     * Next request will re-race.
     */
    fun reset() {
        activeUrl = null
        resolved = false
        AppLog.d(TAG, "Selection reset — will re-race on next request")
    }

    /**
     * Races HEAD requests to local and cloud URLs. Returns the first URL that responds
     * with a successful HTTP status. Prefers local if both respond within the timeout.
     */
    private suspend fun raceUrls(
        localUrl: String,
        cloudUrl: String
    ): String {
        return withTimeoutOrNull(RACE_TIMEOUT_MS) {
            coroutineScope {
                val localProbe = async { probe(localUrl) }
                val cloudProbe = async { probe(cloudUrl) }

                // Wait for local first (preferred) with a short head start
                val localResult = withTimeoutOrNull(PROBE_TIMEOUT_MS) { localProbe.await() }
                if (localResult == true) {
                    cloudProbe.cancel()
                    return@coroutineScope localUrl
                }

                // Local failed or timed out — wait for cloud
                val cloudResult = withTimeoutOrNull(PROBE_TIMEOUT_MS) { cloudProbe.await() }
                if (cloudResult == true) {
                    return@coroutineScope cloudUrl
                }

                // Neither responded — default to cloud (let the real request fail naturally)
                cloudUrl
            }
        } ?: cloudUrl // Overall timeout → default to cloud
    }

    /**
     * Sends a HEAD request to the server's REST API root. Returns true if the server
     * responds with any non-error HTTP status (even 401 means the server is reachable).
     */
    private fun probe(baseUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/rest/")
                .head()
                .build()
            val response = probeClient.newCall(request).execute()
            val code = response.code
            response.close()
            // Any response (including 401) means the server is reachable
            AppLog.d(TAG, "Probe $baseUrl → $code")
            code in 100..599
        } catch (e: Exception) {
            AppLog.d(TAG, "Probe $baseUrl failed: ${e.message}")
            false
        }
    }
}
