package org.openhab.habdroid.wear.data.repository

/**
 * Firebase client configuration acquired from the MobileAudio binding's fcm-config endpoint.
 *
 * These values identify the Firebase project the watch must initialize FCM against so it matches the project the
 * binding sends with. They are supplied by the server at runtime — the app ships no bundled default.
 */
data class FcmClientConfig(
    val projectId: String,
    val senderId: String,
    val applicationId: String,
    val apiKey: String
)
