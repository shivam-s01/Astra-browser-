package com.astra.browser.theme

import androidx.compose.ui.graphics.Color

enum class AstraThemeId(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System"),
    AMOLED("AMOLED"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    MIDNIGHT("Midnight"),
    AURORA("Aurora"),
    CUSTOM("Custom")
}

data class AstraColorScheme(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val toolbar: Color,
    val tabActive: Color,
    val tabInactive: Color,
    val border: Color,
    val isDark: Boolean
)

object AstraThemeCatalog {

    val Light = AstraColorScheme(
        accent = Color(0xFF3D5AFE),
        background = Color(0xFFFBFBFD),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0F1F5),
        onBackground = Color(0xFF1A1B1F),
        onSurface = Color(0xFF1A1B1F),
        toolbar = Color(0xFFFFFFFF),
        tabActive = Color(0xFFFFFFFF),
        tabInactive = Color(0xFFECEDF2),
        border = Color(0xFFE2E4EA),
        isDark = false
    )

    val Dark = AstraColorScheme(
        accent = Color(0xFF7B93FF),
        background = Color(0xFF16171B),
        surface = Color(0xFF1E1F24),
        surfaceVariant = Color(0xFF27282E),
        onBackground = Color(0xFFECEDF2),
        onSurface = Color(0xFFECEDF2),
        toolbar = Color(0xFF1E1F24),
        tabActive = Color(0xFF27282E),
        tabInactive = Color(0xFF1A1B1F),
        border = Color(0xFF303138),
        isDark = true
    )

    val Amoled = AstraColorScheme(
        accent = Color(0xFF7B93FF),
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF161616),
        onBackground = Color(0xFFEDEDED),
        onSurface = Color(0xFFEDEDED),
        toolbar = Color(0xFF000000),
        tabActive = Color(0xFF161616),
        tabInactive = Color(0xFF000000),
        border = Color(0xFF1F1F1F),
        isDark = true
    )

    val Ocean = AstraColorScheme(
        accent = Color(0xFF00B4D8),
        background = Color(0xFF071A2B),
        surface = Color(0xFF0D2A44),
        surfaceVariant = Color(0xFF123A5C),
        onBackground = Color(0xFFE3F3FA),
        onSurface = Color(0xFFE3F3FA),
        toolbar = Color(0xFF0D2A44),
        tabActive = Color(0xFF123A5C),
        tabInactive = Color(0xFF071A2B),
        border = Color(0xFF1B4A70),
        isDark = true
    )

    val Forest = AstraColorScheme(
        accent = Color(0xFF52B788),
        background = Color(0xFF0E1B14),
        surface = Color(0xFF16281E),
        surfaceVariant = Color(0xFF1E3528),
        onBackground = Color(0xFFE4F1E8),
        onSurface = Color(0xFFE4F1E8),
        toolbar = Color(0xFF16281E),
        tabActive = Color(0xFF1E3528),
        tabInactive = Color(0xFF0E1B14),
        border = Color(0xFF294634),
        isDark = true
    )

    val Sunset = AstraColorScheme(
        accent = Color(0xFFFF6B4A),
        background = Color(0xFFFFF6F1),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFFFE9DE),
        onBackground = Color(0xFF2B1A12),
        onSurface = Color(0xFF2B1A12),
        toolbar = Color(0xFFFFFFFF),
        tabActive = Color(0xFFFFFFFF),
        tabInactive = Color(0xFFFFE3D4),
        border = Color(0xFFFFD6C0),
        isDark = false
    )

    val Midnight = AstraColorScheme(
        accent = Color(0xFF9D7BFF),
        background = Color(0xFF0B0B17),
        surface = Color(0xFF131325),
        surfaceVariant = Color(0xFF1B1B33),
        onBackground = Color(0xFFE7E5F5),
        onSurface = Color(0xFFE7E5F5),
        toolbar = Color(0xFF131325),
        tabActive = Color(0xFF1B1B33),
        tabInactive = Color(0xFF0B0B17),
        border = Color(0xFF262647),
        isDark = true
    )

    val Aurora = AstraColorScheme(
        accent = Color(0xFF3FE0C5),
        background = Color(0xFF0A1420),
        surface = Color(0xFF10202F),
        surfaceVariant = Color(0xFF163044),
        onBackground = Color(0xFFE1F7F2),
        onSurface = Color(0xFFE1F7F2),
        toolbar = Color(0xFF10202F),
        tabActive = Color(0xFF163044),
        tabInactive = Color(0xFF0A1420),
        border = Color(0xFF1E3C52),
        isDark = true
    )

    fun forId(id: AstraThemeId, systemIsDark: Boolean): AstraColorScheme = when (id) {
        AstraThemeId.LIGHT -> Light
        AstraThemeId.DARK -> Dark
        AstraThemeId.SYSTEM -> if (systemIsDark) Dark else Light
        AstraThemeId.AMOLED -> Amoled
        AstraThemeId.OCEAN -> Ocean
        AstraThemeId.FOREST -> Forest
        AstraThemeId.SUNSET -> Sunset
        AstraThemeId.MIDNIGHT -> Midnight
        AstraThemeId.AURORA -> Aurora
        AstraThemeId.CUSTOM -> Dark // overridden by custom values at call site
    }
}
