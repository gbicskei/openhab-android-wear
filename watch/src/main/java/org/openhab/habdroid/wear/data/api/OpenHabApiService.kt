package org.openhab.habdroid.wear.data.api

import okhttp3.RequestBody
import org.openhab.habdroid.wear.data.model.Item
import org.openhab.habdroid.wear.data.model.WearTileComponent
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the openHAB REST API.
 */
interface OpenHabApiService {

    /**
     * Get all items, optionally filtered by metadata namespace.
     */
    @GET("rest/items")
    suspend fun getItems(
        @Query("metadata") metadata: String? = null,
        @Query("fields") fields: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Header("Accept-Language") language: String? = null
    ): List<Item>

    /**
     * Get a single item by name.
     */
    @GET("rest/items/{itemName}")
    suspend fun getItem(
        @Path("itemName") itemName: String,
        @Query("metadata") metadata: String? = null
    ): Item

    /**
     * Send a command to an item (e.g., "ON", "OFF", "50").
     */
    @POST("rest/items/{itemName}")
    suspend fun sendCommand(
        @Path("itemName") itemName: String,
        @Body command: RequestBody
    )

    /**
     * Send a voice command to the default human language interpreter.
     * The body is plain text with the spoken command.
     */
    @POST("rest/voice/interpreters")
    suspend fun interpretVoiceCommand(
        @Body command: RequestBody,
        @Header("Accept-Language") language: String? = null
    )

    /**
     * Get all wear:tile UI components (tile page configs + complications).
     */
    @GET("rest/ui/components/wear:tile")
    suspend fun getTileComponents(): List<WearTileComponent>

    /**
     * Get the complication-list document as raw JSON for flexible parsing.
     * Returns the document with nested per-type config objects intact.
     */
    @GET("rest/ui/components/wear:tile/complications")
    suspend fun getComplicationListRaw(): kotlinx.serialization.json.JsonObject

    /**
     * Get the server's icon for an item category.
     * Returns the icon URL — actual fetching is done by Coil.
     */
    companion object {
        fun iconUrl(baseUrl: String, iconName: String, state: String? = null): String {
            val stateParam = state?.let { "&state=$it" } ?: ""
            return "${baseUrl.trimEnd('/')}/icon/$iconName?format=svg$stateParam"
        }
    }
}
