package com.astra.browser.data.store

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "astra_settings")

@Singleton
class SettingsStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private object Keys {
        val HOMEPAGE = stringPreferencesKey("homepage")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val CUSTOM_SEARCH_URL = stringPreferencesKey("custom_search_url")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent_color")
        val CUSTOM_BACKGROUND = intPreferencesKey("custom_background_color")
        val CUSTOM_SURFACE = intPreferencesKey("custom_surface_color")
        val CUSTOM_TEXT = intPreferencesKey("custom_text_color")
        val TRACKING_PROTECTION = booleanPreferencesKey("tracking_protection")
        val AD_BLOCKING = booleanPreferencesKey("ad_blocking")
        val FINGERPRINT_PROTECTION = booleanPreferencesKey("fingerprint_protection")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups")
        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val DO_NOT_TRACK = booleanPreferencesKey("do_not_track")
        val RESTORE_SESSION = booleanPreferencesKey("restore_session")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val TEXT_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val TOTAL_TRACKERS_BLOCKED = intPreferencesKey("total_trackers_blocked")
        val SHOW_SHORTCUTS = booleanPreferencesKey("show_shortcuts")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_RECENT_SITES = booleanPreferencesKey("show_recent_sites")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val WALLPAPER_MODE = stringPreferencesKey("wallpaper_mode")
        val WALLPAPER_VERSION = longPreferencesKey("wallpaper_version")
        val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
        val SHIELD_OFF_SITES = stringSetPreferencesKey("shield_off_sites")
        val DESKTOP_SITES = stringSetPreferencesKey("desktop_sites")

        // ---- Layout / home customisation ----
        val URL_BAR_BOTTOM = booleanPreferencesKey("url_bar_bottom")
        val SHOW_SHIELD_CARD = booleanPreferencesKey("show_shield_card")
        val SHOW_SEARCH_BAR = booleanPreferencesKey("show_search_bar")
        val SHOW_GREETING = booleanPreferencesKey("show_greeting")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val CLOCK_24H = booleanPreferencesKey("clock_24h")
        val CLOCK_SIZE = intPreferencesKey("clock_size")            // sp
        val HOME_ALIGN = stringPreferencesKey("home_align")          // TOP / CENTER
        val TILE_STYLE = stringPreferencesKey("tile_style")          // CIRCLE / SQUARE / ROUNDED
        val TILE_COUNT = intPreferencesKey("tile_count")             // 4..10
        val TILE_LABELS = booleanPreferencesKey("tile_labels")
        val CARD_OPACITY = floatPreferencesKey("card_opacity")       // 0.0 .. 0.8
        val USER_NAME = stringPreferencesKey("user_name")

