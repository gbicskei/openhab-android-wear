package org.openhab.habdroid.wear.phone.ui.tiledesign.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.PhoneItem
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.SlotAction
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.StateDisplay
import org.openhab.habdroid.wear.phone.ui.tiledesign.model.TileSlotState

/**
 * Bottom sheet for configuring a tile slot's properties.
 * Organized into card-based sections with proper headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileConfigSheet(
    slot: TileSlotState,
    pageNames: List<String>,
    currentPageUid: String = "",
    layoutCount: Int,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onSave: (TileSlotState) -> Unit,
    onPositionSwap: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    allItems: List<PhoneItem> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // State
    var label by remember(slot) { mutableStateOf(slot.label ?: "") }
    var icon by remember(slot) { mutableStateOf(slot.icon ?: "") }
    var stateDisplay by remember(slot) { mutableStateOf(slot.stateDisplay) }
    var action by remember(slot) { mutableStateOf(slot.action) }
    var actionCommand by remember(slot) { mutableStateOf(slot.actionCommand ?: "") }
    var actionItem by remember(slot) { mutableStateOf(slot.actionItem ?: "") }
    var stateItem by remember(slot) { mutableStateOf(slot.stateItem ?: "") }
    var invertState by remember(slot) { mutableStateOf(slot.invertState) }
    var actionConfirmation by remember(slot) { mutableStateOf(slot.actionConfirmation) }
    var aggregateState by remember(slot) { mutableStateOf(slot.aggregateState) }
    var doubleTapItem by remember(slot) { mutableStateOf(slot.doubleTapItem ?: "") }
    var doubleTapAction by remember(slot) { mutableStateOf(slot.doubleTapAction) }
    var doubleTapCommand by remember(slot) { mutableStateOf(slot.doubleTapCommand ?: "") }
    var doubleTapConfirmation by remember(slot) { mutableStateOf(slot.doubleTapConfirmation) }
    var doubleTapStateDisplay by remember(slot) { mutableStateOf(slot.doubleTapStateDisplay) }
    var complementAction by remember(slot) { mutableStateOf(slot.complementAction) }
    var targetPage by remember(slot) {
        mutableStateOf(
            (slot.action as? SlotAction.Navigate)?.targetPage ?: pageNames.firstOrNull() ?: "main"
        )
    }
    var showIconPicker by remember { mutableStateOf(false) }

    val primaryItemType = allItems.find { it.name == slot.item }?.type ?: ""
    val primaryIsToggleable = primaryItemType in listOf("Switch", "Dimmer", "Color", "Rollershutter", "Group")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Slot ${slot.position}",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (slot.item != null) {
                    Text(
                        text = slot.item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Position ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                for (pos in 1..layoutCount) {
                    val isCurrent = pos == slot.position
                    Surface(
                        onClick = { if (!isCurrent) onPositionSwap(pos) },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = pos.toString(),
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // ── Appearance Section ──
            SectionCard(title = "Appearance") {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text(slot.item ?: "Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon") },
                    placeholder = { Text("Tap to choose") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIconPicker = true },
                    trailingIcon = {
                        IconButton(onClick = { showIconPicker = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Browse icons")
                        }
                    }
                )

                if (action !is SlotAction.Navigate) {
                    Text(
                        text = "State Display",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = stateDisplay == StateDisplay.COLOR,
                            onClick = { stateDisplay = StateDisplay.COLOR },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) { Text("Color") }
                        SegmentedButton(
                            selected = stateDisplay == StateDisplay.VALUE,
                            onClick = { stateDisplay = StateDisplay.VALUE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) { Text("Value") }
                        SegmentedButton(
                            selected = stateDisplay == StateDisplay.NONE,
                            onClick = { stateDisplay = StateDisplay.NONE },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) { Text("None") }
                    }
                }
            }

            // ── Action Section ──
            SectionCard(title = "Action") {
                ActionDropdown(
                    action = action,
                    showToggle = primaryIsToggleable,
                    onActionChange = { action = it }
                )

                if (action is SlotAction.Command) {
                    OutlinedTextField(
                        value = actionCommand,
                        onValueChange = { actionCommand = it },
                        label = { Text("Command") },
                        placeholder = { Text("ON") },
                        supportingText = { Text("Fixed command sent on tap") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (action is SlotAction.Navigate) {
                    PageTargetDropdown(
                        targetPage = targetPage,
                        pageNames = pageNames.filter { it != "complications" && it != currentPageUid },
                        onPageChange = { targetPage = it }
                    )
                }

                ListItem(
                    headlineContent = { Text("Confirmation") },
                    supportingContent = { Text("Ask before executing") },
                    trailingContent = {
                        Switch(checked = actionConfirmation, onCheckedChange = { actionConfirmation = it })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (action is SlotAction.Navigate) {
                    ListItem(
                        headlineContent = { Text("Aggregate State") },
                        supportingContent = { Text("Active if any target page item is on") },
                        trailingContent = {
                            Switch(checked = aggregateState, onCheckedChange = { aggregateState = it })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // ── Advanced Section (item overrides + invert) ──
            if (action !is SlotAction.Navigate) {
                SectionCard(title = "Advanced") {
                    // Action Item
                    var showActionItemPicker by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Action Item") },
                        supportingContent = {
                            Text(actionItem.ifBlank { "Same as primary" })
                        },
                        trailingContent = {
                            if (actionItem.isNotBlank()) {
                                IconButton(onClick = { actionItem = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = { showActionItemPicker = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Pick")
                                }
                            }
                        },
                        modifier = Modifier.clickable { showActionItemPicker = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showActionItemPicker) {
                        ItemPickerDialog(
                            items = allItems,
                            pageNames = emptyList(),
                            currentPageUid = currentPageUid,
                            onItemSelected = { selected ->
                                actionItem = selected.name
                                showActionItemPicker = false
                            },
                            onNavigateSelected = { _, _, _ -> },
                            onDismiss = { showActionItemPicker = false }
                        )
                    }

                    // State Item
                    var showStateItemPicker by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("State Item") },
                        supportingContent = {
                            Text(stateItem.ifBlank { "Same as primary" })
                        },
                        trailingContent = {
                            if (stateItem.isNotBlank()) {
                                IconButton(onClick = { stateItem = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = { showStateItemPicker = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Pick")
                                }
                            }
                        },
                        modifier = Modifier.clickable { showStateItemPicker = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showStateItemPicker) {
                        ItemPickerDialog(
                            items = allItems,
                            pageNames = emptyList(),
                            currentPageUid = currentPageUid,
                            onItemSelected = { selected ->
                                stateItem = selected.name
                                showStateItemPicker = false
                            },
                            onNavigateSelected = { _, _, _ -> },
                            onDismiss = { showStateItemPicker = false }
                        )
                    }

                    // Invert State
                    ListItem(
                        headlineContent = { Text("Invert State") },
                        supportingContent = { Text("Flip active/inactive display") },
                        trailingContent = {
                            Switch(checked = invertState, onCheckedChange = { invertState = it })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // ── Double Tap Section ──
            if (action !is SlotAction.Navigate) {
                val supportsComplement = primaryItemType in listOf("Dimmer", "Color", "Rollershutter")

                SectionCard(title = "Double Tap") {
                    // Complement action toggle
                    if (supportsComplement && doubleTapItem.isBlank()) {
                        ListItem(
                            headlineContent = { Text("Complement Action") },
                            supportingContent = {
                                Text(
                                    when (action) {
                                        is SlotAction.Toggle -> "Double-tap opens control"
                                        else -> "Double-tap toggles ON/OFF"
                                    }
                                )
                            },
                            trailingContent = {
                                Switch(checked = complementAction, onCheckedChange = { complementAction = it })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    // Double-tap item picker
                    var showDoubleTapItemPicker by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Double Tap Item") },
                        supportingContent = {
                            Text(doubleTapItem.ifBlank { "None (use complement or disable)" })
                        },
                        trailingContent = {
                            if (doubleTapItem.isNotBlank()) {
                                IconButton(onClick = { doubleTapItem = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                                }
                            } else {
                                IconButton(onClick = { showDoubleTapItemPicker = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Pick")
                                }
                            }
                        },
                        modifier = Modifier.clickable { showDoubleTapItemPicker = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showDoubleTapItemPicker) {
                        ItemPickerDialog(
                            items = allItems,
                            pageNames = emptyList(),
                            currentPageUid = currentPageUid,
                            onItemSelected = { selected ->
                                doubleTapItem = selected.name
                                showDoubleTapItemPicker = false
                            },
                            onNavigateSelected = { _, _, _ -> },
                            onDismiss = { showDoubleTapItemPicker = false }
                        )
                    }

                    // Extra double-tap config when item is set
                    if (doubleTapItem.isNotBlank()) {
                        val dblTapItemType = allItems.find { it.name == doubleTapItem }?.type ?: ""
                        val dblTapIsToggleable = dblTapItemType in listOf("Switch", "Dimmer", "Color", "Rollershutter", "Group")

                        DoubleTapActionDropdown(
                            action = doubleTapAction,
                            showToggle = dblTapIsToggleable,
                            onActionChange = { doubleTapAction = it }
                        )

                        if (doubleTapAction is SlotAction.Command) {
                            OutlinedTextField(
                                value = doubleTapCommand,
                                onValueChange = { doubleTapCommand = it },
                                label = { Text("Double Tap Command") },
                                placeholder = { Text("ON") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ListItem(
                            headlineContent = { Text("Confirmation") },
                            supportingContent = { Text("Ask before double-tap action") },
                            trailingContent = {
                                Switch(
                                    checked = doubleTapConfirmation,
                                    onCheckedChange = { doubleTapConfirmation = it }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        Text(
                            text = "State Display",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        val availableDblOptions = buildList {
                            add(StateDisplay.NONE)
                            if (stateDisplay != StateDisplay.COLOR) add(StateDisplay.COLOR)
                            if (stateDisplay != StateDisplay.VALUE) add(StateDisplay.VALUE)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            availableDblOptions.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = doubleTapStateDisplay == option,
                                    onClick = { doubleTapStateDisplay = option },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = availableDblOptions.size
                                    )
                                ) {
                                    Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                        }
                    }
                }
            }

            // ── Footer Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Remove", modifier = Modifier.padding(start = 4.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val finalAction = when (action) {
                            is SlotAction.Navigate -> SlotAction.Navigate(targetPage)
                            else -> action
                        }
                        onSave(
                            slot.copy(
                                label = label.ifBlank { null },
                                icon = icon.ifBlank { null },
                                stateDisplay = stateDisplay,
                                action = finalAction,
                                actionCommand = actionCommand.ifBlank { null },
                                actionItem = actionItem.ifBlank { null },
                                stateItem = stateItem.ifBlank { null },
                                invertState = invertState,
                                actionConfirmation = actionConfirmation,
                                aggregateState = aggregateState,
                                doubleTapItem = doubleTapItem.ifBlank { null },
                                doubleTapAction = if (doubleTapItem.isBlank()) null else doubleTapAction,
                                doubleTapCommand = doubleTapCommand.ifBlank { null },
                                doubleTapConfirmation = if (doubleTapItem.isBlank()) false else doubleTapConfirmation,
                                doubleTapStateDisplay = if (doubleTapItem.isBlank()) StateDisplay.NONE else doubleTapStateDisplay,
                                complementAction = if (doubleTapItem.isNotBlank()) false else complementAction
                            )
                        )
                    }
                ) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Icon picker overlay
    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = icon,
            iconBaseUrl = iconBaseUrl,
            iconAuthHeader = iconAuthHeader,
            onIconSelected = { selectedIcon ->
                icon = selectedIcon
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}

// ─── Section Card ───

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

// ─── Dropdowns ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(
    action: SlotAction,
    showToggle: Boolean = true,
    onActionChange: (SlotAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val actionLabel = when (action) {
        is SlotAction.Auto -> "Auto (detect from item)"
        is SlotAction.Toggle -> "Toggle"
        is SlotAction.Command -> "Fixed Command"
        is SlotAction.Navigate -> "Navigate to Page"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = actionLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Auto (detect from item)") },
                onClick = { onActionChange(SlotAction.Auto); expanded = false }
            )
            if (showToggle) {
                DropdownMenuItem(
                    text = { Text("Toggle") },
                    onClick = { onActionChange(SlotAction.Toggle); expanded = false }
                )
            }
            DropdownMenuItem(
                text = { Text("Fixed Command") },
                onClick = { onActionChange(SlotAction.Command); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Navigate to Page") },
                onClick = { onActionChange(SlotAction.Navigate("main")); expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTargetDropdown(
    targetPage: String,
    pageNames: List<String>,
    onPageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = targetPage,
            onValueChange = {},
            readOnly = true,
            label = { Text("Target Page") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pageNames.forEach { page ->
                DropdownMenuItem(
                    text = { Text(page) },
                    onClick = { onPageChange(page); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoubleTapActionDropdown(
    action: SlotAction?,
    showToggle: Boolean,
    onActionChange: (SlotAction?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val actionLabel = when (action) {
        null -> "Auto (detect from item)"
        is SlotAction.Toggle -> "Toggle"
        is SlotAction.Command -> "Fixed Command"
        else -> "Auto"
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = actionLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Action") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Auto (detect from item)") },
                onClick = { onActionChange(null); expanded = false }
            )
            if (showToggle) {
                DropdownMenuItem(
                    text = { Text("Toggle") },
                    onClick = { onActionChange(SlotAction.Toggle); expanded = false }
                )
            }
            DropdownMenuItem(
                text = { Text("Fixed Command") },
                onClick = { onActionChange(SlotAction.Command); expanded = false }
            )
        }
    }
}
