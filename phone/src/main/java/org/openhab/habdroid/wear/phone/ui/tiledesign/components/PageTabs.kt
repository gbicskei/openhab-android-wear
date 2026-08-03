package org.openhab.habdroid.wear.phone.ui.tiledesign.components

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
 */
@Composable
fun PageTabs(
    pageNames: List<String>,
    pageLabels: List<String>,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onAddPage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (pageName != "main" && index == selectedIndex) {
                            IconButton(
                                onClick = { showDeleteDialog = pageName },
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
            existingPages = pageNames,
            onConfirm = { name ->
                onAddPage(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    showDeleteDialog?.let { pageName ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Page") },
            text = { Text("Delete page \"$pageName\"? All items on this page will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePage(pageName)
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
}

@Composable
private fun AddPageDialog(
    existingPages: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pageName by remember { mutableStateOf("") }
    val normalized = pageName.trim().lowercase().replace(" ", "_")
    val isValid = normalized.isNotBlank() && normalized !in existingPages
    val error = when {
        pageName.isNotBlank() && normalized.isBlank() -> "Name cannot be empty"
        normalized in existingPages -> "Page already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Page") },
        text = {
            OutlinedTextField(
                value = pageName,
                onValueChange = { pageName = it },
                label = { Text("Page Name") },
                placeholder = { Text("e.g. Security, Climate") },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(normalized) }, enabled = isValid) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
