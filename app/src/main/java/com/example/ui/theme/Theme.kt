package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberPink,
    tertiary = GridSpace,
    background = DarkBackground,
    surface = CardSpace,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = TextLight,
    onSurface = TextLight
)

@Composable
fun RabiyaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
