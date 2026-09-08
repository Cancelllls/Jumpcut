package com.cancellls.jumpcut.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.cancellls.jumpcut.model.CutSegment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

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
        ambientNoiseFloorDb: Float = -40f
    ): Flow<SplicerProgress> = callbackFlow {
        val maxDuration = if (totalDurationMs > 0L) totalDurationMs else Long.MAX_VALUE
        val validSegments = speechSegments.mapNotNull { seg ->
            val start = seg.startMs.coerceIn(0L, (maxDuration - 60L).coerceAtLeast(0L))
            val end = seg.endMs.coerceIn(start + 50L, maxDuration)
            if (end - start >= 60L) {
                seg.copy(startMs = start, endMs = end)
            } else null
        }

        if (validSegments.isEmpty()) {
            trySend(SplicerProgress.Error(IllegalArgumentException("No valid speech segments to export within duration")))
            close()
            return@callbackFlow
        }

        // Merge adjacent segments (or micro-gaps <= 80ms) into unified continuous clips.
        // This dramatically reduces MediaCodec seek and re-initialization overhead on device hardware,
        // preventing hardware decoder starvation and freeze at 30%/50%.
        val sortedSegments = validSegments.sortedBy { it.startMs }
        val mergedSegments = mutableListOf<CutSegment>()
        for (seg in sortedSegments) {
            if (mergedSegments.isEmpty()) {
                mergedSegments.add(seg)
            } else {
                val last = mergedSegments.last()
                if (seg.startMs <= last.endMs + 80L) {
                    mergedSegments[mergedSegments.size - 1] = last.copy(endMs = maxOf(last.endMs, seg.endMs))
                } else {
                    mergedSegments.add(seg)
                }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        // Maintain uniform video effects across all sequence items when auto-zoom is enabled:
        val punchInZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(1.12f, 1.12f)
            .build()
        val identityZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(1.0f, 1.0f)
            .build()

        val editedMediaItems = mergedSegments.mapIndexed { index, seg ->
            val isLast = (index == mergedSegments.size - 1)
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startMs)
                .setStartsAtKeyFrame(false)

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

            val videoEffects = if (!extractAudioOnly && autoZoomJumpcuts) {
                if (index % 2 == 1) listOf(punchInZoomEffect) else listOf(identityZoomEffect)
            } else {
                emptyList()
            }

            val audioProcessors = if (studioAudioLeveling || roomToneSmoothing || microCrossfade) {
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

        val composition = Composition.Builder(
            EditedMediaItemSequence(editedMediaItems)
        ).build()

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
