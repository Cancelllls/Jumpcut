package com.cancellls.jumpcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    Column(modifier = modifier) {
        // Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).padding(0.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(SpeechCyan) }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Speech (Kept)", fontSize = 11.sp, color = TextSecondary)

                Spacer(modifier = Modifier.width(16.dp))

                Box(modifier = Modifier.size(8.dp).clip(CircleShape).padding(0.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(SilenceRed) }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Silence (Auto Cut)", fontSize = 11.sp, color = TextSecondary)
            }

            val silentDuration = segments.filter { it.isSilence }.sumOf { it.durationMs }
            Text(
                text = "Cut: ${silentDuration / 1000}s",
                fontSize = 11.sp,
                color = SilenceRed
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Waveform Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        if (totalDurationMs > 0 && size.width > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * totalDurationMs).toLong())
                        }
                    }
                }
                .pointerInput(totalDurationMs) {
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
            val barSpacing = 2f
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = ((canvasWidth - totalSpacing) / barCount).coerceAtLeast(1.5f)

            // Draw Background Silence Region Stripes
            segments.forEach { seg ->
                if (seg.isSilence) {
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

            // Draw Waveform Bars
            for (i in 0 until barCount) {
                val x = i * (barWidth + barSpacing)
                val amp = amplitudes[i].coerceIn(0.06f, 1f)
                val barHeight = amp * (canvasHeight * 0.85f)
                val topY = centerY - (barHeight / 2f)

                // Timestamp corresponding to this bar
                val barTimeMs = ((i.toFloat() / barCount) * totalDurationMs).toLong()
                val isSilent = segments.any { it.isSilence && barTimeMs in it.startMs..it.endMs }

                val barColor = if (isSilent) SilenceRed else SpeechCyan

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }

            // Draw Playhead Line
            val playheadX = ((currentPositionMs.toFloat() / totalDurationMs) * canvasWidth).coerceIn(0f, canvasWidth)
            drawLine(
                color = Color.White,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, canvasHeight),
                strokeWidth = 3.dp.toPx()
            )

            // Draw Playhead Handle Dot
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
