package org.openhab.habdroid.wear

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.openhab.habdroid.wear.sync.DebugLogWriter
import org.openhab.habdroid.wear.ui.voice.AssistantRegistrar
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

@HiltAndroidApp
class OpenHabWearApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var debugLogWriter: DebugLogWriter

    @Inject
    lateinit var assistantRegistrar: AssistantRegistrar

    override fun onCreate() {
        super.onCreate()
        AppLog.onErrorLogged = { debugLogWriter.publish() }

        // Re-register as assistant on every launch (survives reinstalls)
        assistantRegistrar.ensureRegistered(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
