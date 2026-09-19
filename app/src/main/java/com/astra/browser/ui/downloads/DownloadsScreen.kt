package com.astra.browser.ui.downloads

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

private enum class DownloadFilter(val label: String) { ALL("All"), VIDEOS("Videos"), AUDIO("Audio"), IMAGES("Images"), DOCUMENTS("Pages") }

private fun DownloadEntity.matchesFilter(filter: DownloadFilter): Boolean = when (filter) {
    DownloadFilter.ALL -> true
    DownloadFilter.VIDEOS -> mimeType?.startsWith("video/") == true
    DownloadFilter.AUDIO -> mimeType?.startsWith("audio/") == true
    DownloadFilter.IMAGES -> mimeType?.startsWith("image/") == true
    DownloadFilter.DOCUMENTS -> mimeType?.let {
        it == "application/pdf" || it.contains("document") || it.contains("text/html") || it.startsWith("text/")
    } == true
}

private fun DownloadEntity.isMedia(): Boolean =
    mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true || mimeType?.startsWith("image/") == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController, viewModel: DownloadsViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val downloads by viewModel.downloads.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val context = LocalContext.current

    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }

    val visible = remember(downloads, query, filter) {
        downloads
            .filter { it.matchesFilter(filter) }
            .filter { query.isBlank() || it.fileName.contains(query, ignoreCase = true) }
            .sortedByDescending { it.completedAt ?: it.startedAt }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        SearchField(query = query, onQueryChange = { query = it })
                    } else {
                        Text("Downloads", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearch) { showSearch = false; query = "" } else navController.popBackStack()
                    }) {
                        Icon(
                            if (showSearch) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search downloads")
                        }
                        if (downloads.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearAll() }) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear downloads")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (downloads.isNotEmpty()) {
                StorageUsageLine()
                FilterChipsRow(selected = filter, onSelect = { filter = it })
            }
            if (downloads.isEmpty()) {
                EmptyDownloadsState(modifier = Modifier.fillMaxSize())
            } else if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching downloads", color = colors.onSurface.copy(alpha = 0.5f))
                }
            } else {
                val grouped = remember(visible) { groupByDate(visible) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (label, entries) ->
                        item(key = "header_$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)
                            )
                        }
                        items(entries, key = { it.id }) { download ->
                            val live = progress[download.id]
                            val inFlight = (live?.status ?: download.status).let { it == "RUNNING" || it == "PENDING" || it == "PAUSED" }
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                            ) {
                                if (!inFlight && download.status == "COMPLETED" && download.isMedia()) {
                                    DownloadMediaCard(
                                        download = download,
                                        onOpen = { openFile(context, download) },
                                        onDelete = { viewModel.deleteEntry(download) },
                                        onShare = { shareFile(context, download) }
                                    )
                                } else {
                                    DownloadRow(
                                        download = download,
                                        live = live,
                                        onOpen = { openFile(context, download) },
                                        onCancel = { viewModel.cancel(download) },
                                        onDelete = { viewModel.deleteEntry(download) },
                                        onShare = { shareFile(context, download) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalAstraColors.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.onSurface),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text("Search downloads", color = colors.onSurface.copy(alpha = 0.5f))
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
    )
}

@Composable
private fun StorageUsageLine() {
    val colors = LocalAstraColors.current
    val text = remember {
        runCatching {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            "Using ${formatBytes(total - free)} of ${formatBytes(total)}"
        }.getOrDefault(null)
    }
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun FilterChipsRow(selected: DownloadFilter, onSelect: (DownloadFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DownloadFilter.entries.forEach { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                leadingIcon = if (f == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

private fun groupByDate(entries: List<DownloadEntity>): List<Pair<String, List<DownloadEntity>>> {
    val result = LinkedHashMap<String, MutableList<DownloadEntity>>()
    val now = System.currentTimeMillis()
    entries.forEach { entry ->
        val millis = entry.completedAt ?: entry.startedAt
        val label = dateGroupLabel(millis, now)
        result.getOrPut(label) { mutableListOf() }.add(entry)
    }
    return result.map { it.key to it.value }
}

private fun dateGroupLabel(millis: Long, now: Long): String {
    if (now - millis < 5 * 60 * 1000) return "Just now"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(cal, today)) {
        return "Today - ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))}"
    }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(cal, yesterday)) return "Yesterday"
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
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

private fun shareFile(context: android.content.Context, d: DownloadEntity) {
    val path = d.localPath
    if (path.isNullOrBlank()) return
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = d.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share this file", Toast.LENGTH_SHORT).show()
    }
}

/** Chrome-style card for a finished video/audio/image download: a wide preview with a play badge for playable media, filename and size below, three-dot menu for actions. */
@Composable
private fun DownloadMediaCard(
    download: DownloadEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val colors = LocalAstraColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val isImage = download.mimeType?.startsWith("image/") == true
    val thumbnail = if (isImage) rememberFileThumbnail(download.localPath) else null

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(onClick = onOpen)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (download.mimeType?.startsWith("audio/") == true) Icons.Filled.MusicNote else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = colors.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                if (download.mimeType?.startsWith("video/") == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatBytes(download.totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface.copy(alpha = 0.55f)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.onSurface.copy(alpha = 0.6f))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun rememberFileThumbnail(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val realPath = Uri.parse(path).path ?: path
                android.graphics.BitmapFactory.decodeFile(realPath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    live: DownloadProgress?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val colors = LocalAstraColors.current
    val status = live?.status ?: download.status
    val inFlight = status == "RUNNING" || status == "PENDING" || status == "PAUSED"
    var menuOpen by remember { mutableStateOf(false) }

    val fraction = live?.fraction ?: if (status == "COMPLETED") 1f else 0f
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
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.onSurface.copy(alpha = 0.6f))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (status == "COMPLETED") {
                            DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen() })
                            DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                        }
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
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
