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
        microCrossfade: Boolean = true
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

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        // Maintain uniform video effects across all sequence items when auto-zoom is enabled:
        // Even cuts receive 1.0f identity scale; odd cuts receive 1.12f punch-in zoom.
        // Keeping the VideoFrameProcessor pipeline uniform prevents sequence reconfiguration crashes.
        val punchInZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(1.12f, 1.12f)
            .build()
        val identityZoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(1.0f, 1.0f)
            .build()

        val editedMediaItems = validSegments.mapIndexed { index, seg ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startMs)
                .setEndPositionMs(seg.endMs)
                .setStartsAtKeyFrame(false)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()

            val builder = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(extractAudioOnly)
                .setFlattenForSlowMotion(false)

            // Each EditedMediaItem must have its own AudioProcessor instance to avoid
            // state corruption across clip boundaries in Media3 AudioGraph
            val audioProcessors = if (microCrossfade) {
                listOf(MicroCrossfadeAudioProcessor(15L))
            } else {
                emptyList()
            }

            val videoEffects = if (!extractAudioOnly && autoZoomJumpcuts) {
                if (index % 2 == 1) listOf(punchInZoomEffect) else listOf(identityZoomEffect)
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
                        "Muxer timed out while encoding video cuts."
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

        val inAppMuxerFactory = androidx.media3.transformer.InAppMuxer.Factory.Builder().build()

        // Disable artificial watchdog timeout (C.TIME_UNSET) and use in-app pure MP4 muxer to avoid Muxer errors on Snapdragon/Qualcomm chipsets
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(assetLoaderFactory)
            .setEncoderFactory(encoderFactory)
            .setMuxerFactory(inAppMuxerFactory)
            .setMaxDelayBetweenMuxerSamplesMs(androidx.media3.common.C.TIME_UNSET)
            .addListener(listener)
            .build()

        transformer.start(composition, outputFile.absolutePath)

        // Poll progress every 150ms
        val progressRunnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    trySend(SplicerProgress.Progress(p))
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
