package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Icon picker bottom sheet with search and source selector.
 *
 * Three icon sources:
 * - MDI: Material Design Icons via Iconify API (stored as "iconify:mdi:{name}")
 * - Material: Google Material Symbols (stored as "material:{name}")
 * - openHAB: Classic openHAB icons from server (stored as plain "{name}")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerDialog(
    currentIcon: String,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember {
        mutableStateOf(
            when {
                currentIcon.startsWith("iconify:") -> IconSource.MDI
                currentIcon.startsWith("material:") -> IconSource.MATERIAL
                else -> IconSource.MDI
            }
        )
    }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Debounced search
    LaunchedEffect(searchQuery, selectedSource) {
        when (selectedSource) {
            IconSource.MDI -> {
                if (searchQuery.length >= 2) {
                    isSearching = true
                    delay(400)
                    searchResults = searchIconify(searchQuery, "mdi")
                    isSearching = false
                } else if (searchQuery.isEmpty()) {
                    searchResults = DEFAULT_MDI_ICONS
                    isSearching = false
                }
            }
            IconSource.MATERIAL -> {
                if (searchQuery.length >= 2) {
                    isSearching = true
                    delay(400)
                    searchResults = searchIconify(searchQuery, "material-symbols")
                    isSearching = false
                } else if (searchQuery.isEmpty()) {
                    searchResults = DEFAULT_MATERIAL_ICONS
                    isSearching = false
                }
            }
            IconSource.OPENHAB -> {
                val filtered = if (searchQuery.isBlank()) OPENHAB_ICONS
                else OPENHAB_ICONS.filter { it.contains(searchQuery, ignoreCase = true) }
                searchResults = filtered
                isSearching = false
            }
        }
    }

    // Load defaults on first open
    LaunchedEffect(Unit) {
        if (searchResults.isEmpty()) {
            searchResults = DEFAULT_MDI_ICONS
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Choose Icon", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search icons...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Source selector chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSource == IconSource.MDI,
                    onClick = { selectedSource = IconSource.MDI; searchResults = emptyList() },
                    label = { Text("MDI") }
                )
                FilterChip(
                    selected = selectedSource == IconSource.MATERIAL,
                    onClick = { selectedSource = IconSource.MATERIAL; searchResults = emptyList() },
                    label = { Text("Material") }
                )
                FilterChip(
                    selected = selectedSource == IconSource.OPENHAB,
                    onClick = { selectedSource = IconSource.OPENHAB; searchResults = emptyList() },
                    label = { Text("openHAB") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Icon grid
            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                searchResults.isEmpty() && searchQuery.length >= 2 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No icons found. Try a different term.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    IconGrid(
                        icons = searchResults,
                        source = selectedSource,
                        iconBaseUrl = iconBaseUrl,
                        iconAuthHeader = iconAuthHeader,
                        onSelect = { iconName ->
                            val formatted = when (selectedSource) {
                                IconSource.MDI -> "iconify:mdi:$iconName"
                                IconSource.MATERIAL -> "material:$iconName"
                                IconSource.OPENHAB -> iconName
                            }
                            onIconSelected(formatted)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IconGrid(
    icons: List<String>,
    source: IconSource,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(72.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(icons) { iconName ->
            val (url, needsAuth) = when (source) {
                IconSource.MDI -> {
                    "https://api.iconify.design/mdi/$iconName.svg" to false
                }
                IconSource.MATERIAL -> {
                    "https://api.iconify.design/material-symbols/$iconName.svg" to false
                }
                IconSource.OPENHAB -> {
                    val u = if (iconBaseUrl != null) "${iconBaseUrl.trimEnd('/')}/icon/$iconName?format=svg" else null
                    u to true
                }
            }

            IconCell(
                iconUrl = url,
                label = iconName.replace("-", " ").replace("_", " "),
                needsAuth = needsAuth,
                authHeader = iconAuthHeader,
                onClick = { onSelect(iconName) }
            )
        }
    }
}

@Composable
private fun IconCell(
    iconUrl: String?,
    label: String,
    needsAuth: Boolean,
    authHeader: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .apply {
                        if (needsAuth && authHeader != null) {
                            addHeader("Authorization", authHeader)
                        }
                    }
                    .build(),
                contentDescription = label,
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class IconSource { MDI, MATERIAL, OPENHAB }

/**
 * Search icons via Iconify API.
 * @param query Search term
 * @param prefix Icon set prefix ("mdi" or "material-symbols")
 * @return List of icon names without the prefix
 */
private suspend fun searchIconify(query: String, prefix: String): List<String> = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.iconify.design/search?query=$query&prefix=$prefix&limit=60"
        val response = URL(url).readText()
        val json = JSONObject(response)
        val icons = json.optJSONArray("icons") ?: return@withContext emptyList()
        (0 until icons.length()).map { i ->
            icons.getString(i).removePrefix("$prefix:")
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Popular MDI icons shown by default */
private val DEFAULT_MDI_ICONS = listOf(
    "lightbulb", "lightbulb-group", "lightbulb-outline",
    "lamp", "ceiling-light", "floor-lamp", "led-strip",
    "thermostat", "thermometer", "fan", "snowflake", "fire",
    "door", "door-open", "gate", "gate-open", "garage",
    "lock", "lock-open", "shield-home", "shield-lock",
    "motion-sensor", "eye", "cctv",
    "power-plug", "power-socket-eu", "washing-machine",
    "speaker", "television", "cast", "music",
    "water", "water-pump", "sprinkler", "flower",
    "home", "home-outline", "exit-run",
    "weather-sunny", "weather-night", "cloud",
    "battery", "solar-power", "lightning-bolt",
    "blinds", "blinds-open", "window-open", "window-closed",
    "bell", "alarm-light", "alert",
    "car", "garage-variant", "ev-station",
    "movie-open", "sofa", "bed", "shower"
)

/** Popular Material Symbols shown by default */
private val DEFAULT_MATERIAL_ICONS = listOf(
    "light", "lightbulb", "lamp", "fluorescent",
    "thermostat", "ac-unit", "mode-heat", "mode-fan",
    "door-front", "door-back", "garage",
    "lock", "lock-open", "shield", "security",
    "sensors", "motion-sensor-active", "visibility",
    "power", "outlet", "bolt",
    "speaker", "tv", "cast", "music-note",
    "water-drop", "water", "sprinkler",
    "home", "cottage", "apartment",
    "sunny", "nights-stay", "cloud",
    "battery-full", "solar-power", "electric-bolt",
    "blinds", "window", "curtains",
    "notifications", "alarm", "warning",
    "directions-car", "garage-home", "ev-station",
    "movie", "weekend", "bed", "shower"
)

/** Common openHAB classic icon names */
private val OPENHAB_ICONS = listOf(
    "light", "lightbulb", "lamp",
    "switch", "dimmer",
    "temperature", "temperature_cold", "temperature_hot",
    "heating", "radiator", "fan", "climate",
    "humidity",
    "door", "frontdoor", "garagedoor",
    "window", "blinds", "rollershutter",
    "lock", "alarm", "shield", "siren",
    "motion", "presence", "camera",
    "energy", "poweroutlet",
    "battery", "batterylevel", "lowbattery",
    "water", "flow", "rain", "faucet",
    "garden", "lawnmower",
    "sun", "sun_clouds", "moon", "clouds", "wind",
    "snow", "storm",
    "mediacontrol", "receiver", "screen", "soundvolume",
    "player", "projector",
    "network", "wifi",
    "time", "clock", "calendar",
    "house", "garage", "terrace", "corridor",
    "bedroom", "bath", "kitchen", "office",
    "boy_1", "girl_1", "man_1", "woman_1",
    "car", "vacation",
    "fire", "smoke", "gas",
    "colorwheel", "colorlight", "rgb",
    "settings", "chart", "text"
)
