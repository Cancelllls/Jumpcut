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
    skipSilencePreview: Boolean,
    exportConfig: ExportConfig,
    creatorPresets: List<CreatorPreset> = emptyList(),
    isProUser: Boolean = false,
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
                    Text(
                        text = "✂️ ${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))} ($savedPercent%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BgDark
                    )
                }
            }
        }

        // Scrollable Upper Content (Player + Waveform + Speed)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Video Preview Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
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
                        Icon(Icons.Default.GraphicEq, contentDescription = "Audio", tint = PrimaryCyan, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = media.name, fontSize = 14.sp, color = TextPrimary)
                    }
                }

                // Play / Pause Floating Overlay Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Video Resolution & Aspect Ratio Badge
                if (media.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgDark.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SilenceRed.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AUDITIONING RAW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time and Playback Speed Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(currentPositionMs)} / ${formatTime(originalDurationMs)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                // Speed Selector Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1.0f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryCyan else CardDark)
                                .clickable {
                                    playbackSpeed = speed
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${speed}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BgDark else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Skip Silence in Preview & A/B Audition Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = if (skipSilencePreview) PrimaryCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Skip Silence",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (skipSilencePreview) PrimaryCyan else TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isComparingRaw) Icons.Default.Hearing else Icons.Default.GraphicEq,
                                contentDescription = "A/B Raw",
                                tint = if (isComparingRaw) TextPrimary else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isComparingRaw) "AUDITIONING RAW" else "HOLD FOR RAW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isComparingRaw) TextPrimary else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = skipSilencePreview,
                        onCheckedChange = { onToggleSkipSilence(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = PrimaryCyan,
                            uncheckedTrackColor = CardDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Waveform
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector Tabs (Parameters vs Segment Inspector)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = PrimaryCyan,
                indicator = {},
                divider = {},
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(4.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) CardDark else Color.Transparent)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Threshold Tuning",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) PrimaryCyan else TextSecondary
                    )
                }

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) CardDark else Color.Transparent)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Silence List (${silences.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) PrimaryCyan else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab Content
            if (selectedTab == 0) {
                // Preset Quick-Select Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // + Save Preset chip
                    if (onSavePreset != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryCyan.copy(alpha = 0.15f))
                                .border(1.dp, PrimaryCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showSavePresetDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                            }
                        }
                    }

                    // Available presets (Built-in + Custom)
                    creatorPresets.forEach { preset ->
                        val isMatched = (cutSettings.silenceThresholdDb == preset.settings.silenceThresholdDb &&
                                         cutSettings.minSilenceDurationMs == preset.settings.minSilenceDurationMs &&
                                         cutSettings.paddingMs == preset.settings.paddingMs)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isMatched) CardBorder else CardDark)
                                .border(1.dp, if (isMatched) PrimaryCyan else CardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSettingsChanged(preset.settings)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.name,
                                fontSize = 11.sp,
                                fontWeight = if (isMatched) FontWeight.Bold else FontWeight.Normal,
                                color = if (isMatched) PrimaryCyan else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sliders Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Slider 1: Silence Threshold (dB)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Silence Sensitivity", fontSize = 13.sp, color = TextPrimary)
                            Text(text = "${cutSettings.silenceThresholdDb.toInt()} dB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                        }
                        Slider(
                            value = cutSettings.silenceThresholdDb,
                            onValueChange = { onSettingsChanged(cutSettings.copy(silenceThresholdDb = it)) },
                            valueRange = -45f..-18f,
                            colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Slider 2: Min Silence Duration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Min Silence Length", fontSize = 13.sp, color = TextPrimary)
                            Text(text = "${cutSettings.minSilenceDurationMs} ms", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        }
                        Slider(
                            value = cutSettings.minSilenceDurationMs.toFloat(),
                            onValueChange = { onSettingsChanged(cutSettings.copy(minSilenceDurationMs = it.toLong())) },
                            valueRange = 150f..800f,
                            colors = SliderDefaults.colors(thumbColor = ElectricBlue, activeTrackColor = ElectricBlue)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Slider 3: Speech Padding Buffer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Word Padding Armor", fontSize = 13.sp, color = TextPrimary)
                            Text(text = "${cutSettings.paddingMs} ms", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                        }
                        Slider(
                            value = cutSettings.paddingMs.toFloat(),
                            onValueChange = { onSettingsChanged(cutSettings.copy(paddingMs = it.toLong())) },
                            valueRange = 15f..120f,
                            colors = SliderDefaults.colors(thumbColor = GreenSuccess, activeTrackColor = GreenSuccess)
                        )
                    }
                }
            } else {
                // Segment Inspector List
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Tap a pause to inspect and listen. Toggle to force keep or cut.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        silences.take(30).forEachIndexed { idx, seg ->
                            val isKept = seg.isExcluded // if excluded from cutting, it is kept in video
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
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
                                        modifier = Modifier.size(18.dp)
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
                                            text = "${seg.durationMs} ms",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                // Toggle button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isKept) GreenSuccess.copy(alpha = 0.2f) else SilenceRed.copy(alpha = 0.2f))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleSegment(seg.id)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isKept) "KEEP" else "CUT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isKept) GreenSuccess else SilenceRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                        showExportSheet = true
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
                            .background(Brush.horizontalGradient(listOf(PrimaryCyan, ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Export", tint = BgDark)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Export Clean Media (${formatTime(cutDurationMs)})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BgDark
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            initialConfig = exportConfig,
            isVideo = media.isVideo,
            originalDurationMs = originalDurationMs,
            cutDurationMs = cutDurationMs,
            savedPercent = savedPercent,
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
