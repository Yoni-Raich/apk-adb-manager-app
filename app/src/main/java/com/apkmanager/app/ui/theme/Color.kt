package com.apkmanager.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary colors - Modern Electric Violet & Cyan Cyber palette
val PrimaryPurple = Color(0xFF7C4DFF)
val PrimaryPurpleLight = Color(0xFFB47CFF)
val PrimaryPurpleDark = Color(0xFF3F1Dcb)

val SecondaryCyan = Color(0xFF00E5FF)
val SecondaryCyanDark = Color(0xFF00B4D8)

val TertiaryPink = Color(0xFFFF4081)
val AccentOrange = Color(0xFFFF9100)

// Surfaces & Backgrounds - Deep OLED Dark & Sleek Light
val SurfaceDark = Color(0xFF0A0C14)
val SurfaceDarkElevated = Color(0xFF131726)
val SurfaceCard = Color(0xFF1B2036)
val SurfaceCardBorder = Color(0x338A99AD)
val SurfaceLight = Color(0xFFF6F8FC)
val SurfaceLightElevated = Color(0xFFFFFFFF)

// Status colors
val StatusConnected = Color(0xFF00E676)
val StatusConnecting = Color(0xFFFFB74D)
val StatusDisconnected = Color(0xFFFF5252)
val StatusError = Color(0xFFFF1744)

// Legacy / Compatibility aliases
val Green80 = PrimaryPurpleLight
val Green40 = PrimaryPurple
val Green30 = PrimaryPurpleDark
val Teal80 = SecondaryCyan
val Teal40 = SecondaryCyanDark
val Blue80 = Color(0xFF82B1FF)
val Blue40 = Color(0xFF2979FF)

// Gradients
object AppGradients {
    val primary = Brush.linearGradient(
        colors = listOf(PrimaryPurple, SecondaryCyan)
    )
    val purpleToPink = Brush.linearGradient(
        colors = listOf(PrimaryPurple, TertiaryPink)
    )
    val accent = Brush.linearGradient(
        colors = listOf(SecondaryCyan, Color(0xFF00E676))
    )
    val fire = Brush.linearGradient(
        colors = listOf(AccentOrange, TertiaryPink)
    )
    val darkCard = Brush.verticalGradient(
        colors = listOf(Color(0xFF20263F), Color(0xFF141829))
    )
    val surfaceGlass = Brush.verticalGradient(
        colors = listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF))
    )
}
