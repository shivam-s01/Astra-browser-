package com.astra.browser.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.astra.browser.core.engine.AstraDownloadManager
import com.astra.browser.data.local.entity.DownloadEntity
import com.astra.browser.data.repository.DownloadRepository
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val downloadManager: AstraDownloadManager
) : ViewModel() {
    val downloads = repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteEntry(download: DownloadEntity) {
        viewModelScope.launch { repository.deleteById(download.id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun cancel(download: DownloadEntity) {
        download.systemDownloadId?.let { downloadManager.cancelDownload(it) }
        viewModelScope.launch {
            repository.update(download.copy(status = "CANCELLED"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController, viewModel: DownloadsViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Astra Downloads") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear downloads")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyDownloadsState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(downloads) { download ->
                    DownloadRow(
                        download = download,
                        onCancel = { viewModel.cancel(download) },
                        onDelete = { viewModel.deleteEntry(download) }
                    )
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: DownloadEntity, onCancel: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val icon = when (download.status) {
            "COMPLETED" -> Icons.Filled.CheckCircle
            "FAILED" -> Icons.Filled.Error
            "RUNNING" -> Icons.Filled.Download
            else -> Icons.Filled.Pending
        }
        val tint = when (download.status) {
            "COMPLETED" -> colors.accent
            "FAILED" -> androidx.compose.ui.graphics.Color(0xFFE05252)
            else -> colors.onSurface.copy(alpha = 0.6f)
        }
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(download.fileName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = download.status.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.5f)
            )
        }
        if (download.status == "RUNNING") {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.onSurface.copy(alpha = 0.5f))
            }
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = colors.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Download, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No downloads yet", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
        }
    }
}
