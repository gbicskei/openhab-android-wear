package org.openhab.habdroid.wear.phone.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openhab.habdroid.wear.shared.debug.DebugLogEntry
import org.openhab.habdroid.wear.shared.debug.LogLevel
import org.openhab.habdroid.wear.shared.debug.LogSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    onBack: () -> Unit,
    viewModel: DebugLogViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter { entry ->
            entry.tag.contains(searchQuery, ignoreCase = true) ||
                entry.message.contains(searchQuery, ignoreCase = true)
        }
    }

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePaused() }) {
                        Icon(
                            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Resume" else "Pause"
                        )
                    }
                    IconButton(
                        onClick = { exportDebugLog(context, filteredEntries) },
                        enabled = filteredEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter by tag or message...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear filter", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No errors logged" else "No matches",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()

                // Track if user is "following" the tail (at or near bottom)
                val isAtBottom = remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= filteredEntries.size - 3
                    }
                }

                // Auto-scroll to bottom only if user is already following
                LaunchedEffect(filteredEntries.size) {
                    if (filteredEntries.isNotEmpty() && isAtBottom.value && !paused) {
                        listState.animateScrollToItem(filteredEntries.size - 1)
                    }
                }

                // Scroll to bottom when unpausing
                LaunchedEffect(paused) {
                    if (!paused && filteredEntries.isNotEmpty()) {
                        listState.animateScrollToItem(filteredEntries.size - 1)
                    }
                }

                // Initial scroll to bottom
                LaunchedEffect(Unit) {
                    if (filteredEntries.isNotEmpty()) {
                        listState.scrollToItem(filteredEntries.size - 1)
                    }
                }

                // Load more when scrolled near the top
                LaunchedEffect(listState.firstVisibleItemIndex) {
                    if (listState.firstVisibleItemIndex < 3) {
                        viewModel.loadMore()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(filteredEntries.size, key = { index ->
                        val entry = filteredEntries[index]
                        "${entry.timestamp}_${entry.tag}_${entry.message.hashCode()}_$index"
                    }) { index ->
                        val entry = filteredEntries[index]
                        DebugLogEntryCard(entry)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLogEntryCard(entry: DebugLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val bgColor = when (entry.level) {
        LogLevel.ERROR -> Color(0x20FF0000)
        LogLevel.WARN -> Color(0x20FFA000)
        LogLevel.INFO -> Color(0x200080FF)
        LogLevel.DEBUG -> Color(0x10808080)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = entry.stackTrace != null) { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source icon
            Icon(
                imageVector = if (entry.source == LogSource.WATCH) Icons.Filled.Watch
                else Icons.Filled.PhoneAndroid,
                contentDescription = entry.source.name,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Timestamp
            Text(
                text = entry.formattedTime,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Tag
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Message
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )

        // Stack trace (expandable)
        if (expanded && entry.stackTrace != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.stackTrace ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 12.sp
            )
        }
    }
}

private fun exportDebugLog(context: android.content.Context, entries: List<DebugLogEntry>) {
    val sb = StringBuilder()
    sb.appendLine("=== wearOH Debug Log ===")
    sb.appendLine("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
    sb.appendLine("Entries: ${entries.size}")
    sb.appendLine()

    for (entry in entries) {
        val level = entry.level.name.first()
        val source = entry.source.name.take(5).padEnd(5)
        sb.appendLine("[$level] ${entry.formattedTime} $source ${entry.tag}: ${entry.message}")
        entry.stackTrace?.let { trace ->
            trace.lines().forEach { line -> sb.appendLine("    $line") }
        }
    }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "wearOH Debug Log")
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Export Debug Log"))
}
