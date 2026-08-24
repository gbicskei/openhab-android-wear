package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import org.openhab.habdroid.wear.phone.util.AppLog
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.shared.sync.ConnectionPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends connection credentials to the paired Wear OS watch via MessageClient.
 * Also provides a Flow to observe watch connectivity changes.
 *
 * Settings sync uses DataItem (see [WatchSettingsDataItemClient]) — not this class.
 */
@Singleton
class PhoneDataLayerSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }

    companion object {
        private const val TAG = "PhoneDataLayer"
        private const val WATCH_APP_CAPABILITY = "openhab_watch_app"
    }

    /**
     * Checks if the openHAB watch app is installed on any connected watch.
     */
    suspend fun isWatchAppInstalled(): Boolean {
        return withTimeoutOrNull(3_000L) {
            try {
                val capabilityInfo = capabilityClient.getCapability(
                    WATCH_APP_CAPABILITY,
                    CapabilityClient.FILTER_ALL
                ).await()
                capabilityInfo.nodes.isNotEmpty()
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to check watch app capability", e)
                true
            }
        } ?: true
    }

    /**
     * Returns the first connected watch node, or null if no watch is paired/reachable.
     */
    suspend fun getConnectedWatch(): Node? {
        return withTimeoutOrNull(3_000L) {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun hasNetworkConnectivity(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Shared flow of watch connection state, polled every 5 seconds.
     */
    val watchConnectionState: SharedFlow<WatchConnectionInfo?> = flow {
        AppLog.d(TAG, "watchConnectionState flow started")
        var lastInfo: WatchConnectionInfo? = null
        while (currentCoroutineContext().isActive) {
            val node = getConnectedWatch()
            val info = node?.let { WatchConnectionInfo(it.displayName, it.isNearby, watchAppInstalled = true) }
            if (info?.displayName != lastInfo?.displayName || info?.isNearby != lastInfo?.isNearby) {
                AppLog.i(TAG, "Watch connection: node=${info?.displayName}, nearby=${info?.isNearby}, appInstalled=${info?.watchAppInstalled}")
            }
            lastInfo = info
            emit(info)
            delay(5_000L)
        }
        AppLog.d(TAG, "watchConnectionState flow ended")
    }.shareIn(
        scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        replay = 1
    )

    /**
     * Send connection credentials to the watch via MessageClient.
     * DNS pre-resolution of the cloud server hostname is performed automatically.
     */
    suspend fun sendConnection(payload: ConnectionPayload): Result<Unit> = runCatching {
        if (!hasNetworkConnectivity()) {
            throw NoNetworkException()
        }

        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull()
            ?: throw NoWatchConnectedException()

        val resolvedIps = if (payload.serverUrl.isNotBlank()) {
            resolveServerIps(payload.serverUrl)
        } else {
            emptyList()
        }

        val effectivePayload = payload.copy(resolvedIps = resolvedIps)
        val jsonPayload = json.encodeToString(ConnectionPayload.serializer(), effectivePayload)

        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_CONNECTION,
            jsonPayload.toByteArray(Charsets.UTF_8)
        ).await()
    }

    /**
     * Resolve the hostname from a server URL to IP addresses.
     * Skips resolution for IP-literal hosts (no DNS needed).
     */
    private fun resolveServerIps(serverUrl: String): List<String> {
        return try {
            val host = java.net.URI(serverUrl).host ?: return emptyList()
            // Skip DNS for IP literals — they don't need resolution
            if (host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) {
                return listOf(host)
            }
            val addresses = java.net.InetAddress.getAllByName(host)
            addresses.mapNotNull { it.hostAddress }.also { ips ->
                if (ips.isNotEmpty()) {
                    AppLog.d(TAG, "Resolved $host → ${ips.joinToString()}")
                }
            }
        } catch (e: Exception) {
            val host = try { java.net.URI(serverUrl).host } catch (_: Exception) { serverUrl }
            AppLog.w(TAG, "DNS pre-resolve failed for $host: ${e.message}")
            emptyList()
        }
    }

    /**
     * Send reload signal to the watch (clears cache, refreshes tile).
     */
    suspend fun sendReload(): Result<Unit> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull() ?: throw NoWatchConnectedException()
        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_RELOAD,
            ByteArray(0)
        ).await()
    }

    /**
     * Request the watch app's versionName via MessageClient.
     */
    suspend fun requestWatchVersion(): Result<Unit> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull() ?: throw NoWatchConnectedException()
        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_VERSION_REQUEST,
            ByteArray(0)
        ).await()
    }
}

class NoWatchConnectedException : Exception("No connected watch found")
class NoNetworkException : Exception("No network connection")

data class WatchConnectionInfo(
    val displayName: String,
    val isNearby: Boolean,
    val watchAppInstalled: Boolean
)
