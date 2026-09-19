package com.astra.browser.ui.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.astra.browser.data.store.SettingsStore
import com.astra.browser.ui.motion.MotionSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Everything that customises how Astra LOOKS and MOVES, as one immutable
 * snapshot. Built by combining the DataStore flows once (in MainActivity) and
 * provided down the tree via [LocalUiPrefs], so no screen has to collect a
 * dozen flows on its own -- fewer collectors = fewer recompositions = cooler
 * phone.
 */
@Immutable
data class UiPrefs(
    // Layout
    val urlBarBottom: Boolean = false,

    // Home page
    val showClock: Boolean = true,
    val clock24h: Boolean = false,
    val clockSize: Int = 64,
    val showDate: Boolean = true,
    val showGreeting: Boolean = false,
    val userName: String = "",
    val showSearchBar: Boolean = true,
    val showShieldCard: Boolean = true,
    val showShortcuts: Boolean = true,
    val tileStyle: String = "CIRCLE",
    val tileCount: Int = 8,
    val tileLabels: Boolean = true,
    val cardOpacity: Float = 0.40f,
    val homeAlign: String = "TOP",

    // Motion / perf
    val animStyle: String = "FADE",
    val liteMode: Boolean = true,
    val reducedMotion: Boolean = false,
    val haptics: Boolean = true
) {
    val motion: MotionSpec get() = MotionSpec.resolve(animStyle, liteMode, reducedMotion)
}

val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

/** Combines every relevant flow into one [UiPrefs] stream (collected once at the root). */
fun SettingsStore.uiPrefsFlow(): Flow<UiPrefs> {
    val layout: Flow<UiPrefs> = combine(
        urlBarBottom, showClock, clock24h, clockSize, showDate
    ) { bottom, clock, h24, size, date ->
        UiPrefs(urlBarBottom = bottom, showClock = clock, clock24h = h24, clockSize = size, showDate = date)
    }

    val home = combine(
        showGreeting, userName, showSearchBar, showShieldCard, showShortcuts
    ) { greet, name, search, shield, shortcuts ->
        arrayOf<Any>(greet, name, search, shield, shortcuts)
    }

    val tiles = combine(
        tileStyle, tileCount, tileLabels, cardOpacity, homeAlign
    ) { style, count, labels, opacity, align ->
        arrayOf<Any>(style, count, labels, opacity, align)
    }

    val motion = combine(
        animStyle, liteMode, reducedMotion, haptics
    ) { anim, lite, reduced, hap ->
        arrayOf<Any>(anim, lite, reduced, hap)
    }

    return combine(layout, home, tiles, motion) { base, h, t, m ->
        base.copy(
            showGreeting = h[0] as Boolean,
            userName = h[1] as String,
            showSearchBar = h[2] as Boolean,
            showShieldCard = h[3] as Boolean,
            showShortcuts = h[4] as Boolean,
            tileStyle = t[0] as String,
            tileCount = t[1] as Int,
            tileLabels = t[2] as Boolean,
            cardOpacity = t[3] as Float,
            homeAlign = t[4] as String,
            animStyle = m[0] as String,
            liteMode = m[1] as Boolean,
            reducedMotion = m[2] as Boolean,
            haptics = m[3] as Boolean
        )
    }.distinctUntilChanged()
}

@Composable
fun rememberUiPrefs(store: SettingsStore): State<UiPrefs> =
    produceState(initialValue = UiPrefs(), store) {
        store.uiPrefsFlow().collect { value = it }
    }
