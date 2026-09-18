package com.astra.browser.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.domain.model.SearchEngine
import com.astra.browser.theme.AstraThemeId
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore) : ViewModel() {
    val homepage = store.homepage.stateIn(viewModelScope, SharingStarted.Eagerly, "astra://newtab")
    val searchEngine = store.searchEngine.stateIn(viewModelScope, SharingStarted.Eagerly, "GOOGLE")
    val searchSuggestions = store.searchSuggestionsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val themeId = store.themeId.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val trackingProtection = store.trackingProtection.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val adBlocking = store.adBlocking.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val fingerprintProtection = store.fingerprintProtection.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val blockThirdPartyCookies = store.blockThirdPartyCookies.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val blockPopups = store.blockPopups.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val httpsOnly = store.httpsOnly.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val doNotTrack = store.doNotTrack.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val restoreSession = store.restoreSession.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val askBeforeDownload = store.askBeforeDownload.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val reducedMotion = store.reducedMotion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val textScale = store.textScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    fun setHomepage(v: String) = viewModelScope.launch { store.setHomepage(v) }
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
    fun setReducedMotion(v: Boolean) = viewModelScope.launch { store.setReducedMotion(v) }
    fun setTextScale(v: Float) = viewModelScope.launch { store.setTextScale(v) }
    fun resetAll() = viewModelScope.launch { store.resetAll() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Astra Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Search") }
            item {
                val engine by viewModel.searchEngine.collectAsState()
                DropdownSetting(
                    label = "Default search engine",
                    options = SearchEngine.entries.filter { it != SearchEngine.CUSTOM }.map { it.name to it.displayName },
                    selected = engine,
                    onSelect = viewModel::setSearchEngine
                )
            }
            item {
                val suggestions by viewModel.searchSuggestions.collectAsState()
                SwitchSetting("Search suggestions", "Show suggestions as you type", suggestions, viewModel::setSearchSuggestions)
            }

            item { SectionHeader("Privacy & Security") }
            item {
                val v by viewModel.trackingProtection.collectAsState()
                SwitchSetting("Tracking protection", "Block known trackers while browsing", v, viewModel::setTrackingProtection)
            }
            item {
                val v by viewModel.adBlocking.collectAsState()
                SwitchSetting("Ad blocking", "Block known ad-serving domains", v, viewModel::setAdBlocking)
            }
            item {
                val v by viewModel.fingerprintProtection.collectAsState()
                SwitchSetting("Fingerprinting protection", "Reduce identifiable signals sites can read", v, viewModel::setFingerprintProtection)
            }
            item {
                val v by viewModel.blockThirdPartyCookies.collectAsState()
                SwitchSetting("Block third-party cookies", "Stop cross-site cookies from tracking you", v, viewModel::setBlockThirdPartyCookies)
            }
            item {
                val v by viewModel.blockPopups.collectAsState()
                SwitchSetting("Block pop-ups", "Prevent sites from opening pop-up windows", v, viewModel::setBlockPopups)
            }
            item {
                val v by viewModel.httpsOnly.collectAsState()
                SwitchSetting("HTTPS-only mode", "Warn before loading insecure HTTP pages", v, viewModel::setHttpsOnly)
            }
            item {
                val v by viewModel.doNotTrack.collectAsState()
                SwitchSetting("Send \"Do Not Track\"", "Ask sites not to track you (advisory only)", v, viewModel::setDoNotTrack)
            }

            item { SectionHeader("Appearance") }
            item {
                val theme by viewModel.themeId.collectAsState()
                DropdownSetting(
                    label = "Theme",
                    options = AstraThemeId.entries.map { it.name to it.displayName },
                    selected = theme,
                    onSelect = viewModel::setThemeId
                )
            }

            item { SectionHeader("Tabs") }
            item {
                val v by viewModel.restoreSession.collectAsState()
                SwitchSetting("Restore previous session", "Reopen your tabs when Astra starts", v, viewModel::setRestoreSession)
            }

            item { SectionHeader("Downloads") }
            item {
                val v by viewModel.askBeforeDownload.collectAsState()
                SwitchSetting("Ask before downloading", "Confirm each download's destination", v, viewModel::setAskBeforeDownload)
            }

            item { SectionHeader("Accessibility") }
            item {
                val v by viewModel.reducedMotion.collectAsState()
                SwitchSetting("Reduced motion", "Minimize animations throughout Astra", v, viewModel::setReducedMotion)
            }

            item { SectionHeader("Advanced") }
            item {
                SettingRow(title = "Reset all settings", subtitle = "Restore every Astra setting to its default") {
                    viewModel.resetAll()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalAstraColors.current
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = colors.accent,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp)
    )
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalAstraColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.5f))
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAstraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickableCompat(onClick)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.5f))
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun DropdownSetting(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalAstraColors.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: selected

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickableCompat { expanded = true },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, color = colors.accent)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label2) ->
                DropdownMenuItem(text = { Text(label2) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
