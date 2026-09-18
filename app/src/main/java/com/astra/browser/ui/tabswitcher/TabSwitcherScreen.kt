package com.astra.browser.ui.tabswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.astra.browser.domain.model.Tab
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    navController: NavController,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val colors = LocalAstraColors.current
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()

    val normalTabs = tabs.filter { !it.isPrivate }
    val privateTabs = tabs.filter { it.isPrivate }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("${tabs.size} tabs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.newTab()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "New tab")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyTabsState(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (normalTabs.isNotEmpty()) {
                    TabGrid(
                        tabs = normalTabs,
                        activeTabId = activeTabId,
                        modifier = Modifier.weight(1f, fill = privateTabs.isEmpty()),
                        onSelect = { viewModel.switchTab(it); navController.popBackStack() },
                        onClose = { viewModel.closeTab(it) }
                    )
                }
                if (privateTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Text("Private", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    }
                    TabGrid(
                        tabs = privateTabs,
                        activeTabId = activeTabId,
                        modifier = Modifier.weight(1f),
                        onSelect = { viewModel.switchTab(it); navController.popBackStack() },
                        onClose = { viewModel.closeTab(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabGrid(
    tabs: List<Tab>,
    activeTabId: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabCard(
                tab = tab,
                isActive = tab.id == activeTabId,
                onSelect = { onSelect(tab.id) },
                onClose = { onClose(tab.id) }
            )
        }
    }
}

@Composable
private fun TabCard(tab: Tab, isActive: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = LocalAstraColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) colors.tabActive else colors.surfaceVariant,
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, colors.accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tab.title.ifBlank { "New Tab" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab", tint = colors.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                tab.url.ifBlank { "astra://newtab" },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyTabsState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No open tabs", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
    }
}
