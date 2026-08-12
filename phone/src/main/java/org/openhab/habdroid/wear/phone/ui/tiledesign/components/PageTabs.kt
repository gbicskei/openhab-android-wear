package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Scrollable tab row for tile page management.
 * Long-press a tab to rename the page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageTabs(
    pageNames: List<String>,
    pageLabels: List<String>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onRenamePage: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // uid to label
    var showRenameDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // uid to current label

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp
    ) {
        pageLabels.forEachIndexed { index, label ->
            val pageName = pageNames[index]
            Tab(
                selected = index == selectedIndex,
                onClick = { onPageSelected(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.combinedClickable(
                            onClick = { onPageSelected(index) },
                            onLongClick = { showRenameDialog = pageName to label }
                        )
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (pageName != "main" && index == selectedIndex) {
                            IconButton(
                                onClick = { showDeleteDialog = pageName to label },
                                modifier = Modifier.size(20.dp).padding(start = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete page $pageName",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        // Add page tab
        Tab(
            selected = false,
            onClick = { showAddDialog = true },
            text = {
                Icon(Icons.Default.Add, contentDescription = "Add page", modifier = Modifier.size(18.dp))
            }
        )
    }

    if (showAddDialog) {
        AddPageDialog(
            onConfirm = { label ->
                onAddPage(label)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    showDeleteDialog?.let { (uid, pageLabel) ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Page") },
            text = { Text("Delete page \"$pageLabel\"? All items on this page will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePage(uid)
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    showRenameDialog?.let { (uid, currentLabel) ->
        RenamePageDialog(
            currentLabel = currentLabel,
            onConfirm = { newLabel ->
                onRenamePage(uid, newLabel)
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }
}

@Composable
private fun RenamePageDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newLabel by remember { mutableStateOf(currentLabel) }
    val isValid = newLabel.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Page") },
        text = {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                label = { Text("Page Name") },
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

@Composable
private fun AddPageDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    val isValid = label.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Page") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Page Label") },
                placeholder = { Text("e.g. Living Room, Security") },
                isError = label.isNotBlank() && !isValid,
                supportingText = if (label.isNotBlank() && !isValid) {
                    { Text("Label cannot be empty") }
                } else null,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim()) }, enabled = isValid) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
