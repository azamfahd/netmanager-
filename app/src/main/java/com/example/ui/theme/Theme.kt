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
    primary = CyberGreenDark,
    onPrimary = Color(0xFF003820),
    primaryContainer = CyberGreenContainerDark,
    onPrimaryContainer = Color(0xFFA6FFD2),
    secondary = NetworkCyanLight,
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFB74D),
    background = SurfaceDark,
    surface = SurfaceDarkCard,
    surfaceVariant = SurfaceDarkVariant,
    onBackground = Color(0xFFE8F5EE),
    onSurface = Color(0xFFE8F5EE),
    error = Color(0xFFFF8A80)
)

private val LightColorScheme = lightColorScheme(
    primary = CyberGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = CyberGreenContainer,
    onPrimaryContainer = CyberOnGreenContainer,
    secondary = NetworkCyan,
    onSecondary = Color.White,
    secondaryContainer = NetworkCyanContainer,
    onSecondaryContainer = Color(0xFF003D33),
    tertiary = Color(0xFFF57C00),
    background = SurfaceLight,
    surface = SurfaceLightCard,
    surfaceVariant = SurfaceLightVariant,
    onBackground = Color(0xFF10221A),
    onSurface = Color(0xFF10221A),
    error = StatusErrorRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our high-contrast ISP branded colors
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
