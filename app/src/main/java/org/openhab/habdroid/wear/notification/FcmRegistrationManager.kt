package org.openhab.habdroid.wear.notification

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.openhab.habdroid.wear.data.repository.CredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages FCM token registration with the openHAB Cloud service.
 * When a new FCM token is obtained, it registers the device with the cloud
 * so the watch can receive push notifications independently from the phone.
 */
@Singleton
class FcmRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleRegistration(context: Context) {
        val work = OneTimeWorkRequestBuilder<FcmRegistrationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                work
            )
    }

    companion object {
        private const val WORK_NAME = "fcm_registration"
    }
}

/**
 * Worker that registers the FCM token with the openHAB Cloud instance.
 */
class FcmRegistrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Note: In production, inject these via HiltWorker. Simplified here for scaffold.
    override suspend fun doWork(): Result {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM token obtained, registering with cloud...")

            // TODO: Register token with openHAB Cloud
            // The cloud API endpoint is: POST {cloudUrl}/addDevice
            // Body: { "deviceId": "<unique-id>", "deviceModel": "WearOS", "regId": "<fcm-token>" }
            // This requires the cloud service credentials (same as REST API auth)

            Log.d(TAG, "FCM registration placeholder — implement cloud registration")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "FCM registration failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "FcmRegistrationWorker"
    }
}
