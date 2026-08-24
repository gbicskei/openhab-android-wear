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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
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
    onNavigateToWatchSettings: () -> Unit = {},
    onNavigateToDebugLog: () -> Unit = {},
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
        val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = systemBarsPadding.calculateTopPadding() + 16.dp,
                    bottom = systemBarsPadding.calculateBottomPadding() + 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Logo wordmark
            Image(
                painter = painterResource(id = R.drawable.ic_wearoh_wordmark),
                contentDescription = "wearOH",
                modifier = Modifier.height(92.dp)
            )

            Text(
                text = "wearOH Configurator",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = (-12).dp)
            )

            Text(
                text = buildString {
                    append("v${org.openhab.habdroid.wear.phone.BuildConfig.VERSION_NAME} (${org.openhab.habdroid.wear.phone.BuildConfig.VERSION_CODE})")
                    if (uiState.userKey.isNotBlank()) {
                        append(" - ${uiState.userKey}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Watch status chip
            WatchStatusChip(
                watchStatus = uiState.watchStatus,
                watchName = uiState.watchName,
                connectionType = uiState.connectionTypeLabel
            )

            // Watch version mismatch warning
            if (uiState.watchVersionMismatch) {
                Spacer(modifier = Modifier.height(8.dp))
                WatchOutdatedBanner(
                    phoneVersion = org.openhab.habdroid.wear.phone.BuildConfig.VERSION_NAME,
                    watchVersion = uiState.watchVersionName
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation cards
            NavigationCard(
                title = "Connection",
                icon = Icons.Outlined.Settings,
                onClick = onNavigateToConnection
            )

            Spacer(modifier = Modifier.height(8.dp))

            val configReady = uiState.configConnectionStatus == ConnectionStatus.Success ||
                uiState.configHasStoredPassword || uiState.configHasStoredApiToken

            // ─── Content Design (collapsible) ───
            ExpandableNavigationCard(
                title = "Content Design",
                icon = Icons.Default.GridView,
                enabled = configReady
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                NavigationCard(
                    title = "Tile",
                    icon = Icons.Default.GridView,
                    onClick = onNavigateToTileDesign,
                    enabled = configReady
                )

                Spacer(modifier = Modifier.height(8.dp))

                NavigationCard(
                    title = "Complications",
                    icon = Icons.Default.Watch,
                    onClick = onNavigateToComplications,
                    enabled = configReady
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sync to Watch button
                if (uiState.configOutOfSync && configReady && uiState.watchStatus != WatchStatus.AppNotInstalled) {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Watch Settings ───
            val watchConnected = uiState.watchStatus == WatchStatus.Connected ||
                uiState.watchStatus == WatchStatus.Synced

            NavigationCard(
                title = "Watch Settings",
                icon = Icons.Default.Watch,
                onClick = onNavigateToWatchSettings,
                enabled = watchConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Debug (shown only when enabled) ───
            if (uiState.debugMode) {
                NavigationCard(
                    title = "Debug Log",
                    icon = Icons.Outlined.Settings,
                    onClick = onNavigateToDebugLog
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
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
        WatchStatus.AppNotInstalled -> Triple(
            Icons.Default.Watch,
            "Watch app not installed",
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
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = when (watchStatus) {
                    WatchStatus.NotFound, WatchStatus.AppNotInstalled -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when (watchStatus) {
                        WatchStatus.NotFound, WatchStatus.AppNotInstalled -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                if (watchStatus == WatchStatus.AppNotInstalled) {
                    Text(
                        text = "Install the wearOH app on your watch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableNavigationCard(
    title: String,
    icon: ImageVector,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        onClick = { if (enabled) expanded = !expanded },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                content()
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

@Composable
private fun WatchOutdatedBanner(
    phoneVersion: String,
    watchVersion: String?
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Watch app outdated — sync paused",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Phone: v$phoneVersion · Watch: v${watchVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "Update the watch app from Play Store",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
