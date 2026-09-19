package com.astra.browser.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.domain.model.SearchEngine
import com.astra.browser.theme.AstraThemeId
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.AstraRoutes
import com.astra.browser.ui.motion.AnimStyles
import com.astra.browser.ui.newtab.WallpaperModes
import com.astra.browser.ui.prefs.LocalUiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// ───────────────────────────────────────────────────────────────────────────
// ViewModel
// ───────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    // General / privacy
    val searchEngine = store.searchEngine.state("GOOGLE")
    val searchSuggestions = store.searchSuggestionsEnabled.state(true)
    val themeId = store.themeId.state("SYSTEM")
    val trackingProtection = store.trackingProtection.state(true)
    val adBlocking = store.adBlocking.state(true)
    val fingerprintProtection = store.fingerprintProtection.state(true)
    val blockThirdPartyCookies = store.blockThirdPartyCookies.state(true)
    val blockPopups = store.blockPopups.state(true)
    val httpsOnly = store.httpsOnly.state(false)
    val doNotTrack = store.doNotTrack.state(false)
    val restoreSession = store.restoreSession.state(true)
    val askBeforeDownload = store.askBeforeDownload.state(true)
    val wallpaperMode = store.wallpaperMode.state("NIGHT_SKY")

    // Media
    val backgroundPlayback = store.backgroundPlayback.state(true)
    val ytAdSkip = store.ytAdSkip.state(true)

    fun setSearchEngine(v: String) = viewModelScope.launch { store.setSearchEngine(v) }
    fun setSearchSuggestions(v: Boolean) = viewModelScope.launch { store.setSearchSuggestions(v) }
    fun setThemeId(v: String) = viewModelScope.launch { store.setThemeId(v) }
    fun setTrackingProtection(v: Boolean) = viewModelScope.launch { store.setTrackingProtection(v) }
    fun setAdBlocking(v: Boolean) = viewModelScope.launch { store.setAdBlocking(v) }
    fun setFingerprintProtection(v: Boolean) = viewModelScope.launch { store.setFingerprintProtection(v) }
    fun setBlockThirdPartyCookies(v: Boolean) = viewModelScope.launch { store.setBlockThirdPartyCookies(v) }
    fun setBlockPopups(v: Boolean) = viewModelScope.launch { store.setBlockPopups(v) }
    fun setHttpsOnly(v: Boolean) = viewModelScope.launch { store.setHttpsOnly(v) }
    fun setDoNotTrack(v: Boolean) = viewModelScope.launch { store.setDoNotTrack(v) }
    fun setRestoreSession(v: Boolean) = viewModelScope.launch { store.setRestoreSession(v) }
    fun setAskBeforeDownload(v: Boolean) = viewModelScope.launch { store.setAskBeforeDownload(v) }
    fun setBackgroundPlayback(v: Boolean) = viewModelScope.launch { store.setBackgroundPlayback(v) }
    fun setYtAdSkip(v: Boolean) = viewModelScope.launch { store.setYtAdSkip(v) }
    fun setReducedMotion(v: Boolean) = viewModelScope.launch { store.setReducedMotion(v) }

    // Layout + home
    fun setUrlBarBottom(v: Boolean) = viewModelScope.launch { store.setUrlBarBottom(v) }
    fun setShowClock(v: Boolean) = viewModelScope.launch { store.setShowClock(v) }
    fun setClock24h(v: Boolean) = viewModelScope.launch { store.setClock24h(v) }
    fun setClockSize(v: Int) = viewModelScope.launch { store.setClockSize(v) }
    fun setShowDate(v: Boolean) = viewModelScope.launch { store.setShowDate(v) }
    fun setShowGreeting(v: Boolean) = viewModelScope.launch { store.setShowGreeting(v) }
    fun setUserName(v: String) = viewModelScope.launch { store.setUserName(v) }
    fun setShowSearchBar(v: Boolean) = viewModelScope.launch { store.setShowSearchBar(v) }
    fun setShowShieldCard(v: Boolean) = viewModelScope.launch { store.setShowShieldCard(v) }
    fun setShowShortcuts(v: Boolean) = viewModelScope.launch { store.setShowShortcuts(v) }
    fun setTileStyle(v: String) = viewModelScope.launch { store.setTileStyle(v) }
    fun setTileCount(v: Int) = viewModelScope.launch { store.setTileCount(v) }
    fun setTileLabels(v: Boolean) = viewModelScope.launch { store.setTileLabels(v) }
    fun setCardOpacity(v: Float) = viewModelScope.launch { store.setCardOpacity(v) }
    fun setHomeAlign(v: String) = viewModelScope.launch { store.setHomeAlign(v) }

    // Motion / perf
    fun setAnimStyle(v: String) = viewModelScope.launch { store.setAnimStyle(v) }
    fun setLiteMode(v: Boolean) = viewModelScope.launch { store.setLiteMode(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { store.setHaptics(v) }

    fun resetAll() = viewModelScope.launch { store.resetAll() }
}

enum class SettingsSection(val title: String) {
    HOME("Home page"),
    LAYOUT("Layout"),
    MOTION("Animations & performance"),
    PRIVACY("Privacy & security"),
    MEDIA("Media & background play"),
    GENERAL("General")
}

// ───────────────────────────────────────────────────────────────────────────
// Hub
// ───────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val colors = LocalAstraColors.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { HubRow(Icons.Filled.Home, "Home page", "Clock, search, shortcuts, wallpaper, alignment") { navController.navigate(AstraRoutes.SET_HOME) } }
            item { HubRow(Icons.Filled.ViewAgenda, "Layout", "Address bar on top or bottom") { navController.navigate(AstraRoutes.SET_LAYOUT) } }
            item { HubRow(Icons.Filled.Speed, "Animations & performance", "Screen transitions, Lite mode for low-end phones") { navController.navigate(AstraRoutes.SET_MOTION) } }
            item { HubRow(Icons.Filled.Shield, "Privacy & security", "Ad blocking, trackers, cookies, pop-ups") { navController.navigate(AstraRoutes.SET_PRIVACY) } }
            item { HubRow(Icons.Filled.MusicNote, "Media & background play", "Keep YouTube playing with the screen off") { navController.navigate(AstraRoutes.SET_MEDIA) } }
            item { HubRow(Icons.Filled.Tune, "General", "Search engine, theme, downloads, reset") { navController.navigate(AstraRoutes.SET_GENERAL) } }
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = colors.accent.copy(alpha = 0.16f), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.55f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurface.copy(alpha = 0.4f))
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Sub-screens
// ───────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubScreen(
    navController: NavController,
    section: SettingsSection,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val colors = LocalAstraColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(section.title, fontWeight = FontWeight.SemiBold) },
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
                .padding(bottom = 24.dp)
        ) {
            when (section) {
                SettingsSection.HOME -> HomeSection(viewModel, navController)
                SettingsSection.LAYOUT -> LayoutSection(viewModel)
                SettingsSection.MOTION -> MotionSection(viewModel)
                SettingsSection.PRIVACY -> PrivacySection(viewModel)
                SettingsSection.MEDIA -> MediaSection(viewModel)
                SettingsSection.GENERAL -> GeneralSection(viewModel)
            }
        }
    }
}

