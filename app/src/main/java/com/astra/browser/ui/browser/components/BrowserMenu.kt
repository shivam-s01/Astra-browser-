package com.astra.browser.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astra.browser.domain.model.Tab
import com.astra.browser.theme.LocalAstraColors

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
    onPrivacyDashboard: () -> Unit
) {
    val colors = LocalAstraColors.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            MenuRow(Icons.Filled.Add, "New tab", onNewTab)
            MenuRow(Icons.Filled.VisibilityOff, "New private tab", onNewPrivateTab)
            HorizontalDivider(color = colors.border)
            MenuRow(Icons.Filled.Bookmarks, "Bookmarks", onBookmarks)
            MenuRow(Icons.Filled.History, "History", onHistory)
            MenuRow(Icons.Filled.Download, "Downloads", onDownloads)
            HorizontalDivider(color = colors.border)
            MenuRow(Icons.Filled.Search, "Find in page", onFindInPage)
            MenuRow(
                icon = Icons.Filled.DesktopWindows,
                label = if (activeTab?.desktopSiteEnabled == true) "Mobile site" else "Desktop site",
                onClick = onDesktopSite
            )
            MenuRow(Icons.Filled.Shield, "Astra Privacy", onPrivacyDashboard)
            HorizontalDivider(color = colors.border)
            MenuRow(Icons.Filled.Settings, "Astra Settings", onSettings)
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.8f))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

