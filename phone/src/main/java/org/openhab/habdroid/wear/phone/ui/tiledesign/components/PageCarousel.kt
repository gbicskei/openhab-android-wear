package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Two-row page selector:
 * 1. Title row: current page name (centered) + action icons (right)
 * 2. Chip row: Reorderable LazyRow with all pages as chips, selected page shown as a dot.
 *    Long-press a chip to drag and reorder pages. The "main" page cannot be moved.
 *
 * Full-page swipe handled by invisible pager in TileDesignScreen.
 */
@Composable
fun PageCarousel(
    pagerState: PagerState,
    pageLabels: List<String>,
    onDeletePage: (String) -> Unit,
    onRenamePage: (String, String) -> Unit,
    onReorderPages: (fromIndex: Int, toIndex: Int) -> Unit,
    pageUids: List<String>,
    modifier: Modifier = Modifier
) {
    val currentIndex = pagerState.currentPage
    val currentUid = pageUids.getOrElse(currentIndex) { "main" }
    val currentLabel = pageLabels.getOrElse(currentIndex) { "" }
    val isMain = currentUid == "main"
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1: Title + action icons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentLabel.take(14),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isMain) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Row 2: Reorderable LazyRow chips with dot replacing the selected item
        val lazyListState = rememberLazyListState()

        val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
            onReorderPages(from.index, to.index)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

        LaunchedEffect(currentIndex) {
            lazyListState.scrollToItem(
                index = (currentIndex - 1).coerceAtLeast(0)
            )
        }

        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(pageUids.size, key = { pageUids[it] }) { index ->
                val uid = pageUids[index]
                val isSelected = index == currentIndex
                val isMainChip = uid == "main"

                ReorderableItem(
                    reorderableLazyListState,
                    key = uid,
                    enabled = !isMainChip // main page cannot be reordered
                ) { isDragging ->
                    val elevation by animateDpAsState(
                        if (isDragging) 4.dp else 0.dp,
                        label = "chipElevation"
                    )

                    Surface(
                        shadowElevation = elevation,
                        tonalElevation = if (isDragging) 2.dp else 0.dp,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = if (isDragging) 0.9f else 0.5f
                        ),
                        modifier = Modifier
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureEnd
                                    )
                                }
                            )
                            .clickable {
                                if (!isSelected) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                horizontal = if (isSelected) 4.dp else 8.dp,
                                vertical = if (isSelected) 4.dp else 2.dp
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            } else {
                                Text(
                                    text = pageLabels[index],
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showRenameDialog) {
        RenamePageDialog(
            currentLabel = currentLabel,
            onConfirm = { newLabel ->
                onRenamePage(currentUid, newLabel)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Page") },
            text = { Text("Delete page \"$currentLabel\"? All items on this page will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePage(currentUid)
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenamePageDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newLabel by remember { mutableStateOf(currentLabel.take(14)) }
    val isValid = newLabel.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Page") },
        text = {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it.take(14) },
                label = { Text("Page Name") },
                supportingText = { Text("${newLabel.length}/14") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newLabel.trim()) }, enabled = isValid) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
