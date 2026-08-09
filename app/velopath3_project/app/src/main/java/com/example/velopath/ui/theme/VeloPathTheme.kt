package com.example.velopath.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PineGreen,
    onPrimary = Snow,
    secondary = WaveBlue,
    onSecondary = Snow,
    tertiary = DeepSea,
    background = Snow,
    onBackground = DeepSea,
    surface = Mist,
    onSurface = DeepSea
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5AD0B2),
    onPrimary = Midnight,
    secondary = Color(0xFF68C6F1),
    onSecondary = Midnight,
    tertiary = Ice,
    background = Midnight,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText
)

@Composable
fun VelopathTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VeloPathTypography,
        content = content
    )
}
