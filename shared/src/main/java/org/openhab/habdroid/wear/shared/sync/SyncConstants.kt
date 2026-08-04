package org.openhab.habdroid.wear.shared.sync

/**
 * Constants for Data Layer communication between phone and watch.
 */
object SyncConstants {
    /** Message path for credential sync (phone → watch) */
    const val PATH_CONFIG = "/openhab/config"

    /** Message path for requesting the watch to reload items */
    const val PATH_RELOAD = "/openhab/reload"

    /** Message path for setting the watch theme (phone → watch) */
    const val PATH_THEME = "/openhab/theme"

    /** Default UI components namespace for wear tile config */
    const val DEFAULT_TILE_NAMESPACE = "wear:tile"

    /** Builds the tile namespace for a given user key */
    fun tileNamespace(userKey: String): String =
        if (userKey.isBlank()) DEFAULT_TILE_NAMESPACE else "$DEFAULT_TILE_NAMESPACE:$userKey"
}
