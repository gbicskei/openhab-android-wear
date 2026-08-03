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
}
