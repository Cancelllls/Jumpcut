package com.cancellls.jumpcut.ui

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf("lifetime") } // "monthly" or "lifetime"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gold Pro Crown Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GoldPro, PrimaryCyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Pro Crown",
                    tint = BgDark,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Unlock JumpCut Pro",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Remove silence, boost speech, and export studio-quality videos with zero restrictions.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature Checklist
            ProFeatureRow(icon = Icons.Default.Bolt, title = "Unlimited 4K 60FPS Exports")
            Spacer(modifier = Modifier.height(10.dp))
            ProFeatureRow(icon = Icons.Default.GraphicEq, title = "AI Background Noise & Echo Remover")
            Spacer(modifier = Modifier.height(10.dp))
            ProFeatureRow(icon = Icons.Default.Block, title = "100% Watermark-Free Videos")
            Spacer(modifier = Modifier.height(10.dp))
            ProFeatureRow(icon = Icons.Default.Tune, title = "Custom Voice Padding & Decibel Presets")

            Spacer(modifier = Modifier.height(24.dp))

            // Pricing Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Plan 1: Lifetime (Featured)
                PlanCard(
                    title = "Lifetime Access",
                    price = "$29.99",
                    period = "Pay once, own forever",
                    badge = "BEST VALUE",
                    isSelected = selectedPlan == "lifetime",
                    accent = GoldPro,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedPlan = "lifetime"
                }

                // Plan 2: Monthly
                PlanCard(
                    title = "Monthly Pro",
                    price = "$4.99",
                    period = "Billed monthly",
                    badge = null,
                    isSelected = selectedPlan == "monthly",
                    accent = PrimaryCyan,
                    modifier = Modifier.weight(1f)
                ) {
                    selectedPlan = "monthly"
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase Button
            Button(
                onClick = {
                    onUnlock()
                    onDismiss()
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
                                if (selectedPlan == "lifetime") listOf(GoldPro, PrimaryCyan)
                                else listOf(PrimaryCyan, ElectricBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedPlan == "lifetime") "Unlock Lifetime Pro ($29.99)" else "Subscribe Monthly ($4.99)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BgDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Purchases & Terms
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { onUnlock(); onDismiss() }) {
                    Text(text = "Restore Purchase", fontSize = 12.sp, color = TextSecondary)
                }
                Text(text = "•", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
                TextButton(onClick = { onDismiss() }) {
                    Text(text = "Terms & Privacy", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun ProFeatureRow(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    period: String,
    badge: String?,
    isSelected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) CardDark else SurfaceDark)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else CardBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            badge?.let {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = it,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BgDark
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(text = title, fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = price, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(text = period, fontSize = 10.sp, color = TextMuted)
        }
    }
}
