package org.openhab.habdroid.wear.ui.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import dagger.hilt.android.AndroidEntryPoint

/**
 * Choice picker activity for items with commandOptions or stateOptions.
 * Shows a scrollable list of selectable options. Tapping an option sends the command.
 * Highlights the currently active option.
 */
@AndroidEntryPoint
class ChoicePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChoicePickerScreen()
        }
    }
}

@Composable
fun ChoicePickerScreen(
    viewModel: ChoicePickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading...", color = Color.White)
            }
        }

        state.error != null && state.options.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${state.error}",
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        else -> {
            val listState = rememberScalingLazyListState()

            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with logo and item label
                item {
                    ListHeader {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ControlLogo()
                            ControlLabel(text = state.label)
                        }
                    }
                }

                // Option cards
                items(state.options) { option ->
                    val isActive = option.command == state.currentValue
                    OptionCard(
                        option = option,
                        isActive = isActive,
                        isSending = state.isSending,
                        themeColor = Color(state.themeColor),
                        onClick = { viewModel.selectOption(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    option: ChoiceOption,
    isActive: Boolean,
    isSending: Boolean,
    themeColor: Color = Color(ControlStyle.DEFAULT_THEME_COLOR),
    onClick: () -> Unit
) {
    TitleCard(
        onClick = { if (!isSending) onClick() },
        title = {
            Text(
                text = option.label,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) themeColor else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Show the raw command value underneath if different from label
        if (option.command != option.label) {
            Text(
                text = option.command,
                fontSize = 11.sp,
                color = Color(0xFF888888),
                maxLines = 1
            )
        }
    }
}
