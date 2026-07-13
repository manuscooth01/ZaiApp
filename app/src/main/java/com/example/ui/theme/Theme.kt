package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = GroqPrimary,
    secondary = GroqSecondary,
    background = GroqBackground,
    surface = GroqSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = GroqOnBackground,
    onSurface = GroqOnSurface,
    surfaceVariant = GroqSurfaceVariant,
    onSurfaceVariant = GroqOnSurfaceVariant,
    outline = GroqOutline
  )

private val LightColorScheme = DarkColorScheme // Keep it dark by default as requested

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark by default
  dynamicColor: Boolean = false, // Disable dynamic colors to enforce the theme
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
