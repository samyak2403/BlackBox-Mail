package com.samyak.blackboxmailt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = GmailBlue,
    onPrimary = Color.White,
    primaryContainer = GmailBlueContainer,
    onPrimaryContainer = GmailBlueOnContainer,
    inversePrimary = GmailBlueLight,
    secondary = Color(0xFF3C4043),
    onSecondary = Color.White,
    secondaryContainer = GmailBlueContainer,
    onSecondaryContainer = GmailBlueOnContainer,
    tertiary = GmailRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE1E6EE),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = GmailBlueLight,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = GmailBlueContainerDark,
    onPrimaryContainer = GmailBlueContainer,
    inversePrimary = GmailBlue,
    secondary = Color(0xFFC4C7C5),
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = GmailBlueContainerDark,
    onSecondaryContainer = GmailBlueContainer,
    tertiary = GmailRedDark,
    onTertiary = Color(0xFF601410),
    tertiaryContainer = Color(0xFF8C1D18),
    onTertiaryContainer = Color(0xFFF9DEDC),
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = Color(0xFF39393B),
    surfaceContainerLowest = Color(0xFF0E0E0F),
    surfaceContainerLow = Color(0xFF1B1B1C),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF333537),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun BlackBoxMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = MailShapes,
        content = content
    )
}
