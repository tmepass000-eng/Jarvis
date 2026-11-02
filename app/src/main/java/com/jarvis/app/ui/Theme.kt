package com.jarvis.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JarvisColors {
    val Charcoal = Color(0xFF121212)
    val Surface = Color(0xFF1C1C1E)
    val Saffron = Color(0xFFFF9933)
    val SoftGold = Color(0xFFFFC266)
    val Text = Color(0xFFF5F0E8)
    val Danger = Color(0xFFE5484D)
    val Muted = Color(0xFF9A948A)
}

private val scheme = darkColorScheme(
    primary = JarvisColors.Saffron,
    onPrimary = JarvisColors.Charcoal,
    secondary = JarvisColors.SoftGold,
    onSecondary = JarvisColors.Charcoal,
    background = JarvisColors.Charcoal,
    onBackground = JarvisColors.Text,
    surface = JarvisColors.Surface,
    onSurface = JarvisColors.Text,
    surfaceVariant = JarvisColors.Surface,
    onSurfaceVariant = JarvisColors.Muted,
    error = JarvisColors.Danger,
    outline = JarvisColors.Muted
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
