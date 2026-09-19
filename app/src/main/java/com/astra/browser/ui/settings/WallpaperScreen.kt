package com.astra.browser.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.data.store.WallpaperStore
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.newtab.HomeWallpaper
import com.astra.browser.ui.newtab.WallpaperModes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val wallpapers: WallpaperStore
) : ViewModel() {
    val mode = settings.wallpaperMode.stateIn(viewModelScope, SharingStarted.Eagerly, WallpaperModes.NIGHT_SKY)
    val dim = settings.wallpaperDim.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)
    val custom = wallpapers.custom

    fun setMode(value: String) {
        viewModelScope.launch { settings.setWallpaperMode(value) }
    }

    fun setDim(value: Float) {
        viewModelScope.launch { settings.setWallpaperDim(value) }
    }

    fun import(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(wallpapers.importFromUri(uri)) }
    }

    fun removeCustom() {
        viewModelScope.launch { wallpapers.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(navController: NavController, viewModel: WallpaperViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val context = LocalContext.current

    val mode by viewModel.mode.collectAsState()
    val savedDim by viewModel.dim.collectAsState()
    val custom by viewModel.custom.collectAsState()
    var dim by remember(savedDim) { mutableFloatStateOf(savedDim) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.import(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Wallpaper applied" else "Couldn't use that image",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val openGallery = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Home wallpaper") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // ── Live preview ────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                val shape = RoundedCornerShape(26.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .aspectRatio(9f / 16f)
                        .clip(shape)
                        .border(1.5.dp, colors.border, shape)
                ) {
                    HomeWallpaper(mode = mode, custom = custom, dim = dim, modifier = Modifier.fillMaxSize())

                    // Mock browser chrome so the preview reads like the real page
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .fillMaxWidth(0.88f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.48f))
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 42.dp)
                            .fillMaxWidth(0.88f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.40f))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(4) {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f)))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color.Black.copy(alpha = 0.60f)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
                        }
                    }
                }
            }

            // ── Choose from gallery ─────────────────────────────────────
            Button(
                onClick = { openGallery() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose from gallery", fontWeight = FontWeight.SemiBold)
            }
            if (custom != null) {
                TextButton(
                    onClick = { viewModel.removeCustom() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Remove my photo", color = colors.onSurface.copy(alpha = 0.7f))
                }
            }

            // ── Wallpapers row ──────────────────────────────────────────
            Text(
                "Wallpapers",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Gallery tile: "+" until a photo exists, then its thumbnail
                item {
                    WallpaperTile(
                        label = "Gallery",
                        selected = mode == WallpaperModes.CUSTOM && custom != null,
                        onClick = {
                            if (custom != null) viewModel.setMode(WallpaperModes.CUSTOM) else openGallery()
                        }
                    ) {
                        if (custom != null) {
                            HomeWallpaper(WallpaperModes.CUSTOM, custom, 0f, Modifier.fillMaxSize())
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(colors.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add from gallery",
                                    tint = colors.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
                WallpaperModes.builtIns.forEach { (id, label) ->
                    item(key = id) {
                        WallpaperTile(
                            label = label,
                            selected = mode == id,
                            onClick = { viewModel.setMode(id) }
                        ) {
                            HomeWallpaper(id, null, 0f, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // ── Dim ─────────────────────────────────────────────────────
            Text(
                "Adjust",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.BrightnessMedium,
                    contentDescription = null,
                    tint = colors.onSurface.copy(alpha = 0.7f)
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Dim wallpaper", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Slider(
                        value = dim,
                        onValueChange = { dim = it },
                        onValueChangeFinished = { viewModel.setDim(dim) },
                        valueRange = 0f..0.6f
                    )
                }
                Text(
                    "${(dim / 0.6f * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalAstraColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 82.dp, height = 138.dp)
                .clip(shape)
                .border(
                    border = if (selected) BorderStroke(2.5.dp, colors.accent) else BorderStroke(1.dp, colors.border),
                    shape = shape
                )
                .clickable(onClick = onClick)
        ) {
            content()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = colors.background,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onSurface else colors.onSurface.copy(alpha = 0.65f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
