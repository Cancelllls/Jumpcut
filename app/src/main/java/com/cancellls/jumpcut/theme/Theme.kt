package com.cancellls.jumpcut.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = BgDark,
    primaryContainer = PrimaryCyanVariant,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = ElectricBlueVariant,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = SilenceRed
)

@Composable
fun JumpCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
