package com.astra.browser.ui.newtab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astra.browser.theme.LocalAstraColors

@Composable
fun NewTabPage(
    onNavigate: (String) -> Unit,
    viewModel: NewTabViewModel = hiltViewModel()
) {
    val colors = LocalAstraColors.current
    var query by remember { mutableStateOf("") }
    val recentSites by viewModel.recentSites.collectAsState()
    val trackersBlocked by viewModel.totalTrackersBlocked.collectAsState()
    val showShortcuts by viewModel.showShortcuts.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AstraWordmark()

            Spacer(Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.5f))
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onNavigate(query) }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search or enter address",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            if (showShortcuts && recentSites.isNotEmpty()) {
                Text(
                    "Shortcuts",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                ) {
                    items(recentSites) { site ->
                        ShortcutTile(title = site, onClick = { onNavigate(site) })
                    }
                }
            } else if (showShortcuts) {
                EmptyShortcutsHint()
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Text(
                    "$trackersBlocked trackers blocked",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun AstraWordmark() {
    val colors = LocalAstraColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = colors.accent, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = colors.background,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            "Astra",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground
        )
    }
}

@Composable
private fun ShortcutTile(title: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    val domain = runCatching { java.net.URI(title).host ?: title }.getOrDefault(title)
    Column(
        modifier = Modifier
            .padding(6.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = colors.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    domain.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            domain.removePrefix("www.").take(12),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyShortcutsHint() {
    val colors = LocalAstraColors.current
    Text(
        "Sites you visit will show up here",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurface.copy(alpha = 0.4f)
    )
}