@Composable
private fun HomeSection(vm: SettingsViewModel, nav: NavController) {
    val p = LocalUiPrefs.current
    val wallpaper by vm.wallpaperMode.collectAsState()

    Header("Wallpaper")
    ClickRow("Home wallpaper", WallpaperModes.label(wallpaper) + "  ·  gallery or preset") { nav.navigate(AstraRoutes.WALLPAPER) }

    Header("Clock & greeting")
    SwitchRow("Show clock", "Big time display on the home page", p.showClock, vm::setShowClock)
    if (p.showClock) {
        SwitchRow("24-hour time", "Show 15:44 instead of 3:44 pm", p.clock24h, vm::setClock24h)
        SliderRow("Clock size", "${p.clockSize} sp", p.clockSize.toFloat(), 36f..96f, 0) { vm.setClockSize(it.roundToInt()) }
        SwitchRow("Show date", "Weekday and date under the time", p.showDate, vm::setShowDate)
    }
    SwitchRow("Greeting", "Good morning / afternoon / evening", p.showGreeting, vm::setShowGreeting)
    if (p.showGreeting) {
        TextRow("Your name (optional)", p.userName, "e.g. Shivam", vm::setUserName)
    }

    Header("Sections")
    SwitchRow("Search bar", "Search box on the home page", p.showSearchBar, vm::setShowSearchBar)
    SwitchRow("Shield card", "Ads & trackers blocked counter", p.showShieldCard, vm::setShowShieldCard)
    SwitchRow("Shortcuts", "Top sites strip", p.showShortcuts, vm::setShowShortcuts)

    if (p.showShortcuts) {
        Header("Shortcuts")
        ChipRow("Icon shape", listOf("CIRCLE" to "Circle", "ROUNDED" to "Rounded", "SQUARE" to "Square"), p.tileStyle, vm::setTileStyle)
        SliderRow("Number of shortcuts", "${p.tileCount}", p.tileCount.toFloat(), 4f..10f, 5) { vm.setTileCount(it.roundToInt()) }
        SwitchRow("Show names", "Label under each shortcut", p.tileLabels, vm::setTileLabels)
    }

    Header("Look")
    ChipRow("Content position", listOf("TOP" to "Top", "CENTER" to "Center"), p.homeAlign, vm::setHomeAlign)
    SliderRow("Card transparency", "${(p.cardOpacity * 100).roundToInt()}%", p.cardOpacity, 0f..0.8f, 0) { vm.setCardOpacity(it) }
}

