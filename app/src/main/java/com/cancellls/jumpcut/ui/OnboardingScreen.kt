package com.cancellls.jumpcut.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.theme.*

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val highlight: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlide(
                title = "Kill Dead Air & Pauses",
                highlight = "Automatic Silence Removal",
                subtitle = "JumpCut's on-device engine detects awkward gaps, deep breaths, and hesitation in your footage, trimming dead air in seconds.",
                icon = Icons.Default.GraphicEq,
                accentColor = PrimaryCyan
            ),
            OnboardingSlide(
                title = "100% Lossless & Private",
                highlight = "Hardware Passthrough",
                subtitle = "Splice 10-minute 4K 60FPS footage without re-encoding quality loss. Zero cloud uploads—your media stays strictly on your hardware.",
                icon = Icons.Default.Bolt,
                accentColor = ElectricBlue
            ),
            OnboardingSlide(
                title = "Word Padding Armor",
                highlight = "Natural Speech Rhythm",
                subtitle = "Smart sentence padding protects consonants and word endings so your cuts feel seamless and natural, never harsh or robotic.",
                icon = Icons.Default.Security,
                accentColor = GreenSuccess
            ),
            OnboardingSlide(
                title = "Inspect Every Single Cut",
                highlight = "Non-Linear Editor Control",
                subtitle = "Listen to pauses on the interactive waveform and choose to keep or cut individual pauses with a single tap.",
                icon = Icons.Default.Tune,
                accentColor = GoldPro
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        // Top Skip Button
        if (currentIndex < slides.size - 1) {
            TextButton(
                onClick = onFinishOnboarding,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "Skip",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Center Content with Animated Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentSlide,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 2 })
                        .togetherWith(fadeOut() + slideOutHorizontally { -it / 2 })
                },
                label = "slide_transition"
            ) { slide ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Big Animated Icon Container
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(listOf(slide.accentColor, CardBorder)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = slide.accentColor,
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Pill Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(slide.accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = slide.highlight.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = slide.accentColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = slide.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle
                    Text(
                        text = slide.subtitle,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // Bottom Navigation Row & Indicators
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Dot Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.indices.forEach { index ->
                    val isSelected = index == currentIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (isSelected) 24.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) PrimaryCyan else CardBorder)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main Action Button
            Button(
                onClick = {
                    if (currentIndex < slides.size - 1) {
                        currentIndex++
                    } else {
                        onFinishOnboarding()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                if (currentIndex == slides.size - 1)
                                    listOf(PrimaryCyan, ElectricBlue)
                                else
                                    listOf(CardDark, CardDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentIndex == slides.size - 1) "Get Started • Cut First Video" else "Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentIndex == slides.size - 1) BgDark else TextPrimary
                    )
                }
            }
        }
    }
}
