package com.astra.browser.ui.browser.components

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.astra.browser.theme.LocalAstraColors

@Composable
fun FindInPageBar(webView: WebView?, onClose: () -> Unit) {
    val colors = LocalAstraColors.current
    var query by remember { mutableStateOf("") }
    var matchCount by remember { mutableStateOf(0) }
    var activeMatch by remember { mutableStateOf(0) }

    DisposableEffect(webView) {
        webView?.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            matchCount = numberOfMatches
            activeMatch = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
        }
        onDispose {
            webView?.clearMatches()
            webView?.setFindListener(null)
        }
    }

    Surface(color = colors.surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isNotEmpty()) webView?.findAllAsync(it) else webView?.clearMatches()
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Find in page") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { webView?.findNext(true) })
            )
            Text(
                text = if (matchCount > 0) "$activeMatch/$matchCount" else "0/0",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface.copy(alpha = 0.6f)
            )
            IconButton(onClick = { webView?.findNext(false) }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = { webView?.findNext(true) }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close find in page")
            }
        }
    }
}
