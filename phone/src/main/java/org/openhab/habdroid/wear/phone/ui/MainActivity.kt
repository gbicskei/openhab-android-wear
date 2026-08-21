package org.openhab.habdroid.wear.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.phone.BuildConfig
import org.openhab.habdroid.wear.phone.data.PhoneCredentialStore
import org.openhab.habdroid.wear.phone.ui.navigation.AppNavHost
import org.openhab.habdroid.wear.phone.ui.theme.OpenHabWearPhoneTheme
import org.openhab.habdroid.wear.shared.sync.VersionCompat
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var credentialStore: PhoneCredentialStore

    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // If the user cancels or the update fails, re-check on next resume
        if (result.resultCode != RESULT_OK) {
            // User declined — we'll re-prompt on next onResume
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent?.getStringExtra(
            org.openhab.habdroid.wear.phone.sync.PhoneWearListenerService.EXTRA_NAVIGATE_TO
        )

        setContent {
            OpenHabWearPhoneTheme(accentName = credentialStore.getSelectedTheme()) {
                val navController = rememberNavController()
                AppNavHost(navController = navController)

                // Deep-link navigation from watch
                androidx.compose.runtime.LaunchedEffect(navigateTo) {
                    if (navigateTo != null) {
                        navController.navigate(navigateTo)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkForAppUpdate()
    }

    /**
     * Checks Play Store for an available update and triggers IMMEDIATE flow
     * if the phone app is a production build with an update available.
     * Dev builds skip this check entirely.
     */
    private fun checkForAppUpdate() {
        // Never block dev builds
        if (VersionCompat.isDevBuild(BuildConfig.VERSION_NAME)) return

        lifecycleScope.launch {
            try {
                val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
                // Also handle the case where an update was already downloaded but not installed
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
            } catch (_: Exception) {
                // Play Store not available or check failed — non-fatal
            }
        }
    }
}
