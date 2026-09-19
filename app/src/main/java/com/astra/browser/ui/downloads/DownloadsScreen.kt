package com.astra.browser.ui.downloads

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.core.engine.AstraDownloadManager
import com.astra.browser.core.engine.DownloadProgress
import com.astra.browser.data.local.entity.DownloadEntity
import com.astra.browser.data.repository.DownloadRepository
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val downloadManager: AstraDownloadManager
) : ViewModel() {
    val downloads = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress: StateFlow<Map<Long, DownloadProgress>> = downloadManager.progress

    init { downloadManager.resumeTracking() }

    fun deleteEntry(download: DownloadEntity) {
        viewModelScope.launch { repository.deleteById(download.id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun cancel(download: DownloadEntity) {
        download.systemDownloadId?.let { downloadManager.cancelDownload(it) }
        viewModelScope.launch { repository.update(download.copy(status = "CANCELLED")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController, viewModel: DownloadsViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val downloads by viewModel.downloads.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
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
                items(downloads, key = { it.id }) { download ->
                    DownloadRow(
                        download = download,
                        live = progress[download.id],
                        onOpen = { openFile(context, download) },
                        onCancel = { viewModel.cancel(download) },
                        onDelete = { viewModel.deleteEntry(download) }
                    )
                }
            }
        }
    }
}

private fun openFile(context: android.content.Context, d: DownloadEntity) {
    val path = d.localPath
    if (d.status != "COMPLETED" || path.isNullOrBlank()) return
    try {
        val uri = Uri.parse(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, d.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    live: DownloadProgress?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAstraColors.current
    val status = live?.status ?: download.status
    val inFlight = status == "RUNNING" || status == "PENDING" || status == "PAUSED"

    val fraction = live?.fraction ?: if (status == "COMPLETED") 1f else 0f
    // Smoothly tween between polls so the bar glides instead of jumping.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = 450),
        label = "downloadProgress"
    )

    val tint by animateColorAsState(
        targetValue = when (status) {
            "COMPLETED" -> colors.accent
            "FAILED" -> Color(0xFFE05252)
            else -> colors.accent
        },
        animationSpec = tween(300),
        label = "downloadTint"
    )

    val downloaded = live?.downloadedBytes ?: download.downloadedBytes
    val total = live?.totalBytes?.takeIf { it > 0 } ?: download.totalBytes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = status == "COMPLETED", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (status) {
                        "COMPLETED" -> Icons.AutoMirrored.Filled.InsertDriveFile
                        "FAILED" -> Icons.Filled.ErrorOutline
                        "CANCELLED" -> Icons.Filled.Cancel
                        else -> Icons.Filled.Download
                    },
                    contentDescription = null,
                    tint = tint
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusLine(status, downloaded, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == "FAILED") Color(0xFFE05252) else colors.onSurface.copy(alpha = 0.6f)
                )
            }

            if (inFlight) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.onSurface.copy(alpha = 0.6f))
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = colors.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        if (inFlight) {
            Spacer(Modifier.height(10.dp))
            val indeterminate = live != null && live.fraction < 0f
            if (indeterminate || status == "PENDING") {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = tint,
                    trackColor = tint.copy(alpha = 0.15f)
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = tint,
                    trackColor = tint.copy(alpha = 0.15f)
                )
            }
        }
    }
}

private fun statusLine(status: String, done: Long, total: Long): String = when (status) {
    "COMPLETED" -> if (total > 0) "Completed • ${formatBytes(total)}" else "Completed"
    "FAILED" -> "Failed • tap delete and retry"
    "CANCELLED" -> "Cancelled"
    "PAUSED" -> "Paused • ${formatBytes(done)}"
    "PENDING" -> "Starting…"
    else -> if (total > 0) {
        "${formatBytes(done)} of ${formatBytes(total)} • ${(done * 100 / total)}%"
    } else {
        formatBytes(done)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
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
