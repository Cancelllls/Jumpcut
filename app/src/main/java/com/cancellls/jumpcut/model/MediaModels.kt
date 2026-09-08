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
    val isSilence: Boolean,
    val isExcluded: Boolean = false
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    val shouldKeep: Boolean get() = if (isSilence) isExcluded else !isExcluded
}

data class ExportConfig(
    val extractAudioOnly: Boolean = false,
    val saveToGallery: Boolean = true,
    val boostVoice: Boolean = false,
    val autoZoomJumpcuts: Boolean = false,
    val microCrossfade: Boolean = true,
    val silenceTimeWarp: Boolean = false,
    val studioAudioLeveling: Boolean = true,
    val roomToneSmoothing: Boolean = true
)

data class SavedProject(
    val id: String,
    val title: String,
    val originalDurationMs: Long,
    val cutDurationMs: Long,
    val savedPercent: Int,
    val filePath: String,
    val fileSizeBytes: Long,
    val isVideo: Boolean,
    val createdAtMs: Long,
    val thumbnailPath: String? = null,
    val segmentsJson: String? = null
) {
    val formattedSize: String get() {
        val mb = fileSizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb)
        else String.format(java.util.Locale.US, "%d KB", fileSizeBytes / 1024)
    }
}

data class CutSettings(
    val silenceThresholdDb: Float = -32f,
    val minSilenceDurationMs: Long = 350L,
    val paddingMs: Long = 50L,
    val removeNoise: Boolean = true,
    val volumeBoost: Float = 1.0f,
    val voiceNoiseRejection: Float = 0.65f,
    val autoNoiseFloor: Boolean = true,
    val studioAudioLeveling: Boolean = true,
    val roomToneSmoothing: Boolean = true
)

data class CreatorPreset(
    val id: String,
    val name: String,
    val settings: CutSettings,
    val isBuiltIn: Boolean = false
)

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class Analyzing(val progress: Float, val status: String) : ProcessingState()
    data class Ready(
        val originalDurationMs: Long,
        val cutDurationMs: Long,
        val segments: List<CutSegment>,
        val waveformAmplitudes: List<Float>,
        val estimatedNoiseFloorDb: Float = -40f
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
