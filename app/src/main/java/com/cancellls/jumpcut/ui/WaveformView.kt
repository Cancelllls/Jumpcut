package com.cancellls.jumpcut.ui

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

    Column(modifier = modifier) {
        // Header & Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape)) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(SpeechCyan) }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Speech", fontSize = 11.sp, color = TextSecondary)

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.size(8.dp).clip(CircleShape)) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(SilenceRed) }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Cut", fontSize = 11.sp, color = TextSecondary)
            }

            // Zoom chips (1x, 2x, 4x)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Zoom",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
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

        Spacer(modifier = Modifier.height(10.dp))

        // Scrollable Multi-Scale Canvas Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
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
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (totalDurationMs > 0 && size.width > 0) {
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    onSeek((fraction * totalDurationMs).toLong())
                                }
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerY = canvasHeight / 2f

                    if (amplitudes.isEmpty() || totalDurationMs <= 0) return@Canvas

                    val barCount = amplitudes.size
                    val barSpacing = 2f * zoomScale.coerceAtLeast(1f)
                    val totalSpacing = barSpacing * (barCount - 1)
                    val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(1.5f)

                    // 1. Draw Background Cut Region Stripes
                    segments.forEach { seg ->
                        if (!seg.shouldKeep) {
                            val startX = (seg.startMs.toFloat() / totalDurationMs) * canvasWidth
                            val endX = (seg.endMs.toFloat() / totalDurationMs) * canvasWidth
                            val width = (endX - startX).coerceAtLeast(1f)
                            drawRect(
                                color = SilenceRedTranslucent,
                                topLeft = Offset(startX, 0f),
                                size = Size(width, canvasHeight)
                            )
                        }
                    }

                    // 2. Draw Waveform Bars
                    for (i in 0 until barCount) {
                        val x = i * (barWidth + barSpacing)
                        val amp = amplitudes[i].coerceIn(0.06f, 1f)
                        val barHeight = amp * (canvasHeight * 0.85f)
                        val topY = centerY - (barHeight / 2f)

                        // Timestamp corresponding to this bar
                        val barTimeMs = ((i.toFloat() / barCount) * totalDurationMs).toLong()
                        val currentSegment = segments.firstOrNull { barTimeMs in it.startMs..it.endMs }
                        val isCut = currentSegment != null && !currentSegment.shouldKeep

                        val barColor = if (isCut) SilenceRed else SpeechCyan

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }

                    // 3. Draw Playhead Line
                    val playheadX = ((currentPositionMs.toFloat() / totalDurationMs) * canvasWidth).coerceIn(0f, canvasWidth)
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, canvasHeight),
                        strokeWidth = 3.dp.toPx()
                    )

                    // 4. Draw Playhead Handle Dot
                    drawCircle(
                        color = PrimaryCyan,
                        radius = 6.dp.toPx(),
                        center = Offset(playheadX, 6.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(playheadX, 6.dp.toPx())
                    )
                }
            }
        }
    }
}
