package com.samyak.blackboxmailt.ui.theme

import androidx.compose.ui.graphics.Color

// Google Material 3 blue ramp, the same family the Gmail app uses for its accents.
val GmailBlue = Color(0xFF0B57D0)
val GmailBlueLight = Color(0xFFA8C7FA)
val GmailBlueContainer = Color(0xFFD3E3FD)
val GmailBlueOnContainer = Color(0xFF041E49)
val GmailBlueContainerDark = Color(0xFF0842A0)

val GmailRed = Color(0xFFEA4335)
val GmailRedDark = Color(0xFFF28B82)

// Light surfaces
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFF8FAFD)
val LightSearchField = Color(0xFFF0F4F9)
val LightSurfaceContainer = Color(0xFFEEF1F5)
val LightSurfaceContainerHigh = Color(0xFFE9EEF6)
val LightOnSurface = Color(0xFF1F1F1F)
val LightOnSurfaceVariant = Color(0xFF444746)
val LightOutline = Color(0xFF747775)
val LightOutlineVariant = Color(0xFFDADCE0)

// Dark surfaces
val DarkSurface = Color(0xFF131314)
val DarkSurfaceDim = Color(0xFF0E0E0F)
val DarkSearchField = Color(0xFF1E1F20)
val DarkSurfaceContainer = Color(0xFF1E1F20)
val DarkSurfaceContainerHigh = Color(0xFF282A2C)
val DarkOnSurface = Color(0xFFE3E3E3)
val DarkOnSurfaceVariant = Color(0xFFC4C7C5)
val DarkOutline = Color(0xFF8E918F)
val DarkOutlineVariant = Color(0xFF444746)

/** Avatar palette, mirroring the vivid circles Gmail draws for senders without a photo. */
val AvatarColors = listOf(
    Color(0xFFD93025),
    Color(0xFFE8710A),
    Color(0xFFF9AB00),
    Color(0xFF1E8E3E),
    Color(0xFF00897B),
    Color(0xFF1A73E8),
    Color(0xFF3949AB),
    Color(0xFF8E24AA),
    Color(0xFFD01884),
    Color(0xFF5F6368)
)

fun avatarColorFor(seed: String): Color {
    if (seed.isEmpty()) return AvatarColors.last()
    val hash = seed.fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7FFFFFFF }
    return AvatarColors[hash % AvatarColors.size]
}
