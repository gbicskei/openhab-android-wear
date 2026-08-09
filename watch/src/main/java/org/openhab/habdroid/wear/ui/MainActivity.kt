package org.openhab.habdroid.wear.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Shows logo, setup on phone, reload items, settings, and about.
 * The primary interaction happens on the tile itself (buttons + mic).
 * Theme config is accessed via long-press on tile → pencil icon.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* no-op: we just need to prompt, user decides */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            MainScreen(
                onAbout = {
                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                },
                onSettings = {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onAbout: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val reloadState by viewModel.reloadState.collectAsState()
    val serverOnline by viewModel.serverOnline.collectAsState()
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

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed header: wordmark logo + connection status dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_openhab_wordmark),
                contentDescription = "openHAB",
                modifier = Modifier.height(40.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (serverOnline) {
                            true -> Color(0xFF4CAF50)   // green
                            false -> Color(0xFFF44336)  // red
                            null -> Color(0xFF9E9E9E)   // gray (checking)
                        }
                    )
            )
        }

        // Scrollable buttons underneath
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val nodeClient = Wearable.getNodeClient(context)
                                val nodes = nodeClient.connectedNodes.await()
                                val phoneNode = nodes.firstOrNull()
                                if (phoneNode != null) {
                                    val messageClient = Wearable.getMessageClient(context)
                                    messageClient.sendMessage(
                                        phoneNode.id,
                                        "/openhab/open-app",
                                        ByteArray(0)
                                    ).await()
                                    Toast.makeText(context, "Opening on phone…", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Phone not connected.\nInstall companion app and enable Bluetooth.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not reach phone", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Settings") },
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
                    onClick = onAbout,
                    modifier = Modifier.fillMaxWidth(),
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
}
