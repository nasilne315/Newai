package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkMinimalColorScheme = darkColorScheme(
    primary = SendButtonDarkBlue,
    onPrimary = TextPrimary,
    primaryContainer = UserBubbleDarkBlue,
    onPrimaryContainer = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    background = DarkBg,
    onBackground = TextPrimary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Pure minimal dark gray experience
    MaterialTheme(
        colorScheme = DarkMinimalColorScheme,
        typography = Typography,
        content = content
    )
}
