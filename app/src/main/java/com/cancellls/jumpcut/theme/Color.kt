package com.cancellls.jumpcut.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Obsidian Studio Dark Palette (DaVinci Resolve / Linear Pro Workstation)
val BgDark = Color(0xFF07090E)            // Pure OLED obsidian studio canvas
val SurfaceDark = Color(0xFF0D1017)       // Rich dark titanium graphite slate surface
val CardDark = Color(0xFF131722)          // Velvet dark card surface
val CardDarkElevated = Color(0xFF181F2E)  // Elevated interactive cards / sheets / dialogs
val CardBorder = Color(0x24FFFFFF)        // Translucent luxury glass border (14% white)
val CardBorderSubtle = Color(0x0DFFFFFF)  // Subtle inner divider glass line (5% white)

// Studio Accent Palette: Calibrated Luminous Cyan-Teal & Electric Sky
val PrimaryCyan = Color(0xFF00D2B4)       // Calibrated Luminous Studio Cyan-Teal (High-vibrancy reference)
val PrimaryCyanVariant = Color(0xFF00A891)// Deep Studio Teal
val ElectricBlue = Color(0xFF0EA5E9)      // Cerulean Sky Blue
val ElectricBlueVariant = Color(0xFF0284C7)

// Alias for transition safety
val NeonViolet = ElectricBlue
val NeonVioletVariant = ElectricBlueVariant

// Audio & Waveform Indicators (Studio Rose Coral & Vocal Mint)
val SilenceRed = Color(0xFFFB7185)        // Sophisticated Rose Coral Crimson
val SilenceRedTranslucent = Color(0x2EFC8181)
val SpeechCyan = Color(0xFF00E5B9)        // Radiant Vocal Waveform Mint-Cyan
val SpeechCyanGlow = Color(0x4000E5B9)

// Typography & Hierarchy
val TextPrimary = Color(0xFFF8FAFC)       // Crisp snow white
val TextSecondary = Color(0xFF94A3B8)     // Cool slate silver
val TextMuted = Color(0xFF64748B)         // Deep muted graphite

// Badges, Rewards & PRO Tier (Studio Emerald & Polished Amber Gold)
val GreenSuccess = Color(0xFF10B981)      // Emerald Green (500)
val EmeraldMint = Color(0xFF10B981)
val GoldPro = Color(0xFFF59E0B)           // Polished Studio Amber Gold (Amber 500)
val GoldProGlow = Color(0x33F59E0B)       // Soft ambient amber aura
val GoldProDark = Color(0xFFD97706)       // Deep antique amber (Amber 600)

// Premium Studio Material Brushes (Top-Edge Specular Lighting & Satin Gradients)
val StudioCardBrush = Brush.verticalGradient(
    listOf(Color(0xFF141926), Color(0xFF0D1018))
)

val StudioCardBorderBrush = Brush.verticalGradient(
    listOf(Color(0x38FFFFFF), Color(0x08FFFFFF))
)

val StudioTealGradient = Brush.horizontalGradient(
    listOf(Color(0xFF00E5B9), Color(0xFF00B4D8))
)

val StudioTealButtonBrush = Brush.verticalGradient(
    listOf(Color(0xFF00E5B9), Color(0xFF00A891))
)

val StudioGoldGradient = Brush.horizontalGradient(
    listOf(GoldPro, GoldProDark)
)

val StudioSlateBrush = Brush.verticalGradient(
    listOf(Color(0xFF1C2332), Color(0xFF131722))
)

val StudioTealSubtleBrush = Brush.verticalGradient(
    listOf(Color(0x2800D2B4), Color(0x0D00D2B4))
)
