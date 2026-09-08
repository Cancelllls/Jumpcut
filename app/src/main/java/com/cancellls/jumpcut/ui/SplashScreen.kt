package com.cancellls.jumpcut.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Aya-inspired luxury startup and loading screen for JumpCut AI.
 * Displays a glowing animated vector logo with breathing radiant aura,
 * dynamic audio equalizer bars, high-tracking studio typography, and
 * a sleek engine status indicator.
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onSplashFinished: () -> Unit
) {
    // Single unified transition progress (0.0 to 1.0) modeled on Aya's AnimationController
    val transitionProgress = remember { Animatable(0f) }

    // Infinite breathing pulse for aura & live equalizer bars
    val infiniteTransition = rememberInfiniteTransition(label = "AuraPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    // Launch sequencing (2.8s total duration like Aya)
    LaunchedEffect(Unit) {
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2500,
                easing = LinearEasing
            )
        )
        delay(300) // Hold briefly at full glory then transition
        onSplashFinished()
    }

    // Keyframed values matching Aya's curves with immediate visibility
    val t = transitionProgress.value
    // Elastic logo entrance from 0.75 to 1.0
    val logoScale = 0.75f + 0.25f * FastOutSlowInEasing.transform((t / 0.5f).coerceIn(0f, 1f))
    // Visible from the very first frame
    val logoAlpha = (0.6f + 0.4f * (t / 0.2f)).coerceIn(0.6f, 1f)
    // Title fades in rapidly within first 25% of timeline
    val textAlpha = (t / 0.25f).coerceIn(0f, 1f)
    // Status indicator fades in shortly after
    val statusAlpha = ((t - 0.15f) / 0.25f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to skip immediate handoff
                onSplashFinished()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // -------------------------------------------------------------
            // 1. Glowing Animated Vector Logo (Aya Style Breathing Halo)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                JumpCutAnimatedLogo(
                    pulse = pulse,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // -------------------------------------------------------------
            // 2. Animated Title & High-Tracking Tagline (Aya Typography)
            // -------------------------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha)
            ) {
                Text(
                    text = "JUMPCUT AI",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                    color = PrimaryCyan
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "STUDIO VIDEO & VOCAL INTELLIGENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(44.dp))

            // -------------------------------------------------------------
            // 3. Engine Status Capsule & Shimmer Progress
            // -------------------------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(statusAlpha)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = CardDarkElevated,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    // Pulsing green/cyan status beacon
                    val beaconAlpha = 0.5f + 0.5f * abs(sin(pulse * 2 * PI.toFloat()))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = GreenSuccess.copy(alpha = beaconAlpha),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STUDIO ENGINE READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subtle thin radiant progress track
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(3.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    PrimaryCyan.copy(alpha = 0.7f + 0.3f * sin(pulse * 2 * PI.toFloat())),
                                    ElectricBlue.copy(alpha = 0.7f + 0.3f * sin(pulse * 2 * PI.toFloat())),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

/**
 * Custom Canvas painter rendering the JumpCut iconic emblem:
 * - Radiant pulsing outer aura (like Aya's breathing halo)
 * - Segmented studio rotation aperture ring
 * - Dynamic 5-bar live equalizer audio waveform
 * - Angled JumpCut precision shear blades
 */
@Composable
fun JumpCutAnimatedLogo(
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f

        // 1. Multi-layered breathing radiant aura
        val breathingRadius = maxRadius * (0.88f + 0.12f * sin(pulse * 2 * PI.toFloat()))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PrimaryCyan.copy(alpha = 0.28f + 0.12f * sin(pulse * 2 * PI.toFloat())),
                    ElectricBlue.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = center,
                radius = breathingRadius
            ),
            radius = breathingRadius,
            center = center
        )

        // 2. Outer glowing precision studio ring
        val ringRadius = maxRadius * 0.78f
        drawCircle(
            color = CardBorder,
            radius = ringRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Rotating cyan & gold aperture highlights
        rotate(degrees = pulse * 120f, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(PrimaryCyan, GoldPro, PrimaryCyan),
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(PrimaryCyan, ElectricBlue, PrimaryCyan),
                    center = center
                ),
                startAngle = 180f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 3. Central Studio Shield disc
        val innerRadius = maxRadius * 0.62f
        drawCircle(
            color = CardDarkElevated,
            radius = innerRadius,
            center = center
        )
        drawCircle(
            brush = Brush.linearGradient(
                listOf(PrimaryCyan.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.3f))
            ),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.8.dp.toPx())
        )

        // 4. Stylized JumpCut shear chevrons (Scissors / Blade Motif)
        val bladeWidth = innerRadius * 0.85f
        val bladeHeight = innerRadius * 0.55f

        // Upper Cyan Blade
        val topBlade = Path().apply {
            moveTo(center.x - bladeWidth * 0.5f, center.y - bladeHeight * 0.7f)
            lineTo(center.x, center.y - bladeHeight * 0.15f)
            lineTo(center.x + bladeWidth * 0.5f, center.y - bladeHeight * 0.7f)
            lineTo(center.x + bladeWidth * 0.62f, center.y - bladeHeight * 0.55f)
            lineTo(center.x, center.y + bladeHeight * 0.05f)
            lineTo(center.x - bladeWidth * 0.62f, center.y - bladeHeight * 0.55f)
            close()
        }
        drawPath(
            path = topBlade,
            color = PrimaryCyan.copy(alpha = 0.9f)
        )

        // Lower Cobalt Blade
        val bottomBlade = Path().apply {
            moveTo(center.x - bladeWidth * 0.5f, center.y + bladeHeight * 0.7f)
            lineTo(center.x, center.y + bladeHeight * 0.15f)
            lineTo(center.x + bladeWidth * 0.5f, center.y + bladeHeight * 0.7f)
            lineTo(center.x + bladeWidth * 0.62f, center.y + bladeHeight * 0.55f)
            lineTo(center.x, center.y - bladeHeight * 0.05f)
            lineTo(center.x - bladeWidth * 0.62f, center.y + bladeHeight * 0.55f)
            close()
        }
        drawPath(
            path = bottomBlade,
            color = ElectricBlue.copy(alpha = 0.9f)
        )

        // 5. Dynamic Live Studio Equalizer bars in center
        val barCount = 5
        val barWidth = 4.dp.toPx()
        val barSpacing = 4.dp.toPx()
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val startX = center.x - totalBarsWidth / 2f
        val maxBarH = innerRadius * 0.7f

        for (i in 0 until barCount) {
            // Harmonically modulated heights simulate real voice activity
            val phaseOffset = i * 0.9f
            val normH = 0.25f + 0.75f * abs(sin((pulse * 3 * PI.toFloat()) + phaseOffset))
            val h = maxBarH * normH
            val x = startX + i * (barWidth + barSpacing)
            val y = center.y - h / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PrimaryCyan,
                        SpeechCyan
                    ),
                    startY = y,
                    endY = y + h
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
