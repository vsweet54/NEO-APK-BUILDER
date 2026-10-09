package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF00388F),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = NeonGreen,
    onTertiary = Color(0xFF003915),
    background = CyberBg,
    onBackground = TextWhite,
    surface = CyberSurface,
    onSurface = TextWhite,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextGray,
    error = CyberRed,
    onError = Color.White
)

@Composable
fun NeoApkBuilderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
