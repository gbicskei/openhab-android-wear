package org.openhab.habdroid.wear.phone.ui.complications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationState
import org.openhab.habdroid.wear.phone.ui.complications.model.ComplicationType
import org.openhab.habdroid.wear.phone.ui.complications.model.LongTextConfig
import org.openhab.habdroid.wear.phone.ui.complications.model.MonochromaticImageConfig
import org.openhab.habdroid.wear.phone.ui.complications.model.RangedValueConfig
import org.openhab.habdroid.wear.phone.ui.complications.model.ShortTextConfig
import org.openhab.habdroid.wear.phone.ui.tiledesign.components.IconPickerDialog

/**
 * Bottom sheet for configuring a complication's per-type settings.
 * Expandable sections for each ComplicationType following Google's naming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplicationConfigSheet(
    slotNumber: Int,
    complication: ComplicationState,
    itemType: String,
    itemState: String,
    iconBaseUrl: String?,
    iconAuthHeader: String?,
    onSave: (ComplicationState) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Top-level fields
    var label by remember(complication) { mutableStateOf(complication.label) }
    var icon by remember(complication) { mutableStateOf(complication.icon) }

    // SHORT_TEXT
    var shortTextTitle by remember(complication) { mutableStateOf(complication.shortText.title) }
    var shortTextText by remember(complication) { mutableStateOf(complication.shortText.text) }

    // LONG_TEXT
    var longTextTitle by remember(complication) { mutableStateOf(complication.longText.title) }
    var longTextText by remember(complication) { mutableStateOf(complication.longText.text) }

    // RANGED_VALUE
    var rangedTitle by remember(complication) { mutableStateOf(complication.rangedValue.title) }
    var rangedText by remember(complication) { mutableStateOf(complication.rangedValue.text) }
    var rangedMin by remember(complication) { mutableStateOf(complication.rangedValue.min?.toString() ?: "") }
    var rangedMax by remember(complication) { mutableStateOf(complication.rangedValue.max?.toString() ?: "") }

    // MONOCHROMATIC_IMAGE
    var monoImage by remember(complication) { mutableStateOf(complication.monochromaticImage.monochromaticImage) }
    var monoActiveIcon by remember(complication) { mutableStateOf(complication.monochromaticImage.activeIcon) }
    var monoInactiveIcon by remember(complication) { mutableStateOf(complication.monochromaticImage.inactiveIcon) }

    // Section expand states
    var expandShortText by remember { mutableStateOf(complication.shortText.isConfigured) }
    var expandLongText by remember { mutableStateOf(complication.longText.isConfigured) }
    var expandRanged by remember { mutableStateOf(complication.rangedValue.isConfigured) }
    var expandMonoImage by remember { mutableStateOf(complication.monochromaticImage.isConfigured) }

    // Icon picker
    var showIconPicker by remember { mutableStateOf(false) }
    var iconPickerTarget by remember { mutableStateOf("") } // "icon", "mono", "active", "inactive"

    // Type-based section filtering
    val supportsRangedValue = itemType.startsWith("Number") ||
        itemType == "Dimmer" ||
        itemType == "Color" ||
        (itemType.startsWith("Group") && itemType.contains(":"))
    val supportsMonochromaticImage = itemType != "String"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Slot $slotNumber — Configure",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Item: ${complication.item}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ─── Common Fields ───

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                placeholder = { Text("Display name (fallback for title)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = icon,
                onValueChange = { icon = it },
                label = { Text("Icon") },
                placeholder = { Text("Shared icon for all types") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { iconPickerTarget = "icon"; showIconPicker = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Browse icons")
                    }
                }
            )

            HorizontalDivider()

            // ─── SHORT_TEXT Section ───

            if (ComplicationType.SHORT_TEXT in ComplicationType.defaultsForItemType(itemType)) {
                SectionHeader(
                    title = "SHORT_TEXT",
                    subtitle = "text max 7 chars, title max 7 chars",
                    expanded = expandShortText,
                    onToggle = { expandShortText = !expandShortText }
                )
                AnimatedVisibility(visible = expandShortText) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = shortTextTitle,
                            onValueChange = { if (it.length <= ShortTextConfig.MAX_TITLE_LENGTH) shortTextTitle = it },
                            label = { Text("title") },
                            placeholder = { Text("Label") },
                            supportingText = { Text("${shortTextTitle.length}/${ShortTextConfig.MAX_TITLE_LENGTH}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PatternTextField(
                            value = shortTextText,
                            onValueChange = { if (it.length <= ShortTextConfig.MAX_TEXT_LENGTH) shortTextText = it },
                            label = "text",
                            maxLength = ShortTextConfig.MAX_TEXT_LENGTH,
                            placeholder = "%.0f°C",
                            itemState = itemState
                        )
                    }
                }
            }

            // ─── LONG_TEXT Section ───

            if (ComplicationType.LONG_TEXT in ComplicationType.defaultsForItemType(itemType)) {
                SectionHeader(
                    title = "LONG_TEXT",
                    subtitle = "no strict char limit",
                    expanded = expandLongText,
                    onToggle = { expandLongText = !expandLongText }
                )
                AnimatedVisibility(visible = expandLongText) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = longTextTitle,
                            onValueChange = { longTextTitle = it },
                            label = { Text("title") },
                            placeholder = { Text("Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PatternTextField(
                            value = longTextText,
                            onValueChange = { longTextText = it },
                            label = "text",
                            maxLength = null,
                            placeholder = "Temperature: %.1f °C",
                            itemState = itemState
                        )
                    }
                }
            }

            // ─── RANGED_VALUE Section ───

            if (supportsRangedValue) {
                SectionHeader(
                    title = "RANGED_VALUE",
                    subtitle = "value from item state, text/title max 7 chars",
                    expanded = expandRanged,
                    onToggle = { expandRanged = !expandRanged }
                )
                AnimatedVisibility(visible = expandRanged) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rangedTitle,
                            onValueChange = { if (it.length <= RangedValueConfig.MAX_TITLE_LENGTH) rangedTitle = it },
                            label = { Text("title") },
                            placeholder = { Text("Label") },
                            supportingText = { Text("${rangedTitle.length}/${RangedValueConfig.MAX_TITLE_LENGTH}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PatternTextField(
                            value = rangedText,
                            onValueChange = { if (it.length <= RangedValueConfig.MAX_TEXT_LENGTH) rangedText = it },
                            label = "text",
                            maxLength = RangedValueConfig.MAX_TEXT_LENGTH,
                            placeholder = "%.0f%%",
                            itemState = itemState
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = rangedMin,
                                onValueChange = { rangedMin = it },
                                label = { Text("min") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rangedMax,
                                onValueChange = { rangedMax = it },
                                label = { Text("max") },
                                placeholder = { Text("100") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ─── MONOCHROMATIC_IMAGE Section ───

            if (supportsMonochromaticImage) {
                SectionHeader(
                    title = "MONOCHROMATIC_IMAGE",
                    subtitle = "icon-only complication, state-based",
                    expanded = expandMonoImage,
                    onToggle = { expandMonoImage = !expandMonoImage }
                )
                AnimatedVisibility(visible = expandMonoImage) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monoImage,
                        onValueChange = { monoImage = it },
                        label = { Text("monochromaticImage") },
                        placeholder = { Text("Default icon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { iconPickerTarget = "mono"; showIconPicker = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Browse")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = monoActiveIcon,
                        onValueChange = { monoActiveIcon = it },
                        label = { Text("activeIcon (optional)") },
                        placeholder = { Text("Icon when item is ON/active") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { iconPickerTarget = "active"; showIconPicker = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Browse")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = monoInactiveIcon,
                        onValueChange = { monoInactiveIcon = it },
                        label = { Text("inactiveIcon (optional)") },
                        placeholder = { Text("Icon when item is OFF/inactive") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { iconPickerTarget = "inactive"; showIconPicker = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Browse")
                            }
                        }
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ─── Buttons ───

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("Clear slot", modifier = Modifier.padding(start = 4.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        onSave(
                            complication.copy(
                                label = label,
                                icon = icon,
                                shortText = ShortTextConfig(
                                    text = shortTextText,
                                    title = shortTextTitle
                                ),
                                longText = LongTextConfig(
                                    text = longTextText,
                                    title = longTextTitle
                                ),
                                rangedValue = RangedValueConfig(
                                    text = rangedText,
                                    title = rangedTitle,
                                    min = rangedMin.toDoubleOrNull(),
                                    max = rangedMax.toDoubleOrNull()
                                ),
                                monochromaticImage = MonochromaticImageConfig(
                                    monochromaticImage = monoImage,
                                    activeIcon = monoActiveIcon,
                                    inactiveIcon = monoInactiveIcon
                                )
                            )
                        )
                    }
                ) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Icon picker overlay
    if (showIconPicker) {
        IconPickerDialog(
            currentIcon = when (iconPickerTarget) {
                "mono" -> monoImage
                "active" -> monoActiveIcon
                "inactive" -> monoInactiveIcon
                else -> icon
            },
            iconBaseUrl = iconBaseUrl,
            iconAuthHeader = iconAuthHeader,
            onIconSelected = { selected ->
                when (iconPickerTarget) {
                    "icon" -> icon = selected
                    "mono" -> monoImage = selected
                    "active" -> monoActiveIcon = selected
                    "inactive" -> monoInactiveIcon = selected
                }
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
    }
}

/**
 * Text field for format patterns with live validation preview and expandable help.
 * Uses the item's current state for realistic previews.
 */
