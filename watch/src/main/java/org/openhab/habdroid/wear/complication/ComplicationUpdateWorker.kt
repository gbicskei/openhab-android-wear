package org.openhab.habdroid.wear.complication

import android.content.ComponentName
import android.content.Context
import org.openhab.habdroid.wear.util.AppLog
import androidx.hilt.work.HiltWorker
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that triggers complication data refresh.
 *
 * Calls requestUpdateAll() every 15 minutes, which causes the system to invoke
 * onComplicationRequest() on OpenHabComplicationService for each active complication slot.
 * The service then fetches fresh state from the server.
 *
 * Requires network connectivity — skipped if offline (retried next period).
 */
@HiltWorker
class ComplicationUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        AppLog.d(TAG, "ComplicationUpdateWorker running — requesting update for all complications")

        val requester = ComplicationDataSourceUpdateRequester.create(
            context = applicationContext,
            complicationDataSourceComponent = ComponentName(
                applicationContext,
                OpenHabComplicationService::class.java
            )
        )
        requester.requestUpdateAll()

        return Result.success()
    }

    companion object {
        private const val TAG = "ComplicationWorker"
        private const val WORK_NAME = "complication_periodic_refresh"

        /**
         * Schedule the periodic refresh worker.
         * Uses KEEP policy — if already scheduled, does nothing.
         * Call this when a complication is first activated.
         */
        fun schedule(context: Context) {
            AppLog.d(TAG, "Scheduling periodic complication refresh (15 min)")

            val request = PeriodicWorkRequestBuilder<ComplicationUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Cancel the periodic refresh worker.
         * Call this when no complications remain active.
         */
        fun cancel(context: Context) {
            AppLog.d(TAG, "Cancelling periodic complication refresh")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
