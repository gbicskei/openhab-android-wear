package org.openhab.habdroid.wear.phone.ui.complications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationItem
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationState
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.IconPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplicationEditorScreen(
    onBack: () -> Unit,
    viewModel: ComplicationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showItemPicker by viewModel.showItemPicker.collectAsState()
    val editingIndex by viewModel.editingIndex.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Complications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = { viewModel.importFromMetadata() }) {
                        Icon(Icons.Default.Download, contentDescription = "Import from metadata")
                    }
                    IconButton(onClick = { viewModel.loadComplications() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val state = uiState
            if (state is ComplicationUiState.Success && !state.isReadOnly) {
                FloatingActionButton(onClick = { viewModel.showAddComplication() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add complication")
                }
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is ComplicationUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading complications...")
                    }
                }
            }

            is ComplicationUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ComplicationUiState.Success -> {
                if (state.editor.complications.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No complications configured",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap + to add items for the watch face",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        itemsIndexed(state.editor.complications) { index, complication ->
                            ComplicationCard(
                                complication = complication,
                                iconBaseUrl = state.iconBaseUrl,
                                iconAuthHeader = state.iconAuthHeader,
                                onClick = { viewModel.editComplication(index) }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) } // FAB clearance
                    }
                }

                // Item picker dialog
                if (showItemPicker) {
                    ComplicationItemPickerDialog(
                        items = state.editor.allItems,
                        onItemSelected = { viewModel.addComplication(it) },
                        onDismiss = { viewModel.dismissItemPicker() }
                    )
                }

                // Config sheet for editing
                editingIndex?.let { index ->
                    val complication = state.editor.complications.getOrNull(index) ?: return@let
                    val itemType = state.editor.allItems
                        .find { it.name == complication.item }?.type ?: ""
                    ComplicationConfigSheet(
                        complication = complication,
                        itemType = itemType,
                        iconBaseUrl = state.iconBaseUrl,
                        iconAuthHeader = state.iconAuthHeader,
                        onSave = { viewModel.updateComplication(index, it) },
                        onDelete = { viewModel.removeComplication(index) },
                        onDismiss = { viewModel.dismissEditor() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComplicationCard(
    complication: ComplicationState,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon preview
            val iconRef = complication.icon.ifBlank { null }
            if (iconRef != null) {
                val iconUrl = resolveIconUrl(iconRef, iconBaseUrl)
                if (iconUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(iconUrl)
                            .decoderFactory(SvgDecoder.Factory())
                            .crossfade(true)
                            .apply {
                                if (!iconRef.startsWith("iconify:") && !iconRef.startsWith("material:") && iconAuthHeader != null) {
                                    addHeader("Authorization", iconAuthHeader)
                                }
                            }
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = complication.label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = complication.label.ifBlank { complication.item },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = complication.item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Show configured types
                val types = buildList {
                    if (complication.shortText.isConfigured) add("Short")
                    if (complication.longText.isConfigured) add("Long")
                    if (complication.rangedValue.isConfigured) add("Range")
                    if (complication.monochromaticImage.isConfigured) add("Icon")
                }
                if (types.isNotEmpty()) {
                    Text(
                        text = types.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun resolveIconUrl(iconRef: String, iconBaseUrl: String?): String? {
    return when {
        iconRef.startsWith("iconify:") -> {
            val parts = iconRef.removePrefix("iconify:").split(":", limit = 2)
            if (parts.size == 2) "https://api.iconify.design/${parts[0]}/${parts[1]}.svg" else null
        }
        iconRef.startsWith("material:") -> {
            val name = iconRef.removePrefix("material:").replace("-", "_")
            "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/$name/default/48px.svg"
        }
        iconBaseUrl != null -> {
            "${iconBaseUrl.trimEnd('/')}/icon/$iconRef?format=svg"
        }
        else -> null
    }
}