@Composable
private fun PatternTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxLength: Int?,
    placeholder: String,
    itemState: String = ""
) {
    var showHelp by remember { mutableStateOf(false) }
    val validation = validatePattern(value, itemState)

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (maxLength == null || newValue.length <= maxLength) onValueChange(newValue)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = validation is PatternValidation.Error,
            supportingText = {
                when (validation) {
                    is PatternValidation.Valid -> {
                        val lengthInfo = if (maxLength != null) "${value.length}/$maxLength · " else ""
                        Text("${lengthInfo}Preview: ${validation.preview}")
                    }
                    is PatternValidation.Error -> Text(validation.message)
                    is PatternValidation.Empty -> {
                        val lengthInfo = if (maxLength != null) "${value.length}/$maxLength · " else ""
                        Text("${lengthInfo}uses item state as-is")
                    }
                }
            },
            trailingIcon = {
                IconButton(onClick = { showHelp = !showHelp }) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "Pattern help",
                        tint = if (showHelp) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = showHelp) {
            PatternHelpCard()
        }
    }
}

@Composable
private fun PatternHelpCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Format Pattern Reference",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            PatternExample("%.0f", "Integer", "22")
            PatternExample("%.1f", "1 decimal", "22.5")
            PatternExample("%.0f°C", "With unit", "22°C")
            PatternExample("%.0f%%", "Percent", "75%")
            PatternExample("%.1f kWh", "With text", "3.2 kWh")
            PatternExample("%s", "Raw state", "ON")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Use %% for a literal percent sign.\nLeave empty to use the server's default format.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PatternExample(pattern: String, description: String, result: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = pattern,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "→ $result",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.8f)
        )
    }
}

