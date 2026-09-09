package com.cancellls.jumpcut.engine

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.cancellls.jumpcut.model.CaptionStyle
import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.TargetAspectRatio
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class VolumeAudioProcessor(private val volume: Float) : BaseAudioProcessor() {
    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        val shortBuffer = inputBuffer.asShortBuffer()
        val totalShorts = remaining / 2
        for (i in 0 until totalShorts) {
            val sample = shortBuffer.get()
            val scaled = (sample * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(scaled.toShort())
        }
        outputBuffer.flip()
    }
}

sealed class SplicerProgress {
    data class Progress(val percentage: Float) : SplicerProgress()
    data class Success(val outputFile: File) : SplicerProgress()
    data class Error(val throwable: Throwable) : SplicerProgress()
}

object VideoSplicer {
    private const val TAG = "VideoSplicer"

    @OptIn(UnstableApi::class)
    fun splice(
        context: Context,
        inputUri: Uri,
        speechSegments: List<CutSegment>,
        outputFile: File,
        totalDurationMs: Long = 0L,
        extractAudioOnly: Boolean = false,
        autoZoomJumpcuts: Boolean = false,
        microCrossfade: Boolean = true,
        studioAudioLeveling: Boolean = true,
        roomToneSmoothing: Boolean = true,
        ambientNoiseFloorDb: Float = -40f,
        videoResolution: String = "original",
        silenceTimeWarp: Boolean = false,
        silenceSpeedMultiplier: Float = 3.0f,
        allSegments: List<CutSegment> = emptyList(),
        targetAspectRatio: TargetAspectRatio = TargetAspectRatio.ORIGINAL,
        burnInCaptions: Boolean = false,
        captionStyle: CaptionStyle = CaptionStyle.NONE,
        instantRemux: Boolean = false,
        backgroundMusicUri: Uri? = null,
        backgroundMusicVolume: Float = 0.20f,
        musicAutoDuck: Boolean = true
    ): Flow<SplicerProgress> = callbackFlow {
        val maxDuration = if (totalDurationMs > 0L) totalDurationMs else Long.MAX_VALUE
        val segmentsToProcess = if (silenceTimeWarp && allSegments.isNotEmpty()) {
            allSegments
        } else {
            speechSegments
        }

        val validSegments = segmentsToProcess.mapNotNull { seg ->
            val start = seg.startMs.coerceIn(0L, (maxDuration - 60L).coerceAtLeast(0L))
            val end = seg.endMs.coerceIn(start + 50L, maxDuration)
            if (end - start >= 60L) {
                seg.copy(startMs = start, endMs = end)
            } else null
        }

        if (validSegments.isEmpty()) {
            trySend(SplicerProgress.Error(IllegalArgumentException("No valid segments to export within duration")))
            close()
            return@callbackFlow
        }

        // When not in time-warp mode, merge adjacent segments (or micro-gaps <= 80ms) into unified clips
        // to reduce MediaCodec seek and re-initialization overhead.
        val sortedSegments = validSegments.sortedBy { it.startMs }
        val mergedSegments = if (!silenceTimeWarp) {
            val list = mutableListOf<CutSegment>()
            for (seg in sortedSegments) {
                if (list.isEmpty()) {
                    list.add(seg)
                } else {
                    val last = list.last()
                    if (seg.startMs <= last.endMs + 80L) {
                        list[list.size - 1] = last.copy(endMs = maxOf(last.endMs, seg.endMs))
                    } else {
                        list.add(seg)
                    }
                }
            }
            list
        } else {
            sortedSegments
        }

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        // Maintain uniform video effects across all sequence items (resolution downscale + auto-zoom punch-ins):
        val baseScale = when (videoResolution.lowercase()) {
            "720p" -> 0.67f
            else -> 1.0f
        }

        val punchInZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(baseScale * 1.12f, baseScale * 1.12f)
            .build()
        val identityZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(baseScale, baseScale)
            .build()
        val speedChangeGlEffect = SpeedChangeEffect(silenceSpeedMultiplier)

        // Aspect ratio cropping effect (e.g. 9:16 Shorts/Reels, 1:1 Square)
        val presentationEffect = if (!extractAudioOnly && targetAspectRatio != TargetAspectRatio.ORIGINAL && targetAspectRatio.ratio != null) {
            Presentation.createForAspectRatio(targetAspectRatio.ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        } else null

        // Open Captions / Burn-in Subtitle Overlay
        val captionOverlayEffect = if (!extractAudioOnly && burnInCaptions && captionStyle != CaptionStyle.NONE && speechSegments.isNotEmpty()) {
            val textOverlay = object : TextOverlay() {
                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                    return OverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0f, -0.72f)
                        .build()
                }

                override fun getText(presentationTimeUs: Long): SpannableString {
                    val timeMs = presentationTimeUs / 1000L
                    val segIndex = speechSegments.indexOfFirst { timeMs in it.startMs..it.endMs }
                    if (segIndex >= 0) {
                        val text = "  SPEECH #${segIndex + 1}  "
                        val spannable = SpannableString(text)
                        spannable.setSpan(
                            ForegroundColorSpan(captionStyle.textColor.toInt()),
                            0,
                            text.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            BackgroundColorSpan(captionStyle.boxColor.toInt()),
                            0,
                            text.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            text.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        return spannable
                    }
                    return SpannableString("")
                }
            }
            OverlayEffect(listOf(textOverlay))
        } else null

        // Instant Remux can be used when no video transformation effects are active
        val canTransmux = instantRemux &&
            !extractAudioOnly &&
            targetAspectRatio == TargetAspectRatio.ORIGINAL &&
            !autoZoomJumpcuts &&
            !burnInCaptions &&
            !silenceTimeWarp &&
            videoResolution == "original"

        val editedMediaItems = mergedSegments.mapIndexed { index, seg ->
            val isLast = (index == mergedSegments.size - 1)
            val isWarpedSilence = silenceTimeWarp && seg.isSilence && !seg.isExcluded

            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startMs)
                .setStartsAtKeyFrame(canTransmux)

            // For the final segment reaching near source end, use TIME_END_OF_SOURCE
            // to prevent the asset loader from stalling waiting for non-existent frames.
            if (!isLast || seg.endMs < maxDuration - 250L) {
                clippingBuilder.setEndPositionMs(seg.endMs)
            } else {
                clippingBuilder.setEndPositionMs(androidx.media3.common.C.TIME_END_OF_SOURCE)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clippingBuilder.build())
                .build()

            val builder = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(extractAudioOnly)
                .setFlattenForSlowMotion(false)

            val videoEffects = if (!extractAudioOnly) {
                val list = mutableListOf<androidx.media3.common.Effect>()
                if (isWarpedSilence) {
                    list.add(speedChangeGlEffect)
                    list.add(identityZoomEffect)
                } else if (autoZoomJumpcuts) {
                    list.add(if (index % 2 == 1) punchInZoomEffect else identityZoomEffect)
                } else if (baseScale != 1.0f) {
                    list.add(identityZoomEffect)
                }
                presentationEffect?.let { list.add(it) }
                captionOverlayEffect?.let { list.add(it) }
                list
            } else {
                emptyList()
            }

            val audioProcessors = if (isWarpedSilence) {
                listOf(SonicAudioProcessor().apply { setSpeed(silenceSpeedMultiplier) })
            } else if (studioAudioLeveling || roomToneSmoothing || microCrossfade) {
                listOf(
                    StudioAudioProcessor(
                        segmentDurationMs = seg.durationMs,
                        levelingEnabled = studioAudioLeveling,
                        roomToneSmoothingEnabled = roomToneSmoothing,
                        ambientNoiseFloorDb = ambientNoiseFloorDb
                    )
                )
            } else {
                emptyList()
            }

            if (audioProcessors.isNotEmpty() || videoEffects.isNotEmpty()) {
                builder.setEffects(Effects(audioProcessors, videoEffects))
            }

            builder.build()
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence(editedMediaItems))

