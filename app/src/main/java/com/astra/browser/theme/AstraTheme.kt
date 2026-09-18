package com.astra.browser.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val LocalAstraColors = staticCompositionLocalOf { AstraThemeCatalog.Light }

@Composable
fun AstraTheme(
    themeId: AstraThemeId,
    customColors: AstraColorScheme? = null,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val scheme = if (themeId == AstraThemeId.CUSTOM && customColors != null) {
        customColors
    } else {
        AstraThemeCatalog.forId(themeId, systemIsDark)
    }

    val materialScheme = if (scheme.isDark) {
        darkColorScheme(
            primary = scheme.accent,
            background = scheme.background,
            surface = scheme.surface,
            surfaceVariant = scheme.surfaceVariant,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface
        )
    } else {
        lightColorScheme(
            primary = scheme.accent,
            background = scheme.background,
            surface = scheme.surface,
            surfaceVariant = scheme.surfaceVariant,
            onBackground = scheme.onBackground,
            onSurface = scheme.onSurface
        )
    }

    CompositionLocalProvider(LocalAstraColors provides scheme) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AstraTypography,
            content = content
        )
    }
}

val AstraTypography = androidx.compose.material3.Typography()