@Composable
private fun LayoutSection(vm: SettingsViewModel) {
    val p = LocalUiPrefs.current
    Header("Address bar")
    ChipRow("Position", listOf("TOP" to "Top", "BOTTOM" to "Bottom"), if (p.urlBarBottom) "BOTTOM" else "TOP") {
        vm.setUrlBarBottom(it == "BOTTOM")
    }
    Hint("Bottom puts the address bar right above the navigation buttons, so you can reach it with your thumb on tall phones.")
}

@Composable
private fun MotionSection(vm: SettingsViewModel) {
    val p = LocalUiPrefs.current
    Header("Performance")
    SwitchRow(
        "Lite mode",
        "Best for low-end phones: shorter animations, no blur or heavy effects, less battery use and heat",
        p.liteMode, vm::setLiteMode
    )
    SwitchRow("Reduce motion", "Turn off all animations", p.reducedMotion, vm::setReducedMotion)

    Header("Screen transitions")
    ChipRow("Style", AnimStyles.all, p.animStyle, vm::setAnimStyle)
    if (p.liteMode && p.animStyle != AnimStyles.NONE) {
        Hint("Lite mode is on, so transitions use a short fade. Turn Lite mode off to use Slide or Zoom.")
    }

    Header("Feedback")
    SwitchRow("Haptic feedback", "Small vibration on taps", p.haptics, vm::setHaptics)
}

