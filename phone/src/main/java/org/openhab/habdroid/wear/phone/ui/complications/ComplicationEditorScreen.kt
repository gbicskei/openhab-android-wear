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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationListDto
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationState
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplicationEditorScreen(
    onBack: () -> Unit,
    viewModel: ComplicationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val assigningSlot by viewModel.assigningSlot.collectAsState()
    val editingSlot by viewModel.editingSlot.collectAsState()
    val confirmingDelete by viewModel.confirmingDelete.collectAsState()
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
                title = {
                    Column {
                        Text("Complications")
                        val state = uiState
                        if (state is ComplicationUiState.Success) {
                            Text(
                                text = "${state.editor.configuredCount}/${ComplicationListDto.MAX_SLOTS} slots",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                val isFull = state.editor.configuredCount >= ComplicationListDto.MAX_SLOTS
                FloatingActionButton(
                    onClick = { if (!isFull) viewModel.addComplication() },
                    containerColor = if (isFull) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add complication",
                        tint = if (isFull) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
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
                val configuredSlots = state.editor.slots.entries
                    .filter { it.value != null }
                    .sortedBy { it.key }

                if (configuredSlots.isEmpty()) {
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
                                "Tap + to add an item for the watch face",
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

                        items(configuredSlots, key = { it.key }) { (slotNumber, complication) ->
                            ConfiguredSlotCard(
                                slotNumber = slotNumber,
                                complication = complication!!,
                                iconBaseUrl = state.iconBaseUrl,
                                iconAuthHeader = state.iconAuthHeader,
                                onClick = { viewModel.editSlot(slotNumber) }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) } // FAB clearance
                    }
                }

                // Item picker dialog (assigning an item to a slot)
                assigningSlot?.let {
                    ComplicationItemPickerDialog(
                        items = state.editor.allItems,
                        onItemSelected = { viewModel.confirmAssignment(it) },
                        onDismiss = { viewModel.dismissItemPicker() }
                    )
                }

                // Config sheet for editing an existing slot
                editingSlot?.let { slotNum ->
                    val complication = state.editor.slots[slotNum] ?: return@let
                    val matchingItem = state.editor.allItems
                        .find { it.name == complication.item }
                    val itemType = matchingItem?.type ?: ""
                    val itemState = matchingItem?.state ?: ""
                    ComplicationConfigSheet(
                        slotNumber = slotNum,
                        complication = complication,
                        itemType = itemType,
                        itemState = itemState,
                        iconBaseUrl = state.iconBaseUrl,
                        iconAuthHeader = state.iconAuthHeader,
                        onSave = { viewModel.updateSlot(slotNum, it) },
                        onDelete = { viewModel.clearSlot(slotNum) },
                        onDismiss = { viewModel.dismissEditor() }
                    )
                }

                // Delete confirmation dialog
                confirmingDelete?.let { slotNum ->
                    val state2 = state // avoid shadowing
                    val itemLabel = state2.editor.slots[slotNum]?.let { it.label.ifBlank { it.item } } ?: "Slot $slotNum"
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { viewModel.cancelDelete() },
                        title = { Text("Remove complication?") },
                        text = { Text("Remove \"$itemLabel\" from Slot $slotNum? Remaining slots will be renumbered.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.confirmDelete() }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { viewModel.cancelDelete() }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfiguredSlotCard(
    slotNumber: Int,
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
            // Slot number badge
            SlotBadge(slotNumber)

            Spacer(modifier = Modifier.width(12.dp))

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
                        modifier = Modifier.size(36.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }

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
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2x2 type indicator grid
            TypeIndicatorGrid(complication.supportedTypes)
        }
    }
}

@Composable
private fun SlotBadge(slotNumber: Int, dimmed: Boolean = false) {
    val alpha = if (dimmed) 0.4f else 1f
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slotNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
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

/**
 * 2x2 grid showing small icons for each ComplicationType.
 * Enabled types are tinted with primary color, disabled ones are very faint.
 * Layout:
 *   [ShortText]  [LongText]
 *   [RangedVal]  [MonoImage]
 */
@Composable
private fun TypeIndicatorGrid(supportedTypes: Set<ComplicationType>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TypeIcon(ComplicationType.SHORT_TEXT, supportedTypes)
            TypeIcon(ComplicationType.LONG_TEXT, supportedTypes)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TypeIcon(ComplicationType.RANGED_VALUE, supportedTypes)
            TypeIcon(ComplicationType.MONOCHROMATIC_IMAGE, supportedTypes)
        }
    }
}

@Composable
private fun TypeIcon(type: ComplicationType, enabledTypes: Set<ComplicationType>) {
    val enabled = type in enabledTypes
    val icon: ImageVector = when (type) {
        ComplicationType.SHORT_TEXT -> Icons.AutoMirrored.Outlined.ShortText
        ComplicationType.LONG_TEXT -> Icons.AutoMirrored.Outlined.Subject
        ComplicationType.RANGED_VALUE -> Icons.Outlined.DataUsage
        ComplicationType.MONOCHROMATIC_IMAGE -> Icons.Outlined.Image
    }
    Icon(
        imageVector = icon,
        contentDescription = type.displayName,
        modifier = Modifier.size(16.dp),
        tint = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        }
    )
}
