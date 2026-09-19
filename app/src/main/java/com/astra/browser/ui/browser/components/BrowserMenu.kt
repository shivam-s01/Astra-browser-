package com.astra.browser.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.astra.browser.domain.model.Tab
import com.astra.browser.theme.LocalAstraColors

/**
 * Brave-style overflow menu (opened from the 3-dot button in the bottom bar).
 * Top: quick action row (back / forward / reload / bookmark). Below: the full
 * list with Settings first, exactly where Brave puts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenu(
    activeTab: Tab?,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onFindInPage: () -> Unit,
    onDesktopSite: () -> Unit,
    onSettings: () -> Unit,
    onPrivacyDashboard: () -> Unit,
    onBack: () -> Unit = {},
    onForward: () -> Unit = {},
    onReload: () -> Unit = {},
    onBookmarkToggle: () -> Unit = {}
) {
    val colors = LocalAstraColors.current
    val onPage = activeTab != null && !activeTab.isBlankTab

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.onSurface.copy(alpha = 0.25f)) }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            if (onPage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", activeTab?.canGoBack == true, onBack)
                    QuickAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", activeTab?.canGoForward == true, onForward)
                    QuickAction(Icons.Filled.Refresh, "Reload", true, onReload)
                    QuickAction(
                        if (activeTab?.isBookmarked == true) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        "Bookmark", true, onBookmarkToggle
                    )
                }
                HorizontalDivider(color = colors.border)
            }

            MenuRow(Icons.Filled.Settings, "Settings", onSettings)
            HorizontalDivider(color = colors.border)

            MenuRow(Icons.Filled.Add, "New tab", onNewTab)
            MenuRow(Icons.Filled.VisibilityOff, "New private tab", onNewPrivateTab)
            HorizontalDivider(color = colors.border)

            MenuRow(Icons.Filled.Bookmarks, "Bookmarks", onBookmarks)
            MenuRow(Icons.Filled.History, "History", onHistory)
            MenuRow(Icons.Filled.Download, "Downloads", onDownloads)
            HorizontalDivider(color = colors.border)

            if (onPage) {
                MenuRow(Icons.Filled.Search, "Find in page", onFindInPage)
                MenuRow(
                    icon = Icons.Filled.DesktopWindows,
                    label = if (activeTab?.desktopSiteEnabled == true) "Mobile site" else "Desktop site",
                    onClick = onDesktopSite
                )
            }
            MenuRow(Icons.Filled.Shield, "Astra Privacy", onPrivacyDashboard)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}
