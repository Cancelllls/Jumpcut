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
        extractAudioOnly: Boolean = false,
        autoZoomJumpcuts: Boolean = false,
        microCrossfade: Boolean = true
    ): Flow<SplicerProgress> = callbackFlow {
        val validSegments = speechSegments.filter { (it.endMs - it.startMs) >= 80L }
        if (validSegments.isEmpty()) {
            trySend(SplicerProgress.Error(IllegalArgumentException("No valid speech segments to export")))
            close()
            return@callbackFlow
        }

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        // 1.12x punch-in zoom for dynamic 2-camera talking-head pacing
        val zoomEffect = ScaleAndRotateTransformation.Builder()
            .setScale(1.12f, 1.12f)
            .build()

        val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = if (microCrossfade) {
            listOf(MicroCrossfadeAudioProcessor(15L))
        } else {
            emptyList()
        }

        val baseEffects = Effects(audioProcessors, emptyList())
        val zoomEffects = Effects(audioProcessors, listOf(zoomEffect))

        val editedMediaItems = validSegments.mapIndexed { index, seg ->
            val start = maxOf(0L, seg.startMs)
            val end = maxOf(start + 50L, seg.endMs)
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(start)
                .setEndPositionMs(end)
                .setStartsAtKeyFrame(false)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()

            val builder = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(extractAudioOnly)
                .setFlattenForSlowMotion(false)

            // Alternate punch-in zoom on every odd speech cut with micro-crossfade
            if (!extractAudioOnly && autoZoomJumpcuts && (index % 2 == 1)) {
                builder.setEffects(zoomEffects)
            } else if (audioProcessors.isNotEmpty()) {
                builder.setEffects(baseEffects)
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
                Log.e(
                    TAG,
                    "Export failed: code=${exportException.errorCode}, name=${exportException.errorCodeName}, msg=${exportException.message}",
                    exportException
                )
                val detailedMsg = when (exportException.errorCode) {
                    ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                        "Muxer timed out while encoding video cuts."
                    ExportException.ERROR_CODE_MUXING_FAILED ->
                        "Hardware muxer error: ${exportException.cause?.message ?: exportException.message}"
                    else -> exportException.message ?: "Export failed (${exportException.errorCodeName})"
                }
                trySend(SplicerProgress.Error(Exception(detailedMsg, exportException)))
                close()
            }
        }

        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .build()

        val inAppMuxerFactory = androidx.media3.transformer.InAppMuxer.Factory.Builder().build()

        // Disable artificial watchdog timeout (C.TIME_UNSET) and use in-app pure MP4 muxer to avoid Muxer errors on Snapdragon/Qualcomm chipsets
        val transformer = Transformer.Builder(context)
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
