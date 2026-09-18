package com.astra.browser.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.astra.browser.theme.LocalAstraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraToolbar(
    addressText: String,
    onAddressChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    isSecure: Boolean,
    isLoading: Boolean,
    isBookmarked: Boolean,
    isPrivate: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTabSwitcherClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val colors = LocalAstraColors.current

    Surface(color = if (isPrivate) colors.surfaceVariant else colors.toolbar, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = if (canGoBack) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward",
                    tint = if (canGoForward) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.Info,
                        contentDescription = if (isSecure) "Secure connection" else "Not secure",
                        tint = if (isSecure) colors.accent else colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    BasicAddressField(
                        value = addressText,
                        onValueChange = onAddressChange,
                        onFocusChange = onAddressFocusChange,
                        onSubmit = { onNavigate(addressText) },
                        modifier = Modifier.weight(1f)
                    )
                    if (addressText.isNotBlank()) {
                        IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) colors.accent else colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onReloadOrStop) {
                Icon(
                    imageVector = if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (isLoading) "Stop" else "Reload",
                    tint = colors.onSurface
                )
            }

            TabCounterButton(count = tabCount, onClick = onTabSwitcherClick)

            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = colors.onSurface)
            }
        }
    }
}

@Composable
private fun BasicAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAstraColors.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(
            Modifier.onFocusEvent { focusState -> onFocusChange(focusState.isFocused) }
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    "Search or enter address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(alpha = 0.4f)
                )
            }
            inner()
        }
    )
}

@Composable
private fun TabCounterButton(count: Int, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.onSurface),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}


