package com.apkmanager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Material 3 type scale with Play-style weights: regular headlines, medium titles. */
val Typography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Normal),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Normal),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.1.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
