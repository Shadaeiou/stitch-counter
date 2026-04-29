package com.shadaeiou.stitchcounter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = BgBlack,
    secondary = Accent,
    background = BgBlack,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    error = AccentRed,
)

@Composable
fun StitchCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
