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

        setContent {
            OpenHabWearPhoneTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
