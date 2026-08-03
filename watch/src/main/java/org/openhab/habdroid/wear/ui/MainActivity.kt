package org.openhab.habdroid.wear.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.openhab.habdroid.wear.R

/**
 * Main launcher activity for the openHAB Wear OS app.
 * Shows logo, setup on phone, reload items, and about.
 * The primary interaction happens on the tile itself (buttons + mic).
 * Theme config is accessed via long-press on tile → pencil icon.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onAbout = {
                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onAbout: () -> Unit = {}
) {
    val reloadState by viewModel.reloadState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
            Image(
                painter = painterResource(id = R.drawable.ic_openhab_logo),
                contentDescription = "openHAB",
                modifier = Modifier.size(36.dp)
            )
        }
        item {
            ListHeader {
                Text("openHAB")
            }
        }
        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val nodeClient = Wearable.getNodeClient(context)
                            val nodes = nodeClient.connectedNodes.await()
                            val phoneNode = nodes.firstOrNull()
                            if (phoneNode != null) {
                                // Check if phone has the companion app installed
                                val capClient = Wearable.getCapabilityClient(context)
                                val capInfo = try {
                                    capClient.getCapability("openhab_phone_app", com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE).await()
                                } catch (_: Exception) { null }

                                if (capInfo != null && capInfo.nodes.isNotEmpty()) {
                                    // Phone app is installed — send open message
                                    val messageClient = Wearable.getMessageClient(context)
                                    messageClient.sendMessage(
                                        phoneNode.id,
                                        "/openhab/open-app",
                                        ByteArray(0)
                                    ).await()
                                    Toast.makeText(context, "Opening on phone…", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Phone connected but companion app not installed
                                    Toast.makeText(context, "Install openHAB companion on phone", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Phone not connected via Bluetooth", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not reach phone", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                label = { Text("Setup on Phone") },
                icon = {
                    Icon(
                        Icons.Default.PhoneAndroid,
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
        item {
            Button(
                onClick = onAbout,
                label = { Text("About") },
                icon = {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}
