package com.astra.browser.ui.newtab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astra.browser.data.repository.HistoryRepository
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.data.store.WallpaperStore
import com.astra.browser.domain.model.SearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NewTabViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    settingsStore: SettingsStore,
    wallpaperStore: WallpaperStore
) : ViewModel() {

    val recentSites = historyRepository.observeAll()
        .map { list -> list.map { it.url }.distinct().take(8) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalTrackersBlocked = settingsStore.totalTrackersBlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val showShortcuts = settingsStore.showShortcuts
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showClock = settingsStore.showClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Display name of the user's search engine, shown inside the home search bar. */
    val searchEngineName = settingsStore.searchEngine
        .map { name -> runCatching { SearchEngine.valueOf(name).displayName }.getOrDefault("Google") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Google")

    val wallpaperMode = settingsStore.wallpaperMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "NIGHT_SKY")

    val wallpaperDim = settingsStore.wallpaperDim
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val customWallpaper = wallpaperStore.custom
}
