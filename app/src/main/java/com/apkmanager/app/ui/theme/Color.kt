package com.apkmanager.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Official Google Play & Material Design 3 Palette ──
val GooglePlayBlue = Color(0xFF0B57D0)
val GooglePlayBlueDark = Color(0xFFA8C7FA)
val GooglePlayBlueContainer = Color(0xFFD3E3FD)
val GooglePlayBlueContainerDark = Color(0xFF0842A0)

val GooglePlayGreen = Color(0xFF00875E)
val GooglePlayGreenDark = Color(0xFF79D6AC)
val GooglePlayGreenContainer = Color(0xFFC4EED0)
val GooglePlayGreenContainerDark = Color(0xFF005238)

val GooglePlaySurfaceLight = Color(0xFFFFFFFF)
val GooglePlaySurfaceContainerLowLight = Color(0xFFF8F9FA)
val GooglePlaySurfaceContainerLight = Color(0xFFF0F4F9)
val GooglePlaySurfaceContainerHighLight = Color(0xFFE9EEF6)
val GooglePlaySurfaceContainerHighestLight = Color(0xFFE1E7F0)

val GooglePlaySurfaceDark = Color(0xFF111315)
val GooglePlaySurfaceContainerLowDark = Color(0xFF1A1C1E)
val GooglePlaySurfaceContainerDark = Color(0xFF1E2022)
val GooglePlaySurfaceContainerHighDark = Color(0xFF282A2D)
val GooglePlaySurfaceContainerHighestDark = Color(0xFF333537)

val GooglePlayOutlineLight = Color(0xFFDFE3E7)
val GooglePlayOutlineDark = Color(0xFF42474E)

val GooglePlayTextPrimaryLight = Color(0xFF1F1F1F)
val GooglePlayTextSecondaryLight = Color(0xFF444746)
val GooglePlayTextPrimaryDark = Color(0xFFE2E2E6)
val GooglePlayTextSecondaryDark = Color(0xFFC4C7C5)

// ── Status: Google Play colors ──
val StatusConnected = GooglePlayGreen
val StatusConnecting = Color(0xFFE37400)
val StatusDisconnected = Color(0xFF74777F)
val StatusError = Color(0xFFB3261E)

// ── Obsidian & legacy aliases ──
val ObsidianBackground = GooglePlaySurfaceDark
val ObsidianSurface = GooglePlaySurfaceContainerDark
val ObsidianSurfaceElevated = GooglePlaySurfaceContainerHighDark
val ObsidianSurfaceHighlight = GooglePlaySurfaceContainerHighestDark
val ObsidianBorder = GooglePlayOutlineDark

// ── Brand: Google Play Blue ──
val PrimaryIndigo = GooglePlayBlue
val PrimaryIndigoDark = GooglePlayBlueDark
val PrimaryIndigoContainer = GooglePlayBlueContainerDark
val SecondarySky = Color(0xFF00639B)
val SecondarySkyDark = Color(0xFF7FCFFF)

// ── Text ──
val TextPrimaryDark = GooglePlayTextPrimaryDark
val TextSecondaryDark = GooglePlayTextSecondaryDark
val TextTertiaryDark = Color(0xFF8E918F)

// ── Back-compat aliases (old casino names → new refined values) ──
// Kept so existing call-sites keep compiling without neon output.
val PrimaryPurple = PrimaryIndigo
val PrimaryPurpleLight = Color(0xFF818CF8)
val PrimaryPurpleDark = PrimaryIndigoDark
val SecondaryCyan = SecondarySky
val SecondaryCyanDark = SecondarySkyDark
val TertiaryPink = Color(0xFFF472B6)
val AccentOrange = StatusConnecting

val SurfaceDark = ObsidianBackground
val SurfaceDarkElevated = ObsidianSurfaceElevated
val SurfaceCard = ObsidianSurface
val SurfaceCardBorder = ObsidianBorder
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceLightElevated = Color(0xFFFFFFFF)

// Legacy / Compatibility aliases
val Green80 = PrimaryPurpleLight
val Green40 = PrimaryPurple
val Green30 = PrimaryPurpleDark
val Teal80 = SecondaryCyan
val Teal40 = SecondaryCyanDark
val Blue80 = Color(0xFF93C5FD)
val Blue40 = Color(0xFF3B82F6)

// ── Subtle tonal gradients (monochrome, no neon) ──
// All entries intentionally near-solid so legacy `gradient = ...` params
// render as confident flat surfaces instead of casino rainbows.
object AppGradients {
    val primary = Brush.verticalGradient(
        colors = listOf(PrimaryIndigo, PrimaryIndigoDark)
    )
    val purpleToPink = Brush.verticalGradient(
        colors = listOf(PrimaryIndigo, PrimaryIndigoDark)
    )
    val accent = Brush.verticalGradient(
        colors = listOf(Color(0xFF1E2430), Color(0xFF1A1F2A))
    )
    val fire = Brush.verticalGradient(
        colors = listOf(Color(0xFF232A38), Color(0xFF1C2028))
    )
    val darkCard = Brush.verticalGradient(
        colors = listOf(ObsidianSurface, ObsidianSurface)
    )
    val surfaceGlass = Brush.verticalGradient(
        colors = listOf(Color(0x0FFFFFFF), Color(0x05FFFFFF))
    )

    // New canonical subtle surface wash (barely-there top light)
    val subtleSurface = Brush.verticalGradient(
        colors = listOf(Color(0xFF191D26), ObsidianSurface)
    )
}
