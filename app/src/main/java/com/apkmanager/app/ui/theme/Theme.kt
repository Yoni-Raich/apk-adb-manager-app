package com.apkmanager.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = PrimaryIndigoContainer,
    onPrimaryContainer = Color(0xFFC7D2FE),

    secondary = SecondarySky,
    onSecondary = Color(0xFF06202B),
    secondaryContainer = Color(0xFF0E2A3A),
    onSecondaryContainer = Color(0xFFBAE6FD),

    tertiary = Color(0xFF94A3B8),
    onTertiary = Color(0xFF0E1015),
    tertiaryContainer = Color(0xFF252A34),
    onTertiaryContainer = Color(0xFFE2E8F0),

    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHighlight,
    onSurfaceVariant = TextSecondaryDark,

    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    surfaceContainerHighest = ObsidianSurfaceHighlight,

    outline = Color(0xFF343B4A),
    outlineVariant = ObsidianBorder,
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigoDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),

    secondary = SecondarySkyDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),

    tertiary = Color(0xFF64748B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1F5F9),
    onTertiaryContainer = Color(0xFF0F172A),

    background = SurfaceLight,
    onBackground = Color(0xFF111418),
    surface = SurfaceLightElevated,
    onSurface = Color(0xFF111418),
    surfaceVariant = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFF5B6472),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F6),
    surfaceContainer = Color(0xFFECEFF3),
    surfaceContainerHigh = Color(0xFFE2E6EC),
    surfaceContainerHighest = Color(0xFFD6DBE2),

    outline = Color(0xFFC3CAD4),
    outlineVariant = Color(0xFFDDE2EA),
    error = StatusError,
    onError = Color.White
)

@Composable
fun ApkManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default dynamicColor to false so the custom obsidian palette is used
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
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
