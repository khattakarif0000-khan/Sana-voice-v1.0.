package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SanaColorScheme = darkColorScheme(
    primary = SanaNeonPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D0820),
    onPrimaryContainer = SanaNeonPink,
    secondary = SanaNeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003840),
    onSecondaryContainer = SanaNeonCyan,
    tertiary = SanaNeonBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0D2556),
    onTertiaryContainer = Color(0xFF90CAF9),
    background = SanaBlack,
    onBackground = SanaTextPrimary,
    surface = SanaSurfaceDark,
    onSurface = SanaTextPrimary,
    surfaceVariant = SanaSurfaceCard,
    onSurfaceVariant = SanaTextSecondary,
    outline = SanaBorderGlow,
    error = SanaNeonRed,
    onError = Color.White,
    errorContainer = Color(0xFF4B0A11),
    onErrorContainer = SanaNeonRed
)

@Composable
fun SanaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SanaColorScheme,
        typography = Typography,
        content = content
    )
}
