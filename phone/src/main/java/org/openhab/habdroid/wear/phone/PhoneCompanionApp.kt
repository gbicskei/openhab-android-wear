package org.openhab.habdroid.wear.phone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.openhab.habdroid.wear.phone.sync.DebugLogReader
import javax.inject.Inject

@HiltAndroidApp
class PhoneCompanionApp : Application() {

    // Eagerly inject DebugLogReader so its DataClient listener starts at app launch,
    // not when the Debug Log screen is first opened. This ensures watch log entries
    // are captured for the entire process lifetime.
    @Inject
    lateinit var debugLogReader: DebugLogReader
}
