package org.openhab.habdroid.wear.phone.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openhab.habdroid.wear.phone.R
import org.openhab.habdroid.wear.phone.ui.setup.ConnectionStatus
import org.openhab.habdroid.wear.phone.ui.setup.SetupViewModel
import org.openhab.habdroid.wear.phone.ui.setup.SyncResult
import org.openhab.habdroid.wear.phone.ui.setup.WatchStatus

@Composable
fun HomeScreen(
    onNavigateToConnection: () -> Unit,
    onNavigateToTileDesign: () -> Unit,
    onNavigateToComplications: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-check sync status whenever this screen becomes visible
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadSavedCredentials()
            viewModel.checkConfigSync()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Logo
            Image(
                painter = painterResource(id = R.drawable.ic_openhab_logo),
                contentDescription = "openHAB",
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "openHAB",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Wear OS Configurator",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Watch status chip
            WatchStatusChip(
                watchStatus = uiState.watchStatus,
                watchName = uiState.watchName,
                connectionType = uiState.connectionTypeLabel
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation cards
            NavigationCard(
                title = "Connection",
                subtitle = "Server credentials & config server",
                icon = Icons.Outlined.Settings,
                onClick = onNavigateToConnection
            )

            Spacer(modifier = Modifier.height(12.dp))

            val configReady = uiState.configConnectionStatus == ConnectionStatus.Success ||
                uiState.configHasStoredPassword

            NavigationCard(
                title = "Tile Design",
                subtitle = if (configReady) "Configure watch tile layout"
                    else "Set up config server first",
                icon = Icons.Default.GridView,
                onClick = onNavigateToTileDesign,
                enabled = configReady
            )

            Spacer(modifier = Modifier.height(12.dp))

            NavigationCard(
                title = "Complications",
                subtitle = if (configReady) "Configure watch face data"
                    else "Set up config server first",
                icon = Icons.Default.Watch,
                onClick = onNavigateToComplications,
                enabled = configReady
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sync to Watch button
            if (uiState.configOutOfSync && configReady) {
                Text(
                    text = "Watch config out of sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            SyncToWatchButton(
                syncResult = uiState.syncResult,
                canSync = uiState.canSendToWatch,
                onSync = viewModel::sendToWatch
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WatchStatusChip(
    watchStatus: WatchStatus,
    watchName: String?,
    connectionType: String?
) {
    val (icon, label, containerColor) = when (watchStatus) {
        WatchStatus.Unknown -> Triple(
            Icons.Default.Watch,
            "Checking…",
            MaterialTheme.colorScheme.surfaceVariant
        )
        WatchStatus.NotFound -> Triple(
            Icons.Default.CloudOff,
            "Watch not connected",
            MaterialTheme.colorScheme.errorContainer
        )
        WatchStatus.Connected -> Triple(
            if (connectionType == "Bluetooth") Icons.Outlined.Bluetooth else Icons.Default.Cloud,
            "${watchName ?: "Watch"} · $connectionType",
            MaterialTheme.colorScheme.primaryContainer
        )
        WatchStatus.Synced -> Triple(
            if (connectionType == "Bluetooth") Icons.Outlined.Bluetooth else Icons.Default.Cloud,
            "${watchName ?: "Watch"} · Synced",
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when (watchStatus) {
                    WatchStatus.NotFound -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when (watchStatus) {
                    WatchStatus.NotFound -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    }
}

@Composable
private fun NavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncToWatchButton(
    syncResult: SyncResult?,
    canSync: Boolean,
    onSync: () -> Unit
) {
    Button(
        onClick = onSync,
        enabled = canSync,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (syncResult == SyncResult.Sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sync to Watch")
    }

    if (syncResult is SyncResult.Success) {
        Text(
            text = "Credentials & tile config synced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    } else if (syncResult is SyncResult.Error) {
        Text(
            text = syncResult.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
