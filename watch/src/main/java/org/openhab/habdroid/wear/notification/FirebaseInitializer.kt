package org.openhab.habdroid.wear.notification

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.openhab.habdroid.wear.data.repository.FcmClientConfig
import org.openhab.habdroid.wear.util.AppLog

/**
 * Initializes the default [FirebaseApp] at runtime from a server-supplied [FcmClientConfig].
 *
 * The app ships no `google-services.json`, so Firebase does not auto-initialize. The default app must be created
 * explicitly before any `FirebaseMessaging.getInstance()` call, and it must be the DEFAULT app (not a named
 * secondary instance) so that the manifest-declared `FirebaseMessagingService` receives messages.
 *
 * Initialization is idempotent: if the default app already exists with the same application id, it is left as is;
 * if it exists with a different id (the server switched projects), it is deleted and recreated so a fresh token can
 * be obtained for the new project.
 */
object FirebaseInitializer {

    private const val TAG = "FirebaseInit"

    /**
     * Ensure the default [FirebaseApp] reflects [config].
     *
     * @return true if the app is initialized with [config] (either already or after (re)initialization), false if
     *         initialization failed.
     */
    @Synchronized
    fun ensureInitialized(context: Context, config: FcmClientConfig): Boolean {
        val existing = runCatching { FirebaseApp.getInstance() }.getOrNull()
        if (existing != null) {
            if (existing.options.applicationId == config.applicationId) {
                return true
            }
            AppLog.d(
                TAG,
                "Firebase project changed (${existing.options.applicationId} -> ${config.applicationId}); reinitializing"
            )
            runCatching { existing.delete() }
        }

        return try {
            val options = FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
                .setGcmSenderId(config.senderId)
                .build()
            FirebaseApp.initializeApp(context, options)
            AppLog.d(TAG, "Firebase initialized for project ${config.projectId}")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to initialize Firebase", e)
            false
        }
    }

    /** Whether the default [FirebaseApp] is currently initialized. */
    fun isInitialized(): Boolean = runCatching { FirebaseApp.getInstance() }.getOrNull() != null

    /** The application id of the currently initialized default app, or null if none. */
    fun currentApplicationId(): String? =
        runCatching { FirebaseApp.getInstance().options.applicationId }.getOrNull()
}
