package org.openhab.habdroid.wear.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.phone.util.AppLog
import org.openhab.habdroid.wear.shared.sync.WatchSettingsPayload
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles server-side backup of watch settings via openHAB REST API.
 *
 * Creates a String item named `{deviceName}_Config` and stores settings
 * as metadata in the `wearConfig` namespace.
 *
 * Operations:
 * - createBackupItem: PUT /rest/items/{deviceName}_Config (creates item if not exists)
 * - writeBackup: PUT /rest/items/{deviceName}_Config/metadata/wearConfig
 * - readBackup: GET /rest/items/{deviceName}_Config?metadata=wearConfig
 */
@Singleton
class ServerBackupRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        private const val TAG = "ServerBackup"
    }

    /**
     * Creates the backup item on the server if it doesn't already exist.
     * Uses PUT which creates or updates.
     */
    suspend fun ensureBackupItemExists(
        localConfig: LocalServerConfig,
        deviceName: String
    ): Result<Unit> = runCatching {
        val itemName = "${deviceName}_Config"
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/items/$itemName"

        val itemBody = json.encodeToString(
            CreateItemPayload.serializer(),
            CreateItemPayload(
                type = "String",
                name = itemName,
                label = "Wear OS Config ($deviceName)",
                tags = listOf("WearOSConfig")
            )
        )

        executePut(url, itemBody, localConfig.resolveAuthHeader())
        AppLog.d(TAG, "Backup item '$itemName' ensured on server")
    }

    /**
     * Writes the settings payload to server as item metadata.
     */
    suspend fun writeBackup(
        localConfig: LocalServerConfig,
        deviceName: String,
        settings: WatchSettingsPayload
    ): Result<Unit> = runCatching {
        val itemName = "${deviceName}_Config"
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/items/$itemName/metadata/${WatchSettingsPayload.METADATA_NAMESPACE}"

        val metadataBody = json.encodeToString(
            MetadataPayload.serializer(),
            MetadataPayload(
                value = WatchSettingsPayload.SCHEMA_VERSION,
                config = settings.toMetadataConfig()
            )
        )

        executePut(url, metadataBody, localConfig.resolveAuthHeader())
        AppLog.d(TAG, "Backup written for '$itemName'")
    }

    /**
     * Reads the settings payload from server item metadata.
     * Returns null if the item or metadata doesn't exist.
     */
    suspend fun readBackup(
        localConfig: LocalServerConfig,
        deviceName: String
    ): Result<WatchSettingsPayload?> = runCatching {
        val itemName = "${deviceName}_Config"
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/items/$itemName?metadata=${WatchSettingsPayload.METADATA_NAMESPACE}"

        val responseBody = executeGet(url, localConfig.resolveAuthHeader())

        // Parse the item response and extract the wearConfig metadata
        val itemJson = json.parseToJsonElement(responseBody).jsonObject
        val metadata = itemJson["metadata"]?.jsonObject
            ?: return@runCatching null

        val wearConfig = metadata[WatchSettingsPayload.METADATA_NAMESPACE]?.jsonObject
            ?: return@runCatching null

        val configObj = wearConfig["config"]?.jsonObject
            ?: return@runCatching null

        // Convert JsonObject to Map<String, String>
        val configMap = configObj.entries.associate { (key, value) ->
            key to value.jsonPrimitive.content
        }

        WatchSettingsPayload.fromMetadataConfig(configMap)
    }

    // ─── HTTP helpers ───

    private suspend fun executeGet(url: String, authHeader: String?): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { authHeader?.let { addHeader("Authorization", it) } }
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw BackupApiException(response.code, "GET failed: ${response.message}")
            }
            response.body?.string() ?: throw BackupApiException(0, "Empty response body")
        }

    private suspend fun executePut(url: String, body: String, authHeader: String?): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody(jsonMediaType))
                .apply { authHeader?.let { addHeader("Authorization", it) } }
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw BackupApiException(response.code, "PUT failed: ${response.message}")
            }
            response.body?.string() ?: ""
        }
}

@Serializable
private data class CreateItemPayload(
    val type: String,
    val name: String,
    val label: String,
    val tags: List<String> = emptyList()
)

@Serializable
private data class MetadataPayload(
    val value: String,
    val config: Map<String, String>
)

class BackupApiException(val httpCode: Int, message: String) : Exception(message)
