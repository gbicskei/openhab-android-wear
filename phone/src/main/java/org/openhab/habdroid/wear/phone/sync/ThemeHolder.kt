package org.openhab.habdroid.wear.phone.sync

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.util.AppLog

/**
 * Receives watch-side theme changes from the DataLayer listener service
 * and persists them to [PhoneCredentialStore].
 *
 * Uses Hilt EntryPoint to access the singleton credential store from a non-DI context.
 */
object ThemeHolder {
    private const val TAG = "ThemeHolder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ThemeHolderEntryPoint {
        fun credentialStore(): PhoneCredentialStore
    }

    fun update(themeName: String, context: Context) {
        AppLog.d(TAG, "Watch theme changed to $themeName — updating phone")
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeHolderEntryPoint::class.java
        )
        scope.launch {
            val store = entryPoint.credentialStore()
            store.saveSelectedTheme(themeName.uppercase())
        }
    }
}
