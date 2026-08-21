package org.openhab.habdroid.wear.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.openhab.habdroid.wear.R
import org.openhab.habdroid.wear.ui.theme.StatusChecking
import org.openhab.habdroid.wear.ui.theme.StatusOffline
import org.openhab.habdroid.wear.ui.theme.StatusOnline
import org.openhab.habdroid.wear.ui.MainViewModel

/**
 * Shared app logo header with optional connection status indicator.
 * Shows the watch icon centered with a small colored dot at the bottom-right
 * indicating server connectivity (green = online, red = offline, gray = checking).
 */
@Composable
fun AppLogoHeader(
    viewModel: MainViewModel = hiltViewModel()
) {
    val serverOnline by viewModel.serverOnline.collectAsState()

    AppLogoHeader(serverOnline = serverOnline, showIndicator = true)
}

@Composable
fun AppLogoHeader(serverOnline: Boolean? = null, showIndicator: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_wearoh_logo),
            contentDescription = "openHAB",
            modifier = Modifier.size(40.dp)
        )
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .offset(x = 15.dp, y = 6.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when (serverOnline) {
                            true -> StatusOnline
                            false -> StatusOffline
                            else -> StatusChecking
                        }
                    )
            )
        }
    }
}