@Composable
private fun PrivacySection(vm: SettingsViewModel) {
    val tracking by vm.trackingProtection.collectAsState()
    val ads by vm.adBlocking.collectAsState()
    val fp by vm.fingerprintProtection.collectAsState()
    val cookies by vm.blockThirdPartyCookies.collectAsState()
    val popups by vm.blockPopups.collectAsState()
    val https by vm.httpsOnly.collectAsState()
    val dnt by vm.doNotTrack.collectAsState()

    Header("Blocking")
    SwitchRow("Ad blocking", "Block ad networks, video ads and pop-ups", ads, vm::setAdBlocking)
    SwitchRow("Tracking protection", "Block known trackers while browsing", tracking, vm::setTrackingProtection)
    SwitchRow("Block pop-ups", "Stop sites opening unwanted windows", popups, vm::setBlockPopups)
    SwitchRow("Block third-party cookies", "Stop cross-site cookies from tracking you", cookies, vm::setBlockThirdPartyCookies)

    Header("Security")
    SwitchRow("Fingerprinting protection", "Reduce identifiable signals sites can read", fp, vm::setFingerprintProtection)
    SwitchRow("HTTPS-only mode", "Warn before loading insecure HTTP pages", https, vm::setHttpsOnly)
    SwitchRow("Send \"Do Not Track\"", "Ask sites not to track you (advisory only)", dnt, vm::setDoNotTrack)
}

@Composable
private fun MediaSection(vm: SettingsViewModel) {
    val bg by vm.backgroundPlayback.collectAsState()
    val yt by vm.ytAdSkip.collectAsState()

    Header("Background play")
    SwitchRow(
        "Play in background",
        "Keep audio playing when you switch apps or turn the screen off. Shows a notification with controls.",
        bg, vm::setBackgroundPlayback
    )
    Hint("Works on YouTube (m.youtube.com) and other video/music sites. If your phone still stops playback, set Astra's battery usage to \"Unrestricted\" in Android settings.")

    Header("YouTube")
    SwitchRow("Skip YouTube ads", "Remove video ads and skip them automatically", yt, vm::setYtAdSkip)
}

@Composable
private fun GeneralSection(vm: SettingsViewModel) {
    val engine by vm.searchEngine.collectAsState()
    val suggestions by vm.searchSuggestions.collectAsState()
    val theme by vm.themeId.collectAsState()
    val restore by vm.restoreSession.collectAsState()
    val ask by vm.askBeforeDownload.collectAsState()

    Header("Search")
    ChipRow(
        "Default search engine",
        SearchEngine.entries.filter { it != SearchEngine.CUSTOM }.map { it.name to it.displayName },
        engine, vm::setSearchEngine
    )
    SwitchRow("Search suggestions", "Show suggestions as you type", suggestions, vm::setSearchSuggestions)

    Header("Appearance")
    ChipRow("Theme", AstraThemeId.entries.map { it.name to it.displayName }, theme, vm::setThemeId)

    Header("Tabs & downloads")
    SwitchRow("Restore previous session", "Reopen your tabs when Astra starts", restore, vm::setRestoreSession)
    SwitchRow("Ask before downloading", "Confirm each download's destination", ask, vm::setAskBeforeDownload)

    Header("Advanced")
    ClickRow("Reset all settings", "Restore every Astra setting to its default") { vm.resetAll() }
}

// ───────────────────────────────────────────────────────────────────────────
// Reusable rows
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(title: String) {
    val colors = LocalAstraColors.current
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(16.dp, 22.dp, 16.dp, 6.dp)
    )
}

@Composable
private fun Hint(text: String) {
    val colors = LocalAstraColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 4.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.55f))
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.55f))
    }
}

/** Slider with a live value label. [steps] = number of discrete stops between ends (0 = continuous). */
@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChangeFinished: (Float) -> Unit
) {
    val colors = LocalAstraColors.current
    // Local state while dragging; only write to DataStore when the finger lifts,
    // so we don't hammer disk (and recompose the whole app) on every pixel.
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChangeFinished(local) },
            valueRange = range,
            steps = steps
        )
    }
}

/** Wrap-around chip selector: one row of tappable pills, current one filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalAstraColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, label) ->
                val on = id == selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (on) colors.accent else colors.surfaceVariant,
                    border = if (on) null else BorderStroke(1.dp, colors.border),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelect(id) }
                ) {
                    Text(
                        label,
                        color = if (on) colors.background else colors.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextRow(title: String, value: String, placeholder: String, onCommit: (String) -> Unit) {
    val colors = LocalAstraColors.current
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(24); onCommit(it.take(24)) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
