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

enum class TargetAspectRatio(val displayName: String, val ratio: Float?, val tag: String) {
    ORIGINAL("Original", null, "SOURCE"),
    PORTRAIT_9_16("9:16 Shorts/Reels", 9f / 16f, "9:16"),
    SQUARE_1_1("1:1 Square", 1f, "1:1"),
    PORTRAIT_4_5("4:5 Feed", 4f / 5f, "4:5"),
    LANDSCAPE_16_9("16:9 Landscape", 16f / 9f, "16:9")
}

enum class CaptionStyle(val displayName: String, val textColor: Long, val boxColor: Long) {
    NONE("No Captions", 0xFFFFFFFF, 0x00000000),
    NEON_CYAN("Neon Cyan", 0xFF00D2B4, 0xCC07090E),
    YELLOW_PUNCH("Yellow Punch", 0xFFFFD600, 0xE6000000),
    CLASSIC_WHITE("Classic White", 0xFFFFFFFF, 0xCC111827)
}

data class EditorHistorySnapshot(
    val segments: List<CutSegment>,
    val cutSettings: CutSettings,
    val description: String = ""
)

data class ExportConfig(
    val extractAudioOnly: Boolean = false,
    val saveToGallery: Boolean = true,
    val boostVoice: Boolean = false,
    val autoZoomJumpcuts: Boolean = false,
    val microCrossfade: Boolean = true,
    val silenceTimeWarp: Boolean = false,
    val studioAudioLeveling: Boolean = true,
    val roomToneSmoothing: Boolean = true,
    val audioFormat: String = "m4a",
    val videoResolution: String = "original",
    val targetAspectRatio: TargetAspectRatio = TargetAspectRatio.ORIGINAL,
    val burnInCaptions: Boolean = false,
    val captionStyle: CaptionStyle = CaptionStyle.NONE,
    val instantRemux: Boolean = false,
    val backgroundMusicUri: Uri? = null,
    val backgroundMusicVolume: Float = 0.20f,
    val musicAutoDuck: Boolean = true
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
