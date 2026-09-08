package com.cancellls.jumpcut.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = BgDark,
    primaryContainer = CardDarkElevated,
    onPrimaryContainer = PrimaryCyan,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = CardDark,
    onSecondaryContainer = TextSecondary,
    tertiary = GoldPro,
    onTertiary = BgDark,
    tertiaryContainer = CardDarkElevated,
    onTertiaryContainer = GoldPro,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = BgDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDarkElevated,
    outline = CardBorder,
    outlineVariant = CardBorderSubtle,
    error = SilenceRed,
    onError = TextPrimary
)

@Composable
fun JumpCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
