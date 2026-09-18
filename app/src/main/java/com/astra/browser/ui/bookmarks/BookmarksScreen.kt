package com.astra.browser.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
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
import com.astra.browser.data.local.entity.BookmarkEntity
import com.astra.browser.data.repository.BookmarkRepository
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository
) : ViewModel() {
    val bookmarks = repository.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun remove(bookmark: BookmarkEntity) {
        viewModelScope.launch { repository.removeById(bookmark.id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(navController: NavController, viewModel: BookmarksViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val bookmarks by viewModel.bookmarks.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Astra Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(bookmarks) { bookmark ->
                    BookmarkRow(bookmark = bookmark, onDelete = { viewModel.remove(bookmark) })
                    HorizontalDivider(color = colors.border)
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: BookmarkEntity, onDelete: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Bookmarks, contentDescription = null, tint = colors.accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(bookmark.title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bookmark.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark", tint = colors.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Bookmarks, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No bookmarks yet", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
        }
    }
}