private sealed interface PatternValidation {
    data class Valid(val preview: String) : PatternValidation
    data class Error(val message: String) : PatternValidation
    data object Empty : PatternValidation
}

/**
 * Validates a format pattern by attempting to format using the item's current state.
 * Falls back to sample values if the state is unavailable or incompatible.
 */
private fun validatePattern(pattern: String, itemState: String): PatternValidation {
    if (pattern.isBlank()) return PatternValidation.Empty

    // Try to parse numeric value from item state
    val numericValue = itemState
        .replace(Regex("[^\\d.\\-]"), "") // Strip units like "22.5 °C" → "22.5"
        .toDoubleOrNull()

    return try {
        if (numericValue != null) {
            // Use the real numeric state
            val result = String.format(pattern, numericValue)
            PatternValidation.Valid(result)
        } else {
            // Try as number first (pattern might expect %f)
            val result = String.format(pattern, 0.0)
            PatternValidation.Valid(result)
        }
    } catch (_: java.util.IllegalFormatConversionException) {
        // Pattern expects a string — use the raw state
        try {
            val stateStr = itemState.ifBlank { "NULL" }
            val result = String.format(pattern, stateStr)
            PatternValidation.Valid(result)
        } catch (e: Exception) {
            PatternValidation.Error("Invalid pattern: ${e.message?.take(30)}")
        }
    } catch (e: Exception) {
        PatternValidation.Error("Invalid pattern: ${e.message?.take(30)}")
    }
}
