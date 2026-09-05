package com.cancellls.jumpcut.model

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val isVideo: Boolean = true,
    val width: Int = 1080,
    val height: Int = 1920
)

data class CutSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val isSilence: Boolean
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

data class CutSettings(
    val silenceThresholdDb: Float = -32f,
    val minSilenceDurationMs: Long = 350L,
    val paddingMs: Long = 50L,
    val removeNoise: Boolean = true,
    val volumeBoost: Float = 1.0f
)

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class Analyzing(val progress: Float, val status: String) : ProcessingState()
    data class Ready(
        val originalDurationMs: Long,
        val cutDurationMs: Long,
        val segments: List<CutSegment>,
        val waveformAmplitudes: List<Float>
    ) : ProcessingState() {
        val savedMs: Long get() = (originalDurationMs - cutDurationMs).coerceAtLeast(0)
        val savedPercent: Int get() = if (originalDurationMs > 0) {
            ((savedMs.toFloat() / originalDurationMs) * 100).toInt()
        } else 0
    }
    data class Exporting(val progress: Float, val status: String) : ProcessingState()
    data class Exported(
        val outputUri: Uri,
        val outputPath: String,
        val originalDurationMs: Long,
        val cutDurationMs: Long,
        val savedPercent: Int
    ) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
