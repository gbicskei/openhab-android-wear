package org.openhab.habdroid.wear.phone.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton holder for the watch app's reported versionName.
 * Written by PhoneWearListenerService, observed by SetupViewModel.
 */
object WatchVersionHolder {
    private val _watchVersion = MutableStateFlow<String?>(null)

    /** The last reported watch app versionName, or null if not yet received. */
    val watchVersion: StateFlow<String?> = _watchVersion.asStateFlow()

    fun update(versionName: String) {
        _watchVersion.value = versionName
    }

    fun clear() {
        _watchVersion.value = null
    }
}
