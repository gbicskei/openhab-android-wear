package org.openhab.habdroid.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to trigger complication data refresh from anywhere in the app.
 * Requests the system to re-query both the legacy OpenHabComplicationService
 * and all enabled slot complication services for fresh data.
 */
@Singleton
class ComplicationRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun requestUpdate() {
        // Refresh legacy complication service
        ComplicationDataSourceUpdateRequester.create(
            context, ComponentName(context, OpenHabComplicationService::class.java)
        ).requestUpdateAll()
        // Refresh all enabled slot complication services
        OpenHabSlotComplicationService.requestUpdateAll(context)
    }
}
