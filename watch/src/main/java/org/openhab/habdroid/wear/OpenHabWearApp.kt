package org.openhab.habdroid.wear

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.ThemeStore
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

    @Inject
    lateinit var credentialStore: CredentialStore

    @Inject
    lateinit var themeStore: ThemeStore

    @Inject
    lateinit var watchStatusWriter: org.openhab.habdroid.wear.sync.WatchStatusWriter

    @Inject
    lateinit var watchSettingsDataStore: org.openhab.habdroid.wear.sync.WatchSettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.onErrorLogged = { debugLogWriter.publish() }

        // Warm the theme cache so activities render the correct color on first frame
        themeStore.getThemeBlocking()

        // Capture uncaught exceptions to DebugLog (sent to phone in debug mode)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e("CRASH", "Uncaught exception on ${thread.name}", throwable)
            // Give a moment for the publish to fire
            try { Thread.sleep(500) } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Restore persisted debug mode
        appScope.launch {
            AppLog.debugMode = credentialStore.getDebugMode()
        }

        // Initialize Firebase from the config previously acquired from the binding, so the FCM listener service can
        // receive messages on cold start. There is no bundled google-services.json — FCM stays inactive until a
        // config has been acquired.
        appScope.launch {
            credentialStore.getFcmClientConfig()?.let { config ->
                org.openhab.habdroid.wear.notification.FirebaseInitializer.ensureInitialized(this@OpenHabWearApp, config)
            }
        }

        // Initialize DataItem: reads existing settings, publishes fresh status (version, speaker)
        appScope.launch {
            watchSettingsDataStore.initialize()
        }

        // Publish current theme to DataItem (in case themeStore has a value not yet in DataItem)
        appScope.launch {
            val theme = themeStore.getTheme()
            watchSettingsDataStore.writeTheme(theme.name)
        }

        // Re-register as assistant on every launch (survives reinstalls)
        assistantRegistrar.ensureRegistered(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
