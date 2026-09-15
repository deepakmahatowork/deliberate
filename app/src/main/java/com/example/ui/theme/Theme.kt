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

private val DarkColorScheme =
  darkColorScheme(
    primary = TextPrimaryDark,
    onPrimary = CharcoalDark,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentSageDark,
    onSecondary = CharcoalDark,
    background = CharcoalDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TextPrimaryLight,
    onPrimary = SurfaceLight,
    primaryContainer = AlabasterLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = AccentSageLight,
    onSecondary = SurfaceLight,
    background = AlabasterLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = AlabasterLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
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
