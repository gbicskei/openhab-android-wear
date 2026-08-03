package org.openhab.habdroid.wear.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.phone.ui.navigation.AppNavHost
import org.openhab.habdroid.wear.phone.ui.theme.OpenHabWearPhoneTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent?.getStringExtra(
            org.openhab.habdroid.wear.phone.sync.PhoneWearListenerService.EXTRA_NAVIGATE_TO
        )

        setContent {
            OpenHabWearPhoneTheme {
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
}
