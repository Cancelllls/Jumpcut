package com.cancellls.jumpcut.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.horizontalScroll
import com.cancellls.jumpcut.model.CreatorPreset
import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.ExportConfig
import com.cancellls.jumpcut.model.MediaItem
import com.cancellls.jumpcut.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import com.cancellls.jumpcut.ads.BannerAdComposable

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    media: MediaItem,
    segments: List<CutSegment>,
    waveformAmplitudes: List<Float>,
    originalDurationMs: Long,
    cutDurationMs: Long,
    savedPercent: Int,
    cutSettings: CutSettings,
    estimatedNoiseFloorDb: Float = -36f,
    skipSilencePreview: Boolean,
    exportConfig: ExportConfig,
    creatorPresets: List<CreatorPreset> = emptyList(),
    isProUser: Boolean = false,
    remainingExports: Int = 2,
    onOpenPro: () -> Unit = {},
    onSettingsChanged: (CutSettings) -> Unit,
    onToggleSkipSilence: (Boolean) -> Unit,
    onToggleSegment: (Int) -> Unit,
    onExportConfirm: (ExportConfig) -> Unit,
    onSavePreset: ((String, CutSettings) -> Unit)? = null,
    onExportEdl: ((Boolean) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val aspectRatioBadge = remember(media.width, media.height) {
        if (!media.isVideo) "Audio Only"
        else {
            val ratio = media.width.toFloat() / media.height.toFloat().coerceAtLeast(1f)
            when {
                ratio < 0.65f -> "9:16 Shorts"
                ratio > 1.5f -> "16:9 Landscape"
                ratio in 0.9f..1.1f -> "1:1 Square"
                else -> "${media.width}x${media.height}"
            }
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isComparingRaw by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Parameters, 1: Segments List
    var showExportSheet by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(media.uri))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Update speed on player
    LaunchedEffect(playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Playback loop tracking position & auto-skipping silence (suspended during A/B raw comparison)
    LaunchedEffect(isPlaying, skipSilencePreview, isComparingRaw, segments) {
        while (isPlaying) {
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos

            if (skipSilencePreview && !isComparingRaw) {
                val currentSilence = segments.firstOrNull {
                    it.isSilence && !it.isExcluded && pos in it.startMs until it.endMs
                }
                if (currentSilence != null && currentSilence.endMs < originalDurationMs) {
                    exoPlayer.seekTo(currentSilence.endMs)
                    currentPositionMs = currentSilence.endMs
                }
            }
            delay(35)
        }
    }

    val silences = remember(segments) { segments.filter { it.isSilence } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardDark)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            ) {
                Text(
                    text = media.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${silences.size} silences detected",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onExportEdl != null) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExportEdl(false)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CardDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = "Export EDL",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Time Saved Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(GreenSuccess, PrimaryCyan)))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = BgDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "-${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))} ($savedPercent%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BgDark
                        )
                    }
                }
            }
        }

        // PINNED UPPER WORKSTATION MONITOR & TIMELINE (Fixed, does not scroll away!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Video Preview Surface (Responsive 185dp height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (media.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Audio", tint = PrimaryCyan, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = media.name, fontSize = 13.sp, color = TextPrimary)
                    }
                }

                // Play / Pause Floating Overlay Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CardDark.copy(alpha = 0.85f))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Video Resolution & Aspect Ratio Badge
                if (media.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgDark.copy(alpha = 0.8f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${media.width}x${media.height} • $aspectRatioBadge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Raw Auditioning Active Indicator Badge
                if (isComparingRaw) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SilenceRed.copy(alpha = 0.9f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AUDITIONING RAW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transport: Timecode + Speed Selector + Skip Silence / A/B Raw
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timecode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(currentPositionMs),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryCyan
                    )
                    Text(
                        text = " / ${formatTime(originalDurationMs)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Speed Selector Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1.0f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) PrimaryCyan else CardDark)
                                .clickable {
                                    playbackSpeed = speed
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${speed}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BgDark else TextSecondary
                            )
                        }
                    }
                }

                // Tactile Hold to Audition Raw A/B Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isComparingRaw) SilenceRed else CardDark)
                        .border(1.dp, if (isComparingRaw) SilenceRed else CardBorder, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isComparingRaw = true
                                    waitForUpOrCancellation()
                                    isComparingRaw = false
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isComparingRaw) "AUDITIONING RAW" else "HOLD FOR RAW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isComparingRaw) TextPrimary else TextSecondary
                    )
                }

                // Skip Silence Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Skip",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (skipSilencePreview) PrimaryCyan else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = skipSilencePreview,
                        onCheckedChange = { onToggleSkipSilence(it) },
                        modifier = Modifier.height(24.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = PrimaryCyan,
                            uncheckedTrackColor = CardDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Precision Waveform (Pinned!)
            WaveformView(
                amplitudes = waveformAmplitudes,
                segments = segments,
                totalDurationMs = originalDurationMs,
                currentPositionMs = currentPositionMs,
                onSeek = { seekMs ->
                    exoPlayer.seekTo(seekMs)
                    currentPositionMs = seekMs
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LOWER SCROLLABLE CONTROL DECK (weight(1f) fills remaining space)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Mode Selector Tabs (0: Tuning, 1: Presets, 2: Silence Inspector)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = PrimaryCyan,
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) CardDark else Color.Transparent)
                        .padding(vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = if (selectedTab == 0) PrimaryCyan else TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tuning",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) PrimaryCyan else TextSecondary
                        )
                    }
                }

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) CardDark else Color.Transparent)
                        .padding(vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (selectedTab == 1) PrimaryCyan else TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Presets",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) PrimaryCyan else TextSecondary
                        )
                    }
                }

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 2) CardDark else Color.Transparent)
                        .padding(vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeMute, contentDescription = null, tint = if (selectedTab == 2) PrimaryCyan else TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pauses (${silences.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 2) PrimaryCyan else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 0: PRECISION TUNING SLIDERS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Live Impact Readout Banner with Voice Discriminator stats
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "VOICE DISCRIMINATOR",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "Room Noise Floor: ${estimatedNoiseFloorDb.toInt()} dB",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (estimatedNoiseFloorDb > -35f) GoldPro else PrimaryCyan
                                        )
                                    }
                                    Text(
                                        text = "${silences.size} cuts • -${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GreenSuccess
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Slider 1: Voice vs Background Noise Armor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = GoldPro, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Voice vs Noise Armor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Text(text = "Filters out fans, A/C, street rumble & room hum", fontSize = 10.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GoldPro.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "${(cutSettings.voiceNoiseRejection * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = GoldPro)
                                }
                            }
                            Slider(
                                value = cutSettings.voiceNoiseRejection,
                                onValueChange = { onSettingsChanged(cutSettings.copy(voiceNoiseRejection = it)) },
                                valueRange = 0.15f..0.95f,
                                colors = SliderDefaults.colors(thumbColor = GoldPro, activeTrackColor = GoldPro)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Slider 2: Silence Sensitivity (dB)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Silence Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "Base cutoff threshold", fontSize = 10.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PrimaryCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "${cutSettings.silenceThresholdDb.toInt()} dB", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryCyan)
                                }
                            }
                            Slider(
                                value = cutSettings.silenceThresholdDb,
                                onValueChange = { onSettingsChanged(cutSettings.copy(silenceThresholdDb = it)) },
                                valueRange = -45f..-18f,
                                colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Slider 3: Min Silence Duration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Minimum Silence Length", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "Shorter pauses are left untouched", fontSize = 10.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ElectricBlue.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "${cutSettings.minSilenceDurationMs} ms", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = ElectricBlue)
                                }
                            }
                            Slider(
                                value = cutSettings.minSilenceDurationMs.toFloat(),
                                onValueChange = { onSettingsChanged(cutSettings.copy(minSilenceDurationMs = it.toLong())) },
                                valueRange = 150f..800f,
                                colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Slider 4: Speech Padding Buffer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Word Padding Armor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(text = "Safety buffer to prevent clipping word edges", fontSize = 10.sp, color = TextSecondary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GreenSuccess.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "${cutSettings.paddingMs} ms", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = GreenSuccess)
                                }
                            }
                            Slider(
                                value = cutSettings.paddingMs.toFloat(),
                                onValueChange = { onSettingsChanged(cutSettings.copy(paddingMs = it.toLong())) },
                                valueRange = 15f..120f,
                                colors = SliderDefaults.colors(thumbColor = GreenSuccess, activeTrackColor = GreenSuccess)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Auto-Adapt Room Noise Floor Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Adapt to Room Acoustics",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (cutSettings.autoNoiseFloor) "Dynamically tracks ambient floor (${estimatedNoiseFloorDb.toInt()} dB)" else "Fixed manual threshold mode",
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = cutSettings.autoNoiseFloor,
                                    onCheckedChange = { onSettingsChanged(cutSettings.copy(autoNoiseFloor = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryCyan,
                                        checkedTrackColor = PrimaryCyan.copy(alpha = 0.35f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = SurfaceDark
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Studio Speech Leveler Toggle (-14 LUFS)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Studio Speech Leveler",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GoldPro.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text("-14 LUFS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GoldPro)
                                        }
                                    }
                                    Text(
                                        text = if (cutSettings.studioAudioLeveling) "Auto-levels dialogue with -1 dB peak limiter" else "Raw recorded volume dynamics",
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = cutSettings.studioAudioLeveling,
                                    onCheckedChange = { onSettingsChanged(cutSettings.copy(studioAudioLeveling = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = GoldPro,
                                        checkedTrackColor = GoldPro.copy(alpha = 0.35f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = SurfaceDark
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Room Tone Smoothing Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Room Tone Smoothing",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (cutSettings.roomToneSmoothing) "15ms S-curve crossfades & ambient floor continuity" else "Hard cut boundaries",
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = cutSettings.roomToneSmoothing,
                                    onCheckedChange = { onSettingsChanged(cutSettings.copy(roomToneSmoothing = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryCyan,
                                        checkedTrackColor = PrimaryCyan.copy(alpha = 0.35f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = SurfaceDark
                                    )
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: RHYTHM PRESETS & CUSTOM PROFILES
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rhythm Profiles",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                if (onSavePreset != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PrimaryCyan.copy(alpha = 0.15f))
                                            .border(1.dp, PrimaryCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                showSavePresetDialog = true
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Save Current", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val displayPresets = if (creatorPresets.isNotEmpty()) creatorPresets else listOf(
                                CreatorPreset("preset_shorts", "Viral Shorts", CutSettings(-30f, 250L, 40L), isBuiltIn = true),
                                CreatorPreset("preset_podcast", "Podcast Studio", CutSettings(-34f, 450L, 70L), isBuiltIn = true),
                                CreatorPreset("preset_lecture", "Fast Lecture", CutSettings(-28f, 200L, 30L), isBuiltIn = true),
                                CreatorPreset("preset_vlog", "Vlog & Story", CutSettings(-36f, 600L, 80L), isBuiltIn = true)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayPresets.forEach { preset ->
                                    val isMatched = (cutSettings.silenceThresholdDb == preset.settings.silenceThresholdDb &&
                                                     cutSettings.minSilenceDurationMs == preset.settings.minSilenceDurationMs &&
                                                     cutSettings.paddingMs == preset.settings.paddingMs)

                                    val icon = when {
                                        preset.name.contains("Short", true) || preset.name.contains("TikTok", true) -> Icons.Default.FlashOn
                                        preset.name.contains("Pod", true) -> Icons.Default.Podcasts
                                        preset.name.contains("Lect", true) -> Icons.Default.Speed
                                        preset.name.contains("Vlog", true) || preset.name.contains("Story", true) -> Icons.Default.Videocam
                                        else -> Icons.Default.Tune
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isMatched) CardDark else BgDark)
                                            .border(1.dp, if (isMatched) PrimaryCyan else CardBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSettingsChanged(preset.settings)
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isMatched) PrimaryCyan else TextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = preset.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isMatched) FontWeight.Bold else FontWeight.SemiBold,
                                                        color = if (isMatched) PrimaryCyan else TextPrimary
                                                    )
                                                    Text(
                                                        text = "${preset.settings.silenceThresholdDb.toInt()} dB • ${preset.settings.minSilenceDurationMs} ms • ${preset.settings.paddingMs} ms pad",
                                                        fontSize = 10.sp,
                                                        color = TextMuted
                                                    )
                                                }
                                            }

                                            if (isMatched) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(PrimaryCyan)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = BgDark)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: SILENCE SEGMENT INSPECTOR
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Tap any silence to seek. Toggle to force-keep specific pauses.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (silences.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No pauses detected under current settings", fontSize = 12.sp, color = TextMuted)
                                }
                            } else {
                                silences.take(40).forEachIndexed { idx, seg ->
                                    val isKept = seg.isExcluded
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CardDark)
                                            .clickable {
                                                exoPlayer.seekTo(seg.startMs)
                                                currentPositionMs = seg.startMs
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeMute,
                                                contentDescription = null,
                                                tint = if (isKept) GreenSuccess else SilenceRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Pause #${idx + 1}: ${formatTime(seg.startMs)} → ${formatTime(seg.endMs)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "${seg.durationMs} ms duration",
                                                    fontSize = 10.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isKept) GreenSuccess.copy(alpha = 0.2f) else SilenceRed.copy(alpha = 0.2f))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onToggleSegment(seg.id)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (isKept) "KEPT" else "CUT",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isKept) GreenSuccess else SilenceRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom Action Bar & Monetization Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
        ) {
            BannerAdComposable(isProUser = isProUser)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isProUser && remainingExports <= 0) {
                            onOpenPro()
                        } else {
                            showExportSheet = true
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
                                if (!isProUser && remainingExports <= 0) Brush.horizontalGradient(listOf(SilenceRed, PrimaryCyan))
                                else Brush.horizontalGradient(listOf(PrimaryCyan, ElectricBlue))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (!isProUser && remainingExports <= 0) Icons.Default.Lock else Icons.Default.ContentCut,
                                contentDescription = "Export",
                                tint = if (!isProUser && remainingExports <= 0) TextPrimary else BgDark
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (!isProUser && remainingExports <= 0) "Daily Limit Reached (2/2 Used) • Get Pro"
                                       else "Export Clean Media (${formatTime(cutDurationMs)})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isProUser && remainingExports <= 0) TextPrimary else BgDark
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            initialConfig = exportConfig.copy(
                studioAudioLeveling = cutSettings.studioAudioLeveling,
                roomToneSmoothing = cutSettings.roomToneSmoothing
            ),
            isVideo = media.isVideo,
            originalDurationMs = originalDurationMs,
            cutDurationMs = cutDurationMs,
            savedPercent = savedPercent,
            isProUser = isProUser,
            remainingExports = remainingExports,
            onOpenPro = onOpenPro,
            onDismiss = { showExportSheet = false },
            onConfirmExport = { config ->
                showExportSheet = false
                onExportConfirm(config)
            },
            onExportEdl = onExportEdl
        )
    }

    if (showSavePresetDialog && onSavePreset != null) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Custom Preset", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Save current threshold and padding values as a reusable profile:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("Preset name (e.g. Wireless Mic)", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = PrimaryCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Values: ${cutSettings.silenceThresholdDb.toInt()} dB • ${cutSettings.minSilenceDurationMs} ms • ${cutSettings.paddingMs} ms padding",
                        fontSize = 11.sp,
                        color = PrimaryCyan
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPresetName.trim().ifEmpty { "Custom Profile" }
                        onSavePreset(name, cutSettings)
                        newPresetName = ""
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Save Preset", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
