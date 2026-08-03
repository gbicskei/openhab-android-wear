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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * REST API service for the tile design editor.
 * Reads/writes wear:tile UI components via the openHAB REST API.
 */
@Singleton
class TileApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json".toMediaType()

    // ─── Tile Config CRUD (uses local server for writes) ───

    /**
     * Get all tile page documents from the wear:tile namespace.
     * Can use either remote or local server.
     */
    suspend fun getAllTilePages(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<WearTilePageDto>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/wear:tile"
        val response = executeGet(url, username, password)
        json.decodeFromString<List<WearTilePageDto>>(response)
    }

    /**
     * Get a single tile page by UID.
     */
    suspend fun getTilePage(
        serverUrl: String,
        username: String,
        password: String,
        uid: String
    ): Result<WearTilePageDto> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/$uid"
        val response = executeGet(url, username, password)
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Create a new tile page (POST).
     * Requires local server with basic auth.
     */
    suspend fun createTilePage(
        localConfig: LocalServerConfig,
        page: WearTilePageDto
    ): Result<WearTilePageDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/wear:tile"
        val body = json.encodeToString(WearTilePageDto.serializer(), page)
        val response = executePost(url, body, localConfig.username, localConfig.password)
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Update an existing tile page (PUT).
     * Requires local server with basic auth.
     */
    suspend fun updateTilePage(
        localConfig: LocalServerConfig,
        page: WearTilePageDto
    ): Result<WearTilePageDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/${page.uid}"
        val body = json.encodeToString(WearTilePageDto.serializer(), page)
        val response = executePut(url, body, localConfig.username, localConfig.password)
        json.decodeFromString<WearTilePageDto>(response)
    }

    /**
     * Delete a tile page (DELETE).
     * Requires local server with basic auth.
     */
    suspend fun deleteTilePage(
        localConfig: LocalServerConfig,
        uid: String
    ): Result<Unit> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/$uid"
        executeDelete(url, localConfig.username, localConfig.password)
    }

    // ─── Items (for picker) ───

    /**
     * Get all items from the server (for the item picker).
     * Can use remote or local server.
     */
    suspend fun getAllItems(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<PhoneItem>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/items" +
            "?fields=name,label,type,state,category,tags,groupNames"
        val response = executeGet(url, username, password)
        json.decodeFromString<List<PhoneItem>>(response)
    }

    // ─── Complications CRUD ───

    /**
     * Get the complication-list document from the wear:tile namespace.
     * Returns null if the document doesn't exist yet.
     */
    suspend fun getComplicationList(
        serverUrl: String,
        username: String,
        password: String
    ): Result<ComplicationListDto?> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/${ComplicationListDto.UID}"
        try {
            val response = executeGet(url, username, password)
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
        dto: ComplicationListDto
    ): Result<ComplicationListDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/wear:tile"
        val body = json.encodeToString(ComplicationListDto.serializer(), dto)
        val response = executePost(url, body, localConfig.username, localConfig.password)
        json.decodeFromString<ComplicationListDto>(response)
    }

    /**
     * Update the complication-list document (PUT).
     */
    suspend fun updateComplicationList(
        localConfig: LocalServerConfig,
        dto: ComplicationListDto
    ): Result<ComplicationListDto> = runCatching {
        val url = "${localConfig.serverUrl.trimEnd('/')}/rest/ui/components/wear:tile/${dto.uid}"
        val body = json.encodeToString(ComplicationListDto.serializer(), dto)
        val response = executePut(url, body, localConfig.username, localConfig.password)
        json.decodeFromString<ComplicationListDto>(response)
    }

    /**
     * Get items with wearTile metadata (for import/migration).
     */
    suspend fun getItemsWithMetadata(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<PhoneItemWithMetadata>> = runCatching {
        val url = "${serverUrl.trimEnd('/')}/rest/items" +
            "?metadata=wearTile&fields=name,label,type,state,category,tags,groupNames,metadata"
        val response = executeGet(url, username, password)
        json.decodeFromString<List<PhoneItemWithMetadata>>(response)
    }

    // ─── HTTP Methods ───

    private suspend fun executeGet(url: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(username, password))
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
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .addHeader("Authorization", Credentials.basic(username, password))
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
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .put(body.toRequestBody(jsonMediaType))
            .addHeader("Authorization", Credentials.basic(username, password))
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
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", Credentials.basic(username, password))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.message)
            }
        }
}

class ApiException(val code: Int, override val message: String) : Exception("HTTP $code: $message")
