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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.ConnectionPayload
import org.openhab.habdroid.wear.shared.sync.SyncConfigPayload
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
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
     * Shared flow of watch connection state, polled every 5 seconds.
     * Single polling loop regardless of how many collectors subscribe.
     * Uses [SharingStarted.WhileSubscribed] so polling stops when the app goes to background.
     */
    val watchConnectionState: SharedFlow<WatchConnectionInfo?> = flow {
        AppLog.d(TAG, "watchConnectionState flow started")
        var lastInfo: WatchConnectionInfo? = null
        while (currentCoroutineContext().isActive) {
            val node = getConnectedWatch()
            val info = node?.let { WatchConnectionInfo(it.displayName, it.isNearby, watchAppInstalled = true) }
            // Only log when connection state changes (avoid flooding debug log)
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
     * Send the complete, unified watch settings payload (non-credential preferences).
     * Called on every voice, notification, theme, or debug change.
     * @deprecated Use [WatchSettingsDataItemClient.writeSettings] instead for DataItem-based sync.
     */
    @Deprecated("Use WatchSettingsDataItemClient.writeSettings() for DataItem-based sync")
    suspend fun sendSettings(payload: WatchSettingsPayload): Result<Unit> = runCatching {
        if (!hasNetworkConnectivity()) {
            throw NoNetworkException()
        }

        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull()
            ?: throw NoWatchConnectedException()

        val jsonPayload = json.encodeToString(WatchSettingsPayload.serializer(), payload)

        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_SETTINGS,
            jsonPayload.toByteArray(Charsets.UTF_8)
        ).await()
    }

    /**
     * Send connection credentials and server configuration to the watch.
     * Called when the user saves connection settings in the Setup screen.
     * DNS pre-resolution of the cloud server hostname is performed automatically.
     */
    suspend fun sendConnection(payload: ConnectionPayload): Result<Unit> = runCatching {
        if (!hasNetworkConnectivity()) {
            throw NoNetworkException()
        }

        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull()
            ?: throw NoWatchConnectedException()

        // Pre-resolve server hostname so the watch has cached IPs from the start
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
     * Send credentials to the connected watch.
     * @deprecated Use [sendSettings] with [WatchSettingsPayload] instead.
     */
    @Deprecated("Use sendSettings() with WatchSettingsPayload for atomic sync", replaceWith = ReplaceWith("sendSettings(payload)"))
    suspend fun sendCredentials(
        credentials: ServerCredentials,
        debugMode: Boolean = false,
        localServerUrl: String = "",
        localUsername: String = "",
        localPassword: String = "",
        localApiToken: String = "",
        deviceName: String = "",
        bindingInstalled: Boolean = false,
        triggerReload: Boolean = false
    ): Result<Unit> = runCatching {
        if (!hasNetworkConnectivity()) {
            throw NoNetworkException()
        }

        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull()
            ?: throw NoWatchConnectedException()

        // Pre-resolve server hostname so the watch has cached IPs from the start
        val resolvedIps = resolveServerIps(credentials.serverUrl)

        val payload = json.encodeToString(
            SyncConfigPayload.serializer(),
            SyncConfigPayload(
                serverUrl = credentials.serverUrl,
                username = credentials.username,
                password = credentials.password,
                userKey = credentials.userKey,
                deviceName = deviceName,
                googleTtsApiKey = credentials.googleTtsApiKey,
                debugMode = debugMode,
                bindingInstalled = bindingInstalled,
                resolvedIps = resolvedIps,
                localServerUrl = localServerUrl,
                localUsername = localUsername,
                localPassword = localPassword,
                localApiToken = localApiToken,
                triggerReload = triggerReload
            )
        )

        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_CONFIG,
            payload.toByteArray(Charsets.UTF_8)
        ).await()
    }

    /**
     * Resolve the hostname from a server URL to IP addresses.
     * Returns empty list if resolution fails (non-fatal — watch will resolve on its own).
     */
    private fun resolveServerIps(serverUrl: String): List<String> {
        return try {
            val host = java.net.URI(serverUrl).host ?: return emptyList()
            val addresses = java.net.InetAddress.getAllByName(host)
            addresses.mapNotNull { it.hostAddress }.also { ips ->
                if (ips.isNotEmpty()) {
                    AppLog.d(TAG, "Resolved $host → ${ips.joinToString()}")
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "DNS pre-resolve failed: ${e.message}")
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
     * Request the watch app's versionName via the Data Layer.
     * The watch will reply on PATH_VERSION_RESPONSE (handled by PhoneWearListenerService).
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

    /**
     * Send voice settings to the watch.
     * @deprecated Use [sendSettings] with [WatchSettingsPayload] instead.
     */
    @Deprecated("Use sendSettings() with WatchSettingsPayload for atomic sync", replaceWith = ReplaceWith("sendSettings(payload)"))
    suspend fun sendVoiceSettings(payloadJson: String): Result<Unit> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull() ?: throw NoWatchConnectedException()
        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_VOICE_SETTINGS,
            payloadJson.toByteArray(Charsets.UTF_8)
        ).await()
    }

    /**
     * Send notification settings to the watch.
     * @deprecated Use [sendSettings] with [WatchSettingsPayload] instead.
     */
    @Deprecated("Use sendSettings() with WatchSettingsPayload for atomic sync", replaceWith = ReplaceWith("sendSettings(payload)"))
    suspend fun sendNotificationSettings(payloadJson: String): Result<Unit> = runCatching {
        val nodes = nodeClient.connectedNodes.await()
        val watchNode = nodes.firstOrNull() ?: throw NoWatchConnectedException()
        messageClient.sendMessage(
            watchNode.id,
            SyncConstants.PATH_NOTIFICATION_SETTINGS,
            payloadJson.toByteArray(Charsets.UTF_8)
        ).await()
    }

    /**
     * Send theme selection to the watch.
     * @deprecated Use [sendSettings] with [WatchSettingsPayload] instead.
     */
    @Deprecated("Use sendSettings() with WatchSettingsPayload for atomic sync", replaceWith = ReplaceWith("sendSettings(payload)"))
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
