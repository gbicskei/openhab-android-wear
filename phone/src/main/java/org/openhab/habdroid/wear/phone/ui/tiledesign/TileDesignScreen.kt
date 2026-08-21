package org.openhab.habdroid.wear.phone.ui.tiledesign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.ItemPickerDialog
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.LayoutSelector
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.PageCarousel
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
                val pageCount = editor.pages.size
                var showAddPageDialog by remember { mutableStateOf(false) }
                var showDuplicatePageDialog by remember { mutableStateOf(false) }

                // Pager state for page carousel and content swipe
                val pagerState = rememberPagerState(
                    initialPage = editor.currentPageIndex,
                    pageCount = { pageCount }
                )

                // Sync pager → ViewModel (user swiped)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page != editor.currentPageIndex) {
                            viewModel.selectPage(page)
                        }
                    }
                }

                // Sync ViewModel → pager (programmatic page change, e.g. add/delete)
                LaunchedEffect(editor.currentPageIndex, pageCount) {
                    if (pagerState.currentPage != editor.currentPageIndex) {
                        pagerState.animateScrollToPage(editor.currentPageIndex)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Invisible pager BEHIND content for full-page swipe
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0f),
                        beyondViewportPageCount = 0
                    ) { _ -> }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // Carousel page selector (includes action icons)
                    PageCarousel(
                        pagerState = pagerState,
                        pageLabels = editor.pageLabels,
                        pageUids = editor.pageNames,
                        onDeletePage = { viewModel.deletePage(it) },
                        onRenamePage = { uid, newLabel -> viewModel.renamePage(uid, newLabel) },
                        modifier = Modifier
                    )

                    val currentPage = editor.pages.getOrElse(pagerState.currentPage) { editor.currentPage }

                    androidx.compose.animation.AnimatedContent(
                        targetState = pagerState.currentPage,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(250)) +
                                slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth / 3 else -fullWidth / 3 }
                                )).togetherWith(
                                fadeOut(animationSpec = tween(200)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(250),
                                        targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth / 3 else fullWidth / 3 }
                                    )
                            )
                        },
                        label = "pageContentAnimation"
                    ) { pageIndex ->
                        val animatedPage = editor.pages.getOrElse(pageIndex) { editor.currentPage }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Layout selector
                        LayoutSelector(
                            selectedLayout = animatedPage.layout,
                            onLayoutSelected = { viewModel.onLayoutChanged(it) },
                            enabled = !state.isReadOnly,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Watch preview
                        WatchPreview(
                            layout = animatedPage.layout,
                            slots = animatedPage.slots,
                            onSlotTap = { position ->
                                val slot = animatedPage.slots.find { it.position == position }
                                if (slot == null || slot.isEmpty) {
                                    viewModel.onEmptySlotTapped(animatedPage.uid, position)
                                } else {
                                    viewModel.onFilledSlotTapped(animatedPage.uid, position)
                                }
                            },
                            iconBaseUrl = state.iconBaseUrl,
                            iconAuthHeader = state.iconAuthHeader,
                            themeColor = TileThemeColor.fromName(selectedTheme).color,
                            watchScreenWidthDp = state.watchScreenWidthDp?.toFloat() ?: 226f,
                            itemStates = itemStates,
                            allPages = editor.pages,
                            allItems = editor.allItems,
                            pageName = animatedPage.uid,
                            pageLabel = animatedPage.label.ifBlank {
                                animatedPage.uid.replaceFirstChar { c -> c.uppercase() }
                            },
                            modifier = Modifier.padding(16.dp)
                        )

                        Spacer(modifier = Modifier.height(60.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Duplicate on the left
                            androidx.compose.material3.FilledTonalIconButton(
                                onClick = { showDuplicatePageDialog = true },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate page",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = "${animatedPage.filledSlotCount}/${animatedPage.layout} slots used",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Add on the right
                            androidx.compose.material3.FilledTonalIconButton(
                                onClick = { showAddPageDialog = true },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add page",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (state.isReadOnly) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Read-only — configure local server to edit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                    } // AnimatedContent

                    // Theme selector at the bottom (outside animation — shared)
                    ThemeSelector(
                        selectedTheme = selectedTheme,
                        onThemeSelected = { viewModel.onThemeSelected(it) }
                    )
                    }

                }

                // Item picker
                if (showItemPicker) {
                    ItemPickerDialog(
                        items = editor.allItems,
                        pageNames = editor.pageNames,
                        currentPageUid = editor.currentPage.uid,
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
                        currentPageUid = pageUid,
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

                // Add page dialog
                if (showAddPageDialog) {
                    var newPageLabel by remember { mutableStateOf("") }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showAddPageDialog = false },
                        title = { Text("Add Page") },
                        text = {
                            androidx.compose.material3.OutlinedTextField(
                                value = newPageLabel,
                                onValueChange = { newPageLabel = it.take(14) },
                                label = { Text("Page Label") },
                                placeholder = { Text("e.g. Living Room") },
                                supportingText = { Text("${newPageLabel.length}/14") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (newPageLabel.trim().isNotBlank()) {
                                        viewModel.addPage(newPageLabel.trim())
                                        showAddPageDialog = false
                                    }
                                },
                                enabled = newPageLabel.trim().isNotBlank()
                            ) { Text("Add") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddPageDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showDuplicatePageDialog) {
                    val dupPage = editor.pages.getOrElse(pagerState.currentPage) { editor.currentPage }
                    val defaultLabel = "${dupPage.label.ifBlank { dupPage.uid }} (copy)"
                    var dupLabel by remember { mutableStateOf(defaultLabel) }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDuplicatePageDialog = false },
                        title = { Text("Save As...") },
                        text = {
                            androidx.compose.material3.OutlinedTextField(
                                value = dupLabel,
                                onValueChange = { dupLabel = it.take(14) },
                                label = { Text("Page Label") },
                                supportingText = { Text("${dupLabel.length}/14") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (dupLabel.trim().isNotBlank()) {
                                        viewModel.duplicatePage(dupPage.uid, dupLabel.trim())
                                        showDuplicatePageDialog = false
                                    }
                                },
                                enabled = dupLabel.trim().isNotBlank()
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDuplicatePageDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
