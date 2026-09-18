package com.apkmanager.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurpleLight,
    onPrimary = Color(0xFF1E0059),
    primaryContainer = PrimaryPurpleDark,
    onPrimaryContainer = Color(0xFFEADBFF),

    secondary = SecondaryCyan,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF80F3FF),

    tertiary = TertiaryPink,
    onTertiary = Color(0xFF5D0025),
    tertiaryContainer = Color(0xFF860037),
    onTertiaryContainer = Color(0xFFFFD9E2),

    background = SurfaceDark,
    onBackground = Color(0xFFE2E4EE),
    surface = SurfaceDarkElevated,
    onSurface = Color(0xFFE2E4EE),
    surfaceVariant = Color(0xFF272D43),
    onSurfaceVariant = Color(0xFFB0B7CB),

    surfaceContainerLowest = Color(0xFF06080E),
    surfaceContainerLow = SurfaceDarkElevated,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = Color(0xFF232943),
    surfaceContainerHighest = Color(0xFF2E3555),

    outline = Color(0xFF4C5574),
    outlineVariant = SurfaceCardBorder,
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADBFF),
    onPrimaryContainer = PrimaryPurpleDark,

    secondary = SecondaryCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3F5FF),
    onSecondaryContainer = Color(0xFF00363D),

    tertiary = TertiaryPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3B0014),

    background = SurfaceLight,
    onBackground = Color(0xFF141724),
    surface = SurfaceLightElevated,
    onSurface = Color(0xFF141724),
    surfaceVariant = Color(0xFFE3E7F2),
    onSurfaceVariant = Color(0xFF4C5574),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFEAEFF8),
    surfaceContainerHigh = Color(0xFFDFE5F2),
    surfaceContainerHighest = Color(0xFFD3DAEB),

    outline = Color(0xFF8A95B3),
    outlineVariant = Color(0xFFCAD2E3),
    error = StatusError,
    onError = Color.White
)

@Composable
fun ApkManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default dynamicColor to false so the custom vibrant cyberpunk palette is used
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
