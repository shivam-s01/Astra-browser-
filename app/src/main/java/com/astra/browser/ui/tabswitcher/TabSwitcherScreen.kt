package com.astra.browser.ui.tabswitcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.astra.browser.domain.model.Tab
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.browser.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Chrome-grade tab switcher: live WebView thumbnails, fling-to-dismiss cards
 * (any direction, matching Chrome's grid tab switcher), smooth spring physics
 * for both the dismiss gesture and the grid reflow that follows it, plus a
 * bottom search bar that filters open tabs live -- same shape as Chrome's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    navController: NavController,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val colors = LocalAstraColors.current
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()

    var query by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var showOverflow by remember { mutableStateOf(false) }

    val filtered = remember(tabs, query) {
        if (query.isBlank()) tabs
        else tabs.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }
    }
    val normalTabs = filtered.filter { !it.isPrivate }
    val privateTabs = filtered.filter { it.isPrivate }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tabs.isEmpty()) "Tabs" else "${tabs.size} ${if (tabs.size == 1) "tab" else "tabs"}",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close tab switcher")
                    }
                },
                actions = {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                            contentDescription = "Toggle layout"
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Close all tabs") },
                                enabled = tabs.isNotEmpty(),
                                onClick = {
                                    showOverflow = false
                                    viewModel.closeAllTabs()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        },
        bottomBar = {
            TabSwitcherBottomBar(
                query = query,
                onQueryChange = { query = it },
                onNewTab = {
                    viewModel.newTab()
                    navController.popBackStack()
                }
            )
        }
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyTabsState(modifier = Modifier.padding(padding))
        } else if (filtered.isEmpty()) {
            NoSearchResultsState(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (normalTabs.isNotEmpty()) {
                    TabGrid(
                        tabs = normalTabs,
                        activeTabId = activeTabId,
                        isGridView = isGridView,
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
                        isGridView = isGridView,
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
private fun TabSwitcherBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onNewTab: () -> Unit
) {
    val colors = LocalAstraColors.current
    Surface(color = colors.toolbar, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceVariant,
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search your tabs", color = colors.onSurface.copy(alpha = 0.5f), fontSize = 15.sp)
                        }
                        BasicTextFieldSingleLine(value = query, onValueChange = onQueryChange, color = colors.onSurface)
                    }
                }
            }
            FilledIconButton(
                onClick = onNewTab,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accent),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New tab")
            }
        }
    }
}

@Composable
private fun BasicTextFieldSingleLine(value: String, onValueChange: (String) -> Unit, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = color, fontSize = 15.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(color),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TabGrid(
    tabs: List<Tab>,
    activeTabId: String?,
    isGridView: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tabs, key = { it.id }) { tab ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140)),
                modifier = Modifier.animateItem(
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            ) {
                SwipeToDismissTabCard(
                    onDismissed = { onClose(tab.id) },
                    isCompact = !isGridView
                ) {
                    TabCard(
                        tab = tab,
                        isActive = tab.id == activeTabId,
                        isCompact = !isGridView,
                        onSelect = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) }
                    )
                }
            }
        }
    }
}

/**
 * Wraps a tab card with Chrome's grid-switcher gesture: drag it any direction
 * and it either springs back (small/slow drag) or flings off-screen and
 * closes the tab (past the distance/velocity threshold), fading out as it
 * travels so the motion reads as "thrown away" rather than just sliding.
 */
@Composable
private fun SwipeToDismissTabCard(
    onDismissed: () -> Unit,
    isCompact: Boolean,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dismissed by remember { mutableStateOf(false) }

    val distance = hypot(offsetX.value, offsetY.value)
    val maxDist = (size.width.coerceAtLeast(1)) * 1.15f
    val alpha = (1f - (distance / maxDist)).coerceIn(0.1f, 1f)
    val rotation = (offsetX.value / (size.width.coerceAtLeast(1)).toFloat()) * 10f

    Box(
        modifier = Modifier
            .onSizeChanged { size = it }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation
                this.alpha = alpha
            }
            .pointerInput(isCompact) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo(offsetY.value + dragAmount.y)
                        }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val speed = hypot(velocity.x, velocity.y)
                        val travelled = hypot(offsetX.value, offsetY.value)
                        val threshold = size.width.coerceAtLeast(1) * 0.32f
                        val shouldDismiss = travelled > threshold || speed > 1200f
                        if (shouldDismiss && !dismissed) {
                            dismissed = true
                            val dirX = if (abs(offsetX.value) < 1f) 0f else offsetX.value / abs(offsetX.value)
                            val dirY = if (abs(offsetY.value) < 1f) -1f else offsetY.value / abs(offsetY.value)
                            val flyX = if (dirX != 0f) dirX * size.width * 1.6f else offsetX.value
                            val flyY = dirY * size.height.coerceAtLeast(size.width) * 1.6f
                            scope.launch {
                                launch {
                                    offsetX.animateTo(flyX, animationSpec = tween(240))
                                }
                                offsetY.animateTo(flyY, animationSpec = tween(240))
                                onDismissed()
                            }
                        } else {
                            scope.launch {
                                launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                                offsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { offsetX.animateTo(0f) }
                            offsetY.animateTo(0f)
                        }
                    }
                )
            }
    ) {
        content()
    }
}

@Composable
private fun TabCard(tab: Tab, isActive: Boolean, isCompact: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = LocalAstraColors.current
    val thumbnail = rememberTabThumbnail(tab.thumbnailPath)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceVariant,
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, colors.accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isCompact) it.height(120.dp) else it.aspectRatio(0.72f) }
            .clickable(onClick = onSelect)
    ) {
        if (isCompact) {
            Row(modifier = Modifier.fillMaxSize()) {
                TabThumbnailArea(
                    thumbnail = thumbnail,
                    modifier = Modifier.fillMaxHeight().width(96.dp).clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                )
                TabCardHeader(tab, onClose, modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp), centered = true)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabCardHeader(tab, onClose, modifier = Modifier.fillMaxWidth().padding(10.dp, 8.dp, 4.dp, 8.dp))
                TabThumbnailArea(
                    thumbnail = thumbnail,
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                )
            }
        }
    }
}

@Composable
private fun TabCardHeader(tab: Tab, onClose: () -> Unit, modifier: Modifier = Modifier, centered: Boolean = false) {
    val colors = LocalAstraColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(18.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tab.title.ifBlank { "New Tab" },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (centered && tab.url.isNotBlank()) {
                Text(
                    tab.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close tab", tint = colors.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TabThumbnailArea(thumbnail: ImageBitmap?, modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.background(colors.background.copy(alpha = 0.6f))) {
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
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = colors.onSurface.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/** Decodes a tab's cached thumbnail off the main thread. Re-decodes whenever the screen (and thus this card) is freshly composed, which is exactly when a fresh capture was just taken. */
@Composable
private fun rememberTabThumbnail(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path.isNullOrBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun EmptyTabsState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No open tabs", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun NoSearchResultsState(modifier: Modifier = Modifier) {
    val colors = LocalAstraColors.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No matching tabs", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha = 0.5f))
    }
}
