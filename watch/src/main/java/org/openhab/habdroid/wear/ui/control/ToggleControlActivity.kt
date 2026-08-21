package org.openhab.habdroid.wear.ui.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.openhab.habdroid.wear.complication.ComplicationRefresher
import org.openhab.habdroid.wear.data.repository.OpenHabRepository
import org.openhab.habdroid.wear.data.repository.ThemeStore
import org.openhab.habdroid.wear.ui.theme.WearOHTheme
import org.openhab.habdroid.wear.util.AppLog
import javax.inject.Inject

/**
 * Toggle control activity for Switch items.
 * Shows the wearOH icon, item value using shortText config, and a toggle button.
 */
@AndroidEntryPoint
class ToggleControlActivity : ComponentActivity() {

    @Inject
    lateinit var repository: OpenHabRepository

    @Inject
    lateinit var complicationRefresher: ComplicationRefresher

    @Inject
    lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemName = intent.getStringExtra("item_name") ?: run {
            finish()
            return
        }
        val label = intent.getStringExtra("label") ?: ""

        setContent {
            WearOHTheme {
                ToggleControlScreen(
                    itemName = itemName,
                    passedLabel = label,
                    repository = repository,
                    themeStore = themeStore,
                    onCommandSent = {
                        complicationRefresher.requestUpdate()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleControlScreen(
    itemName: String,
    passedLabel: String,
    repository: OpenHabRepository,
    themeStore: ThemeStore,
    onCommandSent: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var isOn by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var themeColor by remember { mutableStateOf(ControlStyle.DEFAULT_THEME_COLOR) }

    LaunchedEffect(Unit) {
        themeColor = themeStore.getTheme().color.toLong() and 0xFFFFFFFFL
    }

    LaunchedEffect(itemName) {
        val itemResult = repository.getItem(itemName)

        itemResult.onSuccess { item ->
            isOn = item.state == "ON" || item.state == "OPEN"

            // Use passed label, fall back to item's actual label, then name
            title = passedLabel.takeIf { it.isNotBlank() && it != item.type }
                ?: item.label?.takeIf { it.isNotBlank() }
                ?: item.name

            // Use transformedState (server-formatted via stateDescription)
            valueText = item.transformedState
                ?.takeIf { it !in listOf("NULL", "UNDEF") }
                ?: formatStateDisplay(item.state)

            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    // Subscribe to SSE events for real-time state updates while visible
    LaunchedEffect(itemName) {
        repository.observeItemState(itemName).collect { newState ->
            isOn = newState == "ON" || newState == "OPEN"
            valueText = formatStateDisplay(newState)
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Item label
            ControlLabel(text = title)

            // Value text
            ControlValue(
                text = valueText,
                highlighted = isOn,
                highlightColor = Color(themeColor),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Toggle button
            IconToggleButton(
                checked = isOn,
                onCheckedChange = { newState ->
                    isOn = newState
                    valueText = if (newState) "On" else "Off"
                    scope.launch {
                        val command = if (newState) "ON" else "OFF"
                        repository.sendCommand(itemName, command)
                        AppLog.d("ToggleControl", "Sent $command to $itemName")
                        onCommandSent()
                    }
                },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isOn) "Turn off" else "Turn on",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // wearOH logo at top center
        ControlLogo(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ControlStyle.LogoTopOffset)
        )
    }
}
