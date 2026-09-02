package org.openhab.habdroid.wear.notification

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.api.ServerSelector
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.util.AppLog

/**
 * One-shot WorkManager job that registers the watch's FCM token with the
 * MobileAudio binding on the openHAB server.
 *
 * Calls GET {serverUrl}/mobileaudio/register?regId={fcmToken}&deviceId={androidId}&deviceModel={model}
 * using the active server URL and auth resolved by [ServerSelector].
 *
 * The endpoint is reachable both directly on the local network and through the
 * myopenhab.org cloud proxy (the cloud forwards any path to the local instance),
 * so registration can succeed whether the watch is home or remote.
 *
 * On success the registered token is persisted via [CredentialStore.saveLastRegisteredFcmToken]
 * so redundant re-registrations can be skipped when the token is unchanged.
 *
 * Triggered:
 * - After credential sync from phone
 * - On FCM token refresh (onNewToken)
 * - When the app or tile is opened (self-heals a token the binding rejected as UNREGISTERED)
 */
@HiltWorker
class FcmRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val credentialStore: CredentialStore,
    private val serverSelector: ServerSelector
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        AppLog.d(TAG, "FcmRegistrationWorker starting")

        // Check if binding is installed (synced from phone)
        val bindingInstalled = credentialStore.bindingInstalled.first()
        if (!bindingInstalled) {
            AppLog.d(TAG, "Mobile Audio binding not installed — skipping FCM registration")
            return Result.success()
        }

        val credentials = credentialStore.credentials.first()
        if (credentials == null) {
            AppLog.w(TAG, "No credentials configured — skipping FCM registration")
            return Result.failure()
        }

        // Use ServerSelector to resolve the best reachable server
        serverSelector.reset()
        val serverUrl = serverSelector.resolveUrl().trimEnd('/')
        if (serverUrl.isBlank()) {
            AppLog.w(TAG, "Server URL is blank — skipping FCM registration")
            return Result.failure()
        }

        AppLog.d(TAG, "Resolved server: $serverUrl (local=${serverSelector.isLocalActive()})")

        val authHeader = serverSelector.resolveAuthHeader()

        // Firebase is initialized at runtime from the config the binding supplies (there is no bundled
        // google-services.json). If no config has been acquired yet, kick off acquisition and stop — that worker
        // re-schedules registration once Firebase is initialized.
        val fcmConfig = credentialStore.getFcmClientConfig()
        if (fcmConfig == null) {
            AppLog.d(TAG, "No FCM client config yet — triggering acquisition before registration")
            FcmConfigWorker.schedule(applicationContext)
            return Result.success()
        }
        if (!FirebaseInitializer.ensureInitialized(applicationContext, fcmConfig)) {
            AppLog.w(TAG, "Firebase not initialized — retrying")
            return Result.retry()
        }

        // Get FCM token
        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get FCM token", e)
            return Result.retry()
        }

        if (fcmToken.isBlank()) {
            AppLog.w(TAG, "FCM token is blank — retrying")
            return Result.retry()
        }

        AppLog.d(TAG, "Got FCM token (${fcmToken.take(10)}...)")

        // Skip the network call when this is a non-forced trigger (app/tile open) and the
        // current token was already registered successfully. Forced triggers (token refresh,
        // phone sync) always re-register.
        val force = inputData.getBoolean(KEY_FORCE, true)
        if (!force) {
            val lastRegistered = credentialStore.lastRegisteredFcmToken.first()
            if (lastRegistered == fcmToken) {
                AppLog.d(TAG, "Token unchanged since last registration — skipping")
                return Result.success()
            }
        }

        // Get device identifiers
        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val deviceModel = Build.MODEL ?: "Wear OS"
        val deviceName = credentialStore.deviceName.first()

        // Register with the MobileAudio binding on the openHAB server
        val registrationUrl = "$serverUrl/mobileaudio/register" +
            "?regId=${java.net.URLEncoder.encode(fcmToken, "UTF-8")}" +
            "&deviceId=${java.net.URLEncoder.encode(deviceId, "UTF-8")}" +
            "&deviceModel=${java.net.URLEncoder.encode(deviceModel, "UTF-8")}" +
            if (deviceName.isNotBlank()) "&deviceName=${java.net.URLEncoder.encode(deviceName, "UTF-8")}" else ""

        // Use a plain OkHttpClient without the AuthInterceptor (which rewrites URLs)
        val plainClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()

        val requestBuilder = Request.Builder()
            .url(registrationUrl)
            .get()

        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }

        return try {
            val response = plainClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()
            when {
                response.isSuccessful -> {
                    AppLog.d(TAG, "FCM registration successful (device=$deviceId, model=$deviceModel, name=$deviceName)")
                    // Record the token we just registered so subsequent triggers can skip redundant work.
                    credentialStore.saveLastRegisteredFcmToken(fcmToken)
                    Result.success()
                }
                // 401/403: cloud/local auth rejected the request. Credentials come from phone sync;
                // retrying here won't fix it until the next sync, so treat as terminal for this run.
                code == 401 || code == 403 -> {
                    AppLog.w(TAG, "FCM registration rejected (HTTP $code) — auth problem, not retrying")
                    Result.failure()
                }
                // Other 4xx: malformed request (bad params) — deterministic, retrying won't help.
                code in 400..499 -> {
                    AppLog.w(TAG, "FCM registration failed (HTTP $code) — client error, not retrying")
                    Result.failure()
                }
                // 5xx and anything else: transient server-side/proxy condition — retry with backoff.
                else -> {
                    AppLog.w(TAG, "FCM registration failed (HTTP $code) — transient, will retry")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            // Network error / server unreachable — transient, retry when connectivity returns.
            AppLog.w(TAG, "FCM registration request failed (${e.message}) — will retry")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "FcmRegistration"
        private const val WORK_NAME = "fcm_registration"

        /** Input flag: when true, always re-register; when false, skip if the token is unchanged. */
        private const val KEY_FORCE = "force"

        /**
         * Schedule FCM registration, always re-registering the current token.
         * Use for events where the token may have changed (token refresh, phone credential sync).
         * Uses REPLACE policy so the latest token is always registered.
         */
        fun schedule(context: Context) {
            enqueue(context, force = true)
        }

        /**
         * Schedule FCM registration but skip the network call if the current token was already
         * registered successfully. Use for frequent triggers (app open, tile open) where a
         * re-registration is only needed when the token actually rotated or was rejected.
         */
        fun scheduleIfNeeded(context: Context) {
            enqueue(context, force = false)
        }

        private fun enqueue(context: Context, force: Boolean) {
            AppLog.d(TAG, "Scheduling FCM registration (force=$force)")

            val request = OneTimeWorkRequestBuilder<FcmRegistrationWorker>()
                .setInputData(androidx.work.Data.Builder().putBoolean(KEY_FORCE, force).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
