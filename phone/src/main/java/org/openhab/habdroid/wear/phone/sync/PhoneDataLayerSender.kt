package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import org.openhab.habdroid.wear.phone.util.AppLog
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConfigPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends credentials to the paired Wear OS watch via the Data Layer MessageClient.
 * Also provides a Flow to observe watch connectivity changes.
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
     * Checks if the openHAB watch app is installed on any connected watch
     * by querying for the openhab_watch_app capability.
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
                // If capability check fails, assume installed to avoid false negatives
                true
            }
        } ?: true // Timeout → assume installed (don't block UX on timeout)
    }

    /**
     * Returns the first connected watch node, or null if no watch is paired/reachable.
     * Times out after 3 seconds to avoid blocking the polling flow.
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

    /**
     * Checks if the device has any network connectivity (needed for Data Layer).
     */
    private fun hasNetworkConnectivity(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Emits the currently connected watch node (or null) every [intervalMs].
     * Polls NodeClient since it doesn't have a listener API.
     * The flow is lifecycle-aware when collected in a viewModelScope.
     */
    fun watchConnectionState(intervalMs: Long = 5_000L): Flow<WatchConnectionInfo?> = flow {
        AppLog.i(TAG, "watchConnectionState flow started")
        while (currentCoroutineContext().isActive) {
            // Data Layer works over Bluetooth directly — no internet needed.
            val node = getConnectedWatch()
            // connectedNodes only returns nodes with matching applicationId,
            // so node presence already implies the watch app is installed.
            val info = node?.let { WatchConnectionInfo(it.displayName, it.isNearby, watchAppInstalled = true) }
            AppLog.i(TAG, "Poll: node=${info?.displayName}, nearby=${info?.isNearby}, appInstalled=${info?.watchAppInstalled}")
            emit(info)
            delay(intervalMs)
        }
        AppLog.i(TAG, "watchConnectionState flow ended")
    }

    /**
     * Send credentials to the connected watch.
     */
    suspend fun sendCredentials(credentials: ServerCredentials): Result<Unit> = runCatching {
        if (!hasNetworkConnectivity()) {
            throw NoNetworkException()
        }

        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull()
            ?: throw NoWatchConnectedException()

        val payload = json.encodeToString(
            SyncConfigPayload.serializer(),
            SyncConfigPayload(
                serverUrl = credentials.serverUrl,
                username = credentials.username,
                password = credentials.password,
                userKey = credentials.userKey
            )
        )

        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_CONFIG,
            payload.toByteArray(Charsets.UTF_8)
        ).await()
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
     * Send theme name to the watch.
     */
    suspend fun sendTheme(themeName: String): Result<Unit> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull() ?: throw NoWatchConnectedException()
        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_THEME,
            themeName.toByteArray(Charsets.UTF_8)
        ).await()
    }
}

class NoWatchConnectedException : Exception("No connected watch found")
class NoNetworkException : Exception("No network connection")

/**
 * Simplified watch connection info emitted by the polling flow.
 */
data class WatchConnectionInfo(
    val displayName: String,
    val isNearby: Boolean,
    val watchAppInstalled: Boolean
)
