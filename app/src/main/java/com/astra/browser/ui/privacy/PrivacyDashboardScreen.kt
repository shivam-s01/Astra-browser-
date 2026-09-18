package com.astra.browser.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(store: SettingsStore) : ViewModel() {
    val totalTrackersBlocked = store.totalTrackersBlocked.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val trackingProtection = store.trackingProtection.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val adBlocking = store.adBlocking.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val fingerprintProtection = store.fingerprintProtection.stateIn(viewModelScope, SharingStarted.Eagerly, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(navController: NavController, viewModel: PrivacyDashboardViewModel = hiltViewModel()) {
    val colors = LocalAstraColors.current
    val trackersBlocked by viewModel.totalTrackersBlocked.collectAsState()
    val tracking by viewModel.trackingProtection.collectAsState()
    val ads by viewModel.adBlocking.collectAsState()
    val fingerprint by viewModel.fingerprintProtection.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Astra Privacy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.toolbar)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$trackersBlocked",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        "trackers blocked so far",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            ProtectionStatusRow("Tracking protection", tracking, colors)
            ProtectionStatusRow("Ad blocking", ads, colors)
            ProtectionStatusRow("Fingerprinting protection", fingerprint, colors)

            Spacer(Modifier.height(24.dp))

            Text(
                "What private browsing protects — and what it doesn't",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Private tabs aren't saved to your history, bookmarks, or search suggestions, and their cookies are cleared when you close them. " +
                    "Private browsing does not hide your activity from your internet provider, employer, school network, or the websites you visit — " +
                    "it only keeps that activity out of Astra's own local history.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ProtectionStatusRow(label: String, enabled: Boolean, colors: com.astra.browser.theme.AstraColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        Text(
            if (enabled) "Active" else "Off",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.accent else colors.onSurface.copy(alpha = 0.4f)
        )
    }
}
