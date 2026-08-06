package org.openhab.habdroid.wear.phone.ui.tiledesign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.ItemPickerDialog
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.LayoutSelector
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.PageTabs
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.ThemeSelector
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.TileConfigSheet
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.TileThemeColor
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.WatchPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileDesignScreen(
    onBack: () -> Unit,
    viewModel: TileDesignViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showItemPicker by viewModel.showItemPicker.collectAsState()
    val showConfigSheet by viewModel.showConfigSheet.collectAsState()
    val editingSlot by viewModel.editingSlot.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val itemStates by viewModel.itemStates.collectAsState()

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
                title = { Text("Tile Design") },
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
                    IconButton(onClick = { viewModel.loadTileConfig() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {}
    ) { padding ->
        when (val state = uiState) {
            is TileDesignUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading tile configuration...")
                    }
                }
            }

            is TileDesignUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadTileConfig() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is TileDesignUiState.Success -> {
                val editor = state.editor
                val currentPage = editor.currentPage

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Page tabs
                    PageTabs(
                        pageNames = editor.pageNames,
                        pageLabels = editor.pageLabels,
                        selectedIndex = editor.currentPageIndex,
                        onPageSelected = { viewModel.selectPage(it) },
                        onAddPage = { viewModel.addPage(it) },
                        onDeletePage = { viewModel.deletePage(it) },
                        onRenamePage = { uid, newLabel -> viewModel.renamePage(uid, newLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Layout selector (dice pictograms)
                    LayoutSelector(
                        selectedLayout = currentPage.layout,
                        onLayoutSelected = { viewModel.onLayoutChanged(it) },
                        enabled = !state.isReadOnly,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Watch preview
                    WatchPreview(
                        layout = currentPage.layout,
                        slots = currentPage.slots,
                        onSlotTap = { position ->
                            val slot = currentPage.slots.find { it.position == position }
                            if (slot == null || slot.isEmpty) {
                                viewModel.onEmptySlotTapped(currentPage.uid, position)
                            } else {
                                viewModel.onFilledSlotTapped(currentPage.uid, position)
                            }
                        },
                        iconBaseUrl = state.iconBaseUrl,
                        iconAuthHeader = state.iconAuthHeader,
                        themeColor = TileThemeColor.fromName(selectedTheme).color,
                        watchScreenWidthDp = state.watchScreenWidthDp?.toFloat() ?: 226f,
                        itemStates = itemStates,
                        allPages = editor.pages,
                        allItems = editor.allItems,
                        pageName = currentPage.uid,
                        pageLabel = currentPage.label.ifBlank { currentPage.uid.replaceFirstChar { c -> c.uppercase() } },
                        modifier = Modifier.padding(16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${currentPage.filledSlotCount}/${currentPage.layout} slots used",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.isReadOnly) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Read-only — configure local server to edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap slot to configure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Theme selector
                    ThemeSelector(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { viewModel.onThemeSelected(it) }
                    )
                }

                // Item picker
                if (showItemPicker) {
                    ItemPickerDialog(
                        items = editor.allItems,
                        pageNames = editor.pageNames,
                        onItemSelected = { viewModel.assignItemToSlot(it) },
                        onNavigateSelected = { targetPage, label, icon ->
                            viewModel.assignNavigationToSlot(targetPage, label, icon)
                        },
                        onDismiss = { viewModel.dismissItemPicker() }
                    )
                }

                // Config sheet
                if (showConfigSheet) {
                    val (pageUid, position) = editingSlot ?: return@Scaffold
                    val slot = editor.pages
                        .find { it.uid == pageUid }
                        ?.slots
                        ?.find { it.position == position } ?: return@Scaffold

                    TileConfigSheet(
                        slot = slot,
                        pageNames = editor.pageNames,
                        layoutCount = editor.currentPage.layout,
                        iconBaseUrl = state.iconBaseUrl,
                        iconAuthHeader = state.iconAuthHeader,
                        onSave = { viewModel.updateSlotConfig(it) },
                        onPositionSwap = { newPos -> viewModel.swapSlotPosition(pageUid, position, newPos) },
                        onDelete = { viewModel.removeSlot(pageUid, position) },
                        onDismiss = { viewModel.dismissConfigSheet() },
                        allItems = editor.allItems
                    )
                }
            }
        }
    }
}
