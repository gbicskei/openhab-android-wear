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

    /** Whether the active URL is the local server (vs cloud). */
    @Volatile
    private var activeIsLocal: Boolean = false

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
        val localCreds = credentialStore.localCredentials.first()

        // Resolve auth headers for probes
        val cloudAuth = if (credentials != null && credentials.username.isNotBlank() && credentials.password.isNotBlank()) {
            okhttp3.Credentials.basic(credentials.username, credentials.password)
        } else null

        val localAuth = when {
            localCreds.hasApiToken -> "Bearer ${localCreds.apiToken}"
            localCreds.hasBasicAuth -> okhttp3.Credentials.basic(localCreds.username, localCreds.password)
            else -> null
        }

        // If no local URL configured, just use cloud directly
        if (localUrl.isBlank()) {
            activeUrl = cloudUrl
            activeIsLocal = false
            resolved = true
            AppLog.d(TAG, "No local URL configured, using cloud: $cloudUrl")
            return cloudUrl
        }

        // If local and cloud are the same URL, skip the race (no point probing twice)
        if (localUrl == cloudUrl) {
            activeUrl = cloudUrl
            activeIsLocal = false
            resolved = true
            AppLog.d(TAG, "Local == cloud, skipping race: $cloudUrl")
            return cloudUrl
        }

        // Race both URLs
        val winner = raceUrls(localUrl, localAuth, cloudUrl, cloudAuth)
        activeUrl = winner
        activeIsLocal = winner == localUrl
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
        activeIsLocal = false
        resolved = false
        AppLog.d(TAG, "Selection reset — will re-race on next request")
    }

    /**
     * Whether the currently active URL is the local server (vs cloud).
     * Only meaningful after [resolveUrl] has been called.
     */
    fun isLocalActive(): Boolean = activeIsLocal

    /**
     * Resolves the appropriate Authorization header for the currently active server.
     * - Local: API token (Bearer) > Basic Auth with local creds > null (no auth)
     * - Cloud: Basic Auth with cloud credentials
     *
     * Must be called after [resolveUrl] or [getActiveUrlOrDefault] has determined
     * which server is active. If not yet resolved, defaults to cloud auth.
     */
    suspend fun resolveAuthHeader(): String? {
        return if (activeIsLocal) {
            val localCreds = credentialStore.localCredentials.first()
            when {
                localCreds.hasApiToken -> {
                    AppLog.d(TAG, "Auth: local Bearer token")
                    "Bearer ${localCreds.apiToken}"
                }
                localCreds.hasBasicAuth -> {
                    AppLog.d(TAG, "Auth: local Basic (${localCreds.username})")
                    okhttp3.Credentials.basic(localCreds.username, localCreds.password)
                }
                else -> {
                    AppLog.d(TAG, "Auth: local no-auth")
                    null
                }
            }
        } else {
            val credentials = credentialStore.credentials.first()
            if (credentials != null && credentials.username.isNotBlank() && credentials.password.isNotBlank()) {
                AppLog.d(TAG, "Auth: cloud Basic (${credentials.username})")
                okhttp3.Credentials.basic(credentials.username, credentials.password)
            } else {
                AppLog.d(TAG, "Auth: cloud no credentials")
                null
            }
        }
    }

    /**
     * Races HEAD requests to local and cloud URLs. Returns the first URL that responds
     * with a successful HTTP status. Prefers local if both respond within the timeout.
     */
    private suspend fun raceUrls(
        localUrl: String,
        localAuth: String?,
        cloudUrl: String,
        cloudAuth: String?
    ): String {
        return withTimeoutOrNull(RACE_TIMEOUT_MS) {
            coroutineScope {
                val localProbe = async { probe(localUrl, localAuth) }
                val cloudProbe = async { probe(cloudUrl, cloudAuth) }

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
     * Sends a HEAD request to the server's REST API root with optional auth.
     * Returns true if the server responds with any HTTP status (even 401 means reachable,
     * though with proper auth it should return 200).
     */
    private fun probe(baseUrl: String, authHeader: String? = null): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/rest/")
                .head()
                .apply { authHeader?.let { header("Authorization", it) } }
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
