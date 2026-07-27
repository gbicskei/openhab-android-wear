package org.openhab.habdroid.wear.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import org.openhab.habdroid.wear.ui.setup.SetupActivity

/**
 * Main launcher activity for the openHAB Wear OS app.
 * If not configured, redirects to setup. Otherwise shows settings and reload.
 * The primary interaction happens on the tile itself (buttons + mic).
 * Theme config is accessed via long-press on tile → pencil icon.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val isConfigured by viewModel.isConfigured.collectAsState(initial = true)

            if (!isConfigured) {
                LaunchedEffect(Unit) {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                }
            }

            MainScreen(
                viewModel = viewModel,
                onSetup = {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onSetup: () -> Unit
) {
    val reloadState by viewModel.reloadState.collectAsState()
    val context = LocalContext.current

    // Show toast when reload completes
    LaunchedEffect(reloadState) {
        when (val state = reloadState) {
            is ReloadState.Success -> {
                Toast.makeText(context, "Loaded ${state.count} items", Toast.LENGTH_SHORT).show()
                viewModel.clearReloadState()
            }
            is ReloadState.Error -> {
                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.clearReloadState()
            }
            else -> {}
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("openHAB")
            }
        }
        item {
            Button(
                onClick = onSetup,
                label = { Text("Server Settings") },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
        item {
            Button(
                onClick = { viewModel.reloadItems() },
                enabled = reloadState !is ReloadState.Loading,
                label = { Text(if (reloadState is ReloadState.Loading) "Loading..." else "Reload Items") },
                icon = {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}
