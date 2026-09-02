package org.openhab.habdroid.wear.notification

import android.content.Context
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openhab.habdroid.wear.data.api.ServerSelector
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.FcmClientConfig
import org.openhab.habdroid.wear.util.AppLog

/**
 * One-shot WorkManager job that acquires the Firebase client configuration from the MobileAudio binding and
 * initializes FCM on the watch.
 *
 * Flow:
 * 1. Skip if the binding is not installed (synced flag) — FCM stays dormant.
 * 2. GET {server}/mobileaudio/fcm-config (resolved via [ServerSelector], with auth).
 *    - 200: parse config, persist it, (re)initialize the default FirebaseApp, then chain [FcmRegistrationWorker].
 *      If the acquired config differs from the currently initialized project, the FCM token is deleted first so a
 *      fresh one is minted for the new project.
 *    - 404 (no client config) / 503 (binding cannot send yet): FCM is not ready. Clear any stored config and leave
 *      Firebase uninitialized. Not an error — self-heals on the next trigger.
 *    - 401/403: auth problem (fixed by the next phone sync), terminal for this run.
 *    - 5xx/network: transient, retry with backoff.
 *
 * The binding's fcm-config endpoint returns 200 only when FCM is fully operational (client config present AND the
 * binding can send), so a successful fetch is a single readiness signal.
 */
@HiltWorker
class FcmConfigWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val credentialStore: CredentialStore,
    private val serverSelector: ServerSelector,
    private val json: Json
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        AppLog.d(TAG, "FcmConfigWorker starting")

        val bindingInstalled = credentialStore.bindingInstalled.first()
        if (!bindingInstalled) {
            AppLog.d(TAG, "Mobile Audio binding not installed — skipping FCM config acquisition")
            return Result.success()
        }

        val credentials = credentialStore.credentials.first()
        if (credentials == null) {
            AppLog.w(TAG, "No credentials configured — skipping FCM config acquisition")
            return Result.failure()
        }

        serverSelector.reset()
        val serverUrl = serverSelector.resolveUrl().trimEnd('/')
        if (serverUrl.isBlank()) {
            AppLog.w(TAG, "Server URL is blank — skipping FCM config acquisition")
            return Result.failure()
        }
        val authHeader = serverSelector.resolveAuthHeader()

        val request = Request.Builder()
            .url("$serverUrl/mobileaudio/fcm-config")
            .get()
            .apply { authHeader?.let { header("Authorization", it) } }
            .build()

        val plainClient = OkHttpClient.Builder().followRedirects(true).build()

        val (code, body) = try {
            plainClient.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "FCM config request failed (${e.message}) — will retry")
            return Result.retry()
        }

        return when {
            code == 200 -> handleConfig(body)
            // Not ready: no client config (404) or the binding cannot send yet (503). Leave FCM inactive.
            code == 404 || code == 503 -> {
                AppLog.d(TAG, "FCM not available on server (HTTP $code) — clearing config, FCM stays inactive")
                credentialStore.clearFcmClientConfig()
                Result.success()
            }
            code == 401 || code == 403 -> {
                AppLog.w(TAG, "FCM config rejected (HTTP $code) — auth problem, not retrying")
                Result.failure()
            }
            code in 400..499 -> {
                AppLog.w(TAG, "FCM config failed (HTTP $code) — client error, not retrying")
                Result.failure()
            }
            else -> {
                AppLog.w(TAG, "FCM config failed (HTTP $code) — transient, will retry")
                Result.retry()
            }
        }
    }

    private suspend fun handleConfig(body: String): Result {
        val config = try {
            val obj = json.parseToJsonElement(body).jsonObject
            FcmClientConfig(
                projectId = obj["projectId"]!!.jsonPrimitive.content,
                senderId = obj["senderId"]!!.jsonPrimitive.content,
                applicationId = obj["applicationId"]!!.jsonPrimitive.content,
                apiKey = obj["apiKey"]!!.jsonPrimitive.content
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse FCM config response", e)
            return Result.failure()
        }

        if (config.projectId.isBlank() || config.senderId.isBlank() || config.applicationId.isBlank() ||
            config.apiKey.isBlank()
        ) {
            AppLog.w(TAG, "FCM config response missing required fields — not retrying")
            return Result.failure()
        }

        // If the project changed, delete the current token so a fresh one is minted for the new project.
        val projectChanged = FirebaseInitializer.isInitialized() &&
            FirebaseInitializer.currentApplicationId() != config.applicationId
        if (projectChanged) {
            AppLog.d(TAG, "FCM project changed — deleting current token before reinitializing")
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        }

        credentialStore.saveFcmClientConfig(config)

        val initialized = FirebaseInitializer.ensureInitialized(applicationContext, config)
        if (!initialized) {
            AppLog.w(TAG, "Firebase initialization failed — will retry")
            return Result.retry()
        }

        AppLog.d(TAG, "FCM config acquired and Firebase initialized — scheduling registration")
        FcmRegistrationWorker.schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "FcmConfig"
        private const val WORK_NAME = "fcm_config"

        /**
         * Acquire the FCM client config and initialize FCM. Chains [FcmRegistrationWorker] on success.
         * Safe to call on every relevant trigger (credential sync, app open, tile open): the fetch is cheap and
         * self-heals when the server's readiness changes.
         */
        fun schedule(context: Context) {
            AppLog.d(TAG, "Scheduling FCM config acquisition")
            val request = OneTimeWorkRequestBuilder<FcmConfigWorker>()
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
