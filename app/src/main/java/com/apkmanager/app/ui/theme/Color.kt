package com.apkmanager.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Obsidian dark foundation (Linear / GitHub-Mobile inspired) ──
val ObsidianBackground = Color(0xFF0E1015)
val ObsidianSurface = Color(0xFF15181E)
val ObsidianSurfaceElevated = Color(0xFF1C2028)
val ObsidianSurfaceHighlight = Color(0xFF252A34)
val ObsidianBorder = Color(0xFF272C37)

// ── Brand: sophisticated indigo + clean sky ──
val PrimaryIndigo = Color(0xFF6366F1)
val PrimaryIndigoDark = Color(0xFF4F46E5)
val PrimaryIndigoContainer = Color(0xFF23233B)
val SecondarySky = Color(0xFF38BDF8)
val SecondarySkyDark = Color(0xFF0284C7)

// ── Text ──
val TextPrimaryDark = Color(0xFFE8EAF0)
val TextSecondaryDark = Color(0xFF9AA3B2)
val TextTertiaryDark = Color(0xFF6B7280)

// ── Status: refined emerald / amber / crimson ──
val StatusConnected = Color(0xFF10B981)
val StatusConnecting = Color(0xFFF59E0B)
val StatusDisconnected = Color(0xFF6B7280)
val StatusError = Color(0xFFEF4444)

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
