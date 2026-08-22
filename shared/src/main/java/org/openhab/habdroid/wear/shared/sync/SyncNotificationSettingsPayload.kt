package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * JSON payload for notification settings sync from phone to watch.
 *
 * @deprecated Replaced by [WatchSettingsPayload] (PATH_SETTINGS) which includes notification fields.
 * Kept for backwards compatibility with watch app versions < 1.10.0.
 */
@Deprecated("Use WatchSettingsPayload instead")
@Serializable
data class SyncNotificationSettingsPayload(
    val notificationsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val chimeSound: String = "default",
    val minReadAloudPriority: String = "normal"
)
