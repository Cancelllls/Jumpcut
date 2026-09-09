package com.cancellls.jumpcut.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.model.CaptionStyle
import com.cancellls.jumpcut.model.ExportConfig
import com.cancellls.jumpcut.model.TargetAspectRatio
import com.cancellls.jumpcut.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    initialConfig: ExportConfig,
    isVideo: Boolean,
    originalDurationMs: Long,
    cutDurationMs: Long,
    savedPercent: Int,
    isProUser: Boolean = false,
    remainingExports: Int = 2,
    onOpenPro: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirmExport: (ExportConfig) -> Unit,
    onExportEdl: ((Boolean) -> Unit)? = null,
    onExportSubtitles: ((Boolean) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var extractAudioOnly by remember { mutableStateOf(initialConfig.extractAudioOnly || !isVideo) }
    var audioFormat by remember { mutableStateOf(initialConfig.audioFormat) }
    var videoResolution by remember { mutableStateOf(initialConfig.videoResolution) }
    var saveToGallery by remember { mutableStateOf(initialConfig.saveToGallery) }
    var autoZoomJumpcuts by remember { mutableStateOf(initialConfig.autoZoomJumpcuts) }
    var microCrossfade by remember { mutableStateOf(initialConfig.microCrossfade) }
    var studioAudioLeveling by remember { mutableStateOf(initialConfig.studioAudioLeveling) }
    var roomToneSmoothing by remember { mutableStateOf(initialConfig.roomToneSmoothing) }
    var silenceTimeWarp by remember { mutableStateOf(initialConfig.silenceTimeWarp) }
    var targetAspectRatio by remember { mutableStateOf(initialConfig.targetAspectRatio) }
    var burnInCaptions by remember { mutableStateOf(initialConfig.burnInCaptions) }
    var captionStyle by remember { mutableStateOf(initialConfig.captionStyle) }
    var instantRemux by remember { mutableStateOf(initialConfig.instantRemux) }
    var backgroundMusicUri by remember { mutableStateOf<Uri?>(initialConfig.backgroundMusicUri) }
    var backgroundMusicVolume by remember { mutableFloatStateOf(initialConfig.backgroundMusicVolume) }
    var musicAutoDuck by remember { mutableStateOf(initialConfig.musicAutoDuck) }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { backgroundMusicUri = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CardBorder) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Export Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Select output format & destination",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Duration & Savings Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorderBrush)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioCardBrush)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Final Duration", fontSize = 12.sp, color = TextMuted)
                        Text(
                            text = formatTime(cutDurationMs),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryCyan
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenSuccess.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "✂️ Saved ${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))} ($savedPercent%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Format Selection
            Text(
                text = "Output Format",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video option (only if input has video)
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (!extractAudioOnly) CardBorder.copy(alpha = 0.5f) else CardDark)
                            .border(
                                width = if (!extractAudioOnly) 2.dp else 1.dp,
                                color = if (!extractAudioOnly) PrimaryCyan else CardBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { extractAudioOnly = false }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = if (!extractAudioOnly) PrimaryCyan else TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Video (MP4)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Lossless video splice",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Audio Only option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (extractAudioOnly) CardBorder.copy(alpha = 0.5f) else CardDark)
                        .border(
                            width = if (extractAudioOnly) 2.dp else 1.dp,
                            color = if (extractAudioOnly) ElectricBlue else CardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { extractAudioOnly = true }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = if (extractAudioOnly) ElectricBlue else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Audio Only (M4A)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Extract for podcast",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (!extractAudioOnly && isVideo) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("original" to "Source Resolution", "720p" to "720p (Fast Share)").forEach { (res, label) ->
                        val isSel = videoResolution == res
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PrimaryCyan.copy(alpha = 0.15f) else CardDark)
                                .border(1.dp, if (isSel) PrimaryCyan else CardBorder, RoundedCornerShape(8.dp))
                            .clickable { videoResolution = res }
                            .padding(vertical = 7.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) PrimaryCyan else TextSecondary
                            )
                        }
                    }
                }
            } else if (extractAudioOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("m4a" to "M4A (AAC Audio)", "wav" to "WAV (Studio PCM)").forEach { (fmt, label) ->
                        val isSel = audioFormat == fmt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) ElectricBlue.copy(alpha = 0.15f) else CardDark)
                                .border(1.dp, if (isSel) ElectricBlue else CardBorder, RoundedCornerShape(8.dp))
                            .clickable { audioFormat = fmt }
                            .padding(vertical = 7.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) ElectricBlue else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-Zoom Jumpcuts (Pro Feature)
            if (isVideo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .clickable { autoZoomJumpcuts = !autoZoomJumpcuts }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Zoom Jumpcuts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryCyan.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("PRO PACING", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                }
                            }
                            Text(
                                text = "Alternates 1.12x punch-ins for YouTube retention",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = autoZoomJumpcuts,
                        onCheckedChange = { autoZoomJumpcuts = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = PrimaryCyan,
                            uncheckedTrackColor = CardBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Studio Speech Auto-Leveler (-14 LUFS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .clickable { studioAudioLeveling = !studioAudioLeveling }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = GoldPro,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Studio Speech Leveler",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldPro.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("-14 LUFS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GoldPro)
                            }
                        }
                        Text(
                            text = "Auto-levels dialogue loudness with -1 dB peak limiter",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = studioAudioLeveling,
                    onCheckedChange = { studioAudioLeveling = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = GoldPro,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Room Tone & Seam Smoothing (Pro Audio)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .clickable {
                        roomToneSmoothing = !roomToneSmoothing
                        microCrossfade = roomToneSmoothing
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Room Tone Smoothing",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("PRO AUDIO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                            }
                        }
                        Text(
                            text = "15ms S-curve crossfades & ambient floor continuity",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = roomToneSmoothing,
                    onCheckedChange = {
                        roomToneSmoothing = it
                        microCrossfade = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save to Gallery switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .clickable { saveToGallery = !saveToGallery }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Save to Device Gallery",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Movies/JumpCut (visible in Google Photos)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = saveToGallery,
                    onCheckedChange = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        saveToGallery = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Silence Handling Mode: Cut vs Time-Warp (3x)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .clickable {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        silenceTimeWarp = !silenceTimeWarp
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = if (silenceTimeWarp) PrimaryCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Silence Time Warp (3× Speed)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (silenceTimeWarp) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryCyan.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("FAST-FORWARD", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                }
                            }
                        }
                        Text(
                            text = if (silenceTimeWarp) "Smooth 3× speed ramp during pauses (no jumpcuts)"
                                   else "Standard JumpCut: completely cuts and drops silence",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = silenceTimeWarp,
                    onCheckedChange = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        silenceTimeWarp = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            // Multi-Format Social Aspect Ratio Converter
            if (isVideo && !extractAudioOnly) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AspectRatio, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Social Aspect Ratio", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text(targetAspectRatio.tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TargetAspectRatio.entries.forEach { ratio ->
                            val isSel = targetAspectRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PrimaryCyan.copy(alpha = 0.2f) else CardDarkElevated)
                                    .border(1.dp, if (isSel) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        targetAspectRatio = ratio
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ratio.tag,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) PrimaryCyan else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Instant Lossless Remux Mode
            if (isVideo && !extractAudioOnly) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .clickable {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            instantRemux = !instantRemux
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPro, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Instant Lossless Remux", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GoldPro.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("5-SEC EXPORT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GoldPro)
                                }
                            }
                            Text("Direct bitstream transmux • Zero quality degradation", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    Switch(
                        checked = instantRemux,
                        onCheckedChange = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            instantRemux = it
                            if (it && burnInCaptions) {
                                burnInCaptions = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = GoldPro,
                            uncheckedTrackColor = CardBorder
                        )
                    )
                }
            }

            // TikTok / Reels Open Captions (Burn-In Subtitles)
            if (isVideo && !extractAudioOnly) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Burn-In Open Captions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PrimaryCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("TIKTOK STYLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                    }
                                }
                                Text(
                                    if (instantRemux) "Requires re-encode (disables instant remux)"
                                    else "Burn speech cues directly into exported MP4 frames",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = burnInCaptions,
                            onCheckedChange = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                burnInCaptions = it
                                if (it && instantRemux) {
                                    instantRemux = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = PrimaryCyan,
                                uncheckedTrackColor = CardBorder
                            )
                        )
                    }

                    if (burnInCaptions) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Caption Graphic Style", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                CaptionStyle.NEON_CYAN to "Neon Cyan",
                                CaptionStyle.YELLOW_PUNCH to "Yellow Punch",
                                CaptionStyle.CLASSIC_WHITE to "Classic White"
                            ).forEach { (style, label) ->
                                val isSel = captionStyle == style
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) PrimaryCyan.copy(alpha = 0.2f) else CardDarkElevated)
                                        .border(1.dp, if (isSel) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            captionStyle = style
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) PrimaryCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Background Music Bed with Auto-Ducking
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = GoldPro, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Background Music Bed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    if (backgroundMusicUri != null) {
                        Text(
                            text = "Remove",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SilenceRed,
                            modifier = Modifier.clickable {
                                backgroundMusicUri = null
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (backgroundMusicUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardDarkElevated)
                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                musicPicker.launch("audio/*")
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Audio Bed (MP3 / WAV)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardDarkElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = backgroundMusicUri?.lastPathSegment ?: "Audio Track Loaded",
                            fontSize = 11.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto-Ducking Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-Ducking (-14 dB)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Automatically lowers music when voice is detected", fontSize = 10.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = musicAutoDuck,
                            onCheckedChange = { musicAutoDuck = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = GoldPro,
                                uncheckedTrackColor = CardBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Volume slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Music Volume", fontSize = 11.sp, color = TextSecondary)
                        Text("${(backgroundMusicVolume * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPro)
                    }
                    Slider(
                        value = backgroundMusicVolume,
                        onValueChange = { backgroundMusicVolume = it },
                        valueRange = 0.05f..0.60f,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPro,
                            activeTrackColor = GoldPro,
                            inactiveTrackColor = CardBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start Export Button
            if (!isProUser && remainingExports <= 0) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onDismiss()
                        onOpenPro()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(SilenceRed, PrimaryCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daily Limit Reached (2/2 Used) • Get Pro",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onConfirmExport(
                            ExportConfig(
                                extractAudioOnly = extractAudioOnly,
                                saveToGallery = saveToGallery,
                                autoZoomJumpcuts = autoZoomJumpcuts,
                                microCrossfade = microCrossfade,
                                studioAudioLeveling = studioAudioLeveling,
                                roomToneSmoothing = roomToneSmoothing,
                                audioFormat = audioFormat,
                                videoResolution = videoResolution,
                                silenceTimeWarp = silenceTimeWarp,
                                targetAspectRatio = targetAspectRatio,
                                burnInCaptions = burnInCaptions,
                                captionStyle = captionStyle,
                                instantRemux = instantRemux,
                                backgroundMusicUri = backgroundMusicUri,
                                backgroundMusicVolume = backgroundMusicVolume,
                                musicAutoDuck = musicAutoDuck
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(StudioTealGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = BgDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProUser) "Start Render & Splice (Unlimited Pro)"
                                       else "Start Render ($remainingExports of 2 free left today)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BgDark
                            )
                        }
                    }
                }
            }

            // Subtitle & NLE Bridge Buttons
            if (onExportSubtitles != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onExportSubtitles(false)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardDark,
                            contentColor = TextPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        )
                    ) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export .SRT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onExportSubtitles(true)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardDark,
                            contentColor = TextPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        )
                    ) {
                        Icon(
                            Icons.Default.ClosedCaption,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export .VTT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Desktop NLE Bridge Export Button
            if (onExportEdl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onExportEdl(false)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardDark,
                        contentColor = TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                    )
                ) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export Timeline (.EDL for Premiere & DaVinci)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
