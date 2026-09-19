package com.astra.browser.ui.browser.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.browser.theme.LocalAstraColors

/**
 * Brave-style bottom navigation: Home | Bookmarks | Search | Tabs | Menu.
 * Always visible (new tab page and while browsing), exactly like Brave on Android.
 */
@Composable
fun BraveBottomBar(
    tabCount: Int,
    isPrivate: Boolean,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onSearch: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAstraColors.current
    val barColor = if (isPrivate) colors.surfaceVariant else colors.toolbar

    Surface(
        modifier = modifier,
        color = barColor,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BarIcon(Icons.Outlined.Home, "Home", onHome)
            BarIcon(Icons.Outlined.BookmarkBorder, "Bookmarks", onBookmarks)
            BarIcon(Icons.Filled.Search, "Search", onSearch)
            BarTabCounter(tabCount, onTabs)
            BarIcon(Icons.Filled.MoreVert, "Menu", onMenu)
        }
    }
}

@Composable
private fun RowScope.BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.onSurface.copy(alpha = 0.92f),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun RowScope.BarTabCounter(count: Int, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = Color.Transparent,
            border = BorderStroke(2.dp, colors.onSurface.copy(alpha = 0.92f)),
            modifier = Modifier.size(width = 26.dp, height = 26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (count > 99) ":D" else count.toString(),
                    fontSize = if (count > 9) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface.copy(alpha = 0.92f)
                )
            }
        }
    }
}