        if (backgroundMusicUri != null) {
            try {
                val musicMediaItem = MediaItem.Builder().setUri(backgroundMusicUri).build()
                val musicAudioProcessors = mutableListOf<AudioProcessor>()
                val effectiveVol = if (musicAutoDuck) (backgroundMusicVolume * 0.4f).coerceIn(0.01f, 1f) else backgroundMusicVolume
                musicAudioProcessors.add(VolumeAudioProcessor(effectiveVol))
                val musicEdited = EditedMediaItem.Builder(musicMediaItem)
                    .setRemoveVideo(true)
                    .setEffects(Effects(musicAudioProcessors, emptyList()))
                    .build()
                sequences.add(EditedMediaItemSequence(listOf(musicEdited)))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load background music sequence: ${e.message}")
            }
        }

        val compositionBuilder = Composition.Builder(sequences)
        if (canTransmux) {
            compositionBuilder.setTransmuxVideo(true)
            Log.d(TAG, "Instant Remux mode active: transmuxing video bitstream")
        }
        val composition = compositionBuilder.build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.d(TAG, "Export completed successfully: ${outputFile.absolutePath}")
                trySend(SplicerProgress.Success(outputFile))
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                val cause = exportException.cause
                val causeMsg = cause?.message
                val detailedCause = generateSequence(cause) { it.cause }
                    .mapNotNull { it.message }
                    .firstOrNull { it.isNotBlank() && it != "Asset loader error" }

