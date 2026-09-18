package com.apkmanager.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GooglePlayBlueDark,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = GooglePlayBlueContainerDark,
    onPrimaryContainer = Color(0xFFD3E3FD),

    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),

    tertiary = GooglePlayGreenDark,
    onTertiary = Color(0xFF003825),
    tertiaryContainer = GooglePlayGreenContainerDark,
    onTertiaryContainer = Color(0xFFC4EED0),

    background = GooglePlaySurfaceDark,
    onBackground = GooglePlayTextPrimaryDark,
    surface = GooglePlaySurfaceDark,
    onSurface = GooglePlayTextPrimaryDark,
    surfaceVariant = GooglePlaySurfaceContainerHighDark,
    onSurfaceVariant = GooglePlayTextSecondaryDark,

    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = GooglePlaySurfaceContainerLowDark,
    surfaceContainer = GooglePlaySurfaceContainerDark,
    surfaceContainerHigh = GooglePlaySurfaceContainerHighDark,
    surfaceContainerHighest = GooglePlaySurfaceContainerHighestDark,

    outline = GooglePlayOutlineDark,
    outlineVariant = Color(0xFF32363D),
    error = StatusError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = GooglePlayBlue,
    onPrimary = Color.White,
    primaryContainer = GooglePlayBlueContainer,
    onPrimaryContainer = Color(0xFF041E49),

    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),

    tertiary = GooglePlayGreen,
    onTertiary = Color.White,
    tertiaryContainer = GooglePlayGreenContainer,
    onTertiaryContainer = Color(0xFF002114),

    background = GooglePlaySurfaceLight,
    onBackground = GooglePlayTextPrimaryLight,
    surface = GooglePlaySurfaceLight,
    onSurface = GooglePlayTextPrimaryLight,
    surfaceVariant = GooglePlaySurfaceContainerHighLight,
    onSurfaceVariant = GooglePlayTextSecondaryLight,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = GooglePlaySurfaceContainerLowLight,
    surfaceContainer = GooglePlaySurfaceContainerLight,
    surfaceContainerHigh = GooglePlaySurfaceContainerHighLight,
    surfaceContainerHighest = GooglePlaySurfaceContainerHighestLight,

    outline = GooglePlayOutlineLight,
    outlineVariant = Color(0xFFE0E3E8),
    error = StatusError,
    onError = Color.White
)

@Composable
fun ApkManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default dynamicColor to true for authentic Android 12+ Material You theming
    dynamicColor: Boolean = true,
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
