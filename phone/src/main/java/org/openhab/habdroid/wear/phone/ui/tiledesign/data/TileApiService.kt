package org.openhab.habdroid.wear.phone.ui.tiledesign.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.phone.data.LocalServerConfig
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationListDto
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PhoneItem
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PhoneItemWithMetadata
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.WearTilePageDto
import org.openhab.habdroid.wear.shared.model.ServerCredentials
import org.openhab.habdroid.wear.shared.sync.SyncConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST API service for the tile design editor.
 * Reads/writes wear:tile UI components via the openHAB REST API.
 * Namespace is user-scoped: "wear:tile" (default) or "wear:tile:{userKey}" (per-user).
 */
@Singleton
class TileApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json".toMediaType()

    // ─── Tile Config CRUD (uses local server for writes) ───

    /**
     * Get all tile page documents from the user's namespace.
     * Accepts LocalServerConfig to properly resolve auth (Bearer token or Basic).
     */
    suspend fun getAllTilePages(
        localConfig: LocalServerConfig,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<List<WearTilePageDto>> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace"
        val response = executeGet(url, resolveAuth(localConfig))
        json.decodeFromString<List<WearTilePageDto>>(response)
    }

    /**
     * Get all tile page documents — fallback for remote (cloud) server with Basic Auth.
     */
    suspend fun getAllTilePages(
        serverUrl: String,
        username: String,
        password: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<List<WearTilePageDto>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/$namespace"
        val response = executeGet(url, Credentials.basic(username, password))
        json.decodeFromString<List<WearTilePageDto>>(response)
    }

    /**
     * Get a single tile page by UID.
     */
    suspend fun getTilePage(
        localConfig: LocalServerConfig,
        uid: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<WearTilePageDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace/$uid"
        val response = executeGet(url, resolveAuth(localConfig))
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Get a single tile page by UID — fallback for remote server with Basic Auth.
     */
    suspend fun getTilePage(
        serverUrl: String,
        username: String,
        password: String,
        uid: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<WearTilePageDto> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/$namespace/$uid"
        val response = executeGet(url, Credentials.basic(username, password))
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Create a new tile page (POST).
     * Requires local server with basic auth.
     */
    suspend fun createTilePage(
        localConfig: LocalServerConfig,
        page: WearTilePageDto,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<WearTilePageDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace"
        val body = json.encodeToString(WearTilePageDto.serializer(), page)
        val response = executePost(url, body, resolveAuth(localConfig))
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Update an existing tile page (PUT).
     * Requires local server with basic auth.
     */
    suspend fun updateTilePage(
        localConfig: LocalServerConfig,
        page: WearTilePageDto,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<WearTilePageDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace/${page.uid}"
        val body = json.encodeToString(WearTilePageDto.serializer(), page)
        val response = executePut(url, body, resolveAuth(localConfig))
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Delete a tile page (DELETE).
     * Requires local server with basic auth.
     */
    suspend fun deleteTilePage(
        localConfig: LocalServerConfig,
        uid: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<Unit> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace/$uid"
        executeDelete(url, resolveAuth(localConfig))
    }

    // ─── Items (for picker) ───

    /**
     * Get all items from the server (for the item picker).
     * Accepts LocalServerConfig to properly resolve auth.
     */
    suspend fun getAllItems(
        localConfig: LocalServerConfig
    ): Result<List<PhoneItem>> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/items" +
            "?fields=name,label,type,state,category,tags,groupNames"
        val response = executeGet(url, resolveAuth(localConfig))
        json.decodeFromString<List<PhoneItem>>(response)
    }

    /**
     * Get all items — fallback for remote (cloud) server with Basic Auth.
     */
    suspend fun getAllItems(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<PhoneItem>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/items" +
            "?fields=name,label,type,state,category,tags,groupNames"
        val response = executeGet(url, Credentials.basic(username, password))
        json.decodeFromString<List<PhoneItem>>(response)
    }

    // ─── Complications CRUD ───

    /**
     * Get the complication-list document from the user's namespace.
     * Returns null if the document doesn't exist yet.
     * Accepts LocalServerConfig to properly resolve auth.
     */
    suspend fun getComplicationList(
        localConfig: LocalServerConfig,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<ComplicationListDto?> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace/${ComplicationListDto.UID}"
        try {
            val response = executeGet(url, resolveAuth(localConfig))
            json.decodeFromString<ComplicationListDto>(response)
        } catch (e: ApiException) {
            if (e.code == 404) null else throw e
        }
    }

    /**
     * Get complication-list — fallback for remote (cloud) server with Basic Auth.
     */
    suspend fun getComplicationList(
        serverUrl: String,
        username: String,
        password: String,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<ComplicationListDto?> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/$namespace/${ComplicationListDto.UID}"
        try {
            val response = executeGet(url, Credentials.basic(username, password))
            json.decodeFromString<ComplicationListDto>(response)
        } catch (e: ApiException) {
            if (e.code == 404) null else throw e
        }
    }

    /**
     * Create the complication-list document (POST). Used on first save.
     */
    suspend fun createComplicationList(
        localConfig: LocalServerConfig,
        dto: ComplicationListDto,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<ComplicationListDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace"
        val body = json.encodeToString(ComplicationListDto.serializer(), dto)
        val response = executePost(url, body, resolveAuth(localConfig))
        json.decodeFromString<ComplicationListDto>(response)
    }

    /**
     * Update the complication-list document (PUT).
     */
    suspend fun updateComplicationList(
        localConfig: LocalServerConfig,
        dto: ComplicationListDto,
        namespace: String = SyncConstants.DEFAULT_TILE_NAMESPACE
    ): Result<ComplicationListDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/$namespace/${dto.uid}"
        val body = json.encodeToString(ComplicationListDto.serializer(), dto)
        val response = executePut(url, body, resolveAuth(localConfig))
        json.decodeFromString<ComplicationListDto>(response)
    }

    /**
     * Get items with wearTile metadata (for import/migration).
     * Accepts LocalServerConfig to properly resolve auth.
     */
    suspend fun getItemsWithMetadata(
        localConfig: LocalServerConfig
    ): Result<List<PhoneItemWithMetadata>> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/items" +
            "?metadata=wearTile&fields=name,label,type,state,category,tags,groupNames,metadata"
        val response = executeGet(url, resolveAuth(localConfig))
        json.decodeFromString<List<PhoneItemWithMetadata>>(response)
    }

    /**
     * Get items with wearTile metadata — fallback for remote server with Basic Auth.
     */
    suspend fun getItemsWithMetadata(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<PhoneItemWithMetadata>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/items" +
            "?metadata=wearTile&fields=name,label,type,state,category,tags,groupNames,metadata"
        val response = executeGet(url, Credentials.basic(username, password))
        json.decodeFromString<List<PhoneItemWithMetadata>>(response)
    }

    // ─── HTTP Methods ───

    /**
     * Resolves the Authorization header value from a LocalServerConfig.
     * Prefers API token (Bearer) over Basic Auth with username/password.
     */
    private fun resolveAuth(config: LocalServerConfig): String? {
        return config.resolveAuthHeader()
    }

    private suspend fun executeGet(url: String, authHeader: String?): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { authHeader?.let { addHeader("Authorization", it) } }
                .addHeader("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.message)
            }
            response.body?.string() ?: throw ApiException(0, "Empty response body")
        }

    private suspend fun executePost(
        url: String,
        body: String,
        username: String,
        password: String
    ): String = executePost(url, body, Credentials.basic(username, password))

    private suspend fun executePost(
        url: String,
        body: String,
        authHeader: String?
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .apply { authHeader?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException(response.code, response.message)
        }
        response.body?.string() ?: ""
    }

    private suspend fun executePut(
        url: String,
        body: String,
        username: String,
        password: String
    ): String = executePut(url, body, Credentials.basic(username, password))

    private suspend fun executePut(
        url: String,
        body: String,
        authHeader: String?
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .put(body.toRequestBody(jsonMediaType))
            .apply { authHeader?.let { addHeader("Authorization", it) } }
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException(response.code, response.message)
        }
        response.body?.string() ?: ""
    }

    private suspend fun executeDelete(url: String, username: String, password: String): Unit =
        executeDelete(url, Credentials.basic(username, password))

    private suspend fun executeDelete(url: String, authHeader: String?): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .delete()
                .apply { authHeader?.let { addHeader("Authorization", it) } }
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.message)
            }
        }
}

class ApiException(val code: Int, override val message: String) : Exception("HTTP $code: $message")
