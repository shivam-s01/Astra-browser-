package com.astra.browser.ui.newtab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astra.browser.data.repository.HistoryRepository
import com.astra.browser.data.store.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NewTabViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    settingsStore: SettingsStore
) : ViewModel() {

    val recentSites = historyRepository.observeAll()
        .map { list -> list.map { it.url }.distinct().take(8) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalTrackersBlocked = settingsStore.totalTrackersBlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val showShortcuts = settingsStore.showShortcuts
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
}
