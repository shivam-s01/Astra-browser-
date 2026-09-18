package com.astra.browser.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.data.local.entity.HistoryEntity
import com.astra.browser.data.repository.HistoryRepository
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ClearRange(val label: String, val millis: Long) {
    LAST_HOUR("Last hour", 60 * 60 * 1000L),
    LAST_24_HOURS("Last 24 hours", 24 * 60 * 60 * 1000L),
    LAST_7_DAYS("Last 7 days", 7 * 24 * 60 * 60 * 1000L),
    LAST_4_WEEKS("Last 4 weeks", 28 * 24 * 60 * 60 * 1000L),
    ALL_TIME("All time", Long.MAX_VALUE)
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository
) : ViewModel() {
    val history = repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun clear(range: ClearRange) {
        viewModelScope.launch {
            if (range == ClearRange.ALL_TIME) repository.clearAll()
            else repository.clearSince(System.currentTimeMillis() - range.millis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController, viewModel: HistoryViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val history by viewModel.history.collectAsState()
    var showClearMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showClearMenu = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear browsing data")
                        }
                        DropdownMenu(expanded = showClearMenu, onDismissRequest = { showClearMenu = false }) {
                            ClearRange.entries.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(range.label) },
                                    onClick = { viewModel.clear(range); showClearMenu = false }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(padding))
        } else {
            val grouped = groupByDay(history)
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                grouped.forEach { (label, entries) ->
                    item {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
                        )
                    }
                    items(entries) { entry ->
                        HistoryRow(entry = entry, onDelete = { viewModel.deleteEntry(entry.id) })
                        HorizontalDivider(color = colors.border)
                    }
                }
            }
        }
    }
}

private fun groupByDay(history: List<HistoryEntity>): Map<String, List<HistoryEntity>> {
    val now = Calendar.getInstance()
    val today = now.get(Calendar.DAY_OF_YEAR)
    val year = now.get(Calendar.YEAR)
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    return history.groupBy { entry ->
        val cal = Calendar.getInstance().apply { timeInMillis = entry.visitedAt }
        when {
            cal.get(Calendar.DAY_OF_YEAR) == today && cal.get(Calendar.YEAR) == year -> "Today"
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) -> "Yesterday"
            else -> fmt.format(Date(entry.visitedAt))
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onDelete: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.History, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.5f))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = colors.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.History, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No browsing history", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
        }
    }
}
