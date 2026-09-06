package com.example.soarmcontroller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * User-facing theme preference. [SYSTEM] follows the OS setting; the top-bar
 * toggle flips to an explicit [LIGHT] / [DARK] override.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = AccentOrange,
    onPrimary = OnAccent,
    secondary = AccentOrange,
    onSecondary = OnAccent,
    background = LightBackground,
    onBackground = LightOn,
    surface = LightSurface,
    onSurface = LightOn,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnMuted,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = StatusError,
)

private val DarkColors = darkColorScheme(
    primary = AccentOrangeDark,
    onPrimary = OnAccent,
    secondary = AccentOrangeDark,
    onSecondary = OnAccent,
    background = DarkBackground,
    onBackground = DarkOn,
    surface = DarkSurface,
    onSurface = DarkOn,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = StatusError,
)

@Composable
fun SOArmControllerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
