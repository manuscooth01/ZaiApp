package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = GroqOrange,
    secondary = GroqSecondary,
    background = GroqBackground,
    surface = GroqSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GroqOnBackground,
    onSurface = GroqOnSurface,
    surfaceVariant = GroqSurfaceVariant,
    onSurfaceVariant = GroqTextSecondary,
    outline = GroqOutline
)

private val LightColorScheme = lightColorScheme(
    primary = GroqOrange,
    secondary = GroqSecondary,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0D0D0D),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
