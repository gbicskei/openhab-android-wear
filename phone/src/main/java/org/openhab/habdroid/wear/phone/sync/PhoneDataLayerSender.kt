package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import android.util.Log
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
        Log.i(TAG, "watchConnectionState flow started")
        while (currentCoroutineContext().isActive) {
            val hasNetwork = hasNetworkConnectivity()
            val node = if (hasNetwork) getConnectedWatch() else null
            val info = node?.let { WatchConnectionInfo(it.displayName, it.isNearby) }
            Log.i(TAG, "Poll: hasNetwork=$hasNetwork, node=${info?.displayName}, nearby=${info?.isNearby}")
            emit(info)
            delay(intervalMs)
        }
        Log.i(TAG, "watchConnectionState flow ended")
    }

    companion object {
        private const val TAG = "PhoneDataLayer"
    }

    /**
     * Send credentials to the connected watch.
     * @return Result.success if sent, Result.failure with exception if no watch or send failed.
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
                password = credentials.password
            )
        )

        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_CONFIG,
            payload.toByteArray(Charsets.UTF_8)
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
    val isNearby: Boolean
)
