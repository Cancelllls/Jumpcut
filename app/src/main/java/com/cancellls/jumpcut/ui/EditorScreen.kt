package com.cancellls.jumpcut.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import com.cancellls.jumpcut.ads.BannerAdComposable
import com.cancellls.jumpcut.model.CreatorPreset
import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.EditorHistorySnapshot
import com.cancellls.jumpcut.model.ExportConfig
import com.cancellls.jumpcut.model.MediaItem
import com.cancellls.jumpcut.model.TargetAspectRatio
import com.cancellls.jumpcut.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput

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
    onToggleAllSilences: ((Boolean) -> Unit)? = null,
    onResetAllSegments: (() -> Unit)? = null,
    onSplitAtPosition: ((Long) -> Boolean)? = null,
    onToggleSegmentCutStatus: ((Int) -> Unit)? = null,
    onNudgeSegment: ((Int, Long, Long) -> Unit)? = null,
    onManualUpdateSegments: ((List<CutSegment>) -> Unit)? = null,
    onExportConfirm: (ExportConfig) -> Unit,
    onSavePreset: ((String, CutSettings) -> Unit)? = null,
    onExportEdl: ((Boolean) -> Unit)? = null,
    onExportSubtitles: ((Boolean) -> Unit)? = null,
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
    var isWarpPreview by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Parameters, 1: Presets, 2: Silence Inspector, 3: Manual Pro
    var showExportSheet by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Pro Manual Editor & Formatting States
    var selectedSegmentId by remember { mutableStateOf<Int?>(null) }
    var previewAspectRatio by remember { mutableStateOf(exportConfig.targetAspectRatio) }
    var markInMs by remember { mutableStateOf<Long?>(null) }
    var markOutMs by remember { mutableStateOf<Long?>(null) }
    val undoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }
    val redoStack = remember { mutableStateListOf<EditorHistorySnapshot>() }

    fun pushUndo(description: String) {
        undoStack.add(EditorHistorySnapshot(segments = segments, cutSettings = cutSettings, description = description))
        redoStack.clear()
        if (undoStack.size > 35) {
            undoStack.removeAt(0)
        }
    }

    fun handleUndo() {
        if (undoStack.isEmpty()) return
        val current = EditorHistorySnapshot(segments = segments, cutSettings = cutSettings, description = "Current")
        redoStack.add(current)
        val target = undoStack.removeAt(undoStack.size - 1)
        onManualUpdateSegments?.invoke(target.segments)
        onSettingsChanged(target.cutSettings)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        android.widget.Toast.makeText(context, "↶ Undid: ${target.description.ifBlank { "Action" }}", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun handleRedo() {
        if (redoStack.isEmpty()) return
        val current = EditorHistorySnapshot(segments = segments, cutSettings = cutSettings, description = "Current")
        undoStack.add(current)
        val target = redoStack.removeAt(redoStack.size - 1)
        onManualUpdateSegments?.invoke(target.segments)
        onSettingsChanged(target.cutSettings)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        android.widget.Toast.makeText(context, "↷ Redid: ${target.description.ifBlank { "Action" }}", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Audition specific pause segment state
    var auditionTargetEndMs by remember { mutableLongStateOf(-1L) }
    var auditioningPauseId by remember { mutableStateOf<Int?>(null) }

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
                if (!playing) {
                    auditioningPauseId = null
                    auditionTargetEndMs = -1L
                }
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

    // Playback loop tracking position & auto-skipping or time-warping silence
    LaunchedEffect(isPlaying, skipSilencePreview, isWarpPreview, isComparingRaw, segments, auditionTargetEndMs, playbackSpeed) {
        while (isPlaying) {
            val pos = exoPlayer.currentPosition
            currentPositionMs = pos

            if (auditionTargetEndMs > 0L && pos >= auditionTargetEndMs) {
                exoPlayer.pause()
                auditionTargetEndMs = -1L
                auditioningPauseId = null
            } else if (!isComparingRaw && auditionTargetEndMs <= 0L) {
                val currentSilence = segments.firstOrNull {
                    it.isSilence && !it.isExcluded && pos in it.startMs until it.endMs
                }
                if (isWarpPreview) {
                    val targetSpeed = if (currentSilence != null) 3.0f else playbackSpeed
                    if (exoPlayer.playbackParameters.speed != targetSpeed) {
                        exoPlayer.playbackParameters = PlaybackParameters(targetSpeed)
                    }
                } else if (skipSilencePreview) {
                    if (exoPlayer.playbackParameters.speed != playbackSpeed) {
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    }
                    if (currentSilence != null && currentSilence.endMs < originalDurationMs) {
                        exoPlayer.seekTo(currentSilence.endMs)
                        currentPositionMs = currentSilence.endMs
                    }
                } else {
                    if (exoPlayer.playbackParameters.speed != playbackSpeed) {
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    }
                }
            }
            delay(35)
        }
    }

    // Live Decibel Calculation from Audio Energy at Playhead
    val currentAmp = remember(currentPositionMs, waveformAmplitudes, originalDurationMs) {
        if (waveformAmplitudes.isNotEmpty() && originalDurationMs > 0L) {
            val index = ((currentPositionMs.toFloat() / originalDurationMs) * waveformAmplitudes.size).toInt()
                .coerceIn(0, waveformAmplitudes.size - 1)
            waveformAmplitudes[index]
        } else 0f
    }
    val currentDb = remember(currentAmp) {
        if (currentAmp > 0.0001f) (20f * kotlin.math.log10(currentAmp)).coerceIn(-60f, 0f)
        else -60f
    }
    val isCurrentPositionSilence = remember(currentPositionMs, segments) {
        segments.any { it.isSilence && !it.isExcluded && currentPositionMs in it.startMs until it.endMs }
    }

    val silences = remember(segments) { segments.filter { it.isSilence } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top Action Bar Dock
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(listOf(CardBorderSubtle, Color.Transparent)),
                    shape = RoundedCornerShape(0.dp)
                ),
            color = SurfaceDark
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorderSubtle, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                val cleanTitle = remember(media.name) {
                    media.name
                        .replace(Regex("^JumpCut_"), "")
                        .replace(Regex("_\\d{10,}.*$"), "")
                        .replace("_", " ")
                        .ifBlank { media.name }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = cleanTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${silences.size} silences detected • lossless mode",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Undo Button
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (undoStack.isNotEmpty()) CardDarkElevated else CardDark.copy(alpha = 0.5f))
                            .border(1.dp, if (undoStack.isNotEmpty()) PrimaryCyan.copy(alpha = 0.5f) else CardBorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) PrimaryCyan else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Redo Button
                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (redoStack.isNotEmpty()) CardDarkElevated else CardDark.copy(alpha = 0.5f))
                            .border(1.dp, if (redoStack.isNotEmpty()) PrimaryCyan.copy(alpha = 0.5f) else CardBorderSubtle, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) PrimaryCyan else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (onExportEdl != null) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExportEdl(false)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardDarkElevated)
                                .border(1.dp, CardBorderSubtle, CircleShape)
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

                    // Time Saved Badge (Sophisticated Translucent Emerald Pill)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(GreenSuccess.copy(alpha = 0.15f))
                            .border(1.dp, GreenSuccess.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "-${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))} ($savedPercent%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GreenSuccess,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
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

                // Video Resolution & Aspect Ratio Badge (Clickable Guide Selector)
                if (media.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (previewAspectRatio != TargetAspectRatio.ORIGINAL) PrimaryCyan.copy(alpha = 0.25f) else BgDark.copy(alpha = 0.8f))
                            .border(1.dp, if (previewAspectRatio != TargetAspectRatio.ORIGINAL) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(6.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val values = TargetAspectRatio.entries
                                val nextIdx = (values.indexOf(previewAspectRatio) + 1) % values.size
                                previewAspectRatio = values[nextIdx]
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (previewAspectRatio == TargetAspectRatio.ORIGINAL) "${media.width}x${media.height} • $aspectRatioBadge"
                                   else "Crop: ${previewAspectRatio.tag}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (previewAspectRatio != TargetAspectRatio.ORIGINAL) PrimaryCyan else TextPrimary
                        )
                    }
                }

                // Social Aspect Ratio Safe-Zone Framing Guide Mask
                if (media.isVideo && previewAspectRatio.ratio != null) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalW = maxWidth
                        val targetW = (maxHeight * previewAspectRatio.ratio!!).coerceAtMost(totalW)
                        val maskW = ((totalW - targetW) / 2f).coerceAtLeast(0.dp)
                        if (maskW > 0.dp) {
                            // Left dark pillarbox mask
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(maskW)
                                    .align(Alignment.CenterStart)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            )
                            // Right dark pillarbox mask
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(maskW)
                                    .align(Alignment.CenterEnd)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            )
                            // Safe frame border with corner guides
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(targetW)
                                    .align(Alignment.Center)
                                    .border(1.dp, PrimaryCyan.copy(alpha = 0.45f))
                            )
                        }
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
                        color = PrimaryCyan,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = " / ${formatTime(originalDurationMs)}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                // Connected Segmented Speed Selector Capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorderSubtle, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(1.0f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) PrimaryCyan else Color.Transparent)
                                .clickable {
                                    playbackSpeed = speed
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
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
                        .background(if (isComparingRaw) SilenceRed else CardDarkElevated)
                        .border(1.dp, if (isComparingRaw) SilenceRed else CardBorderSubtle, RoundedCornerShape(8.dp))
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
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isComparingRaw) "RAW ACTIVE" else "HOLD RAW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isComparingRaw) TextPrimary else TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                // Playback JumpCut / Warp Capsule
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorderSubtle, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (skipSilencePreview && !isWarpPreview) PrimaryCyan else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isWarpPreview) isWarpPreview = false
                                onToggleSkipSilence(!skipSilencePreview)
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cut",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (skipSilencePreview && !isWarpPreview) BgDark else TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isWarpPreview) PrimaryCyan else Color.Transparent)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isWarpPreview = !isWarpPreview
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "3× Warp",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWarpPreview) BgDark else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Precision Waveform (Pinned!)
            WaveformView(
                amplitudes = waveformAmplitudes,
                segments = segments,
                totalDurationMs = originalDurationMs,
                currentPositionMs = currentPositionMs,
                selectedSegmentId = selectedSegmentId,
                onSegmentClick = { seg ->
                    selectedSegmentId = seg.id
                    currentPositionMs = seg.startMs
                    exoPlayer.seekTo(seg.startMs)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onSeek = { seekMs ->
                    exoPlayer.seekTo(seekMs)
                    currentPositionMs = seekMs
                    selectedSegmentId = segments.firstOrNull { seekMs in it.startMs until it.endMs }?.id
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioCardBrush)
                    .border(1.dp, StudioCardBorderBrush, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Editor Ribbon (Pinned directly under waveform)
            val currentSegAtPlayhead = remember(segments, currentPositionMs, selectedSegmentId) {
                segments.firstOrNull { it.id == selectedSegmentId }
                    ?: segments.firstOrNull { currentPositionMs in it.startMs until it.endMs }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardDarkElevated)
                    .border(1.dp, CardBorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Split at Playhead (Razor Tool)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PrimaryCyan.copy(alpha = 0.15f))
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable {
                            pushUndo("Split at ${formatTime(currentPositionMs)}")
                            val splitOk = onSplitAtPosition?.invoke(currentPositionMs) ?: false
                            if (splitOk) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                android.widget.Toast.makeText(context, "✂ Sliced at ${formatTime(currentPositionMs)}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = PrimaryCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Split (✂)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                    }
                }

                // Invert / Toggle Voice / Cut for current segment
                if (currentSegAtPlayhead != null) {
                    val isSil = currentSegAtPlayhead.isSilence && !currentSegAtPlayhead.isExcluded
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSil) GreenSuccess.copy(alpha = 0.18f) else SilenceRed.copy(alpha = 0.18f))
                            .border(1.dp, if (isSil) GreenSuccess.copy(alpha = 0.4f) else SilenceRed.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                pushUndo("Toggle Segment #${currentSegAtPlayhead.id}")
                                onToggleSegmentCutStatus?.invoke(currentSegAtPlayhead.id)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSil) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isSil) GreenSuccess else SilenceRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isSil) "Keep Voice" else "Cut Silence",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSil) GreenSuccess else SilenceRed
                            )
                        }
                    }
                }

                // In / Out Quick Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (markInMs != null) PrimaryCyan.copy(alpha = 0.25f) else CardDark)
                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                            .clickable {
                                markInMs = currentPositionMs
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                android.widget.Toast.makeText(context, "In: ${formatTime(currentPositionMs)}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (markInMs != null) "[ ${formatTime(markInMs!!)}" else "[ In",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (markInMs != null) PrimaryCyan else TextSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (markOutMs != null) PrimaryCyan.copy(alpha = 0.25f) else CardDark)
                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                            .clickable {
                                markOutMs = currentPositionMs
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                android.widget.Toast.makeText(context, "Out: ${formatTime(currentPositionMs)}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (markOutMs != null) "${formatTime(markOutMs!!)} ]" else "] Out",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (markOutMs != null) PrimaryCyan else TextSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                // Pro Manual Quick Switch Tab Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedTab == 3) PrimaryCyan else CardDark)
                        .border(1.dp, if (selectedTab == 3) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(6.dp))
                        .clickable {
                            selectedTab = 3
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Manual ✂",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 3) BgDark else PrimaryCyan
                    )
                }
            }
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
            // Mode Selector Tabs (0: Tuning, 1: Presets, 2: Silence Inspector, 3: Manual Pro)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = PrimaryCyan,
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(3.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedTab == 0) CardDarkElevated else Color.Transparent)
                        .padding(vertical = 8.dp)
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
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedTab == 1) CardDarkElevated else Color.Transparent)
                        .padding(vertical = 8.dp)
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
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedTab == 2) CardDarkElevated else Color.Transparent)
                        .padding(vertical = 8.dp)
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

                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedTab == 3) CardDarkElevated else Color.Transparent)
                        .padding(vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = if (selectedTab == 3) PrimaryCyan else TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Manual ✂",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 3) PrimaryCyan else TextSecondary
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, StudioCardBorderBrush, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioCardBrush)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Live Impact Readout Banner with Voice Discriminator stats
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardDarkElevated)
                                        .border(1.dp, CardBorderSubtle, RoundedCornerShape(10.dp))
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
                                                color = if (estimatedNoiseFloorDb > -35f) GoldPro else PrimaryCyan,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        Text(
                                            text = "${silences.size} cuts • -${formatTime((originalDurationMs - cutDurationMs).coerceAtLeast(0))}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GreenSuccess,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Rhythm Presets Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val quickPresets = listOf(
                                        Triple("Shorts / Fast", Icons.Default.Bolt, CutSettings(silenceThresholdDb = -30f, minSilenceDurationMs = 200L, paddingMs = 35L, voiceNoiseRejection = 0.70f)),
                                        Triple("YouTube Video", Icons.Default.SmartDisplay, CutSettings(silenceThresholdDb = -32f, minSilenceDurationMs = 320L, paddingMs = 50L, voiceNoiseRejection = 0.65f)),
                                        Triple("Podcast / Natural", Icons.Default.Mic, CutSettings(silenceThresholdDb = -35f, minSilenceDurationMs = 500L, paddingMs = 80L, voiceNoiseRejection = 0.55f)),
                                        Triple("Lecture / Seminar", Icons.Default.School, CutSettings(silenceThresholdDb = -38f, minSilenceDurationMs = 650L, paddingMs = 90L, voiceNoiseRejection = 0.50f))
                                    )
                                    quickPresets.forEach { (label, icon, presetSettings) ->
                                        val isActive = cutSettings.minSilenceDurationMs == presetSettings.minSilenceDurationMs &&
                                                       cutSettings.silenceThresholdDb == presetSettings.silenceThresholdDb
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) PrimaryCyan.copy(alpha = 0.18f) else CardDarkElevated)
                                                .border(1.dp, if (isActive) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSettingsChanged(presetSettings)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(icon, contentDescription = null, tint = if (isActive) PrimaryCyan else TextSecondary, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isActive) PrimaryCyan else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Slider 1: Voice vs Background Noise Armor
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "Voice vs Noise Armor", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        Text(text = "Filters out fans, A/C, street rumble & room hum", fontSize = 10.sp, color = TextSecondary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardDarkElevated)
                                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${(cutSettings.voiceNoiseRejection * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PrimaryCyan,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                                Slider(
                                    value = cutSettings.voiceNoiseRejection,
                                    onValueChange = { onSettingsChanged(cutSettings.copy(voiceNoiseRejection = it)) },
                                    valueRange = 0.15f..0.95f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryCyan,
                                        activeTrackColor = PrimaryCyan,
                                        inactiveTrackColor = CardDarkElevated
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Real-Time Studio VU Level Meter
                                StudioVuMeter(
                                    currentDb = currentDb,
                                    thresholdDb = cutSettings.silenceThresholdDb,
                                    isSilence = isCurrentPositionSilence
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Slider 2: Silence Sensitivity (dB) + AI Auto-Calibrate
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "Silence Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(text = "Base cutoff threshold", fontSize = 10.sp, color = TextSecondary)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // AI Auto-Calibrate Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(PrimaryCyan.copy(alpha = 0.15f))
                                                .border(1.dp, PrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (waveformAmplitudes.isNotEmpty()) {
                                                        val nonZero = waveformAmplitudes.filter { it > 0.0001f }.sorted()
                                                        if (nonZero.isNotEmpty()) {
                                                            val noiseAmp = nonZero[(nonZero.size * 0.15f).toInt().coerceIn(0, nonZero.size - 1)]
                                                            val speechAmp = nonZero[(nonZero.size * 0.75f).toInt().coerceIn(0, nonZero.size - 1)]
                                                            val noiseDb = (20f * kotlin.math.log10(noiseAmp)).coerceIn(-60f, -20f)
                                                            val speechDb = (20f * kotlin.math.log10(speechAmp)).coerceIn(-40f, -5f)
                                                            val calibratedThreshold = ((noiseDb + speechDb) / 2f).coerceIn(-42f, -22f)
                                                            onSettingsChanged(
                                                                cutSettings.copy(
                                                                    silenceThresholdDb = calibratedThreshold,
                                                                    voiceNoiseRejection = if (noiseDb > -35f) 0.75f else 0.60f
                                                                )
                                                            )
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                "AI Calibrated: ${calibratedThreshold.toInt()} dB (Noise floor: ${noiseDb.toInt()} dB)",
                                                                android.widget.Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Auto-Calibrate", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(CardDarkElevated)
                                                .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "${cutSettings.silenceThresholdDb.toInt()} dB",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PrimaryCyan,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                Slider(
                                    value = cutSettings.silenceThresholdDb,
                                    onValueChange = {
                                        if (it.toInt() != cutSettings.silenceThresholdDb.toInt()) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        onSettingsChanged(cutSettings.copy(silenceThresholdDb = it))
                                    },
                                    valueRange = -45f..-18f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryCyan,
                                        activeTrackColor = PrimaryCyan,
                                        inactiveTrackColor = CardDarkElevated
                                    )
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
                                            .background(CardDarkElevated)
                                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${cutSettings.minSilenceDurationMs} ms",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PrimaryCyan,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                                Slider(
                                    value = cutSettings.minSilenceDurationMs.toFloat(),
                                    onValueChange = { onSettingsChanged(cutSettings.copy(minSilenceDurationMs = it.toLong())) },
                                    valueRange = 150f..800f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryCyan,
                                        activeTrackColor = PrimaryCyan,
                                        inactiveTrackColor = CardDarkElevated
                                    )
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
                                            .background(CardDarkElevated)
                                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "${cutSettings.paddingMs} ms",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PrimaryCyan,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                                Slider(
                                    value = cutSettings.paddingMs.toFloat(),
                                    onValueChange = { onSettingsChanged(cutSettings.copy(paddingMs = it.toLong())) },
                                    valueRange = 15f..120f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = PrimaryCyan,
                                        activeTrackColor = PrimaryCyan,
                                        inactiveTrackColor = CardDarkElevated
                                    )
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
                                        checkedThumbColor = BgDark,
                                        checkedTrackColor = PrimaryCyan,
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
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GoldPro.copy(alpha = 0.15f))
                                                .border(0.5.dp, GoldPro.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
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
                                        checkedThumbColor = BgDark,
                                        checkedTrackColor = GoldPro,
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
                                        checkedThumbColor = BgDark,
                                        checkedTrackColor = PrimaryCyan,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = SurfaceDark
                                    )
                                )
                            }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tap to seek • Audition pauses before cutting",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Batch Action Toolbar (Cut All, Keep All, Reset Defaults)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SilenceRed.copy(alpha = 0.15f))
                                        .border(1.dp, SilenceRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleAllSilences?.invoke(true)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = SilenceRed, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cut All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SilenceRed)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GreenSuccess.copy(alpha = 0.15f))
                                        .border(1.dp, GreenSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleAllSilences?.invoke(false)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Keep All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark)
                                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onResetAllSegments?.invoke()
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

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
                                    val isAuditioningThis = auditioningPauseId == seg.id && isPlaying
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isAuditioningThis) PrimaryCyan.copy(alpha = 0.08f) else CardDark)
                                            .border(1.dp, if (isAuditioningThis) PrimaryCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                                            .clickable {
                                                exoPlayer.seekTo(seg.startMs)
                                                currentPositionMs = seg.startMs
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeMute,
                                                contentDescription = null,
                                                tint = if (isKept) GreenSuccess else SilenceRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
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

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Mini Audition Play/Stop Button
                                            IconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (isAuditioningThis) {
                                                        exoPlayer.pause()
                                                        auditioningPauseId = null
                                                        auditionTargetEndMs = -1L
                                                    } else {
                                                        val auditionStart = (seg.startMs - 150L).coerceAtLeast(0L)
                                                        val auditionEnd = (seg.endMs + 150L).coerceAtMost(originalDurationMs)
                                                        auditioningPauseId = seg.id
                                                        auditionTargetEndMs = auditionEnd
                                                        exoPlayer.seekTo(auditionStart)
                                                        currentPositionMs = auditionStart
                                                        exoPlayer.play()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAuditioningThis) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = "Audition Pause",
                                                    tint = if (isAuditioningThis) GoldPro else PrimaryCyan,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isKept) GreenSuccess.copy(alpha = 0.2f) else SilenceRed.copy(alpha = 0.2f))
                                                    .clickable {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onToggleSegment(seg.id)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
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

                3 -> {
                    // TAB 3: PRO MANUAL EDITOR (Razor, Nudge, In/Out Trimming, Segment Inspector)
                    val activeSeg = remember(segments, currentPositionMs, selectedSegmentId) {
                        segments.firstOrNull { it.id == selectedSegmentId }
                            ?: segments.firstOrNull { currentPositionMs in it.startMs until it.endMs }
                            ?: segments.minByOrNull { kotlin.math.abs(it.startMs - currentPositionMs) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Section 1: Active Segment Inspector & Razor Split
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Active Segment Inspector", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                if (activeSeg != null) {
                                    val isCut = activeSeg.isSilence && !activeSeg.isExcluded
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCut) SilenceRed.copy(alpha = 0.2f) else GreenSuccess.copy(alpha = 0.2f))
                                            .border(1.dp, if (isCut) SilenceRed.copy(alpha = 0.5f) else GreenSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (isCut) "CUT (REMOVED)" else "VOICE (KEPT)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isCut) SilenceRed else GreenSuccess
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (activeSeg != null) {
                                val isCut = activeSeg.isSilence && !activeSeg.isExcluded
                                val segDuration = activeSeg.endMs - activeSeg.startMs

                                // Info Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDarkElevated)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Segment #${activeSeg.id}", fontSize = 10.sp, color = TextSecondary)
                                        Text(
                                            "${formatTime(activeSeg.startMs)} → ${formatTime(activeSeg.endMs)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryCyan,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Duration", fontSize = 10.sp, color = TextSecondary)
                                        Text(
                                            String.format(Locale.US, "%.2fs (%d ms)", segDuration / 1000f, segDuration),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Toggle Cut / Voice Button
                                Button(
                                    onClick = {
                                        pushUndo("Toggle Segment #${activeSeg.id}")
                                        onToggleSegmentCutStatus?.invoke(activeSeg.id)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCut) GreenSuccess.copy(alpha = 0.2f) else SilenceRed.copy(alpha = 0.2f),
                                        contentColor = if (isCut) GreenSuccess else SilenceRed
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isCut) GreenSuccess.copy(alpha = 0.5f) else SilenceRed.copy(alpha = 0.5f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isCut) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCut) "Restore to Voice (Keep in final cut)" else "Mark as Silence (Remove from final cut)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Razor Tool: Split at current playhead
                                val canSplitHere = currentPositionMs > activeSeg.startMs + 50L && currentPositionMs < activeSeg.endMs - 50L
                                Button(
                                    onClick = {
                                        pushUndo("Split at ${formatTime(currentPositionMs)}")
                                        val ok = onSplitAtPosition?.invoke(currentPositionMs) ?: false
                                        if (ok) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            android.widget.Toast.makeText(context, "✂ Sliced at ${formatTime(currentPositionMs)}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = canSplitHere,
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryCyan.copy(alpha = 0.2f),
                                        contentColor = PrimaryCyan,
                                        disabledContainerColor = CardDark,
                                        disabledContentColor = TextMuted
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (canSplitHere) PrimaryCyan.copy(alpha = 0.4f) else CardBorderSubtle)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (canSplitHere) "✂ Split at Playhead (${formatTime(currentPositionMs)})"
                                                   else "Move playhead inside segment to split",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Section 2: Boundary Nudge Controls (±100ms / ±500ms)
                                Text("Fine Precision Boundary Nudge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Adjust boundaries by ±100ms or ±500ms so dialogue is never clipped", fontSize = 10.sp, color = TextSecondary)

                                Spacer(modifier = Modifier.height(8.dp))

                                // Start Boundary Nudges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Start Boundary", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(CardDarkElevated)
                                                    .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        pushUndo("Nudge Start ${if (delta > 0) "+$delta" else "$delta"}ms")
                                                        onNudgeSegment?.invoke(activeSeg.id, delta, 0L)
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (delta > 0) "+${delta}ms" else "${delta}ms",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryCyan
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // End Boundary Nudges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("End Boundary", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(-500L, -100L, 100L, 500L).forEach { delta ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(CardDarkElevated)
                                                    .border(1.dp, CardBorderSubtle, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        pushUndo("Nudge End ${if (delta > 0) "+$delta" else "$delta"}ms")
                                                        onNudgeSegment?.invoke(activeSeg.id, 0L, delta)
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (delta > 0) "+${delta}ms" else "${delta}ms",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("No segment selected. Tap on any segment in the waveform or move playhead.", fontSize = 11.sp, color = TextMuted)
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorderSubtle))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Section 3: In / Out Work Area Trimming
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Crop, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Work Area Range (In / Out)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                                if (markInMs != null || markOutMs != null) {
                                    Text(
                                        text = "Clear",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SilenceRed,
                                        modifier = Modifier.clickable {
                                            markInMs = null
                                            markOutMs = null
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        markInMs = currentPositionMs
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
                                ) {
                                    Text(
                                        text = if (markInMs != null) "In: ${formatTime(markInMs!!)}" else "[ Mark In",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (markInMs != null) PrimaryCyan else TextSecondary
                                    )
                                }

                                Button(
                                    onClick = {
                                        markOutMs = currentPositionMs
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardDarkElevated),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
                                ) {
                                    Text(
                                        text = if (markOutMs != null) "Out: ${formatTime(markOutMs!!)}" else "] Mark Out",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (markOutMs != null) PrimaryCyan else TextSecondary
                                    )
                                }
                            }

                            if (markInMs != null || markOutMs != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val inPos = markInMs ?: 0L
                                        val outPos = markOutMs ?: originalDurationMs
                                        pushUndo("Trim Outside Work Area")
                                        val updated = segments.map { seg ->
                                            if (seg.endMs <= inPos || seg.startMs >= outPos) {
                                                seg.copy(isSilence = true, isExcluded = false)
                                            } else seg
                                        }
                                        onManualUpdateSegments?.invoke(updated)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        android.widget.Toast.makeText(context, "Trimmed media outside In/Out", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan.copy(alpha = 0.15f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.35f))
                                ) {
                                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cut Outside In/Out Work Area", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardBorderSubtle))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Section 4: Mini Segment Strip (Quick Jump to Any Segment)
                            Text("All Segments (${segments.size} cuts & blocks)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            val segmentsSlice = remember(segments, currentPositionMs) {
                                val currentIdx = segments.indexOfFirst { currentPositionMs in it.startMs until it.endMs }.coerceAtLeast(0)
                                val start = (currentIdx - 5).coerceAtLeast(0)
                                val end = (currentIdx + 15).coerceAtMost(segments.size)
                                segments.subList(start, end)
                            }

                            segmentsSlice.forEach { seg ->
                                val isSelected = seg.id == activeSeg?.id
                                val isCut = seg.isSilence && !seg.isExcluded
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) PrimaryCyan.copy(alpha = 0.15f) else CardDark)
                                        .border(1.dp, if (isSelected) PrimaryCyan else CardBorderSubtle, RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedSegmentId = seg.id
                                            currentPositionMs = seg.startMs
                                            exoPlayer.seekTo(seg.startMs)
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "#${seg.id}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PrimaryCyan else TextSecondary,
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Text(
                                            "${formatTime(seg.startMs)} - ${formatTime(seg.endMs)}",
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = TextPrimary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isCut) SilenceRed.copy(alpha = 0.2f) else GreenSuccess.copy(alpha = 0.2f))
                                                .clickable {
                                                    pushUndo("Toggle #${seg.id}")
                                                    onToggleSegmentCutStatus?.invoke(seg.id)
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isCut) "CUT" else "KEEP",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCut) SilenceRed else GreenSuccess
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
                        showExportSheet = true
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
                            .background(
                                if (!isProUser && remainingExports <= 0) Brush.horizontalGradient(listOf(SilenceRed, SilenceRed.copy(alpha = 0.8f)))
                                else StudioTealGradient,
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (!isProUser && remainingExports <= 0) Icons.Default.Lock else Icons.Default.ContentCut,
                                contentDescription = "Export",
                                tint = BgDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!isProUser && remainingExports <= 0) "Daily Limit Reached (2/2 Used) • Get Pro"
                                       else "Export Clean Media (${formatTime(cutDurationMs)})",
                                fontSize = 14.sp,
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
            initialConfig = exportConfig.copy(
                studioAudioLeveling = cutSettings.studioAudioLeveling,
                roomToneSmoothing = cutSettings.roomToneSmoothing,
                targetAspectRatio = previewAspectRatio
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
            onExportEdl = onExportEdl,
            onExportSubtitles = onExportSubtitles
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

@Composable
fun StudioVuMeter(
    currentDb: Float,
    thresholdDb: Float,
    isSilence: Boolean,
    modifier: Modifier = Modifier
) {
    val normalizedLevel = ((currentDb + 60f) / 60f).coerceIn(0f, 1f)
    val normalizedThreshold = ((thresholdDb + 60f) / 60f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDarkElevated)
            .border(1.dp, CardBorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = if (isSilence) SilenceRed else PrimaryCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "STUDIO VU LEVEL",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%.1f dB", currentDb),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (isSilence) SilenceRed else PrimaryCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSilence) SilenceRed.copy(alpha = 0.2f) else PrimaryCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isSilence) "CUT" else "VOICE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSilence) SilenceRed else PrimaryCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Segmented LED VU Track with Threshold needle
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            // Background track
            drawRect(color = BgDark)

            // Active level fill
            if (normalizedLevel > 0.01f) {
                val fillWidth = size.width * normalizedLevel
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(GreenSuccess, PrimaryCyan, GoldPro, SilenceRed),
                        startX = 0f,
                        endX = size.width
                    ),
                    size = androidx.compose.ui.geometry.Size(fillWidth, size.height)
                )
            }

            // Threshold marker needle
            val thresholdX = size.width * normalizedThreshold
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(thresholdX, 0f),
                end = androidx.compose.ui.geometry.Offset(thresholdX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-60 dB", fontSize = 8.sp, color = TextMuted)
            Text("Threshold: ${thresholdDb.toInt()} dB", fontSize = 8.sp, color = PrimaryCyan, fontWeight = FontWeight.Bold)
            Text("0 dB", fontSize = 8.sp, color = TextMuted)
        }
    }
}
