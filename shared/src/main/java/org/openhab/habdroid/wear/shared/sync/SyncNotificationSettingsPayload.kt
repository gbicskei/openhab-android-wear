package org.openhab.habdroid.wear.shared.sync

import kotlinx.serialization.Serializable

/**
 * JSON payload for notification settings sync from phone to watch.
 */
@Serializable
data class SyncNotificationSettingsPayload(
    val notificationsEnabled: Boolean = true,
    val readAloudEnabled: Boolean = false,
    val chimeEnabled: Boolean = true,
    val chimeSound: String = "default",
    val minReadAloudPriority: String = "normal"
)