                Log.e(
                    TAG,
                    "Export failed: code=${exportException.errorCode}, name=${exportException.errorCodeName}, msg=${exportException.message}, cause=${cause?.javaClass?.name}: $causeMsg",
                    exportException
                )

                val detailedMsg = when (exportException.errorCode) {
                    ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                        "Export timed out while encoding video cuts."
                    ExportException.ERROR_CODE_MUXING_FAILED ->
                        "Hardware muxer error: ${detailedCause ?: causeMsg ?: exportException.message}"
                    ExportException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Hardware decoder initialization failed: ${detailedCause ?: "Device codec limit reached"}"
                    ExportException.ERROR_CODE_DECODING_FAILED ->
                        "Video decoding error: ${detailedCause ?: "Corrupted media segment or unsupported codec"}"
                    else -> {
                        if (!detailedCause.isNullOrBlank()) {
                            "Asset processing error: $detailedCause"
                        } else if (!exportException.message.isNullOrBlank()) {
                            exportException.message!!
                        } else {
                            "Export failed (${exportException.errorCodeName})"
                        }
                    }
                }
                trySend(SplicerProgress.Error(Exception(detailedMsg, exportException)))
                close()
            }
        }

        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .build()

        val decoderFactory = androidx.media3.transformer.DefaultDecoderFactory.Builder(context)
            .build()

        val assetLoaderFactory = androidx.media3.transformer.DefaultAssetLoaderFactory(
            context,
            decoderFactory,
            androidx.media3.common.util.Clock.DEFAULT
        )

        val muxerFactory = try {
            androidx.media3.transformer.InAppMuxer.Factory.Builder().build()
        } catch (_: Throwable) {
            androidx.media3.transformer.DefaultMuxer.Factory()
        }

        // Set a 15-second watchdog timeout so the muxer can never stall indefinitely ("stops for life")
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(muxerFactory)
            .setMaxDelayBetweenMuxerSamplesMs(15_000L)
            .addListener(listener)
            .build()

        transformer.start(composition, outputFile.absolutePath)

        // Poll progress and maintain active stall watchdog
        var lastReportedProgress = -1f
        var lastProgressChangeTimeMs = System.currentTimeMillis()
        val STALL_TIMEOUT_MS = 30_000L

        val progressRunnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    if (p != lastReportedProgress) {
                        lastReportedProgress = p
                        lastProgressChangeTimeMs = System.currentTimeMillis()
                        trySend(SplicerProgress.Progress(p))
                    }
                }

                // If progress has completely stopped for > 30s and has not completed, fail cleanly
                if (System.currentTimeMillis() - lastProgressChangeTimeMs > STALL_TIMEOUT_MS) {
                    Log.e(TAG, "Export stalled: no progress for ${STALL_TIMEOUT_MS / 1000}s at ${(lastReportedProgress * 100).toInt()}%")
                    trySend(SplicerProgress.Error(IllegalStateException("Export stalled at ${(lastReportedProgress * 100).toInt()}%. Device hardware codec limit reached.")))
                    close()
                    return
                }

                handler.postDelayed(this, 150)
            }
        }
        handler.post(progressRunnable)

        awaitClose {
            handler.removeCallbacks(progressRunnable)
            try {
                transformer.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Transformer cancel exception", e)
            }
        }
    }
}