        // ---- Motion / performance ----
        val ANIM_STYLE = stringPreferencesKey("anim_style")          // NONE / FADE / SLIDE / SCALE
        val LITE_MODE = booleanPreferencesKey("lite_mode")           // low-end device mode
        val YT_AD_SKIP = booleanPreferencesKey("yt_ad_skip")
        val HAPTICS = booleanPreferencesKey("haptics")
    }

    val homepage: Flow<String> = context.dataStore.data.map { it[Keys.HOMEPAGE] ?: "astra://newtab" }
    val searchEngine: Flow<String> = context.dataStore.data.map { it[Keys.SEARCH_ENGINE] ?: "GOOGLE" }
    val customSearchUrl: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_SEARCH_URL] ?: "" }
    val searchSuggestionsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SEARCH_SUGGESTIONS] ?: true }
    val themeId: Flow<String> = context.dataStore.data.map { it[Keys.THEME_ID] ?: "SYSTEM" }
    val customAccent: Flow<Int?> = context.dataStore.data.map { it[Keys.CUSTOM_ACCENT] }
    val customBackground: Flow<Int?> = context.dataStore.data.map { it[Keys.CUSTOM_BACKGROUND] }
    val customSurface: Flow<Int?> = context.dataStore.data.map { it[Keys.CUSTOM_SURFACE] }
    val customText: Flow<Int?> = context.dataStore.data.map { it[Keys.CUSTOM_TEXT] }
    val trackingProtection: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKING_PROTECTION] ?: true }
    val adBlocking: Flow<Boolean> = context.dataStore.data.map { it[Keys.AD_BLOCKING] ?: true }
    val fingerprintProtection: Flow<Boolean> = context.dataStore.data.map { it[Keys.FINGERPRINT_PROTECTION] ?: true }
    val blockThirdPartyCookies: Flow<Boolean> = context.dataStore.data.map { it[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: true }
    val blockPopups: Flow<Boolean> = context.dataStore.data.map { it[Keys.BLOCK_POPUPS] ?: true }
    val httpsOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.HTTPS_ONLY] ?: false }
    val doNotTrack: Flow<Boolean> = context.dataStore.data.map { it[Keys.DO_NOT_TRACK] ?: false }
    val restoreSession: Flow<Boolean> = context.dataStore.data.map { it[Keys.RESTORE_SESSION] ?: true }
    val askBeforeDownload: Flow<Boolean> = context.dataStore.data.map { it[Keys.ASK_BEFORE_DOWNLOAD] ?: true }
    val textScale: Flow<Float> = context.dataStore.data.map { it[Keys.TEXT_SCALE] ?: 1.0f }
    val reducedMotion: Flow<Boolean> = context.dataStore.data.map { it[Keys.REDUCED_MOTION] ?: false }
    val totalTrackersBlocked: Flow<Int> = context.dataStore.data.map { it[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0 }
    val showShortcuts: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_SHORTCUTS] ?: true }
    val showClock: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_CLOCK] ?: true }
    val showRecentSites: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_RECENT_SITES] ?: true }
    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKGROUND_PLAYBACK] ?: true }
    val wallpaperMode: Flow<String> = context.dataStore.data.map { it[Keys.WALLPAPER_MODE] ?: "NIGHT_SKY" }
    val wallpaperVersion: Flow<Long> = context.dataStore.data.map { it[Keys.WALLPAPER_VERSION] ?: 0L }
    /** Hosts where the user turned Shield OFF (blocking disabled just for that site). */
    val shieldOffSites: Flow<Set<String>> = context.dataStore.data.map { it[Keys.SHIELD_OFF_SITES] ?: emptySet() }
    /** Hosts where the user asked for the desktop version of the site. */
    val desktopSites: Flow<Set<String>> = context.dataStore.data.map { it[Keys.DESKTOP_SITES] ?: emptySet() }
    val wallpaperDim: Flow<Float> = context.dataStore.data.map { it[Keys.WALLPAPER_DIM] ?: 0f }

    val urlBarBottom: Flow<Boolean> = context.dataStore.data.map { it[Keys.URL_BAR_BOTTOM] ?: false }
    val showShieldCard: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_SHIELD_CARD] ?: true }
    val showSearchBar: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_SEARCH_BAR] ?: true }
    val showGreeting: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_GREETING] ?: false }
    val showDate: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DATE] ?: true }
    val clock24h: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLOCK_24H] ?: false }
    val clockSize: Flow<Int> = context.dataStore.data.map { it[Keys.CLOCK_SIZE] ?: 64 }
    val homeAlign: Flow<String> = context.dataStore.data.map { it[Keys.HOME_ALIGN] ?: "TOP" }
    val tileStyle: Flow<String> = context.dataStore.data.map { it[Keys.TILE_STYLE] ?: "CIRCLE" }
    val tileCount: Flow<Int> = context.dataStore.data.map { it[Keys.TILE_COUNT] ?: 8 }
    val tileLabels: Flow<Boolean> = context.dataStore.data.map { it[Keys.TILE_LABELS] ?: true }
    val cardOpacity: Flow<Float> = context.dataStore.data.map { it[Keys.CARD_OPACITY] ?: 0.40f }
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val animStyle: Flow<String> = context.dataStore.data.map { it[Keys.ANIM_STYLE] ?: "FADE" }
    val liteMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.LITE_MODE] ?: true }
    val ytAdSkip: Flow<Boolean> = context.dataStore.data.map { it[Keys.YT_AD_SKIP] ?: true }
    val haptics: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS] ?: true }

    suspend fun setHomepage(value: String) = edit { it[Keys.HOMEPAGE] = value }
    suspend fun setSearchEngine(value: String) = edit { it[Keys.SEARCH_ENGINE] = value }
    suspend fun setCustomSearchUrl(value: String) = edit { it[Keys.CUSTOM_SEARCH_URL] = value }
    suspend fun setSearchSuggestions(value: Boolean) = edit { it[Keys.SEARCH_SUGGESTIONS] = value }
    suspend fun setThemeId(value: String) = edit { it[Keys.THEME_ID] = value }
    suspend fun setCustomTheme(accent: Int, background: Int, surface: Int, text: Int) = edit {
        it[Keys.CUSTOM_ACCENT] = accent
        it[Keys.CUSTOM_BACKGROUND] = background
        it[Keys.CUSTOM_SURFACE] = surface
        it[Keys.CUSTOM_TEXT] = text
    }
    suspend fun setTrackingProtection(value: Boolean) = edit { it[Keys.TRACKING_PROTECTION] = value }
    suspend fun setAdBlocking(value: Boolean) = edit { it[Keys.AD_BLOCKING] = value }
    suspend fun setFingerprintProtection(value: Boolean) = edit { it[Keys.FINGERPRINT_PROTECTION] = value }
    suspend fun setBlockThirdPartyCookies(value: Boolean) = edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = value }
    suspend fun setBlockPopups(value: Boolean) = edit { it[Keys.BLOCK_POPUPS] = value }
    suspend fun setHttpsOnly(value: Boolean) = edit { it[Keys.HTTPS_ONLY] = value }
    suspend fun setDoNotTrack(value: Boolean) = edit { it[Keys.DO_NOT_TRACK] = value }
    suspend fun setRestoreSession(value: Boolean) = edit { it[Keys.RESTORE_SESSION] = value }
    suspend fun setAskBeforeDownload(value: Boolean) = edit { it[Keys.ASK_BEFORE_DOWNLOAD] = value }
    suspend fun setTextScale(value: Float) = edit { it[Keys.TEXT_SCALE] = value }
    suspend fun setReducedMotion(value: Boolean) = edit { it[Keys.REDUCED_MOTION] = value }
    suspend fun incrementTrackersBlocked(by: Int = 1) = edit {
        it[Keys.TOTAL_TRACKERS_BLOCKED] = (it[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0) + by
    }
    suspend fun setShowShortcuts(value: Boolean) = edit { it[Keys.SHOW_SHORTCUTS] = value }
    suspend fun setShowClock(value: Boolean) = edit { it[Keys.SHOW_CLOCK] = value }
    suspend fun setShowRecentSites(value: Boolean) = edit { it[Keys.SHOW_RECENT_SITES] = value }
    suspend fun setBackgroundPlayback(value: Boolean) = edit { it[Keys.BACKGROUND_PLAYBACK] = value }
    suspend fun setWallpaperMode(value: String) = edit { it[Keys.WALLPAPER_MODE] = value }
    suspend fun setWallpaperVersion(value: Long) = edit { it[Keys.WALLPAPER_VERSION] = value }
    suspend fun setWallpaperDim(value: Float) = edit { it[Keys.WALLPAPER_DIM] = value }
    suspend fun setShieldEnabledForSite(host: String, enabled: Boolean) = edit {
        val cur = it[Keys.SHIELD_OFF_SITES] ?: emptySet()
        it[Keys.SHIELD_OFF_SITES] = if (enabled) cur - host else cur + host
    }
    suspend fun setDesktopForSite(host: String, desktop: Boolean) = edit {
        val cur = it[Keys.DESKTOP_SITES] ?: emptySet()
        it[Keys.DESKTOP_SITES] = if (desktop) cur + host else cur - host
    }

    suspend fun setUrlBarBottom(v: Boolean) = edit { it[Keys.URL_BAR_BOTTOM] = v }
    suspend fun setShowShieldCard(v: Boolean) = edit { it[Keys.SHOW_SHIELD_CARD] = v }
    suspend fun setShowSearchBar(v: Boolean) = edit { it[Keys.SHOW_SEARCH_BAR] = v }
    suspend fun setShowGreeting(v: Boolean) = edit { it[Keys.SHOW_GREETING] = v }
    suspend fun setShowDate(v: Boolean) = edit { it[Keys.SHOW_DATE] = v }
    suspend fun setClock24h(v: Boolean) = edit { it[Keys.CLOCK_24H] = v }
    suspend fun setClockSize(v: Int) = edit { it[Keys.CLOCK_SIZE] = v.coerceIn(36, 96) }
    suspend fun setHomeAlign(v: String) = edit { it[Keys.HOME_ALIGN] = v }
    suspend fun setTileStyle(v: String) = edit { it[Keys.TILE_STYLE] = v }
    suspend fun setTileCount(v: Int) = edit { it[Keys.TILE_COUNT] = v.coerceIn(4, 10) }
    suspend fun setTileLabels(v: Boolean) = edit { it[Keys.TILE_LABELS] = v }
    suspend fun setCardOpacity(v: Float) = edit { it[Keys.CARD_OPACITY] = v.coerceIn(0f, 0.8f) }
    suspend fun setUserName(v: String) = edit { it[Keys.USER_NAME] = v.take(24) }
    suspend fun setAnimStyle(v: String) = edit { it[Keys.ANIM_STYLE] = v }
    suspend fun setLiteMode(v: Boolean) = edit { it[Keys.LITE_MODE] = v }
    suspend fun setYtAdSkip(v: Boolean) = edit { it[Keys.YT_AD_SKIP] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.HAPTICS] = v }

    suspend fun resetAll() = context.dataStore.edit { it.clear() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
