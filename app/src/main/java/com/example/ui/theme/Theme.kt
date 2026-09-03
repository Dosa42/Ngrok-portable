package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = CyanPrimaryLight,
  onPrimary = Color(0xFF00354E),
  primaryContainer = Color(0xFF004D70),
  onPrimaryContainer = Color(0xFFC7E7FF),
  secondary = CyanSecondaryLight,
  onSecondary = Color(0xFF003830),
  secondaryContainer = Color(0xFF005146),
  onSecondaryContainer = Color(0xFF88F8E2),
  tertiary = ElectricIndigo,
  background = BackgroundDark,
  onBackground = Color(0xFFF1F5F9),
  surface = SurfaceDark,
  onSurface = Color(0xFFF1F5F9),
  surfaceVariant = SurfaceDarkVariant,
  onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
  primary = CyanPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFC7E7FF),
  onPrimaryContainer = Color(0xFF001E2E),
  secondary = CyanSecondary,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF88F8E2),
  onSecondaryContainer = Color(0xFF00201B),
  tertiary = ElectricIndigo,
  background = BackgroundLight,
  onBackground = Color(0xFF0F172A),
  surface = SurfaceLight,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = SurfaceLightVariant,
  onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
