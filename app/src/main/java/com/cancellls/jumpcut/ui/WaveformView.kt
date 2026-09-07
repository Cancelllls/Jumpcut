package com.cancellls.jumpcut.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.theme.*

@Composable
fun WaveformView(
    amplitudes: List<Float>,
    segments: List<CutSegment>,
    totalDurationMs: Long,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val textPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.argb(160, 148, 163, 184) // TextSecondary / TextMuted
            textSize = with(density) { 9.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    Column(modifier = modifier) {
        // Header: Studio Legend & Multi-scale Zoom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Precision Legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SpeechCyan)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "Voice", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SilenceRed)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "Cut", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SilenceRed)

                val hasKeptPauses = remember(segments) { segments.any { it.isSilence && it.isExcluded } }
                if (hasKeptPauses) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GreenSuccess)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Kept", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                }
            }

            // Zoom chips (1x, 2x, 4x)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "ZOOM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextMuted,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(end = 2.dp)
                )

                listOf(1f to "1x", 2f to "2x", 4f to "4x").forEach { (scale, label) ->
                    val isSelected = zoomScale == scale
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) PrimaryCyan else CardDark)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                zoomScale = scale
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) BgDark else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable Multi-Scale Canvas Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val viewportWidth = maxWidth
            val canvasWidthDp = maxWidth * zoomScale

            // Smooth playhead tracking when zoomed
            LaunchedEffect(currentPositionMs, zoomScale) {
                if (zoomScale > 1f && totalDurationMs > 0) {
                    val progress = (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    val viewportPx = with(density) { viewportWidth.toPx() }
                    val totalWidthPx = with(density) { canvasWidthDp.toPx() }
                    val playheadPx = progress * totalWidthPx
                    val targetScroll = (playheadPx - viewportPx / 2f).coerceIn(0f, scrollState.maxValue.toFloat())
                    scrollState.scrollTo(targetScroll.toInt())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState, enabled = zoomScale > 1f)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(canvasWidthDp)
                        .fillMaxHeight()
                        .pointerInput(totalDurationMs, zoomScale) {
                            detectTapGestures { offset ->
                                if (totalDurationMs > 0 && size.width > 0) {
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeek((fraction * totalDurationMs).toLong())
                                }
                            }
                        }
                        .pointerInput(totalDurationMs, zoomScale) {
                            var lastHapticSegmentId: Int? = null
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (totalDurationMs > 0 && size.width > 0) {
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val seekMs = (fraction * totalDurationMs).toLong()
                                    val currentSeg = segments.firstOrNull { seekMs in it.startMs..it.endMs }
                                    if (currentSeg?.id != lastHapticSegmentId) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticSegmentId = currentSeg?.id
                                    }
                                    onSeek(seekMs)
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    if (amplitudes.isEmpty() || totalDurationMs <= 0) return@Canvas

                    val rulerHeightPx = 18.dp.toPx()
                    val waveAreaHeight = canvasHeight - rulerHeightPx
                    val centerY = rulerHeightPx + (waveAreaHeight / 2f)

                    // 1. Draw Timecode Ruler Bar along the top
                    drawLine(
                        color = CardBorder,
                        start = Offset(0f, rulerHeightPx),
                        end = Offset(canvasWidth, rulerHeightPx),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Determine ruler interval based on total duration
                    val durationSec = (totalDurationMs / 1000).toInt()
                    val intervalSec = when {
                        durationSec <= 15 -> 1
                        durationSec <= 45 -> 2
                        durationSec <= 120 -> 5
                        durationSec <= 300 -> 10
                        else -> 30
                    }

                    var sec = 0
                    while (sec <= durationSec) {
                        val tickFraction = sec.toFloat() / (totalDurationMs / 1000f)
                        val tickX = tickFraction * canvasWidth
                        val isMajor = (sec % (intervalSec * 2) == 0) || sec == 0

                        drawLine(
                            color = if (isMajor) PrimaryCyan.copy(alpha = 0.7f) else CardBorder,
                            start = Offset(tickX, if (isMajor) rulerHeightPx - 8.dp.toPx() else rulerHeightPx - 4.dp.toPx()),
                            end = Offset(tickX, rulerHeightPx),
                            strokeWidth = 1.dp.toPx()
                        )

                        if (isMajor) {
                            val timeStr = String.format("%02d:%02d", sec / 60, sec % 60)
                            drawContext.canvas.nativeCanvas.drawText(
                                timeStr,
                                tickX,
                                rulerHeightPx - 10.dp.toPx(),
                                textPaint
                            )
                        }
                        sec += intervalSec
                    }

                    // 2. Draw Background Cut Region Stripes in Waveform area
                    segments.forEach { seg ->
                        if (!seg.shouldKeep) {
                            val startX = (seg.startMs.toFloat() / totalDurationMs) * canvasWidth
                            val endX = (seg.endMs.toFloat() / totalDurationMs) * canvasWidth
                            val width = (endX - startX).coerceAtLeast(1f)
                            drawRect(
                                color = SilenceRedTranslucent,
                                topLeft = Offset(startX, rulerHeightPx),
                                size = Size(width, waveAreaHeight)
                            )
                        } else if (seg.isSilence && seg.isExcluded) {
                            // Kept pause segment highlighted with subtle green tint
                            val startX = (seg.startMs.toFloat() / totalDurationMs) * canvasWidth
                            val endX = (seg.endMs.toFloat() / totalDurationMs) * canvasWidth
                            val width = (endX - startX).coerceAtLeast(1f)
                            drawRect(
                                color = GreenSuccess.copy(alpha = 0.15f),
                                topLeft = Offset(startX, rulerHeightPx),
                                size = Size(width, waveAreaHeight)
                            )
                        }
                    }

                    // 3. Draw Waveform Bars
                    val barCount = amplitudes.size
                    val barSpacing = 2f * zoomScale.coerceAtLeast(1f)
                    val totalSpacing = barSpacing * (barCount - 1)
                    val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(1.5f)

                    for (i in 0 until barCount) {
                        val x = i * (barWidth + barSpacing)
                        val amp = amplitudes[i].coerceIn(0.06f, 1f)
                        val barHeight = amp * (waveAreaHeight * 0.85f)
                        val topY = centerY - (barHeight / 2f)

                        // Timestamp corresponding to this bar
                        val barTimeMs = ((i.toFloat() / barCount) * totalDurationMs).toLong()
                        val currentSegment = segments.firstOrNull { barTimeMs in it.startMs..it.endMs }
                        val isCut = currentSegment != null && !currentSegment.shouldKeep
                        val isKeptSilence = currentSegment != null && currentSegment.isSilence && currentSegment.isExcluded

                        val barColor = when {
                            isCut -> SilenceRed
                            isKeptSilence -> GreenSuccess
                            else -> SpeechCyan
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }

                    // 4. Draw Playhead Needle & Scrubber Handle
                    val playheadX = ((currentPositionMs.toFloat() / totalDurationMs) * canvasWidth).coerceIn(0f, canvasWidth)

                    // Vertical needle across ruler and waveform
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, canvasHeight),
                        strokeWidth = 2.5.dp.toPx()
                    )

                    // Glowing top playhead badge
                    drawCircle(
                        color = PrimaryCyan,
                        radius = 5.dp.toPx(),
                        center = Offset(playheadX, rulerHeightPx / 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(playheadX, rulerHeightPx / 2f)
                    )
                }
            }
        }
    }
}
