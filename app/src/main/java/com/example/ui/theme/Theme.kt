package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD4A5FF),       // Warm Lavender/Violet
    secondary = Color(0xFF9D4EDD),     // Electric Purple
    tertiary = Color(0xFFFF85A1),      // Soft Rose Accent
    background = Color(0xFF090616),    // Deep obsidian dark violet
    surface = Color(0xFF141029),       // Elevated container dark card
    onPrimary = Color(0xFF3C006B),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFECE7FF),
    onSurface = Color(0xFFDFD9FF),
    surfaceVariant = Color(0xFF1F1B38),
    onSurfaceVariant = Color(0xFFD0C9E8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF7E1AF5),
    secondary = Color(0xFF9D4EDD),
    tertiary = Color(0xFFFF85A1),
    background = Color(0xFFFAF9FF),
    surface = Color(0xFFF1EEFC),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF13101E),
    onSurface = Color(0xFF1D1B28)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for premium music atmosphere
  dynamicColor: Boolean = false, // Disable dynamic colors to keep brand consistency
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
