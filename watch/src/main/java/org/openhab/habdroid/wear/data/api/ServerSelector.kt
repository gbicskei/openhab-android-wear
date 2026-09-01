package org.openhab.habdroid.wear.data.api

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
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

        /** Brief head-start for local server before accepting cloud results. */
        private const val LOCAL_PREFERENCE_MS = 500L

        /** Per-probe timeout. Caps a stalled socket connect so it can't hold up the race
         *  (the cloud relay has been seen taking 30s+ on a bad connect). */
        private const val PROBE_TIMEOUT_MS = 5_000L
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
     * Races requests to local and cloud URLs. Both probes run in parallel.
     * Local gets a brief head-start ([LOCAL_PREFERENCE_MS]) — if local responds
     * within that window it wins immediately. After the window, the first response
     * from either server wins. This avoids the old sequential-await pattern that
     * could waste up to 5s waiting for an unreachable local before trying cloud.
     */
    private suspend fun raceUrls(
        localUrl: String,
        localAuth: String?,
        cloudUrl: String,
        cloudAuth: String?
    ): String {
        // Use supervisorScope so a hung/failing probe does not cancel the sibling or
        // block the scope from returning once we've picked a winner. Each probe is
        // wrapped in its own timeout so a stalled socket connect (seen with the cloud
        // relay taking 30s+) can't hold up the decision.
        return supervisorScope {
            val localProbe = async { withTimeoutOrNull(PROBE_TIMEOUT_MS) { probe(localUrl, localAuth) } == true }
            val cloudProbe = async { withTimeoutOrNull(PROBE_TIMEOUT_MS) { probe(cloudUrl, cloudAuth) } == true }

            // Ensure both probes are cancelled when we leave this scope, so a slow
            // loser cannot keep running in the background.
            try {
                // Give local a brief head-start: if it responds successfully within the
                // preference window, use it immediately without waiting for cloud.
                val localQuick = withTimeoutOrNull(LOCAL_PREFERENCE_MS) { localProbe.await() }
                if (localQuick == true) {
                    AppLog.d(TAG, "Local won within ${LOCAL_PREFERENCE_MS}ms preference window")
                    return@supervisorScope localUrl
                }

                AppLog.d(TAG, "Local not ready within ${LOCAL_PREFERENCE_MS}ms — racing both to completion")

                // Race: first probe to finish *successfully* wins. If a probe finishes
                // unsuccessfully, keep waiting for the other rather than falling through.
                var localDone = localQuick != null  // completed (with false) during the preference wait
                var cloudDone = false
                while (!localDone || !cloudDone) {
                    val winner = select<String?> {
                        if (!localDone) localProbe.onAwait { ok -> if (ok) localUrl else { localDone = true; null } }
                        if (!cloudDone) cloudProbe.onAwait { ok -> if (ok) cloudUrl else { cloudDone = true; null } }
                    }
                    if (winner != null) {
                        return@supervisorScope winner
                    }
                }

                // Neither responded successfully — default to cloud
                AppLog.d(TAG, "Neither server reachable — defaulting to cloud")
                cloudUrl
            } finally {
                localProbe.cancel()
                cloudProbe.cancel()
            }
        }
    }

    /**
     * Sends a HEAD request to the server's REST API root with optional auth.
     * Returns true if the server responds with a usable status. A 401 still counts as
     * reachable (server is up, just needs auth), but 5xx does NOT — a cloud relay
     * returning 500/502/503 is erroring and would fail the actual SSE/REST calls,
     * so it must not win the race over a healthy local server.
     */
    private fun probe(baseUrl: String, authHeader: String? = null): Boolean {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url("$baseUrl/rest/")
                .head()
                .apply { authHeader?.let { header("Authorization", it) } }
                .build()
            val response = probeClient.newCall(request).execute()
            val code = response.code
            response.close()
            // Reachable if we got a non-server-error response (2xx/3xx/4xx). 5xx = unusable.
            val usable = code in 100..499
            AppLog.d(TAG, "Probe $baseUrl → $code (${System.currentTimeMillis() - start}ms)${if (!usable) " [server error, treating as unreachable]" else ""}")
            usable
        } catch (e: Exception) {
            AppLog.d(TAG, "Probe $baseUrl failed after ${System.currentTimeMillis() - start}ms: ${e.message}")
            false
        }
    }
}
