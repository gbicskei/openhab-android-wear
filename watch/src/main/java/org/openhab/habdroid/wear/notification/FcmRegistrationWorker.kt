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
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.util.AppLog

/**
 * One-shot WorkManager job that registers the watch's FCM token with the
 * MobileAudio binding on the openHAB server.
 *
 * Calls GET {serverUrl}/mobileaudio/register?regId={fcmToken}&deviceId={androidId}&deviceModel={model}
 * using the configured server URL and Basic Auth credentials from CredentialStore.
 *
 * Triggered:
 * - After credential sync from phone
 * - On FCM token refresh (onNewToken)
 */
@HiltWorker
class FcmRegistrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val credentialStore: CredentialStore,
    private val okHttpClient: OkHttpClient
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

        // Prefer local server URL for registration (the servlet is not proxied by myopenhab.org)
        val localUrl = credentialStore.localServerUrl.first()
        val serverUrl = localUrl.takeIf { it.isNotBlank() }?.trimEnd('/')
            ?: credentials.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            AppLog.w(TAG, "Server URL is blank — skipping FCM registration")
            return Result.failure()
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

        val request = Request.Builder()
            .url(registrationUrl)
            .header("Authorization", Credentials.basic(credentials.username, credentials.password))
            .get()
            .build()

        return try {
            val response = plainClient.newCall(request).execute()
            if (response.isSuccessful) {
                AppLog.d(TAG, "FCM registration successful (device=$deviceId, model=$deviceModel, name=$deviceName)")
                Result.success()
            } else {
                AppLog.w(TAG, "FCM registration failed: HTTP ${response.code}")
                if (response.code in 400..499) {
                    // Client error — don't retry (likely auth issue)
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "FCM registration request failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "FcmRegistration"
        private const val WORK_NAME = "fcm_registration"

        /**
         * Schedule FCM registration. Uses REPLACE policy so the latest token is always registered.
         */
        fun schedule(context: Context) {
            AppLog.d(TAG, "Scheduling FCM registration")

            val request = OneTimeWorkRequestBuilder<FcmRegistrationWorker>()
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
